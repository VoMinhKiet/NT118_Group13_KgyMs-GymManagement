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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgyms.app.R
import com.kgyms.app.ui.theme.KgyMsTheme

@Composable
fun ProfileScreen(
    onAction: (ProfileAction) -> Unit,
    modifier: Modifier = Modifier,
    profile: ProfileUiData = MockMemberProfile,
    avatarUri: String? = null,
    bottomBar: @Composable () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = LocalProfilePalette.current.background,
        contentColor = LocalProfilePalette.current.text,
        bottomBar = bottomBar,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding).testTag("profile_list"),
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
                    ProfileAvatar(
                        avatarUri = avatarUri,
                        initials = profile.initials,
                        modifier = Modifier.padding(bottom = 8.dp).size(88.dp).testTag("profile_avatar_edit"),
                        editable = true,
                        onClick = { onAction(ProfileAction.EditProfile) },
                    )
                    Text(
                        profile.name,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Text(profile.email, color = LocalProfilePalette.current.muted, fontSize = 14.sp)
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ProfileStat(profile.workouts, profileText("Buổi tập", "Workouts"), Modifier.weight(1f))
                    ProfileStat(profile.daysActive, profileText("Ngày hoạt động", "Days Active"), Modifier.weight(1f))
                    ProfileStat(profile.averageRating, profileText("Đánh giá TB", "Avg Rating"), Modifier.weight(1f))
                }
            }
            item { ProfileMenuRow(profileText("Gói hội viên", "My Membership"), { onAction(ProfileAction.Membership) }, tag = "action_Membership", detail = profile.membership) }
            item { ProfileMenuRow(profileText("Thông tin cá nhân", "Personal Information"), { onAction(ProfileAction.EditProfile) }, tag = "action_EditProfile") }
            item { ProfileMenuRow(profileText("Mục tiêu tập luyện", "Fitness Goals"), { onAction(ProfileAction.FitnessGoals) }, tag = "action_FitnessGoals") }
            item { ProfileMenuRow(profileText("Cài đặt thông báo", "Notification Settings"), { onAction(ProfileAction.NotificationSettings) }, tag = "action_NotificationSettings") }
            item { ProfileMenuRow(profileText("Cài đặt ứng dụng", "App Settings"), { onAction(ProfileAction.Settings) }, tag = "action_Settings") }
            item { ProfileMenuRow(profileText("Trợ giúp & hỗ trợ", "Help & Support"), { onAction(ProfileAction.HelpAndSupport) }, tag = "action_HelpAndSupport") }
            item {
                Button(
                    onClick = { onAction(ProfileAction.Logout) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 50.dp).testTag("action_Logout"),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalProfilePalette.current.dangerBackground,
                        contentColor = LocalProfilePalette.current.danger,
                    ),
                ) {
                    Text(profileText("Đăng xuất", "Log Out"), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
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
        color = LocalProfilePalette.current.card,
        contentColor = LocalProfilePalette.current.text,
        border = BorderStroke(1.dp, LocalProfilePalette.current.border),
    ) {
        Column(
            modifier = Modifier.heightIn(min = 78.dp).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(label, color = LocalProfilePalette.current.muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ProfileMenuRow(title: String, onClick: () -> Unit, detail: String? = null, tag: String) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag(tag),
        shape = RoundedCornerShape(18.dp),
        color = LocalProfilePalette.current.card,
        contentColor = LocalProfilePalette.current.text,
        border = BorderStroke(1.dp, LocalProfilePalette.current.border),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 58.dp).padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(34.dp).background(LocalProfilePalette.current.tint, CircleShape)
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(6.dp).background(LocalProfilePalette.current.accent, CircleShape))
            }
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (detail != null) {
                Text(detail, modifier = Modifier.widthIn(max = 92.dp), color = LocalProfilePalette.current.muted, fontSize = 11.sp)
            }
            Text("›", color = LocalProfilePalette.current.muted, fontSize = 24.sp, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

@Preview(name = "Member Profile", showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Small screen", showBackground = true, widthDp = 320, heightDp = 640)
@Composable
private fun ProfileScreenPreview() {
    KgyMsTheme(darkTheme = true, dynamicColor = false) {
        ProfileAppearance(dark = true, language = "en") {
            ProfileScreen(onAction = {})
        }
    }
}
