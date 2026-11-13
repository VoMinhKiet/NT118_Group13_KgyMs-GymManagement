package com.kgyms.app.viewmodel.qr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgyms.app.data.model.CheckInResult
import com.kgyms.app.data.repository.CheckInRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Loại kết quả; màn hình chọn câu chữ vi/en theo loại. */
enum class CheckInOutcome { CheckedIn, CheckedOut, NotCheckInCode, InvalidCode, StaffNotRequired, NoActiveMembership }

data class QrCheckInResultUi(
    val outcome: CheckInOutcome,
    /** "HH:mm" lúc vào (CheckedIn) hoặc lúc ra (CheckedOut). */
    val time: String = "",
    /** "HH:mm" lúc vào, chỉ dùng với CheckedOut. */
    val since: String = "",
    /** Số phút đã tập, chỉ dùng với CheckedOut. */
    val minutes: Long = 0,
    val packageName: String = ""
) {
    val isSuccess: Boolean get() = outcome == CheckInOutcome.CheckedIn || outcome == CheckInOutcome.CheckedOut
}

/** Giai đoạn của màn quét QR điểm danh. */
sealed interface QrCheckInPhase {
    /** Camera đang chờ mã. */
    data object Scanning : QrCheckInPhase
    /** Đã đọc được mã, đang ghi Firebase. */
    data object Processing : QrCheckInPhase
    data class Done(val result: QrCheckInResultUi) : QrCheckInPhase
    /** Lỗi mạng/quyền/chưa cấu hình (câu tiếng Việt). */
    data class Failed(val message: String) : QrCheckInPhase
}

data class QrCheckInUiState(val phase: QrCheckInPhase = QrCheckInPhase.Scanning) {
    val isScanning: Boolean get() = phase == QrCheckInPhase.Scanning
    val showCamera: Boolean get() = phase == QrCheckInPhase.Scanning || phase == QrCheckInPhase.Processing
}

class QrCheckInViewModel(
    private val repository: CheckInRepository = CheckInRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(QrCheckInUiState())
    val state: StateFlow<QrCheckInUiState> = _state.asStateFlow()

    /** Camera gọi liên tục mỗi khung hình đọc được mã; chỉ xử lý mã đầu tiên. */
    fun onCodeScanned(raw: String) {
        if (!_state.value.isScanning) return
        _state.value = QrCheckInUiState(QrCheckInPhase.Processing)
        viewModelScope.launch {
            val phase = try {
                QrCheckInPhase.Done(repository.handleScan(raw).toUi())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                QrCheckInPhase.Failed(e.toUserMessage())
            }
            _state.value = QrCheckInUiState(phase)
        }
    }

    /** Quét lại; cũng gọi khi mở màn để không hiện kết quả cũ. */
    fun scanAgain() {
        if (_state.value.phase != QrCheckInPhase.Processing) _state.value = QrCheckInUiState()
    }

    private fun CheckInResult.toUi(): QrCheckInResultUi = when (this) {
        is CheckInResult.CheckedIn -> QrCheckInResultUi(CheckInOutcome.CheckedIn, time = hhmm(at), packageName = packageName)
        is CheckInResult.CheckedOut -> QrCheckInResultUi(
            CheckInOutcome.CheckedOut,
            time = hhmm(checkOutAt),
            since = hhmm(checkInAt),
            minutes = ((checkOutAt - checkInAt) / 60_000).coerceAtLeast(0)
        )
        CheckInResult.NotCheckInCode -> QrCheckInResultUi(CheckInOutcome.NotCheckInCode)
        CheckInResult.InvalidCode -> QrCheckInResultUi(CheckInOutcome.InvalidCode)
        CheckInResult.StaffNotRequired -> QrCheckInResultUi(CheckInOutcome.StaffNotRequired)
        CheckInResult.NoActiveMembership -> QrCheckInResultUi(CheckInOutcome.NoActiveMembership)
    }

    private fun hhmm(millis: Long) = SimpleDateFormat("HH:mm", Locale.ROOT).format(Date(millis))

    private fun Throwable.toUserMessage(): String {
        val raw = message.orEmpty()
        return when {
            raw.contains("permission", ignoreCase = true) -> "Không có quyền điểm danh với tài khoản này."
            raw.isNotBlank() -> raw
            else -> "Đã xảy ra lỗi, vui lòng thử lại."
        }
    }
}
