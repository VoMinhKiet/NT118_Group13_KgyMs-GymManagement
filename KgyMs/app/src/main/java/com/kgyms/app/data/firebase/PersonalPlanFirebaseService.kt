package com.kgyms.app.data.firebase

import android.net.Uri
import com.google.firebase.database.ServerValue
import com.kgyms.app.data.model.*
import com.kgyms.app.data.repository.PersonalPlanRemote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import com.google.firebase.database.DatabaseError
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal class PersonalPlanFirebaseService(private val connection: PersonalPlanConnection, override val uid: String) : PersonalPlanRemote {
    override val available = connection.available
    override val target = connection.target
    override fun prepareRetry() = connection.retry()
    init { require(uid.matches(Regex("[A-Za-z0-9_-]{1,128}"))) }
    private val path = "memberWorkoutPlans/$uid"
    // Server-only REST read avoids treating an SDK cached get() fallback as a confirmed remote snapshot.
    override suspend fun read(): RemotePersonalPlanArchive {
        val token = connection.token(uid)
        val raw = withContext(Dispatchers.IO) {
            connection.guard(uid)
            val endpoint = connection.readEndpoint(uid)
            check(endpoint == if (connection.target == PersonalPlanSyncTarget.Firebase) PersonalPlanProductionPolicy.endpoint(uid)
                else "http://${PersonalPlanEmulatorConnection.HOST}:${PersonalPlanEmulatorConnection.DATABASE_PORT}/$path.json?ns=${PersonalPlanEmulatorConnection.PROJECT}-default-rtdb")
            val url = URL(endpoint + (if ('?' in endpoint) "&" else "?") + "auth=${Uri.encode(token)}")
            connection.record("GET", uid)
            val request = url.openConnection() as HttpURLConnection
            request.connectTimeout = 8_000; request.readTimeout = 10_000
            request.instanceFollowRedirects = false
            try {
                val status = request.responseCode
                if (status == 401 || status == 403) throw PersonalPlanAccessDenied()
                if (status == 408 || status == 429 || status in 500..599) throw PersonalPlanOffline()
                check(status == 200) { "Personal Plan read failed: $status" }
                val text = request.inputStream.bufferedReader().use { it.readText() }
                if (text.trim() == "null") null else jsonValue(JSONObject(text))
            } catch (_: IOException) { throw PersonalPlanOffline() }
            finally { request.disconnect() }
        }
        connection.guard(uid)
        return PersonalPlanRemoteCodec.decode(raw, uid)
    }
    override suspend fun append(base: RemotePersonalPlanArchive, plan: PersonalPlan) {
        require(plan.uid == uid); PersonalPlanRemoteCodec.validate(plan)
        require(base.records.none { it.plan.id == plan.id })
        val state = state(base.revision + 1, base.nextOrdinal + 1, base.activeId)
        val updates = mutableMapOf<String, Any>(
            "plans/${plan.id}" to mapOf("ordinal" to base.nextOrdinal, "uploadedAt" to ServerValue.TIMESTAMP, "payload" to PersonalPlanRemoteCodec.encode(plan)),
            "versionOrder/v${base.nextOrdinal}" to plan.id, "state" to state)
        if (base.revision == 0L) updates["remoteSchemaVersion"] = 1
        write(updates)
    }
    override suspend fun activate(base: RemotePersonalPlanArchive, id: String) {
        require(base.records.any { it.plan.id == id })
        write(mapOf("state" to state(base.revision + 1, base.nextOrdinal, id)))
    }
    private fun state(revision: Long, next: Int, active: String?): Map<String, Any> = buildMap {
        put("revision", revision); put("nextOrdinal", next); active?.let { put("activeId", it) }
    }
    private suspend fun write(updates: Map<String, Any>) {
        connection.token(uid) // verify current owner and token audience before a business write
        connection.guard(uid)
        require(updates.keys.all { it == "state" || it == "remoteSchemaVersion" || it.startsWith("plans/") || it.startsWith("versionOrder/") })
        connection.record("UPDATE", uid)
        val confirmed = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { continuation ->
                connection.database.getReference(path).updateChildren(updates) { error, _ ->
                    if (continuation.isActive) {
                        if (error == null) continuation.resume(Unit)
                        else continuation.resumeWithException(if (error.code == DatabaseError.PERMISSION_DENIED) PersonalPlanAccessDenied() else error.toException())
                    }
                }
            }
            true
        }
        if (confirmed != true) throw PersonalPlanOffline()
        connection.guard(uid)
    }
    private fun jsonValue(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> null
        is JSONObject -> value.keys().asSequence().associateWith { jsonValue(value.get(it)) }
        is JSONArray -> (0 until value.length()).map { jsonValue(value.get(it)) }
        else -> value
    }
}
