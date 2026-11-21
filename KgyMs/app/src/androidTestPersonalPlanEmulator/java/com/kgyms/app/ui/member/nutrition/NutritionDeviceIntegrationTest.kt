package com.kgyms.app.ui.member.nutrition

import android.content.Context
import android.net.ConnectivityManager
import android.os.Process
import android.provider.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
private const val HOST = "127.0.0.1"
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
        check(Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1)
        check(context.getSystemService(ConnectivityManager::class.java).activeNetwork == null)
        check(FirebaseApp.getApps(context).all { it.name != FirebaseApp.DEFAULT_APP_NAME && it.options.projectId == DEMO })
        val options = FirebaseOptions.Builder().setProjectId(DEMO)
            .setApplicationId("1:1234567890:android:nutritionemulator")
            .setApiKey("demo-nutrition-test-key").setDatabaseUrl("https://$DEMO-default-rtdb.firebaseio.com").build()
        val app = FirebaseApp.initializeApp(context, options, "nutrition-demo-" + UUID.randomUUID())
        val auth = FirebaseAuth.getInstance(app).apply { useEmulator(HOST, 9110) }
        val database = FirebaseDatabase.getInstance(app).apply { useEmulator(HOST, 9010) }
        fun createDemoUser(): String = runBlocking {
            withTimeout(15_000) {
                auth.createUserWithEmailAndPassword("nutrition-${UUID.randomUUID()}@emulator.invalid", "demo-only-credential").await()
                checkNotNull(auth.currentUser).uid
            }
        }
        val uid = createDemoUser()
        database.goOffline()
        val transport = NutritionFirebaseService.isolatedEmulator(context, auth, database)
        check(transport.uid == uid)
        val store = NutritionJournalPreferences(context, uid)
        assertTrue(NutritionLocalStore(context, uid).save(initialMeals))
        val repo = NutritionRepository(uid, store, transport, transport::ownsSession)
        var screenRepo by mutableStateOf<NutritionRepository?>(repo)
        var nextRepo: NutritionRepository? = null
        try {
            compose.setContent {
                val visibleRepo = screenRepo
                LaunchedEffect(visibleRepo) { visibleRepo?.run() }
                ProfileAppearance(false, "en") {
                    CompositionLocalProvider(LocalNutritionRepository provides visibleRepo) {
                        NutritionScreen(visibleRepo?.uid, onBack = {}, onHome = {})
                    }
                }
            }
            waitFor(repo, NutritionSyncStatus.Offline)
            compose.onNodeWithText("Offline").performScrollTo().assertIsDisplayed()
            choosePho()
            assertPho()
            assertTrue(runBlocking { store.read() }.pending)
            waitFor(repo, NutritionSyncStatus.Offline)
            compose.onNodeWithText("Changes are saved on this device and have not synced.")
                .performScrollTo().assertIsDisplayed()

            compose.runOnIdle { database.goOnline() }
            waitFor(repo, NutritionSyncStatus.Error)
            compose.onNodeWithText("Sync failed").performScrollTo().assertIsDisplayed()
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
            compose.onNodeWithText("Meal plan synced").performScrollTo().assertIsDisplayed()
            assertPho()

            val synced = runBlocking { store.read() }
            assertEquals(2, synced.base?.revision)
            assertEquals("bf_pho_ga", synced.base?.plan?.meals?.get("0:BREAKFAST"))
            assertEquals(synced.base, runBlocking { transport.fetch() }.record)
            assertFalse(synced.pending)
            assertEquals(initialMeals, NutritionLocalStore(context, uid).read())

            compose.runOnIdle {
                auth.signOut()
                screenRepo = null
            }
            assertNull(auth.currentUser)
            compose.onNodeWithText("Please sign in to save a personal meal plan.").assertIsDisplayed()
            assertFalse(runBlocking { repo.saveMeal(NutritionMealTarget(0, MealSlot.BREAKFAST), "bf_oats") })

            val nextUid = createDemoUser()
            assertNotEquals(uid, nextUid)
            val nextStore = NutritionJournalPreferences(context, nextUid)
            assertNull(runBlocking { nextStore.read() }.plan)
            seedDemo("users/$nextUid", JSONObject(mapOf("status" to "active", "role" to "user")))
            val nextPlan = initialMeals.copy(meals = mapOf("0:BREAKFAST" to "bf_pancake_yen_mach"))
            seedDemo("memberNutritionPlans/$nextUid",
                JSONObject(NutritionRemoteCodec.encode(NutritionPush(1, "b".repeat(32), nextPlan))))
            val nextTransport = NutritionFirebaseService.isolatedEmulator(context, auth, database)
            val second = NutritionRepository(nextUid, nextStore, nextTransport, nextTransport::ownsSession)
            nextRepo = second
            compose.runOnIdle { screenRepo = second }
            waitFor(second, NutritionSyncStatus.Synced)
            compose.onNodeWithTag("nutrition_day_0").performScrollTo().performClick()
            val nextMeal = requireNotNull(NutritionMealCatalog.byId("bf_pancake_yen_mach"))
            compose.onNodeWithTag("nutrition_meal_name_breakfast").performScrollTo()
                .assertTextEquals(nextMeal.en)
            val crossOwnerRead = runBlocking {
                withTimeout(8_000) {
                    runCatching { database.getReference("memberNutritionPlans/$uid").get().await() }
                }
            }
            assertTrue(crossOwnerRead.exceptionOrNull()?.message.orEmpty().contains("permission denied", ignoreCase = true))
            assertEquals(synced, runBlocking { store.read() })
        } finally {
            nextRepo?.close()
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
