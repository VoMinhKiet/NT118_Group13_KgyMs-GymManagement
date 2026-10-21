package com.kgyms.app.integration

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kgyms.app.ui.auth.AgeScreen
import com.kgyms.app.ui.auth.HeightScreen
import com.kgyms.app.ui.auth.WeightScreen
import com.kgyms.app.ui.theme.KgyMsTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real drag gestures exercise the same Canvas interaction as a user. */
class OnboardingRulerTest {
    @get:Rule val compose = createComposeRule()
    private fun current(tag: String): Float = compose.onNodeWithTag(tag).fetchSemanticsNode()
        .config[SemanticsProperties.ProgressBarRangeInfo].current

    @Test fun ageCanBeDraggedInBothDirections() {
        compose.setContent { KgyMsTheme { AgeScreen({}, {}) } }
        compose.onNodeWithTag("age_ruler").performTouchInput { swipeLeft() }
        val increased = current("age_ruler")
        assertTrue(increased > 28f)
        compose.onNodeWithTag("age_ruler").performTouchInput { swipeRight() }
        assertTrue(current("age_ruler") < increased)
    }
    @Test fun poundsRulerUsesNewBoundsInsteadOfCapturedKilogramBounds() {
        compose.setContent { KgyMsTheme { WeightScreen({}, { _, _ -> }) } }
        compose.onNodeWithText("LB").performClick()
        assertEquals(165f, current("weight_ruler"), 0.01f)
        compose.onNodeWithTag("weight_ruler").performSemanticsAction(SemanticsActions.SetProgress) { it(440f) }
        compose.onNodeWithTag("weight_ruler").performTouchInput { swipeLeft() }
        assertEquals(440f, current("weight_ruler"), 0.01f)
        compose.onNodeWithText("KG").performClick()
        assertEquals(200f, current("weight_ruler"), 0.01f)
    }
    @Test fun heightRetainsVerticalDraggingInteraction() {
        compose.setContent { KgyMsTheme { HeightScreen({}, {}) } }
        compose.onNodeWithTag("height_ruler").performTouchInput { swipeDown() }
        assertTrue(current("height_ruler") > 165f)
        assertTrue(current("height_ruler") <= 250f)
    }
}
