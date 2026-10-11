package com.kgyms.app.ui.profile

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgyms.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


private fun decodeProfileAvatar(context: android.content.Context, uriString: String, targetSize: Int = 512): ImageBitmap? = runCatching {
    val uri = Uri.parse(uriString)
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

    var sample = 1
    val largest = maxOf(bounds.outWidth, bounds.outHeight)
    while (largest / sample > targetSize * 2) sample *= 2

    val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
    resolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, options)?.asImageBitmap()
    }
}.getOrNull()

@Composable
internal fun ProfileAvatar(
    avatarUri: String?,
    initials: String,
    modifier: Modifier = Modifier,
    editable: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val p = LocalProfilePalette.current
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, avatarUri) {
        value = if (avatarUri.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            decodeProfileAvatar(context, avatarUri)
        }
    }
    val actionLabel = profileText("Đổi ảnh đại diện", "Change profile photo")
    val outerModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick).semantics { contentDescription = actionLabel }
    } else modifier

    Box(modifier = outerModifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier.matchParentSize()
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(p.avatarStart, p.avatarEnd)))
                .border(2.dp, p.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = profileText("Ảnh đại diện", "Profile photo"),
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(initials, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (editable) {
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).size(28.dp),
                shape = CircleShape,
                color = p.accent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                border = BorderStroke(2.dp, p.background),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("✎", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
internal fun ProfilePage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalProfilePalette.current
    Scaffold(containerColor = p.background, contentColor = p.text) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val backLabel = profileText("Quay lại", "Back")
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp).testTag("profile_back").semantics { contentDescription = backLabel },
                    contentPadding = PaddingValues(0.dp),
                ) { Text("‹", color = p.text, fontSize = 32.sp) }
                Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Image(painterResource(R.drawable.profile_kgyms_logo), "KgyMs", Modifier.size(44.dp, 32.dp))
            }
            Column(
                Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

@Composable
internal fun ProfileHeading(title: String, subtitle: String? = null) {
    Text(title, fontWeight = FontWeight.Bold, fontSize = 24.sp)
    if (subtitle != null) Text(subtitle, color = LocalProfilePalette.current.muted, fontSize = 14.sp)
}

@Composable
internal fun ProfileCard(content: @Composable ColumnScope.() -> Unit) {
    val p = LocalProfilePalette.current
    Surface(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        color = p.card, contentColor = p.text, border = BorderStroke(1.dp, p.border),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
internal fun ProfileSettingRow(title: String, detail: String? = null, onClick: () -> Unit) {
    val p = LocalProfilePalette.current
    Surface(
        onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        color = p.card, contentColor = p.text, border = BorderStroke(1.dp, p.border),
    ) {
        Row(Modifier.heightIn(min = 64.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                if (detail != null) Text(detail, color = p.muted, fontSize = 12.sp)
            }
            Text("›", fontSize = 24.sp, color = p.muted)
        }
    }
}

@Composable
internal fun ProfileField(
    label: String, value: String, onChange: (String) -> Unit,
    tag: String, keyboardType: KeyboardType = KeyboardType.Text,
    error: String? = null, password: Boolean = false, visible: Boolean = false,
    onVisibility: (() -> Unit)? = null,
) {
    val p = LocalProfilePalette.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = p.muted, fontSize = 13.sp)
        OutlinedTextField(
            value = value, onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().testTag(tag), singleLine = true,
            shape = RoundedCornerShape(16.dp), isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = if (onVisibility != null) {
                { TextButton(onClick = onVisibility) { Text(profileText(if (visible) "Ẩn" else "Hiện", if (visible) "Hide" else "Show")) } }
            } else null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = p.card, unfocusedContainerColor = p.card,
                focusedBorderColor = p.accent, unfocusedBorderColor = p.border,
                focusedTextColor = p.text, unfocusedTextColor = p.text,
            ),
        )
        if (error != null) Text(error, color = p.danger, fontSize = 12.sp)
    }
}

@Composable
internal fun ProfilePrimaryButton(label: String, tag: String = "profile_save", onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(tag),
        shape = RoundedCornerShape(18.dp),
    ) { Text(label, fontWeight = FontWeight.SemiBold) }
}

@Composable
internal fun ProfileChoice(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalProfilePalette.current
    Surface(
        onClick = onClick, modifier = modifier.heightIn(min = 48.dp).semantics { this.selected = selected; role = Role.RadioButton }, shape = RoundedCornerShape(24.dp),
        color = if (selected) p.tint else p.card, contentColor = if (selected) p.accent else p.muted,
        border = BorderStroke(1.dp, if (selected) p.accent else p.border),
    ) {
        Box(Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
            Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}
