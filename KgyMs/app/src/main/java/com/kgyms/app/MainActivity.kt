
package com.kgyms.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

import com.kgyms.app.ui.theme.KgyMsTheme
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
                    // Tạm vào Member để test Auth + Profile.
                    // Role ADMIN/MEMBER sẽ tích hợp sau.
                    navController.navigate("member") {
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
                    // TODO
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
                    navController.navigate("member") {
                        popUpTo("welcome") { inclusive = true }
                    }
                }
            )
        }

        composable("member") {
            ProfileFlow()
        }
    }
}
