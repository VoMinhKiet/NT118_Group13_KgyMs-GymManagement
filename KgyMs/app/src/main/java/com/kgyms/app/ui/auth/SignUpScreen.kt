
package com.kgyms.app.ui.auth

import com.kgyms.app.ui.components.LanguageSwitch
import com.kgyms.app.ui.localization.LocalAppLanguage
import com.kgyms.app.ui.localization.appText
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.kgyms.app.data.model.User
import com.kgyms.app.R
import com.kgyms.app.ui.components.KgyMsButton
import com.kgyms.app.ui.components.KgyMsTextField
import kotlinx.coroutines.launch
import com.kgyms.app.viewmodel.auth.AuthState
import com.kgyms.app.viewmodel.auth.AuthViewModel

@Composable
fun SignUpScreen(
    onNavigateBack: () -> Unit,
    onNavigateToGenderSelection: () -> Unit,
    onNavigateToLogin: () -> Unit,
    authViewModel: AuthViewModel = viewModel(),
    mockEnabled: Boolean = false,
    onProfileDraft: (String, String) -> Unit = { _, _ -> },
    // Nếu được cung cấp, host tự quyết định điều hướng (onboarding/member/admin)
    // dựa trên hồ sơ trả về, áp dụng chung cho email/password và Google.
    onAuthSuccess: ((User) -> Unit)? = null
) {
    var fullName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var googleLoading by remember { mutableStateOf(false) }

    val authState by authViewModel.authState.collectAsState()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val credentialManager = remember(activity) {
        activity?.let { CredentialManager.create(it) }
    }
    val coroutineScope = rememberCoroutineScope()
    val language = LocalAppLanguage.current.language
    val signedInMessage = appText("Đăng nhập thành công.", "Signed in successfully.")
    val createdMessage = appText("Tạo tài khoản thành công. Hãy hoàn tất hồ sơ.", "Account created. Please complete your profile.")
    val googleStartError = appText("Không thể mở đăng nhập Google. Vui lòng thử lại.", "Unable to start Google sign-in. Please try again.")
    val googleInvalidCredential = appText("Không nhận được thông tin tài khoản Google hợp lệ.", "Did not receive valid Google account information.")
    val googleCancelled = appText("Đăng ký Google đã bị hủy hoặc không thể hoàn tất.", "Google sign-up was cancelled or could not be completed.")
    val googleFailed = appText("Đăng ký bằng Google thất bại. Vui lòng thử lại.", "Google sign-up failed. Please try again.")

    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthState.Success -> {
                onProfileDraft(
                    state.user.fullName,
                    state.user.email
                )
                authViewModel.resetState()

                Toast.makeText(
                    context,
                    if (state.user.hasCompletedOnboarding) {
                        signedInMessage
                    } else {
                        createdMessage
                    },
                    Toast.LENGTH_LONG
                ).show()

                if (onAuthSuccess != null) {
                    onAuthSuccess(state.user)
                } else {
                    onNavigateToGenderSelection()
                }
            }

            is AuthState.Error -> {
                Toast.makeText(
                    context,
                    state.error.message(language),
                    Toast.LENGTH_LONG
                ).show()
                authViewModel.resetState()
            }

            else -> Unit
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            TextButton(
                onClick = {
                    authViewModel.resetState()
                    onNavigateBack()
                },
                modifier = Modifier.testTag("auth_back"),
                enabled = authState !is AuthState.Loading && !googleLoading
            ) {
                Text("←")
            }

            // Chọn ngôn ngữ EN/VI; lựa chọn được lưu và dùng cho toàn app.
            LanguageSwitch(modifier = Modifier.align(Alignment.CenterEnd))
        }

        Text(
            text = appText("Tạo tài khoản", "Create Your Account"),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = appText("Tham gia KgyMs và bắt đầu thay đổi bản thân", "Join KgyMs and start your transformation"),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )

        KgyMsTextField(
            value = fullName,
            onValueChange = { fullName = it },
            placeholder = appText("Họ và tên", "Full Name"),
            modifier = Modifier.testTag("signup_name"),
            enabled = authState !is AuthState.Loading && !googleLoading
        )

        Spacer(Modifier.height(16.dp))

        KgyMsTextField(
            value = email,
            onValueChange = { email = it },
            placeholder = "Email",
            modifier = Modifier.testTag("signup_email"),
            enabled = authState !is AuthState.Loading && !googleLoading
        )

        Spacer(Modifier.height(16.dp))

        KgyMsTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = appText("Mật khẩu", "Password"),
            modifier = Modifier.testTag("signup_password"),
            enabled = authState !is AuthState.Loading && !googleLoading,
            visualTransformation = PasswordVisualTransformation()
        )

        Spacer(Modifier.height(16.dp))

        KgyMsTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            placeholder = appText("Xác nhận mật khẩu", "Confirm Password"),
            modifier = Modifier.testTag("signup_confirm"),
            enabled = authState !is AuthState.Loading && !googleLoading,
            visualTransformation = PasswordVisualTransformation()
        )

        Spacer(Modifier.height(24.dp))

        if (authState is AuthState.Loading || googleLoading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            KgyMsButton(
                text = appText("Đăng ký", "Sign Up"),
                onClick = {
                    authViewModel.register(
                        fullName = fullName,
                        email = email,
                        password = password,
                        confirmPassword = confirmPassword
                    )
                }
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = appText("hoặc tiếp tục với", "or continue with"),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 13.sp
            )

            Spacer(Modifier.height(16.dp))

            KgyMsButton(
                text = appText("Đăng ký với Google", "Sign up with Google"),
                onClick = {
                    val currentActivity = activity
                    val manager = credentialManager

                    if (currentActivity == null || manager == null) {
                        Toast.makeText(
                            context,
                            googleStartError,
                            Toast.LENGTH_LONG
                        ).show()
                        return@KgyMsButton
                    }

                    coroutineScope.launch {
                        googleLoading = true
                        try {
                            val googleIdOption = GetGoogleIdOption.Builder()
                                .setFilterByAuthorizedAccounts(false)
                                .setServerClientId(
                                    currentActivity.getString(
                                        R.string.default_web_client_id
                                    )
                                )
                                .setAutoSelectEnabled(false)
                                .build()

                            val request = GetCredentialRequest.Builder()
                                .addCredentialOption(googleIdOption)
                                .build()

                            val result = manager.getCredential(
                                context = currentActivity,
                                request = request
                            )

                            val credential = result.credential

                            if (
                                credential is CustomCredential &&
                                credential.type ==
                                GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                            ) {
                                val googleCredential =
                                    GoogleIdTokenCredential.createFrom(
                                        credential.data
                                    )

                                authViewModel.loginWithGoogle(
                                    googleCredential.idToken,
                                    mockEnabled
                                )
                            } else {
                                Toast.makeText(
                                    context,
                                    googleInvalidCredential,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } catch (e: GetCredentialException) {
                            android.util.Log.e(
                                "GoogleSignUp",
                                "Credential Manager error: ${e.type}",
                                e
                            )
                            Toast.makeText(
                                context,
                                googleCancelled,
                                Toast.LENGTH_LONG
                            ).show()
                        } catch (e: Exception) {
                            android.util.Log.e(
                                "GoogleSignUp",
                                "Google sign-up failed",
                                e
                            )
                            Toast.makeText(
                                context,
                                googleFailed,
                                Toast.LENGTH_LONG
                            ).show()
                        } finally {
                            googleLoading = false
                        }
                    }
                },
                isOutlined = true
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = appText("Đã có tài khoản?", "Already have an account?"),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )

        TextButton(
            onClick = onNavigateToLogin,
            enabled = authState !is AuthState.Loading && !googleLoading
        ) {
            Text(
                text = appText("Đăng nhập", "Sign In"),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun Context.findActivity(): Activity? {
    var currentContext = this

    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) return currentContext
        currentContext = currentContext.baseContext
    }

    return currentContext as? Activity
}
