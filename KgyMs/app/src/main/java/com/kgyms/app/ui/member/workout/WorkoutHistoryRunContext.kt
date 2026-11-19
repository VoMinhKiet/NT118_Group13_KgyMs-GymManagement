package com.kgyms.app.ui.member.workout

import com.kgyms.app.data.model.*
import java.util.TimeZone

internal fun workoutHistoryRunContext(workout:WorkoutSummaryUiModel,session:WorkoutSessionUiModel?,
    plans:PersonalPlanArchive?,exerciseIds:List<String>,now:Long,zone:String=TimeZone.getDefault().id):WorkoutRunContext {
    val match=plans?.plans?.asSequence()?.flatMap { plan->plan.sessions.asSequence().map { plan to it } }
        ?.firstOrNull { (plan,s)->plan.runtimeSessionId(s)==session?.id }
    val key=if(session!=null)"session:${session.id}" else "practice:${PlanDates.date(now,zone)}:${workout.id}"
    return WorkoutRunContext(key,match?.second?.id ?: session?.id ?: key,match?.first?.id ?: "",
        match?.second?.templateId ?: workout.id,workout.title.vi,workout.title.en,exerciseIds)
}
