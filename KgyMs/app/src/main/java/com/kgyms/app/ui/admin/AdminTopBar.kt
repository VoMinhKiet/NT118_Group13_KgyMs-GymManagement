package com.kgyms.app.ui.admin

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    /*
     * ============================
     * PROFILE MENU STATE
     * ============================
     */

    var menuExpanded by remember {
        mutableStateOf(false)
    }


    /*
     * ============================
     * AVATAR ANIMATION
     * ============================
     *
     * Avatar sẽ phóng nhẹ + xoay
     * khi menu được mở.
     */

    val avatarScale by animateFloatAsState(

        targetValue =
            if (menuExpanded) {
                1.08f
            } else {
                1f
            },

        animationSpec =
            spring(
                dampingRatio = 0.65f,
                stiffness = 500f
            ),

        label = "avatarScale"
    )


    val avatarRotation by animateFloatAsState(

        targetValue =
            if (menuExpanded) {
                3f
            } else {
                0f
            },

        animationSpec =
            spring(
                dampingRatio = 0.7f,
                stiffness = 450f
            ),

        label = "avatarRotation"
    )


    /*
     * ============================
     * AVATAR BACKGROUND ANIMATION
     * ============================
     */

    val avatarBackground by animateColorAsState(

        targetValue =
            if (menuExpanded) {
                AdminColors.SurfaceLight
            } else {
                Color.Transparent
            },

        animationSpec =
            spring(
                dampingRatio = 0.8f
            ),

        label = "avatarBackground"
    )


    /*
     * ============================
     * TOP BAR
     * ============================
     */

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

            /*
             * ============================
             * LEFT SIDE
             * ============================
             */

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                /*
                 * BACK BUTTON
                 */

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


                /*
                 * TITLE + SUBTITLE
                 */

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


            /*
             * ============================
             * RIGHT SIDE
             * ============================
             */

            Box(
                contentAlignment =
                    Alignment.Center
            ) {

                /*
                 * ============================
                 * AVATAR CONTAINER
                 * ============================
                 */

                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(
                            RoundedCornerShape(14.dp)
                        )
                        .background(
                            avatarBackground
                        )
                        .clickable {

                            menuExpanded =
                                !menuExpanded
                        }
                        .padding(2.dp),

                    contentAlignment =
                        Alignment.Center
                ) {

                    Image(
                        painter =
                            painterResource(
                                id =
                                    R.drawable.kgyms_logo
                            ),

                        contentDescription =
                            "KgyMs Admin",

                        modifier = Modifier
                            .size(42.dp)
                            .graphicsLayer {
                                scaleX =
                                    avatarScale

                                scaleY =
                                    avatarScale

                                rotationZ =
                                    avatarRotation
                            }
                    )
                }


                /*
                 * ============================
                 * DROPDOWN MENU
                 * ============================
                 *
                 * DropdownMenu là Popup nên
                 * không bị giới hạn bởi TopBar.
                 *
                 * Nó sẽ xuất hiện phía dưới
                 * avatar.
                 */

                DropdownMenu(

                    expanded =
                        menuExpanded,

                    onDismissRequest = {

                        menuExpanded =
                            false
                    },

                    shape =
                        RoundedCornerShape(18.dp),

                    containerColor =
                        AdminColors.Surface,

                    tonalElevation =
                        8.dp,

                    shadowElevation =
                        14.dp
                ) {

                    /*
                     * ============================
                     * PROFILE HEADER
                     * ============================
                     */

                    Column(
                        modifier = Modifier
                            .width(220.dp)
                            .padding(
                                horizontal = 14.dp,
                                vertical = 8.dp
                            )
                    ) {

                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            /*
                             * MINI AVATAR
                             */

                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(
                                        RoundedCornerShape(11.dp)
                                    )
                                    .background(
                                        AdminColors.SurfaceLight
                                    ),

                                contentAlignment =
                                    Alignment.Center
                            ) {

                                Image(
                                    painter =
                                        painterResource(
                                            id =
                                                R.drawable.kgyms_logo
                                        ),

                                    contentDescription =
                                        null,

                                    modifier =
                                        Modifier.size(34.dp)
                                )
                            }


                            Spacer(
                                modifier =
                                    Modifier.width(10.dp)
                            )


                            /*
                             * ADMIN INFO
                             */

                            Column {

                                Text(
                                    text =
                                        "Võ Minh Kiệt",

                                    color =
                                        AdminColors.TextPrimary,

                                    fontSize = 11.sp,

                                    fontWeight =
                                        FontWeight.Bold
                                )


                                Spacer(
                                    modifier =
                                        Modifier.height(2.dp)
                                )


                                Text(
                                    text =
                                        "Quản trị viên",

                                    color =
                                        AdminColors.TextSecondary,

                                    fontSize = 8.sp
                                )
                            }
                        }
                    }


                    /*
                     * ============================
                     * DIVIDER
                     * ============================
                     */

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(
                                AdminColors.Border
                            )
                    )


                    /*
                     * ============================
                     * PROFILE
                     * ============================
                     */

                    DropdownMenuItem(

                        leadingIcon = {

                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(
                                        RoundedCornerShape(9.dp)
                                    )
                                    .background(
                                        AdminColors.Primary
                                            .copy(
                                                alpha = 0.15f
                                            )
                                    ),

                                contentAlignment =
                                    Alignment.Center
                            ) {

                                Text(
                                    text = "●",

                                    color =
                                        AdminColors.Primary,

                                    fontSize = 14.sp
                                )
                            }
                        },

                        text = {

                            Column {

                                Text(
                                    text =
                                        "Thông tin cá nhân",

                                    color =
                                        AdminColors.TextPrimary,

                                    fontSize = 11.sp,

                                    fontWeight =
                                        FontWeight.SemiBold
                                )


                                Spacer(
                                    modifier =
                                        Modifier.height(2.dp)
                                )


                                Text(
                                    text =
                                        "Xem và chỉnh sửa hồ sơ",

                                    color =
                                        AdminColors.TextSecondary,

                                    fontSize = 8.sp
                                )
                            }
                        },

                        trailingIcon = {

                            Text(
                                text = "›",

                                color =
                                    AdminColors.TextSecondary,

                                fontSize = 20.sp
                            )
                        },

                        onClick = {

                            menuExpanded =
                                false

                            onProfileClick()
                        }
                    )


                    /*
                     * ============================
                     * LOGOUT
                     * ============================
                     */

                    DropdownMenuItem(

                        leadingIcon = {

                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(
                                        RoundedCornerShape(9.dp)
                                    )
                                    .background(
                                        AdminColors.Pink
                                            .copy(
                                                alpha = 0.12f
                                            )
                                    ),

                                contentAlignment =
                                    Alignment.Center
                            ) {

                                Text(
                                    text = "↪",

                                    color =
                                        AdminColors.Pink,

                                    fontSize = 16.sp,

                                    fontWeight =
                                        FontWeight.Bold
                                )
                            }
                        },

                        text = {

                            Column {

                                Text(
                                    text =
                                        "Đăng xuất",

                                    color =
                                        AdminColors.Pink,

                                    fontSize = 11.sp,

                                    fontWeight =
                                        FontWeight.SemiBold
                                )


                                Spacer(
                                    modifier =
                                        Modifier.height(2.dp)
                                )


                                Text(
                                    text =
                                        "Thoát khỏi tài khoản",

                                    color =
                                        AdminColors.TextSecondary,

                                    fontSize = 8.sp
                                )
                            }
                        },

                        onClick = {

                            menuExpanded =
                                false

                            onLogoutClick()
                        }
                    )
                }
            }
        }
    }
}