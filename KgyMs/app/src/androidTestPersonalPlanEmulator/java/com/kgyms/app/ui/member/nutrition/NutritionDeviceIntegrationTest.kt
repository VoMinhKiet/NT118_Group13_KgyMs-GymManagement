package com.kgyms.app.ui.member.nutrition

import android.content.Context
import android.os.Process
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.kgyms.app.data.firebase.NutritionFirebaseService
import com.kgyms.app.data.firebase.NutritionRemoteCodec
import com.kgyms.app.data.local.NutritionJournalPreferences
import com.kgyms.app.data.local.NutritionLocalStore
import com.kgyms.app.data.model.*
import com.kgyms.app.data.repository.NutritionRepository
import com.kgyms.app.ui.member.profile.ProfileAppearance
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

private const val DEMO = "demo-kgyms-nutrition"
private const val HOST = "10.0.2.2"
private const val APP_ID = "com.kgyms.app.personalplanemu"
private val initialMeals = NutritionPlanData(
    NutritionSetup(NutritionGoal.MAINTAIN, NutritionActivity.LOW, NutritionDiet.REGULAR),
    mapOf("0:BREAKFAST" to "bf_oats"),
)

class NutritionFirebaseUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun offlineChoiceSurvivesPermissionDenialAndAnOlderCloudPlan() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("nutritionFirebaseDemo") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName == APP_ID)
        check(FirebaseApp.getApps(context).all { it.name != FirebaseApp.DEFAULT_APP_NAME && it.options.projectId == DEMO })
        val options = FirebaseOptions.Builder().setProjectId(DEMO)
            .setApplicationId("1:1234567890:android:nutritionemulator")
            .setApiKey("demo-nutrition-test-key").setDatabaseUrl("https://$DEMO-default-rtdb.firebaseio.com").build()
        val app = FirebaseApp.initializeApp(context, options, "nutrition-demo-" + UUID.randomUUID())
        val auth = FirebaseAuth.getInstance(app).apply { useEmulator(HOST, 9110) }
        val database = FirebaseDatabase.getInstance(app).apply { useEmulator(HOST, 9010) }
        val uid = runBlocking {
            withTimeout(15_000) {
                auth.createUserWithEmailAndPassword("nutrition-${UUID.randomUUID()}@emulator.invalid", "demo-only-credential").await()
                checkNotNull(auth.currentUser).uid
            }
        }
        database.goOffline()
        val transport = NutritionFirebaseService.isolatedEmulator(context, auth, database)
        check(transport.uid == uid)
        val store = NutritionJournalPreferences(context, uid)
        assertTrue(NutritionLocalStore(context, uid).save(initialMeals))
        val repo = NutritionRepository(uid, store, transport, transport::ownsSession)
        try {
            compose.setContent {
                LaunchedEffect(repo) { repo.run() }
                ProfileAppearance(false, "en") {
                    CompositionLocalProvider(LocalNutritionRepository provides repo) {
                        NutritionScreen(uid, onBack = {}, onHome = {})
                    }
                }
            }
            waitFor(repo, NutritionSyncStatus.Offline)
            choosePho()
            assertPho()
            assertTrue(runBlocking { store.read() }.pending)

            compose.runOnIdle { database.goOnline() }
            waitFor(repo, NutritionSyncStatus.Error)
            assertPho()
            assertTrue(runBlocking { store.read() }.pending)

            seedDemo("users/$uid", JSONObject(mapOf("status" to "active", "role" to "user")))
            seedDemo("memberNutritionPlans/$uid",
                JSONObject(NutritionRemoteCodec.encode(NutritionPush(1, "a".repeat(32), initialMeals))))
            compose.onNodeWithTag("nutrition_sync_retry").performScrollTo().performClick()
            waitFor(repo, NutritionSyncStatus.Conflict)
            assertPho()
            assertEquals(initialMeals, repo.view.value.cloudConflict?.plan)
            compose.onNodeWithTag("nutrition_sync_conflict").performScrollTo().performClick()
            compose.onNodeWithTag("nutrition_sync_keep_device").performClick()
            waitFor(repo, NutritionSyncStatus.Synced)
            assertPho()

            val synced = runBlocking { store.read() }
            assertEquals(2, synced.base?.revision)
            assertEquals("bf_pho_ga", synced.base?.plan?.meals?.get("0:BREAKFAST"))
            assertEquals(synced.base, runBlocking { transport.fetch() }.record)
            assertFalse(synced.pending)
            assertEquals(initialMeals, NutritionLocalStore(context, uid).read())
        } finally {
            repo.close()
            database.goOffline()
            auth.signOut()
        }
    }

    private fun waitFor(repo: NutritionRepository, status: NutritionSyncStatus) {
        compose.waitUntil(timeoutMillis = 15_000) { repo.view.value.status == status }
    }

    private fun choosePho() = pickPho(compose)

    private fun assertPho() = assertPhoVisible(compose)

    /** Writes synthetic fixtures only to the fixed Android Emulator host and demo namespace. */
    private fun seedDemo(path: String, data: JSONObject) {
        require(path.matches(Regex("(users|memberNutritionPlans)/[A-Za-z0-9_-]{1,128}")))
        val call = URL("http://$HOST:9010/$path.json?ns=$DEMO-default-rtdb").openConnection() as HttpURLConnection
        call.instanceFollowRedirects = false
        call.connectTimeout = 5_000
        call.readTimeout = 5_000
        call.requestMethod = "PUT"
        call.doOutput = true
        call.setRequestProperty("Authorization", "Bearer owner")
        call.setRequestProperty("Content-Type", "application/json")
        try {
            call.outputStream.use { it.write(data.toString().toByteArray(Charsets.UTF_8)) }
            assertEquals(200, call.responseCode)
        } finally {
            call.disconnect()
        }
    }
}

class NutritionProcessRestartTest {
    @get:Rule val compose = createComposeRule()

    /** Run once per phase in separate instrumentation processes, using the same fresh test UID. */
    @Test fun persistedMealIsRestoredAcrossProcessRestart() {
        val args = InstrumentationRegistry.getArguments()
        val uid = args.getString("nutritionRestartUid")
        assumeTrue(uid != null)
        checkNotNull(uid)
        require(uid.matches(Regex("nutrition-restart-[A-Za-z0-9_-]+")))
        val phase = args.getString("nutritionRestartPhase")
        check(phase == "save" || phase == "reopen")
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName == APP_ID)
        val marker = context.getSharedPreferences(uid + "-test-marker", Context.MODE_PRIVATE)
        val store = NutritionJournalPreferences(context, uid)
        if (phase == "save") {
            assertTrue(NutritionLocalStore(context, uid).save(initialMeals))
        } else {
            val priorPid = marker.getInt("savePid", 0)
            assertTrue(priorPid > 0)
            assertNotEquals("Both phases must run in different processes", priorPid, Process.myPid())
        }
        val repo = NutritionRepository(uid, store, null, { true })
        compose.setContent {
            LaunchedEffect(repo) { repo.load() }
            ProfileAppearance(false, "en") {
                CompositionLocalProvider(LocalNutritionRepository provides repo) {
                    NutritionScreen(uid, onBack = {}, onHome = {})
                }
            }
        }
        if (phase == "save") {
            pickPho(compose)
            assertTrue(marker.edit().putInt("savePid", Process.myPid()).commit())
        } else {
            compose.onNodeWithTag("nutrition_day_0").performScrollTo().performClick()
        }
        assertPhoVisible(compose)
        assertEquals("bf_pho_ga", runBlocking { store.read() }.plan?.meals?.get("0:BREAKFAST"))
        assertEquals(initialMeals, NutritionLocalStore(context, uid).read())
    }
}

private fun pickPho(compose: androidx.compose.ui.test.junit4.ComposeContentTestRule) {
    compose.onNodeWithTag("nutrition_day_0").performScrollTo().performClick()
    compose.onNodeWithTag("nutrition_change_breakfast").performScrollTo().performClick()
    compose.onNodeWithTag("nutrition_meal_options").performScrollToNode(hasTestTag("nutrition_choice_bf_pho_ga"))
    compose.onNodeWithTag("nutrition_choice_bf_pho_ga").performTouchInput { click() }
    compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithTag("nutrition_meal_chooser").fetchSemanticsNodes().isEmpty() }
}

private fun assertPhoVisible(compose: androidx.compose.ui.test.junit4.ComposeContentTestRule) {
    compose.onNodeWithTag("nutrition_meal_name_breakfast").performScrollTo().assertTextEquals("Chicken pho").assertIsDisplayed()
}
