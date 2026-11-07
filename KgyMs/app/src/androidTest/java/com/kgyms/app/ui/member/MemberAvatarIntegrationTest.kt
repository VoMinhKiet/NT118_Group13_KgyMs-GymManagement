package com.kgyms.app.ui.member

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.kgyms.app.ui.member.home.MemberHomeScreen
import com.kgyms.app.ui.member.profile.ProfileAppearance
import com.kgyms.app.data.mock.MockMemberProfile
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class MemberAvatarIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var directory: File
    private lateinit var context: Context
    private lateinit var scope: CoroutineScope
    private lateinit var state: MemberAvatarState

    @Before fun setup() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(app.cacheDir, "avatar-test-${System.nanoTime()}").apply { mkdirs() }
        context = object : ContextWrapper(app) { override fun getFilesDir(): File = directory }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        runBlocking(Dispatchers.Main) {
            state = MemberAvatarState(context, scope, mutableStateOf(MockMemberProfile.name))
            state.restore()
        }
    }

    @After fun cleanup() {
        scope.cancel()
        directory.deleteRecursively()
    }

    private fun select(uri: String?) {
        compose.runOnIdle { state.select(uri) }
        compose.waitUntil(10_000) { !state.busy }
    }

    private fun photo(): String {
        val file = File(directory, "source.jpg")
        val bitmap = Bitmap.createBitmap(2400, 1600, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.MAGENTA)
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
        bitmap.recycle()
        return Uri.fromFile(file).toString()
    }

    @Test fun sharedBitmapSurvivesMemberTabsAndProfileEditUpdatesFallback() {
        compose.setContent {
            CompositionLocalProvider(LocalMemberAvatar provides state) { MemberApp() }
        }
        compose.onNodeWithTag("member_avatar_initials", useUnmergedTree = true).assertTextEquals(state.initials)
        compose.onNodeWithTag("home_avatar").performClick()
        compose.onNodeWithTag("profile_avatar_edit").performClick()
        compose.onNodeWithTag("profile_name").performTextReplacement("Trương Tấn Kiệt")
        select(photo())
        val first = state.bitmap
        assertNotNull(first)
        assertTrue(first!!.width <= 384 && first.height <= 384)
        compose.onNodeWithTag("profile_save").performScrollTo().performClick()
        compose.onNodeWithTag("profile_message_ok").performClick()
        repeat(8) {
            compose.onNodeWithTag("member_tab_home").performClick()
            compose.onNodeWithTag("member_avatar_photo", useUnmergedTree = true).assertExists()
            compose.onNodeWithText("Trương Tấn Kiệt").assertExists()
            compose.onNodeWithTag("member_tab_profile").performClick()
            compose.onNodeWithTag("member_avatar_photo", useUnmergedTree = true).assertExists()
            assertSame(first, state.bitmap)
        }
        select(null)
        compose.onNodeWithTag("member_avatar_initials", useUnmergedTree = true).assertTextEquals("TK")
        compose.onNodeWithTag("member_tab_home").performClick()
        compose.onNodeWithTag("member_avatar_initials", useUnmergedTree = true).assertTextEquals("TK")
    }

    @Test fun appearanceChangesKeepSharedAvatar() {
        var dark by mutableStateOf(true)
        var language by mutableStateOf("vi")
        compose.setContent {
            CompositionLocalProvider(LocalMemberAvatar provides state) {
                ProfileAppearance(dark, language) { MemberHomeScreen(onAction = {}) }
            }
        }
        select(photo())
        val first = state.bitmap
        listOf(true to "vi", false to "vi", false to "en", true to "en").forEach { (d, l) ->
            compose.runOnIdle { dark = d; language = l }
            compose.onNodeWithTag("member_avatar_photo", useUnmergedTree = true).assertExists()
            assertSame(first, state.bitmap)
        }
    }

    @Test fun privateCopyRestoresAfterSourceDisappearsAndBadSelectionsKeepPreviousPhoto() {
        compose.setContent { /* state and storage test; no picker automation required */ }
        val source = photo()
        select(source)
        val previous = state.bitmap
        File(Uri.parse(source).path!!).delete()
        select(Uri.fromFile(File(directory, "missing.jpg")).toString())
        assertTrue(state.failed)
        assertSame(previous, state.bitmap)
        val corrupt = File(directory, "corrupt.jpg").apply { writeText("not an image") }
        select(Uri.fromFile(corrupt).toString())
        assertTrue(state.failed)
        assertSame(previous, state.bitmap)
        select("content://com.kgyms.nonexistent/avatar")
        assertTrue(state.failed)
        assertSame(previous, state.bitmap)
        runBlocking(Dispatchers.Main) {
            val restored = MemberAvatarState(context, scope, mutableStateOf(MockMemberProfile.name))
            restored.restore()
            assertNotNull(restored.bitmap)
            assertTrue(restored.avatarUri!!.startsWith("file:"))
        }
        select(null)
        runBlocking(Dispatchers.Main) {
            val restored = MemberAvatarState(context, scope, mutableStateOf(MockMemberProfile.name))
            restored.restore()
            assertNull(restored.bitmap)
            assertNull(restored.avatarUri)
        }
        File(directory, "member_sample_avatar.image").writeText("corrupt saved image")
        runBlocking(Dispatchers.Main) {
            val restored = MemberAvatarState(context, scope, mutableStateOf(MockMemberProfile.name))
            restored.restore()
            assertNull(restored.bitmap)
            assertFalse(restored.busy)
        }
    }
}
