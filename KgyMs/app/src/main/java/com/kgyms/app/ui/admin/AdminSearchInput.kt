package com.kgyms.app.ui.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

/** Editable text inside the team's existing search/response container. */
@Composable
internal fun AdminSearchInput(value: String, onChange: (String) -> Unit, placeholder: String, tag: String) {
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag(tag),
        textStyle = TextStyle(color = AdminColors.TextPrimary, fontSize = 9.sp),
        cursorBrush = SolidColor(AdminColors.Primary),
        decorationBox = { field ->
            Box {
                if (value.isEmpty()) Text(placeholder, color = AdminColors.TextSecondary, fontSize = 9.sp)
                field()
            }
        },
    )
}
