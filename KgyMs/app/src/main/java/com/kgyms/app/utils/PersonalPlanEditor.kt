package com.kgyms.app.utils

import com.kgyms.app.data.model.*
import com.kgyms.app.ui.member.workout.safeCatalog
import java.security.MessageDigest

internal enum class ExerciseEditScope { Session, SameTemplate }

/** Pure draft operations. Only the repository confirmation publishes a new immutable version. */
internal object PersonalPlanEditor {
    fun eligible(catalog: WorkoutCatalog, level: WorkoutLevel) = catalog.safeCatalog().exercises
        .filter { it.level.ordinal <= level.ordinal }

    fun validate(ids: List<String>, catalog: WorkoutCatalog, level: WorkoutLevel) {
        val eligible = eligible(catalog, level).map { it.id }.toSet()
        require(ids.isNotEmpty() && ids.distinct().size == ids.size && ids.all { it in eligible })
    }

    fun choices(ids: List<String>, replacing: String?, catalog: WorkoutCatalog, level: WorkoutLevel): List<ExerciseUiModel> {
        val muscle = catalog.exercises.firstOrNull { it.id == replacing }?.muscleGroup
        return eligible(catalog, level).filter { it.id !in ids }
            .sortedWith(compareByDescending<ExerciseUiModel> { muscle != null && muscle.en.isNotBlank() && it.muscleGroup == muscle }.thenBy { it.id })
    }

    fun add(ids: List<String>, id: String, catalog: WorkoutCatalog, level: WorkoutLevel) =
        (ids + id).also { validate(it, catalog, level) }

    fun replace(ids: List<String>, old: String, id: String, catalog: WorkoutCatalog, level: WorkoutLevel): List<String> {
        require(old in ids && id != old)
        return ids.map { if (it == old) id else it }.also { validate(it, catalog, level) }
    }

    fun remove(ids: List<String>, id: String): List<String> {
        require(id in ids)
        return ids - id // An empty draft is allowed; saving it is not.
    }

    fun canApplyToTemplate(session: PersonalPlanSession, catalog: WorkoutCatalog) =
        session.templateId.isNotBlank() && catalog.workouts.any { it.id == session.templateId }

    fun suggestions(session: PersonalPlanSession, catalog: WorkoutCatalog, level: WorkoutLevel): List<String>? {
        val known = eligible(catalog, level).map { it.id }.toSet()
        session.suggestedExerciseIds?.let { return it.takeIf { ids -> ids.isNotEmpty() && ids.distinct().size == ids.size && ids.all { id -> id in known } } }
        val template = catalog.workouts.firstOrNull { it.id == session.templateId } ?: return null
        if (template.exerciseIds.any { id -> catalog.exercises.none { it.id == id } }) return null
        val original = template.exerciseIds.filter { it in known }
        // Legacy plans had no overrides. A mismatched list without a snapshot is not a trusted suggestion.
        return original.takeIf { it.isNotEmpty() && it.distinct().size == it.size && it == session.exerciseIds }
    }

    fun apply(plan: PersonalPlan, sessionId: String, ids: List<String>, scope: ExerciseEditScope, catalog: WorkoutCatalog): PersonalPlan {
        validate(ids, catalog, plan.config.level)
        val selected = requireNotNull(plan.sessions.singleOrNull { it.id == sessionId })
        if (scope == ExerciseEditScope.SameTemplate) require(canApplyToTemplate(selected, catalog))
        return plan.copy(sessions = plan.sessions.map { session ->
            if (session.id == sessionId || scope == ExerciseEditScope.SameTemplate && session.templateId == selected.templateId) {
                if (session.exerciseIds == ids) session else session.copy(exerciseIds = ids.toList(),
                    suggestedExerciseIds = suggestions(session, catalog, plan.config.level))
            } else session
        })
    }

    fun revision(original: PersonalPlan, edited: PersonalPlan, catalog: WorkoutCatalog): PersonalPlan {
        require(original.id == edited.id && original.uid == edited.uid && original.config == edited.config)
        require(original.sessions.size == edited.sessions.size)
        original.sessions.zip(edited.sessions).forEach { (before, after) ->
            require(before.id == after.id && before.date == after.date && before.templateId == after.templateId)
        }
        // Editing exercises must also work after the plan has started. Dates are unchanged above.
        PersonalPlanGenerator.validate(edited, catalog, PlanDates.millis(edited.config.startDate, edited.config.zoneId))
        if (original == edited) return original
        val bytes = "$original\n$edited".toByteArray(Charsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }.take(24)
        return edited.copy(id = "plan-$hash", revisionOf = original.id)
    }
}
