package com.kgyms.app.data.local

import android.content.Context
import com.kgyms.app.data.firebase.NutritionRemoteCodec
import com.kgyms.app.data.model.*
import com.kgyms.app.data.repository.NutritionJournalStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest

internal fun JSONObject.nutritionFields(): Map<String, Any?> = keys().asSequence().associateWith { key ->
    when (val value = get(key)) {
        is JSONObject -> value.nutritionFields()
        JSONObject.NULL -> null
        else -> value
    }
}

internal class NutritionJournalPreferences(context: Context, uid: String) : NutritionJournalStore {
    private val legacy = NutritionLocalStore(context, uid)
    private val prefs = context.applicationContext.getSharedPreferences(
        "kgyms_nutrition_sync_" + MessageDigest.getInstance("SHA-256")
            .digest(uid.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) },
        Context.MODE_PRIVATE,
    )

    override suspend fun read(): NutritionJournal = withContext(Dispatchers.IO) {
        val text = prefs.getString(KEY, null)
        if (text == null) {
            val imported = legacy.readForMigration()
            val initial = NutritionJournal(plan = imported, pending = imported != null)
            if (imported != null) commit(initial)
            return@withContext initial
        }
        try {
            val obj = JSONObject(text)
            require(obj.getInt("formatVersion") == 1)
            val base = obj.optJSONObject("base")?.let { NutritionRemoteCodec.decode(it.nutritionFields()) }
            val conflict = obj.optJSONObject("conflict")?.let { NutritionRemoteCodec.decode(it.nutritionFields()) }
            val plan = obj.optJSONObject("plan")?.let { NutritionRemoteCodec.decodePlan(it.nutritionFields()) }
            val sent = obj.optJSONObject("sent")?.let {
                val record = NutritionRemoteCodec.decode(it.nutritionFields() + ("updatedAt" to 1))
                NutritionPush(record.revision, record.writeId, record.plan)
            }
            require(listOf("base", "conflict", "plan", "sent").all { !obj.has(it) || obj.optJSONObject(it) != null })
            val pending = obj.getBoolean("pending")
            require(if (pending) plan != null else conflict == null && sent == null && plan == base?.plan)
            NutritionJournal(plan, base, pending, conflict, sent)
        } catch (_: Exception) {
            throw NutritionUnreadable()
        }
    }

    override suspend fun save(value: NutritionJournal) = withContext(Dispatchers.IO) { commit(value) }

    private fun commit(value: NutritionJournal) {
        val obj = JSONObject().put("formatVersion", 1).put("pending", value.pending)
        value.plan?.let { obj.put("plan", JSONObject(NutritionRemoteCodec.encodePlan(it))) }
        value.base?.let { obj.put("base", JSONObject(recordFields(it))) }
        value.conflict?.let { obj.put("conflict", JSONObject(recordFields(it))) }
        value.sent?.let { obj.put("sent", JSONObject(NutritionRemoteCodec.encode(it) - "updatedAt")) }
        check(prefs.edit().putString(KEY, obj.toString()).commit()) { "Could not save Nutrition locally" }
    }

    private fun recordFields(record: NutritionRemoteRecord): Map<String, Any> =
        NutritionRemoteCodec.encode(NutritionPush(record.revision, record.writeId, record.plan)) +
            ("updatedAt" to record.updatedAt)

    private companion object { const val KEY = "journal_v1" }
}
