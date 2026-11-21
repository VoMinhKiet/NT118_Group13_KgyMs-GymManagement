package com.kgyms.app.data.firebase

import android.content.Context
import android.util.Base64
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.kgyms.app.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** Uses the existing app/auth/database. Never signs in/out, changes persistence, or takes DB offline. */
internal class PersonalPlanProductionConnection private constructor(
    private val auth: FirebaseAuth, override val database: FirebaseDatabase, private val uid: String,
    override val target: PersonalPlanSyncTarget = PersonalPlanSyncTarget.Firebase,
    observePlanState: Boolean = true,
) : PersonalPlanConnection {
    private val project = if (target == PersonalPlanSyncTarget.Firebase) PersonalPlanProductionPolicy.PROJECT else PersonalPlanEmulatorConnection.PROJECT
    private val rootUrl = database.reference.toString().trimEnd('/')
    override val available = MutableStateFlow(false)
    val signals = MutableStateFlow(PersonalPlanSyncSignal())
    @Volatile private var closed = false
    @Volatile private var started = false
    private var revision: Long? = null
    private var epoch = 0L
    private var stateCancelled = false
    private val info = database.getReference(".info/connected")
    private val state = if (observePlanState) database.getReference("memberWorkoutPlans/$uid/state") else null
    private fun publish(connected: Boolean = available.value) {
        if (connected && !available.value) epoch++
        available.value = connected
        signals.value = PersonalPlanSyncSignal(connected, revision, epoch)
    }
    private val infoListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            publish(started && !closed && auth.currentUser?.uid == uid && snapshot.getValue(Boolean::class.java) == true)
        }
        override fun onCancelled(error: DatabaseError) { publish(false) }
    }
    private val stateListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            // Cached listener data only wakes sync; only a server read/write can confirm it.
            revision = if (!snapshot.exists()) 0 else runCatching { snapshot.child("revision").getValue(Long::class.java) }.getOrNull()
            publish()
        }
        override fun onCancelled(error: DatabaseError) { stateCancelled = true }
    }
    private val authListener = FirebaseAuth.AuthStateListener { if (it.currentUser?.uid != uid) stop() }
    fun start() {
        check(!closed)
        if (started) return
        if (auth.currentUser?.uid != uid) return
        started = true
        auth.addAuthStateListener(authListener)
        info.addValueEventListener(infoListener)
        stateCancelled = false
        state?.addValueEventListener(stateListener)
    }
    fun stop() {
        started = false; publish(false)
        info.removeEventListener(infoListener)
        state?.removeEventListener(stateListener)
        auth.removeAuthStateListener(authListener)
    }
    fun close() { closed = true; stop() }
    override fun retry() {
        if (started && !closed && stateCancelled) {
            stateCancelled = false
            state?.addValueEventListener(stateListener)
        }
    }
    override fun guard(uid: String) {
        check(!closed && uid == this.uid && auth.currentUser?.uid == uid) { "Personal Plan owner changed" }
        check(auth.app.options.projectId == project)
        check(database.app.name == auth.app.name && database.reference.toString().trimEnd('/') == rootUrl)
        if (!started || !available.value) throw PersonalPlanOffline()
    }
    override suspend fun token(uid: String): String {
        guard(uid)
        val user = checkNotNull(auth.currentUser)
        val token = withTimeoutOrNull(10_000) { user.getIdToken(false).await().token } ?: throw PersonalPlanOffline()
        guard(uid)
        val claims = JSONObject(String(Base64.decode(token.split('.')[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8))
        if (target == PersonalPlanSyncTarget.Firebase) PersonalPlanProductionPolicy.requireIdentity(claims.getString("aud"), claims.getString("iss"), claims.getString("sub"), uid)
        else check(claims.getString("aud") == project && claims.getString("iss") == "https://securetoken.google.com/$project" && claims.getString("sub") == uid)
        return token
    }
    override fun readEndpoint(uid: String): String {
        require(uid == this.uid)
        return if (target == PersonalPlanSyncTarget.Firebase) PersonalPlanProductionPolicy.endpoint(uid)
        else "http://${PersonalPlanEmulatorConnection.HOST}:${PersonalPlanEmulatorConnection.DATABASE_PORT}/memberWorkoutPlans/$uid.json?ns=$project-default-rtdb"
    }
    companion object {
        /** Exercise the exact lifecycle/token/UID adapter with a capability already isolated by 2B.
         * This cannot create an app, change SDK endpoints, or open a production connection. */
        fun observeIsolatedEmulator(
            context: Context, demo: PersonalPlanEmulatorConnection, uid: String, observePlanState: Boolean = true,
        ): PersonalPlanProductionConnection {
            check(context.packageName == PersonalPlanEmulatorConnection.APPLICATION_ID)
            check(demo.app.options.projectId == PersonalPlanEmulatorConnection.PROJECT && demo.auth.currentUser?.uid == uid)
            val root = java.net.URI.create(demo.database.reference.toString())
            check(root.scheme == "http" && root.host == PersonalPlanEmulatorConnection.HOST && root.port == PersonalPlanEmulatorConnection.DATABASE_PORT && root.path in listOf("", "/"))
            return PersonalPlanProductionConnection(demo.auth, demo.database, uid, PersonalPlanSyncTarget.Emulator, observePlanState)
        }
        fun open(context: Context, uid: String, observePlanState: Boolean = true): PersonalPlanProductionConnection? {
            // Also protects all old preview/UI tests and the separate demo APK. No auto-init here.
            if (context.packageName != "com.kgyms.app") return null
            val app = FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME } ?: return null
            if (app.options.projectId != PersonalPlanProductionPolicy.PROJECT) return null
            val auth = FirebaseAuth.getInstance(app)
            if (auth.currentUser?.uid != uid) return null
            PersonalPlanProductionPolicy.endpoint(uid)
            val db = FirebaseDatabase.getInstance(app, PersonalPlanProductionPolicy.DATABASE)
            check(db.reference.toString().trimEnd('/') == PersonalPlanProductionPolicy.DATABASE) { "Unexpected Personal Plan database endpoint" }
            return PersonalPlanProductionConnection(auth, db, uid, observePlanState = observePlanState)
        }
    }
}
