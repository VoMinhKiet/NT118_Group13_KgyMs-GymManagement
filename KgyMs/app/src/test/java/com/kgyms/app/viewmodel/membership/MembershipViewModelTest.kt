package com.kgyms.app.viewmodel.membership

import com.kgyms.app.data.model.*
import com.kgyms.app.data.repository.MemberMembershipRepository
import com.kgyms.app.data.util.AppDates
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class MembershipViewModelTest {
    private val now = AppDates.parseDay("18/11/2026")!!
    private val gym = GymPackage(id = "gym", name = "Gym", price = 300000, durationDays = 30)
    private val pt = GymPackage(id = "pt", name = "Coaching", type = PackageType.PT, sessions = 12, trainerUid = "trainer")
    private fun membership(id: String = "m", uid: String = "A", status: String = MembershipStatus.ACTIVE,
        packageId: String = "gym") = Membership(id = id, uid = uid, packageId = packageId, status = status,
        startDate = "01/11/2026", endDate = "30/11/2026")

    private class FakeRepository : MemberMembershipRepository {
        var auth: String? = "A"
        val uid = MutableStateFlow<String?>("A")
        val packages = MutableStateFlow<List<GymPackage>>(emptyList())
        val members = mutableMapOf<String, MutableStateFlow<List<Membership>>>()
        val trainers = MutableStateFlow<Map<String, String>>(emptyMap())
        val listeners = mutableMapOf<String, Int>()
        var initialGate: CompletableDeferred<Unit>? = null
        var packageFailure = false
        var membershipFailure = false
        var trainerFailure = false
        override fun currentUid() = auth
        override fun observeUid() = uid
        override fun observePackages(): Flow<List<GymPackage>> = flow {
            initialGate?.await()
            if (packageFailure) error("Permission denied")
            emitAll(packages)
        }
        override fun observeMemberships(uid: String): Flow<List<Membership>> = flow {
            initialGate?.await()
            if (membershipFailure) error("Firebase unavailable")
            emitAll(members.getOrPut(uid) { MutableStateFlow(emptyList()) })
        }.onStart { listeners[uid] = (listeners[uid] ?: 0) + 1 }
            .onCompletion { listeners[uid] = (listeners[uid] ?: 0) - 1 }
        override fun observeTrainerNames(): Flow<Map<String, String>> = flow {
            if (trainerFailure) error("Permission denied")
            emitAll(trainers)
        }
        fun signIn(value: String?) { auth = value; uid.value = value }
        fun publish(uid: String, list: List<Membership>) { members.getOrPut(uid) { MutableStateFlow(emptyList()) }.value = list }
    }

    private fun withModel(test: (FakeRepository, MembershipViewModel) -> Unit) {
        val repository = FakeRepository()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try { test(repository, MembershipViewModel(repository, { now }, scope)) }
        finally { scope.cancel() }
    }

    @Test fun loadingPersistsUntilFirstSnapshotsAndCancellationRemovesListeners() {
        val repo = FakeRepository()
        val gate = CompletableDeferred<Unit>(); repo.initialGate = gate
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val vm = MembershipViewModel(repo, { now }, scope)
            assertTrue(vm.state.value.packagesLoading)
            assertTrue(vm.state.value.membershipsLoading)
            gate.complete(Unit)
            assertFalse(vm.state.value.packagesLoading)
            assertFalse(vm.state.value.membershipsLoading)
            assertEquals(1, repo.listeners["A"])
        } finally { scope.cancel() }
        assertEquals(0, repo.listeners["A"])
    }
    @Test fun catalogContainsBothTypesAndExcludesInactiveAndUnsupportedPackages() = withModel { repo, vm ->
        repo.packages.value = listOf(gym, pt, gym.copy(id = "old", isActive = false), gym.copy(id = "bad", type = "other"))
        assertEquals(setOf(PackageType.MEMBERSHIP, PackageType.PT), vm.state.value.catalog.map { it.type }.toSet())
        assertEquals(setOf("gym", "pt"), vm.state.value.catalog.map { it.id }.toSet())
    }

    @Test fun ownershipFiltersEvenUnexpectedForeignRecordsAndSupportsMultipleMemberships() = withModel { repo, vm ->
        repo.publish("A", listOf(membership("a1"), membership("a2"), membership("foreign", "B")))
        assertEquals(listOf("a1", "a2"), vm.state.value.owned.map { it.membership.id })
    }

    @Test fun allServerStatusesAreKeptAndMappedSeparately() = withModel { repo, vm ->
        val statuses = listOf("active", "pending", "frozen", "expired", "cancelled")
        repo.publish("A", statuses.map { membership(it, status = it) })
        assertEquals(statuses.toSet(), vm.state.value.owned.map { it.membership.status }.toSet())
        assertEquals(setOf(MembershipValidity.Active, MembershipValidity.Pending, MembershipValidity.Frozen,
            MembershipValidity.Expired, MembershipValidity.Cancelled), vm.state.value.owned.map { it.validity }.toSet())
    }

    @Test fun ptDisplaysRealUsageAndHistoricalTrainerRatherThanCurrentPackageAssignment() = withModel { repo, vm ->
        repo.packages.value = listOf(pt)
        repo.trainers.value = mapOf("historical" to "Coach Mai", "trainer" to "Coach An")
        repo.publish("A", listOf(membership(packageId = "pt").copy(trainerUid = "historical", sessionsUsed = 5)))
        val row = vm.state.value.owned.single()
        assertEquals(12, row.totalSessions)
        assertEquals(5, row.usedSessions)
        assertEquals(7, row.remainingSessions)
        assertEquals("Coach Mai", row.trainerName)
    }

    @Test fun missingUsageDoesNotInventZeroAndOveruseDoesNotShowNegativeRemaining() = withModel { repo, vm ->
        repo.packages.value = listOf(pt)
        repo.publish("A", listOf(membership(packageId = "pt").copy(sessionsUsedAvailable = false)))
        assertNull(vm.state.value.owned.single().usedSessions)
        assertNull(vm.state.value.owned.single().remainingSessions)
        repo.publish("A", listOf(membership(packageId = "pt").copy(sessionsUsed = 15)))
        assertEquals(15, vm.state.value.owned.single().usedSessions)
        assertEquals(0, vm.state.value.owned.single().remainingSessions)
    }

    @Test fun deletedPackageRetainsMembershipWithoutInventedNameOrBenefits() = withModel { repo, vm ->
        repo.packages.value = listOf(gym)
        repo.publish("A", listOf(membership()))
        repo.packages.value = emptyList()
        assertEquals("m", vm.state.value.owned.single().membership.id)
        assertNull(vm.state.value.owned.single().pkg)
        assertNull(vm.state.value.owned.single().totalSessions)
    }

    @Test fun noLongerSoldPackageIsStillOwnedAndCanRemainActive() = withModel { repo, vm ->
        repo.packages.value = listOf(gym.copy(isActive = false))
        repo.publish("A", listOf(membership()))
        assertTrue(vm.state.value.catalog.isEmpty())
        assertEquals("Gym", vm.state.value.owned.single().pkg?.name)
        assertEquals(MembershipValidity.Active, vm.state.value.owned.single().validity)
    }

    @Test fun emptyDataCompletesLoadingWithoutInventedPremium() = withModel { _, vm ->
        assertFalse(vm.state.value.packagesLoading)
        assertFalse(vm.state.value.membershipsLoading)
        assertTrue(vm.state.value.owned.isEmpty())
        assertTrue(vm.state.value.catalog.isEmpty())
    }

    @Test fun permissionAndNetworkErrorsAreIndependentAndRetryResubscribes() = withModel { repo, vm ->
        repo.packageFailure = true; repo.membershipFailure = true; vm.retry()
        assertEquals(MembershipLoadError.Permission, vm.state.value.packagesError)
        assertEquals(MembershipLoadError.Unavailable, vm.state.value.membershipsError)
        repo.packageFailure = false; repo.membershipFailure = false
        repo.packages.value = listOf(gym); repo.publish("A", listOf(membership())); vm.retry()
        assertNull(vm.state.value.packagesError); assertNull(vm.state.value.membershipsError)
        assertEquals(1, vm.state.value.owned.size)
        assertEquals(1, repo.listeners["A"])
    }

    @Test fun trainerPermissionFailureDoesNotHideMembershipsOrCatalog() = withModel { repo, vm ->
        repo.trainerFailure = true; repo.packages.value = listOf(pt)
        repo.publish("A", listOf(membership(packageId = "pt"))); vm.retry()
        assertTrue(vm.state.value.trainersUnavailable)
        assertEquals(1, vm.state.value.catalog.size)
        assertEquals(1, vm.state.value.owned.size)
        assertNull(vm.state.value.owned.single().trainerName)
    }

    @Test fun catalogFailureRetainsOwnedRecordWithoutDeclaringItDeleted() = withModel { repo, vm ->
        repo.packageFailure = true; repo.publish("A", listOf(membership())); vm.retry()
        assertEquals(1, vm.state.value.owned.size)
        assertEquals(MembershipLoadError.Permission, vm.state.value.packagesError)
    }

    @Test fun authorityChangesPendingToActiveAndStateUpdatesRealtime() = withModel { repo, vm ->
        repo.publish("A", listOf(membership(status = "pending")))
        assertEquals(MembershipValidity.Pending, vm.state.value.owned.single().validity)
        repo.publish("A", listOf(membership(status = "active")))
        assertEquals(MembershipValidity.Active, vm.state.value.owned.single().validity)
    }

    @Test fun accountSwitchCancelsOldListenerAndLogoutClearsAllData() = withModel { repo, vm ->
        repo.packages.value = listOf(gym); repo.publish("A", listOf(membership()))
        val stale = vm.state.value
        repo.auth = "B"
        assertTrue(vm.forCurrentUser(stale).owned.isEmpty())
        repo.uid.value = "B"
        assertEquals(0, repo.listeners["A"])
        assertTrue(vm.state.value.owned.isEmpty())
        repo.publish("A", listOf(membership("late")))
        assertTrue(vm.state.value.owned.isEmpty())
        repo.publish("B", listOf(membership("b", "B")))
        assertEquals("b", vm.state.value.owned.single().membership.id)
        repo.signIn(null)
        assertEquals(0, repo.listeners["B"])
        assertNull(vm.state.value.uid)
        assertTrue(vm.state.value.owned.isEmpty()); assertTrue(vm.state.value.packages.isEmpty())
    }

    @Test fun dateValidityDoesNotMutateActiveServerStatusAndIncludesEndDate() {
        val m = membership()
        assertEquals(MembershipValidity.Active, membershipValidity(m, AppDates.parseDay("30/11/2026")!! + 86_399_999))
        assertEquals(MembershipValidity.PastEnd, membershipValidity(m, AppDates.parseDay("01/12/2026")!!))
        assertEquals(MembershipValidity.NotStarted, membershipValidity(m, AppDates.parseDay("31/10/2026")!!))
        assertEquals(MembershipStatus.ACTIVE, m.status)
    }

    @Test fun invalidOrMissingDatesNeverGrantEffectiveActiveMembership() {
        for (m in listOf(membership().copy(endDate = ""), membership().copy(endDate = "31/02/2026"),
            membership().copy(startDate = "01/12/2026"), membership().copy(endDate = "30/11/2026junk"))) {
            assertEquals(MembershipValidity.InvalidDates, membershipValidity(m, now))
        }
    }

    @Test fun clockRefreshReevaluatesValidityWithoutServerMutation() {
        var time = now
        val repo = FakeRepository(); val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val vm = MembershipViewModel(repo, { time }, scope)
            repo.publish("A", listOf(membership()))
            time = AppDates.parseDay("01/12/2026")!!; vm.refreshValidity()
            assertEquals(MembershipValidity.PastEnd, vm.state.value.owned.single().validity)
            assertEquals("active", repo.members["A"]!!.value.single().status)
        } finally { scope.cancel() }
    }

    @Test fun memberBoundaryExposesOnlyReadsAndRetryCannotCreateOrActivateMembership() = withModel { repo, vm ->
        assertEquals(setOf("currentUid", "observeUid", "observePackages", "observeMemberships", "observeTrainerNames"),
            MemberMembershipRepository::class.java.declaredMethods.map { it.name }.toSet())
        repo.publish("A", listOf(membership(status = "pending")))
        repeat(3) { vm.retry(); vm.refreshValidity() }
        assertEquals("pending", repo.members["A"]!!.value.single().status)
        assertEquals(1, repo.members["A"]!!.value.size)
    }
}
