package com.kgyms.app.data.local

import android.content.Context
import com.kgyms.app.data.model.*
import com.kgyms.app.data.repository.PersonalPlanRepository
import com.kgyms.app.data.repository.appendPersonalPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** A separate app-private file per UID. No Firebase initialization, writes, or offline sync queue. */
internal class LocalPersonalPlanRepository(context: Context, override val uid: String) : PersonalPlanRepository {
    override val storage = com.kgyms.app.data.repository.PersonalPlanStorage.Device
    init { require(uid.isNotBlank()) }
    private val preferences = context.applicationContext.getSharedPreferences("kgyms_personal_plans_" + memberAccountKey(uid), Context.MODE_PRIVATE)
    override suspend fun load(): PersonalPlanArchive = withContext(Dispatchers.IO) { synchronized(lock) { read() } }
    override suspend fun save(plan: PersonalPlan, expectedActiveId: String?): PersonalPlanArchive = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val next = appendPersonalPlan(read(), plan, uid, expectedActiveId)
            check(preferences.edit().putString("archive_v1", PersonalPlanCodec.encode(next, uid)).commit()) { "Could not save locally" }
            next
        }
    }
    private fun read() = preferences.getString("archive_v1", null)?.let { PersonalPlanCodec.decode(it, uid) } ?: PersonalPlanArchive()
    private companion object { val lock = Any() }
}

internal object PersonalPlanCodec {
    fun encode(archive: PersonalPlanArchive, uid: String): String = JSONObject().apply {
        put("schemaVersion", 1); put("uid", uid); put("activeId", archive.activeId ?: JSONObject.NULL)
        put("plans", JSONArray().apply { archive.plans.forEach { plan -> put(JSONObject().apply {
            put("id", plan.id); put("uid", plan.uid); put("schemaVersion", plan.schemaVersion); put("generatorVersion", plan.generatorVersion)
            put("level", plan.config.level.name); put("sessionsPerWeek", plan.config.sessionsPerWeek)
            put("startDate", plan.config.startDate); put("zoneId", plan.config.zoneId); put("weekdays", JSONArray(plan.config.weekdays))
            put("repeated", plan.repeated)
            put("sessions", JSONArray().apply { plan.sessions.forEach { session -> put(JSONObject().apply {
                put("id", session.id); put("date", session.date); put("templateId", session.templateId); put("exerciseIds", JSONArray(session.exerciseIds))
            }) } })
        }) } })
    }.toString()

    fun decode(json: String, uid: String): PersonalPlanArchive {
        val root = JSONObject(json)
        require(root.getInt("schemaVersion") == 1 && root.getString("uid") == uid) { "Unknown format or owner" }
        val array = root.getJSONArray("plans")
        val plans = (0 until array.length()).map { i ->
            val value = array.getJSONObject(i)
            require(value.getString("uid") == uid && value.getInt("schemaVersion") == 1 && value.getInt("generatorVersion") == 1)
            val days = value.getJSONArray("weekdays")
            val config = PersonalPlanConfig(WorkoutLevel.valueOf(value.getString("level")), value.getInt("sessionsPerWeek"),
                value.getString("startDate"), value.getString("zoneId"), (0 until days.length()).map { days.getInt(it) })
            PlanDates.millis(config.startDate, config.zoneId)
            require(config.sessionsPerWeek in 1..7 && config.weekdays.distinct().size == config.sessionsPerWeek && config.weekdays.all { it in 0..6 })
            val sessions = value.getJSONArray("sessions")
            val records = (0 until sessions.length()).map { j ->
                val session = sessions.getJSONObject(j)
                val ids = session.getJSONArray("exerciseIds")
                PersonalPlanSession(session.getString("id"), session.getString("date"), session.getString("templateId"),
                    (0 until ids.length()).map { ids.getString(it) }).also {
                    PlanDates.millis(it.date, config.zoneId)
                    require(it.id.isNotBlank() && it.templateId.isNotBlank() && it.exerciseIds.isNotEmpty() && it.exerciseIds.distinct().size == it.exerciseIds.size)
                }
            }
            require(records.size == config.sessionsPerWeek * 4 && records.map { it.id }.distinct().size == records.size && records.map { it.date }.distinct().size == records.size)
            require(value.getString("id").isNotBlank())
            require(records.all { it.exerciseIds.all(String::isNotBlank) && it.date >= config.startDate && it.date < PlanDates.add(config.startDate, 28, config.zoneId) })
            PersonalPlan(value.getString("id"), uid, config, records, value.getBoolean("repeated"))
        }
        require(plans.map { it.id }.distinct().size == plans.size)
        val active = if (root.isNull("activeId")) null else root.getString("activeId")
        require(active == null || plans.any { it.id == active })
        return PersonalPlanArchive(active, plans)
    }
}
