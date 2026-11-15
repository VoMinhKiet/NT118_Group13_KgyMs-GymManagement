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

/** Shared avatar state. Authenticated members use initials; sample previews may use a local image. */
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
    val editable: Boolean get() = account == null
    val hasAvatar: Boolean get() = editable && avatarUri != null
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
            // Keep any legacy avatarUrl untouched in the profile. Member UI intentionally makes no
            // network image or Storage request and consistently falls back to account initials.
            bitmap = null
            avatarUri = null
            failed = false
            busy = false
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
        if (busy || account != null) return
        busy = true
        failed = false
        scope.launch {
            try {
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
