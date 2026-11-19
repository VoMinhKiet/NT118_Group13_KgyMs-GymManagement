package com.kgyms.app.viewmodel.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgyms.app.data.model.StaffCandidate
import com.kgyms.app.data.model.StaffMember
import com.kgyms.app.data.model.StaffPosition
import com.kgyms.app.data.model.StaffStatus
import com.kgyms.app.data.repository.StaffRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

data class StaffRowUi(
    val uid: String,
    /** "Trần Hoàng • PT" */
    val title: String,
    /** "Strength Coach • Ca 14:00–22:00" */
    val subtitle: String,
    val tone: AdminTone
)

data class StaffDetailUi(
    val uid: String,
    val positionLabel: String,
    val name: String,
    val headline: String,
    val statusLabel: String,
    val statusTone: AdminTone,
    val isActive: Boolean,
    val fields: List<DetailFieldUi>,
    val bio: String
)

data class CandidateUi(val uid: String, val name: String, val email: String)

/** Bước 1 của "Thêm nhân sự": chọn user. */
data class StaffPickerUi(val query: String = "")

/** Bước 2 (hoặc sửa hồ sơ): form nhân sự. Giờ và ngày lưu dạng chữ số, UI tự chèn ":" và "/". */
data class StaffFormUi(
    val uid: String,
    val name: String,
    val contact: String,
    val isEdit: Boolean,
    val position: String = StaffPosition.PT,
    val specialty: String = "",
    val shiftStartDigits: String = "",
    val shiftEndDigits: String = "",
    val bio: String = "",
    val startDateDigits: String = "",
    /** Giữ nguyên trạng thái khi sửa. */
    val status: String = StaffStatus.ACTIVE,
    val isSaving: Boolean = false,
    val error: String? = null
) {
    val title: String get() = if (isEdit) "Sửa hồ sơ nhân sự" else "Đăng ký nhân sự"
}

data class StaffStatusConfirmUi(val uid: String, val name: String, val toActive: Boolean, val note: String)

data class StaffUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val activeCount: String = "–",
    val trainerCount: String = "–",
    val filter: String = StaffViewModel.FILTER_ACTIVE,
    val filters: List<OptionUi> = emptyList(),
    val rows: List<StaffRowUi> = emptyList(),
    val picker: StaffPickerUi? = null,
    val candidates: List<CandidateUi> = emptyList(),
    val form: StaffFormUi? = null,
    val confirmStatus: StaffStatusConfirmUi? = null,
    val message: String? = null
) {
    val positionOptions: List<OptionUi> = StaffPosition.all.map { OptionUi(it, StaffLabels.position(it)) }
    val emptyMessage: String?
        get() = if (!isLoading && errorMessage == null && rows.isEmpty())
            "Chưa có nhân sự nào ở mục này. Bấm \"Thêm nhân sự\" để chọn từ danh sách user." else null
}

object StaffLabels {
    fun position(position: String) = when (position) {
        StaffPosition.PT -> "PT"
        StaffPosition.RECEPTIONIST -> "Lễ tân"
        StaffPosition.SUPPORT -> "CSKH"
        StaffPosition.MANAGER -> "Quản lý"
        else -> position
    }
}

/* ============================ VIEWMODEL ============================ */

class StaffViewModel(
    private val repository: StaffRepository = StaffRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(StaffUiState())
    val state: StateFlow<StaffUiState> = _state.asStateFlow()

    private var staff: List<StaffMember> = emptyList()
    private var users: Map<String, StaffCandidate> = emptyMap()
    private var assignedPackages: Map<String, List<String>> = emptyMap()

    init {
        viewModelScope.launch {
            combine(
                repository.observeStaff(),
                repository.observeUsers().onStart { emit(emptyList()) }.catch { emit(emptyList()) },
                repository.observeAssignedPackages().onStart { emit(emptyMap()) }.catch { emit(emptyMap()) }
            ) { s, u, p -> Triple(s, u, p) }
                .catch { e -> _state.update { it.copy(isLoading = false, errorMessage = e.toUserMessage()) } }
                .collect { (s, u, p) ->
                    staff = s.sortedWith(compareBy<StaffMember>({ !it.isActive }, { StaffPosition.all.indexOf(it.position) }, { it.fullName.lowercase() }))
                    users = u.associateBy { it.uid }
                    assignedPackages = p
                    _state.update { it.copy(isLoading = false, errorMessage = null).recompute() }
                }
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    fun onFilterSelected(filter: String) = _state.update { it.copy(filter = filter).recompute() }

    fun detail(uid: String): StaffDetailUi? = staff.firstOrNull { it.uid == uid }?.let { s ->
        val contact = users[uid]
        val packages = assignedPackages[uid].orEmpty()
        StaffDetailUi(
            uid = uid,
            positionLabel = StaffLabels.position(s.position).uppercase(),
            name = s.fullName,
            headline = s.specialty.ifBlank { "Chưa có chuyên môn" },
            statusLabel = if (s.isActive) "Đang làm việc" else "Đã nghỉ việc",
            statusTone = if (s.isActive) AdminTone.Positive else AdminTone.Neutral,
            isActive = s.isActive,
            fields = buildList {
                add(DetailFieldUi("Số điện thoại", contact?.phone.orEmpty().ifBlank { "—" }))
                add(DetailFieldUi("Email", contact?.email.orEmpty().ifBlank { "—" }))
                add(DetailFieldUi("Ca làm", s.workShift.ifBlank { "—" }))
                add(DetailFieldUi("Ngày vào làm", s.startDate.ifBlank { "—" }))
                if (s.isTrainer) add(DetailFieldUi("Gói PT phụ trách", if (packages.isEmpty()) "Chưa có" else packages.joinToString(", ")))
                add(DetailFieldUi("Trạng thái", if (s.isActive) "Đang làm việc" else "Đã nghỉ việc",
                    if (s.isActive) AdminTone.Positive else AdminTone.Neutral))
            },
            bio = s.bio
        )
    }

    /* ---------- Thêm nhân sự: bước 1 chọn user ---------- */

    fun openPicker() = _state.update { it.copy(picker = StaffPickerUi(), form = null).recompute() }

    fun onPickerQuery(query: String) = _state.update { it.copy(picker = StaffPickerUi(query)).recompute() }

    fun closeSheet() = _state.update { if (it.form?.isSaving == true) it else it.copy(picker = null, form = null) }

    fun pickUser(uid: String) {
        val user = users[uid] ?: return
        _state.update {
            it.copy(
                form = StaffFormUi(
                    uid = uid,
                    name = user.fullName.ifBlank { user.email },
                    contact = listOf(user.email, user.phone).filter { c -> c.isNotBlank() }.joinToString(" • "),
                    isEdit = false,
                    startDateDigits = SimpleDateFormat("ddMMyyyy", Locale.ROOT).format(Date())
                )
            )
        }
    }

    /** Từ form quay lại danh sách chọn user (chỉ khi đang thêm mới). */
    fun backToPicker() = _state.update { if (it.form?.isEdit == false) it.copy(form = null) else it }

    /* ---------- Bước 2 / sửa hồ sơ ---------- */

    fun openEdit(uid: String) {
        val s = staff.firstOrNull { it.uid == uid } ?: return
        val user = users[uid]
        val (start, end) = s.workShift.split("–", "-").map { it.trim() }.let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "") }
        _state.update {
            it.copy(
                picker = null,
                form = StaffFormUi(
                    uid = uid,
                    name = s.fullName,
                    contact = listOfNotNull(user?.email, user?.phone).filter { c -> c.isNotBlank() }.joinToString(" • "),
                    isEdit = true,
                    position = s.position,
                    specialty = s.specialty,
                    shiftStartDigits = start.filter(Char::isDigit),
                    shiftEndDigits = end.filter(Char::isDigit),
                    bio = s.bio,
                    startDateDigits = s.startDate.filter(Char::isDigit),
                    status = s.status
                )
            )
        }
    }

    fun onFormChange(form: StaffFormUi) = _state.update {
        it.copy(
            form = form.copy(
                shiftStartDigits = form.shiftStartDigits.filter(Char::isDigit).take(4),
                shiftEndDigits = form.shiftEndDigits.filter(Char::isDigit).take(4),
                startDateDigits = form.startDateDigits.filter(Char::isDigit).take(8),
                error = null
            )
        )
    }

    fun submitForm() {
        val form = _state.value.form ?: return
        validate(form)?.let { error -> return _state.update { it.copy(form = form.copy(error = error)) } }

        val previous = staff.firstOrNull { it.uid == form.uid }
        val member = StaffMember(
            uid = form.uid,
            fullName = form.name.trim(),
            avatarUrl = users[form.uid]?.avatarUrl ?: previous?.avatarUrl.orEmpty(),
            position = form.position,
            specialty = form.specialty.trim(),
            bio = form.bio.trim(),
            workShift = "${formatTime(form.shiftStartDigits)}–${formatTime(form.shiftEndDigits)}",
            startDate = AssetViewModel.formatDate(form.startDateDigits),
            status = form.status
        )
        val positionLabel = StaffLabels.position(form.position)

        _state.update { it.copy(form = form.copy(isSaving = true)) }
        viewModelScope.launch {
            val result = runSafely {
                if (form.isEdit) repository.updateStaff(member) else repository.addStaff(member, positionLabel)
            }
            _state.update {
                result.fold(
                    onSuccess = { _ ->
                        it.copy(
                            picker = null,
                            form = null,
                            message = if (form.isEdit) "Đã cập nhật hồ sơ ${member.fullName}"
                            else "Đã thêm ${member.fullName} làm $positionLabel"
                        )
                    },
                    onFailure = { e -> it.copy(form = form.copy(isSaving = false, error = e.toUserMessage())) }
                )
            }
        }
    }

    /* ---------- Nghỉ việc / làm lại ---------- */

    fun requestStatusChange(uid: String) {
        val s = staff.firstOrNull { it.uid == uid } ?: return
        val packages = assignedPackages[uid].orEmpty()
        val note = when {
            !s.isActive -> "${s.fullName} sẽ làm việc lại." + if (s.isTrainer) " Quyền PT được bật lại, nhưng các gói PT cần phân công lại." else ""
            packages.isNotEmpty() -> "${s.fullName} sẽ bị gỡ khỏi ${packages.size} gói PT: ${packages.joinToString(", ")}."
            else -> "Hồ sơ vẫn được giữ lại, có thể cho làm lại sau."
        }
        _state.update { it.copy(confirmStatus = StaffStatusConfirmUi(uid, s.fullName, toActive = !s.isActive, note = note)) }
    }

    fun cancelStatusChange() = _state.update { it.copy(confirmStatus = null) }

    fun confirmStatusChange() {
        val confirm = _state.value.confirmStatus ?: return
        val s = staff.firstOrNull { it.uid == confirm.uid } ?: return cancelStatusChange()
        val status = if (confirm.toActive) StaffStatus.ACTIVE else StaffStatus.INACTIVE
        _state.update { it.copy(confirmStatus = null) }
        viewModelScope.launch {
            val result = runSafely { repository.setStatus(s, status) }
            _state.update {
                it.copy(message = result.exceptionOrNull()?.toUserMessage()
                    ?: if (confirm.toActive) "${s.fullName} đã làm việc lại" else "Đã cho ${s.fullName} nghỉ việc")
            }
        }
    }

    /* ============================ MAPPING ============================ */

    private fun StaffUiState.recompute(): StaffUiState {
        val active = staff.filter { it.isActive }
        val visible = when (filter) {
            FILTER_ACTIVE -> active
            FILTER_INACTIVE -> staff.filterNot { it.isActive }
            else -> staff
        }
        val staffUids = staff.map { it.uid }.toSet()
        val keyword = picker?.query?.trim().orEmpty()
        return copy(
            activeCount = active.size.toString(),
            trainerCount = active.count { it.isTrainer }.toString(),
            filters = listOf(
                OptionUi(FILTER_ACTIVE, "Đang làm (${active.size})"),
                OptionUi(FILTER_INACTIVE, "Nghỉ việc (${staff.size - active.size})"),
                OptionUi(FILTER_ALL, "Tất cả (${staff.size})")
            ),
            rows = visible.map { s ->
                StaffRowUi(
                    uid = s.uid,
                    title = "${s.fullName} • ${StaffLabels.position(s.position)}",
                    subtitle = listOf(
                        s.specialty,
                        if (s.workShift.isNotBlank()) "Ca ${s.workShift}" else "",
                        if (s.isActive) "" else "Đã nghỉ việc"
                    ).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "Chưa cập nhật hồ sơ" },
                    tone = when {
                        !s.isActive -> AdminTone.Neutral
                        s.isTrainer -> AdminTone.Brand
                        else -> AdminTone.Info
                    }
                )
            },
            // Người đã có hồ sơ (kể cả đã nghỉ) không hiện lại; muốn tuyển lại thì dùng "Cho làm lại".
            candidates = users.values
                .filter { it.uid !in staffUids }
                .filter {
                    keyword.isEmpty() || it.fullName.contains(keyword, ignoreCase = true) ||
                        it.email.contains(keyword, ignoreCase = true) || it.phone.contains(keyword)
                }
                .sortedBy { it.fullName.lowercase() }
                .map { CandidateUi(it.uid, it.fullName.ifBlank { "(Chưa có tên)" }, listOf(it.email, it.phone).filter { c -> c.isNotBlank() }.joinToString(" • ")) }
        )
    }

    private fun validate(form: StaffFormUi): String? {
        if (form.position !in StaffPosition.all) return "Vui lòng chọn chức vụ."
        if (form.specialty.trim().length > 100) return "Chuyên môn tối đa 100 ký tự."
        if (form.bio.trim().length > 500) return "Giới thiệu tối đa 500 ký tự."
        if (!form.shiftStartDigits.isValidTime() || !form.shiftEndDigits.isValidTime()) return "Ca làm phải có dạng HH:mm, vd 14:00."
        if (form.shiftStartDigits == form.shiftEndDigits) return "Giờ bắt đầu và kết thúc ca không được trùng nhau."
        val date = AssetViewModel.formatDate(form.startDateDigits)
        val validDate = date.matches(Regex("""\d{2}/\d{2}/\d{4}""")) && runCatching {
            SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply { isLenient = false }.parse(date)
        }.isSuccess
        if (!validDate) return "Ngày vào làm phải có dạng dd/MM/yyyy."
        return null
    }

    private fun String.isValidTime(): Boolean {
        if (length != 4) return false
        val hour = substring(0, 2).toIntOrNull() ?: return false
        val minute = substring(2, 4).toIntOrNull() ?: return false
        return hour in 0..23 && minute in 0..59
    }

    private fun formatTime(digits: String) = "${digits.take(2)}:${digits.drop(2)}"

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
            raw.contains("permission", ignoreCase = true) -> "Bạn không có quyền quản lý nhân sự (cần tài khoản admin)."
            raw.isNotBlank() -> raw
            else -> "Đã xảy ra lỗi, vui lòng thử lại."
        }
    }

    companion object {
        const val FILTER_ACTIVE = "active"
        const val FILTER_INACTIVE = "inactive"
        const val FILTER_ALL = "all"
    }
}
