package moe.notice.filter.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun SectionHeader(
    title: String,
    trailing: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Segmented list item with an optional leading icon, title, supporting text and a trailing slot. */
@Composable
fun SettingRow(
    title: String,
    shape: Shape,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            trailing?.invoke()
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            content = content,
        )
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            content = content,
        )
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    shape: Shape,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    supporting: String? = null,
) {
    SettingRow(
        title = title,
        shape = shape,
        icon = icon,
        supporting = supporting,
        onClick = { onCheckedChange(!checked) },
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            thumbContent = if (checked) {
                {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                }
            } else {
                null
            },
        )
    }
}

/** Segmented list item with a title, live value label and a slider; commits on release. */
@Composable
fun SettingSliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    shape: Shape,
    onValueCommit: (Float) -> Unit,
    icon: ImageVector? = null,
    supporting: String? = null,
) {
    var local by remember { mutableFloatStateOf(value) }
    LaunchedEffect(value) { local = value }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    if (supporting != null) {
                        Text(
                            text = supporting,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = String.format(Locale.ROOT, "%.2f", local),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = local,
                onValueChange = { local = it },
                onValueChangeFinished = { onValueCommit(local) },
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.padding(start = if (icon != null) 40.dp else 0.dp),
            )
        }
    }
}
