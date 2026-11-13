package com.kgyms.app.data.reference

import com.kgyms.app.R
import com.kgyms.app.data.mock.WorkoutMockData
import com.kgyms.app.data.model.*

/** Static exercise content only; never contains member history or trainer assignments. */
internal object WorkoutReferenceCatalog {
    fun snapshot(): WorkoutCatalog {
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
        return WorkoutCatalog(exercises, workouts, emptyList(), null, null, emptyList())
    }
}
