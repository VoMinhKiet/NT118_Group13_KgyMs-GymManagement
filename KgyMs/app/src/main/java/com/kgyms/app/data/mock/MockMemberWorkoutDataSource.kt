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

        val exercises = WorkoutMockData.exercises.map { e ->
            val level = if (e.id == "plank") WorkoutLevel.Intermediate else WorkoutLevel.Beginner
            val category = when (e.id) { "plank" -> WorkoutCategory.Core; "bridge" -> WorkoutCategory.Mobility; else -> WorkoutCategory.Strength }
            val image = when (e.id) { "squat", "bridge" -> R.drawable.workout_lower; "plank" -> R.drawable.workout_core; else -> R.drawable.workout_upper }
            ExerciseUiModel(e.id, e.name, e.muscle, level, category, image, e.equipment, e.sets, e.reps,
                e.restSeconds, e.instructions, e.safety, VideoSourceUiModel("video-${e.id}", e.name, VideoProvider.LocalPreview))
        } + listOf(ExerciseUiModel("tempo-squat", WorkoutText("Squat nhịp chậm", "Tempo squat"), WorkoutText("Chân", "Legs"),
            WorkoutLevel.Advanced, WorkoutCategory.Strength, R.drawable.workout_lower, WorkoutText("Tạ đơn", "Dumbbells"), 4, "8", 90,
            listOf(WorkoutText("Giữ tạ trước ngực, hạ người chậm trong 3 giây.", "Hold the weight at your chest and lower slowly for 3 seconds."),
                WorkoutText("Giữ thân vững, đứng lên có kiểm soát.", "Keep your torso stable and stand with control.")),
            WorkoutText("Chỉ tăng độ khó khi đã vững kỹ thuật squat cơ bản.", "Progress only after mastering the basic squat."),
            VideoSourceUiModel("video-tempo-squat", WorkoutText("Squat nhịp chậm", "Tempo squat"), VideoProvider.LocalPreview)))
        val workouts = listOf(
            WorkoutSummaryUiModel("upper-body", WorkoutText("Sức mạnh thân trên", "Upper Body Strength"), 45, 240, WorkoutLevel.Intermediate, WorkoutCategory.Strength, R.drawable.home_workout_upper_body, listOf("press", "row", "curl"), listOf(WorkoutText("Thân trên", "Upper body"))),
            WorkoutSummaryUiModel("pt-lower", WorkoutText("Nền tảng thân dưới", "Lower Body Foundations"), 40, 210, WorkoutLevel.Beginner, WorkoutCategory.Strength, R.drawable.workout_lower, listOf("squat", "bridge", "plank"), listOf(WorkoutText("Chân & mông", "Legs & glutes"))),
            WorkoutSummaryUiModel("full-body", WorkoutText("Toàn thân cân bằng", "Balanced Full Body"), 45, 260, WorkoutLevel.Beginner, WorkoutCategory.Strength, R.drawable.workout_upper, listOf("squat", "press", "row", "plank"), listOf(WorkoutText("Toàn thân", "Full body"))),
            WorkoutSummaryUiModel("pt-upper", WorkoutText("Thân trên cùng HLV", "Trainer Upper Body"), 45, 240, WorkoutLevel.Intermediate, WorkoutCategory.Strength, R.drawable.home_workout_upper_body, listOf("press", "row", "curl"), listOf(WorkoutText("Thân trên", "Upper body"))),
            WorkoutSummaryUiModel("core-control", WorkoutText("Vững cơ trung tâm", "Core Control"), 20, 100, WorkoutLevel.Intermediate, WorkoutCategory.Core, R.drawable.workout_core, listOf("plank", "bridge"), listOf(WorkoutText("Kiểm soát", "Control"))),
            WorkoutSummaryUiModel("strength-plus", WorkoutText("Thử thách sức mạnh", "Strength Challenge"), 50, 300, WorkoutLevel.Advanced, WorkoutCategory.Strength, R.drawable.workout_lower, listOf("tempo-squat", "press", "row", "plank"), listOf(WorkoutText("Toàn thân", "Full body"))),
            WorkoutSummaryUiModel("core-plus", WorkoutText("Sức bền thân người", "Core Endurance"), 30, 160, WorkoutLevel.Advanced, WorkoutCategory.Core, R.drawable.workout_core, listOf("tempo-squat", "plank", "bridge"), listOf(WorkoutText("Sức bền", "Endurance"))),
        )
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
                TrainerMessageUiModel("m3", true, WorkoutText("Em xem hướng dẫn trong giáo án nhé. Mình sẽ cùng kiểm tra kỹ thuật ở buổi tới.", "Take a look at the guide in your plan. We'll check your form together next session."))))
    }
}
