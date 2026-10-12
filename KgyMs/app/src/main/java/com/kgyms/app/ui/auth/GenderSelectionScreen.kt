package com.kgyms.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgyms.app.ui.components.KgyMsButton

@Composable
fun GenderSelectionScreen(
    onNavigateBack: () -> Unit,
    onContinue: (String) -> Unit
) {
    var selectedGender by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        com.kgyms.app.ui.components.OnboardingTopBar(
            progress = 0.25f,
            onBackClick = onNavigateBack
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "What's Your Gender?",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "This helps us create a personalized\nexperience for you.",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 48.dp),
            textAlign = TextAlign.Center
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            GenderOption(
                label = "Male",
                isSelected = selectedGender == "Male",
                onClick = { selectedGender = "Male" }
            )
            GenderOption(
                label = "Female",
                isSelected = selectedGender == "Female",
                onClick = { selectedGender = "Female" }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        KgyMsButton(
            text = "Continue",
            onClick = { selectedGender?.let { onContinue(it) } },
            backgroundColor = if (selectedGender != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            textColor = if (selectedGender != null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun GenderOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // Chọn màu: Nam (Xanh dương), Nữ (Hồng)
    val activeColor = if (label == "Male") Color(0xFF3B82F6) else Color(0xFFEC4899)
    
    val backgroundColor = if (isSelected) activeColor else MaterialTheme.colorScheme.surface
    val contentColor = if (isSelected) Color.White else activeColor
    val borderColor = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(backgroundColor)
                .border(
                    width = 2.dp,
                    color = borderColor,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (label == "Male") "♂" else "♀",
                fontSize = 56.sp,
                color = contentColor,
                // Chỉnh offset lại, kéo về giữa cho icon Nam
                modifier = Modifier.offset(
                    x = if (label == "Male") (-2).dp else 0.dp,
                    y = if (label == "Male") 2.dp else (-4).dp
                ),
                style = TextStyle(
                    platformStyle = PlatformTextStyle(
                        includeFontPadding = false
                    )
                )
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = label,
            color = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
