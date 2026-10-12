package com.kgyms.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kgyms.app.ui.theme.KgyMsTheme

import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kgyms.app.ui.screens.LoginScreen
import com.kgyms.app.ui.screens.SignUpScreen
import com.kgyms.app.ui.screens.SplashScreen
import com.kgyms.app.ui.screens.WelcomeScreen
import com.kgyms.app.ui.screens.GenderSelectionScreen
import com.kgyms.app.ui.screens.AgeScreen
import com.kgyms.app.ui.screens.WeightScreen
import com.kgyms.app.ui.screens.HeightScreen

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

    NavHost(navController = navController, startDestination = "splash") {
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
                onNavigateToLogin = { navController.navigate("login") },
                onNavigateToSignUp = { navController.navigate("signup") }
            )
        }
        composable("login") {
            LoginScreen(
                onNavigateBack = { navController.popBackStack() },
                onLoginSuccess = { /* TODO: Navigate to Home */ },
                onNavigateToSignUp = { 
                    navController.navigate("signup") {
                        popUpTo("welcome")
                    }
                },
                onNavigateToForgotPassword = { /* TODO */ }
            )
        }
        composable("signup") {
            SignUpScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToGenderSelection = { navController.navigate("gender") },
                onNavigateToLogin = { 
                    navController.navigate("login") {
                        popUpTo("welcome")
                    }
                }
            )
        }
        composable("gender") {
            GenderSelectionScreen(
                onNavigateBack = { navController.popBackStack() },
                onContinue = { gender ->
                    navController.navigate("age")
                }
            )
        }
        composable("age") {
            AgeScreen(
                onNavigateBack = { navController.popBackStack() },
                onContinue = { age ->
                    navController.navigate("weight")
                }
            )
        }
        composable("weight") {
            WeightScreen(
                onNavigateBack = { navController.popBackStack() },
                onContinue = { weight, unit ->
                    navController.navigate("height")
                }
            )
        }
        composable("height") {
            HeightScreen(
                onNavigateBack = { navController.popBackStack() },
                onContinue = { height ->
                    // TODO: Navigate to next step or dashboard
                }
            )
        }
    }
}