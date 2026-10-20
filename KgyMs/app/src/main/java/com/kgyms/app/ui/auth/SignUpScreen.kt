
package com.kgyms.app.ui.auth

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
import com.kgyms.app.ui.components.KgyMsButton
import com.kgyms.app.ui.components.KgyMsTextField

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

    val authState by authViewModel.authState.collectAsState()
    val context = LocalContext.current

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
                modifier = Modifier.testTag("auth_back")
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
            enabled = authState !is AuthState.Loading
        )

        Spacer(Modifier.height(16.dp))

        KgyMsTextField(
            value = email,
            onValueChange = { email = it },
            placeholder = "Email",
            modifier = Modifier.testTag("signup_email"),
            enabled = authState !is AuthState.Loading
        )

        Spacer(Modifier.height(16.dp))

        KgyMsTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = "Password",
            modifier = Modifier.testTag("signup_password"),
            enabled = authState !is AuthState.Loading,
            visualTransformation = PasswordVisualTransformation()
        )

        Spacer(Modifier.height(16.dp))

        KgyMsTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            placeholder = "Confirm Password",
            modifier = Modifier.testTag("signup_confirm"),
            enabled = authState !is AuthState.Loading,
            visualTransformation = PasswordVisualTransformation()
        )

        Spacer(Modifier.height(32.dp))

        if (authState is AuthState.Loading) {
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
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "Already have an account?",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )

        TextButton(
            onClick = onNavigateToLogin,
            enabled = authState !is AuthState.Loading
        ) {
            Text(
                text = "Sign In",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
