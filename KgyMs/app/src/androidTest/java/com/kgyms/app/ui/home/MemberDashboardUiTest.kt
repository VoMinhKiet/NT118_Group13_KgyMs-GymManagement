package com.kgyms.app.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kgyms.app.ui.member.DashboardDarkPalette
import com.kgyms.app.ui.member.DashboardLightPalette
import com.kgyms.app.ui.member.MemberApp
import com.kgyms.app.ui.member.MemberDashboardAppearance
import com.kgyms.app.ui.profile.DarkProfilePalette
import com.kgyms.app.ui.profile.LightProfilePalette
import com.kgyms.app.ui.profile.LocalProfilePalette
import com.kgyms.app.ui.profile.ProfileAppearance
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Constrained layouts and behavior assertions, not a frame-time benchmark. */
class MemberDashboardUiTest {
    @get:Rule val compose = createComposeRule()

    private fun item(tag: String) {
        compose.onNodeWithTag("member_screen_home").performScrollToNode(hasTestTag(tag))
    }

    @Test fun compactDashboardRetainsEveryActionAndLabelsSampleData() {
        val actions = mutableListOf<MemberHomeAction>()
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.5f)) {
                ProfileAppearance(false, "en") {
                    Box(Modifier.requiredWidth(320.dp).fillMaxHeight()) { MemberHomeScreen(onAction = { actions.add(it) }) }
                }
            }
        }
        compose.onNodeWithTag("home_sample_label").assertTextEquals("Sample data")
        item("home_weekly_activity")
        compose.onNodeWithTag("home_weekly_activity").assertIsDisplayed()
        val routes = listOf(
            "home_action_workouts" to MemberHomeAction.Workouts,
            "home_action_nutrition" to MemberHomeAction.Nutrition,
            "home_action_progress" to MemberHomeAction.Progress,
            "home_action_pt" to MemberHomeAction.AssignedWorkout,
            "home_workouts_all" to MemberHomeAction.WorkoutLibrary,
            "home_workout_upper-body" to MemberHomeAction.RecommendedWorkout,
            "home_meals_all" to MemberHomeAction.Nutrition,
        )
        routes.forEach { (tag, action) ->
            item(tag)
            compose.onNodeWithTag(tag).performClick()
            compose.runOnIdle { assertEquals(action, actions.last()) }
        }
        listOf("home_meal_chicken-bowl", "home_membership", "home_booking", "home_card_request").forEach { tag ->
            item(tag)
            compose.onNodeWithTag(tag).performClick()
            compose.onNodeWithTag("home_preview").assertIsDisplayed()
            compose.onNodeWithTag("home_preview_close").performClick()
        }
    }

    @Test fun dashboardAppearanceIsScopedAndSupportsBothLanguagesAndModes() {
        var dark by mutableStateOf(true)
        var language by mutableStateOf("vi")
        var dashboardPalette = DashboardDarkPalette
        var surroundingPalette = DarkProfilePalette
        var bodySize = 0f
        compose.setContent {
            ProfileAppearance(dark, language) {
                MemberDashboardAppearance {
                    dashboardPalette = LocalProfilePalette.current
                    bodySize = MaterialTheme.typography.bodyMedium.fontSize.value
                }
                surroundingPalette = LocalProfilePalette.current
                MemberHomeScreen(onAction = {})
            }
        }
        listOf(true to "vi", false to "vi", false to "en", true to "en").forEach { (d, l) ->
            compose.runOnIdle { dark = d; language = l }
            compose.onNodeWithTag("home_sample_label").assertTextEquals(if (l == "vi") "Dữ liệu mẫu" else "Sample data")
            compose.runOnIdle {
                assertEquals(if (d) DashboardDarkPalette else DashboardLightPalette, dashboardPalette)
                assertEquals(if (d) DarkProfilePalette else LightProfilePalette, surroundingPalette)
                assertEquals(14f, bodySize, 0f)
            }
        }
    }

    @Test fun compactShellKeepsFiveDistinctTapTargetsAndSelectedTabs() {
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            Box(Modifier.requiredWidth(320.dp).fillMaxHeight()) { MemberApp() }
        }
        val tabs = listOf("home", "plan", "qr", "progress", "profile")
        var previousRight = Float.NEGATIVE_INFINITY
        tabs.forEach { tab ->
            val bounds = compose.onNodeWithTag("member_tab_$tab").fetchSemanticsNode().boundsInRoot
            assertTrue("Tab width must be at least 48dp", bounds.width / density >= 48f)
            assertTrue("Tab height must be at least 48dp", bounds.height / density >= 48f)
            assertTrue("Tabs must not overlap", bounds.left >= previousRight - 0.5f)
            previousRight = bounds.right
        }
        tabs.forEach { tab ->
            compose.onNodeWithTag("member_tab_$tab").performClick().assertIsSelected()
        }
        compose.onNodeWithTag("member_tab_home").performClick()
        compose.onNodeWithTag("member_screen_home").assertIsDisplayed()
    }
}
