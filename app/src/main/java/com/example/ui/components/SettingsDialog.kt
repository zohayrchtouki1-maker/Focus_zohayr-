package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.i18n.Strings
import com.example.model.AppLanguage
import com.example.model.AppSettings
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.ForestEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.viewmodel.MainUiState
import kotlin.math.roundToInt

@Composable
fun SettingsDialog(
    uiState: MainUiState,
    s: Strings,
    onDismiss: () -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onExportJson: () -> String,
    onImportJson: (String) -> Boolean,
    onResetDemoData: () -> Unit
) {
    BackHandler { onDismiss() }

    val context = LocalContext.current
    val settings = uiState.data.settings
    var backupFeedback by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val json = onExportJson()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
                backupFeedback = s.backupSuccessExport
            } catch (_: Exception) {
                backupFeedback = s.backupError
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val raw = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader(Charsets.UTF_8).readText()
                }
                backupFeedback = if (raw != null && onImportJson(raw)) {
                    s.backupSuccessImport
                } else {
                    s.backupError
                }
            } catch (_: Exception) {
                backupFeedback = s.backupError
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = s.settingsTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_settings_button")) {
                    Icon(Icons.Default.Close, contentDescription = s.cancel)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Pomodoro Durations
                item {
                    SettingsSectionCard(title = s.pomodoroSettings) {
                        SettingsSliderRow(
                            label = s.pomodoroLength,
                            value = settings.pomodoroMinutes,
                            range = 1f..120f,
                            unit = s.minutesShort,
                            onChange = { v -> onUpdateSettings { it.copy(pomodoroMinutes = v) } }
                        )
                        SettingsSliderRow(
                            label = s.shortBreakLength,
                            value = settings.shortBreakMinutes,
                            range = 1f..30f,
                            unit = s.minutesShort,
                            onChange = { v -> onUpdateSettings { it.copy(shortBreakMinutes = v) } }
                        )
                        SettingsSliderRow(
                            label = s.longBreakLength,
                            value = settings.longBreakMinutes,
                            range = 5f..60f,
                            unit = s.minutesShort,
                            onChange = { v -> onUpdateSettings { it.copy(longBreakMinutes = v) } }
                        )
                        SettingsSliderRow(
                            label = s.longBreakInterval,
                            value = settings.longBreakInterval,
                            range = 2f..8f,
                            unit = "",
                            onChange = { v -> onUpdateSettings { it.copy(longBreakInterval = v) } }
                        )
                    }
                }

                // Automation Toggles
                item {
                    SettingsSectionCard(title = s.automation) {
                        SettingsSwitchRow(
                            label = s.autoStartBreaks,
                            checked = settings.autoStartBreaks,
                            onCheckedChange = { c -> onUpdateSettings { it.copy(autoStartBreaks = c) } }
                        )
                        SettingsSwitchRow(
                            label = s.autoStartNextPomodoro,
                            checked = settings.autoStartNextPomodoro,
                            onCheckedChange = { c -> onUpdateSettings { it.copy(autoStartNextPomodoro = c) } }
                        )
                        SettingsSwitchRow(
                            label = s.disableBreaks,
                            checked = settings.disableBreaks,
                            onCheckedChange = { c -> onUpdateSettings { it.copy(disableBreaks = c) } }
                        )
                    }
                }

                // Sound & Appearance
                item {
                    SettingsSectionCard(title = s.soundAndAppearance) {
                        SettingsSwitchRow(
                            label = s.alarmSound,
                            checked = settings.alarmEnabled,
                            onCheckedChange = { c -> onUpdateSettings { it.copy(alarmEnabled = c) } }
                        )
                        SettingsSwitchRow(
                            label = s.darkMode,
                            checked = settings.darkTheme,
                            onCheckedChange = { c -> onUpdateSettings { it.copy(darkTheme = c) } }
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = s.languageLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(AppLanguage.AR to s.arabicLang, AppLanguage.EN to s.englishLang).forEach { (lang, lbl) ->
                                    val active = settings.language == lang
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (active) CoralPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { onUpdateSettings { it.copy(language = lang) } }
                                    ) {
                                        Text(
                                            text = lbl,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // JSON Export/Import & Reset Demo Data
                item {
                    SettingsSectionCard(title = s.dataBackup) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { exportLauncher.launch("focus_todo_backup.json") },
                                colors = ButtonDefaults.buttonColors(containerColor = ForestEmerald),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).testTag("export_json_button")
                            ) {
                                Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(s.exportJson, maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).testTag("import_json_button")
                            ) {
                                Icon(Icons.Default.Upload, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(s.importJson, maxLines = 1)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                onResetDemoData()
                                backupFeedback = s.backupSuccessImport
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralPrimary),
                            border = BorderStroke(1.dp, CoralPrimary.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("reset_demo_button")
                        ) {
                            Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(s.resetDemoData)
                        }

                        if (backupFeedback != null) {
                            Text(
                                text = backupFeedback!!,
                                style = MaterialTheme.typography.labelMedium,
                                color = ForestEmerald,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = CoralPrimary
            )
            content()
        }
    }
}

@Composable
private fun SettingsSliderRow(
    label: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onChange: (Int) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "$value $unit".trim(),
                style = MaterialTheme.typography.titleSmall,
                fontFamily = JetBrainsMonoFontFamily,
                color = CoralPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = CoralPrimary, activeTrackColor = CoralPrimary)
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CoralPrimary)
        )
    }
}
