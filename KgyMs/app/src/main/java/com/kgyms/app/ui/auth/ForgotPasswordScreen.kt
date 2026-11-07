
package com.kgyms.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgyms.app.ui.components.KgyMsButton
import com.kgyms.app.ui.components.KgyMsTextField

@Composable
fun ForgotPasswordScreen(
    onNavigateBack: () -> Unit,
    forgotPasswordViewModel: ForgotPasswordViewModel = viewModel()
) {
    var email by rememberSaveable {
        mutableStateOf("")
    }

    val state by forgotPasswordViewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TextButton(
            onClick = {
                forgotPasswordViewModel.resetState()
                onNavigateBack()
            },
            modifier = Modifier.align(Alignment.Start)
        ) {
            Text(
                text = "← Back",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "KgyMs",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(28.dp))

        Text(
            text = "Forgot Password?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Enter your registered email address. " +
                    "We will send you a link to reset your password.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Spacer(Modifier.height(28.dp))

        KgyMsTextField(
            value = email,
            onValueChange = { email = it },
            placeholder = "Email address",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email
            ),
            enabled = state !is ForgotPasswordState.Loading
        )

        Spacer(Modifier.height(20.dp))

        KgyMsButton(
            text = "Send Reset Link",
            onClick = {
                forgotPasswordViewModel.sendResetEmail(email)
            },
            enabled = state !is ForgotPasswordState.Loading
        )

        if (state is ForgotPasswordState.Loading) {
            Spacer(Modifier.height(20.dp))

            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary
            )
        }

        when (val currentState = state) {
            is ForgotPasswordState.EmailSent -> {
                Spacer(Modifier.height(20.dp))

                Text(
                    text = "If an account exists for this email, " +
                            "a password reset link has been sent. " +
                            "Please check your inbox and spam folder.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            is ForgotPasswordState.Error -> {
                Spacer(Modifier.height(20.dp))

                Text(
                    text = currentState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            else -> Unit
        }
    }
}
