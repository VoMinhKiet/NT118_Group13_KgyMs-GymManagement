
package com.kgyms.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kgyms.app.data.model.User
import com.kgyms.app.data.repository.AuthRepository
import com.kgyms.app.ui.admin.AdminNavHost
import com.kgyms.app.ui.auth.*
import com.kgyms.app.ui.profile.MockMemberProfile
import com.kgyms.app.ui.profile.ProfileFlow
import kotlinx.coroutines.launch

@Composable
fun KgyMsApp(
    navController: NavHostController = rememberNavController(),
    debugToolsEnabled: Boolean? = null
) {
    val authRepository = remember { AuthRepository() }
    val coroutineScope = rememberCoroutineScope()

    var memberName by rememberSaveable {
        mutableStateOf(MockMemberProfile.name)
    }
    var memberEmail by rememberSaveable {
        mutableStateOf(MockMemberProfile.email)
    }

    var gender by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var age by rememberSaveable {
        mutableIntStateOf(28)
    }
    var weight by rememberSaveable {
        mutableIntStateOf(75)
    }
    var weightUnit by rememberSaveable {
        mutableStateOf("KG")
    }
    var height by rememberSaveable {
        mutableIntStateOf(165)
    }
    var notice by rememberSaveable {
        mutableStateOf<String?>(null)
    }

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

    // Điều hướng người dùng theo vai trò sau khi xác thực.
    fun navigateToUser(user: User) {
        if (!user.isActive) {
            authRepository.logout()
            resetDraft()
            notice =
                "Tài khoản không hoạt động. Vui lòng liên hệ quản trị viên."
            replaceStack("welcome")
            return
        }

        memberName = user.fullName
        memberEmail = user.email

        replaceStack(
            if (user.isAdmin) "admin" else "member"
        )
    }

    fun handleLoginSuccess(user: User) {
        resetDraft()
        navigateToUser(user)
    }

    // Khôi phục phiên Firebase khi mở lại ứng dụng.
    fun restoreSession() {
        coroutineScope.launch {
            try {
                if (!authRepository.isLoggedIn()) {
                    replaceStack("welcome")
                    return@launch
                }

                val user = authRepository.getCurrentProfile()

                if (user == null) {
                    // Có phiên Firebase nhưng không có hồ sơ tương ứng.
                    authRepository.logout()
                    resetDraft()
                    notice =
                        "Không tìm thấy hồ sơ tài khoản. Vui lòng đăng nhập lại."
                    replaceStack("welcome")
                    return@launch
                }

                navigateToUser(user)
            } catch (exception: Exception) {
                // Không tự đăng xuất khi có lỗi mạng hoặc lỗi đọc dữ liệu.
                // Người dùng có thể thử lại sau.
                notice =
                    "Chưa thể khôi phục phiên đăng nhập. " +
                            "Vui lòng kiểm tra kết nối mạng và thử lại."
                replaceStack("welcome")
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = "splash"
    ) {
        composable("splash") {
            RoutePage("splash") {
                SplashScreen(
                    onSplashFinished = ::restoreSession
                )
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
                        notice =
                            "Chức năng khôi phục mật khẩu chưa được tích hợp."
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
                            notice =
                                "Vui lòng kiểm tra lại thông tin hồ sơ."
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
