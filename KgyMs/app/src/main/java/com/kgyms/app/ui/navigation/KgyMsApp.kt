
package com.kgyms.app.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kgyms.app.data.model.User
import com.kgyms.app.data.repository.AuthRepository
import com.kgyms.app.ui.admin.AdminNavHost
import com.kgyms.app.ui.auth.*
import com.kgyms.app.ui.membership.MembershipScreen
import com.kgyms.app.ui.profile.MockMemberProfile
import com.kgyms.app.ui.profile.ProfileFlow

@Composable
fun KgyMsApp(
    navController: NavHostController = rememberNavController(),
    debugToolsEnabled: Boolean? = null
) {
    val authRepository = remember { AuthRepository() }

    var memberName by rememberSaveable {
        mutableStateOf(MockMemberProfile.name)
    }
    var memberEmail by rememberSaveable {
        mutableStateOf(MockMemberProfile.email)
    }

    var gender by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var age by rememberSaveable { mutableIntStateOf(28) }
    var weight by rememberSaveable { mutableIntStateOf(75) }
    var weightUnit by rememberSaveable { mutableStateOf("KG") }
    var height by rememberSaveable { mutableIntStateOf(165) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }

    fun resetDraft() {
        memberName = MockMemberProfile.name
        memberEmail = MockMemberProfile.email
        gender = null
        age = 28
        weight = 75
        weightUnit = "KG"
        height = 165
    }

    fun replaceStack(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.id) {
                inclusive = true
            }
            launchSingleTop = true
        }
    }

    fun logout() {
        authRepository.logout()
        resetDraft()
        replaceStack("login")
    }

    fun startSignUp() {
        resetDraft()
        navController.navigate("signup") {
            launchSingleTop = true
        }
    }

    fun backToAuth() {
        if (!navController.popBackStack()) {
            replaceStack("welcome")
        }
    }

    fun handleLoginSuccess(user: User) {
        resetDraft()
        memberName = user.fullName
        memberEmail = user.email

        if (!user.isActive) {
            authRepository.logout()
            notice = "Tài khoản không hoạt động. Vui lòng liên hệ quản trị viên."
            replaceStack("login")
            return
        }

        if (user.isAdmin) {
            replaceStack("admin")
        } else {
            replaceStack("member")
        }
    }

    NavHost(
        navController = navController,
        startDestination = "membership" // Tạm thời để mở thẳng vào màn hình Quản lý thẻ để bạn test nhanh
    ) {
        composable("membership") {
            RoutePage("membership") {
                MembershipScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }

        composable("splash") {
            RoutePage("splash") {
                SplashScreen {
                    navController.navigate("welcome") {
                        popUpTo("splash") {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                }
            }
        }

        composable("welcome") {
            RoutePage("welcome", authInsets = true) {
                WelcomeScreen(
                    onNavigateToLogin = {
                        navController.navigate("login") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToSignUp = ::startSignUp
                )
            }
        }

        composable("login") {
            RoutePage("login", authInsets = true) {
                LoginScreen(
                    onNavigateBack = ::backToAuth,
                    onLoginSuccess = ::handleLoginSuccess,
                    onNavigateToSignUp = ::startSignUp,
                    onNavigateToForgotPassword = {
                        navController.navigate("forgot-password") {
                            launchSingleTop = true
                        }
                    }
                )
            }
        }

        // Màn hình quên mật khẩu mới
        composable("forgot-password") {
            RoutePage("forgot-password", authInsets = true) {
                ForgotPasswordScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }

        composable("signup") {
            RoutePage("signup", authInsets = true) {
                SignUpScreen(
                    onNavigateBack = ::backToAuth,
                    onNavigateToGenderSelection = {
                        navController.navigate("gender") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToLogin = ::logout,
                    onProfileDraft = { name, email ->
                        memberName = name
                        memberEmail = email
                    }
                )
            }
        }

        composable("gender") {
            RoutePage("gender", authInsets = true) {
                GenderSelectionScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    initialGender = gender,
                    onGenderChanged = {
                        gender = it
                    },
                    onContinue = {
                        gender = it
                        navController.navigate("age") {
                            launchSingleTop = true
                        }
                    }
                )
            }
        }

        composable("age") {
            RoutePage("age", authInsets = true) {
                AgeScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    initialAge = age,
                    onAgeChanged = {
                        age = it
                    },
                    onContinue = {
                        age = it
                        navController.navigate("weight") {
                            launchSingleTop = true
                        }
                    }
                )
            }
        }

        composable("weight") {
            RoutePage("weight", authInsets = true) {
                WeightScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    initialWeight = weight,
                    initialUnit = weightUnit,
                    onWeightChanged = { value, unit ->
                        weight = value
                        weightUnit = unit
                    },
                    onContinue = { value, unit ->
                        weight = value
                        weightUnit = unit
                        navController.navigate("height") {
                            launchSingleTop = true
                        }
                    }
                )
            }
        }

        composable("height") {
            RoutePage("height", authInsets = true) {
                HeightScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    initialHeight = height,
                    onHeightChanged = {
                        height = it
                    },
                    onContinue = { value ->
                        height = value

                        val validWeight = if (weightUnit == "KG") {
                            weight in 30..200
                        } else {
                            weight in 66..440
                        }

                        if (
                            gender != null &&
                            age in 10..100 &&
                            height in 100..250 &&
                            validWeight
                        ) {
                            replaceStack("member")
                        } else {
                            notice = "Vui lòng kiểm tra lại thông tin hồ sơ."
                        }
                    }
                )
            }
        }

        composable("admin") {
            RoutePage("admin") {
                AdminNavHost(
                    onLogout = ::logout
                )
            }
        }

        composable("member") {
            RoutePage("member") {
                ProfileFlow(
                    onLogout = ::logout,
                    initialProfile = MockMemberProfile.copy(
                        name = memberName,
                        email = memberEmail
                    )
                )
            }
        }
    }

    notice?.let { message ->
        AlertDialog(
            onDismissRequest = {
                notice = null
            },
            title = {
                Text("KgyMs")
            },
            text = {
                Text(message)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        notice = null
                    },
                    modifier = Modifier.testTag("app_notice_ok")
                ) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun RoutePage(
    route: String,
    authInsets: Boolean = false,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (authInsets) {
                    Modifier.safeDrawingPadding().imePadding()
                } else {
                    Modifier
                }
            )
            .testTag("route_$route")
    ) {
        content()
    }
}
