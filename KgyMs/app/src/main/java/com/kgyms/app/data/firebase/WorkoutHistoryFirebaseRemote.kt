package com.kgyms.app.data.firebase

import android.content.Context
import android.net.Uri
import com.google.firebase.database.*
import com.kgyms.app.data.model.*
import com.kgyms.app.data.repository.WorkoutHistoryRemote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Only the two M2C owner paths. No confirmation writer or legacy-session fallback exists. */
internal class WorkoutHistoryFirebaseRemote private constructor(private val connection: PersonalPlanConnection,
    override val uid: String, private val startOwner: () -> Unit, private val stopOwner: () -> Unit,
    private val closeOwner: () -> Unit,
) : WorkoutHistoryRemote {
    override val available = connection.available
    override val changes = MutableStateFlow(0L)
    private var listening = false
    @Volatile private var closed = false
    private val refs = listOf("memberWorkoutAttempts", "memberWorkoutConfirmations").map { connection.database.getReference("$it/$uid") }
    private val listeners = refs.map { object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) { changes.value++ } // wake only; never a server ACK
        override fun onCancelled(error: DatabaseError) { changes.value++ }
    } }
    override fun start() {
        check(!closed); startOwner()
        if (!listening) { listening = true; refs.forEachIndexed { i, ref -> ref.addValueEventListener(listeners[i]) } }
    }
    override fun stop() {
        if (listening) refs.forEachIndexed { i, ref -> ref.removeEventListener(listeners[i]) }
        listening = false; stopOwner()
    }
    override fun close() { closed = true; stop(); closeOwner() }
    private fun guard() { check(!closed); connection.guard(uid) }
    private fun endpoint(node: String, id: String? = null): String {
        require(node in listOf("memberWorkoutAttempts","memberWorkoutConfirmations"))
        val path = "$node/$uid" + (id?.let { "/$it" } ?: "") + ".json"
        return if (connection.target == PersonalPlanSyncTarget.Firebase) "${PersonalPlanProductionPolicy.DATABASE}/$path"
        else "http://${PersonalPlanEmulatorConnection.HOST}:${PersonalPlanEmulatorConnection.DATABASE_PORT}/$path?ns=${PersonalPlanEmulatorConnection.PROJECT}-default-rtdb"
    }
    private suspend fun request(node: String, id: String? = null, body: Map<String,Any>? = null): Any? {
        require(uid.matches(Regex("[A-Za-z0-9_-]{1,128}")) && (id == null || id.matches(Regex("[A-Za-z0-9_-]{1,128}"))))
        guard()
        val token = connection.token(uid)
        return withContext(Dispatchers.IO) {
            guard()
            val base = endpoint(node,id)
            val url = URL(base + (if ('?' in base) "&" else "?") + "auth=${Uri.encode(token)}")
            val call = url.openConnection() as HttpURLConnection
            call.instanceFollowRedirects = false; call.connectTimeout = 8_000; call.readTimeout = 10_000
            try {
                if (body != null) {
                    require(node == "memberWorkoutAttempts" && id != null)
                    call.requestMethod = "PUT"; call.doOutput = true
                    call.setRequestProperty("Content-Type","application/json; charset=UTF-8")
                    call.outputStream.use { it.write(JSONObject(body).toString().toByteArray(Charsets.UTF_8)) }
                }
                val code = call.responseCode
                if (code == 401 || code == 403) throw WorkoutHistoryDenied()
                if (code == 408 || code == 429 || code in 500..599) throw WorkoutHistoryOffline()
                check(code == 200) { "Workout history request failed: $code" }
                val text = call.inputStream.bufferedReader().use { it.readText() }
                guard()
                if (text.trim() == "null") null else json(JSONObject(text))
            } catch (_: IOException) { throw WorkoutHistoryOffline() }
            finally { call.disconnect() }
        }
    }
    override suspend fun fetch(): RemoteWorkoutHistory {
        // Confirmation creation requires an already-existing immutable attempt. Reading these first
        // prevents a newly confirmed attempt racing the two requests and appearing as an orphan.
        val confirmationsRaw = request("memberWorkoutConfirmations") as? Map<*, *> ?: emptyMap<Any,Any>()
        val confirmations = confirmationsRaw.map { (id,value) -> WorkoutHistoryCodec.confirmation(value,uid,id as String) }
        val attemptsRaw = request("memberWorkoutAttempts") as? Map<*, *> ?: emptyMap<Any,Any>()
        val attempts = attemptsRaw.map { (id,value) -> WorkoutHistoryCodec.decode(value,uid,id as String,remote=true) }
        require(confirmations.all { c -> attempts.any { it.id == c.attemptId } })
        guard(); return RemoteWorkoutHistory(attempts,confirmations)
    }
    override suspend fun append(attempt: WorkoutAttempt) {
        require(attempt.uid == uid)
        WorkoutHistoryCodec.decode(WorkoutHistoryCodec.encode(attempt),uid,attempt.id)
        val result = request("memberWorkoutAttempts",attempt.id,WorkoutHistoryCodec.encode(attempt)+("receivedAt" to ServerValue.TIMESTAMP))
        check(WorkoutHistoryCodec.decode(result,uid,attempt.id,remote=true) == attempt)
    }
    private fun json(value: Any?): Any? = when(value) {
        null, JSONObject.NULL -> null
        is JSONObject -> value.keys().asSequence().associateWith { json(value.get(it)) }
        is JSONArray -> (0 until value.length()).map { json(value.get(it)) }
        else -> value
    }
    companion object {
        fun production(context: Context, uid: String): WorkoutHistoryFirebaseRemote? {
            val owner = PersonalPlanProductionConnection.open(context,uid) ?: return null
            return WorkoutHistoryFirebaseRemote(owner,uid,owner::start,owner::stop,owner::close)
        }
        fun emulator(context: Context, demo: PersonalPlanEmulatorConnection, uid: String): WorkoutHistoryFirebaseRemote {
            val owner = PersonalPlanProductionConnection.observeIsolatedEmulator(context,demo,uid)
            return WorkoutHistoryFirebaseRemote(owner,uid,owner::start,owner::stop,owner::close)
        }
    }
}
