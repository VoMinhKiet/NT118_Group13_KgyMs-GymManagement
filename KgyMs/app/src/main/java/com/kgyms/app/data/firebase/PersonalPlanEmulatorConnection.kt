package com.kgyms.app.data.firebase

import android.content.Context
import android.util.Base64
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.kgyms.app.data.model.PersonalPlanOffline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.UUID

/** Capability for the separate demo APK only. No default FirebaseApp, production options or Storage. */
internal class PersonalPlanEmulatorConnection private constructor(val app: FirebaseApp) : PersonalPlanConnection {
    val auth: FirebaseAuth = FirebaseAuth.getInstance(app).apply { useEmulator(HOST, AUTH_PORT) }
    override val database: FirebaseDatabase = FirebaseDatabase.getInstance(app).apply { useEmulator(HOST, DATABASE_PORT) }
    override val available = MutableStateFlow(false)
    override val target = com.kgyms.app.data.model.PersonalPlanSyncTarget.Emulator
    override fun readEndpoint(uid: String) = "http://$HOST:$DATABASE_PORT/memberWorkoutPlans/$uid.json?ns=$PROJECT-default-rtdb"
    override fun record(operation: String, uid: String) { synchronized(audit) { audit += "$operation http://$HOST:$DATABASE_PORT/memberWorkoutPlans/$uid" } }
    val audit = mutableListOf<String>()
    private var closed = false
    private var keepAlive: DatabaseReference? = null
    private val connected = database.getReference(".info/connected")
    private val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) { available.value = !closed && snapshot.getValue(Boolean::class.java) == true }
        override fun onCancelled(error: DatabaseError) { available.value = false }
    }
    init {
        check(app.options.projectId == PROJECT && auth.app.name == app.name && database.app.name == app.name)
        check(database.getReference("memberWorkoutPlans").toString().startsWith("http://$HOST:$DATABASE_PORT/"))
        connected.addValueEventListener(listener)
    }
    suspend fun signIn(label: String, namespace: String = "manual"): String {
        check(label in listOf("A", "B")); check(!closed); require(namespace.matches(Regex("[A-Za-z0-9-]{1,64}")))
        val email = "personal-plan-$namespace-${label.lowercase()}@emulator.invalid"
        val password = "OnlyDemoPersonalPlan!2026"
        audit += "AUTH $PROJECT http://$HOST:$AUTH_PORT account=$label"
        try { auth.signInWithEmailAndPassword(email, password).await() }
        catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            try { auth.createUserWithEmailAndPassword(email, password).await() }
            catch (createError: Exception) {
                if (createError is kotlinx.coroutines.CancellationException) throw createError
                auth.signInWithEmailAndPassword(email, password).await()
            }
        }
        val uid = checkNotNull(auth.currentUser?.uid)
        keepAlive?.keepSynced(false)
        // .info listeners alone do not keep an RTDB connection alive. Keep only this demo owner's
        // small state node subscribed; cached listener events are never used as sync acknowledgements.
        keepAlive = database.getReference("memberWorkoutPlans/$uid/state").also { it.keepSynced(true) }
        synchronized(audit) { audit += "LISTEN http://$HOST:$DATABASE_PORT/memberWorkoutPlans/$uid/state" }
        return uid
    }
    override fun guard(uid: String) {
        check(!closed && app.options.projectId == PROJECT && auth.app.name == database.app.name)
        check(auth.currentUser?.uid == uid) { "Owner changed" }
        if (!available.value) throw PersonalPlanOffline()
    }
    override suspend fun token(uid: String): String {
        guard(uid)
        val token = withTimeoutOrNull(4_000) { auth.currentUser!!.getIdToken(false).await().token } ?: throw PersonalPlanOffline()
        val claims = JSONObject(String(Base64.decode(token.split('.')[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8))
        check(claims.getString("aud") == PROJECT && claims.getString("iss") == "https://securetoken.google.com/$PROJECT")
        return token
    }
    fun setOnline(online: Boolean) {
        check(!closed)
        if (online) database.goOnline() else { available.value = false; database.goOffline() }
    }
    fun close() {
        if (closed) return
        closed = true; available.value = false
        connected.removeEventListener(listener)
        keepAlive?.keepSynced(false)
        database.goOffline(); auth.signOut()
        // Keep the named app until process exit: no global purge, deletion of data or shared-app side effects.
    }
    companion object {
        const val PROJECT = "demo-kgyms-personal-plans"
        const val HOST = "10.0.2.2"
        const val AUTH_PORT = 9099
        const val DATABASE_PORT = 9000
        const val APPLICATION_ID = "com.kgyms.app.personalplanemu"
        fun create(context: Context): PersonalPlanEmulatorConnection {
            check(context.packageName == APPLICATION_ID) { "Emulator connections are forbidden in the ordinary app" }
            check(FirebaseApp.getApps(context).all { it.name != FirebaseApp.DEFAULT_APP_NAME && it.options.projectId == PROJECT }) { "Non-demo FirebaseApp detected; refusing network tests" }
            val options = FirebaseOptions.Builder().setProjectId(PROJECT).setApplicationId("1:1234567890:android:personalplanemulator")
                .setApiKey("demo-personal-plan-emulator-key").setDatabaseUrl("https://$PROJECT-default-rtdb.firebaseio.com").build()
            val app = FirebaseApp.initializeApp(context, options, "personal-plan-demo-${UUID.randomUUID()}")
            return PersonalPlanEmulatorConnection(app)
        }
    }
}
