package com.kgyms.app.integration

import com.google.firebase.auth.FirebaseAuth
import com.kgyms.app.data.firebase.FirestoreService
import com.kgyms.app.data.model.User
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Tạo sẵn một hội viên đã có hồ sơ (như tài khoản đã xác nhận email) cho test tích hợp.
 * Luồng đăng ký thật trong app chỉ tạo hồ sơ sau khi xác nhận email, nên fixture tạo trực tiếp.
 */
internal suspend fun seedRegisteredMember(profile: User, password: String): User {
    val auth = FirebaseAuth.getInstance()
    val firebaseUser = checkNotNull(
        auth.createUserWithEmailAndPassword(profile.email, password).await().user
    )
    val saved = profile.copy(
        uid = firebaseUser.uid,
        email = firebaseUser.email ?: profile.email,
        role = "user",
        status = "active",
    )
    FirestoreService().saveUser(firebaseUser.uid, saved)
    return saved
}

/** Lấy mã hành động (oobCode) mà Auth Emulator "gửi" tới [recipient] cho [requestType]. */
internal fun emulatorActionCode(
    host: String,
    authPort: Int,
    project: String,
    requestType: String,
    recipient: String,
): String {
    val connection = URL("http://$host:$authPort/emulator/v1/projects/$project/oobCodes")
        .openConnection() as HttpURLConnection
    connection.connectTimeout = 5_000
    connection.readTimeout = 5_000
    try {
        check(connection.responseCode == 200) { "Auth Emulator did not list account actions" }
        val codes = connection.inputStream.bufferedReader().use { JSONObject(it.readText()).getJSONArray("oobCodes") }
        return (0 until codes.length()).map(codes::getJSONObject).last {
            it.getString("requestType") == requestType &&
                (it.optString("email") == recipient || it.optString("newEmail") == recipient)
        }.getString("oobCode")
    } finally {
        connection.disconnect()
    }
}

/** Mô phỏng việc người dùng bấm link xác nhận trong email (chỉ trên Auth Emulator). */
internal suspend fun verifyEmailOnEmulator(host: String, authPort: Int, project: String, email: String) {
    val code = emulatorActionCode(host, authPort, project, "VERIFY_EMAIL", email)
    FirebaseAuth.getInstance().applyActionCode(code).await()
}
