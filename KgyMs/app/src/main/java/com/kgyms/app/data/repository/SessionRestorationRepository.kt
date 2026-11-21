package com.kgyms.app.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.kgyms.app.data.firebase.FirestoreService
import com.kgyms.app.data.firebase.ServerUserRead
import com.kgyms.app.data.firebase.UserObservation
import com.kgyms.app.data.local.VerifiedMemberProfileCache
import com.kgyms.app.data.model.User

internal data class SessionIdentity(val uid: String, val email: String)

internal sealed interface SessionProfileRead {
    data class Found(val user: User) : SessionProfileRead
    data object Missing : SessionProfileRead
    data object Offline : SessionProfileRead
    data object Denied : SessionProfileRead
    data class Failed(val cause: Throwable) : SessionProfileRead
}

internal interface SessionProfileSource {
    suspend fun read(uid: String): SessionProfileRead
    fun observe(
        uid: String,
        onConnectivity: (Boolean) -> Unit,
        onRefresh: () -> Unit,
        onDenied: () -> Unit,
    ): AutoCloseable = AutoCloseable { }
}

internal interface VerifiedMemberCache {
    fun read(uid: String): User?
    fun save(user: User)
    fun invalidate(uid: String)
}

internal enum class InvalidAccountReason { MissingProfile, Inactive }

internal sealed interface SessionResolution {
    data object SignedOut : SessionResolution
    data class Verified(val user: User) : SessionResolution
    data class OfflineMember(val user: User) : SessionResolution
    data object OfflineWithoutCache : SessionResolution
    data object AccessDenied : SessionResolution
    data class InvalidAccount(val reason: InvalidAccountReason) : SessionResolution
    data object SessionChanged : SessionResolution
    data class Failed(val cause: Throwable) : SessionResolution
}

/** Resolves navigation authority without ever deriving Admin access from local data. */
internal class SessionRestorationRepository(
    private val identity: () -> SessionIdentity?,
    private val profiles: SessionProfileSource,
    private val cache: VerifiedMemberCache,
) {
    suspend fun resolve(allowOfflineMember: Boolean = true): SessionResolution {
        val owner = identity() ?: return SessionResolution.SignedOut
        if (owner.uid.isBlank()) return SessionResolution.SignedOut
        val read = profiles.read(owner.uid)
        if (identity()?.uid != owner.uid) return SessionResolution.SessionChanged
        return when (read) {
            is SessionProfileRead.Found -> resolveFound(owner, read.user)
            SessionProfileRead.Missing -> {
                runCatching { cache.invalidate(owner.uid) }
                SessionResolution.InvalidAccount(InvalidAccountReason.MissingProfile)
            }
            SessionProfileRead.Denied -> {
                runCatching { cache.invalidate(owner.uid) }
                SessionResolution.AccessDenied
            }
            SessionProfileRead.Offline -> {
                val cached = if (allowOfflineMember) runCatching { cache.read(owner.uid) }.getOrNull() else null
                if (cached != null && cached.uid == owner.uid && cached.isActive &&
                    !cached.isAdmin && cached.hasCompletedOnboarding
                ) SessionResolution.OfflineMember(cached.copy(email = owner.email.ifBlank { cached.email }))
                else SessionResolution.OfflineWithoutCache
            }
            is SessionProfileRead.Failed -> SessionResolution.Failed(read.cause)
        }
    }

    suspend fun revalidate(expectedUid: String): SessionResolution {
        if (identity()?.uid != expectedUid) return SessionResolution.SessionChanged
        return resolve(allowOfflineMember = false)
    }

    fun currentUid(): String? = identity()?.uid

    fun rememberVerifiedMember(user: User) {
        // Cache availability must never decide whether a freshly verified online session can open.
        runCatching {
            if (user.isActive && !user.isAdmin && user.hasCompletedOnboarding) cache.save(user)
            else if (user.uid.isNotBlank()) cache.invalidate(user.uid)
        }
    }

    fun revokeOfflineAccess(uid: String) {
        if (uid.isNotBlank()) runCatching { cache.invalidate(uid) }
    }

    fun observe(
        uid: String,
        onConnectivity: (Boolean) -> Unit,
        onRefresh: () -> Unit,
        onDenied: () -> Unit,
    ): AutoCloseable = profiles.observe(uid, onConnectivity, onRefresh, onDenied)

    private fun resolveFound(owner: SessionIdentity, raw: User): SessionResolution {
        if (raw.uid.isNotBlank() && raw.uid != owner.uid) {
            runCatching { cache.invalidate(owner.uid) }
            return SessionResolution.AccessDenied
        }
        val user = raw.copy(
            uid = owner.uid,
            email = if (raw.isAdmin) raw.email.ifBlank { owner.email } else owner.email.ifBlank { raw.email },
        )
        if (!user.isActive) {
            runCatching { cache.invalidate(owner.uid) }
            return SessionResolution.InvalidAccount(InvalidAccountReason.Inactive)
        }
        if (user.isAdmin) {
            // Admin must be authorized by this online result every time; never persist that role.
            runCatching { cache.invalidate(owner.uid) }
            return SessionResolution.Verified(user)
        }
        runCatching {
            if (user.hasCompletedOnboarding) cache.save(user) else cache.invalidate(owner.uid)
        }
        return SessionResolution.Verified(user)
    }

    companion object {
        fun production(context: Context): SessionRestorationRepository {
            val auth = FirebaseAuth.getInstance()
            val service = FirestoreService()
            val source = object : SessionProfileSource {
                override suspend fun read(uid: String): SessionProfileRead = when (val result = service.getAuthoritativeUser(uid)) {
                    is ServerUserRead.Found -> SessionProfileRead.Found(result.user)
                    ServerUserRead.Missing -> SessionProfileRead.Missing
                    ServerUserRead.Offline -> SessionProfileRead.Offline
                    ServerUserRead.Denied -> SessionProfileRead.Denied
                    is ServerUserRead.Failed -> SessionProfileRead.Failed(result.cause)
                }

                override fun observe(
                    uid: String,
                    onConnectivity: (Boolean) -> Unit,
                    onRefresh: () -> Unit,
                    onDenied: () -> Unit,
                ): AutoCloseable {
                    val observation: UserObservation = service.observeUserRefresh(
                        uid,
                        onConnectivity,
                        onRefresh,
                        onDenied,
                    )
                    return AutoCloseable(observation::close)
                }
            }
            return SessionRestorationRepository(
                identity = {
                    auth.currentUser?.let { SessionIdentity(it.uid, it.email.orEmpty()) }
                },
                profiles = source,
                cache = VerifiedMemberProfileCache(context),
            )
        }
    }
}
