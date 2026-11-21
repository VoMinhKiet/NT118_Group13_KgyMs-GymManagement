package com.kgyms.app.data.firebase

import android.content.Context
import android.net.Uri
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.kgyms.app.data.local.nutritionFields
import com.kgyms.app.data.model.*
import com.kgyms.app.data.repository.NutritionRemote
import com.kgyms.app.data.repository.NutritionRemoteHead
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** SDK listeners signal connectivity and changes only. An authenticated REST GET supplies server data and its ETag. */
internal class NutritionFirebaseService private constructor(
    private val auth: FirebaseAuth,
    database: FirebaseDatabase,
    override val uid: String,
    private val endpoint: String,
) : NutritionRemote {
    override val connected = MutableStateFlow(false)
    override val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val ownerRef = database.getReference("memberNutritionPlans").child(uid)
    private val info = database.getReference(".info/connected")
    private val lock = Any()
    @Volatile private var started = false
    private var ownerListening = false
    private var inFlight: HttpURLConnection? = null
    private val authListener = FirebaseAuth.AuthStateListener {
        if (!ownsSession()) {
            stop()
            changes.tryEmit(Unit)
        }
    }
    private val infoListener = listener {
        connected.value = started && ownsSession() && it?.getValue(Boolean::class.java) == true
    }
    private val ownerListener = listener { snapshot ->
        if (snapshot == null) ownerListening = false
        if (started && ownsSession()) changes.tryEmit(Unit)
    }

    fun ownsSession(): Boolean = auth.currentUser?.uid == uid

    override fun start() {
        if (started || !ownsSession()) return
        synchronized(lock) { started = true }
        auth.addAuthStateListener(authListener)
        info.addValueEventListener(infoListener)
        retryListener()
    }

    override fun retryListener() {
        if (started && ownsSession() && !ownerListening) {
            ownerListening = true
            ownerRef.addValueEventListener(ownerListener)
        }
    }

    override fun stop() {
        synchronized(lock) {
            started = false
            inFlight?.disconnect()
            inFlight = null
        }
        connected.value = false
        info.removeEventListener(infoListener)
        ownerRef.removeEventListener(ownerListener)
        ownerListening = false
        auth.removeAuthStateListener(authListener)
    }

    private fun guard() {
        if (!ownsSession()) throw NutritionOwnerChanged()
        if (!synchronized(lock) { started } || !connected.value) throw NutritionOffline()
    }

    override suspend fun fetch(): NutritionRemoteHead = request { call ->
        call.setRequestProperty("X-Firebase-ETag", "true")
        requireSuccess(call.responseCode)
        val etag = call.getHeaderField("ETag")?.takeIf { it.isNotBlank() } ?: throw NutritionUnreadable()
        val text = call.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            val content = StringBuilder()
            val buffer = CharArray(2048)
            while (true) {
                val size = reader.read(buffer)
                if (size < 0) break
                if (content.length + size > 16_384) throw NutritionUnreadable()
                content.append(buffer, 0, size)
            }
            content.toString().trim()
        }
        val record = if (text == "null") null else {
            val fields = try { JSONObject(text).nutritionFields() } catch (_: Exception) { throw NutritionUnreadable() }
            NutritionRemoteCodec.decode(fields)
        }
        NutritionRemoteHead(record, etag)
    }

    override suspend fun write(expectedEtag: String, push: NutritionPush): Boolean = request { call ->
        val body = JSONObject(NutritionRemoteCodec.encode(push)).toString().toByteArray(Charsets.UTF_8)
        call.requestMethod = "PUT"
        call.doOutput = true
        call.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        call.setRequestProperty("If-Match", expectedEtag)
        call.setFixedLengthStreamingMode(body.size)
        guard()
        call.outputStream.use { it.write(body) }
        val code = call.responseCode
        if (code == 412) false else {
            requireSuccess(code)
            true
        }
    }

    private suspend fun <T> request(block: (HttpURLConnection) -> T): T {
        guard()
        val user = auth.currentUser?.takeIf { it.uid == uid } ?: throw NutritionOwnerChanged()
        val token = try {
            withTimeoutOrNull(10_000) { user.getIdToken(false).await().token } ?: throw NutritionOffline()
        } catch (_: FirebaseNetworkException) {
            throw NutritionOffline()
        }
        return withContext(Dispatchers.IO) {
            guard()
            val divider = if ('?' in endpoint) '&' else '?'
            val call = URL(endpoint + divider + "auth=" + Uri.encode(token)).openConnection() as HttpURLConnection
            call.instanceFollowRedirects = false
            call.useCaches = false
            call.connectTimeout = 8_000
            call.readTimeout = 10_000
            try {
                synchronized(lock) {
                    guard()
                    inFlight = call
                }
                block(call).also { guard() }
            } catch (_: IOException) {
                guard()
                throw NutritionOffline()
            } finally {
                synchronized(lock) { if (inFlight === call) inFlight = null }
                call.disconnect()
            }
        }
    }

    private fun requireSuccess(code: Int) {
        when (code) {
            200 -> Unit
            401, 403 -> throw NutritionDenied()
            408, 429, in 500..599 -> throw NutritionOffline()
            else -> error("Nutrition server returned an unexpected response")
        }
    }

    companion object {
        private val uidPattern = Regex("[A-Za-z0-9_-]{1,128}")

        fun production(context: Context, uid: String): NutritionFirebaseService? {
            if (context.packageName != "com.kgyms.app" || !uidPattern.matches(uid)) return null
            val app = FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME } ?: return null
            if (app.options.projectId != PersonalPlanProductionPolicy.PROJECT) return null
            val auth = FirebaseAuth.getInstance(app)
            if (auth.currentUser?.uid != uid) return null
            val root = PersonalPlanProductionPolicy.DATABASE
            val db = FirebaseDatabase.getInstance(app, root)
            check(db.reference.toString().trimEnd('/') == root)
            return NutritionFirebaseService(auth, db, uid, "$root/memberNutritionPlans/$uid.json")
        }


        /** Allows instrumentation to exercise the same transport without creating a default FirebaseApp. */
        fun isolatedEmulator(context: Context, auth: FirebaseAuth, database: FirebaseDatabase): NutritionFirebaseService {
            val project = "demo-kgyms-nutrition"
            val root = "http://127.0.0.1:9010"
            check(context.packageName == PersonalPlanEmulatorConnection.APPLICATION_ID)
            check(auth.app.name != FirebaseApp.DEFAULT_APP_NAME && auth.app.options.projectId == project)
            check(database.app.name == auth.app.name)
            check(database.getReference("memberNutritionPlans").toString().startsWith("$root/"))
            val uid = checkNotNull(auth.currentUser?.uid)
            check(uidPattern.matches(uid))
            return NutritionFirebaseService(auth, database, uid, "$root/memberNutritionPlans/$uid.json?ns=$project-default-rtdb")
        }

        private fun listener(event: (DataSnapshot?) -> Unit) = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) = event(snapshot)
            override fun onCancelled(error: DatabaseError) = event(null)
        }
    }
}
