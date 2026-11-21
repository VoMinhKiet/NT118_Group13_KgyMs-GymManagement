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
            AuthRepository().register(User(fullName = name, email = demoEmail(), gender = "Female", age = 30), password)
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
