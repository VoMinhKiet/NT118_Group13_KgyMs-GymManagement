package com.kgyms.app.personalplan

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kgyms.app.data.model.*
import com.kgyms.app.data.reference.WorkoutReferenceCatalog
import com.kgyms.app.data.repository.*
import com.kgyms.app.ui.member.*
import com.kgyms.app.ui.member.plan.*
import com.kgyms.app.utils.PersonalPlanGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.TimeZone
import java.util.UUID

/** Production UI/coordinator, with an in-memory remote. Never opens a production FirebaseApp. */
class PersonalPlanProductionUiTest {
    @get:Rule val compose=createComposeRule()
    private class Store(override val uid:String):PersonalPlanSyncStore {
        var value=LocalPersonalPlanSnapshot()
        override suspend fun snapshot()=value
        override suspend fun compareAndSet(expected:LocalPersonalPlanSnapshot,next:LocalPersonalPlanSnapshot) { check(value==expected); value=next }
    }
    private class Remote(override val uid:String):PersonalPlanRemote {
        override val target=PersonalPlanSyncTarget.Firebase
        override val available=MutableStateFlow(true)
        var denied=false
        var archive=RemotePersonalPlanArchive()
        override suspend fun read():RemotePersonalPlanArchive { if(denied)throw PersonalPlanAccessDenied(); return archive }
        override suspend fun append(base:RemotePersonalPlanArchive,plan:PersonalPlan) {
            check(base==archive && plan.uid==uid)
            archive=base.copy(revision=base.revision+1,nextOrdinal=base.nextOrdinal+1,records=base.records+RemotePersonalPlan(plan,base.nextOrdinal,1))
        }
        override suspend fun activate(base:RemotePersonalPlanArchive,id:String) { check(base==archive); archive=base.copy(revision=base.revision+1,activeId=id) }
    }
    @Composable private fun Host(state:PersonalPlanState,signals:MutableStateFlow<PersonalPlanSyncSignal>?=null) {
        val scope=rememberCoroutineScope()
        val member=remember { MemberAccountState(MemberAccountSession(state.uid,DemoPersonalPlanMemberSource) { state.uid },scope) }
        LaunchedEffect(member){member.load()}
        LaunchedEffect(state,signals) { if(signals!=null) PersonalPlanSyncCoordinator().run(signals,state.syncRequests,
            {(state.repository as SyncedPersonalPlanRepository).metadata.revision},{state.syncStatus},{state.synchronize(automatic=true)}) }
        CompositionLocalProvider(LocalPersonalPlans provides state,LocalMemberAccount provides member){MemberApp()}
    }
    @Test fun deniedOrPendingCloudReadIsNotDisplayedAsAnEmptyPlanAndRetryCanConfirmEmpty() = runBlocking<Unit> {
        val uid="ui-${UUID.randomUUID()}"; val remote=Remote(uid).apply{denied=true}
        val state=PersonalPlanState(SyncedPersonalPlanRepository(Store(uid),remote)); state.load()
        compose.setContent { Host(state) }
        compose.onNodeWithTag("personal_remote_unverified").assertExists()
        compose.onNodeWithText("Bạn chưa có lịch tập").assertDoesNotExist()
        state.synchronize(); assertEquals(PersonalPlanSyncStatus.AccessDenied,state.syncStatus)
        compose.onNodeWithTag("member_screen_home").performScrollToNode(hasTestTag("personal_remote_unverified"))
        compose.onNodeWithTag("personal_remote_unverified").assert(hasText("Chưa tải được lịch tập của bạn. Các lịch đã lưu vẫn được giữ lại.") or hasText("Could not load your plan. Your saved plans are safe."))
        remote.denied=false
        compose.onNodeWithTag("personal_sync_retry").performClick()
        compose.waitUntil(5_000){state.syncStatus==PersonalPlanSyncStatus.Synced}
        compose.onNodeWithTag("personal_remote_unverified").assertDoesNotExist()
        compose.onNodeWithTag("personal_sync_status").assertDoesNotExist()
        compose.onNodeWithTag("personal_sync_retry").assertDoesNotExist()
        compose.onAllNodes(hasText("Firebase",substring=true)).assertCountEquals(0)
    }
    @Test fun localSaveAutomaticallySyncsOnReconnectAndHomePlanOpenTheExactSession() = runBlocking<Unit> {
        val uid="ui-${UUID.randomUUID()}"; val remote=Remote(uid); val store=Store(uid)
        val state=PersonalPlanState(SyncedPersonalPlanRepository(store,remote)); state.load()
        val signals=MutableStateFlow(PersonalPlanSyncSignal(false,0,0))
        compose.setContent { Host(state,signals) }
        val now=System.currentTimeMillis(); val zone=TimeZone.getDefault().id; val date=PlanDates.date(now,zone)
        val catalog=WorkoutReferenceCatalog.snapshot()
        val plan=PersonalPlanGenerator.generate(uid,PersonalPlanConfig(WorkoutLevel.Beginner,1,date,zone,listOf(PlanDates.weekday(date,zone))),catalog,now)
        assertTrue(state.save(plan,catalog,now)); val saved=state.active!!
        compose.onNodeWithTag("member_screen_home").performScrollToNode(hasTestTag("personal_today_start"))
        compose.onNodeWithTag("personal_today_start").assertExists()
        assertEquals(PersonalPlanSyncStatus.Pending,state.syncStatus)
        signals.value=PersonalPlanSyncSignal(true,0,1)
        compose.waitUntil(5_000){state.syncStatus==PersonalPlanSyncStatus.Synced}
        assertEquals(listOf(saved),remote.archive.records.map{it.plan})
        compose.onNodeWithTag("personal_today_start").performClick()
        val id=saved.runtimeSessionId(saved.sessions.first())
        compose.onNodeWithTag("workout_list").performScrollToNode(hasTestTag("hero_personal-workout-$id"))
        compose.onNodeWithTag("hero_personal-workout-$id").assertExists()
        compose.onNodeWithTag("member_tab_progress").performClick()
        compose.onNodeWithTag("progress_completed").assertDoesNotExist()
    }
    @Test fun differentDevicePlanChoiceStillRequiresExplicitSelectionWithoutTechnicalLabels()=runBlocking<Unit> {
        val uid="ui-${UUID.randomUUID()}";val remote=Remote(uid)
        val state=PersonalPlanState(SyncedPersonalPlanRepository(Store(uid),remote));state.load()
        val now=System.currentTimeMillis();val zone=TimeZone.getDefault().id;val date=PlanDates.date(now,zone)
        val catalog=WorkoutReferenceCatalog.snapshot();val day=PlanDates.weekday(date,zone)
        val original=PersonalPlanGenerator.generate(uid,PersonalPlanConfig(WorkoutLevel.Beginner,1,date,zone,listOf(day)),catalog,now)
        val other=PersonalPlanGenerator.seal(PersonalPlanGenerator.generate(uid,PersonalPlanConfig(WorkoutLevel.Beginner,2,date,zone,listOf(day,(day+3)%7)),catalog,now))
        assertTrue(state.save(original,catalog,now));val current=state.active!!
        remote.archive=RemotePersonalPlanArchive(1,2,other.id,listOf(RemotePersonalPlan(other,1,1)))
        state.synchronize();assertTrue(state.activeSyncConflict)
        compose.setContent{Host(state)}
        compose.onNodeWithTag("member_tab_plan").performClick()
        compose.onNodeWithTag("workout_list").performScrollToNode(hasTestTag("personal_sync_keep_device"))
        compose.onNodeWithTag("personal_sync_keep_device").assert(hasText("Giữ lịch đang dùng") or hasText("Keep current plan"))
        compose.onNodeWithTag("personal_sync_use_remote").assert(hasText("Dùng lịch mới nhận") or hasText("Use the other plan"))
        listOf("Firebase","đồng bộ","sync","revision").forEach{compose.onAllNodes(hasText(it,substring=true,ignoreCase=true)).assertCountEquals(0)}
        assertEquals(current.id,state.active!!.id)
        compose.onNodeWithTag("workout_list").performScrollToNode(hasTestTag("personal_sync_use_remote"))
        compose.onNodeWithTag("personal_sync_use_remote").performClick()
        compose.waitUntil(5_000){state.syncStatus==PersonalPlanSyncStatus.Synced}
        assertEquals(other.id,state.active!!.id);assertEquals(setOf(current,other),state.archive.plans.toSet())
        compose.onNodeWithTag("personal_sync_retry").assertDoesNotExist()
    }
}
