package com.kgyms.app.viewmodel.membership

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgyms.app.data.repository.FirebaseMemberMembershipRepository
import com.kgyms.app.data.repository.MemberMembershipRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MembershipViewModel(
    private val repository: MemberMembershipRepository = FirebaseMemberMembershipRepository(),
    private val clock: () -> Long = System::currentTimeMillis,
    externalScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope = externalScope ?: viewModelScope
    private val reload = MutableStateFlow(0)
    private val _state = MutableStateFlow(MembershipUiState())
    val state = _state.asStateFlow()
    private val observation: Job
    private val validityClock: Job

    init {
        observation = scope.launch {
            combine(repository.observeUid().distinctUntilChanged(), reload) { uid, _ -> uid }
                .collectLatest { uid ->
                    // Cancel old listeners before subscribing for the new UID (also on retry).
                    _state.value = MembershipUiState(uid = uid, packagesLoading = uid != null,
                        membershipsLoading = uid != null, now = clock())
                    if (uid == null) return@collectLatest
                    coroutineScope {
                        launch {
                            repository.observePackages().catch { error ->
                                updateFor(uid) { it.copy(packages = emptyList(), packagesLoading = false, packagesError = error.loadError()) }
                            }.collect { packages ->
                                updateFor(uid) { it.copy(packages = packages, packagesLoading = false, packagesError = null) }
                            }
                        }
                        launch {
                            repository.observeMemberships(uid).catch { error ->
                                updateFor(uid) { it.copy(memberships = emptyList(), membershipsLoading = false, membershipsError = error.loadError()) }
                            }.collect { memberships ->
                                updateFor(uid) { it.copy(memberships = memberships.filter { m -> m.uid == uid }, membershipsLoading = false, membershipsError = null, now = clock()) }
                            }
                        }
                        launch {
                            repository.observeTrainerNames().catch {
                                updateFor(uid) { it.copy(trainerNames = emptyMap(), trainersUnavailable = true) }
                            }.collect { names -> updateFor(uid) { it.copy(trainerNames = names, trainersUnavailable = false) } }
                        }
                    }
                }
        }
        validityClock = scope.launch {
            while (true) {
                delay(60_000)
                refreshValidity()
            }
        }
    }

    /** Hide the first stale frame even before the Auth flow callback has been dispatched. */
    fun forCurrentUser(snapshot: MembershipUiState): MembershipUiState {
        val uid = repository.currentUid()
        return if (snapshot.uid == uid) snapshot else MembershipUiState(uid = uid,
            packagesLoading = uid != null, membershipsLoading = uid != null, now = clock())
    }

    fun retry() { reload.update { it + 1 } }
    fun refreshValidity() { _state.update { it.copy(now = clock()) } }

    private inline fun updateFor(uid: String, transform: (MembershipUiState) -> MembershipUiState) {
        _state.update { if (it.uid == uid && repository.currentUid() == uid) transform(it) else it }
    }

    override fun onCleared() { observation.cancel(); validityClock.cancel(); super.onCleared() }

    private fun Throwable.loadError() = if (message.orEmpty().contains("permission", true))
        MembershipLoadError.Permission else MembershipLoadError.Unavailable
}
