package com.kgyms.app.ui.member.progress

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kgyms.app.data.firebase.WorkoutHistoryFirebaseRemote
import com.kgyms.app.data.local.LocalWorkoutHistoryStore
import com.kgyms.app.data.model.*
import com.kgyms.app.data.repository.WorkoutHistoryRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal val LocalWorkoutHistory = staticCompositionLocalOf<WorkoutHistoryState?> { null }

@Stable
internal class WorkoutHistoryState(val repository: WorkoutHistoryRepository) {
    val uid get() = repository.uid
    var archive by mutableStateOf(WorkoutHistoryArchive()); private set
    var loading by mutableStateOf(true); private set
    var failed by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var sync by mutableStateOf(HistorySyncState.LocalOnly); private set
    var actionFailed by mutableStateOf(false); private set
    suspend fun load() {
        loading=true; failed=false
        try { repository.load(); archive=repository.archive.value }
        catch(e:CancellationException){throw e} catch(_:Exception){failed=true}
        finally{loading=false}
    }
    suspend fun observeArchive() { repository.archive.collect { archive=it } }
    suspend fun observeStatus() { repository.status.collect { sync=it } }
    private suspend fun local(action:suspend()->Unit):Boolean {
        if(loading || failed || busy)return false
        busy=true; actionFailed=false
        return try { action(); archive=repository.archive.value; sync=repository.status.value; true }
        catch(e:CancellationException){throw e} catch(_:Exception){actionFailed=true; false}
        finally{busy=false}
    }
    suspend fun begin(context:WorkoutRunContext,now:Long)=local { repository.begin(context,now) }
    suspend fun mark(key:String,id:String)=local { repository.mark(key,id) }
    suspend fun finish(key:String,now:Long)=local { repository.finish(key,now) }
    suspend fun synchronize() {
        if(loading || failed || sync==HistorySyncState.Syncing)return
        sync=HistorySyncState.Syncing
        try { repository.synchronize(); archive=repository.archive.value; sync=repository.status.value }
        catch(e:CancellationException){sync=repository.status.value;throw e}
    }
    suspend fun retry() {
        if(failed)load()
        repository.remote?.let { it.stop(); it.start() }
        synchronize()
    }
}

@OptIn(FlowPreview::class,ExperimentalCoroutinesApi::class)
internal suspend fun WorkoutHistoryState.runSyncLoop():Unit=coroutineScope {
    val remote=repository.remote ?: return@coroutineScope
    remote.start()
    try {
        var retry:Job?=null;var tries=0
        val retryEvents=MutableSharedFlow<Unit>(extraBufferCapacity=1)
        merge(remote.changes.map{Unit},remote.available.map{Unit},repository.requests,retryEvents)
            .debounce(400).conflate().collect {
                if(remote.available.value && sync!=HistorySyncState.Conflict) {
                    retry?.cancel();synchronize()
                    if(sync==HistorySyncState.Pending && tries<3) {
                        val pause=listOf(5_000L,20_000L,60_000L)[tries++]
                        retry=launch{delay(pause);retryEvents.emit(Unit)}
                    } else if(sync==HistorySyncState.Synced)tries=0
                }
            }
    } finally {remote.stop()}
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@Composable
internal fun rememberWorkoutHistoryState(uid:String?):WorkoutHistoryState? {
    val context=LocalContext.current.applicationContext
    val state=remember(uid,context) { uid?.let {
        WorkoutHistoryState(WorkoutHistoryRepository(it,LocalWorkoutHistoryStore(context,it),
            runCatching{WorkoutHistoryFirebaseRemote.production(context,it)}.getOrNull()))
    } }
    LaunchedEffect(state) { state?.load() }
    LaunchedEffect(state) { state?.observeArchive() }
    LaunchedEffect(state) { state?.observeStatus() }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle){mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))}
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,_->foreground=lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)}
        lifecycle.addObserver(observer); onDispose{lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(state,foreground,state?.loading,state?.failed) {
        val s=state; val remote=s?.repository?.remote
        if(s!=null && remote!=null && foreground && !s.loading && !s.failed) {
            s.runSyncLoop()
        }
    }
    DisposableEffect(state){onDispose{state?.repository?.close()}}
    return state
}
