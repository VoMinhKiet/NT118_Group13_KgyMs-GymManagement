package com.kgyms.app.data.repository

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.kgyms.app.data.firebase.StaffService
import com.kgyms.app.data.model.GymPackage
import com.kgyms.app.data.model.Membership
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf

/** Read-only boundary: checkout and membership mutations are deliberately not exposed. */
interface MemberMembershipRepository {
    fun currentUid(): String?
    fun observeUid(): Flow<String?>
    fun observePackages(): Flow<List<GymPackage>>
    fun observeMemberships(uid: String): Flow<List<Membership>>
    fun observeTrainerNames(): Flow<Map<String, String>>
}

class FirebaseMemberMembershipRepository : MemberMembershipRepository {
    private val auth by lazy {
        val app = try { FirebaseApp.getInstance() } catch (_: IllegalStateException) { null }
        app?.let(FirebaseAuth::getInstance)
    }
    private val packages by lazy { PackageRepository() }
    private val payments by lazy { PaymentRepository() }
    private val staff by lazy { StaffService() }

    override fun currentUid(): String? = auth?.currentUser?.uid
    override fun observeUid(): Flow<String?> {
        val session = auth ?: return flowOf(null)
        return callbackFlow {
            val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
            session.addAuthStateListener(listener)
            awaitClose { session.removeAuthStateListener(listener) }
        }
    }

    override fun observePackages() = packages.observePackages()
    override fun observeMemberships(uid: String): Flow<List<Membership>> {
        require(uid.isNotBlank())
        return payments.observeMyMemberships(uid)
    }

    override fun observeTrainerNames(): Flow<Map<String, String>> = staff.observeStaff().map { list ->
        // staff is readable by members. Never read users or Admin's getTrainers().
        list.filter { it.fullName.isNotBlank() }.associate { it.uid to it.fullName }
    }
}
