package com.kgyms.app.data.firebase

import com.kgyms.app.data.model.*

/** No Firebase initialization. Decode maps defensively without regenerating or resealing a plan. */
internal object PersonalPlanRemoteCodec {
    private fun Any?.obj(): Map<String, Any?> {
        require(this is Map<*, *> && keys.all { it is String })
        @Suppress("UNCHECKED_CAST") return this as Map<String, Any?>
    }
    private fun Map<String, Any?>.keys(required: Set<String>, optional: Set<String> = emptySet()) {
        require(keys.containsAll(required) && keys.all { it in required || it in optional })
    }
    private fun Any?.string() = (this as? String)?.also { require(it.isNotBlank()) } ?: error("Expected a string")
    private fun Any?.number(): Long {
        require(this is Number && toDouble().isFinite() && toDouble() == toLong().toDouble())
        return toLong()
    }
    private fun Any?.items(): List<Any?> = when (this) {
        is List<*> -> this.also { require(it.none { value -> value == null }) }
        is Map<*, *> -> (0 until size).map { index -> require(containsKey(index.toString())); get(index.toString()) }.also { require(it.none { value -> value == null }) }
        else -> error("Expected a dense list")
    }
    private fun Any?.ids() = items().map { it.string() }.also { require(it.isNotEmpty() && it.distinct().size == it.size && it.size <= 64) }
    fun encode(plan: PersonalPlan): Map<String, Any> = buildMap {
        put("id", plan.id); put("uid", plan.uid); put("schemaVersion", plan.schemaVersion); put("generatorVersion", plan.generatorVersion)
        put("level", plan.config.level.name); put("sessionsPerWeek", plan.config.sessionsPerWeek); put("startDate", plan.config.startDate)
        put("zoneId", plan.config.zoneId); put("weekdays", plan.config.weekdays); put("repeated", plan.repeated)
        plan.revisionOf?.let { put("revisionOf", it) }
        put("sessions", plan.sessions.map { session -> buildMap {
            put("id", session.id); put("date", session.date); put("templateId", session.templateId); put("exerciseIds", session.exerciseIds)
            session.suggestedExerciseIds?.let { put("suggestedExerciseIds", it) }
        } })
    }
    fun decodePlan(value: Any?, uid: String, id: String): PersonalPlan {
        val data = value.obj()
        data.keys(setOf("id","uid","schemaVersion","generatorVersion","level","sessionsPerWeek","startDate","zoneId","weekdays","repeated","sessions"), setOf("revisionOf"))
        require(data["id"] == id && data["uid"] == uid && data["schemaVersion"].number() == 1L && data["generatorVersion"].number() == 1L)
        val count = data["sessionsPerWeek"].number().also { require(it in 1..7) }.toInt()
        val config = PersonalPlanConfig(WorkoutLevel.valueOf(data["level"].string()), count,
            data["startDate"].string(), data["zoneId"].string(), data["weekdays"].items().map { it.number().also { day -> require(day in 0..6) }.toInt() })
        val sessions = data["sessions"].items().map { raw ->
            val s = raw.obj(); s.keys(setOf("id","date","templateId","exerciseIds"), setOf("suggestedExerciseIds"))
            PersonalPlanSession(s["id"].string(), s["date"].string(), s["templateId"].string(), s["exerciseIds"].ids(), s["suggestedExerciseIds"]?.ids())
        }
        return PersonalPlan(id, uid, config, sessions, data["repeated"] as? Boolean ?: error("Invalid repeated flag"),
            revisionOf = data["revisionOf"]?.string()).also { validate(it) }
    }
    fun validate(plan: PersonalPlan) {
        require(plan.id.matches(Regex("[A-Za-z0-9_-]{1,128}")) && plan.uid.isNotBlank() && plan.uid.length <= 128)
        require(plan.schemaVersion == 1 && plan.generatorVersion == 1)
        val c = plan.config
        require(c.sessionsPerWeek in 1..7 && c.weekdays.size == c.sessionsPerWeek && c.weekdays.distinct().size == c.weekdays.size && c.weekdays.all { it in 0..6 })
        PlanDates.millis(c.startDate, c.zoneId)
        require(plan.sessions.size == c.sessionsPerWeek * 4 && plan.sessions.map { it.id }.distinct().size == plan.sessions.size && plan.sessions.map { it.date }.distinct().size == plan.sessions.size)
        val end = PlanDates.add(c.startDate, 28, c.zoneId)
        plan.sessions.forEach { s ->
            PlanDates.millis(s.date, c.zoneId)
            require(s.id.isNotBlank() && s.id.length <= 256 && s.templateId.isNotBlank() && s.templateId.length <= 256 && s.date >= c.startDate && s.date < end)
            listOfNotNull(s.exerciseIds, s.suggestedExerciseIds).forEach { ids -> require(ids.isNotEmpty() && ids.size <= 64 && ids.distinct().size == ids.size && ids.all { it.isNotBlank() && it.length <= 256 }) }
        }
        (0..3).forEach { week -> require(plan.sessions.count { it.date in PlanDates.weekDates(plan, week) } == c.sessionsPerWeek) }
        require(plan.revisionOf != plan.id)
    }
    fun decode(value: Any?, uid: String): RemotePersonalPlanArchive {
        if (value == null) return RemotePersonalPlanArchive()
        val root = value.obj(); root.keys(setOf("remoteSchemaVersion","state","plans","versionOrder"))
        require(root["remoteSchemaVersion"].number() == 1L)
        val state = root["state"].obj(); state.keys(setOf("revision","nextOrdinal"), setOf("activeId"))
        val revision = state["revision"].number(); val next = state["nextOrdinal"].number()
        require(revision in 1..1_000_000_000 && next in 2..1_000_000_000)
        val records = root["plans"].obj().map { (id, raw) ->
            val entry = raw.obj(); entry.keys(setOf("ordinal","uploadedAt","payload"))
            val ordinal = entry["ordinal"].number(); val timestamp = entry["uploadedAt"].number()
            require(ordinal in 1 until next && timestamp >= 0)
            RemotePersonalPlan(decodePlan(entry["payload"], uid, id), ordinal.toInt(), timestamp)
        }.sortedBy { it.ordinal }
        require(records.size.toLong() == next - 1 && records.mapIndexed { index, record -> record.ordinal == index + 1 }.all { it })
        val order = root["versionOrder"].obj()
        require(order.size == records.size && records.all { order["v${it.ordinal}"] == it.plan.id })
        val active = state["activeId"]?.string()
        require(active == null || records.any { it.plan.id == active })
        records.forEach { r -> r.plan.revisionOf?.let { parent -> require(records.any { it.plan.id == parent && it.ordinal < r.ordinal }) } }
        return RemotePersonalPlanArchive(revision, next.toInt(), active, records)
    }
}
