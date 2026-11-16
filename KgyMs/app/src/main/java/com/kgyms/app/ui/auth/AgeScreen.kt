package com.kgyms.app.ui.auth

import com.kgyms.app.ui.localization.appText
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
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
    onContinue: (Int) -> Unit,
    initialAge: Int = 28,
    onAgeChanged: (Int) -> Unit = {}
) {
    var age by rememberSaveable { mutableIntStateOf(initialAge.coerceIn(10, 100)) }

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
            text = appText("Bạn bao nhiêu tuổi?", "How Old Are You?"),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = appText("Tuổi giúp chúng tôi cá nhân hóa\nkế hoạch tập luyện và dinh dưỡng.", "Your age helps us personalize\nyour workout and nutrition plan."),
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
            text = appText("tuổi", "years old"),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            fontSize = 18.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        HorizontalRuler(
            minValue = 10,
            maxValue = 100,
            currentValue = age,
            onValueChange = { age = it; onAgeChanged(it) },
            modifier = Modifier.testTag("age_ruler")
        )

        Spacer(modifier = Modifier.weight(1f))

        KgyMsButton(
            text = appText("Tiếp tục", "Continue"),
            onClick = { onContinue(age) }
        )
        Spacer(modifier = Modifier.height(32.dp))
    }
}
