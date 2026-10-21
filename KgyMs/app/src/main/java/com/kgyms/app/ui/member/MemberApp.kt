package com.kgyms.app.ui.member

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgyms.app.ui.profile.ProfileAppearance
import com.kgyms.app.ui.profile.profileText as tr
import com.kgyms.app.ui.profile.LocalProfilePalette
import com.kgyms.app.ui.profile.ProfileFlow
import com.kgyms.app.ui.workout.MemberWorkoutScreen
import com.kgyms.app.ui.home.MemberHomeAction
import com.kgyms.app.ui.home.MemberHomeScreen

private enum class MemberTab { Home, Plan, QR, Progress, Profile }

@Composable
private fun MemberTab.title(): String = when (this) {
    MemberTab.Home -> tr("Trang chủ", "Home")
    MemberTab.Plan -> tr("Kế hoạch", "Plan")
    MemberTab.QR -> "QR"
    MemberTab.Progress -> tr("Tiến độ", "Progress")
    MemberTab.Profile -> tr("Hồ sơ", "Profile")
}

/** UI shell only. The authenticated host owns the session and logout. */
@Composable
fun MemberApp(
    modifier: Modifier = Modifier,
    profileContent: @Composable (@Composable () -> Unit) -> Unit = { bar ->
        ProfileFlow(bottomBar = bar)
    },
) {
    val preferences = rememberMemberPreferences()
    CompositionLocalProvider(LocalMemberPreferences provides preferences) {
        ProfileAppearance(preferences.dark, preferences.language) {
            MemberShell(modifier, profileContent)
        }
    }
}

@Composable
private fun MemberShell(
    modifier: Modifier,
    profileContent: @Composable (@Composable () -> Unit) -> Unit,
) {
    var workoutEntry by rememberSaveable { mutableStateOf("plan") }
    var workoutRequest by rememberSaveable { mutableIntStateOf(0) }
    val workoutState = rememberSaveableStateHolder()
    var planSection by rememberSaveable { mutableStateOf(MemberPlanSection.Overview.name) }
    var selectedName by rememberSaveable { mutableStateOf(MemberTab.Home.name) }
    val selected = MemberTab.valueOf(selectedName)
    val stateHolder = rememberSaveableStateHolder()
    val select: (MemberTab) -> Unit = { selectedName = it.name }
    // ProfileFlow registers its deeper BackHandler after this parent handler.
    BackHandler(enabled = selected != MemberTab.Home) {
        if (selected == MemberTab.Plan && planSection != MemberPlanSection.Overview.name) {
            planSection = MemberPlanSection.Overview.name
        } else select(MemberTab.Home)
    }
    val bar: @Composable () -> Unit = { MemberBottomBar(selected, select) }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("member_app"),
        containerColor = LocalProfilePalette.current.background,
        contentColor = LocalProfilePalette.current.text,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // Profile's existing Scaffold owns this slot on its root screen.
            if (selected != MemberTab.Profile) bar()
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            stateHolder.SaveableStateProvider(selected.name) {
                if (selected == MemberTab.Home) {
                    MemberHomeScreen(onAction = { action ->
                        select(when (action) {
                            MemberHomeAction.Profile -> MemberTab.Profile
                            MemberHomeAction.Workouts, MemberHomeAction.WorkoutLibrary,
                            MemberHomeAction.RecommendedWorkout, MemberHomeAction.AssignedWorkout -> {
                                workoutEntry = when (action) {
                                    MemberHomeAction.WorkoutLibrary -> "library"
                                    MemberHomeAction.RecommendedWorkout -> "session:upper-body"
                                    MemberHomeAction.AssignedWorkout -> "assigned"
                                    else -> "plan"
                                }
                                workoutRequest++
                                planSection = MemberPlanSection.Workout.name; MemberTab.Plan
                            }
                            MemberHomeAction.Nutrition -> {
                                planSection = MemberPlanSection.Nutrition.name; MemberTab.Plan
                            }
                            MemberHomeAction.Progress -> MemberTab.Progress
                        })
                    })
                } else if (selected == MemberTab.Profile) {
                    profileContent(bar)
                } else if (selected == MemberTab.Plan) {
                    if (planSection == MemberPlanSection.Workout.name) {
                        workoutState.SaveableStateProvider("workout") {
                            MemberWorkoutScreen(workoutEntry, workoutRequest,
                                onOverview = { planSection = MemberPlanSection.Overview.name },
                                onHome = { select(MemberTab.Home) })
                        }
                    } else MemberPlanPreview(MemberPlanSection.valueOf(planSection),
                        onSection = {
                            if (it == MemberPlanSection.Workout) { workoutEntry = "plan"; workoutRequest++ }
                            planSection = it.name
                        }, onHome = { select(MemberTab.Home) })
                } else if (selected == MemberTab.QR) {
                    MemberQrPreview(onHome = { select(MemberTab.Home) })
                } else {
                    MemberPlaceholder(selected) {
                        select(if (selected == MemberTab.Home) MemberTab.Profile else MemberTab.Home)
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberBottomBar(selected: MemberTab, onSelect: (MemberTab) -> Unit) {
    val p = LocalProfilePalette.current
    Box(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth().selectableGroup()) {
        Surface(
            modifier = Modifier.padding(top = 28.dp).fillMaxWidth(),
            shape = MemberBarShape,
            color = p.card,
            shadowElevation = 8.dp,
        ) {
            Row(Modifier.fillMaxWidth().heightIn(min = 84.dp),
                verticalAlignment = Alignment.CenterVertically) {
                MemberTab.entries.forEach { tab ->
                    if (tab == MemberTab.QR) Spacer(Modifier.weight(1f))
                    else {
                        val active = selected == tab
                        val color = if (active) p.accent else p.muted
                        val icon = remember(tab) { tab.icon() }
                        Column(
                            Modifier.weight(1f).selectable(active, role = Role.Tab, onClick = { onSelect(tab) })
                                .testTag("member_tab_" + tab.name.lowercase()).heightIn(min = 64.dp).padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterVertically),
                        ) {
                            Icon(icon, null, Modifier.size(23.dp), tint = color)
                            Text(tab.title(), color = color, fontSize = 10.sp, textAlign = TextAlign.Center,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
        val qr = remember { MemberTab.QR.icon() }
        Column(
            Modifier.align(Alignment.TopCenter).width(88.dp)
                .selectable(selected == MemberTab.QR, role = Role.Tab, onClick = { onSelect(MemberTab.QR) })
                .testTag("member_tab_qr"),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Surface(shape = CircleShape, color = p.tint, modifier = Modifier.size(80.dp),
                border = BorderStroke(if (selected == MemberTab.QR) 3.dp else 1.dp, p.accent.copy(alpha = 0.5f))) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(shape = CircleShape, color = p.accent, modifier = Modifier.size(64.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(qr, tr("Mở lựa chọn QR", "Open QR options"), Modifier.size(33.dp), tint = Color.White)
                        }
                    }
                }
            }
            Text("QR", color = if (selected == MemberTab.QR) p.accent else p.muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun MemberPlaceholder(tab: MemberTab, onNavigate: () -> Unit) {
    val p = LocalProfilePalette.current
    val description = when (tab) {
        MemberTab.Home -> ""
        MemberTab.Plan -> ""
        MemberTab.QR -> ""
        MemberTab.Progress -> tr("Nhật ký và biểu đồ tiến độ sẽ được bổ sung sau.", "Workout logs and progress charts are coming soon.")
        MemberTab.Profile -> ""
    }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(24.dp)
            .testTag("member_screen_" + tab.name.lowercase()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("KgyMs", color = p.accent, style = MaterialTheme.typography.labelLarge)
        Text(tab.title(), color = p.text, style = MaterialTheme.typography.headlineLarge)
        Surface(
            shape = RoundedCornerShape(22.dp), color = p.card,
            border = BorderStroke(1.dp, p.border),
        ) {
            Text(
                description, color = p.muted, modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Button(
            onClick = onNavigate,
            colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = Color.White),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(tr("Về Trang chủ", "Back to Home"))
        }
    }
}

/** Local vector paths: no icon dependency or downloaded assets. */
private fun MemberTab.icon(): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f).apply {
        path(fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 1.8f) {
            when (this@icon) {
                MemberTab.Home -> {
                    moveTo(3f, 10f); lineTo(12f, 3f); lineTo(21f, 10f)
                    moveTo(5f, 9f); lineTo(5f, 21f); lineTo(10f, 21f)
                    lineTo(10f, 14f); lineTo(14f, 14f); lineTo(14f, 21f)
                    lineTo(19f, 21f); lineTo(19f, 9f)
                }
                MemberTab.Plan -> {
                    moveTo(5f, 5f); lineTo(19f, 5f); lineTo(19f, 21f)
                    lineTo(5f, 21f); close()
                    moveTo(8f, 2f); lineTo(8f, 8f)
                    moveTo(16f, 2f); lineTo(16f, 8f)
                    moveTo(8f, 12f); lineTo(16f, 12f)
                    moveTo(8f, 16f); lineTo(14f, 16f)
                }
                MemberTab.QR -> {
                    moveTo(3f, 3f); lineTo(10f, 3f); lineTo(10f, 10f); lineTo(3f, 10f); close()
                    moveTo(14f, 3f); lineTo(21f, 3f); lineTo(21f, 10f); lineTo(14f, 10f); close()
                    moveTo(3f, 14f); lineTo(10f, 14f); lineTo(10f, 21f); lineTo(3f, 21f); close()
                    moveTo(14f, 14f); lineTo(18f, 14f); lineTo(18f, 18f); lineTo(21f, 18f)
                    moveTo(14f, 18f); lineTo(14f, 21f); lineTo(18f, 21f)
                    moveTo(21f, 13f); lineTo(21f, 15f)
                }
                MemberTab.Progress -> {
                    moveTo(3f, 3f); lineTo(3f, 21f); lineTo(22f, 21f)
                    moveTo(6f, 17f); lineTo(11f, 11f); lineTo(15f, 14f); lineTo(21f, 5f)
                }
                MemberTab.Profile -> {
                    moveTo(8f, 7f); curveTo(8f, 1f, 16f, 1f, 16f, 7f)
                    curveTo(16f, 13f, 8f, 13f, 8f, 7f); close()
                    moveTo(4f, 22f); lineTo(4f, 19f)
                    curveTo(4f, 12f, 20f, 12f, 20f, 19f); lineTo(20f, 22f)
                }
            }
        }
    }.build()
