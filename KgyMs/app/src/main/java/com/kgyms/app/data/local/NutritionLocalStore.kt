package com.kgyms.app.data.local

import android.content.Context
import com.kgyms.app.data.model.*
import com.kgyms.app.data.firebase.NutritionRemoteCodec
import org.json.JSONObject
import java.security.MessageDigest

/** Private app storage; UID scoped. Never reads/writes Firebase or another account's record. */
internal class NutritionLocalStore(context: Context, uid: String) {
    init { require(uid.isNotBlank()) { "An authenticated account is required" } }

    private val prefs = context.applicationContext.getSharedPreferences(
        "kgyms_nutrition_" + MessageDigest.getInstance("SHA-256")
            .digest(uid.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) },
        Context.MODE_PRIVATE,
    )

    private var readFailed = false

    fun read(): NutritionPlanData {
        return try {
        val text = prefs.getString("nutrition_data_v1", null) ?: return NutritionPlanData()
        val raw = JSONObject(text)
        if (raw.optInt("schemaVersion", 0) != 1) {
            readFailed = true
            return NutritionPlanData()
        }
        val settings = raw.optJSONObject("setup")?.let {
            NutritionSetup(
                NutritionGoal.valueOf(it.getString("goal")),
                NutritionActivity.valueOf(it.getString("activity")),
                NutritionDiet.valueOf(it.getString("diet")),
            )
        }
        val meals = mutableMapOf<String, String>()
        val savedMeals = raw.optJSONObject("meals")
        if (savedMeals != null) {
            val keys = savedMeals.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val id = savedMeals.optString(key)
                if (NutritionMealCatalog.byId(id) != null) meals[key] = id
            }
        }
        NutritionPlanData(settings, meals, raw.optString("generatedFor", ""))
    } catch (_: Exception) {
        // Prevent overwriting an unreadable or future-version record.
        readFailed = true
        NutritionPlanData()
        }
    }

    /** Synchronous commit: UI only reports saved after local data has been acknowledged. */
    fun save(value: NutritionPlanData): Boolean {
        if (readFailed) return false
        val json = JSONObject().put("schemaVersion", 1)
        value.setup?.let {
            json.put("setup", JSONObject().put("goal", it.goal.name)
                .put("activity", it.activity.name).put("diet", it.diet.name))
        }
        val meals = JSONObject()
        value.meals.forEach { (key, id) -> meals.put(key, id) }
        json.put("meals", meals).put("generatedFor", value.generatedFor)
        return prefs.edit().putString("nutrition_data_v1", json.toString()).commit()
    }

    /** Read legacy data without filtering unknown selections or altering the original preferences. */
    fun readForMigration(): NutritionPlanData? {
        val text = prefs.getString("nutrition_data_v1", null) ?: return null
        try {
            val fields = JSONObject(text).nutritionFields()
            require(fields["schemaVersion"] == 1 && fields.keys.all { it in LEGACY_FIELDS })
            val plan = fields - "schemaVersion"
            val meals = plan["meals"]
            require(!plan.containsKey("meals") || meals is Map<*, *>)
            val generated = plan["generatedFor"] ?: ""
            if (!plan.containsKey("setup") && (meals == null || (meals as Map<*, *>).isEmpty()) && generated == "") return null
            return NutritionRemoteCodec.decodePlan(plan + ("generatedFor" to generated))
        } catch (_: Exception) {
            throw NutritionUnreadable()
        }
    }

    private companion object {
        val LEGACY_FIELDS = setOf("schemaVersion", "setup", "meals", "generatedFor")
    }
}
