package com.kgyms.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgyms.app.ui.components.HorizontalRuler
import com.kgyms.app.ui.components.KgyMsButton

@Composable
fun AgeScreen(
    onNavigateBack: () -> Unit,
    onContinue: (Int) -> Unit
) {
    var age by remember { mutableIntStateOf(28) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        com.kgyms.app.ui.components.OnboardingTopBar(
            progress = 0.5f,
            onBackClick = onNavigateBack
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "How Old Are You?",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Your age helps us personalize\nyour workout and nutrition plan.",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 48.dp),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = age.toString(),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "years old",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            fontSize = 18.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        HorizontalRuler(
            minValue = 10,
            maxValue = 100,
            currentValue = age,
            onValueChange = { age = it }
        )

        Spacer(modifier = Modifier.weight(1f))

        KgyMsButton(
            text = "Continue",
            onClick = { onContinue(age) }
        )
        Spacer(modifier = Modifier.height(32.dp))
    }
}
