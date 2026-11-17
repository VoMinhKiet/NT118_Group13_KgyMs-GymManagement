package com.kgyms.app.data.mock

import com.kgyms.app.R
import java.util.Calendar
import com.kgyms.app.data.model.ExerciseUiModel
import com.kgyms.app.data.model.MemberWorkoutDataSource
import com.kgyms.app.data.model.PlanSource
import com.kgyms.app.data.model.SessionStatus
import com.kgyms.app.data.model.TrainerMessageUiModel
import com.kgyms.app.data.model.TrainerPlanUiModel
import com.kgyms.app.data.model.TrainerUiModel
import com.kgyms.app.data.model.VideoProvider
import com.kgyms.app.data.model.VideoSourceUiModel
import com.kgyms.app.data.model.WorkoutCatalog
import com.kgyms.app.data.model.WorkoutCategory
import com.kgyms.app.data.model.WorkoutLevel
import com.kgyms.app.data.model.WorkoutSessionUiModel
import com.kgyms.app.data.model.WorkoutSummaryUiModel
import com.kgyms.app.data.model.WorkoutText
import com.kgyms.app.utils.workoutMonday

internal object MockMemberWorkoutDataSource : MemberWorkoutDataSource {
    override fun snapshot(now: Long): WorkoutCatalog {
        fun at(day: Int) = Calendar.getInstance().apply {
            timeInMillis = workoutMonday(now); add(Calendar.DAY_OF_YEAR, day); this.set(Calendar.HOUR_OF_DAY, 9)
        }.timeInMillis

        val reference = com.kgyms.app.data.reference.WorkoutReferenceCatalog.snapshot()
        val exercises = reference.exercises
        val workouts = reference.workouts
        // Seed confirmations only for sessions whose time has passed. Booking never confirms completion.
        val sessions = listOf(0 to "upper-body", 2 to "pt-lower", 4 to "full-body", 5 to "pt-upper").mapIndexed { index, (day, id) ->
            val scheduled = at(day)
            val confirmed = index == 0 && scheduled <= now
            WorkoutSessionUiModel("week-${workoutMonday(now)}-$id", id, scheduled,
                if (confirmed) SessionStatus.Confirmed else if (index == 1) SessionStatus.Booked else SessionStatus.Planned,
                if (id.startsWith("pt-")) PlanSource.Trainer else PlanSource.Self,
                if (id.startsWith("pt-")) "trainer-huy" else null,
                if (confirmed) scheduled else null, if (confirmed) scheduled else null)
        }
        return WorkoutCatalog(exercises, workouts, sessions,
            TrainerUiModel("trainer-huy", WorkoutMockData.trainer, "QH", WorkoutText("Sức mạnh & kỹ thuật", "Strength & technique")),
            TrainerPlanUiModel("foundation-plan", "trainer-huy", WorkoutText("Xây nền tảng vững chắc", "Build a Strong Foundation"), at(0), sessions.filter { it.source == PlanSource.Trainer }.map { it.id }),
            listOf(TrainerMessageUiModel("m1", true, WorkoutText("Chào Minh Anh! Tuần này mình tập trung vào kỹ thuật và nhịp thở nhé.", "Hi Minh Anh! This week, let's focus on technique and breathing.")),
                TrainerMessageUiModel("m2", false, WorkoutText("Em muốn xem lại động tác squat trước buổi tập.", "I'd like to review the squat before our session.")),
                TrainerMessageUiModel("m3", true, WorkoutText("Em xem hướng dẫn trong giáo án nhé. Mình sẽ cùng kiểm tra kỹ thuật ở buổi tới.", "Take a look at the guide in your plan. We'll check your form together next session."))), historyAvailable = true)
    }
}
