
package com.kgyms.app.ui.auth

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
import com.kgyms.app.R
import com.kgyms.app.ui.components.KgyMsButton
import com.kgyms.app.ui.components.KgyMsTextField
import kotlinx.coroutines.launch

@Composable
fun SignUpScreen(
    onNavigateBack: () -> Unit,
    onNavigateToGenderSelection: () -> Unit,
    onNavigateToLogin: () -> Unit,
    authViewModel: AuthViewModel = viewModel(),
    mockEnabled: Boolean = false,
    onProfileDraft: (String, String) -> Unit = { _, _ -> }
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
                    "Tạo tài khoản thành công. Hãy hoàn tất hồ sơ.",
                    Toast.LENGTH_LONG
                ).show()

                onNavigateToGenderSelection()
            }

            is AuthState.Error -> {
                Toast.makeText(
                    context,
                    state.message,
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
                .height(32.dp),
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
        }

        Text(
            text = "Create Your Account",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = "Join KgyMs and start your transformation",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )

        KgyMsTextField(
            value = fullName,
            onValueChange = { fullName = it },
            placeholder = "Full Name",
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
            placeholder = "Password",
            modifier = Modifier.testTag("signup_password"),
            enabled = authState !is AuthState.Loading && !googleLoading,
            visualTransformation = PasswordVisualTransformation()
        )

        Spacer(Modifier.height(16.dp))

        KgyMsTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            placeholder = "Confirm Password",
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
                text = "Sign Up",
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
                text = "or continue with",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 13.sp
            )

            Spacer(Modifier.height(16.dp))

            KgyMsButton(
                text = "Sign up with Google",
                onClick = {
                    val currentActivity = activity
                    val manager = credentialManager

                    if (currentActivity == null || manager == null) {
                        Toast.makeText(
                            context,
                            "Không thể mở Google Sign-In. Vui lòng thử lại.",
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
                                    "Không nhận được thông tin tài khoản Google hợp lệ.",
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
                                "Không thể đăng ký bằng Google: ${e.type}",
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
                                "Đăng ký Google thất bại: ${
                                    e.localizedMessage ?: "Lỗi không xác định"
                                }",
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
            text = "Already have an account?",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )

        TextButton(
            onClick = onNavigateToLogin,
            enabled = authState !is AuthState.Loading && !googleLoading
        ) {
            Text(
                text = "Sign In",
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
