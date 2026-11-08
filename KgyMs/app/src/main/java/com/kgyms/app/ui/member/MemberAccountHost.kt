package com.kgyms.app.ui.member

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.kgyms.app.data.model.MemberProfileEdit
import com.kgyms.app.data.model.User
import com.kgyms.app.data.model.memberProfile
import com.kgyms.app.data.repository.MemberAccountSession
import com.kgyms.app.data.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal val LocalMemberAccount = staticCompositionLocalOf<MemberAccountState?> { null }

@Stable
internal class MemberAccountState(private val session: MemberAccountSession, val scope: kotlinx.coroutines.CoroutineScope) {
    val uid get() = session.uid
    var user by mutableStateOf<User?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var saving by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    suspend fun load() {
        loading = true; failed = false
        try { user = session.load() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { user = null; failed = true }
        finally { loading = false }
    }
    suspend fun save(edit: MemberProfileEdit): Boolean {
        if (saving) return false
        saving = true; failed = false
        return try { user = session.save(edit); true }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true; false }
        finally { saving = false }
    }
    suspend fun saveAvatar(bytes: ByteArray?): String {
        check(!saving) { "Profile save in progress" }
        saving = true
        try {
            val url = session.saveAvatar(bytes)
            user = user?.copy(avatarUrl = url)
            return url
        } finally { saving = false }
    }
}

/** Production entry only. No mock fallback on missing/denied account data. */
@Composable
internal fun MemberAccountHost(onLogout: () -> Unit) {
    val auth = remember { FirebaseAuth.getInstance() }
    var uid by remember { mutableStateOf(auth.currentUser?.uid) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { uid = it.currentUser?.uid }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    val owner = uid
    if (owner == null) {
        LaunchedEffect(Unit) { onLogout() }
        return
    }
    key(owner) {
        val session = remember { MemberAccountSession(owner, UserRepository(), { auth.currentUser?.uid }) }
        val scope = rememberCoroutineScope()
        val account = remember { MemberAccountState(session, scope) }
        DisposableEffect(session) { onDispose { session.close() } }
        LaunchedEffect(account) { account.load() }
        val user = account.user
        if (user == null) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (account.loading) CircularProgressIndicator()
                else {
                    Text("Không thể tải hồ sơ / Could not load profile")
                    Button(onClick = { scope.launch { account.load() } }) { Text("Thử lại / Retry") }
                    TextButton(onClick = onLogout) { Text("Đăng xuất / Log out") }
                }
            }
        } else {
            CompositionLocalProvider(LocalMemberAccount provides account) {
                MemberApp(onLogout = onLogout, initialProfile = user.memberProfile("vi"))
            }
        }
    }
}
