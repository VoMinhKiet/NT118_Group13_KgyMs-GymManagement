package com.kgyms.app.ui.auth

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgyms.app.data.model.User
import com.kgyms.app.ui.components.KgyMsButton
import com.kgyms.app.ui.localization.LocalAppLanguage
import com.kgyms.app.ui.localization.appText
import com.kgyms.app.viewmodel.auth.VerifyEmailError
import com.kgyms.app.viewmodel.auth.VerifyEmailEvent
import com.kgyms.app.viewmodel.auth.VerifyEmailViewModel

/**
 * Màn chờ xác nhận email sau khi đăng ký (hoặc khi đăng nhập tài khoản chưa xác nhận).
 * Người dùng mở email, bấm link, quay lại app; app tự kiểm tra khi được mở lại.
 */
@Composable
fun VerifyEmailScreen(
    email: String,
    emailSent: Boolean?,
    onVerified: (User) -> Unit,
    onBackToLogin: () -> Unit,
    onUseAnotherEmail: () -> Unit,
    viewModel: VerifyEmailViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val language = LocalAppLanguage.current.language
    // Nhớ hành động thoát mà người dùng chọn để điều hướng đúng sau khi đăng xuất xong.
    var leavingToSignUp by remember { mutableStateOf(false) }

    val notVerifiedMessage = appText(
        "Email chưa được xác nhận. Hãy mở email và bấm vào link xác nhận.",
        "Your email is not verified yet. Open the email and tap the verification link."
    )
    val resentMessage = appText("Đã gửi lại email xác nhận.", "Verification email sent again.")
    val initialFailedMessage = appText(
        "Chưa gửi được email xác nhận. Hãy bấm \"Gửi lại email\".",
        "Could not send the verification email. Tap \"Resend email\"."
    )
    val verifiedMessage = appText("Xác nhận email thành công!", "Email verified!")

    LaunchedEffect(Unit) { viewModel.start(email, emailSent) }

    // Quay lại từ ứng dụng email: tự kiểm tra (bỏ qua lần resume đầu tiên khi vừa mở màn).
    var firstResume by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        if (firstResume) firstResume = false else viewModel.checkVerified(silent = true)
        onPauseOrDispose { }
    }

    LaunchedEffect(uiState.event) {
        val event = uiState.event ?: return@LaunchedEffect
        viewModel.consumeEvent()
        when (event) {
            is VerifyEmailEvent.Verified -> {
                Toast.makeText(context, verifiedMessage, Toast.LENGTH_SHORT).show()
                onVerified(event.user)
            }
            VerifyEmailEvent.NotVerifiedYet ->
                Toast.makeText(context, notVerifiedMessage, Toast.LENGTH_LONG).show()
            VerifyEmailEvent.EmailResent ->
                Toast.makeText(context, resentMessage, Toast.LENGTH_SHORT).show()
            VerifyEmailEvent.InitialSendFailed ->
                Toast.makeText(context, initialFailedMessage, Toast.LENGTH_LONG).show()
            VerifyEmailEvent.SignedOut ->
                if (leavingToSignUp) onUseAnotherEmail() else onBackToLogin()
            is VerifyEmailEvent.Error -> {
                Toast.makeText(context, event.error.message(language), Toast.LENGTH_LONG).show()
                // Hai lỗi này đã làm mất phiên: đưa người dùng về đăng nhập.
                if (event.error == VerifyEmailError.AccountInactive ||
                    event.error == VerifyEmailError.SessionExpired
                ) viewModel.backToLogin()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AuthTopBar(
            onBack = viewModel::backToLogin,
            enabled = !uiState.isBusy
        )

        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(88.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Email,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = appText("Xác nhận email của bạn", "Verify your email"),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        val sentTo = appText("Chúng tôi đã gửi link xác nhận tới ", "We sent a verification link to ")
        val instruction = appText(
            ". Hãy mở email và bấm vào link, sau đó quay lại đây.",
            ". Open the email and tap the link, then come back here."
        )
        Text(
            text = buildAnnotatedString {
                append(sentTo)
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)) {
                    append(uiState.email.ifBlank { email })
                }
                append(instruction)
            },
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("verify_email_address")
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = appText(
                "Không thấy email? Hãy kiểm tra cả thư mục Spam / Quảng cáo.",
                "Can't find it? Check your Spam / Promotions folder too."
            ),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        if (uiState.isChecking) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            KgyMsButton(
                text = appText("Tôi đã xác nhận", "I've verified"),
                onClick = { viewModel.checkVerified() },
                modifier = Modifier.testTag("verify_email_check"),
                enabled = !uiState.isBusy
            )
        }

        Spacer(Modifier.height(16.dp))

        val resendLabel = if (uiState.resendCooldown > 0) {
            appText("Gửi lại email (${uiState.resendCooldown}s)", "Resend email (${uiState.resendCooldown}s)")
        } else {
            appText("Gửi lại email", "Resend email")
        }
        KgyMsButton(
            text = resendLabel,
            onClick = { viewModel.resend(language) },
            modifier = Modifier.testTag("verify_email_resend"),
            backgroundColor = MaterialTheme.colorScheme.surface,
            isOutlined = true,
            enabled = !uiState.isBusy && uiState.resendCooldown == 0
        )

        Spacer(Modifier.height(24.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = appText("Nhập sai email?", "Wrong email?"),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
            TextButton(
                onClick = {
                    leavingToSignUp = true
                    viewModel.useAnotherEmail()
                },
                enabled = !uiState.isBusy,
                modifier = Modifier.testTag("verify_email_change")
            ) {
                Text(
                    text = appText("Đăng ký bằng email khác", "Sign up with another email"),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
