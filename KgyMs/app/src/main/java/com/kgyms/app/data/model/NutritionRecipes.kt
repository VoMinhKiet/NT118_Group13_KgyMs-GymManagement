package com.kgyms.app.data.model

/** USDA SR Legacy April 2018 JSON, retrieved 2026-11-16.
 * https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_json_2018-04.zip
 * Nutrient IDs: 1008 kcal, 1003 protein, 1005 carbohydrate, 1004 fat, per 100 g.
 * These are explicit reference recipe variants, not measured restaurant dishes.
 * No unlisted oil, sugar or sauce is included. Water contributes no macros.
 */
internal object NutritionRecipes {
    private fun food(vi: String, en: String, id: Int, description: String, kcal: Double, p: Double, c: Double, f: Double) =
        NutritionIngredient(vi, en, 100.0, id, description, NutritionValues(kcal, p, c, f))
    private val oats = food("Yến mạch khô", "Dry oats", 173904, "Cereals, oats, regular and quick, not fortified, dry", 379.0, 13.2, 67.7, 6.52)
    private val banana = food("Chuối, bỏ vỏ", "Peeled banana", 173944, "Bananas, raw", 89.0, 1.09, 22.8, .33)
    private val yogurt = food("Sữa chua nguyên kem không đường", "Plain whole-milk yogurt", 171284, "Yogurt, plain, whole milk", 61.0, 3.47, 4.66, 3.25)
    private val egg = food("Trứng sống, bỏ vỏ (cân trước khi nấu)", "Raw egg without shell (weigh before cooking)", 171287, "Egg, whole, raw, fresh", 143.0, 12.6, .72, 9.51)
    private val boiledEgg = food("Trứng luộc chín, bỏ vỏ", "Peeled hard-boiled egg", 173424, "Egg, whole, cooked, hard-boiled", 155.0, 12.6, 1.12, 10.6)
    private val bread = food("Bánh mì nguyên cám", "Whole-wheat bread", 172688, "Bread, whole-wheat, commercially prepared", 252.0, 12.4, 42.7, 3.5)
    private val tomato = food("Cà chua sống", "Raw tomato", 170457, "Tomatoes, red, ripe, raw, year round average", 18.0, .88, 3.89, .2)
    private val tofu = food("Đậu hũ cứng, cân trước khi nấu", "Firm tofu, weigh before cooking", 172475, "Tofu, raw, firm, prepared with calcium sulfate", 144.0, 17.3, 2.78, 8.72)
    private val lettuce = food("Xà lách lá xanh sống", "Raw green-leaf lettuce", 169249, "Lettuce, green leaf, raw", 15.0, 1.36, 2.87, .15)
    private val chicken = food("Ức gà bỏ da, đã nướng chín", "Skinless roasted chicken breast, cooked weight", 171477, "Chicken, broilers or fryers, breast, meat only, cooked, roasted", 165.0, 31.0, 0.0, 3.57)
    private val rice = food("Cơm trắng đã nấu chín", "Cooked white rice", 168878, "Rice, white, long-grain, regular, enriched, cooked", 130.0, 2.69, 28.2, .28)
    private val broccoli = food("Bông cải xanh đã luộc, để ráo", "Boiled drained broccoli", 169967, "Broccoli, cooked, boiled, drained, without salt", 35.0, 2.38, 7.18, .41)
    private val mushroom = food("Nấm mỡ sống", "Raw white mushrooms", 169251, "Mushrooms, white, raw", 22.0, 3.09, 3.26, .34)
    private val fish = food("Cá rô phi đã nấu chín bằng nhiệt khô", "Tilapia cooked with dry heat", 175177, "Fish, tilapia, cooked, dry heat", 128.0, 26.2, 0.0, 2.65)
    private val noodles = food("Bún gạo đã nấu chín", "Cooked rice noodles", 168914, "Rice noodles, cooked", 108.0, 1.79, 24.0, .2)
    private val beans = food("Đậu đỏ kidney đã luộc chín", "Fully cooked red kidney beans", 175194, "Beans, kidney, red, mature seeds, cooked, boiled, without salt", 127.0, 8.67, 22.8, .5)
    private val sweetPotato = food("Khoai lang luộc bỏ vỏ", "Peeled boiled sweet potato", 168484, "Sweet potato, cooked, boiled, without skin", 76.0, 1.37, 17.7, .14)
    private val potato = food("Khoai tây luộc bỏ vỏ", "Peeled boiled potato", 170440, "Potatoes, boiled, cooked without skin, flesh, without salt", 86.0, 1.71, 20.0, .1)
    private val oil = food("Dầu ô liu (tính toàn bộ lượng dùng)", "Olive oil (all used oil counted)", 171413, "Oil, olive, salad or cooking", 884.0, 0.0, 0.0, 100.0)
    private val apple = food("Táo cả vỏ, bỏ lõi", "Apple with skin, core removed", 171688, "Apples, raw, with skin (Includes foods for USDA's Food Distribution Program)", 52.0, .26, 13.8, .17)
    private val almonds = food("Hạnh nhân", "Almonds", 170567, "Nuts, almonds", 579.0, 21.2, 21.6, 49.9)
    private val chia = food("Hạt chia khô", "Dried chia seeds", 170554, "Seeds, chia seeds, dried", 486.0, 16.5, 42.1, 30.7)
    private fun recipe(vararg ingredients: Pair<NutritionIngredient, Int>, vi: List<String>, en: List<String>) =
        NutritionRecipe(1, ingredients.map { (food, grams) -> food.copy(grams = grams.toDouble()) }, vi, en)

    val byMealId: Map<String, NutritionRecipe> = mapOf(
        "bf_oats" to recipe(oats to 40, banana to 100, yogurt to 150,
            vi = listOf("Nấu yến mạch với nước đến khi mềm rồi để nguội bớt.", "Bóc vỏ chuối, cân phần ăn được và thái lát.", "Cho sữa chua vào bát yến mạch, thêm chuối và dùng ngay."),
            en = listOf("Cook oats in water until soft and let cool slightly.", "Peel, weigh and slice the banana.", "Top the oats with yogurt and banana; serve immediately.")),
        "bf_egg" to recipe(egg to 100, bread to 60, tomato to 100,
            vi = listOf("Rửa cà chua, thái lát; đập trứng và cân phần không vỏ.", "Đánh trứng rồi nấu trong chảo chống dính không thêm dầu đến khi chín hẳn (71°C).", "Nướng bánh mì nhẹ, ăn kèm trứng và cà chua."),
            en = listOf("Wash and slice the tomato; crack and weigh the eggs without shells.", "Beat eggs and cook in a nonstick pan without added oil until fully set (71°C).", "Lightly toast the bread and serve with eggs and tomato.")),
        "bf_tofu" to recipe(tofu to 100, bread to 60, lettuce to 50, oil to 5,
            vi = listOf("Thấm khô đậu hũ, cắt lát; rửa xà lách và để ráo.", "Làm nóng dầu, áp chảo đậu hũ đến khi nóng đều và vàng hai mặt.", "Kẹp đậu hũ và xà lách vào bánh mì nguyên cám."),
            en = listOf("Pat tofu dry and slice; wash and drain lettuce.", "Heat the measured oil and pan-sear tofu until hot and golden on both sides.", "Layer tofu and lettuce in the whole-wheat bread.")),
        "bf_chicken" to recipe(chicken to 80, bread to 60, lettuce to 40,
            vi = listOf("Nướng ức gà bỏ da đến nhiệt độ tâm 74°C, sau đó cân lượng chín và xé nhỏ.", "Rửa xà lách, để ráo; làm ấm bánh mì.", "Kẹp gà và xà lách vào bánh mì, không thêm sốt ngoài công thức."),
            en = listOf("Roast skinless chicken to a core temperature of 74°C, then weigh the cooked portion and shred.", "Wash and drain lettuce; warm the bread.", "Fill the bread with chicken and lettuce, without extra sauces.")),
        "bf_fruit" to recipe(yogurt to 150, apple to 120, chia to 10,
            vi = listOf("Rửa táo, bỏ lõi rồi cân và cắt miếng.", "Trộn hạt chia vào sữa chua, để hạt hút ẩm và nở mềm.", "Thêm táo và dùng khi hạt đã mềm; không ăn hạt chia khô riêng."),
            en = listOf("Wash and core the apple, then weigh and dice it.", "Stir chia into yogurt and allow the seeds to hydrate and soften.", "Add apple once seeds are soft; do not eat dry chia separately.")),
        "lu_chicken" to recipe(rice to 150, chicken to 120, broccoli to 150,
            vi = listOf("Nấu cơm, luộc bông cải rồi để ráo; cân sau khi chín.", "Nướng ức gà bỏ da đến tâm 74°C, cân phần thịt chín và thái lát.", "Bày cơm, gà và bông cải ra đĩa; không thêm dầu hoặc sốt ngoài công thức."),
            en = listOf("Cook rice and boil broccoli; drain and weigh both after cooking.", "Roast skinless chicken to 74°C, weigh the cooked meat and slice.", "Plate rice, chicken and broccoli without additional oil or sauce.")),
        "lu_tofu" to recipe(rice to 150, tofu to 120, mushroom to 80, broccoli to 100, oil to 5,
            vi = listOf("Nấu cơm và luộc bông cải, cân lượng đã chín; cắt đậu và nấm.", "Xào nấm với dầu đã cân, thêm chút nước tạo sốt nấm đơn giản.", "Cho đậu vào om đến khi nóng đều, dùng với cơm và bông cải."),
            en = listOf("Cook and weigh rice and broccoli; slice tofu and mushrooms.", "Sauté mushrooms in the measured oil and add a splash of water for a simple sauce.", "Simmer tofu in the mushrooms until hot throughout; serve with rice and broccoli.")),
        "lu_fish" to recipe(rice to 150, fish to 120, broccoli to 150,
            vi = listOf("Nấu cơm và luộc bông cải; cân lượng chín, để ráo.", "Nướng cá rô phi đến tâm 63°C, gỡ xương và cân thịt cá chín.", "Bày cá cùng cơm và rau, dùng khi còn nóng."),
            en = listOf("Cook rice and boil broccoli; drain and weigh cooked portions.", "Bake tilapia to 63°C, remove bones and weigh the cooked fish.", "Serve fish with rice and broccoli while hot.")),
        "lu_veg" to recipe(noodles to 150, tofu to 100, broccoli to 100, tomato to 80,
            vi = listOf("Nấu bún gạo và luộc bông cải, để ráo rồi cân.", "Hấp đậu hũ đến nóng đều; rửa và thái cà chua.", "Xếp bún, đậu và rau vào tô, dùng không thêm sốt ngoài công thức."),
            en = listOf("Cook rice noodles and boil broccoli; drain and weigh.", "Steam tofu until hot throughout; wash and slice tomato.", "Arrange noodles, tofu and vegetables in a bowl without extra sauce.")),
        "lu_beans" to recipe(beans to 120, sweetPotato to 150, lettuce to 60, oil to 5,
            vi = listOf("Dùng đậu kidney đã luộc chín hoàn toàn, cân sau khi để ráo.", "Luộc khoai lang bỏ vỏ, cân và cắt miếng; rửa xà lách.", "Trộn đậu, khoai và xà lách với lượng dầu đã cân."),
            en = listOf("Use fully cooked kidney beans; drain and weigh them.", "Boil peeled sweet potato, weigh and dice; wash lettuce.", "Toss beans, sweet potato and lettuce with the measured oil.")),
        "di_salmon" to recipe(fish to 130, potato to 180, broccoli to 120, oil to 5,
            vi = listOf("Luộc khoai tây bỏ vỏ và bông cải, cân sau khi chín.", "Dùng dầu đã cân áp chảo cá rô phi đến tâm 63°C; cân thịt cá đã chín, bỏ xương.", "Bày cá cùng khoai và rau. Công thức tham khảo này dùng cá rô phi."),
            en = listOf("Boil peeled potato and broccoli and weigh after cooking.", "Pan-sear tilapia in the measured oil to 63°C; weigh cooked boneless flesh.", "Plate with potato and broccoli. This reference variant uses tilapia.")),
        "di_tofu" to recipe(tofu to 150, rice to 150, broccoli to 100,
            vi = listOf("Cắt đậu hũ thành khối, nấu cơm và luộc bông cải.", "Om đậu với ít nước đến khi nóng đều và nước rút bớt; không thêm đường hoặc sốt ngoài công thức.", "Cân cơm và rau đã chín, dùng cùng đậu om."),
            en = listOf("Cube tofu, cook rice and boil broccoli.", "Braise tofu in a little water until hot and slightly reduced; add no extra sugar or sauce.", "Weigh cooked rice and broccoli and serve with braised tofu.")),
        "di_chicken" to recipe(chicken to 130, broccoli to 180, potato to 150,
            vi = listOf("Nướng ức gà bỏ da đến tâm 74°C, cân sau khi chín.", "Luộc khoai tây bỏ vỏ và bông cải, để ráo và cân.", "Thái gà và bày với khoai, rau; dùng nóng."),
            en = listOf("Roast skinless chicken breast to 74°C and weigh after cooking.", "Boil peeled potato and broccoli, then drain and weigh.", "Slice chicken and serve hot with potato and broccoli.")),
        "di_egg" to recipe(egg to 100, rice to 150, tomato to 80, broccoli to 80, oil to 5,
            vi = listOf("Nấu cơm, luộc bông cải và cân lượng chín; thái nhỏ cà chua và rau.", "Đánh trứng với rau, tráng trong chảo với dầu đã cân.", "Khi trứng chín hẳn (71°C), cuộn lại và dùng cùng cơm."),
            en = listOf("Cook rice and broccoli and weigh cooked portions; finely chop tomato and broccoli.", "Beat eggs with vegetables and spread in a pan with the measured oil.", "Once eggs are fully set (71°C), roll the omelet and serve with rice.")),
        "di_soup" to recipe(beans to 150, broccoli to 120, mushroom to 80,
            vi = listOf("Dùng đậu kidney đã luộc chín hoàn toàn; rửa và thái nấm.", "Nấu nấm với nước đến chín, thêm đậu chín và bông cải đã luộc.", "Đun cho các nguyên liệu nóng đều, múc ra bát; lượng nước không làm đổi tổng kcal."),
            en = listOf("Use fully cooked kidney beans; clean and slice mushrooms.", "Simmer mushrooms in water until cooked, then add cooked beans and boiled broccoli.", "Heat everything through and serve; water quantity does not change total calories.")),
        "sn_banana" to recipe(banana to 100, yogurt to 150,
            vi = listOf("Bóc vỏ chuối và cân phần ăn được.", "Thái chuối vào bát, thêm sữa chua không đường.", "Trộn nhẹ và dùng ngay, không thêm đường."),
            en = listOf("Peel and weigh the edible banana portion.", "Slice into a bowl and add plain yogurt.", "Mix gently and serve immediately without added sugar.")),
        "sn_fruit" to recipe(apple to 180,
            vi = listOf("Công thức tham khảo chọn táo làm trái cây.", "Rửa kỹ, bỏ lõi và cân phần táo cả vỏ.", "Cắt miếng vừa ăn và dùng ngay."),
            en = listOf("This reference fruit serving uses apple.", "Wash, core and weigh the apple with its skin.", "Cut into bite-sized pieces and serve immediately.")),
        "sn_nuts" to recipe(apple to 150, almonds to 20,
            vi = listOf("Rửa táo, bỏ lõi và cân phần cả vỏ.", "Cắt táo thành miếng; cân hạnh nhân không tẩm phủ.", "Bày cùng nhau để dùng; kiểm tra dị ứng hạt trước khi ăn."),
            en = listOf("Wash and core the apple; weigh it with skin.", "Slice the apple and weigh plain almonds.", "Serve together; check nut allergies before eating.")),
        "sn_eggs" to recipe(boiledEgg to 100,
            vi = listOf("Cho trứng vào nồi nước, đun đến khi lòng trắng và lòng đỏ chín chắc.", "Làm nguội bằng nước sạch rồi bóc vỏ.", "Cân phần trứng đã luộc và dùng ngay hoặc bảo quản lạnh."),
            en = listOf("Boil eggs in water until both whites and yolks are firm.", "Cool in clean water and peel.", "Weigh the cooked peeled eggs; serve promptly or refrigerate.")),
    )
}
