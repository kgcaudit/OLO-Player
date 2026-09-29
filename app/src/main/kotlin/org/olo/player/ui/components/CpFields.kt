package org.olo.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.olo.player.ui.theme.OloTheme

// Compact OLO input rows: a label on the left and the value on the right, on the
// same 52dp rhythm as the list and settings rows, with a hairline under each --
// instead of Material's tall outlined boxes, which read as a different design.
// A required, still-empty field shows "필수 항목" in the accent; a placeholder is
// the muted outline colour.

private const val LABEL_WIDTH = 92

@Composable
private fun FieldFrame(label: String, trailing: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    val c = OloTheme.colors
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = c.text, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, modifier = Modifier.width(LABEL_WIDTH.dp))
            Box(Modifier.weight(1f)) { content() }
            if (trailing != null) trailing()
        }
        Box(Modifier.fillMaxWidth().padding(start = 20.dp).height(1.dp).background(c.divider))
    }
}

/** A single-line text field styled as a compact OLO row (no box). */
@Composable
fun CpField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    required: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = OloTheme.colors
    FieldFrame(label, trailing) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = c.text, fontSize = 15.sp),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = visualTransformation,
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        if (required) "필수 항목" else placeholder,
                        color = if (required) c.accent else c.outline,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                inner()
            },
        )
    }
}

/** A password field: masked by default, with an eye to reveal it. */
@Composable
fun CpFieldSecret(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
) {
    val c = OloTheme.colors
    var revealed by remember { mutableStateOf(false) }
    CpField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        keyboardType = KeyboardType.Password,
        visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
        trailing = {
            Icon(
                if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                contentDescription = null,
                tint = c.outline,
                modifier = Modifier.size(20.dp).clickable { revealed = !revealed },
            )
        },
    )
}

/** A select row: the label, the chosen value, and a chevron opening a menu. */
@Composable
fun CpSelectRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val c = OloTheme.colors
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second ?: options.firstOrNull()?.second.orEmpty()
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { open = true }.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = c.text, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, modifier = Modifier.width(LABEL_WIDTH.dp))
            Text(current, color = c.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = c.outline, modifier = Modifier.size(22.dp))
            Box {
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    for ((key, text) in options) {
                        DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(key); open = false })
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(start = 20.dp).height(1.dp).background(c.divider))
    }
}

/** A labelled toggle on the compact field rhythm. */
@Composable
fun CpToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val c = OloTheme.colors
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = c.text, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, modifier = Modifier.weight(1f))
            CpToggle(checked = checked, onCheckedChange = onCheckedChange)
        }
        Box(Modifier.fillMaxWidth().padding(start = 20.dp).height(1.dp).background(c.divider))
    }
}

// Common charset presets for the encoding select, so a mistyped charset can no
// longer break a connection. "" means the client default (auto).
val ENCODING_OPTIONS: List<Pair<String, String>> = listOf(
    "" to "자동",
    "UTF-8" to "UTF-8",
    "EUC-KR" to "EUC-KR",
    "MS949" to "CP949 (MS949)",
    "Shift_JIS" to "Shift_JIS",
    "GBK" to "GBK",
    "ISO-8859-1" to "ISO-8859-1",
)
