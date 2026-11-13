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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssetUiState(
    val assets: List<Asset> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {
    /** Tổng số thiết bị = tổng quantity của mọi loại máy. */
    val totalQuantity: Int
        get() = assets.sumOf { it.quantity }

    /** Số thiết bị cần bảo trì = tổng quantity của máy đang bảo trì hoặc hỏng. */
    val maintenanceQuantity: Int
        get() = assets
            .filter { it.status != AssetStatus.WORKING }
            .sumOf { it.quantity }
}

/** Dữ liệu người dùng nhập ở form "Thêm tài sản" (dạng chuỗi, chưa kiểm tra). */
data class AssetForm(
    val name: String = "",
    val category: String = AssetCategory.CARDIO,
    val brand: String = "",
    val quantity: String = "",
    val status: String = AssetStatus.WORKING,
    val location: String = "",
    val purchaseDate: String = "",
    val lastMaintenanceDate: String = "",
    val nextMaintenanceDate: String = "",
    val warrantyMonths: String = "",
    val description: String = ""
)

class AssetViewModel(
    private val repository: AssetRepository = AssetRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(AssetUiState())
    val state: StateFlow<AssetUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAssets()
                .catch { error ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = error.toUserMessage())
                    }
                }
                .collect { assets ->
                    _state.update {
                        it.copy(assets = assets, isLoading = false, errorMessage = null)
                    }
                }
        }
    }

    /**
     * Kiểm tra form; hợp lệ thì ghi lên Firebase.
     * onResult nhận null nếu thành công, ngược lại là thông báo lỗi.
     */
    fun addAsset(form: AssetForm, onResult: (String?) -> Unit) {
        val validationError = validate(form)
        if (validationError != null) {
            onResult(validationError)
            return
        }

        viewModelScope.launch {
            val result = runCatching { repository.addAsset(form.toAsset()) }
            onResult(result.exceptionOrNull()?.toUserMessage())
        }
    }

    /** onResult nhận null nếu xóa thành công, ngược lại là thông báo lỗi. */
    fun deleteAsset(id: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val name = assetName(id)
            val result = runCatching { repository.deleteAsset(id, name) }
            onResult(result.exceptionOrNull()?.toUserMessage())
        }
    }

    /**
     * Tạo phiếu bảo trì: máy chuyển sang "Đang bảo trì" hoặc "Hỏng", lý do ghi vào mô tả.
     * onResult nhận null nếu thành công, ngược lại là thông báo lỗi.
     */
    fun createMaintenanceTicket(
        assetId: String?,
        status: String,
        reason: String,
        onResult: (String?) -> Unit
    ) {
        when {
            assetId.isNullOrBlank() -> return onResult("Vui lòng chọn 1 loại máy.")
            status != AssetStatus.MAINTENANCE && status != AssetStatus.BROKEN ->
                return onResult("Trạng thái phải là Đang bảo trì hoặc Hỏng.")
            reason.isBlank() -> return onResult("Vui lòng nhập lý do cần sửa/bảo trì.")
        }

        viewModelScope.launch {
            val id = assetId!!
            val result = runCatching { repository.reportIssue(id, assetName(id), status, reason.trim()) }
            onResult(result.exceptionOrNull()?.toUserMessage())
        }
    }

    /** "Đã sửa" / "Đã bảo trì": máy về Đang hoạt động, lần bảo trì gần nhất = hôm nay. */
    fun markResolved(assetId: String, onResult: (String?) -> Unit) {
        val today = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).format(Date())

        viewModelScope.launch {
            val previousStatus = _state.value.assets.firstOrNull { it.id == assetId }?.status.orEmpty()
            val result = runCatching {
                repository.markResolved(assetId, assetName(assetId), previousStatus, today)
            }
            onResult(result.exceptionOrNull()?.toUserMessage())
        }
    }

    private fun assetName(id: String): String =
        _state.value.assets.firstOrNull { it.id == id }?.name.orEmpty()

    private fun validate(form: AssetForm): String? {
        if (form.name.isBlank()) return "Vui lòng nhập tên máy."

        val quantity = form.quantity.trim().toIntOrNull()
        if (quantity == null || quantity < 0) return "Số lượng phải là số nguyên ≥ 0."

        if (form.warrantyMonths.isNotBlank()) {
            val warranty = form.warrantyMonths.trim().toIntOrNull()
            if (warranty == null || warranty < 0) return "Bảo hành phải là số tháng ≥ 0."
        }

        if (form.category !in AssetCategory.all) return "Loại máy không hợp lệ."
        if (form.status !in AssetStatus.all) return "Trạng thái không hợp lệ."

        listOf(
            "Ngày mua" to form.purchaseDate,
            "Lần bảo trì gần nhất" to form.lastMaintenanceDate,
            "Lịch bảo trì tiếp theo" to form.nextMaintenanceDate
        ).forEach { (label, value) ->
            if (value.isNotBlank() && !value.trim().isValidDate()) {
                return "$label phải có dạng dd/MM/yyyy."
            }
        }

        return null
    }

    private fun AssetForm.toAsset() = Asset(
        name = name.trim(),
        category = category,
        brand = brand.trim(),
        quantity = quantity.trim().toInt(),
        status = status,
        location = location.trim(),
        purchaseDate = purchaseDate.trim(),
        lastMaintenanceDate = lastMaintenanceDate.trim(),
        nextMaintenanceDate = nextMaintenanceDate.trim(),
        warrantyMonths = warrantyMonths.trim().toIntOrNull() ?: 0,
        imageUrl = "",
        description = description.trim()
    )

    private fun String.isValidDate(): Boolean {
        if (!matches(Regex("""\d{2}/\d{2}/\d{4}"""))) return false
        return runCatching {
            SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply { isLenient = false }.parse(this)
        }.isSuccess
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
}
