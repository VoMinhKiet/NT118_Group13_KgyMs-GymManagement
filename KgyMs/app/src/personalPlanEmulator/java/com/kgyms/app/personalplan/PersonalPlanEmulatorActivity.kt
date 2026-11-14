package com.kgyms.app.personalplan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kgyms.app.data.firebase.*
import com.kgyms.app.data.local.LocalPersonalPlanRepository
import com.kgyms.app.data.local.LocalWorkoutHistoryStore
import com.kgyms.app.data.repository.WorkoutHistoryRepository
import com.kgyms.app.ui.member.LocalMemberPreferences
import com.kgyms.app.ui.member.rememberMemberPreferences
import com.kgyms.app.ui.member.progress.*
import com.google.firebase.auth.FirebaseAuth
import com.kgyms.app.data.model.*
import com.kgyms.app.data.mock.ReferenceMemberWorkoutDataSource
import com.kgyms.app.data.repository.SyncedPersonalPlanRepository
import com.kgyms.app.data.repository.MemberAccountSession
import com.kgyms.app.data.repository.MemberProfileDataSource
import com.kgyms.app.ui.member.LocalMemberAccount
import com.kgyms.app.ui.member.MemberAccountState
import com.kgyms.app.ui.member.MemberDashboardAppearance
import com.kgyms.app.ui.member.home.MemberHomeAction
import com.kgyms.app.ui.member.home.MemberHomeScreen
import com.kgyms.app.ui.member.plan.*
import com.kgyms.app.ui.member.profile.ProfileAppearance
import com.kgyms.app.ui.member.workout.MemberWorkoutScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A separate launcher; never enters KgyMsApp, production Auth, Profile, avatar or Storage flows. */
class PersonalPlanEmulatorActivity : ComponentActivity() {
    private var connection: PersonalPlanEmulatorConnection? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent { ProfileAppearance(true, "vi") { MemberDashboardAppearance { Harness() } } }
    }
    override fun onDestroy() { connection?.close(); super.onDestroy() }
    @Composable
    private fun Harness() {
        val preferences = remember { getSharedPreferences("personal_plan_emulator_harness", MODE_PRIVATE) }
        var account by rememberSaveable { mutableStateOf(preferences.getString("account", "A") ?: "A") }
        var uid by remember(account) { mutableStateOf(preferences.getString("uid_$account", null)) }
        var loginError by remember(account) { mutableStateOf(false) }
        val endpoint = remember(account) { PersonalPlanEmulatorConnection.create(applicationContext).also { connection = it } }
        val available by endpoint.available.collectAsState()
        var signedUid by remember(endpoint) { mutableStateOf(endpoint.auth.currentUser?.uid) }
        DisposableEffect(endpoint) {
            val listener=FirebaseAuth.AuthStateListener { signedUid=it.currentUser?.uid }
            endpoint.auth.addAuthStateListener(listener)
            onDispose { endpoint.auth.removeAuthStateListener(listener) }
        }
        val scope = rememberCoroutineScope()
        LaunchedEffect(endpoint) {
            try {
                uid = endpoint.signIn(account)
                preferences.edit().putString("account", account).putString("uid_$account", uid).commit()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { loginError = true }
        }
        DisposableEffect(endpoint) { onDispose { endpoint.close() } }
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Text("Personal Plan • FIREBASE EMULATOR ONLY", Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("A", "B").forEach { label -> FilterChip(account == label, { account = label }, label = { Text("Demo $label") }, modifier = Modifier.testTag("demo_account_$label")) }
                TextButton(onClick = { endpoint.setOnline(false) }, modifier = Modifier.testTag("demo_offline")) { Text("Offline") }
                TextButton(onClick = { endpoint.setOnline(true); if (endpoint.auth.currentUser == null) scope.launch {
                    runCatching { endpoint.signIn(account) }.onSuccess { uid = it; loginError = false; preferences.edit().putString("uid_$account", it).commit() }
                } }, modifier = Modifier.testTag("demo_online")) { Text("Online") }
            }
            Text(if (available) "Auth + Database: demo-kgyms-personal-plans" else "Offline / đang kết nối Emulator", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
            if (loginError) Text("Chưa đăng nhập được Auth Emulator. Bật Emulator rồi nhấn Online.", Modifier.padding(16.dp))
            val owner = uid
            if (owner == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            else key(owner, endpoint) {
                val repo = remember(owner, endpoint) { SyncedPersonalPlanRepository(LocalPersonalPlanRepository(applicationContext, owner), PersonalPlanFirebaseService(endpoint, owner)) }
                val state = remember(repo) { PersonalPlanState(repo) }
                val memberSession = remember(owner, endpoint) { MemberAccountSession(owner, DemoPersonalPlanMemberSource) { endpoint.auth.currentUser?.uid ?: owner } }
                val member = remember(memberSession) { MemberAccountState(memberSession, scope) }
                LaunchedEffect(member) { member.load() }
                DisposableEffect(memberSession) { onDispose { memberSession.close() } }
                LaunchedEffect(state) { state.load() }
                LaunchedEffect(state, available) {
                    while (true) {
                        if (available && !state.loading && state.syncStatus != PersonalPlanSyncStatus.Conflict) {
                            try { state.synchronize() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { }
                        }
                        delay(3_000)
                    }
                }
                DisposableEffect(repo) { onDispose { repo.close() } }
                val historyRepo=remember(owner,endpoint,signedUid) {
                    WorkoutHistoryRepository(owner,LocalWorkoutHistoryStore(applicationContext,owner),
                        if(signedUid==owner) WorkoutHistoryFirebaseRemote.emulator(applicationContext,endpoint,owner) else null)
                }
                val history=remember(historyRepo) { WorkoutHistoryState(historyRepo) }
                LaunchedEffect(history) { history.load() }
                LaunchedEffect(history) { history.observeArchive() }
                LaunchedEffect(history) { history.observeStatus() }
                LaunchedEffect(history,history.loading,history.failed) {
                    if(!history.loading && !history.failed) history.runSyncLoop()
                }
                DisposableEffect(historyRepo) { onDispose { historyRepo.close() } }
                val memberPreferences=rememberMemberPreferences(owner,state.active?.config?.sessionsPerWeek)
                CompositionLocalProvider(LocalPersonalPlans provides state, LocalMemberAccount provides member,
                    LocalWorkoutHistory provides history, LocalMemberPreferences provides memberPreferences) { HarnessScreens(owner) }
            }
        }
    }
    @Composable
    private fun ColumnScope.HarnessScreens(uid: String) {
        var screen by rememberSaveable(uid) { mutableStateOf("home") }
        var route by rememberSaveable(uid) { mutableStateOf("plan") }
        var request by rememberSaveable(uid) { mutableIntStateOf(0) }
        fun open(id: String) { route = "session:$id"; request++; screen = "workout" }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { screen = "home" }, modifier = Modifier.testTag("demo_tab_home")) { Text("Home") }
            TextButton(onClick = { screen = "plan" }, modifier = Modifier.testTag("demo_tab_plan")) { Text("Plan") }
            TextButton(onClick = { screen = "progress" }, modifier = Modifier.testTag("demo_tab_progress")) { Text("Progress") }
        }
        Box(Modifier.weight(1f)) {
            when (screen) {
                "progress" -> MemberProgressScreen(uid,LocalMemberPreferences.current?.weeklyTarget ?: 0,{screen="home"},{screen="plan"})
                "create" -> PersonalPlanSetupScreen({ screen = "plan" }, { screen = "plan" })
                "plan" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) { PersonalPlanOverview({ screen = "create" }, ::open) }
                "workout" -> MemberWorkoutScreen(route, request, { screen = "plan" }, { screen = "home" },
                    dataSource = ReferenceMemberWorkoutDataSource, onCreatePersonalPlan = { screen = "create" })
                else -> MemberHomeScreen(onAction = { action -> when (action) {
                    MemberHomeAction.CreatePersonalPlan -> screen = "create"
                    MemberHomeAction.PersonalSchedule -> screen = "plan"
                    is MemberHomeAction.PersonalSession -> open(action.sessionId)
                    MemberHomeAction.WorkoutLibrary -> { route = "library"; request++; screen = "workout" }
                    else -> screen = "plan"
                } }, data = User(uid = uid, fullName = "Demo member").memberHome("vi", 3, System.currentTimeMillis()))
            }
        }
    }
}

/** Read-only identity for the demo launcher, never a Firebase Profile/Storage adapter. */
internal object DemoPersonalPlanMemberSource : MemberProfileDataSource {
    override suspend fun load(uid: String) = User(uid = uid, fullName = "Demo member")
    override suspend fun save(uid: String, edit: MemberProfileEdit) = error("Demo launcher does not edit profiles")
    override suspend fun saveAvatar(uid: String, bytes: ByteArray?): String = error("Storage is forbidden in this launcher")
}
