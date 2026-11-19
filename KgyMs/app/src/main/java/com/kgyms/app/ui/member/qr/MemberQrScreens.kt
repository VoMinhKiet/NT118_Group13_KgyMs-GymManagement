package com.kgyms.app.ui.member.qr

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.kgyms.app.data.model.SpecialPackages
import com.kgyms.app.ui.member.profile.LocalProfilePalette
import com.kgyms.app.ui.member.profile.profileText as tr
import com.kgyms.app.viewmodel.qr.CheckInOutcome
import com.kgyms.app.viewmodel.qr.QrCheckInPhase
import com.kgyms.app.viewmodel.qr.QrCheckInResultUi
import com.kgyms.app.viewmodel.qr.QrCheckInViewModel
import com.kgyms.app.viewmodel.qr.QrPackageUi
import com.kgyms.app.viewmodel.qr.QrPaymentDetailUi
import com.kgyms.app.viewmodel.qr.QrPaymentMessage
import com.kgyms.app.viewmodel.qr.QrPaymentUiState
import com.kgyms.app.viewmodel.qr.QrPaymentViewModel

private enum class QrPage { Hub, CheckIn, Payment }

/**
 * Tab QR của hội viên: chọn "Quét QR điểm danh" hoặc "QR thanh toán".
 * Màn thanh toán ở đây là chỗ test tạm; sau này tích hợp vào tab Kế hoạch.
 */
@Composable
internal fun MemberQrScreen(onHome: () -> Unit) {
    var pageName by rememberSaveable { mutableStateOf(QrPage.Hub.name) }
    /** Gói cần mở sẵn khi vào màn thanh toán (vd vé ngày). */
    var preselect by rememberSaveable { mutableStateOf<String?>(null) }
    val page = QrPage.valueOf(pageName)
    // Đăng ký trước nên các BackHandler sâu hơn (màn VietQR) được ưu tiên.
    BackHandler(enabled = page != QrPage.Hub) { pageName = QrPage.Hub.name }

    when (page) {
        QrPage.Hub -> QrHub(
            onCheckIn = { pageName = QrPage.CheckIn.name },
            onPayment = { preselect = null; pageName = QrPage.Payment.name },
            onHome = onHome
        )
        QrPage.CheckIn -> QrCheckInScreen(
            onBack = { pageName = QrPage.Hub.name },
            onBuyDayPass = { preselect = SpecialPackages.DAY_PASS; pageName = QrPage.Payment.name }
        )
        QrPage.Payment -> QrPaymentScreen(
            preselectPackageId = preselect,
            onPreselectHandled = { preselect = null },
            onBack = { pageName = QrPage.Hub.name }
        )
    }
}

/* ============================ KHUNG CHUNG ============================ */

@Composable
private fun QrPageScaffold(
    title: String,
    tag: String,
    onBack: () -> Unit,
    backLabel: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
            .padding(24.dp).testTag(tag),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        content()
        TextButton(onClick = onBack, modifier = Modifier.testTag("${tag}_back")) { Text(backLabel) }
    }
}

@Composable
private fun QrCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    border: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val p = LocalProfilePalette.current
    val shape = RoundedCornerShape(22.dp)
    val stroke = BorderStroke(1.dp, border ?: p.border)
    val inner: @Composable () -> Unit = {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = p.card, border = stroke, modifier = modifier.fillMaxWidth()) { inner() }
    } else {
        Surface(shape = shape, color = p.card, border = stroke, modifier = modifier.fillMaxWidth()) { inner() }
    }
}

@Composable
private fun QrPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    val p = LocalProfilePalette.current
    Button(
        onClick = onClick, enabled = enabled && !loading,
        colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = Color.White),
        shape = RoundedCornerShape(18.dp), modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        else Text(text)
    }
}

/** Toast một lần cho thông báo của ViewModel. */
@Composable
private fun QrToast(text: String?, onShown: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(text) {
        if (text != null) {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            onShown()
        }
    }
}

/* ============================ HUB ============================ */

@Composable
private fun QrHub(onCheckIn: () -> Unit, onPayment: () -> Unit, onHome: () -> Unit) {
    val p = LocalProfilePalette.current
    QrPageScaffold(tr("Dịch vụ QR", "QR services"), "member_screen_qr", onHome, tr("Về Trang chủ", "Back to Home")) {
        QrCard(Modifier.testTag("qr_checkin"), onClick = onCheckIn) {
            Text(tr("Quét QR điểm danh tại gym", "Scan gym check-in QR"), style = MaterialTheme.typography.titleMedium)
            Text(tr("Quét mã trên màn hình ở quầy để vào/ra phòng tập.", "Scan the code at the front desk to check in or out."), color = p.muted)
        }
        QrCard(Modifier.testTag("qr_payment"), onClick = onPayment) {
            Text(tr("QR thanh toán", "QR payment"), style = MaterialTheme.typography.titleMedium)
            Text(tr("Chọn gói tập và chuyển khoản bằng mã VietQR.", "Pick a package and pay by VietQR bank transfer."), color = p.muted)
        }
    }
}

/* ============================ ĐIỂM DANH ============================ */

@Composable
private fun QrCheckInScreen(
    onBack: () -> Unit,
    onBuyDayPass: () -> Unit,
    viewModel: QrCheckInViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val p = LocalProfilePalette.current

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }
    LaunchedEffect(Unit) {
        viewModel.scanAgain()
        if (!hasPermission && !askedOnce) {
            askedOnce = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    QrPageScaffold(tr("Điểm danh", "Check-in"), "qr_checkin_screen", onBack, tr("Quay lại", "Back")) {
        if (!hasPermission) {
            QrCard {
                Text(tr("Cần quyền camera", "Camera permission needed"), style = MaterialTheme.typography.titleMedium)
                Text(tr("KgyMs dùng camera để quét mã QR điểm danh ở quầy. Ảnh không được lưu hay gửi đi.",
                    "KgyMs uses the camera only to scan the check-in QR at the desk. No images are saved or sent."), color = p.muted)
                QrPrimaryButton(tr("Cấp quyền camera", "Allow camera"), { permissionLauncher.launch(Manifest.permission.CAMERA) })
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                }) { Text(tr("Mở cài đặt ứng dụng", "Open app settings")) }
            }
            return@QrPageScaffold
        }

        if (state.showCamera) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)).background(Color.Black)
                    .testTag("qr_camera"),
                contentAlignment = Alignment.Center
            ) {
                QrCameraPreview(onCode = viewModel::onCodeScanned, modifier = Modifier.fillMaxSize())
                if (state.phase == QrCheckInPhase.Processing) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
            Text(
                if (state.phase == QrCheckInPhase.Processing) tr("Đang xử lý…", "Processing…")
                else tr("Đưa mã QR trên màn hình ở quầy vào khung. Mã đổi mỗi 30 giây.",
                    "Point the camera at the QR on the front-desk screen. It changes every 30 seconds."),
                color = p.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        }

        when (val phase = state.phase) {
            is QrCheckInPhase.Done -> CheckInResultCard(phase.result, onScanAgain = viewModel::scanAgain, onBuyDayPass = onBuyDayPass)
            is QrCheckInPhase.Failed -> QrCard(border = p.danger) {
                Text(tr("Không điểm danh được", "Check-in failed"), style = MaterialTheme.typography.titleMedium, color = p.danger)
                Text(phase.message, color = p.muted)
                QrPrimaryButton(tr("Quét lại", "Scan again"), viewModel::scanAgain)
            }
            else -> Unit
        }
    }
}

@Composable
private fun CheckInResultCard(result: QrCheckInResultUi, onScanAgain: () -> Unit, onBuyDayPass: () -> Unit) {
    val p = LocalProfilePalette.current
    val (title, detail) = when (result.outcome) {
        CheckInOutcome.CheckedIn -> tr("Check-in thành công", "Checked in") to buildString {
            append(tr("Vào lúc ", "At ")); append(result.time)
            if (result.packageName.isNotBlank()) append(" • ").append(result.packageName)
            append(". ").append(tr("Quét lại khi ra về để check-out.", "Scan again when leaving to check out."))
        }
        CheckInOutcome.CheckedOut -> tr("Check-out thành công", "Checked out") to
            tr("Vào ${result.since} • Ra ${result.time} • ${result.minutes} phút tập.",
                "In ${result.since} • Out ${result.time} • ${result.minutes} min workout.")
        CheckInOutcome.NotCheckInCode -> tr("Không phải mã điểm danh", "Not a check-in code") to
            tr("Hãy quét mã QR trên màn hình ở quầy KgyMs.", "Scan the QR shown on the KgyMs front-desk screen.")
        CheckInOutcome.InvalidCode -> tr("Mã đã hết hạn", "Code expired") to
            tr("Mã đổi mỗi 30 giây. Hãy quét mã mới trên màn hình ở quầy.", "The code changes every 30 seconds. Scan the current one at the desk.")
        CheckInOutcome.StaffNotRequired -> tr("Không cần điểm danh", "No check-in needed") to
            tr("Tài khoản admin và PT không cần điểm danh.", "Admin and trainer accounts don't need to check in.")
        CheckInOutcome.NoActiveMembership -> tr("Chưa có gói đang hoạt động", "No active membership") to
            tr("Bạn cần gói tập còn hạn hôm nay để vào phòng gym. Có thể mua vé ngày 5.000đ.",
                "You need a membership valid today. You can buy a 5,000đ day pass.")
    }
    QrCard(border = if (result.isSuccess) p.accent else p.border) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            color = if (result.isSuccess) p.accent else p.text, modifier = Modifier.testTag("qr_checkin_result"))
        Text(detail, color = p.muted)
        if (result.outcome == CheckInOutcome.NoActiveMembership) {
            QrPrimaryButton(tr("Mua vé ngày", "Buy day pass"), onBuyDayPass)
        }
        if (result.isSuccess) {
            OutlinedButton(onClick = onScanAgain, modifier = Modifier.fillMaxWidth()) { Text(tr("Quét mã khác", "Scan another")) }
        } else {
            OutlinedButton(onClick = onScanAgain, modifier = Modifier.fillMaxWidth()) { Text(tr("Quét lại", "Scan again")) }
        }
    }
}

/* ============================ THANH TOÁN ============================ */

@Composable
private fun QrPaymentScreen(
    preselectPackageId: String?,
    onPreselectHandled: () -> Unit,
    onBack: () -> Unit,
    viewModel: QrPaymentViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(preselectPackageId) {
        if (preselectPackageId != null) {
            viewModel.preselectPackage(preselectPackageId)
            onPreselectHandled()
        }
    }

    QrToast(state.error, viewModel::onErrorShown)
    QrToast(
        when (state.message) {
            QrPaymentMessage.Cancelled -> tr("Đã huỷ hoá đơn.", "Invoice cancelled.")
            QrPaymentMessage.NotLoggedIn -> tr("Bạn cần đăng nhập để thanh toán.", "Please sign in to pay.")
            null -> null
        },
        viewModel::onMessageShown
    )

    val detail = state.detail
    if (detail != null) {
        BackHandler { viewModel.closePayment() }
        PaymentDetail(
            detail = detail,
            confirmCancel = state.confirmCancel,
            isCancelling = state.isCancelling,
            onRequestCancel = viewModel::requestCancel,
            onDismissCancel = viewModel::dismissCancel,
            onConfirmCancel = viewModel::confirmCancel,
            onClose = viewModel::closePayment
        )
    } else {
        PackageList(state, onSelect = viewModel::selectPackage, onOpenPending = viewModel::openPayment, onBack = onBack)
    }

    state.confirm?.let { pkg ->
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirm,
            modifier = Modifier.testTag("qr_payment_confirm"),
            title = { Text(pkg.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(pkg.priceLabel, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(packageSummary(pkg))
                    Text(tr("Hệ thống sẽ tạo hoá đơn và mã VietQR. Gói được kích hoạt sau khi KgyMs xác nhận đã nhận tiền.",
                        "An invoice and VietQR code will be created. The package activates once KgyMs confirms the payment."),
                        color = LocalProfilePalette.current.muted)
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmPurchase, enabled = !state.isSubmitting) {
                    Text(if (state.isSubmitting) tr("Đang tạo…", "Creating…") else tr("Tạo mã QR", "Create QR"))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConfirm, enabled = !state.isSubmitting) { Text(tr("Đóng", "Close")) }
            }
        )
    }
}

@Composable
private fun packageSummary(pkg: QrPackageUi): String = buildList {
    add(if (pkg.isPt) tr("Gói PT", "PT package") else tr("Gói hội viên", "Membership"))
    if (pkg.isPt && pkg.sessions > 0) add(tr("${pkg.sessions} buổi", "${pkg.sessions} sessions"))
    if (pkg.durationDays > 0) add(tr("${pkg.durationDays} ngày", "${pkg.durationDays} days"))
}.joinToString(" • ")

@Composable
private fun PackageList(
    state: QrPaymentUiState,
    onSelect: (String) -> Unit,
    onOpenPending: (String) -> Unit,
    onBack: () -> Unit
) {
    val p = LocalProfilePalette.current
    QrPageScaffold(tr("QR thanh toán", "QR payment"), "qr_payment_screen", onBack, tr("Quay lại", "Back")) {
        Text(tr("Màn thử nghiệm: chọn gói để tạo hoá đơn và mã VietQR.", "Test screen: pick a package to create an invoice and VietQR code."),
            color = p.muted)

        if (state.isLoading) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = p.accent) }
            return@QrPageScaffold
        }

        if (state.pending.isNotEmpty()) {
            Text(tr("Hoá đơn chờ thanh toán", "Awaiting payment"), style = MaterialTheme.typography.titleMedium)
            state.pending.forEach { row ->
                QrCard(Modifier.testTag("qr_pending_${row.paymentId}"), onClick = { onOpenPending(row.paymentId) }, border = p.accent) {
                    Text("${row.paymentId} • ${row.amountLabel}", fontWeight = FontWeight.SemiBold)
                    Text(listOf(row.packageName, row.createdAt).filter { it.isNotBlank() }.joinToString(" • "), color = p.muted)
                }
            }
        }

        Text(tr("Gói tập đang bán", "Available packages"), style = MaterialTheme.typography.titleMedium)
        if (state.packages.isEmpty()) {
            Text(tr("Chưa có gói nào đang bán.", "No packages for sale yet."), color = p.muted)
        }
        state.packages.forEach { pkg ->
            QrCard(Modifier.testTag("qr_package_${pkg.id}"), onClick = { onSelect(pkg.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pkg.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(pkg.priceLabel, fontWeight = FontWeight.Bold, color = p.accent)
                }
                Text(packageSummary(pkg), color = p.muted)
                if (pkg.description.isNotBlank()) Text(pkg.description, color = p.muted, style = MaterialTheme.typography.bodySmall)
                if (pkg.pendingPaymentId != null) {
                    Text(tr("Đang chờ thanh toán • bấm để xem mã QR", "Awaiting payment • tap to view QR"),
                        color = p.accent, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun PaymentDetail(
    detail: QrPaymentDetailUi,
    confirmCancel: Boolean,
    isCancelling: Boolean,
    onRequestCancel: () -> Unit,
    onDismissCancel: () -> Unit,
    onConfirmCancel: () -> Unit,
    onClose: () -> Unit
) {
    val p = LocalProfilePalette.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val copiedText = tr("Đã sao chép", "Copied")
    val copy: (String) -> Unit = { value ->
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("KgyMs", value)))
            Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
        }
    }

    QrPageScaffold(detail.packageName.ifBlank { tr("Hoá đơn", "Invoice") }, "qr_payment_detail", onClose, tr("Quay lại danh sách", "Back to list")) {
        when {
            detail.isPaid -> QrCard(border = p.accent) {
                Text(tr("Thanh toán thành công", "Payment confirmed"), style = MaterialTheme.typography.titleMedium,
                    color = p.accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("qr_payment_paid"))
                Text("${detail.paymentId} • ${detail.amountLabel}")
                if (detail.paidAt.isNotBlank()) Text(tr("Xác nhận lúc ${detail.paidAt}", "Confirmed at ${detail.paidAt}"), color = p.muted)
                if (detail.period.isNotBlank()) Text(tr("Thời hạn: ${detail.period}", "Valid: ${detail.period}"), color = p.muted)
            }
            detail.isCancelled -> QrCard {
                Text(tr("Hoá đơn đã huỷ", "Invoice cancelled"), style = MaterialTheme.typography.titleMedium)
                Text("${detail.paymentId} • ${detail.amountLabel}", color = p.muted)
            }
            else -> {
                Surface(shape = RoundedCornerShape(24.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (detail.qrPayload.isNotBlank()) {
                            QrCodeImage(detail.qrPayload, tr("Mã VietQR", "VietQR code"),
                                Modifier.fillMaxWidth().aspectRatio(1f).testTag("qr_payment_code"))
                        }
                        Text(detail.accountName, color = Color.Black, fontWeight = FontWeight.SemiBold)
                        Text("${detail.bankName} • ${detail.accountNo}", color = Color.DarkGray)
                    }
                }
                Text(tr("Quét bằng app ngân hàng hoặc MoMo. Giữ nguyên số tiền và nội dung chuyển khoản.",
                    "Scan with any banking app or MoMo. Keep the amount and transfer note unchanged."),
                    color = p.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

                QrCard {
                    CopyRow(tr("Số tiền", "Amount"), detail.amountLabel, null, copy)
                    CopyRow(tr("Ngân hàng", "Bank"), detail.bankName, null, copy)
                    CopyRow(tr("Số tài khoản", "Account no."), detail.accountNo, detail.accountNo, copy)
                    CopyRow(tr("Chủ tài khoản", "Account name"), detail.accountName, null, copy)
                    CopyRow(tr("Nội dung", "Transfer note"), detail.transferContent, detail.transferContent, copy)
                }

                QrCard(border = p.accent) {
                    Text(tr("Đang chờ xác nhận", "Awaiting confirmation"), fontWeight = FontWeight.SemiBold, color = p.accent)
                    Text(tr("Sau khi KgyMs nhận được tiền, màn này tự chuyển sang \"Thanh toán thành công\" và gói của bạn được kích hoạt.",
                        "Once KgyMs receives the transfer, this screen switches to \"Payment confirmed\" and your package activates."),
                        color = p.muted)
                }

                OutlinedButton(
                    onClick = onRequestCancel, enabled = !isCancelling,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = p.danger),
                    border = BorderStroke(1.dp, p.danger),
                    modifier = Modifier.fillMaxWidth().testTag("qr_payment_cancel")
                ) { Text(tr("Huỷ hoá đơn", "Cancel invoice")) }
            }
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = onDismissCancel,
            title = { Text(tr("Huỷ hoá đơn ${detail.paymentId}?", "Cancel invoice ${detail.paymentId}?")) },
            text = { Text(tr("Chỉ huỷ khi bạn chưa chuyển khoản. Nếu đã chuyển, hãy chờ KgyMs xác nhận.",
                "Only cancel if you haven't transferred yet. If you have, please wait for KgyMs to confirm.")) },
            confirmButton = {
                TextButton(onClick = onConfirmCancel, enabled = !isCancelling) {
                    Text(if (isCancelling) tr("Đang huỷ…", "Cancelling…") else tr("Huỷ hoá đơn", "Cancel invoice"), color = p.danger)
                }
            },
            dismissButton = { TextButton(onClick = onDismissCancel, enabled = !isCancelling) { Text(tr("Giữ lại", "Keep")) } }
        )
    }
}

@Composable
private fun CopyRow(label: String, value: String, copyValue: String?, onCopy: (String) -> Unit) {
    val p = LocalProfilePalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = p.muted, style = MaterialTheme.typography.labelMedium)
            Text(value, fontWeight = FontWeight.SemiBold)
        }
        if (copyValue != null) {
            TextButton(onClick = { onCopy(copyValue) }) { Text(tr("Sao chép", "Copy")) }
        }
    }
}
