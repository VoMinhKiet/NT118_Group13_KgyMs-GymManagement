package com.kgyms.app.data

import com.kgyms.app.data.firebase.OnboardingMeasurements
import com.kgyms.app.data.firebase.missingOnboardingFields
import com.kgyms.app.data.model.*
import com.kgyms.app.data.repository.MemberAccountSession
import com.kgyms.app.data.repository.MemberProfileDataSource
import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.*

/** Test-only in-memory persistence, never a substitute for Firebase in production. */
class MemberAccountDataTest {
    private val registered = User(uid = "A", fullName = "Nguyễn Minh Anh", email = "a@example.com",
        gender = "Female", age = 24)

    @Test fun onboardingSurvivesProjectionAndMissingFieldsStayMissing() {
        val profile = registered.memberProfile("vi")
        assertEquals("Nữ", profile.gender)
        assertEquals("24", profile.age)
        // Legacy account with no saved measurements.
        assertEquals("Chưa cập nhật", profile.weight)
        assertEquals("Chưa cập nhật", profile.height)
        assertTrue(registered.hasCompletedOnboarding)
        listOf(profile.phone, profile.birthday, profile.membership, profile.workouts,
            profile.daysActive, profile.averageRating).forEach { assertEquals("Chưa cập nhật", it) }
        assertEquals("MA", profile.initials)
    }

    @Test fun freshOnboardingFillsAllFieldsAsOnePatch() {
        val current = mapOf("role" to "user", "status" to "active", "gender" to "", "age" to 0L, "phone" to "0901234567")
        val patch = missingOnboardingFields(current, "male", 22, OnboardingMeasurements(154, "LB", 175))
        assertEquals(mapOf("gender" to "male", "age" to 22, "weight" to 154, "weightUnit" to "LB", "height" to 175), patch)
        assertTrue(patch.values.none { it == "" })
    }

    @Test fun retriesFillOnlyMissingFieldsAndNeverAssignANewUnitToAnExistingWeight() {
        val current = mapOf("gender" to "female", "age" to "25", "weight" to 62L, "email" to "a@example.test")
        val patch = missingOnboardingFields(current, "male", 21, OnboardingMeasurements(150, "LB", 170))
        assertEquals(mapOf("height" to 170), patch)
        assertTrue(missingOnboardingFields(current + patch, "male", 20, OnboardingMeasurements(170, "LB", 190)).isEmpty())
        val partial = missingOnboardingFields(mapOf("weightUnit" to "KG"), "male", 22, OnboardingMeasurements(150, "LB", 175))
        assertFalse("weightUnit" in partial)
        assertEquals(68.0388555, partial["weight"] as Double, 0.000001)
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
        assertEquals(registered.age, again.age)
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

    @Test fun profileEditPreservesLegacyAvatarUrlWithoutAnAvatarWritePath() {
        val legacyUrl = "https://example.com/existing-avatar.jpg"
        val source = MemorySource(mutableMapOf("A" to registered.copy(avatarUrl = legacyUrl)))
        val session = MemberAccountSession("A", source) { "A" }
        immediate { session.save(MemberProfileEdit("Updated", "0901234567", "")) }
        assertEquals(legacyUrl, immediate { session.load() }.avatarUrl)
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
