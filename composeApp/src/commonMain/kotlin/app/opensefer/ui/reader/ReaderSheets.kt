package app.opensefer.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.opensefer.core.domain.ReadingLanguage
import app.opensefer.core.domain.ReadingPreferences
import app.opensefer.core.domain.ReadingTheme
import app.opensefer.ui.UiStrings
import app.opensefer.ui.theme.LocalReadingColors

/** The "א/A" display options: text size, language, theme, nikud. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplayOptionsSheet(
    preferences: ReadingPreferences,
    onFontScale: (Float) -> Unit,
    onTheme: (ReadingTheme) -> Unit,
    onLanguage: (ReadingLanguage) -> Unit,
    onToggleNikud: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalReadingColors.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            SheetLabel(UiStrings.TEXT_SIZE)
            Slider(
                value = preferences.fontScale,
                onValueChange = onFontScale,
                valueRange = 0.8f..2.0f,
                steps = 5,
                colors = SliderDefaults.colors(
                    thumbColor = colors.accent,
                    activeTrackColor = colors.accent,
                    inactiveTrackColor = colors.divider,
                ),
            )

            SheetLabel(UiStrings.LANGUAGE)
            SegmentedRow(
                options = listOf(
                    UiStrings.LANG_HEBREW to ReadingLanguage.Hebrew,
                    UiStrings.LANG_BOTH to ReadingLanguage.Bilingual,
                    UiStrings.LANG_ENGLISH to ReadingLanguage.English,
                ),
                selected = preferences.language,
                onSelect = onLanguage,
            )

            SheetLabel(UiStrings.THEME)
            SegmentedRow(
                options = listOf(
                    UiStrings.THEME_LIGHT to ReadingTheme.Light,
                    UiStrings.THEME_SEPIA to ReadingTheme.Sepia,
                    UiStrings.THEME_DARK to ReadingTheme.Dark,
                ),
                selected = preferences.theme,
                onSelect = onTheme,
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
                    .heightIn(min = 48.dp)
                    .toggleable(value = preferences.showNikud, role = Role.Switch, onValueChange = { onToggleNikud() }),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(UiStrings.NIKUD, color = colors.text, style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = preferences.showNikud,
                    onCheckedChange = null, // the whole row toggles
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.onAccent,
                        checkedTrackColor = colors.accent,
                        uncheckedThumbColor = colors.secondaryText,
                        uncheckedTrackColor = colors.background,
                        uncheckedBorderColor = colors.divider,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = LocalReadingColors.current.secondaryText,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp).semantics { heading() },
    )
}

@Composable
private fun <T> SegmentedRow(
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val colors = LocalReadingColors.current
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, value) ->
            val isSelected = value == selected
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) colors.accent else colors.background,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(value) }),
            ) {
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) colors.onAccent else colors.text,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
