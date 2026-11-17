package com.kgyms.app.data.firebase

import com.kgyms.app.data.model.*

/** Shared local/remote payload codec. Client payloads have no status, confirmed or trainer fields. */
internal object WorkoutHistoryCodec {
    private fun Any?.obj(): Map<String, Any?> {
        require(this is Map<*, *> && keys.all { it is String })
        @Suppress("UNCHECKED_CAST") return this as Map<String, Any?>
    }
    private fun Map<String, Any?>.shape(required: Set<String>, optional: Set<String> = emptySet()) =
        require(keys.containsAll(required) && keys.all { it in required || it in optional })
    private fun Any?.text(max: Int = 512, empty: Boolean = false) = (this as? String)?.also {
        require(it.length <= max && (empty || it.isNotBlank()))
    } ?: error("Invalid text")
    private fun Any?.number() = (this as? Number)?.let { require(it.toDouble().isFinite() && it.toDouble() == it.toLong().toDouble()); it.toLong() } ?: error("Invalid timestamp")
    private fun Any?.list(): List<Any?> = when (this) {
        is List<*> -> this
        is Map<*, *> -> (0 until size).map { require(containsKey(it.toString())); get(it.toString()) }
        else -> error("Invalid exercise list")
    }
    fun encode(a: WorkoutAttempt): Map<String, Any> = mapOf("schemaVersion" to 1, "id" to a.id, "uid" to a.uid,
        "logicalSessionId" to a.logicalSessionId, "planVersionId" to a.planVersionId, "workoutId" to a.workoutId,
        "titleVi" to a.titleVi, "titleEn" to a.titleEn, "startedAt" to a.startedAt, "finishedAt" to a.finishedAt,
        "exercises" to a.exercises.map { mapOf("id" to it.id, "performed" to it.performed) })
    fun decode(value: Any?, uid: String, id: String, remote: Boolean = false): WorkoutAttempt {
        val m = value.obj()
        m.shape(setOf("schemaVersion","id","uid","logicalSessionId","planVersionId","workoutId","titleVi","titleEn","startedAt","finishedAt","exercises"), if (remote) setOf("receivedAt") else emptySet())
        require(id.matches(Regex("[A-Za-z0-9_-]{1,128}")) && m["id"] == id && m["uid"] == uid && m["schemaVersion"].number() == 1L)
        if (remote) require(m["receivedAt"].number() > 0)
        val items = m["exercises"].list().map { item -> val e = item.obj(); e.shape(setOf("id","performed"));
            AttemptExercise(e["id"].text(256), e["performed"] as? Boolean ?: error("Invalid performed flag")) }
        require(items.size in 1..64 && items.distinctBy { it.id }.size == items.size)
        val start = m["startedAt"].number(); val end = m["finishedAt"].number(); require(start >= 0 && end >= start)
        return WorkoutAttempt(id, uid, m["logicalSessionId"].text(), m["planVersionId"].text(128, true), m["workoutId"].text(256),
            m["titleVi"].text(200), m["titleEn"].text(200), start, end, items)
    }
    fun confirmation(value: Any?, uid: String, id: String): WorkoutConfirmation {
        val m = value.obj(); m.shape(setOf("schemaVersion","uid","attemptId","confirmedBy","confirmedAt"))
        require(m["schemaVersion"].number() == 1L && m["uid"] == uid && m["attemptId"] == id)
        return WorkoutConfirmation(uid, id, m["confirmedBy"].text(128), m["confirmedAt"].number().also { require(it > 0) })
    }
}
