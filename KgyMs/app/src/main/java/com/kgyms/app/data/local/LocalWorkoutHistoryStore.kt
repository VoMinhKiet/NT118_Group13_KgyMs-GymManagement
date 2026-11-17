package com.kgyms.app.data.local

import android.content.Context
import com.kgyms.app.data.firebase.WorkoutHistoryCodec
import com.kgyms.app.data.model.*
import com.kgyms.app.data.repository.WorkoutHistoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** New per-UID file only. No migration, deletion, or change to Personal Plan / old rehearsal data. */
internal class LocalWorkoutHistoryStore(context: Context, private val uid: String) : WorkoutHistoryStore {
    private val prefs = context.applicationContext.getSharedPreferences("kgyms_workout_history_${memberAccountKey(uid)}",Context.MODE_PRIVATE)
    override suspend fun read() = withContext(Dispatchers.IO) { synchronized(lock) { decode() } }
    override suspend fun update(change: (WorkoutHistoryArchive) -> WorkoutHistoryArchive) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val old = decode(); val next = change(old)
            require(next.attempts.all { it.uid == uid } && old.attempts.all { previous -> next.attempts.any { it == previous } })
            val json = JSONObject().put("schemaVersion",1).put("uid",uid)
                .put("attempts",JSONArray(next.attempts.map { JSONObject(WorkoutHistoryCodec.encode(it)) }))
                .put("acknowledged",JSONArray(next.acknowledged.toList())).put("remoteKnown",next.remoteKnown)
                .put("confirmations",JSONArray(next.confirmations.map { c -> JSONObject().put("schemaVersion",1).put("uid",c.uid)
                    .put("attemptId",c.attemptId).put("confirmedBy",c.confirmedBy).put("confirmedAt",c.confirmedAt) }))
                .put("finishedRuns",JSONObject(next.finishedRuns))
                .put("drafts",JSONObject().apply { next.drafts.forEach { (key,d) ->
                    put(key,JSONObject().put("attemptId",d.attemptId).put("startedAt",d.startedAt).put("performed",JSONArray(d.performed.toList()))
                        .put("logicalSessionId",d.context.logicalSessionId).put("planVersionId",d.context.planVersionId)
                        .put("workoutId",d.context.workoutId).put("titleVi",d.context.titleVi).put("titleEn",d.context.titleEn)
                        .put("exerciseIds",JSONArray(d.context.exerciseIds)))
                } })
            check(prefs.edit().putString("archive_v1",json.toString()).commit()) { "Workout history could not be saved on this device" }
            next
        }
    }
    private fun decode(): WorkoutHistoryArchive {
        val text = prefs.getString("archive_v1",null) ?: return WorkoutHistoryArchive()
        val j = JSONObject(text); require(j.getInt("schemaVersion") == 1 && j.getString("uid") == uid)
        val attempts = j.getJSONArray("attempts").values().map { raw -> val m = raw as Map<*, *>; WorkoutHistoryCodec.decode(raw,uid,m["id"] as String) }
        require(attempts.distinctBy { it.id }.size == attempts.size)
        val confirmations = j.getJSONArray("confirmations").values().map { raw -> val m=raw as Map<*,*>; WorkoutHistoryCodec.confirmation(raw,uid,m["attemptId"] as String) }
        val drafts = j.getJSONObject("drafts").let { values -> values.keys().asSequence().associateWith { key ->
            val d=values.getJSONObject(key)
            WorkoutDraft(WorkoutRunContext(key,d.getString("logicalSessionId"),d.getString("planVersionId"),d.getString("workoutId"),
                d.getString("titleVi"),d.getString("titleEn"),d.getJSONArray("exerciseIds").values().map { it as String }),
                d.getString("attemptId"),d.getLong("startedAt"),d.getJSONArray("performed").values().map { it as String }.toSet())
        } }
        val finished = j.getJSONObject("finishedRuns").let { map -> map.keys().asSequence().associateWith { map.getString(it) } }
        val ack = j.getJSONArray("acknowledged").values().map { it as String }.toSet()
        require(ack.all { id -> attempts.any { it.id == id } } && confirmations.all { c -> c.attemptId in ack })
        return WorkoutHistoryArchive(attempts,ack,confirmations,j.getBoolean("remoteKnown"),drafts,finished)
    }
    private fun JSONArray.values() = (0 until length()).map { value(get(it)) }
    private fun value(raw: Any?): Any? = when (raw) {
        null, JSONObject.NULL -> null
        is JSONObject -> raw.keys().asSequence().associateWith { value(raw.get(it)) }
        is JSONArray -> raw.values()
        else -> raw
    }
    private companion object { val lock = Any() }
}
