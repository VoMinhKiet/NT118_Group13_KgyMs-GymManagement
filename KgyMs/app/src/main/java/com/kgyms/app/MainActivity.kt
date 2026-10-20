
package com.kgyms.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

import com.kgyms.app.ui.theme.KgyMsTheme
import com.kgyms.app.ui.admin.AdminNavHost
import com.kgyms.app.ui.profile.ProfileFlow

import com.kgyms.app.ui.auth.LoginScreen
import com.kgyms.app.ui.auth.SignUpScreen
import com.kgyms.app.ui.auth.SplashScreen
import com.kgyms.app.ui.auth.WelcomeScreen
import com.kgyms.app.ui.auth.GenderSelectionScreen
import com.kgyms.app.ui.auth.AgeScreen
import com.kgyms.app.ui.auth.WeightScreen
import com.kgyms.app.ui.auth.HeightScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            KgyMsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    KgyMsApp()
                }
            }
        }
    }
}

@Composable
fun KgyMsApp() {

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "splash"
    ) {

        composable("splash") {
            SplashScreen(
                onNavigateToWelcome = {
                    navController.navigate("welcome") {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            )
        }

        composable("welcome") {
            WelcomeScreen(
                onNavigateToLogin = {
                    navController.navigate("login")
                },
                onNavigateToSignUp = {
                    navController.navigate("signup")
                }
            )
        }

        composable("login") {
            LoginScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onLoginSuccess = {
                    // TEST ONLY: Firebase role routing sẽ thay chỗ này.
                    navController.navigate("role_preview") {
                        popUpTo("login") { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateToSignUp = {
                    navController.navigate("signup") {
                        popUpTo("welcome")
                    }
                },
                onNavigateToForgotPassword = {
                    // Chưa tích hợp Forgot Password.
                }
            )
        }

        composable("signup") {
            SignUpScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToGenderSelection = {
                    navController.navigate("gender")
                },
                onNavigateToLogin = {
                    navController.navigate("login") {
                        popUpTo("welcome")
                    }
                }
            )
        }

        composable("gender") {
            GenderSelectionScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onContinue = { _ ->
                    navController.navigate("age")
                }
            )
        }

        composable("age") {
            AgeScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onContinue = { _ ->
                    navController.navigate("weight")
                }
            )
        }

        composable("weight") {
            WeightScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onContinue = { _, _ ->
                    navController.navigate("height")
                }
            )
        }

        composable("height") {
            HeightScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onContinue = { _ ->
                    // Tài khoản đăng ký mới mặc định là Member.
                    navController.navigate("member") {
                        popUpTo("welcome") { inclusive = true }
                    }
                }
            )
        }

        // Màn chọn role chỉ phục vụ kiểm thử tích hợp.
        composable("role_preview") {
            RolePreviewScreen(
                onAdmin = {
                    navController.navigate("admin")
                },
                onMember = {
                    navController.navigate("member")
                },
                onBackToLogin = {
                    navController.navigate("login") {
                        popUpTo("role_preview") {
                            inclusive = true
                        }
                    }
                }
            )
        }

        // Giao diện Admin của thành viên 2.
        composable("admin") {
            AdminNavHost()
        }

        // Giao diện Profile Member của bạn.
        composable("member") {
            ProfileFlow()
        }
    }
}

@Composable
private fun RolePreviewScreen(
    onAdmin: () -> Unit,
    onMember: () -> Unit,
    onBackToLogin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "KgyMs - Integration Test",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Chọn khu vực để kiểm tra giao diện. " +
                "Đây chưa phải phân quyền tài khoản thật.",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onAdmin,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Vào Admin Dashboard")
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onMember,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Vào Member Profile")
        }

        Spacer(Modifier.height(20.dp))

        TextButton(onClick = onBackToLogin) {
            Text("Quay lại đăng nhập")
        }
    }
}
