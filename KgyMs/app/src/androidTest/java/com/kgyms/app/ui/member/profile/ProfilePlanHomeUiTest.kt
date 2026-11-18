package com.kgyms.app.ui.member.profile

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.kgyms.app.data.local.MemberBodyMeasurementStore
import com.kgyms.app.data.model.*
import com.kgyms.app.data.repository.*
import com.kgyms.app.ui.localization.AppLanguageController
import com.kgyms.app.ui.localization.LocalAppLanguage
import com.kgyms.app.ui.member.LocalMemberAccount
import com.kgyms.app.ui.member.MemberAccountState
import com.kgyms.app.ui.member.MemberApp
import com.kgyms.app.viewmodel.membership.MembershipViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class ProfilePlanHomeUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun measurementsReloadFromDeviceAndAreIsolatedByUid() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val uid = "test-" + UUID.randomUUID()
        assertEquals(MemberBodyMeasurements(), MemberBodyMeasurementStore(context, uid).load())
        MemberBodyMeasurementStore(context, uid).save(MemberBodyMeasurements("71.5", "175"))
        assertEquals(MemberBodyMeasurements("71.5", "175"), MemberBodyMeasurementStore(context, uid).load())
        val other = MemberBodyMeasurementStore(context, "$uid-B")
        assertEquals(MemberBodyMeasurements(), other.load())
        other.save(MemberBodyMeasurements("90", "185"))
        assertEquals("71.5", MemberBodyMeasurementStore(context, uid).load().weightKg)
        assertEquals("185", other.load().heightCm)
        MemberBodyMeasurementStore(context, uid).save(MemberBodyMeasurements())
        assertEquals("90", other.load().weightKg)
    }

    @Test fun profileShowsServerMeasurementsWithoutAnEditorOrStatistics() {
        val member = User(uid = "A", fullName = "Test Member", email = "member@example.test",
            gender = "female", age = 24, weight = 154.0, weightUnit = "LB", height = 175.0)
        compose.setContent { ProfileAppearance(false, "en") {
            ProfileScreen(onAction = {}, profile = member.memberProfile("en"))
        } }
        for (label in listOf("154 lb", "175 cm", "Female", "24")) {
            compose.onNodeWithTag("profile_list").performScrollToNode(hasText(label))
            compose.onNodeWithText(label).assertIsDisplayed()
        }
        compose.onNodeWithTag("profile_list").performScrollToNode(hasTestTag("action_Membership"))
        compose.onNodeWithTag("profile_edit_body").assertDoesNotExist()
        compose.onNodeWithTag("profile_completed").assertDoesNotExist()
        compose.onNodeWithTag("profile_active_days").assertDoesNotExist()
        compose.onNodeWithTag("profile_weekly").assertDoesNotExist()
    }

    @Test fun homeAndProfileShareLiveStatusAndHomeRoutesOpenTheRightTabs() {
        val packages = listOf(GymPackage(id = "real", name = "Gym from Admin"))
        val member = Membership(id = "m", uid = "A", packageId = "real", status = MembershipStatus.PENDING)
        val memberships = MutableStateFlow<List<Membership>>(emptyList())
        val repo = object : MemberMembershipRepository {
            override fun currentUid() = "A"
            override fun observeUid() = flowOf("A")
            override fun observePackages() = flowOf(packages)
            override fun observeMemberships(uid: String) = memberships
            override fun observeTrainerNames() = flowOf(emptyMap<String, String>())
        }
        val user = User(uid = "A", fullName = "Test Member", email = "member@example.test")
        compose.setContent {
            val scope = rememberCoroutineScope()
            val account = remember {
                val session = MemberAccountSession("A", object : MemberProfileDataSource {
                    override suspend fun load(uid: String) = user
                    override suspend fun save(uid: String, edit: MemberProfileEdit) = error("Test reads only")
                }, { "A" })
                MemberAccountState(session, scope, user)
            }
            val model = remember { MembershipViewModel(repo, externalScope = scope) }
            CompositionLocalProvider(
                LocalMemberAccount provides account,
                LocalAppLanguage provides AppLanguageController("vi") {},
            ) { MemberApp(membershipViewModel = model) }
        }
        fun homeItem(tag: String) = compose.onNodeWithTag("member_screen_home").performScrollToNode(hasTestTag(tag))
        homeItem("home_membership")
        compose.onNodeWithTag("home_membership").assertTextContains("Chưa đăng ký gói", substring = true)
        compose.runOnIdle { memberships.value = listOf(member) }
        compose.onNodeWithTag("home_membership").assertTextContains("Đang chờ kích hoạt", substring = true).performClick()
        compose.onNodeWithTag("packages_tab_owned").assertIsSelected()
        compose.onNodeWithTag("membership_m").assertExists()
        compose.onNodeWithTag("home_preview").assertDoesNotExist()
        Espresso.pressBack()
        homeItem("home_card_request")
        compose.onNodeWithTag("home_card_request").performClick()
        compose.onNodeWithTag("packages_tab_catalog").assertIsSelected()
        compose.onNodeWithTag("packages_back").performClick()
        compose.onNodeWithTag("member_screen_home").assertExists()

        compose.runOnIdle { memberships.value = listOf(member.copy(status = MembershipStatus.FROZEN)) }
        homeItem("home_membership")
        compose.onNodeWithTag("home_membership").assertTextContains("Bị đóng băng", substring = true)
        compose.onNodeWithTag("member_tab_profile").performClick()
        compose.onNodeWithTag("profile_list").performScrollToNode(hasTestTag("action_Membership"))
        compose.onNodeWithTag("action_Membership").assertTextContains("Bị đóng băng", substring = true).performClick()
        compose.onNodeWithTag("packages_tab_owned").assertIsSelected()
        compose.onNodeWithTag("packages_back").performClick()
        compose.onNodeWithTag("profile_list").assertExists()
    }
}
