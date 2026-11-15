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
internal class MemberAccountState(
    private val session: MemberAccountSession,
    val scope: kotlinx.coroutines.CoroutineScope,
    initialUser: User? = null,
) {
    val uid get() = session.uid
    var user by mutableStateOf(initialUser)
        private set
    var loading by mutableStateOf(initialUser == null)
        private set
    var saving by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    var offline by mutableStateOf(false)
        private set

    fun acceptVerified(next: User) {
        check(next.uid == uid && next.isActive && !next.isAdmin)
        user = next
        failed = false
        loading = false
        offline = false
    }

    fun updateOffline(value: Boolean) {
        offline = value
    }
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
}

/** Production entry only. A cached initial user must already be validated by SessionRestorationRepository. */
@Composable
internal fun MemberAccountHost(
    onLogout: () -> Unit,
    initialUser: User?,
    initialOffline: Boolean,
) {
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
    if (initialUser?.let {
            it.uid == owner && it.isActive && !it.isAdmin && it.hasCompletedOnboarding
        } != true
    ) {
        // Never let a changed Auth UID or an unverified role create a Member session here.
        LaunchedEffect(owner, initialUser?.uid) { onLogout() }
        return
    }
    key(owner) {
        val session = remember { MemberAccountSession(owner, UserRepository(), { auth.currentUser?.uid }) }
        val scope = rememberCoroutineScope()
        val seed = initialUser
        val account = remember { MemberAccountState(session, scope, seed) }
        DisposableEffect(session) { onDispose { session.close() } }
        LaunchedEffect(account, initialOffline) {
            account.updateOffline(initialOffline)
            if (account.user == null) account.load()
        }
        LaunchedEffect(initialUser) {
            initialUser?.takeIf {
                it.uid == owner && it.isActive && !it.isAdmin && it.hasCompletedOnboarding
            }?.let(account::acceptVerified)
        }
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
                Column(Modifier.fillMaxSize()) {
                    if (account.offline) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(
                                "Đang sử dụng ngoại tuyến",
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        MemberApp(onLogout = onLogout, initialProfile = user.memberProfile("vi"))
                    }
                }
            }
        }
    }
}
