package com.kgyms.app.data.repository

import com.kgyms.app.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Foreground lifetime is owned by Compose. No periodic archive polling, bounded offline retries. */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
internal class PersonalPlanSyncCoordinator(private val retryDelays: List<Long> = listOf(5_000, 20_000, 60_000), private val debounceMillis: Long = 250) {
    suspend fun run(signals: StateFlow<PersonalPlanSyncSignal>, localEdits: Flow<Unit>,
        revision: () -> Long?, status: () -> PersonalPlanSyncStatus, synchronize: suspend () -> Unit,
    ): Unit = coroutineScope {
        val retries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        var retry: Job? = null
        var attempts = 0
        var seenEpoch: Long? = null
        merge(signals.map { false }, localEdits.map { true }, retries.map { true }).debounce(debounceMillis).conflate().collect { local ->
            val signal = signals.value
            if (!signal.connected) return@collect
            val reconnect = seenEpoch != signal.epoch
            val remoteChanged = signal.revision?.let { it > (revision() ?: -1) } == true
            if (!local && !reconnect && !remoteChanged && status() != PersonalPlanSyncStatus.Pending) return@collect
            seenEpoch = signal.epoch
            retry?.cancel(); retry = null
            synchronize()
            if (status() == PersonalPlanSyncStatus.Pending && attempts < retryDelays.size) {
                val delayMillis = retryDelays[attempts++]
                retry = launch { delay(delayMillis); retries.emit(Unit) }
            } else if (status() == PersonalPlanSyncStatus.Synced) attempts = 0
        }
    }
}
