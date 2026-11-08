package com.kgyms.app.data.mock

import com.kgyms.app.data.model.Exercise
import com.kgyms.app.data.model.WorkoutText

internal object WorkoutMockData {
    private val beginner = WorkoutText("Cơ bản", "Beginner")
    private val intermediate = WorkoutText("Trung cấp", "Intermediate")
    val exercises = listOf(
        Exercise("press", WorkoutText("Đẩy ngực tạ đơn", "Dumbbell chest press"), WorkoutText("Ngực", "Chest"), beginner,
            WorkoutText("Tạ đơn", "Dumbbells"), 3, "10–12", 75,
            listOf(WorkoutText("Nằm trên ghế, đặt chân vững và giữ tạ cạnh ngực.", "Lie on a bench with feet planted and dumbbells beside your chest."),
                WorkoutText("Đẩy tạ lên có kiểm soát, sau đó hạ chậm về vị trí đầu.", "Press up with control, then lower slowly to the starting position.")),
            WorkoutText("Chọn tạ nhẹ để làm quen; không khóa cứng khuỷu tay.", "Start light; avoid locking your elbows.")),
        Exercise("row", WorkoutText("Kéo cáp ngồi", "Seated cable row"), WorkoutText("Lưng", "Back"), beginner,
            WorkoutText("Máy cáp", "Cable machine"), 3, "12", 60,
            listOf(WorkoutText("Ngồi thẳng, giữ tay cầm và siết nhẹ bụng.", "Sit tall, hold the handle and brace gently."),
                WorkoutText("Kéo tay cầm về bụng, đưa khuỷu tay ra sau rồi trả chậm.", "Pull the handle toward your waist, drive elbows back, then return slowly.")),
            WorkoutText("Không giật người hoặc ngửa lưng để kéo tạ.", "Avoid jerking or leaning back to move the weight.")),
        Exercise("squat", WorkoutText("Squat ôm tạ", "Goblet squat"), WorkoutText("Chân", "Legs"), beginner,
            WorkoutText("Tạ đơn", "Dumbbells"), 3, "10–12", 90,
            listOf(WorkoutText("Giữ tạ trước ngực, chân rộng vừa phải.", "Hold a dumbbell at your chest with a comfortable stance."),
                WorkoutText("Hạ hông trong tầm kiểm soát rồi đẩy chân đứng lên.", "Lower your hips within a controlled range, then push through your feet to stand.")),
            WorkoutText("Giữ gối cùng hướng bàn chân; giảm độ sâu nếu khó chịu.", "Keep knees tracking with toes; reduce depth if uncomfortable.")),
        Exercise("bridge", WorkoutText("Nâng hông", "Glute bridge"), WorkoutText("Chân", "Legs"), beginner,
            WorkoutText("Trọng lượng cơ thể", "Bodyweight"), 3, "15", 45,
            listOf(WorkoutText("Nằm ngửa, gập gối và đặt bàn chân trên sàn.", "Lie on your back with knees bent and feet on the floor."),
                WorkoutText("Siết mông nâng hông, giữ ngắn rồi hạ chậm.", "Squeeze your glutes to lift your hips, pause briefly and lower slowly.")),
            WorkoutText("Không ưỡn lưng quá mức ở vị trí trên cùng.", "Do not overarch your lower back at the top.")),
        Exercise("plank", WorkoutText("Plank cẳng tay", "Forearm plank"), WorkoutText("Bụng", "Core"), intermediate,
            WorkoutText("Trọng lượng cơ thể", "Bodyweight"), 3, "20–30 s", 45,
            listOf(WorkoutText("Đặt khuỷu tay dưới vai, duỗi chân ra sau.", "Place elbows under shoulders and extend your legs."),
                WorkoutText("Giữ thân thẳng và thở đều trong suốt lượt tập.", "Keep your body aligned and breathe steadily throughout the hold.")),
            WorkoutText("Dừng trước khi lưng võng; có thể chống gối để giảm tải.", "Stop before your back sags; use your knees for a lighter variation.")),
        Exercise("curl", WorkoutText("Cuốn tay trước", "Dumbbell curl"), WorkoutText("Tay", "Arms"), beginner,
            WorkoutText("Tạ đơn", "Dumbbells"), 2, "12", 60,
            listOf(WorkoutText("Đứng vững, giữ khuỷu tay cạnh thân.", "Stand steadily with elbows beside your body."),
                WorkoutText("Cuốn tạ lên rồi hạ chậm, giữ thân không đung đưa.", "Curl the weights up and lower slowly without swinging.")),
            WorkoutText("Giữ cổ tay trung tính, giảm tạ nếu phải vung người.", "Keep wrists neutral; reduce weight if you need to swing.")),
    )
    const val trainer = "Trần Quốc Huy"
}
