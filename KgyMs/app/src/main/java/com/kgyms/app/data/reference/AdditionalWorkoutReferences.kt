package com.kgyms.app.data.reference

import com.kgyms.app.R
import com.kgyms.app.data.model.*

/** Static instructions and conservative starting prescriptions for the reference library. */
internal object AdditionalWorkoutReferences {
    private enum class Muscle(val label: WorkoutText, val image: Int) {
        Chest(WorkoutText("Ngực", "Chest"), R.drawable.workout_upper),
        Back(WorkoutText("Lưng", "Back"), R.drawable.workout_upper),
        Shoulders(WorkoutText("Vai", "Shoulders"), R.drawable.workout_upper),
        Biceps(WorkoutText("Tay trước", "Biceps"), R.drawable.workout_upper),
        Triceps(WorkoutText("Tay sau", "Triceps"), R.drawable.workout_upper),
        Quadriceps(WorkoutText("Đùi trước", "Quadriceps"), R.drawable.workout_lower),
        Hamstrings(WorkoutText("Đùi sau", "Hamstrings"), R.drawable.workout_lower),
        Glutes(WorkoutText("Mông", "Glutes"), R.drawable.workout_lower),
        Calves(WorkoutText("Bắp chân", "Calves"), R.drawable.workout_lower),
        Core(WorkoutText("Bụng", "Core"), R.drawable.workout_core),
        FullBody(WorkoutText("Toàn thân", "Full body"), R.drawable.workout_upper),
    }

    private enum class Equipment(val label: WorkoutText) {
        Bodyweight(WorkoutText("Trọng lượng cơ thể", "Bodyweight")),
        Dumbbells(WorkoutText("Tạ đơn", "Dumbbells")),
        DumbbellBench(WorkoutText("Tạ đơn và ghế dốc", "Dumbbells and incline bench")),
        Bench(WorkoutText("Ghế vững", "Sturdy bench")),
        Cable(WorkoutText("Máy cáp", "Cable machine")),
        Bar(WorkoutText("Xà đơn", "Pull-up bar")),
        Step(WorkoutText("Bục thấp", "Low step")),
        LegCurl(WorkoutText("Máy gập đùi sau", "Leg curl machine")),
    }

    private fun exercise(
        id: String, vi: String, en: String, muscle: Muscle, level: WorkoutLevel, equipment: Equipment,
        sets: Int, reps: String, rest: Int,
        setupVi: String, setupEn: String, motionVi: String, motionEn: String, safetyVi: String, safetyEn: String,
    ): ExerciseUiModel {
        val title = WorkoutText(vi, en)
        return ExerciseUiModel(id, title, muscle.label, level,
            if (muscle == Muscle.Core) WorkoutCategory.Core else WorkoutCategory.Strength,
            muscle.image, equipment.label, sets, reps, rest,
            listOf(WorkoutText(setupVi, setupEn), WorkoutText(motionVi, motionEn)),
            WorkoutText(safetyVi, safetyEn), VideoSourceUiModel("video-$id", title, VideoProvider.Unavailable))
    }

    val exercises = listOf(
        exercise("incline-push-up", "Chống đẩy tay cao", "Incline push-up", Muscle.Chest, WorkoutLevel.Beginner, Equipment.Bench, 3, "8–12", 60,
            "Chống tay lên ghế chắc, lùi chân và giữ thân thẳng.", "Place your hands on a sturdy bench, step back and keep a straight body line.",
            "Hạ ngực về ghế rồi đẩy lên chậm.", "Lower your chest toward the bench, then press up smoothly.",
            "Cố định ghế trước khi tập.", "Make sure the bench cannot slide."),
        exercise("incline-db-press", "Đẩy ngực ghế dốc", "Incline dumbbell press", Muscle.Chest, WorkoutLevel.Intermediate, Equipment.DumbbellBench, 3, "8–12", 90,
            "Nằm trên ghế dốc nhẹ, đặt hai chân vững.", "Lie on a low incline bench with your feet planted.",
            "Hạ tạ cạnh ngực trên rồi đẩy lên có kiểm soát.", "Lower the weights beside your upper chest, then press with control.",
            "Chọn tạ có thể đưa lên và đặt xuống an toàn.", "Choose weights you can safely lift into and out of position."),
        exercise("lat-pulldown", "Kéo xô máy cáp", "Lat pulldown", Muscle.Back, WorkoutLevel.Beginner, Equipment.Cable, 3, "10–12", 75,
            "Cố định đùi dưới đệm và nắm thanh rộng vừa phải.", "Secure your thighs under the pad and grip the bar comfortably.",
            "Kéo khuỷu tay xuống, đưa thanh về ngực trên rồi trả chậm.", "Drive your elbows down, bring the bar toward your upper chest, then release slowly.",
            "Tránh kéo thanh sau gáy hoặc ngả người mạnh.", "Avoid pulling behind your head or leaning far back."),
        exercise("pull-up", "Kéo xà sấp tay", "Pull-up", Muscle.Back, WorkoutLevel.Advanced, Equipment.Bar, 3, "4–8", 120,
            "Nắm xà sấp tay, gồng thân và kéo khuỷu tay xuống.", "Grip the bar overhand, brace and drive your elbows down.",
            "Kéo lên trong tầm kiểm soát rồi hạ chậm.", "Pull up within a controlled range and lower slowly.",
            "Dùng máy hỗ trợ nếu phải đung đưa.", "Use assistance if you need to swing."),
        exercise("lateral-raise", "Nâng tạ ngang vai", "Dumbbell lateral raise", Muscle.Shoulders, WorkoutLevel.Beginner, Equipment.Dumbbells, 2, "12–15", 60,
            "Giữ tạ nhẹ cạnh thân, khuỷu hơi gập.", "Hold light dumbbells by your sides with softly bent elbows.",
            "Nâng tay sang hai bên đến ngang vai hoặc thấp hơn, rồi hạ chậm.", "Raise out to shoulder height or below, then lower slowly.",
            "Tránh nhún vai hoặc vung người.", "Avoid shrugging or swinging."),
        exercise("db-shoulder-press", "Đẩy vai tạ đơn", "Dumbbell shoulder press", Muscle.Shoulders, WorkoutLevel.Intermediate, Equipment.Dumbbells, 3, "8–12", 90,
            "Giữ tạ ngang vai, siết bụng và giữ sườn ổn định.", "Hold the weights at shoulder height, brace and keep your ribs steady.",
            "Đẩy lên trên đầu trong tầm thoải mái rồi hạ chậm.", "Press overhead within a comfortable range and lower slowly.",
            "Giảm tải nếu phải ưỡn lưng.", "Reduce the load if your back arches."),
        exercise("hammer-curl", "Cuốn tạ kiểu búa", "Dumbbell hammer curl", Muscle.Biceps, WorkoutLevel.Beginner, Equipment.Dumbbells, 2, "10–12", 60,
            "Cầm tạ với hai lòng bàn tay hướng vào nhau.", "Hold the weights with your palms facing inward.",
            "Gập khuỷu, giữ cánh tay sát thân và hạ chậm.", "Bend your elbows, keep upper arms by your sides and lower slowly.",
            "Giữ cổ tay thẳng; giảm tạ nếu thân lắc.", "Keep wrists neutral and reduce the load if you sway."),
        exercise("triceps-pressdown", "Duỗi tay sau với cáp", "Cable triceps pressdown", Muscle.Triceps, WorkoutLevel.Beginner, Equipment.Cable, 2, "10–15", 60,
            "Giữ khuỷu sát thân và cầm dây cáp ngang bụng.", "Keep elbows by your sides and hold the rope at waist height.",
            "Duỗi khuỷu xuống rồi đưa tay về chậm.", "Extend the elbows downward and return slowly.",
            "Tránh dùng vai hoặc dồn người lên dây.", "Avoid using your shoulders or leaning onto the rope."),
        exercise("step-up", "Bước lên bục thấp", "Low step-up", Muscle.Quadriceps, WorkoutLevel.Beginner, Equipment.Step, 2, "8–10 / side", 75,
            "Đặt cả bàn chân lên bục thấp, chắc chắn.", "Place your whole foot on a low, stable step.",
            "Dồn lực qua chân trên bục để đứng lên rồi hạ có kiểm soát.", "Push through the foot on the step to rise, then lower under control.",
            "Giảm độ cao nếu mất thăng bằng hoặc khó kiểm soát gối.", "Use a lower step if balance or knee control is difficult."),
        exercise("split-squat", "Squat tách chân", "Split squat", Muscle.Quadriceps, WorkoutLevel.Intermediate, Equipment.Bodyweight, 3, "8–10 / side", 75,
            "Đứng chân trước chân sau với bề ngang vừa đủ.", "Use a staggered stance with enough width for balance.",
            "Hạ gối sau về sàn rồi đẩy qua bàn chân trước để lên.", "Lower the back knee toward the floor, then drive through the front foot.",
            "Dùng điểm tựa và giảm biên độ nếu cần.", "Use support or shorten the range as needed."),
        exercise("seated-leg-curl", "Gập đùi sau trên máy", "Seated leg curl", Muscle.Hamstrings, WorkoutLevel.Beginner, Equipment.LegCurl, 2, "10–15", 60,
            "Chỉnh trục máy ngang gối và đệm ngay trên gót.", "Align the machine pivot with your knee and adjust the pad above your heels.",
            "Gập gối kéo đệm xuống rồi trả chậm.", "Flex the knees to move the pad down, then return slowly.",
            "Hỏi nhân viên cách chỉnh máy nếu chưa quen.", "Ask gym staff for help setting up an unfamiliar machine."),
        exercise("db-romanian-deadlift", "Deadlift Romania tạ đơn", "Dumbbell Romanian deadlift", Muscle.Hamstrings, WorkoutLevel.Intermediate, Equipment.Dumbbells, 3, "8–12", 90,
            "Gập nhẹ gối, đẩy hông ra sau và giữ tạ sát chân.", "Soften the knees, push hips back and keep weights close to your legs.",
            "Dừng khi căng đùi sau và còn giữ lưng ổn định, rồi đứng lên.", "Stop at a hamstring stretch while keeping your back steady, then stand tall.",
            "Giảm biên độ nếu lưng bắt đầu cong.", "Shorten the range if your back starts to round."),
        exercise("single-leg-rdl", "Deadlift Romania một chân", "Single-leg Romanian deadlift", Muscle.Hamstrings, WorkoutLevel.Advanced, Equipment.Dumbbells, 3, "6–8 / side", 90,
            "Dồn trọng lượng lên một chân, giữ gối hơi gập.", "Shift weight onto one leg and keep the knee softly bent.",
            "Gập từ hông, đưa chân kia ra sau rồi trở lại tư thế đứng.", "Hinge at the hip, reach the other leg back, then return to standing.",
            "Tập không tạ hoặc dùng điểm tựa trước khi tăng tải.", "Practice unloaded or with support before adding load."),
        exercise("side-lying-hip-abduction", "Nâng chân nằm nghiêng", "Side-lying hip abduction", Muscle.Glutes, WorkoutLevel.Beginner, Equipment.Bodyweight, 2, "12–15 / side", 45,
            "Nằm nghiêng, xếp hai hông thẳng hàng.", "Lie on your side with hips stacked.",
            "Nâng chân trên trong tầm kiểm soát rồi hạ chậm.", "Lift the top leg through a controlled range, then lower slowly.",
            "Tránh xoay hông ra sau để nâng cao hơn.", "Avoid rolling your pelvis backward to lift higher."),
        exercise("standing-calf-raise", "Nhón gót đứng", "Standing calf raise", Muscle.Calves, WorkoutLevel.Beginner, Equipment.Bodyweight, 2, "12–15", 45,
            "Đứng trên sàn cạnh điểm tựa, đặt bàn chân song song.", "Stand on the floor near a support with feet parallel.",
            "Nhón gót, dừng ngắn rồi hạ chậm.", "Raise your heels, pause briefly and lower slowly.",
            "Vịn nhẹ để giữ thăng bằng; tránh nhún nảy.", "Hold support lightly and avoid bouncing."),
        exercise("dead-bug", "Duỗi tay chân đối bên nằm ngửa", "Dead bug", Muscle.Core, WorkoutLevel.Beginner, Equipment.Bodyweight, 2, "6–10 / side", 45,
            "Nằm ngửa, hướng hai tay lên và gập hông/gối.", "Lie on your back with arms up and hips and knees bent.",
            "Từ từ duỗi tay và chân đối diện rồi đổi bên.", "Slowly extend opposite arm and leg, then switch sides.",
            "Rút ngắn tầm nếu lưng dưới ưỡn khỏi sàn.", "Shorten the movement if your lower back arches off the floor."),
        exercise("bird-dog", "Duỗi tay chân đối bên chống gối", "Bird dog", Muscle.Core, WorkoutLevel.Beginner, Equipment.Bodyweight, 2, "6–10 / side", 45,
            "Chống hai tay dưới vai và hai gối dưới hông.", "Place hands under shoulders and knees under hips.",
            "Duỗi tay và chân đối diện, giữ hông cân rồi đổi bên.", "Reach with opposite arm and leg, keeping hips level, then switch sides.",
            "Đưa tay/chân thấp hơn nếu phải xoay người.", "Reach lower if your torso starts to rotate."),
        exercise("suitcase-carry", "Đi bộ xách tạ một bên", "Suitcase carry", Muscle.FullBody, WorkoutLevel.Intermediate, Equipment.Dumbbells, 3, "20–30 s / side", 60,
            "Cầm một tạ bên thân, đứng thẳng và giữ vai cân.", "Hold one dumbbell by your side, stand tall and keep shoulders level.",
            "Bước chậm, giữ thân thẳng rồi đổi tay.", "Walk slowly without leaning, then switch hands.",
            "Dọn lối đi và đặt tạ xuống có kiểm soát.", "Clear your path and set the weight down with control."),
        exercise("db-squat-to-press", "Squat kết hợp đẩy tạ", "Dumbbell squat to press", Muscle.FullBody, WorkoutLevel.Advanced, Equipment.Dumbbells, 3, "6–10", 120,
            "Giữ tạ nhẹ ngang vai, hạ squat trong tầm kiểm soát.", "Hold light dumbbells at your shoulders and squat under control.",
            "Đứng lên, đẩy tạ qua đầu rồi hạ về vai.", "Stand, press overhead and return the weights to your shoulders.",
            "Chỉ ghép động tác khi đã kiểm soát được squat và đẩy qua đầu riêng.", "Combine the movements only once you can control each separately."),
    )

    val workouts = listOf(
        workout("reference-push", "Ngực, vai & tay sau", "Chest, shoulders & triceps", WorkoutLevel.Beginner,
            "incline-push-up", "lateral-raise", "triceps-pressdown"),
        workout("reference-pull", "Lưng & tay trước", "Back & biceps", WorkoutLevel.Beginner,
            "lat-pulldown", "row", "hammer-curl"),
        workout("reference-lower", "Đùi, mông & bắp chân", "Thighs, glutes & calves", WorkoutLevel.Beginner,
            "step-up", "seated-leg-curl", "side-lying-hip-abduction", "standing-calf-raise"),
        workout("reference-full-foundations", "Toàn thân cơ bản", "Full-body foundations", WorkoutLevel.Beginner,
            "squat", "incline-push-up", "lat-pulldown", "bridge", "dead-bug"),
        workout("reference-core", "Kiểm soát cơ trung tâm", "Core stability", WorkoutLevel.Beginner,
            "dead-bug", "bird-dog"),
        workout("reference-press-plus", "Đẩy thân trên", "Upper-body press", WorkoutLevel.Intermediate,
            "incline-db-press", "db-shoulder-press", "triceps-pressdown"),
        workout("reference-posterior", "Đùi sau, mông & bắp chân", "Hamstrings, glutes & calves", WorkoutLevel.Intermediate,
            "db-romanian-deadlift", "split-squat", "bridge", "standing-calf-raise"),
        workout("reference-full-carry", "Sức mạnh & xách tạ", "Full-body strength & carry", WorkoutLevel.Intermediate,
            "split-squat", "press", "row", "db-romanian-deadlift", "suitcase-carry"),
        workout("reference-pull-plus", "Kéo xà & kéo cáp", "Pull-ups & rows", WorkoutLevel.Advanced,
            "pull-up", "row", "hammer-curl"),
        workout("reference-full-combo", "Toàn thân nâng cao", "Advanced full body", WorkoutLevel.Advanced,
            "db-squat-to-press", "pull-up", "single-leg-rdl", "dead-bug"),
    )

    private fun workout(id: String, vi: String, en: String, level: WorkoutLevel, vararg exerciseIds: String): WorkoutSummaryUiModel {
        val items = exerciseIds.mapNotNull { id -> exercises.firstOrNull { it.id == id } }
        val thumbnail = items.first().thumbnailRes
        val category = if (items.all { it.category == WorkoutCategory.Core }) WorkoutCategory.Core else WorkoutCategory.Strength
        return WorkoutSummaryUiModel(id, WorkoutText(vi, en), exerciseIds.size * 7, 0, level, category,
            thumbnail, exerciseIds.toList(), items.map { it.muscleGroup }.distinct())
    }
}
