package com.kgyms.app.integration

import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.kgyms.app.R
import com.kgyms.app.data.model.User
import com.kgyms.app.data.repository.AuthRepository
import com.kgyms.app.ui.navigation.KgyMsApp
import com.kgyms.app.ui.theme.KgyMsTheme
import com.kgyms.app.viewmodel.auth.AuthError
import com.kgyms.app.viewmodel.auth.AuthState
import com.kgyms.app.viewmodel.auth.AuthViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Actual integrated NavHost, not replaced with test-only destination mocks. */
class AppIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var nav: NavHostController
    private var demoAuth: FirebaseAuth? = null
    private val fixturePassword = "DemoMember-Phase3B-42"

    @After fun signOutDemoMember() {
        demoAuth?.signOut()
    }

    private fun requireDemoAuth(): FirebaseAuth {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("Member Auth requires the isolated test APK", context.packageName == "com.kgyms.app.memberintegrationemu")
        check(FirebaseApp.getInstance().options.projectId == "demo-kgyms-member-integration")
        check(context.getString(R.string.default_web_client_id) == "DEMO_UNUSED_NO_GOOGLE_SIGN_IN")
        return FirebaseAuth.getInstance().also {
            it.signOut()
            demoAuth = it
        }
    }

    private fun installMemberContent() {
        compose.activityRule.scenario.onActivity { activity ->
            // The normal UI dispatcher keeps real Firebase continuations on the Android main
            // thread. Give the ComposeView a stable ID so Android can restore its saved state.
            val recomposer = Recomposer(AndroidUiDispatcher.Main)
            activity.lifecycleScope.launch(AndroidUiDispatcher.Main) {
                recomposer.runRecomposeAndApplyChanges()
            }.invokeOnCompletion { recomposer.cancel() }
            val view = ComposeView(activity).apply {
                id = android.R.id.custom
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setParentCompositionContext(recomposer)
                setContent {
                    KgyMsTheme {
                        nav = rememberNavController()
                        KgyMsApp(navController = nav)
                    }
                }
            }
            activity.setContentView(view)
        }
    }

    private fun startMember() {
        installMemberContent()
        memberRoute("welcome")
    }

    private fun memberRoute(name: String) {
        val destination = hasTestTag("route_$name")
        val navHostRoute = SemanticsMatcher("NavHost route") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("route_") == true
        }
        // The real recomposer runs navigation animations on the device clock. Old and new
        // routes can coexist briefly, so wait for the outgoing route to leave semantics.
        compose.waitUntil(15_000) {
            val routes = compose.onAllNodes(navHostRoute).fetchSemanticsNodes()
            routes.size == 1 && destination.matches(routes.single())
        }
    }

    private fun button(matcher: SemanticsMatcher): SemanticsNodeInteraction =
        compose.onNode(matcher).also { node ->
            if (compose.onAllNodes(matcher and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty()) {
                node.performScrollTo()
            }
        }

    private fun button(text: String) = button(hasText(text) and hasClickAction())

    private fun tap(text: String) {
        val enabled = hasText(text) and hasClickAction() and isEnabled()
        compose.waitUntil(8_000) { compose.onAllNodes(enabled).fetchSemanticsNodes().size == 1 }
        button(enabled).performClick()
    }

    private fun demoEmail() = "${UUID.randomUUID()}@example.invalid"

    private fun demoMember(name: String): User = runBlocking {
        withTimeout(15_000) {
            AuthRepository().register(User(fullName = name, email = demoEmail(), gender = "Female", age = 30), fixturePassword)
                .also { checkNotNull(demoAuth).signOut() }
        }
    }

    private fun fillLogin(email: String, password: String = fixturePassword) {
        field("login_email", email)
        field("login_password", password)
    }

    private fun signInMember(user: User) {
        fillLogin(user.email)
        tap("Sign In")
        memberRoute("member")
        assertEquals(user.uid, demoAuth?.currentUser?.uid)
    }

    private fun profileAction(action: String) {
        val target = compose.onNodeWithTag("action_$action")
        val list = compose.onNodeWithTag("profile_list")
        compose.waitUntil(8_000) { list.isDisplayed() }
        // Scroll using bounded touch gestures on the real device clock.
        repeat(6) {
            if (target.isDisplayed()) {
                target.performClick()
                return
            }
            list.performTouchInput { swipeUp(durationMillis = 480) }
        }
        target.assertIsDisplayed().performClick()
    }

    private fun assertNoPreviousDestination() {
        compose.runOnIdle { assertNull(nav.previousBackStackEntry) }
    }

    private fun start(debug: Boolean = true): StateRestorationTester {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            KgyMsTheme {
                val controller = rememberNavController()
                nav = controller
                KgyMsApp(controller, debug)
            }
        }
        route("welcome") // Exercises Splash -> Welcome rather than bypassing Splash.
        return restoration
    }
    private fun route(name: String) {
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("route_$name").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun click(text: String) {
        val node = compose.onNodeWithText(text, useUnmergedTree = true)
        val scrollable = compose.onAllNodes(hasText(text) and hasAnyAncestor(hasScrollAction()), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()
        if (scrollable) node.performScrollTo()
        node.performClick()
    }
    private fun field(tag: String, text: String) { compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(text) }
    private fun value(tag: String, value: Float) { compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.SetProgress) { it(value) } }
    private fun continueStep(next: String) {
        tap("Continue")
        memberRoute(next)
    }
    private fun chooseRole(label: String, destination: String) {
        click("Sign In"); route("login")
        field("login_email", "test@gmail.com"); field("login_password", "123456")
        click("Sign In"); route("role_preview")
        click(label); route(destination)
        compose.runOnIdle { assertNull(nav.previousBackStackEntry) }
    }

    // Preserve the four selector IDs used by the existing emulator runner.
    @Test fun signUpKeepsDraftAcrossAllBackStepsAndRestorationThenEntersMember() {
        val auth = requireDemoAuth()
        val email = demoEmail()
        val name = "Phase 3B Signup Member"
        startMember()
        tap("Sign Up")
        memberRoute("signup")
        tap("Sign Up")
        compose.onNodeWithTag("route_signup").assertExists()
        assertNull(auth.currentUser)
        field("signup_name", name)
        field("signup_email", email)
        field("signup_password", fixturePassword)
        field("signup_confirm", fixturePassword)
        tap("Sign Up")
        memberRoute("gender")
        val uid = checkNotNull(auth.currentUser).uid
        button("Continue").assertIsNotEnabled()
        enterOnboardingMeasurements()

        compose.activityRule.scenario.recreate()
        installMemberContent()
        memberRoute("height")
        compose.onNodeWithText("180 cm").assertExists()
        compose.onNodeWithTag("onboarding_back").performClick()
        memberRoute("weight")
        compose.onNodeWithText("176").assertExists()
        compose.onNodeWithTag("onboarding_back").performClick()
        memberRoute("age")
        compose.onNodeWithText("42").assertExists()
        compose.onNodeWithTag("onboarding_back").performClick()
        memberRoute("gender")
        button("Continue").assertIsEnabled()
        assertEquals(uid, auth.currentUser?.uid)

        val loginNavigatedOnMain = CompletableDeferred<Boolean>()
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            if (destination.route == "login") {
                loginNavigatedOnMain.complete(Looper.myLooper() == Looper.getMainLooper())
            }
        }
        compose.runOnIdle { nav.addOnDestinationChangedListener(listener) }
        try {
            compose.onNodeWithTag("onboarding_back").performClick()
            memberRoute("login")
            assertTrue("Logout must navigate on Android's main thread", runBlocking {
                withTimeout(5_000) { loginNavigatedOnMain.await() }
            })
        } finally {
            compose.runOnUiThread { nav.removeOnDestinationChangedListener(listener) }
        }
        assertNull(auth.currentUser)
        assertNoPreviousDestination()
        compose.onNodeWithTag("route_signup").assertDoesNotExist()

        fillLogin(email)
        tap("Sign In")
        memberRoute("gender")
        assertEquals(uid, auth.currentUser?.uid)
        button("Continue").assertIsNotEnabled()
        enterOnboardingMeasurements()
        continueStep("member")

        val saved = runBlocking {
            withTimeout(8_000) { FirebaseDatabase.getInstance().getReference("users/$uid").get().await() }
        }
        assertEquals(name, saved.child("fullName").value)
        assertEquals(email, saved.child("email").value)
        assertEquals("Female", saved.child("gender").value)
        assertEquals(42, saved.child("age").getValue(Int::class.java))
        assertEquals(176, saved.child("weight").getValue(Int::class.java))
        assertEquals("LB", saved.child("weightUnit").value)
        assertEquals(180, saved.child("height").getValue(Int::class.java))
        assertNoPreviousDestination()
    }

    private fun enterOnboardingMeasurements() {
        compose.onNodeWithTag("gender_Female").performClick()
        continueStep("age")
        value("age_ruler", 42f)
        continueStep("weight")
        value("weight_ruler", 80f)
        tap("LB")
        compose.onNodeWithText("176").assertExists()
        continueStep("height")
        value("height_ruler", 180f)
    }

    @Test fun memberLogoutClearsGraphAndANewPreviewHasFreshProfileState() {
        val auth = requireDemoAuth()
        val first = demoMember("Phase 3B First Member")
        val second = demoMember("Phase 3B Second Member")
        val editedName = "Phase 3B Edited First Member"
        startMember()
        tap("Sign In")
        memberRoute("login")
        signInMember(first)
        assertNoPreviousDestination()
        compose.onNodeWithTag("member_tab_profile").performClick()
        profileAction("EditProfile")
        field("profile_name", editedName)
        compose.onNodeWithTag("profile_save").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("profile_message_ok").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("profile_message_ok").performClick()
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("profile_message_ok").fetchSemanticsNodes().isEmpty() }

        val firstProfile = FirebaseDatabase.getInstance().getReference("users/${first.uid}")
        val saved = runBlocking { withTimeout(8_000) { firstProfile.get().await() } }
        assertEquals(editedName, saved.child("fullName").value)
        profileAction("Logout")
        tap("Cancel")
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("profile_confirm").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("route_member").assertExists()
        assertEquals(first.uid, auth.currentUser?.uid)
        profileAction("Logout")
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("profile_confirm").fetchSemanticsNodes().size == 1 }
        compose.onNodeWithTag("profile_confirm").performClick()
        memberRoute("login")
        assertNull(auth.currentUser)
        assertNoPreviousDestination()
        compose.onNodeWithTag("route_member").assertDoesNotExist()

        signInMember(second)
        compose.onNodeWithTag("member_tab_profile").performClick()
        compose.waitUntil(8_000) { compose.onNodeWithText(second.fullName).isDisplayed() }
        compose.onNodeWithText(editedName).assertDoesNotExist()
        val denial = runBlocking { withTimeout(8_000) { runCatching { firstProfile.get().await() } } }
        assertTrue(
            "The next UID cannot read the previous member's profile",
            denial.exceptionOrNull()?.message.orEmpty().contains("permission denied", ignoreCase = true)
        )
    }

    @Test fun adminMenuLogoutReturnsToLoginWithNoAdminOrRolePreviewBehindIt() {
        start()
        chooseRole("Vào Admin Dashboard", "admin")
        compose.onNodeWithTag("admin_account_menu").performClick()
        click("Đăng xuất")
        click("Hủy")
        compose.onNodeWithTag("route_admin").assertExists()
        compose.onNodeWithTag("admin_account_menu").performClick(); click("Đăng xuất")
        compose.onNodeWithTag("admin_confirm_logout").performClick()
        route("login")
        compose.runOnIdle { assertNull(nav.previousBackStackEntry) }
        compose.onNodeWithTag("route_admin").assertDoesNotExist()
        compose.onNodeWithTag("route_role_preview").assertDoesNotExist()
        compose.onNodeWithTag("auth_back").performClick()
        route("welcome") // Back fallback cannot reopen Admin.
    }

    @Test fun noMockSuccessWhenDebugModeIsDisabledAndForgotPasswordIsExplicitPreview() {
        val auth = requireDemoAuth()
        val member = demoMember("Phase 3B Invalid Credentials Member")
        startMember()
        tap("Sign In")
        memberRoute("login")
        fillLogin(member.email, "Wrong-for-demo")
        val loginModel = compose.runOnIdle {
            ViewModelProvider(nav.getBackStackEntry("login"))[AuthViewModel::class.java]
        }
        val error = runBlocking {
            withTimeout(15_000) {
                // Subscribe before tapping because LoginScreen resets state after showing the error.
                val rejection = async(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                    loginModel.authState.filterIsInstance<AuthState.Error>().first().error
                }
                tap("Sign In")
                rejection.await()
            }
        }
        assertEquals(AuthError.WrongCredentials, error)
        memberRoute("login")
        assertNull(auth.currentUser)
        compose.onNodeWithTag("route_member").assertDoesNotExist()
        compose.onNodeWithTag("route_role_preview").assertDoesNotExist()

        tap("Forgot password?")
        memberRoute("forgot-password")
        compose.onNodeWithTag("app_notice_ok").assertDoesNotExist()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("route_forgot-password")))
            .performScrollTo()
            .performTextReplacement("invalid-email")
        tap("Send Reset Link")
        compose.onNodeWithText("Please enter a valid email address.").assertExists()
        compose.onNodeWithText("If an account exists for this email", substring = true).assertDoesNotExist()
        tap("← Back")
        memberRoute("login")
        assertNull(auth.currentUser)
    }

    @Test fun googleButtonIsOnlyAMockAndSignUpStillGoesThroughGender() {
        val auth = requireDemoAuth()
        startMember()
        tap("Sign Up")
        memberRoute("signup")
        awaitGoogleRejection(
            "Sign up with Google",
            setOf(
                "Unable to start Google sign-in. Please try again.",
                "Did not receive valid Google account information.",
                "Google sign-up was cancelled or could not be completed.",
                "Google sign-up failed. Please try again."
            )
        )
        assertNull(auth.currentUser)
        memberRoute("signup")
        compose.onNodeWithTag("route_gender").assertDoesNotExist()
        compose.onNodeWithTag("auth_back").assertIsEnabled().performClick()
        memberRoute("welcome")

        tap("Sign In")
        memberRoute("login")
        awaitGoogleRejection(
            "Continue with Google",
            setOf(
                "Unable to start Google sign-in.",
                "Google did not return a valid sign-in credential.",
                "Google sign-in was cancelled or could not be completed.",
                "Unable to sign in with Google."
            )
        )
        assertNull(auth.currentUser)
        memberRoute("login")
        compose.onNodeWithTag("route_role_preview").assertDoesNotExist()
        compose.onNodeWithTag("route_member").assertDoesNotExist()
        tap("Forgot password?")
        memberRoute("forgot-password")
    }

    @Suppress("DEPRECATION") // AccessibilityEvent recycling is required on the API 28 test AVD.
    private fun awaitGoogleRejection(label: String, messages: Set<String>) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        val google = button(label).assertIsEnabled()
        // Verify feedback from the real credential handler before checking its idle state.
        instrumentation.uiAutomation.executeAndWaitForEvent(
            { google.performClick() },
            { event ->
                event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED &&
                    event.packageName?.toString() == packageName &&
                    event.text.any { it.toString() in messages }
            },
            20_000
        ).recycle()
        compose.waitUntil(8_000) {
            compose.onAllNodes(hasText(label) and hasClickAction() and isEnabled())
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

}
