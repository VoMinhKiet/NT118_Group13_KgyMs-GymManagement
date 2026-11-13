package com.kgyms.app.ui.member.plan

import com.kgyms.app.data.model.*
import com.kgyms.app.data.reference.WorkoutReferenceCatalog
import com.kgyms.app.data.repository.*
import com.kgyms.app.utils.ExerciseEditScope
import com.kgyms.app.utils.PersonalPlanEditor as Editor
import com.kgyms.app.utils.PersonalPlanGenerator as Generator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PersonalPlanEditorTest {
    private val catalog = WorkoutReferenceCatalog.snapshot()
    private val config = PersonalPlanConfig(WorkoutLevel.Beginner, 3, "2026-12-29", "Asia/Ho_Chi_Minh")
    private val now = PlanDates.millis(config.startDate, config.zoneId)
    private fun plan(uid: String = "editor-A") = Generator.seal(Generator.generate(uid, config, catalog, now))
    private fun changed(plan: PersonalPlan, scope: ExerciseEditScope = ExerciseEditScope.Session): PersonalPlan {
        val session = plan.sessions.first()
        return Editor.apply(plan, session.id, listOf("bridge"), scope, catalog)
    }

    @Test fun addPreservesExistingOrderAndCatalogPrescription() {
        val ids = Editor.add(listOf("squat"), "row", catalog, WorkoutLevel.Beginner)
        assertEquals(listOf("squat", "row"), ids)
        assertEquals(catalog.exercises.first { it.id == "row" }, Editor.eligible(catalog, WorkoutLevel.Beginner).first { it.id == "row" })
    }
    @Test fun replaceOnlyChangesSelectedPosition() {
        assertEquals(listOf("squat", "curl", "row"), Editor.replace(listOf("squat", "press", "row"), "press", "curl", catalog, WorkoutLevel.Beginner))
    }
    @Test fun removeDoesNotTouchOtherExercisesOrTheSource() {
        val source = listOf("squat", "press", "row")
        assertEquals(listOf("squat", "row"), Editor.remove(source, "press"))
        assertEquals(3, source.size)
    }
    @Test fun duplicatesUnknownAndOverLevelExercisesAreRejected() {
        listOf("squat", "missing", "plank", "tempo-squat").forEach { id ->
            assertTrue(runCatching { Editor.add(listOf("squat"), id, catalog, WorkoutLevel.Beginner) }.isFailure)
        }
        assertTrue(runCatching { Editor.replace(listOf("squat", "row"), "squat", "row", catalog, WorkoutLevel.Beginner) }.isFailure)
        assertTrue(runCatching { Editor.replace(listOf("squat"), "missing", "row", catalog, WorkoutLevel.Beginner) }.isFailure)
    }
    @Test fun replacementChoicesExcludeCurrentAndDuplicatesAndRespectLevel() {
        val options = Editor.choices(listOf("squat", "press", "row"), "press", catalog, WorkoutLevel.Beginner)
        assertTrue(options.isNotEmpty())
        assertTrue(options.none { it.id in listOf("squat", "press", "row", "plank", "tempo-squat") })
        assertTrue(Editor.choices(Editor.eligible(catalog, WorkoutLevel.Beginner).map { it.id }, null, catalog, WorkoutLevel.Beginner).isEmpty())
        assertTrue(Editor.choices(emptyList(), null, catalog.copy(exercises = emptyList()), WorkoutLevel.Beginner).isEmpty())
    }
    @Test fun reliableMuscleMetadataIsPrioritizedWithoutInventingGroups() {
        val options = Editor.choices(listOf("squat"), "squat", catalog, WorkoutLevel.Advanced)
        assertEquals("Legs", options.first().muscleGroup.en)
        assertTrue(options.indexOfFirst { it.id == "tempo-squat" } < options.indexOfFirst { it.muscleGroup.en != "Legs" })
    }
    @Test fun emptyDraftIsAllowedButCannotBeApplied() {
        assertTrue(Editor.remove(listOf("row"), "row").isEmpty())
        val plan = plan()
        assertTrue(runCatching { Editor.apply(plan, plan.sessions.first().id, emptyList(), ExerciseEditScope.Session, catalog) }.isFailure)
    }
    @Test fun cancelingAnEditedDraftLeavesOriginalUntouched() {
        val original = plan()
        val snapshot = original.copy(sessions = original.sessions.toList())
        changed(original)
        assertEquals(snapshot, original)
    }
    @Test fun restoreUsesOriginalSnapshotAcrossMultipleRevisions() {
        val original = plan()
        val first = Editor.revision(original, changed(original), catalog)
        val session = first.sessions.first()
        assertEquals(original.sessions.first().exerciseIds, Editor.suggestions(session, catalog, config.level))
        val second = Editor.apply(first, session.id, listOf("row"), ExerciseEditScope.Session, catalog)
        assertEquals(original.sessions.first().exerciseIds, Editor.suggestions(second.sessions.first(), catalog, config.level))
        val restored = Editor.apply(second, session.id, Editor.suggestions(session, catalog, config.level)!!, ExerciseEditScope.Session, catalog)
        assertEquals(original.sessions.first().exerciseIds, restored.sessions.first().exerciseIds)
    }
    @Test fun missingOrUntrustworthySuggestionsAreNotFabricated() {
        val session = plan().sessions.first()
        assertNull(Editor.suggestions(session.copy(templateId = "missing"), catalog, config.level))
        assertNull(Editor.suggestions(session.copy(exerciseIds = listOf("bridge")), catalog, config.level))
        assertNull(Editor.suggestions(session.copy(suggestedExerciseIds = listOf("missing")), catalog, config.level))
        assertFalse(Editor.canApplyToTemplate(session.copy(templateId = "missing"), catalog))
        assertTrue(runCatching { Editor.apply(plan(), "invalid", listOf("bridge"), ExerciseEditScope.Session, catalog) }.isFailure)
    }
    @Test fun singleSessionScopePreservesOtherSessionsAndLogicalIds() {
        val original = plan()
        val edited = changed(original)
        assertEquals(listOf("bridge"), edited.sessions.first().exerciseIds)
        assertEquals(original.sessions.drop(1), edited.sessions.drop(1))
        assertEquals(original.sessions.map { it.id }, edited.sessions.map { it.id })
    }
    @Test fun templateScopeDoesNotSpreadThroughSharedExercisesOrOtherVersions() {
        val original = plan()
        val selected = original.sessions.first()
        val edited = changed(original, ExerciseEditScope.SameTemplate)
        assertTrue(original.sessions.count { it.templateId == selected.templateId } > 1)
        original.sessions.zip(edited.sessions).forEach { (before, after) ->
            if (before.templateId == selected.templateId) assertEquals(listOf("bridge"), after.exerciseIds) else assertEquals(before, after)
        }
        assertNotEquals(listOf("bridge"), original.sessions.first().exerciseIds)
    }
    @Test fun revisionPreservesOldVersionAndIsDeterministicAndIdempotent() {
        val original = plan()
        val revision = Editor.revision(original, changed(original), catalog)
        assertEquals(revision, Editor.revision(original, changed(original), catalog))
        assertEquals(original.id, revision.revisionOf)
        assertEquals(original.sessions.map { it.id }, revision.sessions.map { it.id })
        val first = appendPersonalPlan(PersonalPlanArchive(), original, original.uid, null)
        val second = appendPersonalPlan(first, revision, original.uid, original.id)
        assertEquals(listOf(original, revision), second.plans)
        assertEquals(second, appendPersonalPlan(second, revision, original.uid, original.id))
        assertEquals(original, Editor.revision(original, original, catalog))
    }
    @Test fun revisionCannotChangeOwnerDatesTemplatesOrDropSessions() {
        val original = plan()
        listOf(original.copy(uid = "B"), original.copy(config = config.copy(sessionsPerWeek = 4)),
            original.copy(sessions = original.sessions.drop(1)), original.copy(sessions = original.sessions.reversed()),
            original.copy(sessions = original.sessions.mapIndexed { i, s -> if (i == 0) s.copy(templateId = "upper-body") else s }),
            original.copy(sessions = original.sessions.mapIndexed { i, s -> if (i == 0) s.copy(date = PlanDates.add(s.date, 1, config.zoneId)) else s }))
            .forEach { assertTrue(runCatching { Editor.revision(original, it, catalog) }.isFailure) }
    }
    @Test fun editsToStartedPlansPersistWithoutClaimingConfirmedHistory() = runBlocking {
        val pastConfig = config.copy(startDate = "2020-12-29")
        val original = Generator.seal(Generator.generate("editor-A", pastConfig, catalog, PlanDates.millis(pastConfig.startDate, pastConfig.zoneId)))
        val repo = MemoryPersonalPlanRepository(original.uid)
        repo.save(original, null)
        val state = PersonalPlanState(repo); state.load()
        assertTrue(state.saveExercises(original, original.sessions.first().id, listOf("bridge"), ExerciseEditScope.Session, catalog, original.id))
        val revision = state.active!!
        val projected = state.catalog(catalog)
        val runtimeId = revision.runtimeSessionId(revision.sessions.first())
        assertNotEquals(original.sessions.first().id, runtimeId)
        assertEquals(listOf("bridge"), projected.workouts.first { it.id == "personal-workout-$runtimeId" }.exerciseIds)
        assertEquals(original.sessions.first().exerciseIds, projected.workouts.first { it.id == "personal-workout-${original.sessions.first().id}" }.exerciseIds)
        assertEquals(projected.sessions.size, projected.sessions.map { it.id }.distinct().size)
        assertTrue(projected.sessions.all { it.completedAt == null && it.confirmedAt == null && it.status == SessionStatus.Planned })
        assertFalse(projected.historyAvailable)
        val reopened = PersonalPlanState(repo); reopened.load()
        assertEquals(state.archive, reopened.archive)
    }
    @Test fun ownerAndStaleActivePointerAndSaveFailureNeverPublishAnEdit() = runBlocking {
        val original = plan()
        val repo = MemoryPersonalPlanRepository(original.uid); repo.save(original, null)
        val state = PersonalPlanState(repo); state.load()
        assertFalse(state.saveExercises(plan("B"), original.sessions.first().id, listOf("bridge"), ExerciseEditScope.Session, catalog, original.id))
        assertFalse(state.saveExercises(original, original.sessions.first().id, listOf("bridge"), ExerciseEditScope.Session, catalog, null))
        val denied = PersonalPlanState(object : PersonalPlanRepository {
            override val uid = original.uid
            override suspend fun load() = PersonalPlanArchive(original.id, listOf(original))
            override suspend fun save(plan: PersonalPlan, expectedActiveId: String?): PersonalPlanArchive = error("Test disk failure")
        })
        denied.load()
        assertFalse(denied.saveExercises(original, original.sessions.first().id, listOf("bridge"), ExerciseEditScope.Session, catalog, original.id))
        assertEquals(original, denied.active)
        assertFalse(denied.saving); assertFalse(denied.saveNotice)
        assertEquals(original, state.active)
    }
}
