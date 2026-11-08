package com.kgyms.app.data

import com.kgyms.app.data.model.*
import com.kgyms.app.data.repository.MemberAccountSession
import com.kgyms.app.data.repository.MemberProfileDataSource
import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.*

/** Test-only in-memory persistence, never a substitute for Firebase in production. */
class MemberAccountDataTest {
    private val registered = User(uid = "A", fullName = "Nguyễn Minh Anh", email = "a@example.com",
        gender = "Female", age = 24, weight = 57, weightUnit = "KG", height = 162)

    @Test fun onboardingSurvivesProjectionAndMissingFieldsStayMissing() {
        val profile = registered.memberProfile("vi")
        assertEquals("Nữ", profile.gender)
        assertEquals("24", profile.age)
        assertEquals("57 KG", profile.weight)
        assertEquals("162 cm", profile.height)
        listOf(profile.phone, profile.birthday, profile.membership, profile.workouts,
            profile.daysActive, profile.averageRating).forEach { assertEquals("Chưa cập nhật", it) }
        assertEquals("MA", profile.initials)
    }

    @Test fun userRoleAndActiveStatusNeverGrantPremium() {
        assertEquals("Not updated", registered.copy(role = "user", status = "active").memberProfile("en").membership)
        assertEquals("Not updated", User(uid = "B").memberProfile("en").age)
    }

    @Test fun editWhitelistPreservesOnboardingAndAuthFields() {
        val updates = MemberProfileEdit("  New Name  ", "", "").updates()
        assertEquals(setOf("fullName", "phone", "birthday"), updates.keys)
        assertEquals("New Name", updates["fullName"])
        assertEquals("", updates["phone"])
        assertEquals("", updates["birthday"])
    }

    @Test fun invalidPhoneAndCalendarDateAreRejectedBeforeWriting() {
        assertTrue(runCatching { MemberProfileEdit("Anh", "abc", "").updates() }.isFailure)
        assertTrue(runCatching { MemberProfileEdit("Anh", "", "31/02/2024").updates() }.isFailure)
        assertTrue(runCatching { MemberProfileEdit("Anh", "", "29/02/2024").updates() }.isSuccess)
        assertTrue(runCatching { MemberProfileEdit(" ", "", "").updates() }.isFailure)
    }

    @Test fun accountNamespacesCannotReuseLegacySampleAvatarOrAnotherUid() {
        assertNotEquals(memberAccountKey("A"), memberAccountKey("B"))
        assertEquals(memberAccountKey("A"), memberAccountKey("A"))
        assertTrue(memberAccountKey("../A").matches(Regex("[0-9a-f]{64}")))
    }

    @Test fun saveThenReloginReadsPersistedFieldsWithoutInventingBirthdayOrAvatar() {
        val source = MemorySource(mutableMapOf("A" to registered))
        val first = MemberAccountSession("A", source) { "A" }
        val saved = immediate { first.save(MemberProfileEdit("New Name", "0901234567", "")) }
        first.close()
        val again = immediate { MemberAccountSession("A", source) { "A" }.load() }
        assertEquals(saved, again)
        assertEquals("New Name", again.fullName)
        assertEquals("0901234567", again.phone)
        assertEquals("", again.birthday)
        assertEquals("", again.avatarUrl)
        assertEquals(registered.gender, again.gender)
        assertEquals(registered.weight, again.weight)
        assertEquals(registered.email, again.email)
        assertEquals(registered.role, again.role)
    }

    @Test fun switchingAccountsRejectsOldWritesAndDoesNotCopyAvatar() {
        var current = "A"
        val source = MemorySource(mutableMapOf("A" to registered.copy(avatarUrl = "https://example.com/a.jpg"),
            "B" to User(uid = "B", fullName = "B")))
        val a = MemberAccountSession("A", source) { current }
        assertTrue(immediate { a.load() }.avatarUrl.isNotBlank())
        current = "B"
        assertTrue(runCatching { immediate { a.save(MemberProfileEdit("Leak", "", "")) } }.isFailure)
        val b = MemberAccountSession("B", source) { current }
        assertEquals("", immediate { b.load() }.avatarUrl)
        assertEquals("B", immediate { b.load() }.fullName)
        assertEquals(0, source.writes)
    }

    @Test fun lateResponseFromAIsRejectedAfterSwitchToB() {
        var current = "A"
        var pending: Continuation<User>? = null
        var outcome: Result<User>? = null
        val source = object : MemberProfileDataSource {
            override suspend fun load(uid: String): User = suspendCoroutine { pending = it }
            override suspend fun save(uid: String, edit: MemberProfileEdit) = Unit
            override suspend fun saveAvatar(uid: String, bytes: ByteArray?) = ""
        }
        val session = MemberAccountSession("A", source) { current }
        suspend { session.load() }.startCoroutine(object : Continuation<User> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<User>) { outcome = result }
        })
        current = "B"
        pending!!.resume(registered)
        assertTrue(outcome!!.isFailure)
    }

    @Test fun closedSessionCannotReappearAfterSameAccountLogsInAgain() {
        val source = MemorySource(mutableMapOf("A" to registered))
        val session = MemberAccountSession("A", source) { "A" }
        session.close()
        assertTrue(runCatching { immediate { session.load() } }.isFailure)
    }

    @Test fun deniedSaveDoesNotProduceSuccessOrMutateProfile() {
        val source = MemorySource(mutableMapOf("A" to registered), failWrites = true)
        val session = MemberAccountSession("A", source) { "A" }
        assertTrue(runCatching { immediate { session.save(MemberProfileEdit("Changed", "", "")) } }.isFailure)
        assertEquals(registered, immediate { session.load() })
    }

    @Test fun wrongUidMissingAndInactiveProfilesAreRejected() {
        val source = MemorySource(mutableMapOf("A" to registered.copy(uid = "B")))
        assertTrue(runCatching { immediate { MemberAccountSession("A", source) { "A" }.load() } }.isFailure)
        source.records["A"] = registered.copy(status = "inactive")
        assertTrue(runCatching { immediate { MemberAccountSession("A", source) { "A" }.load() } }.isFailure)
        source.records.clear()
        assertTrue(runCatching { immediate { MemberAccountSession("A", source) { "A" }.load() } }.isFailure)
    }

    @Test fun homeDoesNotAttributeFixtureMembershipTrainerOrActivityToAccount() {
        val home = registered.memberHome("vi", 3, 1_800_000_000_000L)
        assertEquals(registered.fullName, home.memberName)
        assertFalse(home.membership.available)
        assertEquals("Chưa cập nhật", home.membership.packageName)
        assertNull(home.trainer)
        assertTrue(home.notifications.isEmpty())
        assertTrue(home.activity.isEmpty())
        assertTrue(home.weeklyGoal.completedSessions.isEmpty())
        assertTrue(home.workouts.isNotEmpty()) // Reference catalog remains accessible.
    }

    @Test fun referenceCatalogHasNoPersonalRecordsOrTrainerAssignments() {
        val catalog = com.kgyms.app.data.mock.ReferenceMemberWorkoutDataSource.snapshot(1_800_000_000_000L)
        assertTrue(catalog.sessions.isEmpty())
        assertNull(catalog.trainer)
        assertNull(catalog.trainerPlan)
        assertTrue(catalog.messages.isEmpty())
        assertTrue(catalog.workouts.isNotEmpty())
        assertTrue(catalog.exercises.isNotEmpty())
    }

    @Test fun deniedAvatarPersistencePropagatesFailureInsteadOfPublishingFakeUrl() {
        val source = MemorySource(mutableMapOf("A" to registered))
        val session = MemberAccountSession("A", source) { "A" }
        assertTrue(runCatching { immediate { session.saveAvatar(byteArrayOf(1)) } }.isFailure)
        assertEquals("", immediate { session.load() }.avatarUrl)
    }

    private class MemorySource(val records: MutableMap<String, User>, val failWrites: Boolean = false) : MemberProfileDataSource {
        var writes = 0
        override suspend fun load(uid: String): User = records[uid] ?: error("Missing profile")
        override suspend fun save(uid: String, edit: MemberProfileEdit) {
            check(!failWrites) { "Permission denied" }
            val values = edit.updates()
            records[uid] = load(uid).copy(fullName = values.getValue("fullName") as String,
                phone = values.getValue("phone") as String, birthday = values.getValue("birthday") as String)
            writes++
        }
        override suspend fun saveAvatar(uid: String, bytes: ByteArray?): String = error("Not used by these tests")
    }

    private fun <T> immediate(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) { outcome = result }
        })
        return checkNotNull(outcome) { "Test operation unexpectedly suspended" }.getOrThrow()
    }
}
