package com.kgyms.app.data.model

import org.junit.Assert.*
import org.junit.Test

class NutritionRecipeTest {
    private val setup = NutritionSetup(NutritionGoal.MAINTAIN, NutritionActivity.LOW, NutritionDiet.REGULAR)
    private fun plan(vararg ids: String) = NutritionPlanData(setup,
        ids.mapIndexed { index, id -> "0:${MealSlot.entries[index].name}" to id }.toMap())

    @Test fun `grams and servings scale all four nutrients`() {
        val ingredient = NutritionIngredient("Test", "Test", 150.0, 173944, "Fixture",
            NutritionValues(100.0, 10.0, 20.0, 5.0))
        val recipe = NutritionRecipe(2, listOf(ingredient), listOf("Test"), listOf("Test"))
        assertEquals(NutritionValues(75.0, 7.5, 15.0, 3.75), recipe.perServing)
    }

    @Test fun `banana yogurt estimate matches USDA weighed ingredients`() {
        val value = NutritionMealCatalog.byId("sn_banana")!!.nutritionPerServing!!
        assertEquals(180.5, value.kcal, .0001)
        assertEquals(6.295, value.protein, .0001)
        assertEquals(29.79, value.carb, .0001)
        assertEquals(5.205, value.fat, .0001)
    }

    @Test fun `four meals sum without rounding intermediate values`() {
        val ids = arrayOf("bf_oats", "lu_chicken", "di_tofu", "sn_eggs")
        val summary = plan(*ids).dayNutrition(0)
        val expected = ids.map { NutritionMealCatalog.byId(it)!!.nutritionPerServing!! }
            .reduce { a, b -> a + b }
        assertEquals(expected, summary.knownTotal)
        assertEquals(4, summary.selectedMeals)
        assertTrue(summary.complete)
    }

    @Test fun `changing meal and removing meal immediately recompute totals`() {
        val original = plan("bf_oats", "lu_chicken", "di_tofu", "sn_eggs")
        val changed = original.copy(meals = original.meals + ("0:SNACK" to "sn_fruit"))
        assertNotEquals(original.dayNutrition(0).knownTotal, changed.dayNutrition(0).knownTotal)
        val removed = changed.copy(meals = changed.meals - "0:SNACK")
        assertEquals(3, removed.dayNutrition(0).selectedMeals)
        assertEquals(changed.dayNutrition(0).knownTotal!!.kcal - 93.6,
            removed.dayNutrition(0).knownTotal!!.kcal, .0001)
        assertNull(original.dayNutrition(1).knownTotal)
    }

    @Test fun `unknown meal gives incomplete subtotal never a complete or zero total`() {
        val partial = plan("bf_oats", "unknown").dayNutrition(0)
        assertFalse(partial.complete)
        assertEquals(1, partial.missingMeals)
        assertEquals(NutritionMealCatalog.byId("bf_oats")!!.nutritionPerServing, partial.knownTotal)
        val unknownOnly = plan("unknown").dayNutrition(0)
        assertNull(unknownOnly.knownTotal)
        assertFalse(unknownOnly.complete)
        assertFalse(plan().dayNutrition(0).complete)
    }

    @Test fun `all legacy meal IDs resolve without changing selections`() {
        val legacyIds = setOf("bf_oats", "bf_egg", "bf_tofu", "bf_chicken", "bf_fruit",
            "lu_chicken", "lu_tofu", "lu_fish", "lu_veg", "lu_beans", "di_salmon", "di_tofu",
            "di_chicken", "di_egg", "di_soup", "sn_banana", "sn_fruit", "sn_nuts", "sn_eggs")
        assertTrue(NutritionMealCatalog.meals.map { it.id }.toSet().containsAll(legacyIds))
        assertEquals(legacyIds, NutritionRecipes.byMealId.keys)
        val saved = NutritionMealCatalog.suggest(setup)
        val before = saved.toMap()
        (0..6).forEach { NutritionPlanData(setup, saved).dayNutrition(it) }
        assertEquals(before, saved)
    }

    @Test fun `all completed recipes have bilingual steps quantities and traceable sources`() {
        NutritionRecipes.byMealId.values.forEach { recipe ->
            assertTrue(recipe.stepsVi.size in 3..5)
            assertEquals(recipe.stepsVi.size, recipe.stepsEn.size)
            assertTrue(recipe.stepsVi.all { it.isNotBlank() })
            assertTrue(recipe.stepsEn.all { it.isNotBlank() })
            assertTrue(recipe.ingredients.all { it.grams > 0 && it.usdaDescription.isNotBlank() && it.sourceUrl.contains("/${it.fdcId}/") })
            assertTrue(recipe.perServing.kcal > 0)
        }
        assertEquals(19, NutritionRecipes.byMealId.values.map { it.stepsEn }.distinct().size)
    }

    @Test(expected = IllegalArgumentException::class) fun `zero servings rejected`() {
        NutritionRecipe(0, NutritionRecipes.byMealId.getValue("sn_eggs").ingredients, emptyList(), emptyList())
    }
}
