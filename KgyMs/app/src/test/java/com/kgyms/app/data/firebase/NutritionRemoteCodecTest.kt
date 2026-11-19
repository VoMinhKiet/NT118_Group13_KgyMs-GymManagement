package com.kgyms.app.data.firebase

import com.kgyms.app.data.model.*
import org.junit.Assert.*
import org.junit.Test

class NutritionRemoteCodecTest {
    private val setup = NutritionSetup(NutritionGoal.MAINTAIN, NutritionActivity.LOW, NutritionDiet.REGULAR)
    private val plan = NutritionPlanData(setup, mapOf(
        "0:BREAKFAST" to "bf_oats",
        "6:DINNER" to "di_ca_hap_gung",
        "6:SNACK" to "sn_sua_chua_granola",
    ), NutritionMealCatalog.fingerprint(setup))

    private val writeId = "c".repeat(32)
    private val encoded = NutritionRemoteCodec.encode(NutritionPush(2, writeId, plan))
    private val resolved = encoded + ("updatedAt" to 1000L)

    @Test fun roundTripStoresOnlySetupAndWeekdayMealIds() {
        assertEquals(mapOf(".sv" to "timestamp"), encoded["updatedAt"])
        assertEquals(plan.meals, encoded["meals"])
        assertEquals(NutritionRemoteRecord(2, writeId, 1000L, plan), NutritionRemoteCodec.decode(resolved))
        assertEquals(setOf("schemaVersion", "revision", "writeId", "updatedAt", "setup", "generatedFor", "meals"), encoded.keys)
    }

    @Test fun emptyMealSelectionOmitsTheOptionalNode() {
        val empty = plan.copy(meals = emptyMap(), generatedFor = "")
        val fields = NutritionRemoteCodec.encode(NutritionPush(1, writeId, empty))
        assertFalse("meals" in fields)
        assertEquals(empty, NutritionRemoteCodec.decode(fields + ("updatedAt" to 1L)).plan)
    }

    @Test fun malformedUnknownAndFuturePayloadsAreRejected() {
        for (invalid in listOf(null, true, emptyMap<String, Any>(), resolved - "revision",
            resolved + ("schemaVersion" to 2), resolved + ("revision" to 1.5),
            resolved + ("updatedAt" to -1), resolved + ("writeId" to "x"),
            resolved + ("unknown" to 1), resolved + ("generatedFor" to "x".repeat(1000)),
            resolved + ("setup" to mapOf("goal" to "MAINTAIN", "activity" to "LOW", "diet" to "UNKNOWN")),
            resolved + ("meals" to mapOf("7:BREAKFAST" to "bf_oats")),
            resolved + ("meals" to mapOf("0:BREAKFAST" to "sn_fruit")),
            resolved + ("meals" to mapOf("0:BREAKFAST" to "missing")),
            resolved + ("meals" to listOf("bf_oats")),
        )) assertThrows(NutritionUnreadable::class.java) { NutritionRemoteCodec.decode(invalid) }
    }
}
