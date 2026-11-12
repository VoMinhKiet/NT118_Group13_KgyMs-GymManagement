package com.kgyms.app.ui.member

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgyms.app.ui.member.profile.ProfileAppearance
import com.kgyms.app.ui.member.profile.profileText as tr
import com.kgyms.app.ui.member.profile.LocalProfilePalette
import com.kgyms.app.ui.member.profile.ProfileFlow
import com.kgyms.app.data.mock.MockMemberProfile
import com.kgyms.app.data.model.ProfileUiData
import com.kgyms.app.ui.member.workout.MemberWorkoutScreen
import com.kgyms.app.ui.member.home.MemberHomeAction
import com.kgyms.app.ui.member.home.MemberHomeScreen
import com.kgyms.app.ui.member.progress.MemberProgressScreen

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
    onLogout: (() -> Unit)? = null,
    initialProfile: ProfileUiData = MockMemberProfile,
    profileContent: @Composable (@Composable () -> Unit) -> Unit = { bar ->
        ProfileFlow(bottomBar = bar, onLogout = onLogout, initialProfile = initialProfile)
    },
) {
    val avatar = LocalMemberAvatar.current ?: rememberMemberAvatarState(LocalMemberAccount.current?.user?.fullName ?: initialProfile.name)
    val preferences = rememberMemberPreferences(LocalMemberAccount.current?.uid)
    CompositionLocalProvider(LocalMemberPreferences provides preferences, LocalMemberAvatar provides avatar) {
        ProfileAppearance(preferences.dark, preferences.language) {
            key(LocalMemberAccount.current?.uid) { MemberShell(modifier, profileContent) }
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
            workoutEntry = "plan"; workoutRequest++
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
                            is MemberHomeAction.RecommendedWorkout, MemberHomeAction.AssignedWorkout -> {
                                workoutEntry = when (action) {
                                    MemberHomeAction.WorkoutLibrary -> "library"
                                    is MemberHomeAction.RecommendedWorkout -> "workout:${action.workoutId}"
                                    MemberHomeAction.AssignedWorkout -> "assigned"
                                    else -> "hub"
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
                    if (planSection != MemberPlanSection.Nutrition.name) {
                        workoutState.SaveableStateProvider("workout") {
                            MemberWorkoutScreen(workoutEntry, workoutRequest,
                                onOverview = { select(MemberTab.Home) },
                                onHome = { select(MemberTab.Home) },
                                onNutrition = { planSection = MemberPlanSection.Nutrition.name },
                                dataSource = if (LocalMemberAccount.current != null)
                                    com.kgyms.app.data.mock.ReferenceMemberWorkoutDataSource
                                else com.kgyms.app.data.mock.MockMemberWorkoutDataSource)
                        }
                    } else MemberPlanPreview(MemberPlanSection.valueOf(planSection),
                        onSection = {
                            if (it != MemberPlanSection.Nutrition) {
                                workoutEntry = if (it == MemberPlanSection.Workout) "hub" else "plan"
                                workoutRequest++
                            }
                            planSection = it.name
                        }, onHome = { select(MemberTab.Home) })
                } else if (selected == MemberTab.QR) {
                    MemberQrPreview(onHome = { select(MemberTab.Home) })
                } else {
                    MemberProgressScreen(
                        uid = LocalMemberAccount.current?.uid,
                        weeklyTarget = checkNotNull(LocalMemberPreferences.current).weeklyTarget,
                        onHome = { select(MemberTab.Home) },
                        onPlan = { select(MemberTab.Plan) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberBottomBar(selected: MemberTab, onSelect: (MemberTab) -> Unit) {
    MemberDashboardAppearance {
        val p = LocalProfilePalette.current
        val colors = MaterialTheme.colorScheme
        Box(Modifier.fillMaxWidth().background(p.background).navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)) {
            Surface(shape = RoundedCornerShape(28.dp), color = p.card,
                border = BorderStroke(1.dp, p.border), shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    MemberTab.entries.forEach { tab ->
                        val active = selected == tab
                        val icon = remember(tab) { tab.icon() }
                        val isQr = tab == MemberTab.QR
                        val tint = if (active) p.accent else p.muted
                        Column(
                            modifier = Modifier.weight(1f).heightIn(min = 64.dp)
                                .selectable(active, role = Role.Tab, onClick = { onSelect(tab) })
                                .testTag("member_tab_" + tab.name.lowercase()).padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(if (isQr) 16.dp else 12.dp),
                                color = when { isQr -> colors.primary; active -> p.tint; else -> Color.Transparent },
                                border = if (isQr && active) BorderStroke(2.dp, p.text) else null,
                            ) {
                                Box(Modifier.size(width = if (isQr) 48.dp else 42.dp, height = if (isQr) 44.dp else 28.dp),
                                    contentAlignment = Alignment.Center) {
                                    Icon(icon, null, Modifier.size(if (isQr) 25.dp else 22.dp),
                                        tint = if (isQr) colors.onPrimary else tint)
                                }
                            }
                            Text(tab.title(), color = tint, style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Member navigation dark", widthDp = 390, showBackground = true)
@Composable
private fun MemberNavigationDarkPreview() {
    ProfileAppearance(true, "vi") { MemberBottomBar(MemberTab.Home) {} }
}

@Preview(name = "Member navigation compact light", widthDp = 320, fontScale = 1.5f, showBackground = true)
@Composable
private fun MemberNavigationLightPreview() {
    ProfileAppearance(false, "en") { MemberBottomBar(MemberTab.QR) {} }
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
