package com.kgyms.app.integration

import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.test.runner.AndroidJUnitRunner
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.core.RepoInfo
import com.google.firebase.database.core.utilities.Utilities

internal object MemberEmulatorFirebase {
    const val APP_ID = "com.kgyms.app.memberintegrationemu"
    const val PROJECT = "demo-kgyms-member-integration"
    const val HOST = "127.0.0.1"
    const val AUTH_PORT = 9296
    const val DB_PORT = 9226
    const val DB_URL = "http://$HOST:$DB_PORT/?ns=$PROJECT-default-rtdb"
    const val LEGACY_DB_URL = "https://kgyms-72c74-default-rtdb.asia-southeast1.firebasedatabase.app"
}

class MemberEmulatorRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle) {
        val appContext = targetContext
        check(appContext.packageName == MemberEmulatorFirebase.APP_ID)
        check(context.packageName == "${MemberEmulatorFirebase.APP_ID}.test")
        check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "A dedicated Android Emulator is required" }
        check(Settings.Global.getInt(appContext.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1)
        check(appContext.getSystemService(ConnectivityManager::class.java).activeNetwork == null) {
            "Keep the Android Emulator offline and use only the loopback adb reverse bridge"
        }
        check(appContext.resources.getIdentifier("google_app_id", "string", appContext.packageName) == 0)
        check(FirebaseApp.getApps(appContext).isEmpty()) { "Firebase was initialized before instrumentation" }

        val options = FirebaseOptions.Builder()
            .setProjectId(MemberEmulatorFirebase.PROJECT)
            .setApplicationId("1:123456789:android:0000000000000000")
            .setApiKey("demo-member-integration")
            .setDatabaseUrl(MemberEmulatorFirebase.DB_URL)
            .build()
        val app = FirebaseApp.initializeApp(appContext, options)
        FirebaseAuth.getInstance(app).apply {
            useEmulator(MemberEmulatorFirebase.HOST, MemberEmulatorFirebase.AUTH_PORT)
            signOut()
        }
        configureDatabase(app)
        super.onCreate(arguments)
    }

    private fun configureDatabase(app: FirebaseApp) {
        val database = FirebaseDatabase.getInstance(app)
        database.useEmulator(MemberEmulatorFirebase.HOST, MemberEmulatorFirebase.DB_PORT)
        check(database.reference.toString().trimEnd('/') == "http://${MemberEmulatorFirebase.HOST}:${MemberEmulatorFirebase.DB_PORT}")
        check(database === FirebaseDatabase.getInstance(app))

        // Some repositories select the regional URL explicitly. Firebase applies emulator settings
        // by mutating the RepoInfo used as its cache key. Use a separate key for the legacy alias
        // and map it to the already configured demo instance. The test APK fails closed if the
        // FirebaseDatabaseComponent changes when the SDK is upgraded.
        val componentClass = Class.forName("com.google.firebase.database.FirebaseDatabaseComponent")
        val component = checkNotNull(app.get(componentClass))
        val cacheField = componentClass.getDeclaredField("instances").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val instances = cacheField.get(component) as MutableMap<RepoInfo, FirebaseDatabase>
        val legacyKey = Utilities.parseUrl(MemberEmulatorFirebase.LEGACY_DB_URL).repoInfo
        synchronized(component) {
            check(instances.values.single() === database)
            check(instances.put(legacyKey, database) == null)
        }
        check(database === FirebaseDatabase.getInstance(app, "${MemberEmulatorFirebase.LEGACY_DB_URL}/"))
    }
}
