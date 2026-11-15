package com.kgyms.app.data.model

internal data class NutritionValues(val kcal: Double, val protein: Double, val carb: Double, val fat: Double) {
    operator fun plus(other: NutritionValues) = NutritionValues(kcal + other.kcal, protein + other.protein, carb + other.carb, fat + other.fat)
    operator fun times(factor: Double) = NutritionValues(kcal * factor, protein * factor, carb * factor, fat * factor)
    companion object { val ZERO = NutritionValues(0.0, 0.0, 0.0, 0.0) }
}

/** Values per 100 g edible portion, matching the preparation state in the USDA description. */
internal data class NutritionIngredient(
    val vi: String, val en: String, val grams: Double,
    val fdcId: Int, val usdaDescription: String, val per100g: NutritionValues,
) {
    init { require(grams > 0 && grams.isFinite() && fdcId > 0) }
    val sourceUrl: String get() = "https://fdc.nal.usda.gov/food-details/$fdcId/nutrients"
    val nutrients: NutritionValues get() = per100g * (grams / 100.0)
}

internal data class NutritionRecipe(
    val servings: Int,
    val ingredients: List<NutritionIngredient>,
    val stepsVi: List<String>, val stepsEn: List<String>,
) {
    init { require(servings > 0 && ingredients.isNotEmpty()) }
    val perServing: NutritionValues get() = ingredients.fold(NutritionValues.ZERO) { sum, ingredient -> sum + ingredient.nutrients } * (1.0 / servings)
}

internal data class NutritionDaySummary(val selectedMeals: Int, val missingMeals: Int, val knownTotal: NutritionValues?) {
    val complete: Boolean get() = selectedMeals > 0 && missingMeals == 0
}

/** Empty slots are not consumed meals. Unknown IDs/data must never silently become zero. */
internal fun NutritionPlanData.dayNutrition(day: Int): NutritionDaySummary {
    require(day in 0..6)
    val ids = MealSlot.entries.mapNotNull { meals["$day:${it.name}"] }
    val known = ids.mapNotNull { NutritionMealCatalog.byId(it)?.nutritionPerServing }
    return NutritionDaySummary(ids.size, ids.size - known.size,
        known.takeIf { it.isNotEmpty() }?.fold(NutritionValues.ZERO) { total, value -> total + value })
}
