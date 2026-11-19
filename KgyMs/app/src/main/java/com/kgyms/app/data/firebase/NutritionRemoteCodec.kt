package com.kgyms.app.data.firebase

import com.kgyms.app.data.model.*

internal object NutritionRemoteCodec {
    private const val VERSION = 1
    private val slots = MealSlot.entries.associateBy { it.name }
    private val writeIdPattern = Regex("[0-9a-f]{32}")
    private val fingerprints = NutritionGoal.entries.flatMap { goal ->
        NutritionActivity.entries.flatMap { activity ->
            NutritionDiet.entries.map { NutritionMealCatalog.fingerprint(NutritionSetup(goal, activity, it)) }
        }
    }.toSet() + ""

    fun encode(push: NutritionPush): Map<String, Any> {
        require(push.revision > 0 && writeIdPattern.matches(push.writeId))
        return encodePlan(push.plan) + mapOf(
            "schemaVersion" to VERSION, "revision" to push.revision, "writeId" to push.writeId,
            "updatedAt" to mapOf(".sv" to "timestamp"),
        )
    }

    fun decode(raw: Any?): NutritionRemoteRecord = validate {
        val fields = map(raw, required = setOf("schemaVersion", "revision", "writeId", "updatedAt", "setup", "generatedFor"),
            optional = setOf("meals"))
        require(integer(fields["schemaVersion"], VERSION.toLong()) == VERSION.toLong())
        val plan = decodePlan(fields.filterKeys { it in setOf("setup", "generatedFor", "meals") })
        NutritionRemoteRecord(
            integer(fields["revision"], Int.MAX_VALUE.toLong()).toInt(),
            (fields["writeId"] as String).also { require(writeIdPattern.matches(it)) },
            integer(fields["updatedAt"], 9_007_199_254_740_991),
            plan,
        )
    }

    fun encodePlan(plan: NutritionPlanData): Map<String, Any> {
        val setup = requireNotNull(plan.setup)
        require(plan.generatedFor in fingerprints)
        require(plan.meals.size <= 28 && plan.meals.all { (key, value) -> validMeal(key, value) })
        val fields = mapOf(
            "setup" to mapOf("goal" to setup.goal.name, "activity" to setup.activity.name, "diet" to setup.diet.name),
            "generatedFor" to plan.generatedFor,
        )
        return if (plan.meals.isEmpty()) fields else fields + ("meals" to plan.meals.toMap())
    }

    fun decodePlan(raw: Any?): NutritionPlanData = validate {
        val fields = map(raw, setOf("setup", "generatedFor"), setOf("meals"))
        val setupFields = map(fields["setup"], setOf("goal", "activity", "diet"))
        val setup = NutritionSetup(
            NutritionGoal.valueOf(setupFields["goal"] as String),
            NutritionActivity.valueOf(setupFields["activity"] as String),
            NutritionDiet.valueOf(setupFields["diet"] as String),
        )
        val generatedFor = (fields["generatedFor"] as String).also { require(it in fingerprints) }
        val meals = if ("meals" in fields) (fields["meals"] as Map<*, *>).entries.associate { entry ->
            val key = entry.key as String
            val id = entry.value as String
            require(validMeal(key, id))
            key to id
        } else emptyMap()
        require(meals.size <= 28)
        NutritionPlanData(setup, meals, generatedFor)
    }

    private fun validMeal(key: String, id: String): Boolean {
        if (key.length < 3 || key[0] !in '0'..'6' || key[1] != ':') return false
        val slot = slots[key.substring(2)] ?: return false
        return NutritionMealCatalog.byId(id)?.slot == slot
    }

    private fun map(raw: Any?, required: Set<String>, optional: Set<String> = emptySet()): Map<String, Any?> {
        val fields = (raw as Map<*, *>).entries.associate { (key, value) -> key as String to value }
        require(fields.keys.containsAll(required) && fields.keys.all { it in required || it in optional })
        return fields
    }

    private fun integer(raw: Any?, maximum: Long): Long {
        val value = (raw as Number).toDouble()
        require(value.isFinite() && value >= 1 && value <= maximum.toDouble() && value == value.toLong().toDouble())
        return value.toLong()
    }

    private inline fun <T> validate(block: () -> T): T = try { block() } catch (_: RuntimeException) {
        throw NutritionUnreadable()
    }
}
