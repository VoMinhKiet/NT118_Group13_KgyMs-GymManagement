package com.kgyms.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgyms.app.ui.components.KgyMsButton
import com.kgyms.app.ui.components.VerticalRuler

@Composable
fun HeightScreen(
    onNavigateBack: () -> Unit,
    onContinue: (Int) -> Unit
) {
    var height by remember { mutableIntStateOf(165) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        com.kgyms.app.ui.components.OnboardingTopBar(
            progress = 1.0f,
            onBackClick = onNavigateBack
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "What Is Your Height?",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "This helps us suggest the right\nexercises and calorie goals.",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 48.dp),
            textAlign = TextAlign.Center
        )

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = height.toString(),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "cm",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                VerticalRuler(
                    minValue = 100,
                    maxValue = 250,
                    currentValue = height,
                    onValueChange = { height = it }
                )
                
                // Badge for current height
                Box(
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "$height cm",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        KgyMsButton(
            text = "Continue",
            onClick = { onContinue(height) }
        )
        Spacer(modifier = Modifier.height(32.dp))
    }
}
