package com.kgyms.app.data.model

/** Nutrition preferences are intentionally separate from onboarding body measurements. */
internal enum class NutritionGoal(val vi: String, val en: String) {
    FAT_LOSS("Giảm mỡ", "Fat loss"),
    MUSCLE_GAIN("Tăng cơ", "Muscle gain"),
    MAINTAIN("Duy trì cân nặng", "Maintain weight")
}

internal enum class NutritionActivity(val vi: String, val en: String) {
    LOW("Ít vận động", "Low activity"),
    MODERATE("Vận động vừa phải", "Moderate activity"),
    HIGH("Vận động nhiều", "High activity")
}

internal enum class NutritionDiet(val vi: String, val en: String) {
    REGULAR("Ăn uống bình thường", "Regular"),
    VEGETARIAN("Ăn chay", "Vegetarian")
}

internal enum class MealSlot(val vi: String, val en: String, val emoji: String) {
    BREAKFAST("Bữa sáng", "Breakfast", "☀️"),
    LUNCH("Bữa trưa", "Lunch", "🥗"),
    DINNER("Bữa tối", "Dinner", "🌙"),
    SNACK("Bữa phụ", "Snack", "🍎")
}

internal data class NutritionSetup(
    val goal: NutritionGoal,
    val activity: NutritionActivity,
    val diet: NutritionDiet,
)

internal data class NutritionPlanData(
    val setup: NutritionSetup? = null,
    val meals: Map<String, String> = emptyMap(),
    val generatedFor: String = "",
) {
    val hasSetup: Boolean get() = setup != null
}

internal data class NutritionMeal(
    val id: String,
    val slot: MealSlot,
    val vi: String,
    val en: String,
    val ingredientsVi: String,
    val ingredientsEn: String,
    val vegetarian: Boolean,
    val proteinFocused: Boolean = false,
    val lightOption: Boolean = false,
    val symbol: String,
) {
    val recipe: NutritionRecipe? get() = NutritionRecipes.byMealId[id]
    val nutritionPerServing: NutritionValues? get() = recipe?.perServing
}

/** Reference recipes; estimates are calculated from explicitly weighed USDA ingredients. */
internal object NutritionMealCatalog {
    val meals: List<NutritionMeal> = listOf(
        NutritionMeal("bf_oats", MealSlot.BREAKFAST, "Yến mạch, chuối và sữa chua", "Oats, banana & yogurt", "Yến mạch, chuối, sữa chua", "Oats, banana, yogurt", true, false, false, "🥣"),
        NutritionMeal("bf_egg", MealSlot.BREAKFAST, "Trứng và bánh mì nguyên cám", "Eggs & whole-wheat toast", "Trứng, bánh mì nguyên cám, cà chua", "Eggs, whole-wheat bread, tomato", true, true, false, "🍳"),
        NutritionMeal("bf_tofu", MealSlot.BREAKFAST, "Đậu hũ áp chảo và bánh mì", "Tofu & toast", "Đậu hũ, bánh mì, rau xanh", "Tofu, bread, greens", true, true, true, "🥪"),
        NutritionMeal("bf_chicken", MealSlot.BREAKFAST, "Bánh mì gà xé", "Shredded chicken sandwich", "Thịt gà, bánh mì, xà lách", "Chicken, bread, lettuce", false, true, false, "🥖"),
        NutritionMeal("bf_fruit", MealSlot.BREAKFAST, "Sữa chua, trái cây và hạt", "Yogurt, fruit & seeds", "Sữa chua, trái cây, hạt ăn được", "Yogurt, fruit, edible seeds", true, false, true, "🫐"),
        NutritionMeal("lu_chicken", MealSlot.LUNCH, "Cơm gà nướng và rau củ", "Grilled chicken & vegetables", "Cơm, thịt gà, rau củ", "Rice, chicken, vegetables", false, true, false, "🍗"),
        NutritionMeal("lu_tofu", MealSlot.LUNCH, "Cơm đậu hũ sốt nấm", "Tofu & mushroom rice", "Cơm, đậu hũ, nấm, rau", "Rice, tofu, mushrooms, greens", true, true, false, "🍚"),
        NutritionMeal("lu_fish", MealSlot.LUNCH, "Cơm cá và rau luộc", "Fish, rice & greens", "Cơm, cá, rau xanh", "Rice, fish, greens", false, true, true, "🐟"),
        NutritionMeal("lu_veg", MealSlot.LUNCH, "Bún rau củ và đậu hũ", "Vegetable tofu noodle bowl", "Bún, đậu hũ, rau củ", "Rice noodles, tofu, vegetables", true, false, true, "🍜"),
        NutritionMeal("lu_beans", MealSlot.LUNCH, "Salad đậu và khoai lang", "Bean & sweet potato salad", "Đậu, khoai lang, rau xanh", "Beans, sweet potato, greens", true, true, true, "🥗"),
        NutritionMeal("di_salmon", MealSlot.DINNER, "Cá áp chảo và khoai", "Pan-seared fish & potatoes", "Cá, khoai, rau củ", "Fish, potato, vegetables", false, true, true, "🍽️"),
        NutritionMeal("di_tofu", MealSlot.DINNER, "Đậu hũ kho và cơm", "Braised tofu & rice", "Đậu hũ, cơm, rau", "Tofu, rice, greens", true, true, false, "🍲"),
        NutritionMeal("di_chicken", MealSlot.DINNER, "Ức gà và rau củ", "Chicken breast & greens", "Ức gà, rau củ, khoai", "Chicken breast, vegetables, potato", false, true, true, "🥘"),
        NutritionMeal("di_egg", MealSlot.DINNER, "Trứng cuộn rau và cơm", "Veggie omelet & rice", "Trứng, cơm, rau củ", "Eggs, rice, vegetables", true, true, false, "🍳"),
        NutritionMeal("di_soup", MealSlot.DINNER, "Canh đậu và rau củ", "Bean & vegetable soup", "Đậu, rau, nấm", "Beans, vegetables, mushrooms", true, false, true, "🍵"),
        NutritionMeal("sn_banana", MealSlot.SNACK, "Chuối và sữa chua", "Banana & yogurt", "Chuối, sữa chua", "Banana, yogurt", true, false, false, "🍌"),
        NutritionMeal("sn_fruit", MealSlot.SNACK, "Trái cây tươi", "Fresh fruit", "Trái cây theo mùa", "Seasonal fruit", true, false, true, "🍎"),
        NutritionMeal("sn_nuts", MealSlot.SNACK, "Trái cây và hạt", "Fruit & nuts", "Trái cây, hạt ăn được", "Fruit, edible nuts", true, false, false, "🥜"),
        NutritionMeal("sn_eggs", MealSlot.SNACK, "Trứng luộc", "Boiled eggs", "Trứng", "Eggs", true, true, false, "🥚"),
    )

    fun available(slot: MealSlot, setup: NutritionSetup): List<NutritionMeal> = meals.filter {
        it.slot == slot && (setup.diet != NutritionDiet.VEGETARIAN || it.vegetarian)
    }

    fun byId(id: String): NutritionMeal? = meals.firstOrNull { it.id == id }

    /** Stable, explainable rotation; does not claim to meet a medical or caloric target. */
    fun suggest(setup: NutritionSetup): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (day in 0..6) for (slot in MealSlot.entries) {
            val options = available(slot, setup).sortedWith(
                compareByDescending<NutritionMeal> {
                    when (setup.goal) {
                        NutritionGoal.MUSCLE_GAIN -> it.proteinFocused
                        NutritionGoal.FAT_LOSS -> it.lightOption
                        NutritionGoal.MAINTAIN -> false
                    }
                }.thenBy { it.id }
            )
            if (options.isNotEmpty()) {
                // Vary suggested meals across days; activity influences rotation, not fake calorie targets.
                val offset = if (setup.activity == NutritionActivity.HIGH) 1 else 0
                result["$day:${slot.name}"] = options[(day + offset + slot.ordinal) % options.size].id
            }
        }
        return result
    }

    fun fingerprint(setup: NutritionSetup): String = "${setup.goal.name}:${setup.activity.name}:${setup.diet.name}"
}

