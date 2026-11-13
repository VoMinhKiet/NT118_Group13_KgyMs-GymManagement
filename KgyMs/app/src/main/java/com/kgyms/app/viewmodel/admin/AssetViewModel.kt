package com.kgyms.app.viewmodel.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgyms.app.data.model.Asset
import com.kgyms.app.data.model.AssetCategory
import com.kgyms.app.data.model.AssetStatus
import com.kgyms.app.data.repository.AssetRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/* ============================ UI MODEL ============================ */

data class AssetRowUi(
    val id: String,
    /** "Máy Smith • 2" */
    val title: String,
    /** "Đang bảo trì • Khu Strength • Tầng 1" */
    val subtitle: String,
    val tone: AdminTone,
    /** "Đã sửa" / "Đã bảo trì" khi nhấn giữ; null nếu máy đang hoạt động. */
    val resolveLabel: String?
)

data class AssetDetailUi(
    val id: String,
    val name: String,
    val location: String,
    val statusLabel: String,
    val statusTone: AdminTone,
    val lastCheck: String,
    /** Các dòng "nhãn — giá trị". */
    val fields: List<Pair<String, String>>,
    val description: String
)

/** Form "Thêm tài sản". Các ô ngày chỉ lưu chữ số (tối đa 8), UI tự chèn "/". */
data class AssetFormUi(
    val name: String = "",
    val category: String = AssetCategory.CARDIO,
    val brand: String = "",
    val quantity: String = "",
    val warrantyMonths: String = "",
    val status: String = AssetStatus.WORKING,
    val location: String = "",
    val purchaseDigits: String = "",
    val lastMaintenanceDigits: String = "",
    val nextMaintenanceDigits: String = "",
    val description: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

data class TicketChoiceUi(val id: String, val title: String, val statusLabel: String, val tone: AdminTone)

/** Form "Tạo phiếu bảo trì". */
data class TicketFormUi(
    val selectedId: String? = null,
    val status: String = AssetStatus.MAINTENANCE,
    val reason: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

data class AssetUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val totalQuantity: String = "–",
    val needFixQuantity: String = "–",
    val rows: List<AssetRowUi> = emptyList(),
    val ticketChoices: List<TicketChoiceUi> = emptyList(),
    /** null = sheet đang đóng. */
    val addForm: AssetFormUi? = null,
    val ticketForm: TicketFormUi? = null,
    val deletingId: String? = null,
    /** Thông báo một lần (Toast); UI gọi onMessageShown() sau khi hiện. */
    val message: String? = null
) {
    val categoryOptions: List<OptionUi> = AssetCategory.all.map { OptionUi(it, AssetLabels.category(it)) }
    val statusOptions: List<OptionUi> = AssetStatus.all.map { OptionUi(it, AssetLabels.status(it)) }
    val ticketStatusOptions: List<OptionUi> =
        listOf(AssetStatus.MAINTENANCE, AssetStatus.BROKEN).map { OptionUi(it, AssetLabels.status(it)) }
}

/** Nhãn/sắc thái hiển thị của tài sản. */
object AssetLabels {
    fun status(status: String) = when (status) {
        AssetStatus.WORKING -> "Đang hoạt động"
        AssetStatus.MAINTENANCE -> "Đang bảo trì"
        AssetStatus.BROKEN -> "Hỏng"
        else -> status
    }

    fun tone(status: String) = when (status) {
        AssetStatus.WORKING -> AdminTone.Positive
        AssetStatus.MAINTENANCE -> AdminTone.Warning
        else -> AdminTone.Critical
    }

    fun category(category: String) = when (category) {
        AssetCategory.CARDIO -> "Cardio"
        AssetCategory.STRENGTH -> "Strength"
        AssetCategory.OTHER -> "Khác"
        else -> category
    }
}

/* ============================ VIEWMODEL ============================ */

class AssetViewModel(
    private val repository: AssetRepository = AssetRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(AssetUiState())
    val state: StateFlow<AssetUiState> = _state.asStateFlow()

    private var assets: List<Asset> = emptyList()

    init {
        viewModelScope.launch {
            repository.observeAssets()
                .catch { error -> _state.update { it.copy(isLoading = false, errorMessage = error.toUserMessage()) } }
                .collect { list ->
                    assets = list
                    _state.update { it.withAssets(list) }
                }
        }
    }

    /** Chi tiết 1 máy; null nếu không còn tồn tại. */
    fun detail(id: String): AssetDetailUi? = assets.firstOrNull { it.id == id }?.toDetail()

    fun onMessageShown() = _state.update { it.copy(message = null) }

    /* ---------- Thêm tài sản ---------- */

    fun openAddForm() = _state.update { it.copy(addForm = AssetFormUi()) }

    fun closeAddForm() = _state.update { if (it.addForm?.isSaving == true) it else it.copy(addForm = null) }

    fun onAddFormChange(form: AssetFormUi) = _state.update {
        it.copy(
            addForm = form.copy(
                quantity = form.quantity.filter(Char::isDigit),
                warrantyMonths = form.warrantyMonths.filter(Char::isDigit),
                purchaseDigits = form.purchaseDigits.filter(Char::isDigit).take(8),
                lastMaintenanceDigits = form.lastMaintenanceDigits.filter(Char::isDigit).take(8),
                nextMaintenanceDigits = form.nextMaintenanceDigits.filter(Char::isDigit).take(8),
                error = null
            )
        )
    }

    fun submitAddForm() {
        val form = _state.value.addForm ?: return
        validate(form)?.let { error ->
            _state.update { it.copy(addForm = form.copy(error = error)) }
            return
        }
        _state.update { it.copy(addForm = form.copy(isSaving = true, error = null)) }

        viewModelScope.launch {
            val result = runSafely { repository.addAsset(form.toAsset()) }
            _state.update {
                result.fold(
                    onSuccess = { _ -> it.copy(addForm = null, message = "Đã thêm \"${form.name.trim()}\"") },
                    onFailure = { e -> it.copy(addForm = form.copy(isSaving = false, error = e.toUserMessage())) }
                )
            }
        }
    }

    /* ---------- Phiếu bảo trì ---------- */

    fun openTicketForm() = _state.update {
        if (assets.isEmpty()) it.copy(message = "Chưa có tài sản nào để tạo phiếu.")
        else it.copy(ticketForm = TicketFormUi())
    }

    fun closeTicketForm() = _state.update { if (it.ticketForm?.isSaving == true) it else it.copy(ticketForm = null) }

    fun onTicketFormChange(form: TicketFormUi) = _state.update { it.copy(ticketForm = form.copy(error = null)) }

    fun submitTicketForm() {
        val form = _state.value.ticketForm ?: return
        val error = when {
            form.selectedId.isNullOrBlank() -> "Vui lòng chọn 1 loại máy."
            form.status != AssetStatus.MAINTENANCE && form.status != AssetStatus.BROKEN ->
                "Trạng thái phải là Đang bảo trì hoặc Hỏng."
            form.reason.isBlank() -> "Vui lòng nhập lý do cần sửa/bảo trì."
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(ticketForm = form.copy(error = error)) }
            return
        }

        val id = form.selectedId!!
        val name = assetName(id)
        _state.update { it.copy(ticketForm = form.copy(isSaving = true)) }
        viewModelScope.launch {
            val result = runSafely { repository.reportIssue(id, name, form.status, form.reason.trim()) }
            _state.update {
                result.fold(
                    onSuccess = { _ -> it.copy(ticketForm = null, message = "Đã tạo phiếu cho \"$name\"") },
                    onFailure = { e -> it.copy(ticketForm = form.copy(isSaving = false, error = e.toUserMessage())) }
                )
            }
        }
    }

    /* ---------- Đã sửa / Đã bảo trì ---------- */

    fun markResolved(id: String) {
        val asset = assets.firstOrNull { it.id == id } ?: return
        val today = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).format(Date())
        val done = if (asset.status == AssetStatus.BROKEN) "đã sửa" else "đã bảo trì"
        viewModelScope.launch {
            val result = runSafely { repository.markResolved(id, asset.name, asset.status, today) }
            _state.update {
                it.copy(message = result.exceptionOrNull()?.toUserMessage()
                    ?: "\"${asset.name}\" $done, đang hoạt động lại")
            }
        }
    }

    /* ---------- Xóa ---------- */

    /** [onDeleted] dùng để màn chi tiết quay lại danh sách. */
    fun deleteAsset(id: String, onDeleted: () -> Unit) {
        val name = assetName(id)
        _state.update { it.copy(deletingId = id) }
        viewModelScope.launch {
            val result = runSafely { repository.deleteAsset(id, name) }
            _state.update {
                it.copy(
                    deletingId = null,
                    message = result.exceptionOrNull()?.toUserMessage() ?: "Đã xóa \"$name\""
                )
            }
            if (result.isSuccess) onDeleted()
        }
    }

    /* ============================ MAPPING ============================ */

    private fun AssetUiState.withAssets(list: List<Asset>) = copy(
        isLoading = false,
        errorMessage = null,
        totalQuantity = list.sumOf { it.quantity }.toString(),
        needFixQuantity = list.filter { it.status != AssetStatus.WORKING }.sumOf { it.quantity }.toString(),
        rows = list.map { asset ->
            AssetRowUi(
                id = asset.id,
                title = "${asset.name} • ${asset.quantity}",
                subtitle = listOf(AssetLabels.status(asset.status), asset.location).filter { it.isNotBlank() }.joinToString(" • "),
                tone = AssetLabels.tone(asset.status),
                resolveLabel = when (asset.status) {
                    AssetStatus.BROKEN -> "Đã sửa"
                    AssetStatus.MAINTENANCE -> "Đã bảo trì"
                    else -> null
                }
            )
        },
        ticketChoices = list.map {
            TicketChoiceUi(it.id, "${it.name} • ${it.quantity}", AssetLabels.status(it.status), AssetLabels.tone(it.status))
        }
    )

    private fun Asset.toDetail() = AssetDetailUi(
        id = id,
        name = name,
        location = location.ifBlank { "Chưa có vị trí" },
        statusLabel = AssetLabels.status(status),
        statusTone = AssetLabels.tone(status),
        lastCheck = "Lần kiểm tra: ${lastMaintenanceDate.ifBlank { "—" }}",
        fields = listOf(
            "Loại" to AssetLabels.category(category),
            "Hãng / Model" to brand,
            "Số lượng" to quantity.toString(),
            "Ngày mua" to purchaseDate,
            "Bảo hành" to if (warrantyMonths > 0) "$warrantyMonths tháng" else "",
            "Lịch bảo trì" to nextMaintenanceDate
        ).map { (label, value) -> label to value.ifBlank { "—" } },
        description = description
    )

    private fun assetName(id: String) = assets.firstOrNull { it.id == id }?.name.orEmpty()

    private fun validate(form: AssetFormUi): String? {
        if (form.name.isBlank()) return "Vui lòng nhập tên máy."
        val quantity = form.quantity.toIntOrNull()
        if (quantity == null || quantity < 0) return "Số lượng phải là số nguyên ≥ 0."
        if (form.category !in AssetCategory.all) return "Loại máy không hợp lệ."
        if (form.status !in AssetStatus.all) return "Trạng thái không hợp lệ."
        listOf(
            "Ngày mua" to form.purchaseDigits,
            "Lần bảo trì gần nhất" to form.lastMaintenanceDigits,
            "Lịch bảo trì tiếp theo" to form.nextMaintenanceDigits
        ).forEach { (label, digits) ->
            if (digits.isNotEmpty() && !formatDate(digits).isValidDate()) return "$label phải có dạng dd/MM/yyyy."
        }
        return null
    }

    private fun AssetFormUi.toAsset() = Asset(
        name = name.trim(),
        category = category,
        brand = brand.trim(),
        quantity = quantity.toInt(),
        status = status,
        location = location.trim(),
        purchaseDate = formatDate(purchaseDigits),
        lastMaintenanceDate = formatDate(lastMaintenanceDigits),
        nextMaintenanceDate = formatDate(nextMaintenanceDigits),
        warrantyMonths = warrantyMonths.toIntOrNull() ?: 0,
        imageUrl = "",
        description = description.trim()
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
            raw.contains("permission", ignoreCase = true) ->
                "Bạn không có quyền thao tác dữ liệu tài sản (cần tài khoản admin)."
            raw.isNotBlank() -> raw
            else -> "Đã xảy ra lỗi, vui lòng thử lại."
        }
    }

    companion object {
        /** "12032024" → "12/03/2024"; chưa đủ số vẫn chèn "/" để báo sai định dạng. */
        fun formatDate(digits: String): String = buildString {
            digits.forEachIndexed { index, char ->
                if (index == 2 || index == 4) append('/')
                append(char)
            }
        }

        private fun String.isValidDate(): Boolean {
            if (!matches(Regex("""\d{2}/\d{2}/\d{4}"""))) return false
            return runCatching {
                SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply { isLenient = false }.parse(this)
            }.isSuccess
        }
    }
}
