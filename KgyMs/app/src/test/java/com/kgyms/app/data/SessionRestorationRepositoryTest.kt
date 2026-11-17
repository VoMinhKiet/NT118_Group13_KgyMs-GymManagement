package com.kgyms.app.data

import com.kgyms.app.data.model.User
import com.kgyms.app.data.repository.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SessionRestorationRepositoryTest {
    private val memberA = member("A")

    @Test fun signedOutGoesToWelcomeWithoutReadingAProfile() = runBlocking {
        val source = FakeSource(SessionProfileRead.Failed(IllegalStateException("must not read")))
        val repository = repository(identity = null, source = source)

        assertEquals(SessionResolution.SignedOut, repository.resolve())
        assertEquals(0, source.reads)
    }

    @Test fun verifiedMemberIsCachedAndRestoredAfterProcessRestartOffline() = runBlocking {
        val cache = MemoryCache()
        val online = repository(SessionIdentity("A", "auth@example.com"),
            FakeSource(SessionProfileRead.Found(memberA.copy(email = ""))), cache)
        val verified = online.resolve() as SessionResolution.Verified
        assertEquals("auth@example.com", verified.user.email)
        assertEquals(memberA.copy(email = "auth@example.com"), cache.read("A"))

        // A new repository represents a process restart; only the durable cache is shared.
        val restarted = repository(SessionIdentity("A", "auth@example.com"),
            FakeSource(SessionProfileRead.Offline), cache)
        val offline = restarted.resolve() as SessionResolution.OfflineMember
        assertEquals("A", offline.user.uid)
    }

    @Test fun adminRequiresOnlineVerificationAndIsNeverCached() = runBlocking {
        val cache = MemoryCache(mutableMapOf("A" to memberA))
        val admin = memberA.copy(role = "admin")
        val verified = repository(SessionIdentity("A", admin.email),
            FakeSource(SessionProfileRead.Found(admin)), cache).resolve()

        assertTrue((verified as SessionResolution.Verified).user.isAdmin)
        assertNull(cache.read("A"))
        val offline = repository(SessionIdentity("A", admin.email),
            FakeSource(SessionProfileRead.Offline), cache).resolve()
        assertEquals(SessionResolution.OfflineWithoutCache, offline)
    }

    @Test fun unfinishedMemberUsesOnboardingOnlineButCannotBypassItOffline() = runBlocking {
        val cache = MemoryCache(mutableMapOf("A" to memberA))
        val unfinished = memberA.copy(gender = "", age = 0)
        val result = repository(SessionIdentity("A", unfinished.email),
            FakeSource(SessionProfileRead.Found(unfinished)), cache).resolve()

        assertFalse((result as SessionResolution.Verified).user.hasCompletedOnboarding)
        assertNull(cache.read("A"))
        assertEquals(SessionResolution.OfflineWithoutCache,
            repository(SessionIdentity("A", unfinished.email),
                FakeSource(SessionProfileRead.Offline), cache).resolve())
    }

    @Test fun coldStartOfflineWithoutVerifiedCacheCannotEnterMember() = runBlocking {
        val result = repository(SessionIdentity("A", memberA.email),
            FakeSource(SessionProfileRead.Offline)).resolve()
        assertEquals(SessionResolution.OfflineWithoutCache, result)
    }

    @Test fun accountCachesAreStrictlySeparatedAcrossLogoutAndLogin() = runBlocking {
        var identity: SessionIdentity? = SessionIdentity("A", memberA.email)
        val cache = MemoryCache(mutableMapOf("A" to memberA))
        val source = FakeSource(SessionProfileRead.Offline)
        val repository = SessionRestorationRepository({ identity }, source, cache)
        assertEquals("A", (repository.resolve() as SessionResolution.OfflineMember).user.uid)

        identity = null
        assertEquals(SessionResolution.SignedOut, repository.resolve())
        identity = SessionIdentity("B", "b@example.com")
        assertEquals(SessionResolution.OfflineWithoutCache, repository.resolve())
        assertNull(cache.read("B"))
    }

    @Test fun inactiveOrMissingAccountIsBlockedAndRevokesOfflineEntry() = runBlocking {
        val cache = MemoryCache(mutableMapOf("A" to memberA))
        val inactive = repository(SessionIdentity("A", memberA.email),
            FakeSource(SessionProfileRead.Found(memberA.copy(status = "locked"))), cache).resolve()
        assertEquals(SessionResolution.InvalidAccount(InvalidAccountReason.Inactive), inactive)
        assertNull(cache.read("A"))

        cache.save(memberA)
        val missing = repository(SessionIdentity("A", memberA.email),
            FakeSource(SessionProfileRead.Missing), cache).resolve()
        assertEquals(SessionResolution.InvalidAccount(InvalidAccountReason.MissingProfile), missing)
        assertNull(cache.read("A"))
    }

    @Test fun permissionDeniedNeverFallsBackToCachedMember() = runBlocking {
        val cache = MemoryCache(mutableMapOf("A" to memberA))
        val result = repository(SessionIdentity("A", memberA.email),
            FakeSource(SessionProfileRead.Denied), cache).resolve()
        assertEquals(SessionResolution.AccessDenied, result)
        assertNull(cache.read("A"))
    }

    @Test fun temporaryNetworkFailureKeepsTheSessionAndLocalMemberAccess() = runBlocking {
        val cache = MemoryCache(mutableMapOf("A" to memberA))
        val result = repository(SessionIdentity("A", memberA.email),
            FakeSource(SessionProfileRead.Offline), cache).resolve()
        assertTrue(result is SessionResolution.OfflineMember)
        assertEquals(memberA, cache.read("A"))
    }

    @Test fun lateResponseCannotCrossFromAccountAToAccountB() = runBlocking {
        var identity = SessionIdentity("A", memberA.email)
        val source = object : SessionProfileSource {
            override suspend fun read(uid: String): SessionProfileRead {
                identity = SessionIdentity("B", "b@example.com")
                return SessionProfileRead.Found(memberA)
            }
        }
        val result = SessionRestorationRepository({ identity }, source, MemoryCache()).resolve()
        assertEquals(SessionResolution.SessionChanged, result)
    }

    private fun member(uid: String) = User(
        uid = uid,
        fullName = "Member $uid",
        email = "$uid@example.com",
        gender = "Other",
        age = 25,
        role = "user",
        status = "active",
    )

    private fun repository(
        identity: SessionIdentity?,
        source: SessionProfileSource,
        cache: MemoryCache = MemoryCache(),
    ) = SessionRestorationRepository({ identity }, source, cache)

    private class FakeSource(private val result: SessionProfileRead) : SessionProfileSource {
        var reads = 0
        override suspend fun read(uid: String): SessionProfileRead {
            reads++
            return result
        }
    }

    private class MemoryCache(
        private val records: MutableMap<String, User> = mutableMapOf(),
    ) : VerifiedMemberCache {
        override fun read(uid: String): User? = records[uid]
        override fun save(user: User) {
            require(user.isActive && !user.isAdmin && user.hasCompletedOnboarding)
            records[user.uid] = user
        }
        override fun invalidate(uid: String) {
            records.remove(uid)
        }
    }
}
