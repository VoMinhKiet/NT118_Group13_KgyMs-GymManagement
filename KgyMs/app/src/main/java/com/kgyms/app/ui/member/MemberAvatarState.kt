package com.kgyms.app.ui.member

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.AtomicFile
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.kgyms.app.data.mock.MockMemberProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

internal val LocalMemberAvatar = staticCompositionLocalOf<MemberAvatarState?> { null }

/** Shared, bounded avatar pipeline. Production storage and state are scoped to one UID. */
@Stable
internal class MemberAvatarState(
    private val context: Context,
    private val scope: CoroutineScope,
    private val nameState: MutableState<String>,
    private val account: MemberAccountState? = null,
) {
    var name: String
        get() = nameState.value
        set(value) { nameState.value = value }
    val initials: String get() = memberInitials(name).ifBlank { "?" }
    val hasAvatar: Boolean get() = account?.user?.avatarUrl?.isNotBlank() ?: (avatarUri != null)
    var bitmap by mutableStateOf<ImageBitmap?>(null)
        private set
    var avatarUri by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(true)
        private set
    var failed by mutableStateOf(false)
        private set
    private val file by lazy {
        val filename = account?.uid?.let { "member_avatar_" + com.kgyms.app.data.model.memberAccountKey(it) + ".image" }
            ?: "member_sample_avatar.image"
        AtomicFile(File(context.filesDir, filename))
    }

    suspend fun restore() {
        if (account != null) {
            try {
                val url = account.user?.avatarUrl.orEmpty()
                val restored = if (url.isBlank()) null else withContext(Dispatchers.IO) {
                    downloadMemberAvatar(context, url) ?: throw IOException("Unreadable avatar")
                }
                bitmap = restored?.asImageBitmap()
                avatarUri = if (restored != null) url else null
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { bitmap = null; avatarUri = null; failed = true }
            finally { busy = false }
            return
        }
        try {
            val restored = withContext(Dispatchers.IO) {
                // AtomicFile recovers an interrupted write before decoding the small local image.
                try {
                    file.openRead().use { /* recover and close before URI decoding */ }
                    decodeMemberAvatar(context, Uri.fromFile(file.baseFile))
                } catch (_: Exception) { null }
            }
            bitmap = restored?.asImageBitmap()
            avatarUri = if (restored != null) Uri.fromFile(file.baseFile).toString() else null
        } finally { busy = false }
    }

    /** The shell owns this job, so leaving Edit/Profile does not cancel an import. */
    fun select(uri: String?) {
        if (busy) return
        busy = true
        failed = false
        scope.launch {
            try {
                if (account != null) {
                    val next = withContext(Dispatchers.IO) {
                        uri?.let { decodeMemberAvatar(context, Uri.parse(it)) ?: throw IOException("Unreadable avatar") }
                    }
                    val encoded = withContext(Dispatchers.IO) {
                        next?.let { image -> java.io.ByteArrayOutputStream().use { output ->
                            check(image.compress(Bitmap.CompressFormat.JPEG, 88, output))
                            output.toByteArray()
                        } }
                    }
                    val url = account.saveAvatar(encoded)
                    // The server URL is authoritative. No legacy/sample cache is ever imported.
                    bitmap = next?.asImageBitmap()
                    avatarUri = url.ifBlank { null }
                    return@launch
                }
                val next = withContext(Dispatchers.IO) {
                    if (uri == null) {
                        file.delete()
                        check(!file.baseFile.exists()) { "Avatar could not be removed" }
                        null
                    } else {
                        val decoded = decodeMemberAvatar(context, Uri.parse(uri))
                            ?: throw IOException("Unreadable avatar")
                        var output: java.io.FileOutputStream? = null
                        try {
                            output = file.startWrite()
                            val format = if (decoded.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                            if (!decoded.compress(format, 88, output)) {
                                throw IOException("Avatar encoding failed")
                            }
                            file.finishWrite(output)
                        } catch (error: Exception) {
                            file.failWrite(output)
                            throw error
                        }
                        decoded
                    }
                }
                bitmap = next?.asImageBitmap()
                avatarUri = if (next != null) Uri.fromFile(file.baseFile).toString() else null
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.e("KgyMsAvatar", "Avatar update failed", error)
                failed = true
            } finally { busy = false }
        }
    }
}

@Composable
internal fun rememberMemberAvatarState(initialName: String = MockMemberProfile.name): MemberAvatarState {
    val account = LocalMemberAccount.current
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val name = rememberSaveable(account?.uid) { mutableStateOf(initialName) }
    val state = remember(context, scope, account?.uid) { MemberAvatarState(context, scope, name, account) }
    LaunchedEffect(state) { state.restore() }
    LaunchedEffect(initialName, account?.uid) { if (account != null) state.name = initialName }
    return state
}

/** Untrusted remote images are bounded in bytes before the existing sampled decoder runs. */
private fun downloadMemberAvatar(context: Context, source: String): Bitmap? {
    var url = java.net.URL(source)
    repeat(4) {
        require(url.protocol == "https") { "Unsupported avatar URL" }
        val connection = url.openConnection() as javax.net.ssl.HttpsURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.instanceFollowRedirects = false
        try {
            val code = connection.responseCode
            if (code in 300..399) {
                url = java.net.URL(url, connection.getHeaderField("Location") ?: throw IOException("Missing redirect"))
            } else {
                check(code == 200) { "Avatar download failed" }
                val limit = 4 * 1024 * 1024
                require(connection.contentLengthLong <= limit)
                val temporary = File.createTempFile("avatar-", ".image", context.cacheDir)
                try {
                    connection.inputStream.use { input -> temporary.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= limit) { "Avatar too large" }
                            output.write(buffer, 0, count)
                        }
                    } }
                    return decodeMemberAvatar(context, Uri.fromFile(temporary))
                } finally { temporary.delete() }
            }
        } finally { connection.disconnect() }
    }
    throw IOException("Too many redirects")
}
