package com.kgyms.app.integration

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.kgyms.app.data.model.User
import com.kgyms.app.data.repository.AuthRepository
import com.kgyms.app.ui.navigation.KgyMsApp
import com.kgyms.app.ui.theme.KgyMsTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Production Member routes using only the demo Auth and RTDB initialized by MemberEmulatorRunner. */
class MemberAuthEmulatorTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var nav: NavHostController
    private val auth get() = FirebaseAuth.getInstance()
    private val database get() = FirebaseDatabase.getInstance()
    private val password = "OnlyMemberDemo-42"

    @Before fun requireDemo() {
        check(FirebaseApp.getInstance().options.projectId == MemberEmulatorFirebase.PROJECT)
        auth.signOut()
    }

    @After fun endSession() = auth.signOut()

    @Composable
    private fun MemberNavigation() {
        KgyMsTheme {
            nav = rememberNavController()
            KgyMsApp(navController = nav)
        }
    }

    private fun start(): StateRestorationTester = StateRestorationTester(compose).also {
        it.setContent { MemberNavigation() }
        route("welcome")
    }

    private fun startOnAndroidUiThread() {
        // Keep app effects on Android's UI dispatcher across real Firebase callbacks. In the
        // default Compose fixture an unconfined test dispatcher can resume navigation off-main.
        compose.setContent {
            val recomposer = remember { Recomposer(AndroidUiDispatcher.Main) }
            DisposableEffect(recomposer) {
                val job = CoroutineScope(AndroidUiDispatcher.Main).launch {
                    recomposer.runRecomposeAndApplyChanges()
                }
                onDispose {
                    recomposer.cancel()
                    job.cancel()
                }
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    ComposeView(context).apply {
                        setParentCompositionContext(recomposer)
                        setContent { MemberNavigation() }
                    }
                }
            )
        }
        route("welcome")
    }

    private fun route(name: String) {
        val tag = "route_$name"
        compose.waitUntil(20_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun click(label: String) {
        val matcher = hasText(label) and hasClickAction()
        val node = compose.onNode(matcher)
        val scrollable = compose.onAllNodes(matcher and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty()
        if (scrollable) node.performScrollTo()
        node.performClick()
    }

    private fun field(tag: String, value: String) =
        compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)

    private fun ruler(tag: String, value: Float) =
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.SetProgress) { it(value) }

    private fun advance(destination: String) {
        click("Continue")
        route(destination)
    }

    private fun demoEmail() = "${UUID.randomUUID()}@example.invalid"

    @Test fun authAndBothDatabaseInstancesReachTheSameDemoProject() = runBlocking<Unit> {
        val user = checkNotNull(withTimeoutOrNull(10_000) {
            auth.createUserWithEmailAndPassword(demoEmail(), password).await().user
        }) { "Auth Emulator did not create the demo account" }
        val node = "users/${user.uid}"
        val regional = FirebaseDatabase.getInstance(MemberEmulatorFirebase.LEGACY_DB_URL)
        check(withTimeoutOrNull(12_000) {
            regional.getReference(node).setValue(mapOf("role" to "user", "status" to "active")).await()
            true
        } == true) { "RTDB Emulator did not acknowledge the regional reference write" }
        val reference = database.getReference("$node/status")
        val observed = CompletableDeferred<DataSnapshot>()
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { observed.complete(snapshot) }
            override fun onCancelled(error: DatabaseError) { observed.completeExceptionally(error.toException()) }
        }
        reference.addListenerForSingleValueEvent(listener)
        try {
            val saved = checkNotNull(withTimeoutOrNull(10_000) { observed.await() }) {
                "RTDB Emulator did not deliver the default reference's value event"
            }
            assertEquals("active", saved.value)
        } finally {
            reference.removeEventListener(listener)
        }
        val loaded = checkNotNull(withTimeoutOrNull(10_000) { reference.get().await() }) {
            "RTDB Emulator did not complete the default reference's one-shot read"
        }
        assertEquals("active", loaded.value)
    }

    @Test fun emailSignupRestoresOnboardingDraftAndPersistsMemberInDemoRtdb() {
        val email = demoEmail()
        val restoration = start()
        click("Sign Up")
        route("signup")
        click("Sign Up")
        compose.onNodeWithTag("route_signup").assertExists()
        field("signup_name", "Member Signup Fixture")
        field("signup_email", email)
        field("signup_password", password)
        field("signup_confirm", password)
        click("Sign Up")
        // Đăng ký email phải qua bước xác nhận email; chưa bấm link thì không vào được onboarding.
        route("verify-email")
        assertNull(runBlocking { withTimeout(8_000) { database.getReference("users/${checkNotNull(auth.currentUser).uid}").get().await() } }.value)
        compose.onNodeWithTag("verify_email_check").performClick()
        compose.onNodeWithTag("route_gender").assertDoesNotExist()
        runBlocking {
            withTimeout(12_000) {
                auth.applyActionCode(authActionCode("VERIFY_EMAIL", email)).await()
            }
        }
        compose.waitUntil(20_000) {
            compose.onAllNodes(hasTestTag("verify_email_check") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("verify_email_check").performClick()
        route("gender")
        compose.onNode(hasText("Continue") and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithTag("gender_Female").performClick()
        advance("age")
        ruler("age_ruler", 42f)
        advance("weight")
        ruler("weight_ruler", 80f)
        click("LB")
        compose.onNodeWithText("176").assertExists()
        advance("height")
        ruler("height_ruler", 180f)
        restoration.emulateSavedInstanceStateRestore()
        route("height")
        compose.onNodeWithText("180 cm").assertExists()
        compose.onNodeWithTag("onboarding_back").performClick()
        route("weight")
        compose.onNodeWithText("176").assertExists()
        compose.onNodeWithTag("onboarding_back").performClick()
        route("age")
        compose.onNodeWithText("42").assertExists()
        advance("weight")
        advance("height")
        compose.onNodeWithText("180 cm").assertExists()
        advance("member")

        val uid = checkNotNull(auth.currentUser).uid
        val saved = runBlocking { withTimeout(8_000) { database.getReference("users/$uid").get().await() } }
        assertEquals("Member Signup Fixture", saved.child("fullName").value)
        assertEquals(email, saved.child("email").value)
        assertEquals(42, saved.child("age").getValue(Int::class.java))
        assertEquals(180.0, checkNotNull(saved.child("height").getValue(Double::class.java)), 0.01)
        assertEquals("user", saved.child("role").value)
        assertEquals("active", saved.child("status").value)
        compose.runOnIdle { assertNull(nav.previousBackStackEntry) }
    }

    private fun seedMember(name: String): User = runBlocking {
        withTimeout(15_000) {
            seedRegisteredMember(User(fullName = name, email = demoEmail(), gender = "Female", age = 30), password)
                .also { auth.signOut() }
        }
    }

    private fun signIn(user: User) {
        field("login_email", user.email)
        field("login_password", password)
        click("Sign In")
        route("member")
        assertEquals(user.uid, auth.currentUser?.uid)
    }

    private fun profileAction(action: String) {
        val tag = "action_$action"
        compose.onNodeWithTag("profile_list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
    }

    private fun authActionCode(requestType: String, recipient: String): String {
        val emulator = MemberEmulatorFirebase
        val connection = URL("http://${emulator.HOST}:${emulator.AUTH_PORT}/emulator/v1/projects/${emulator.PROJECT}/oobCodes")
            .openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        try {
            check(connection.responseCode == 200) { "Auth Emulator did not list account actions" }
            val codes = connection.inputStream.bufferedReader().use { JSONObject(it.readText()).getJSONArray("oobCodes") }
            return (0 until codes.length()).map(codes::getJSONObject).single {
                it.getString("requestType") == requestType &&
                    (it.optString("email") == recipient || it.optString("newEmail") == recipient)
            }.getString("oobCode")
        } finally {
            connection.disconnect()
        }
    }

    private fun seedInbox(uid: String) {
        val emulator = MemberEmulatorFirebase
        val url = "http://${emulator.HOST}:${emulator.DB_PORT}/notifications/$uid.json?ns=${emulator.PROJECT}-default-rtdb"
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        try {
            connection.requestMethod = "PUT"
            connection.setRequestProperty("Authorization", "Bearer owner")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            val rows = JSONObject()
            fun row(id: String, title: String, type: String, broadcastId: String = "") {
                rows.put(id, JSONObject().put("title", title).put("message", title)
                    .put("type", type).put("broadcastId", broadcastId).put("isRead", false))
            }
            row("gym", "Demo gym broadcast", "general", "fixture")
            row("reminder", "Demo member reminder", "reminder")
            row("transaction", "Demo transaction notice", "general")
            connection.outputStream.use { it.write(rows.toString().toByteArray()) }
            check(connection.responseCode == 200) { "Demo inbox seed failed" }
        } finally {
            connection.disconnect()
        }
    }

    @Test fun profileInboxPreferencesHideOnlySelectedNoticesAndAreIsolatedByUid() {
        val first = seedMember("First Inbox Member")
        val second = seedMember("Second Inbox Member")
        seedInbox(first.uid)
        seedInbox(second.uid)
        startOnAndroidUiThread()
        click("Sign In")
        route("login")
        signIn(first)
        compose.onNodeWithTag("member_tab_profile").performClick()
        profileAction("NotificationSettings")
        for (tag in listOf("notification_general", "notification_reminders")) {
            compose.onNodeWithTag(tag).performScrollTo().assertIsOn().performClick()
            compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag) and isOff()).fetchSemanticsNodes().isNotEmpty() }
        }
        compose.onNodeWithTag("profile_back").performClick()
        profileAction("NotificationSettings")
        compose.onNodeWithTag("notification_general").assertIsOff()
        compose.onNodeWithTag("notification_reminders").assertIsOff()
        compose.onNodeWithTag("profile_back").performClick()
        compose.onNodeWithTag("member_tab_home").performClick()
        compose.onNodeWithTag("home_notifications").performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Demo transaction notice").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Demo gym broadcast").assertDoesNotExist()
        compose.onNodeWithText("Demo member reminder").assertDoesNotExist()
        val hidden = runBlocking { withTimeout(8_000) { database.getReference("notifications/${first.uid}/gym/isRead").get().await() } }
        assertEquals(false, hidden.value)
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("member_tab_profile").performClick()
        profileAction("Logout")
        compose.onNodeWithTag("profile_confirm").performClick()
        route("login")
        signIn(second)
        compose.onNodeWithTag("home_notifications").performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Demo gym broadcast").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithText("Demo member reminder").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun profileEmailVerificationAndPasswordResetUseAuthEmulatorActions() {
        val member = seedMember("Profile Recovery Fixture")
        val newEmail = demoEmail()
        startOnAndroidUiThread()
        click("Sign In")
        route("login")
        signIn(member)
        compose.onNodeWithTag("member_tab_profile").performClick()
        profileAction("EditProfile")
        field("profile_email", newEmail)
        compose.onNodeWithTag("profile_save").performScrollTo().performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText("Verification email sent", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(member.email, auth.currentUser?.email)
        compose.onNodeWithTag("profile_message_ok").performClick()
        runBlocking {
            withTimeout(12_000) {
                auth.applyActionCode(authActionCode("VERIFY_AND_CHANGE_EMAIL", newEmail)).await()
            }
        }
        profileAction("Logout")
        compose.onNodeWithTag("profile_confirm").performClick()
        route("login")
        signIn(member.copy(email = newEmail))
        compose.onNodeWithTag("member_tab_profile").performClick()
        compose.onNodeWithText(newEmail).assertExists()
        profileAction("Settings")
        click("Password")
        compose.onNodeWithTag("password_forgot").performScrollTo().performClick()
        compose.onNodeWithTag("password_reset_confirm").performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText("A password reset email was sent", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        runBlocking {
            withTimeout(12_000) {
                val code = authActionCode("PASSWORD_RESET", newEmail)
                assertEquals(newEmail, auth.verifyPasswordResetCode(code).await())
                auth.confirmPasswordReset(code, "Profile-Recovery-Demo-123").await()
            }
        }
    }

    @Test fun profileSaveLogoutAndNextMemberKeepDemoUidsIsolated() {
        val first = seedMember("First Demo Member")
        val second = seedMember("Second Demo Member")
        startOnAndroidUiThread()
        click("Sign In")
        route("login")
        signIn(first)
        compose.onNodeWithTag("member_tab_profile").performClick()
        profileAction("EditProfile")
        field("profile_name", "Renamed Demo Member")
        compose.onNodeWithTag("profile_save").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("profile_message_ok").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("profile_message_ok").performClick()
        val saved = runBlocking { withTimeout(8_000) { database.getReference("users/${first.uid}/fullName").get().await() } }
        assertEquals("Renamed Demo Member", saved.value)

        profileAction("Logout")
        click("Cancel")
        compose.onNodeWithTag("route_member").assertExists()
        profileAction("Logout")
        compose.onNodeWithTag("profile_confirm").performClick()
        route("login")
        compose.runOnIdle { assertNull(nav.previousBackStackEntry) }
        assertNull(auth.currentUser)

        signIn(second)
        compose.onNodeWithTag("member_tab_profile").performClick()
        compose.onNodeWithTag("profile_list").performScrollToIndex(0)
        compose.onNodeWithText("Second Demo Member").assertExists()
        compose.onNodeWithText("Renamed Demo Member").assertDoesNotExist()
        val otherProfile = runBlocking {
            withTimeout(8_000) { runCatching { database.getReference("users/${first.uid}").get().await() } }
        }
        val denial = checkNotNull(otherProfile.exceptionOrNull())
        assertTrue(denial.message.orEmpty().contains("permission denied", ignoreCase = true))
    }

    @Test fun profilePasswordChangeRequiresTheCurrentPassword() {
        val member = seedMember("Profile Credentials Fixture")
        val newPassword = "MemberEmulator-84"
        startOnAndroidUiThread()
        click("Sign In")
        route("login")
        signIn(member)
        compose.onNodeWithTag("member_tab_profile").performClick()
        profileAction("Settings")
        click("Password")
        field("password_current", "Incorrect-for-demo")
        field("password_new", newPassword)
        field("password_confirm", newPassword)
        compose.onNodeWithTag("profile_save").performScrollTo().performClick()
        val incorrect = "Your current password is incorrect."
        compose.waitUntil(15_000) { compose.onAllNodesWithText(incorrect).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("profile_message_ok").performClick()
        assertEquals(member.uid, auth.currentUser?.uid)

        field("password_current", password)
        compose.onNodeWithTag("profile_save").performScrollTo().performClick()
        val changed = "Your password has been changed."
        compose.waitUntil(15_000) { compose.onAllNodesWithText(changed).fetchSemanticsNodes().isNotEmpty() }
        runBlocking {
            withTimeout(10_000) {
                auth.signOut()
                val old = runCatching { auth.signInWithEmailAndPassword(member.email, password).await() }
                assertTrue(old.exceptionOrNull() is com.google.firebase.auth.FirebaseAuthException)
                val result = auth.signInWithEmailAndPassword(member.email, newPassword).await()
                assertEquals(member.uid, result.user?.uid)
            }
        }
    }

    @Test fun wrongPasswordKeepsAuthSignedOutAndForgotPasswordHasItsOwnRoute() {
        val member = seedMember("Login Validation Fixture")
        start()
        click("Sign In")
        route("login")
        field("login_email", member.email)
        field("login_password", "Incorrect-for-demo")
        click("Sign In")
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasText("Sign In") and hasClickAction() and isEnabled())
                .fetchSemanticsNodes().isNotEmpty()
        }
        route("login")
        assertNull(auth.currentUser)
        compose.onNodeWithTag("route_member").assertDoesNotExist()
        click("Forgot password?")
        route("forgot-password")
    }
}
