package com.kgyms.app.data.model

import org.junit.Assert.*
import org.junit.Test

class NutritionMealCatalogTest {
    @Test fun `vegetarian plan has no nonvegetarian dishes`() {
        val setup = NutritionSetup(NutritionGoal.MUSCLE_GAIN, NutritionActivity.MODERATE, NutritionDiet.VEGETARIAN)
        val plan = NutritionMealCatalog.suggest(setup)
        assertEquals(28, plan.size)
        assertTrue(plan.values.all { NutritionMealCatalog.byId(it)?.vegetarian == true })
    }

    @Test fun `plan contains 7 days and 4 meal slots`() {
        val setup = NutritionSetup(NutritionGoal.MAINTAIN, NutritionActivity.LOW, NutritionDiet.REGULAR)
        val plan = NutritionMealCatalog.suggest(setup)
        assertEquals(28, plan.size)
        for (day in 0..6) for (slot in MealSlot.entries)
            assertNotNull(plan["$day:${slot.name}"])
    }

    @Test fun `suggestions are repeatable for same preferences`() {
        val setup = NutritionSetup(NutritionGoal.FAT_LOSS, NutritionActivity.HIGH, NutritionDiet.REGULAR)
        assertEquals(NutritionMealCatalog.suggest(setup), NutritionMealCatalog.suggest(setup))
    }

    @Test fun `setup exposes exactly the approved choices`() {
        assertEquals(3, NutritionGoal.entries.size)
        assertEquals(3, NutritionActivity.entries.size)
        assertEquals(2, NutritionDiet.entries.size)
    }

    @Test fun `editing setup keeps existing meals until explicit regeneration`() {
        val original = NutritionSetup(NutritionGoal.MAINTAIN, NutritionActivity.LOW, NutritionDiet.REGULAR)
        val saved = NutritionPlanData(original, NutritionMealCatalog.suggest(original), NutritionMealCatalog.fingerprint(original))
        val updated = saved.copy(setup = original.copy(goal = NutritionGoal.FAT_LOSS))
        assertEquals(saved.meals, updated.meals)
        assertEquals(saved.generatedFor, updated.generatedFor)
        assertNotEquals(NutritionMealCatalog.fingerprint(updated.setup!!), updated.generatedFor)
    }

    @Test fun `new IDs are unique and all catalog entries resolve with instructions`() {
        val ids = NutritionMealCatalog.meals.map { it.id }
        assertEquals(31, ids.size)
        assertEquals(ids.size, ids.toSet().size)
        NutritionMealCatalog.meals.forEach { meal ->
            assertEquals(meal, NutritionMealCatalog.byId(meal.id))
            if (meal.recipe == null) {
                val preparation = requireNotNull(meal.preparation)
                assertTrue(preparation.stepsVi.size >= 3)
                assertEquals(preparation.stepsVi.size, preparation.stepsEn.size)
                assertNull(meal.nutritionPerServing)
            }
        }
    }

    @Test fun `search works across languages and filters preserve dietary restrictions`() {
        val regular = NutritionSetup(NutritionGoal.MAINTAIN, NutritionActivity.LOW, NutritionDiet.REGULAR)
        fun search(slot: MealSlot, query: String, filter: NutritionMealFilter, setup: NutritionSetup = regular) =
            NutritionMealCatalog.search(slot, setup, query, filter).map { it.id }

        assertEquals(listOf("bf_pho_ga"), search(MealSlot.BREAKFAST, "pho ga", NutritionMealFilter.Protein))
        assertEquals(listOf("bf_pho_ga"), search(MealSlot.BREAKFAST, "chicken pho", NutritionMealFilter.All))
        assertTrue(search(MealSlot.BREAKFAST, "pho ga", NutritionMealFilter.Vegetarian).isEmpty())
        assertEquals(listOf("sn_sua_chua_granola"), search(MealSlot.SNACK, "granola", NutritionMealFilter.Light))
        assertEquals(listOf("bf_pancake_yen_mach"), search(MealSlot.BREAKFAST, "pancake", NutritionMealFilter.Vegetarian))
        val vegetarian = regular.copy(diet = NutritionDiet.VEGETARIAN)
        assertTrue(search(MealSlot.LUNCH, "bò", NutritionMealFilter.Protein, vegetarian).isEmpty())
        assertTrue(search(MealSlot.SNACK, "xxxxxxxx", NutritionMealFilter.All).isEmpty())
    }
}
