
package com.kgyms.app.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgyms.app.data.model.User
import com.kgyms.app.data.repository.AuthRepository
import kotlinx.coroutines.launch
import com.kgyms.app.ui.admin.navigation.AdminNavHost
import com.kgyms.app.ui.auth.*
import com.kgyms.app.viewmodel.auth.OnboardingViewModel
import com.kgyms.app.viewmodel.auth.AuthError
import com.kgyms.app.viewmodel.settings.LanguageViewModel
import com.kgyms.app.ui.localization.AppLanguageController
import com.kgyms.app.ui.localization.LocalAppLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import com.kgyms.app.data.repository.InvalidAccountReason
import com.kgyms.app.data.repository.SessionResolution
import com.kgyms.app.data.repository.SessionRestorationRepository
import com.kgyms.app.ui.member.MemberAccountHost

@Composable
fun KgyMsApp(
    navController: NavHostController = rememberNavController(),
    debugToolsEnabled: Boolean? = null,
    onboardingViewModel: OnboardingViewModel = viewModel(),
    languageViewModel: LanguageViewModel = viewModel()
) {
    val language by languageViewModel.language.collectAsState()
    val languageController = AppLanguageController(language, languageViewModel::setLanguage)
    CompositionLocalProvider(LocalAppLanguage provides languageController) {
        KgyMsAppContent(navController, onboardingViewModel, language)
    }
}

@Composable
private fun KgyMsAppContent(
    navController: NavHostController,
    onboardingViewModel: OnboardingViewModel,
    language: String
) {
    val context = LocalContext.current
    val authRepository = remember { AuthRepository() }
    val sessionRestoration = remember(context.applicationContext) {
        SessionRestorationRepository.production(context.applicationContext)
    }
    val scope = rememberCoroutineScope()
    var savingOnboarding by remember { mutableStateOf(false) }
    var resolvingSession by remember { mutableStateOf(false) }
    var restoredUser by remember { mutableStateOf<User?>(null) }
    var offlineAccess by remember { mutableStateOf(false) }
    var accessIssue by remember { mutableStateOf<SessionResolution?>(null) }
    var revalidationJob by remember { mutableStateOf<Job?>(null) }

    var memberName by rememberSaveable {
        mutableStateOf("")
    }
    var memberEmail by rememberSaveable {
        mutableStateOf("")
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
        memberName = ""
        memberEmail = ""
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

    val logoutScope = rememberCoroutineScope()
    var isLoggingOut by remember { mutableStateOf(false) }

    fun logout() {
        if (isLoggingOut) return
        isLoggingOut = true
        restoredUser = null
        offlineAccess = false
        accessIssue = null
        revalidationJob?.cancel()

        // Ghi log đăng xuất (admin) xong mới thoát phiên, rồi về màn đăng nhập.
        logoutScope.launch {
            try {
                authRepository.logoutWithActivityLog()
            } finally {
                authRepository.logout()
                isLoggingOut = false
                resetDraft()
                replaceStack("login")
            }
        }
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

    fun acceptResolvedUser(user: User, offline: Boolean) {
        resetDraft()
        memberName = user.fullName
        memberEmail = user.email
        restoredUser = user
        offlineAccess = offline
        accessIssue = null

        if (!user.isActive) {
            restoredUser = null
            authRepository.logout()
            notice = AuthError.AccountInactive.message(language)
            replaceStack("login")
            return
        }

        if (!offline) sessionRestoration.rememberVerifiedMember(user)

        when {
            user.isAdmin -> replaceStack("admin")

            // Áp dụng cho mọi kiểu đăng nhập/đăng ký (email/password và Google):
            // hồ sơ chưa có giới tính/tuổi thì phải hoàn tất trước.
            !user.hasCompletedOnboarding -> {
                gender = user.gender.ifBlank { null }
                if (user.age > 0) age = user.age
                replaceStack("gender")
            }

            else -> replaceStack("member")
        }
    }

    fun applySessionResolution(result: SessionResolution) {
        when (result) {
            SessionResolution.SignedOut -> {
                restoredUser = null
                offlineAccess = false
                replaceStack("welcome")
            }
            is SessionResolution.Verified -> acceptResolvedUser(result.user, offline = false)
            is SessionResolution.OfflineMember -> acceptResolvedUser(result.user, offline = true)
            else -> {
                accessIssue = result
                replaceStack("session-access")
            }
        }
    }

    fun resolveSession() {
        if (resolvingSession) return
        resolvingSession = true
        scope.launch {
            try {
                applySessionResolution(sessionRestoration.resolve())
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                applySessionResolution(SessionResolution.Failed(exception))
            } finally {
                resolvingSession = false
            }
        }
    }

    fun handleLoginSuccess(user: User) {
        // Admin authority must come from a live server read, including after login.
        if (user.isAdmin) resolveSession()
        else if (authRepository.getCurrentUid() == user.uid) acceptResolvedUser(user, offline = false)
    }

    fun blockAccess(issue: SessionResolution) {
        accessIssue = issue
        restoredUser = null
        offlineAccess = false
        replaceStack("session-access")
    }

    val resolvedOwner = restoredUser?.uid
    DisposableEffect(sessionRestoration, resolvedOwner) {
        if (resolvedOwner == null) return@DisposableEffect onDispose { }

        fun refresh() {
            if (isLoggingOut) return
            if (revalidationJob?.isActive == true) return
            revalidationJob = scope.launch {
                when (val result = sessionRestoration.revalidate(resolvedOwner)) {
                    is SessionResolution.Verified -> {
                        val user = result.user
                        val route = navController.currentDestination?.route
                        val correctRoute = when {
                            user.isAdmin -> route == "admin"
                            !user.hasCompletedOnboarding -> route in setOf("gender", "age", "weight", "height")
                            else -> route == "member"
                        }
                        if (correctRoute) {
                            restoredUser = user
                            offlineAccess = false
                            sessionRestoration.rememberVerifiedMember(user)
                        } else acceptResolvedUser(user, offline = false)
                    }
                    SessionResolution.OfflineWithoutCache,
                    is SessionResolution.OfflineMember -> offlineAccess = true
                    SessionResolution.AccessDenied,
                    is SessionResolution.InvalidAccount -> blockAccess(result)
                    SessionResolution.SessionChanged,
                    SessionResolution.SignedOut -> logout()
                    is SessionResolution.Failed -> offlineAccess = true
                }
            }
        }

        val observation = sessionRestoration.observe(
            resolvedOwner,
            onConnectivity = { connected -> if (!connected) scope.launch { offlineAccess = true } },
            onRefresh = ::refresh,
            onDenied = {
                scope.launch {
                    sessionRestoration.revokeOfflineAccess(resolvedOwner)
                    blockAccess(SessionResolution.AccessDenied)
                }
            },
        )
        onDispose {
            revalidationJob?.cancel()
            revalidationJob = null
            observation.close()
        }
    }

    val retryOwner = if (accessIssue == SessionResolution.OfflineWithoutCache ||
        accessIssue is SessionResolution.Failed
    ) sessionRestoration.currentUid() else null
    DisposableEffect(sessionRestoration, retryOwner) {
        if (retryOwner == null) return@DisposableEffect onDispose { }
        val observation = sessionRestoration.observe(
            retryOwner,
            onConnectivity = { connected -> if (connected) scope.launch { resolveSession() } },
            onRefresh = { scope.launch { resolveSession() } },
            onDenied = {
                scope.launch {
                    sessionRestoration.revokeOfflineAccess(retryOwner)
                    blockAccess(SessionResolution.AccessDenied)
                }
            },
        )
        onDispose { observation.close() }
    }

    fun completeOnboarding() {
        if (savingOnboarding) return
        val selectedGender = gender ?: return
        val owner = authRepository.getCurrentUid() ?: return
        val selectedWeight = weight
        val selectedUnit = weightUnit
        val selectedHeight = height
        savingOnboarding = true

        scope.launch {
            try {
                authRepository.completeOnboarding(
                    gender = selectedGender,
                    age = age,
                    weight = selectedWeight,
                    weightUnit = selectedUnit,
                    height = selectedHeight,
                    expectedUid = owner
                )
                if (authRepository.getCurrentUid() == owner) {
                    val saved = authRepository.getCurrentProfile() ?: error("Missing profile")
                    if (authRepository.getCurrentUid() == owner) handleLoginSuccess(saved)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (authRepository.getCurrentUid() == owner) {
                    notice = com.kgyms.app.viewmodel.auth.OnboardingError.SaveFailed.message(language)
                }
            } finally {
                savingOnboarding = false
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = "splash"
    ) {
        composable("splash") {
            RoutePage("splash") {
                if (resolvingSession) SessionResolvingScreen()
                else SplashScreen(onSplashFinished = ::resolveSession)
            }
        }

        composable("session-access") {
            RoutePage("session-access", authInsets = true) {
                SessionAccessScreen(
                    issue = accessIssue,
                    loading = resolvingSession,
                    onRetry = ::resolveSession,
                    onUseAnotherAccount = ::logout,
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
                    },
                    onAuthSuccess = ::handleLoginSuccess
                )
            }
        }

        composable("gender") {
            RoutePage("gender", authInsets = true) {
                GenderSelectionScreen(
                    // Gender là bước đầu của onboarding: quay lại nghĩa là bỏ dở,
                    // đăng xuất để lần đăng nhập sau tiếp tục hoàn tất hồ sơ.
                    onNavigateBack = ::logout,
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
                        completeOnboarding()
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
                MemberAccountHost(
                    onLogout = ::logout,
                    initialUser = restoredUser,
                    initialOffline = offlineAccess,
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
private fun SessionResolvingScreen() {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Đang mở tài khoản của bạn…", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SessionAccessScreen(
    issue: SessionResolution?,
    loading: Boolean,
    onRetry: () -> Unit,
    onUseAnotherAccount: () -> Unit,
) {
    val message = when (issue) {
        SessionResolution.OfflineWithoutCache ->
            "Chưa thể xác minh tài khoản khi ngoại tuyến. Hãy kết nối mạng và thử lại."
        SessionResolution.AccessDenied ->
            "Không thể xác minh quyền truy cập của tài khoản này."
        is SessionResolution.InvalidAccount -> when (issue.reason) {
            InvalidAccountReason.Inactive ->
                "Tài khoản hiện không hoạt động. Vui lòng liên hệ quản trị viên."
            InvalidAccountReason.MissingProfile ->
                "Không tìm thấy hồ sơ hợp lệ cho tài khoản này."
        }
        SessionResolution.SessionChanged -> "Phiên đăng nhập đã thay đổi. Vui lòng thử lại."
        is SessionResolution.Failed -> "Không thể kiểm tra tài khoản lúc này. Vui lòng thử lại."
        else -> "Chưa thể mở tài khoản lúc này."
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("KgyMs", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("session_retry"),
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("Thử lại")
        }
        TextButton(
            onClick = onUseAnotherAccount,
            enabled = !loading,
            modifier = Modifier.heightIn(min = 48.dp).testTag("session_use_another_account"),
        ) {
            Text("Dùng tài khoản khác")
        }
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
