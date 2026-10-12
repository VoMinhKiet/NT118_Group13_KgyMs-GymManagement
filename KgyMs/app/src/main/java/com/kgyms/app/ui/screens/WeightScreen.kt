package com.kgyms.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.kgyms.app.ui.components.HorizontalRuler
import com.kgyms.app.ui.components.KgyMsButton

@Composable
fun WeightScreen(
    onNavigateBack: () -> Unit,
    onContinue: (Int, String) -> Unit
) {
    var weight by remember { mutableIntStateOf(75) }
    var unit by remember { mutableStateOf("KG") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        com.kgyms.app.ui.components.OnboardingTopBar(
            progress = 0.75f,
            onBackClick = onNavigateBack
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "What Is Your Weight?",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "This helps us calculate your BMI\nand create accurate plans.",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 48.dp),
            textAlign = TextAlign.Center
        )
        
        Row(
            modifier = Modifier
                .width(200.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            UnitTab(
                text = "KG",
                isSelected = unit == "KG",
                onClick = { unit = "KG" },
                modifier = Modifier.weight(1f)
            )
            UnitTab(
                text = "LB",
                isSelected = unit == "LB",
                onClick = { unit = "LB" },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = weight.toString(),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = unit.lowercase(),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        HorizontalRuler(
            minValue = if (unit == "KG") 30 else 66,
            maxValue = if (unit == "KG") 200 else 440,
            currentValue = weight,
            onValueChange = { weight = it }
        )

        Spacer(modifier = Modifier.weight(1f))

        KgyMsButton(
            text = "Continue",
            onClick = { onContinue(weight, unit) }
        )
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun UnitTab(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(24.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
    }
}
