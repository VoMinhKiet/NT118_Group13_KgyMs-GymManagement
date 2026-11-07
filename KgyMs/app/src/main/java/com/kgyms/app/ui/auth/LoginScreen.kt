
package com.kgyms.app.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.text.input.VisualTransformation
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
fun LoginScreen(
    onNavigateBack: () -> Unit,
    onLoginSuccess: (com.kgyms.app.data.model.User) -> Unit,
    onNavigateToSignUp: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    authViewModel: AuthViewModel = viewModel(),
    mockEnabled: Boolean = false
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var rememberMe by rememberSaveable { mutableStateOf(false) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var googleLoading by remember { mutableStateOf(false) }

    val authState by authViewModel.authState.collectAsState()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val coroutineScope = rememberCoroutineScope()

    val credentialManager = remember(activity) {
        activity?.let { CredentialManager.create(it) }
    }

    val isBusy = authState is AuthState.Loading || googleLoading

    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthState.Success -> {
                authViewModel.resetState()
                onLoginSuccess(state.user)
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
                .height(48.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            TextButton(
                onClick = {
                    authViewModel.resetState()
                    onNavigateBack()
                },
                enabled = !isBusy,
                modifier = Modifier.testTag("auth_back")
            ) {
                Text("←")
            }
        }

        Text(
            text = "KgyMs",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(32.dp))

        Text(
            text = "Welcome Back",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = "Sign in to continue your fitness journey",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )

        KgyMsTextField(
            value = email,
            onValueChange = { email = it },
            placeholder = "Email",
            modifier = Modifier.testTag("login_email"),
            enabled = !isBusy
        )

        Spacer(Modifier.height(16.dp))

        KgyMsTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = "Password",
            modifier = Modifier.testTag("login_password"),
            enabled = !isBusy,
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = rememberMe,
                    onCheckedChange = { rememberMe = it },
                    enabled = !isBusy,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary
                    )
                )

                Text(
                    text = "Remember me",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                )
            }

            TextButton(
                onClick = onNavigateToForgotPassword,
                enabled = !isBusy
            ) {
                Text(
                    text = "Forgot password?",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (authState is AuthState.Loading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            KgyMsButton(
                text = "Sign In",
                onClick = {
                    authViewModel.login(
                        email = email,
                        password = password
                    )
                },
                enabled = !googleLoading
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "or continue with",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )

        Spacer(Modifier.height(16.dp))

        if (isBusy) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            KgyMsButton(
                text = "Continue with Google",
                onClick = {
                    if (activity == null || credentialManager == null) {
                        Toast.makeText(
                            context,
                            "Unable to start Google sign-in.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        googleLoading = true

                        coroutineScope.launch {
                            try {
                                val googleIdOption =
                                    GetGoogleIdOption.Builder()
                                        .setFilterByAuthorizedAccounts(false)
                                        .setServerClientId(
                                            activity.getString(
                                                R.string.default_web_client_id
                                            )
                                        )
                                        .setAutoSelectEnabled(false)
                                        .build()

                                val request =
                                    GetCredentialRequest.Builder()
                                        .addCredentialOption(googleIdOption)
                                        .build()

                                val result = credentialManager.getCredential(
                                    context = activity,
                                    request = request
                                )

                                val credential = result.credential

                                if (
                                    credential is CustomCredential &&
                                    credential.type ==
                                    GoogleIdTokenCredential
                                        .TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                ) {
                                    val googleIdToken =
                                        GoogleIdTokenCredential
                                            .createFrom(credential.data)
                                            .idToken

                                    authViewModel.loginWithGoogle(
                                        idToken = googleIdToken
                                    )
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Google did not return a valid sign-in credential.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } catch (exception: GetCredentialException) {
                                Toast.makeText(
                                    context,
                                    "Google sign-in was cancelled or could not be completed.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } catch (exception: Exception) {
                                Toast.makeText(
                                    context,
                                    exception.message
                                        ?: "Unable to sign in with Google.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } finally {
                                googleLoading = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colorScheme.surface,
                isOutlined = true,
                enabled = !isBusy
            )
        }

        Spacer(Modifier.height(32.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Don't have an account? ",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )

            TextButton(
                onClick = onNavigateToSignUp,
                enabled = !isBusy
            ) {
                Text(
                    text = "Sign Up",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var currentContext = this

    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }

        currentContext = currentContext.baseContext
    }

    return currentContext as? Activity
}
