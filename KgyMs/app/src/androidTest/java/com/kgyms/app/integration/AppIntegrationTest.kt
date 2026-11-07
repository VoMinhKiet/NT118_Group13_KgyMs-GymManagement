package com.kgyms.app.integration

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.espresso.Espresso
import com.kgyms.app.ui.navigation.KgyMsApp
import com.kgyms.app.ui.theme.KgyMsTheme
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Actual integrated NavHost, not replaced with test-only destination mocks. */
class AppIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var nav: NavHostController

    private fun start(debug: Boolean = true): StateRestorationTester {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            KgyMsTheme {
                val controller = rememberNavController()
                nav = controller
                KgyMsApp(controller, debug)
            }
        }
        route("welcome") // Exercises Splash -> Welcome rather than bypassing Splash.
        return restoration
    }
    private fun route(name: String) {
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("route_$name").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun click(text: String) {
        val node = compose.onNodeWithText(text, useUnmergedTree = true)
        val scrollable = compose.onAllNodes(hasText(text) and hasAnyAncestor(hasScrollAction()), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()
        if (scrollable) node.performScrollTo()
        node.performClick()
    }
    private fun field(tag: String, text: String) { compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(text) }
    private fun value(tag: String, value: Float) { compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.SetProgress) { it(value) } }
    private fun continueStep(next: String) { click("Continue"); route(next) }
    private fun chooseRole(label: String, destination: String) {
        click("Sign In"); route("login")
        field("login_email", "test@gmail.com"); field("login_password", "123456")
        click("Sign In"); route("role_preview")
        click(label); route(destination)
        compose.runOnIdle { assertNull(nav.previousBackStackEntry) }
    }

    @Test fun signUpKeepsDraftAcrossAllBackStepsAndRestorationThenEntersMember() {
        val restoration = start()
        click("Sign Up"); route("signup")
        click("Sign Up") // Empty form must stay on Sign Up.
        compose.onNodeWithTag("route_signup").assertExists()
        field("signup_name", "Trương Tấn Kiệt")
        field("signup_email", "kiet@example.com")
        field("signup_password", "123456")
        field("signup_confirm", "123456")
        click("Sign Up"); route("gender")
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose.onNodeWithTag("gender_Female").performClick()
        continueStep("age")
        value("age_ruler", 42f)
        continueStep("weight")
        value("weight_ruler", 80f)
        click("LB") // 80 kg -> 176 lb, not the unchanged number 80.
        compose.onNodeWithText("176").assertExists()
        continueStep("height")
        value("height_ruler", 180f)
        restoration.emulateSavedInstanceStateRestore()
        route("height")
        compose.onNodeWithText("180 cm").assertExists()
        compose.onNodeWithTag("onboarding_back").performClick(); route("weight")
        compose.onNodeWithText("176").assertExists()
        compose.onNodeWithTag("onboarding_back").performClick(); route("age")
        compose.onNodeWithText("42").assertExists()
        compose.onNodeWithTag("onboarding_back").performClick(); route("gender")
        compose.onNodeWithText("Continue").assertIsEnabled()
        compose.onNodeWithTag("onboarding_back").performClick(); route("signup")
        compose.onNodeWithTag("signup_name").assertTextContains("Trương Tấn Kiệt")
        click("Sign Up"); route("gender")
        continueStep("age"); compose.onNodeWithText("42").assertExists()
        continueStep("weight"); compose.onNodeWithText("176").assertExists()
        continueStep("height"); compose.onNodeWithText("180 cm").assertExists()
        continueStep("member")
        compose.onNodeWithTag("app_notice_ok").performClick()
        compose.onNodeWithTag("profile_list").performScrollToIndex(0)
        compose.onNodeWithText("Trương Tấn Kiệt").assertExists()
        compose.onNodeWithText("kiet@example.com").assertExists()
        compose.runOnIdle { assertNull(nav.previousBackStackEntry) }
    }

    @Test fun memberLogoutClearsGraphAndANewPreviewHasFreshProfileState() {
        start()
        chooseRole("Vào Member Profile", "member")
        compose.onNodeWithTag("profile_list").performScrollToNode(hasTestTag("action_EditProfile"))
        compose.onNodeWithTag("action_EditProfile").performClick()
        field("profile_name", "Temporary User")
        compose.onNodeWithTag("profile_save").performScrollTo().performClick()
        compose.onNodeWithTag("profile_message_ok").performClick()
        compose.onNodeWithTag("profile_list").performScrollToNode(hasTestTag("action_Logout"))
        compose.onNodeWithTag("action_Logout").performClick()
        click("Hủy")
        compose.onNodeWithTag("route_member").assertExists()
        compose.onNodeWithTag("action_Logout").performClick()
        compose.onNodeWithTag("profile_confirm").performClick()
        route("login")
        compose.runOnIdle { assertNull(nav.previousBackStackEntry) }
        compose.onAllNodesWithTag("route_member").assertCountEquals(0)
        field("login_email", "test@gmail.com"); field("login_password", "123456")
        click("Sign In"); route("role_preview"); click("Vào Member Profile"); route("member")
        compose.onNodeWithTag("profile_list").performScrollToIndex(0)
        compose.onNodeWithText("Nguyễn Minh Anh").assertExists()
        compose.onNodeWithText("Temporary User").assertDoesNotExist()
    }

    @Test fun adminMenuLogoutReturnsToLoginWithNoAdminOrRolePreviewBehindIt() {
        start()
        chooseRole("Vào Admin Dashboard", "admin")
        compose.onNodeWithTag("admin_account_menu").performClick()
        click("Đăng xuất")
        click("Hủy")
        compose.onNodeWithTag("route_admin").assertExists()
        compose.onNodeWithTag("admin_account_menu").performClick(); click("Đăng xuất")
        compose.onNodeWithTag("admin_confirm_logout").performClick()
        route("login")
        compose.runOnIdle { assertNull(nav.previousBackStackEntry) }
        compose.onNodeWithTag("route_admin").assertDoesNotExist()
        compose.onNodeWithTag("route_role_preview").assertDoesNotExist()
        compose.onNodeWithTag("auth_back").performClick()
        route("welcome") // Back fallback cannot reopen Admin.
    }

    @Test fun noMockSuccessWhenDebugModeIsDisabledAndForgotPasswordIsExplicitPreview() {
        start(debug = false)
        click("Sign In"); route("login")
        field("login_email", "test@gmail.com"); field("login_password", "123456")
        click("Sign In")
        compose.onNodeWithTag("route_login").assertExists()
        compose.onNodeWithTag("route_role_preview").assertDoesNotExist()
        click("Forgot password?")
        compose.onNodeWithTag("app_notice_ok").performClick()
        compose.onNodeWithTag("route_login").assertExists()
    }
    @Test fun googleButtonIsOnlyAMockAndSignUpStillGoesThroughGender() {
        start()
        click("Sign Up"); route("signup")
        click("Continue with Google"); route("gender")
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose.onNodeWithTag("onboarding_back").performClick(); route("signup")
        compose.onNodeWithTag("auth_back").performClick(); route("welcome")
        click("Sign In"); route("login")
        click("Continue with Google"); route("role_preview")
        click("Quay lại đăng nhập"); route("login")
        compose.runOnIdle { assertNull(nav.previousBackStackEntry) }
    }

}
