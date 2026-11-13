package com.kgyms.app.viewmodel.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgyms.app.data.model.GymPackage
import com.kgyms.app.data.model.PackageType
import com.kgyms.app.data.model.TrainerOption
import com.kgyms.app.data.repository.PackageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/* ============================ UI MODEL ============================ */

data class PackageRowUi(
    val id: String,
    /** "Premium 6 tháng • 1.990.000đ" */
    val title: String,
    /** "Hội viên • 180 ngày • 2 hội viên đang dùng" */
    val subtitle: String,
    val statusLabel: String,
    val tone: AdminTone,
    val isActive: Boolean,
    /** Đang có memberships tham chiếu → không cho xóa. */
    val inUse: Boolean
)

data class PackageDetailUi(
    val id: String,
    val title: String,
    val summary: String,
    val description: String,
    val benefits: List<String>
)

/** Form thêm/sửa gói. [editingId] = null nghĩa là tạo mới. */
data class PackageFormUi(
    val editingId: String? = null,
    val name: String = "",
    val type: String = PackageType.MEMBERSHIP,
    val price: String = "",
    val durationDays: String = "",
    val sessions: String = "",
    val trainerUid: String = "",
    val description: String = "",
    /** Mỗi dòng là 1 quyền lợi. */
    val benefits: String = "",
    val isActive: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null
) {
    val title: String get() = if (editingId == null) "Thêm gói tập" else "Sửa gói tập"
    val isPt: Boolean get() = type == PackageType.PT
}

data class PackageUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val headline: String = "",
    val rows: List<PackageRowUi> = emptyList(),
    val form: PackageFormUi? = null,
    val trainerOptions: List<OptionUi> = listOf(OptionUi("", "Chưa phân công")),
    val detail: PackageDetailUi? = null,
    /** Gói đang chờ xác nhận xóa. */
    val confirmDelete: PackageRowUi? = null,
    val message: String? = null
) {
    val typeOptions: List<OptionUi> = PackageType.all.map { OptionUi(it, PackageLabels.type(it)) }
    val activeOptions: List<OptionUi> = listOf(OptionUi("true", "Đang bán"), OptionUi("false", "Ngừng bán"))
}

object PackageLabels {
    fun type(type: String) = if (type == PackageType.PT) "Gói PT" else "Hội viên"
}

/* ============================ VIEWMODEL ============================ */

class PackageViewModel(
    private val repository: PackageRepository = PackageRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(PackageUiState())
    val state: StateFlow<PackageUiState> = _state.asStateFlow()

    private var packages: List<GymPackage> = emptyList()
    private var usage: Map<String, Int> = emptyMap()
    private var trainers: List<TrainerOption> = emptyList()

    init {
        viewModelScope.launch {
            combine(
                repository.observePackages(),
                // Không đọc được memberships thì coi như chưa ai dùng.
                repository.observeUsage().onStart { emit(emptyMap()) }.catch { emit(emptyMap()) }
            ) { list, counts -> list to counts }
                .catch { e -> _state.update { it.copy(isLoading = false, errorMessage = e.toUserMessage()) } }
                .collect { (list, counts) ->
                    packages = list.sortedWith(compareBy<GymPackage>({ it.type != PackageType.MEMBERSHIP }, { it.price }))
                    usage = counts
                    _state.update { it.withPackages() }
                }
        }
        loadTrainers()
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    /* ---------- Chi tiết (bấm) ---------- */

    fun openDetail(id: String) = _state.update { state ->
        state.copy(detail = packages.firstOrNull { it.id == id }?.let { p ->
            PackageDetailUi(
                id = p.id,
                title = p.name,
                summary = summary(p) + " • " + AdminFormat.vnd(p.price),
                description = p.description,
                benefits = p.benefits
            )
        })
    }

    fun closeDetail() = _state.update { it.copy(detail = null) }

    /* ---------- Thêm / sửa ---------- */

    fun openAdd() {
        loadTrainers()
        _state.update { it.copy(form = PackageFormUi()) }
    }

    fun openEdit(id: String) {
        val p = packages.firstOrNull { it.id == id } ?: return
        loadTrainers()
        _state.update {
            it.copy(
                detail = null,
                form = PackageFormUi(
                    editingId = p.id,
                    name = p.name,
                    type = p.type,
                    price = p.price.toString(),
                    durationDays = p.durationDays.toString(),
                    sessions = if (p.sessions > 0) p.sessions.toString() else "",
                    trainerUid = p.trainerUid,
                    description = p.description,
                    benefits = p.benefits.joinToString("\n"),
                    isActive = p.isActive
                )
            )
        }
    }

    fun closeForm() = _state.update { if (it.form?.isSaving == true) it else it.copy(form = null) }

    fun onFormChange(form: PackageFormUi) = _state.update {
        it.copy(
            form = form.copy(
                price = form.price.filter(Char::isDigit).take(10),
                durationDays = form.durationDays.filter(Char::isDigit).take(4),
                sessions = form.sessions.filter(Char::isDigit).take(3),
                error = null
            )
        )
    }

    fun submitForm() {
        val form = _state.value.form ?: return
        validate(form)?.let { error -> return _state.update { it.copy(form = form.copy(error = error)) } }

        val pkg = form.toPackage()
        _state.update { it.copy(form = form.copy(isSaving = true)) }
        viewModelScope.launch {
            val result = runSafely {
                if (form.editingId == null) repository.addPackage(pkg) else repository.updatePackage(pkg)
            }
            _state.update {
                result.fold(
                    onSuccess = { _ ->
                        it.copy(form = null, message = if (form.editingId == null) "Đã thêm gói \"${pkg.name}\"" else "Đã cập nhật gói \"${pkg.name}\"")
                    },
                    onFailure = { e -> it.copy(form = form.copy(isSaving = false, error = e.toUserMessage())) }
                )
            }
        }
    }

    /* ---------- Ngừng bán / xóa ---------- */

    fun toggleActive(id: String) {
        val p = packages.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            val result = runSafely { repository.setActive(p, !p.isActive) }
            _state.update {
                it.copy(message = result.exceptionOrNull()?.toUserMessage()
                    ?: if (p.isActive) "Đã ngừng bán \"${p.name}\"" else "Đã mở bán lại \"${p.name}\"")
            }
        }
    }

    fun requestDelete(id: String) = _state.update { state ->
        val row = state.rows.firstOrNull { it.id == id } ?: return@update state
        if (row.inUse) {
            state.copy(message = "Gói đang có ${usage[id] ?: 0} hội viên sử dụng nên không thể xóa. Hãy chọn \"Ngừng bán\".")
        } else {
            state.copy(confirmDelete = row)
        }
    }

    fun cancelDelete() = _state.update { it.copy(confirmDelete = null) }

    fun confirmDelete() {
        val row = _state.value.confirmDelete ?: return
        val p = packages.firstOrNull { it.id == row.id } ?: return cancelDelete()
        _state.update { it.copy(confirmDelete = null) }
        viewModelScope.launch {
            // Kiểm tra lại ngay trước khi xóa phòng trường hợp vừa có hội viên đăng ký.
            if ((usage[p.id] ?: 0) > 0) {
                _state.update { it.copy(message = "Gói vừa có hội viên sử dụng nên không thể xóa.") }
                return@launch
            }
            val result = runSafely { repository.deletePackage(p) }
            _state.update { it.copy(message = result.exceptionOrNull()?.toUserMessage() ?: "Đã xóa gói \"${p.name}\"") }
        }
    }

    /* ============================ MAPPING ============================ */

    private fun PackageUiState.withPackages() = copy(
        isLoading = false,
        errorMessage = null,
        headline = "${packages.count { it.isActive }} gói đang bán • tổng ${packages.size} gói",
        rows = packages.map { p ->
            val used = usage[p.id] ?: 0
            PackageRowUi(
                id = p.id,
                title = "${p.name} • ${AdminFormat.vnd(p.price)}",
                subtitle = summary(p) + if (used > 0) " • $used hội viên đang dùng" else "",
                statusLabel = if (p.isActive) "Đang bán" else "Ngừng bán",
                tone = if (p.isActive) AdminTone.Positive else AdminTone.Neutral,
                isActive = p.isActive,
                inUse = used > 0
            )
        }
    )

    private fun summary(p: GymPackage): String = buildList {
        add(PackageLabels.type(p.type))
        if (p.durationDays > 0) add("${p.durationDays} ngày")
        if (p.type == PackageType.PT && p.sessions > 0) add("${p.sessions} buổi")
        if (p.type == PackageType.PT) add(trainers.firstOrNull { it.uid == p.trainerUid }?.name ?: "chưa phân PT")
    }.joinToString(" • ")

    private fun loadTrainers() {
        viewModelScope.launch {
            runSafely { repository.getTrainers() }.onSuccess { list ->
                trainers = list
                _state.update {
                    it.copy(trainerOptions = listOf(OptionUi("", "Chưa phân công")) + list.map { t -> OptionUi(t.uid, t.name) })
                        .withPackages()
                }
            }
        }
    }

    private fun validate(form: PackageFormUi): String? = when {
        form.name.isBlank() -> "Vui lòng nhập tên gói."
        form.name.trim().length > 100 -> "Tên gói tối đa 100 ký tự."
        form.price.toLongOrNull() == null -> "Vui lòng nhập giá (VNĐ)."
        (form.durationDays.toIntOrNull() ?: 0) <= 0 -> "Thời hạn phải lớn hơn 0 ngày."
        form.isPt && (form.sessions.toIntOrNull() ?: 0) <= 0 -> "Gói PT cần số buổi lớn hơn 0."
        packages.any { it.id != form.editingId && it.name.equals(form.name.trim(), ignoreCase = true) } ->
            "Đã có gói cùng tên."
        else -> null
    }

    private fun PackageFormUi.toPackage() = GymPackage(
        id = editingId.orEmpty(),
        name = name.trim(),
        description = description.trim(),
        type = type,
        price = price.toLong(),
        durationDays = durationDays.toInt(),
        sessions = if (isPt) sessions.toIntOrNull() ?: 0 else 0,
        trainerUid = if (isPt) trainerUid else "",
        isActive = isActive,
        benefits = benefits.lines().map { it.trim() }.filter { it.isNotEmpty() }
    )

    private inline fun <T> runSafely(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun Throwable.toUserMessage(): String {
        val raw = message.orEmpty()
        return when {
            raw.contains("permission", ignoreCase = true) -> "Bạn không có quyền quản lý gói tập (cần tài khoản admin)."
            raw.isNotBlank() -> raw
            else -> "Đã xảy ra lỗi, vui lòng thử lại."
        }
    }
}
