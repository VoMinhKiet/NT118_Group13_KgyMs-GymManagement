package com.kgyms.app.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgyms.app.R
import com.kgyms.app.ui.theme.KgyMsTheme

// Scoped to Profile until the team has a shared premium palette.
private val ProfileBackground = Color(0xFF0B0E15)
private val ProfileCard = Color(0xFF1B2130)
private val ProfileBorder = Color(0xFF292E3B)
private val ProfileAccent = Color(0xFFA64DFF)
private val ProfileText = Color(0xFFF5F5FC)
private val ProfileMuted = Color(0xFFA1ACBE)


@Composable
fun ProfileScreen(
    onAction: (ProfileAction) -> Unit,
    modifier: Modifier = Modifier,
    profile: ProfileUiData = MockMemberProfile,
    bottomBar: @Composable () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ProfileBackground,
        contentColor = ProfileText,
        bottomBar = bottomBar,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Profile", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Image(
                        painter = painterResource(R.drawable.profile_kgyms_logo),
                        contentDescription = "KgyMs",
                        modifier = Modifier.size(width = 48.dp, height = 36.dp),
                    )
                }
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier.padding(bottom = 8.dp).size(88.dp)
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF2B204B), Color(0xFF192D45))),
                                CircleShape,
                            )
                            .border(2.dp, Color(0xFF7840B3), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(profile.initials, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        profile.name,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Text(profile.email, color = ProfileMuted, fontSize = 14.sp)
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ProfileStat(profile.workouts, "Workouts", Modifier.weight(1f))
                    ProfileStat(profile.daysActive, "Days Active", Modifier.weight(1f))
                    ProfileStat(profile.averageRating, "Avg Rating", Modifier.weight(1f))
                }
            }
            item { ProfileMenuRow("My Membership", { onAction(ProfileAction.Membership) }, profile.membership) }
            item { ProfileMenuRow("Personal Information", { onAction(ProfileAction.EditProfile) }) }
            item { ProfileMenuRow("Fitness Goals", { onAction(ProfileAction.FitnessGoals) }) }
            item { ProfileMenuRow("Notification Settings", { onAction(ProfileAction.NotificationSettings) }) }
            item { ProfileMenuRow("App Settings", { onAction(ProfileAction.Settings) }) }
            item { ProfileMenuRow("Help & Support", { onAction(ProfileAction.HelpAndSupport) }) }
            item {
                Button(
                    onClick = { onAction(ProfileAction.Logout) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 50.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF481B24),
                        contentColor = Color(0xFFFF788E),
                    ),
                ) {
                    Text("Log Out", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun ProfileStat(value: String, label: String, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = ProfileCard,
        contentColor = ProfileText,
        border = BorderStroke(1.dp, ProfileBorder),
    ) {
        Column(
            modifier = Modifier.heightIn(min = 78.dp).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(label, color = ProfileMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ProfileMenuRow(title: String, onClick: () -> Unit, detail: String? = null) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = ProfileCard,
        contentColor = ProfileText,
        border = BorderStroke(1.dp, ProfileBorder),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 58.dp).padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(34.dp).background(Color(0xFF29264C), CircleShape)
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(6.dp).background(ProfileAccent, CircleShape))
            }
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (detail != null) {
                Text(detail, modifier = Modifier.widthIn(max = 92.dp), color = ProfileMuted, fontSize = 11.sp)
            }
            Text("›", color = ProfileMuted, fontSize = 24.sp, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

@Preview(name = "Member Profile", showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Small screen", showBackground = true, widthDp = 320, heightDp = 640)
@Composable
private fun ProfileScreenPreview() {
    KgyMsTheme(darkTheme = true, dynamicColor = false) {
        ProfileScreen(onAction = {})
    }
}
