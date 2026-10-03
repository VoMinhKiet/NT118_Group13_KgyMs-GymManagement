package com.kgyms.app.ui.admin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.statusBars
import com.kgyms.app.R


@Composable
fun AdminTopBar(
    title: String,
    subtitle: String? = null,
    showBackButton: Boolean = false,
    onBack: (() -> Unit)? = null,
    onProfileClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {}
) {

    var menuExpanded by remember {
        mutableStateOf(false)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                AdminColors.Background
            )
            .windowInsetsPadding(
                WindowInsets.statusBars
            )
            .padding(
                horizontal = 16.dp,
                vertical = 4.dp
            )
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),

            horizontalArrangement =
                Arrangement.SpaceBetween,

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            // =================================================
            // LEFT SIDE
            // =================================================

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                // ---------------------------------------------
                // BACK BUTTON
                // ---------------------------------------------

                if (showBackButton) {

                    Text(
                        text = "‹",

                        color =
                            AdminColors.TextPrimary,

                        fontSize = 30.sp,

                        fontWeight =
                            FontWeight.Light,

                        modifier = Modifier
                            .clickable {
                                onBack?.invoke()
                            }
                            .padding(
                                end = 8.dp
                            )
                    )
                }


                // ---------------------------------------------
                // TITLE
                // ---------------------------------------------

                Column {

                    Text(
                        text = title,

                        color =
                            AdminColors.TextPrimary,

                        fontSize = 18.sp,

                        fontWeight =
                            FontWeight.Bold
                    )


                    if (subtitle != null) {

                        Spacer(
                            modifier =
                                Modifier.height(2.dp)
                        )

                        Text(
                            text = subtitle,

                            color =
                                AdminColors.TextSecondary,

                            fontSize = 9.sp
                        )
                    }
                }
            }


            // =================================================
            // RIGHT SIDE - LOGO
            // =================================================

            Box {

                Image(
                    painter = painterResource(
                        id = R.drawable.kgyms_logo
                    ),

                    contentDescription =
                        "KgyMs Admin",

                    modifier = Modifier
                        .size(42.dp)
                        .clickable {
                            menuExpanded = true
                        }
                )


                // =================================================
                // ACCOUNT MENU
                // =================================================

                DropdownMenu(
                    expanded = menuExpanded,

                    onDismissRequest = {
                        menuExpanded = false
                    }
                ) {

                    // -----------------------------------------
                    // PROFILE
                    // -----------------------------------------

                    DropdownMenuItem(

                        text = {
                            Text(
                                text = "Thông tin cá nhân"
                            )
                        },

                        onClick = {

                            menuExpanded = false

                            onProfileClick()
                        }
                    )


                    // -----------------------------------------
                    // LOGOUT
                    // -----------------------------------------

                    DropdownMenuItem(

                        text = {
                            Text(
                                text = "Đăng xuất"
                            )
                        },

                        onClick = {

                            menuExpanded = false

                            onLogoutClick()
                        }
                    )
                }
            }
        }
    }
}