package com.kgyms.app.ui.navigation

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kgyms.app.ui.admin.AdminNavHost
import com.kgyms.app.ui.auth.*
import com.kgyms.app.ui.profile.MockMemberProfile
import com.kgyms.app.ui.profile.ProfileFlow

/** App shell only. Mock sign-in is enabled by the debuggable application flag. */
@Composable
fun KgyMsApp(
    navController: NavHostController = rememberNavController(),
    debugToolsEnabled: Boolean? = null,
) {
    val context = LocalContext.current
    val mockEnabled = debugToolsEnabled ?: ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0)
    var memberName by rememberSaveable { mutableStateOf(MockMemberProfile.name) }
    var memberEmail by rememberSaveable { mutableStateOf(MockMemberProfile.email) }
    var gender by rememberSaveable { mutableStateOf<String?>(null) }
    var age by rememberSaveable { mutableIntStateOf(28) }
    var weight by rememberSaveable { mutableIntStateOf(75) }
    var weightUnit by rememberSaveable { mutableStateOf("KG") }
    var height by rememberSaveable { mutableIntStateOf(165) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }

    fun resetDraft() {
        memberName = MockMemberProfile.name; memberEmail = MockMemberProfile.email
        gender = null; age = 28; weight = 75; weightUnit = "KG"; height = 165
    }
    fun replaceStack(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }
    fun logout() { resetDraft(); replaceStack("login") }
    fun startSignUp() {
        resetDraft()
        navController.navigate("signup") { launchSingleTop = true }
    }
    fun backToAuth() {
        if (!navController.popBackStack()) replaceStack("welcome")
    }

    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") {
            RoutePage("splash") {
                SplashScreen {
                    navController.navigate("welcome") { popUpTo("splash") { inclusive = true }; launchSingleTop = true }
                }
            }
        }
        composable("welcome") {
            RoutePage("welcome", authInsets = true) {
                WelcomeScreen(onNavigateToLogin = { navController.navigate("login") { launchSingleTop = true } }, onNavigateToSignUp = ::startSignUp)
            }
        }
        composable("login") {
            RoutePage("login", authInsets = true) {
                LoginScreen(
                    onNavigateBack = ::backToAuth,
                    onLoginSuccess = {
                        resetDraft()
                        if (mockEnabled) replaceStack("role_preview")
                        else notice = "Chưa tích hợp xác thực thật. Không có phiên đăng nhập được tạo."
                    },
                    onNavigateToSignUp = ::startSignUp,
                    onNavigateToForgotPassword = { notice = "Khôi phục mật khẩu đang ở chế độ xem thử. Chưa gửi email hoặc mã xác nhận." },
                    mockEnabled = mockEnabled,
                )
            }
        }
        composable("signup") {
            RoutePage("signup", authInsets = true) {
                SignUpScreen(
                    onNavigateBack = ::backToAuth,
                    onNavigateToGenderSelection = { navController.navigate("gender") { launchSingleTop = true } },
                    onNavigateToLogin = ::logout,
                    mockEnabled = mockEnabled,
                    onProfileDraft = { name, email ->
                        memberName = name.ifBlank { MockMemberProfile.name }
                        memberEmail = email.ifBlank { "google-preview@example.com" }
                    },
                )
            }
        }
        composable("gender") {
            RoutePage("gender", authInsets = true) {
                GenderSelectionScreen(
                    onNavigateBack = { navController.popBackStack() }, initialGender = gender,
                    onGenderChanged = { gender = it },
                    onContinue = { gender = it; navController.navigate("age") { launchSingleTop = true } },
                )
            }
        }
        composable("age") {
            RoutePage("age", authInsets = true) {
                AgeScreen(
                    onNavigateBack = { navController.popBackStack() }, initialAge = age, onAgeChanged = { age = it },
                    onContinue = { age = it; navController.navigate("weight") { launchSingleTop = true } },
                )
            }
        }
        composable("weight") {
            RoutePage("weight", authInsets = true) {
                WeightScreen(
                    onNavigateBack = { navController.popBackStack() }, initialWeight = weight, initialUnit = weightUnit,
                    onWeightChanged = { value, unit -> weight = value; weightUnit = unit },
                    onContinue = { value, unit -> weight = value; weightUnit = unit; navController.navigate("height") { launchSingleTop = true } },
                )
            }
        }
        composable("height") {
            RoutePage("height", authInsets = true) {
                HeightScreen(
                    onNavigateBack = { navController.popBackStack() }, initialHeight = height, onHeightChanged = { height = it },
                    onContinue = {
                        height = it
                        if (mockEnabled && gender != null && age in 10..100 && height in 100..250 &&
                            weight in (if (weightUnit == "KG") 30..200 else 66..440)) {
                            replaceStack("member")
                            notice = "Hoàn tất Onboarding mẫu. Thông tin chỉ ở trong phiên thử nghiệm; chưa tạo tài khoản thật."
                        } else notice = "Vui lòng hoàn tất các bước Onboarding trong bản debug."
                    },
                )
            }
        }
        composable("role_preview") {
            RoutePage("role_preview", authInsets = true) {
                if (mockEnabled) RolePreviewScreen(
                    onAdmin = { replaceStack("admin") }, onMember = { replaceStack("member") }, onBackToLogin = ::logout,
                ) else LaunchedEffect(Unit) { logout() }
            }
        }
        composable("admin") {
            RoutePage("admin") {
                if (mockEnabled) AdminNavHost(onLogout = ::logout)
                else LaunchedEffect(Unit) { logout() }
            }
        }
        composable("member") {
            RoutePage("member") {
                if (mockEnabled) ProfileFlow(
                    onLogout = ::logout,
                    initialProfile = MockMemberProfile.copy(name = memberName, email = memberEmail),
                ) else LaunchedEffect(Unit) { logout() }
            }
        }
    }
    notice?.let { message ->
        AlertDialog(onDismissRequest = { notice = null }, title = { Text("KgyMs • Bản thử nghiệm") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = { notice = null }, modifier = Modifier.testTag("app_notice_ok")) { Text("OK") } })
    }
}

@Composable
private fun RoutePage(route: String, authInsets: Boolean = false, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().then(if (authInsets) Modifier.safeDrawingPadding().imePadding() else Modifier).testTag("route_$route")) { content() }
}

@Composable
private fun RolePreviewScreen(onAdmin: () -> Unit, onMember: () -> Unit, onBackToLogin: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("KgyMs - Integration Test", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text("Chọn khu vực để kiểm tra giao diện. Đây chưa phải phân quyền tài khoản thật.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onAdmin, modifier = Modifier.fillMaxWidth()) { Text("Vào Admin Dashboard") }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onMember, modifier = Modifier.fillMaxWidth()) { Text("Vào Member Profile") }
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onBackToLogin) { Text("Quay lại đăng nhập") }
    }
}
