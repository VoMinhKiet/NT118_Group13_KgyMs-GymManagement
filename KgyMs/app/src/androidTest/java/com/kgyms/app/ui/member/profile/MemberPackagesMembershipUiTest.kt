package com.kgyms.app.ui.member.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import com.kgyms.app.data.mock.MockMemberProfile
import com.kgyms.app.data.model.*
import com.kgyms.app.data.util.AppDates
import com.kgyms.app.viewmodel.membership.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Runs in the existing isolated emulator APK; immutable fixtures never access Firebase. */
class MemberPackagesMembershipUiTest {
    @get:Rule val compose = createComposeRule()
    private val gym = GymPackage(id = "gym", name = "Gym from Admin", price = 350000, durationDays = 30,
        benefits = listOf("Gym access"), description = "Catalog description")
    private val pt = GymPackage(id = "pt", name = "PT from Admin", type = PackageType.PT, sessions = 12,
        trainerUid = "trainer", price = 1500000, durationDays = 60)
    private val now = AppDates.parseDay("18/11/2026")!!
    private fun member(id: String = "m", status: String = "active", packageId: String = "gym") =
        Membership(id = id, uid = "A", packageId = packageId, status = status,
            startDate = "01/11/2026", endDate = "30/11/2026")
    private fun state(memberships: List<Membership> = listOf(member())) = MembershipUiState(uid = "A",
        packages = listOf(gym, pt, gym.copy(id = "retired", name = "Retired catalog", isActive = false)),
        memberships = memberships, trainerNames = mapOf("trainer" to "Coach Mai"), now = now)

    private fun scroll(tag: String) {
        compose.onNodeWithTag("packages_list").performScrollToNode(hasTestTag(tag))
    }

    @Test fun compactEnglishLightCatalogAndDetailHaveDisabledCheckoutAndCorrectBack() {
        var backCount = 0
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.5f)) {
                ProfileAppearance(false, "en") {
                    Box(Modifier.requiredWidth(320.dp).fillMaxHeight()) {
                        MemberPackagesMembershipScreen(state(), {}, { backCount++ })
                    }
                }
            }
        }
        compose.onNodeWithText("Packages & Membership").assertIsDisplayed()
        compose.onNodeWithTag("packages_tab_catalog").performClick()
        compose.onNodeWithTag("catalog_retired").assertDoesNotExist()
        scroll("catalog_pt")
        compose.onNodeWithTag("catalog_pt").performClick()
        compose.onAllNodesWithText("Assigned PT: Coach Mai").onLast().assertExists()
        compose.onNodeWithTag("package_checkout").performScrollTo().assertIsNotEnabled()
        Espresso.pressBack()
        compose.onNodeWithTag("package_checkout").assertDoesNotExist()
        compose.onNodeWithTag("packages_back").performClick()
        compose.runOnIdle { assertEquals(1, backCount) }
    }

    @Test fun allStatusesAndDeletedPackageRemainVisibleInOwnedHistory() {
        val memberships = listOf("active", "pending", "frozen", "expired", "cancelled").map { member(it, it) } +
            member("deleted", packageId = "deleted")
        compose.setContent { ProfileAppearance(true, "vi") { MemberPackagesMembershipScreen(state(memberships), {}, {}) } }
        for (status in listOf("active", "pending", "frozen", "expired", "cancelled")) {
            scroll("membership_$status")
            compose.onNodeWithTag("membership_$status").assertIsDisplayed()
        }
        scroll("membership_deleted")
        compose.onNodeWithTag("membership_deleted").performClick()
        compose.onNodeWithText("Mã gói: deleted").assertExists()
        compose.onNodeWithText("Gym access").assertDoesNotExist()
    }

    @Test fun activePastEndWarnsAndPtUsageComesFromMembership() {
        val members = listOf(member(packageId = "pt").copy(trainerUid = "trainer", sessionsUsed = 5, endDate = "17/11/2026"))
        compose.setContent { ProfileAppearance(true, "en") { MemberPackagesMembershipScreen(state(members), {}, {}) } }
        compose.onNodeWithTag("membership_m").performClick()
        compose.onAllNodesWithText("Active in Firebase • past end date").onLast().assertExists()
        compose.onAllNodesWithText("Firebase status: Active (active)").onLast().assertExists()
        compose.onAllNodesWithText("Used: 5").onLast().assertExists()
        compose.onAllNodesWithText("Remaining: 7").onLast().assertExists()
    }

    @Test fun uidSwitchClosesOldDetailAndClearsPreviousMemberships() {
        val current = mutableStateOf(state())
        compose.setContent { ProfileAppearance(true, "vi") { MemberPackagesMembershipScreen(current.value, {}, {}) } }
        compose.onNodeWithTag("membership_m").performClick()
        compose.runOnIdle { current.value = MembershipUiState(uid = "B", now = now) }
        compose.onNodeWithTag("package_detail_close").assertDoesNotExist()
        compose.onNodeWithTag("membership_m").assertDoesNotExist()
        compose.onNodeWithTag("memberships_empty").assertIsDisplayed()
        compose.runOnIdle { current.value = MembershipUiState() }
        compose.onNodeWithText("Vui lòng đăng nhập để xem gói tập và hội viên.").assertIsDisplayed()
    }

    @Test fun checkoutCallbackOnlyHandsOffAndDoesNotActivatePendingMembership() {
        val current = state(listOf(member(status = "pending")))
        var handedOff: Pair<String, GymPackage>? = null
        compose.setContent { ProfileAppearance(false, "en") {
            MemberPackagesMembershipScreen(current, {}, {}, { uid, pkg -> handedOff = uid to pkg })
        } }
        compose.onNodeWithTag("packages_tab_catalog").performClick()
        compose.onNodeWithTag("catalog_gym").performClick()
        compose.onNodeWithTag("package_checkout").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("A", handedOff?.first)
            assertEquals(gym, handedOff?.second)
            assertEquals("pending", current.memberships.single().status)
        }
    }

    @Test fun profileRouteAndHardwareBackReturnToProfileUsingSharedMembershipState() {
        val user = User(uid = "A", fullName = "Member A", email = "a@example.test")
        val repo = object : com.kgyms.app.data.repository.MemberMembershipRepository {
            override fun currentUid() = "A"
            override fun observeUid() = kotlinx.coroutines.flow.flowOf<String?>("A")
            override fun observePackages() = kotlinx.coroutines.flow.flowOf(listOf(gym))
            override fun observeMemberships(uid: String) = kotlinx.coroutines.flow.flowOf(listOf(member(status = "pending")))
            override fun observeTrainerNames() = kotlinx.coroutines.flow.flowOf(emptyMap<String, String>())
        }
        compose.setContent {
            val scope = rememberCoroutineScope()
            val session = remember {
                com.kgyms.app.data.repository.MemberAccountSession("A", object : com.kgyms.app.data.repository.MemberProfileDataSource {
                    override suspend fun load(uid: String) = user
                    override suspend fun save(uid: String, edit: MemberProfileEdit) = error("Read-only test")
                }, { "A" })
            }
            val account = remember { com.kgyms.app.ui.member.MemberAccountState(session, scope, user) }
            val vm = remember { MembershipViewModel(repo, { now }, scope) }
            CompositionLocalProvider(com.kgyms.app.ui.member.LocalMemberAccount provides account) {
                ProfileFlow(initialProfile = MockMemberProfile.copy(isRealAccount = true), membershipViewModel = vm)
            }
        }
        fun open() {
            compose.onNodeWithTag("profile_list").performScrollToNode(hasTestTag("action_Membership"))
            compose.onNodeWithTag("action_Membership").performClick()
            compose.onNodeWithTag("membership_m").assertExists()
        }
        open()
        compose.onNodeWithTag("packages_back").performClick()
        compose.onNodeWithTag("profile_list").assertExists()
        open()
        Espresso.pressBack()
        compose.onNodeWithTag("profile_list").assertExists()
        compose.onNodeWithText("1 Đang chờ kích hoạt", substring = true).assertExists()
    }
    @Test fun profileUsesRealMembershipSummaryAndChangesLanguageAndTheme() {
        val dark = mutableStateOf(true)
        val language = mutableStateOf("vi")
        compose.setContent { ProfileAppearance(dark.value, language.value) {
            ProfileScreen({}, profile = MockMemberProfile.copy(isRealAccount = true), membershipState = state(listOf(member(status = "pending"))))
        } }
        compose.onNodeWithTag("profile_list").performScrollToNode(hasTestTag("action_Membership"))
        compose.onNodeWithText("1 Đang chờ kích hoạt", substring = true).assertExists()
        compose.onNodeWithText("Premium Member").assertDoesNotExist()
        compose.runOnIdle { dark.value = false; language.value = "en" }
        compose.onNodeWithText("Packages & Membership").assertIsDisplayed()
        compose.onNodeWithText("1 Pending activation", substring = true).assertExists()
    }

    @Test fun compactMembershipCardsScrollAndReturnToSettingsWithBothBackActions() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                ProfileAppearance(false, "vi") {
                    Box(Modifier.requiredWidth(320.dp).requiredHeight(400.dp)) {
                        SettingsScreen(false, "vi", {}, {}, {})
                    }
                }
            }
        }
        fun openCards() {
            compose.onNodeWithText("Thẻ hội viên").performScrollTo().performClick()
            compose.onNodeWithText("Quản lý thẻ và hội viên").assertIsDisplayed()
        }
        openCards()
        compose.onNodeWithText("Lịch sử tập luyện & Giao dịch").performScrollTo().assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithText("Cài đặt").assertIsDisplayed()
        openCards()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Cài đặt").assertIsDisplayed()
    }
}
