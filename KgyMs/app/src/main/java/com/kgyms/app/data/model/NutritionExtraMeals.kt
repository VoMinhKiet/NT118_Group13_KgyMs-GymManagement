package com.kgyms.app.data.model

internal data class NutritionPreparation(val stepsVi: List<String>, val stepsEn: List<String>)

/** Preparation is available for these dishes; nutrient estimates have not been verified. */
internal val nutritionExtraMeals = listOf(
    NutritionMeal("bf_pho_ga", MealSlot.BREAKFAST, "Phở gà", "Chicken pho", "120 g thịt gà, bánh phở, nước dùng gà, hành, gừng", "120 g chicken, rice noodles, chicken stock, scallions, ginger",
        vegetarian = false, proteinFocused = true, lightOption = false, symbol = "🍜",
        preparation = NutritionPreparation(
            stepsVi = listOf(
                "Nấu thịt gà trong nước dùng với gừng đến khi chín hẳn.",
                "Trụng bánh phở theo hướng dẫn trên gói, rồi xếp vào tô.",
                "Xé gà, chan nước dùng nóng và rắc hành.",
            ),
            stepsEn = listOf(
                "Simmer the chicken in stock with ginger until fully cooked.",
                "Prepare the rice noodles as directed and place them in a bowl.",
                "Shred the chicken, pour over the hot stock and top with scallions.",
            ),
        ),
    ),
    NutritionMeal("bf_chao_thit_bam", MealSlot.BREAKFAST, "Cháo thịt bằm", "Minced pork congee", "100 g thịt heo bằm, gạo, gừng, hành lá", "100 g minced pork, rice, ginger, scallions",
        vegetarian = false, proteinFocused = true, lightOption = false, symbol = "🥣",
        preparation = NutritionPreparation(
            stepsVi = listOf(
                "Vo gạo rồi ninh với nước và gừng cho đến khi nhừ.",
                "Xào thịt bằm cho chín kỹ, sau đó khuấy vào cháo và đun sôi lại.",
                "Nêm vừa ăn rồi rắc hành.",
            ),
            stepsEn = listOf(
                "Rinse the rice and simmer with water and ginger until soft.",
                "Cook the minced pork thoroughly, stir it into the congee and bring it back to a boil.",
                "Season to taste and top with scallions.",
            ),
        ),
    ),
    NutritionMeal("bf_banh_mi_op_la", MealSlot.BREAKFAST, "Bánh mì trứng ốp la", "Fried egg baguette", "Trứng, bánh mì, dưa leo, cà chua, dầu ăn", "Egg, baguette, cucumber, tomato, cooking oil",
        vegetarian = true, proteinFocused = false, lightOption = false, symbol = "🥖",
        preparation = NutritionPreparation(
            stepsVi = listOf(
                "Rửa và thái dưa leo, cà chua.",
                "Áp chảo trứng đến khi lòng trắng và lòng đỏ đông.",
                "Làm nóng bánh mì rồi kẹp trứng và rau.",
            ),
            stepsEn = listOf(
                "Wash and slice the cucumber and tomato.",
                "Fry the egg until both the white and yolk are set.",
                "Warm the baguette and fill it with the egg and vegetables.",
            ),
        ),
    ),
    NutritionMeal("bf_pancake_yen_mach", MealSlot.BREAKFAST, "Pancake yến mạch", "Oat pancakes", "Yến mạch, chuối chín, trứng, sữa, dầu ăn", "Oats, ripe banana, egg, milk, cooking oil",
        vegetarian = true, proteinFocused = false, lightOption = false, symbol = "🥞",
        preparation = NutritionPreparation(
            stepsVi = listOf(
                "Xay yến mạch rồi trộn với chuối nghiền, trứng và một ít sữa.",
                "Đổ từng muỗng bột vào chảo chống dính đã làm nóng.",
                "Lật bánh khi mặt dưới vàng và chiên đến khi bột chín hẳn.",
            ),
            stepsEn = listOf(
                "Blend the oats and mix with mashed banana, egg and a little milk.",
                "Spoon the batter into a hot nonstick pan.",
                "Flip when golden underneath and cook until the batter is fully set.",
            ),
        ),
    ),
    NutritionMeal("lu_com_heo_nuong", MealSlot.LUNCH, "Cơm thịt heo nướng", "Grilled pork rice bowl", "130 g thịt heo nạc, cơm, dưa leo, tỏi, nước tương", "130 g lean pork, rice, cucumber, garlic, soy sauce",
        vegetarian = false, proteinFocused = true, lightOption = false, symbol = "🍚",
        preparation = NutritionPreparation(
            stepsVi = listOf(
                "Ướp thịt heo với tỏi và nước tương trong tủ lạnh.",
                "Nướng thịt đến khi phần dày nhất đạt 63°C rồi để nghỉ ít nhất 3 phút.",
                "Thái thịt và xếp lên cơm cùng dưa leo.",
            ),
            stepsEn = listOf(
                "Marinate the pork with garlic and soy sauce in the fridge.",
                "Grill until the thickest part reaches 63°C and rest for at least 3 minutes.",
                "Slice and serve with rice and cucumber.",
            ),
        ),
    ),
    NutritionMeal("lu_com_bo_xao", MealSlot.LUNCH, "Cơm bò xào rau củ", "Beef and vegetable rice bowl", "120 g thịt bò nạc, cơm, bông cải, ớt chuông, tỏi", "120 g lean beef, rice, broccoli, bell pepper, garlic",
        vegetarian = false, proteinFocused = true, lightOption = false, symbol = "🥩",
        preparation = NutritionPreparation(
            stepsVi = listOf(
                "Cắt bò, bông cải và ớt chuông thành miếng vừa ăn.",
                "Xào bò với tỏi đến khi chín, rồi thêm rau và xào đến khi vừa mềm.",
                "Dọn cùng cơm nóng.",
            ),
            stepsEn = listOf(
                "Cut the beef, broccoli and bell pepper into bite-size pieces.",
                "Stir-fry the beef with garlic until cooked, then add the vegetables and cook until just tender.",
                "Serve over warm rice.",
            ),
        ),
    ),
    NutritionMeal("lu_bun_ga_tron", MealSlot.LUNCH, "Bún gà trộn", "Chicken noodle salad", "120 g ức gà, bún, dưa leo, rau thơm, chanh", "120 g chicken breast, rice noodles, cucumber, herbs, lime",
        vegetarian = false, proteinFocused = true, lightOption = true, symbol = "🍜",
        preparation = NutritionPreparation(
            stepsVi = listOf(
                "Luộc ức gà đến khi phần dày nhất đạt 74°C, rồi để nguội và xé sợi.",
                "Chuẩn bị bún, rửa dưa leo và rau thơm.",
                "Trộn bún với thịt gà, rau và nước cốt chanh trước khi ăn.",
            ),
            stepsEn = listOf(
                "Poach the chicken until the thickest part reaches 74°C, then cool and shred it.",
                "Prepare the noodles and wash the cucumber and herbs.",
                "Toss the noodles with chicken, vegetables and lime juice just before serving.",
            ),
        ),
    ),
    NutritionMeal("di_ca_hap_gung", MealSlot.DINNER, "Cá hấp gừng", "Ginger-steamed fish", "150 g phi lê cá, gừng, hành lá, chanh", "150 g fish fillet, ginger, scallions, lime",
        vegetarian = false, proteinFocused = true, lightOption = true, symbol = "🐟",
        preparation = NutritionPreparation(
            stepsVi = listOf(
                "Đặt phi lê lên đĩa chịu nhiệt cùng gừng thái sợi.",
                "Hấp đến khi thịt cá đục và dễ tách bằng nĩa.",
                "Rắc hành lá và thêm chanh khi dùng.",
            ),
            stepsEn = listOf(
                "Place the fish on a heatproof plate with sliced ginger.",
                "Steam until the fish is opaque and flakes easily with a fork.",
                "Top with scallions and serve with lime.",
            ),
        ),
    ),
    NutritionMeal("di_mien_ga_nam", MealSlot.DINNER, "Miến gà nấm", "Chicken and mushroom glass noodle soup", "120 g thịt gà, miến, nấm, nước dùng gà, hành", "120 g chicken, glass noodles, mushrooms, chicken stock, scallions",
        vegetarian = false, proteinFocused = true, lightOption = false, symbol = "🍲",
        preparation = NutritionPreparation(
            stepsVi = listOf(
                "Nấu gà trong nước dùng đến khi chín hẳn, rồi lấy ra xé sợi.",
                "Nấu nấm trong nước dùng; chuẩn bị miến theo hướng dẫn trên gói.",
                "Cho miến, thịt và nấm vào tô rồi chan nước dùng nóng.",
            ),
            stepsEn = listOf(
                "Simmer the chicken in stock until fully cooked, then remove and shred it.",
                "Cook the mushrooms in the stock and prepare the noodles as directed.",
                "Put the noodles, chicken and mushrooms in a bowl and add the hot stock.",
            ),
        ),
    ),
    NutritionMeal("di_salad_ga_khoai", MealSlot.DINNER, "Salad ức gà khoai lang", "Chicken and sweet potato salad", "130 g ức gà, khoai lang, xà lách, chanh, dầu ô liu", "130 g chicken breast, sweet potato, lettuce, lemon, olive oil",
        vegetarian = false, proteinFocused = true, lightOption = true, symbol = "🥗",
        preparation = NutritionPreparation(
            stepsVi = listOf(
                "Hấp hoặc nướng khoai lang đến khi mềm.",
                "Nấu ức gà đến khi phần dày nhất đạt 74°C, rồi thái lát.",
                "Trộn xà lách đã rửa với gà, khoai, chanh và một ít dầu ô liu.",
            ),
            stepsEn = listOf(
                "Steam or roast the sweet potato until tender.",
                "Cook the chicken to 74°C in the thickest part and slice it.",
                "Toss washed lettuce with chicken, sweet potato, lemon and a little olive oil.",
            ),
        ),
    ),
    NutritionMeal("sn_sinh_to_chuoi", MealSlot.SNACK, "Sinh tố chuối sữa chua", "Banana yogurt smoothie", "Chuối chín, sữa chua không đường, sữa hoặc nước lạnh", "Ripe banana, plain yogurt, milk or cold water",
        vegetarian = true, proteinFocused = false, lightOption = true, symbol = "🥤",
        preparation = NutritionPreparation(
            stepsVi = listOf(
                "Bóc vỏ và cắt chuối thành lát.",
                "Xay chuối với sữa chua và một ít sữa hoặc nước lạnh.",
                "Rót ra ly và dùng ngay.",
            ),
            stepsEn = listOf(
                "Peel and slice the banana.",
                "Blend the banana with yogurt and a little milk or cold water.",
                "Pour into a glass and serve immediately.",
            ),
        ),
    ),
    NutritionMeal("sn_sua_chua_granola", MealSlot.SNACK, "Sữa chua granola", "Yogurt with granola", "Sữa chua không đường, granola không gelatin, trái cây tươi", "Plain yogurt, gelatin-free granola, fresh fruit",
        vegetarian = true, proteinFocused = false, lightOption = true, symbol = "🥣",
        preparation = NutritionPreparation(
            stepsVi = listOf(
                "Rửa và cắt trái cây vừa ăn.",
                "Cho sữa chua vào bát rồi thêm trái cây.",
                "Rắc granola ngay trước khi ăn để giữ độ giòn.",
            ),
            stepsEn = listOf(
                "Wash and cut the fruit into bite-size pieces.",
                "Spoon the yogurt into a bowl and add the fruit.",
                "Sprinkle over the granola just before serving so it stays crunchy.",
            ),
        ),
    ),
)
