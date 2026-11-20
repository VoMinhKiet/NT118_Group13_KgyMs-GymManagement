package com.kgyms.app.data.server

import com.google.firebase.auth.FirebaseAuth
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Gọi server KgyMs (thư mục server/ trong repo, chạy trên Vercel).
 *
 * Mọi request gửi kèm "Authorization: Bearer <Firebase ID token>" của người đang đăng nhập.
 * Server trả lỗi dạng { "error": "<mã>", "message": "<tiếng Việt>" } → ném [ServerException] với message đó.
 */
class KgyMsServerService(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val baseUrl: String = BASE_URL
) {
    /* ============================ HỘI VIÊN ============================ */

    /** POST /payments: tạo (hoặc trả lại) hoá đơn chuyển khoản cho gói [packageId]. */
    suspend fun createPayment(packageId: String): CreatedPayment {
        val json = post("/payments", JSONObject().put("packageId", packageId))
        return CreatedPayment(
            paymentId = json.getString("paymentId"),
            amount = json.optLong("amount"),
            expiresAt = if (json.isNull("expiresAt")) 0L else json.optLong("expiresAt"),
            packageName = json.optString("packageName")
        )
    }

    /** POST /payments/:id/cancel: huỷ hoá đơn đang chờ của mình. */
    suspend fun cancelPayment(paymentId: String) {
        post("/payments/$paymentId/cancel", JSONObject())
    }

    /** POST /check-in: xử lý mã QR điểm danh; trả nguyên JSON kết quả. */
    suspend fun checkIn(code: String): JSONObject = post("/check-in", JSONObject().put("code", code))

    /* ============================ ADMIN ============================ */

    /** POST /admin/reconciliation/:id/resolve. [action]: activate | refunded | ignored. */
    suspend fun resolveReconciliation(id: String, action: String, paymentId: String?, note: String) {
        val body = JSONObject().put("action", action).put("note", note)
        if (!paymentId.isNullOrBlank()) body.put("paymentId", paymentId)
        post("/admin/reconciliation/$id/resolve", body)
    }

    /* ============================ HTTP ============================ */

    private suspend fun post(path: String, body: JSONObject): JSONObject {
        val user = auth.currentUser ?: throw ServerException("not_logged_in", "Bạn cần đăng nhập.")
        val token = user.getIdToken(false).await().token
            ?: throw ServerException("no_token", "Không lấy được phiên đăng nhập, vui lòng đăng nhập lại.")

        return withContext(Dispatchers.IO) {
            val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
            }
            try {
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val json = runCatching { JSONObject(text) }.getOrNull()
                if (status !in 200..299) {
                    throw ServerException(
                        json?.optString("error").orEmpty().ifBlank { "http_$status" },
                        json?.optString("message").orEmpty().ifBlank { "Máy chủ KgyMs báo lỗi ($status), vui lòng thử lại." }
                    )
                }
                json ?: throw ServerException("bad_response", "Máy chủ KgyMs trả dữ liệu không hợp lệ.")
            } catch (e: ServerException) {
                throw e
            } catch (e: SocketTimeoutException) {
                throw ServerException("timeout", "Máy chủ KgyMs phản hồi quá lâu, vui lòng thử lại.")
            } catch (e: IOException) {
                throw ServerException("network", "Không kết nối được máy chủ KgyMs. Kiểm tra mạng rồi thử lại.")
            } finally {
                connection.disconnect()
            }
        }
    }

    companion object {
        /** Server deploy trên Vercel (Root Directory = server). */
        const val BASE_URL = "https://nt-118-group13-kgy-ms-gym-managemen.vercel.app"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 25_000
    }
}

data class CreatedPayment(
    val paymentId: String,
    val amount: Long,
    /** millis; 0 nếu hoá đơn cũ không có hạn. */
    val expiresAt: Long,
    val packageName: String
)

/** Lỗi do server trả về; [message] là câu tiếng Việt hiển thị được cho người dùng. */
class ServerException(val code: String, message: String) : Exception(message)
