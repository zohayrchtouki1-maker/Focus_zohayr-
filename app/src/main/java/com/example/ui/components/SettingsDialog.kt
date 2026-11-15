package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsApplications
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.i18n.Strings
import com.example.model.AppLanguage
import com.example.model.AppSettings
import com.example.notifications.SystemPermissionsHelper
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.ForestEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PriorityHighColor
import com.example.ui.theme.PriorityLowColor
import com.example.ui.theme.SunlightGold
import com.example.viewmodel.MainUiState
import kotlin.math.roundToInt

@Composable
fun SettingsDialog(
    uiState: MainUiState,
    s: Strings,
    onDismiss: () -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenBackgroundPermissionsModal: () -> Unit,
    onExportJson: () -> String,
    onImportJson: (String) -> Boolean,
    onResetDemoData: () -> Unit
) {
    BackHandler { onDismiss() }

    val context = LocalContext.current
    val settings = uiState.data.settings
    val permSnapshot = uiState.systemPermissions
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
                // 1. Notifications & Background Permissions Section (الإشعارات وأذونات الخلفية)
                item {
                    SettingsSectionCard(title = s.notificationsAndPermissionsSection) {
                        SettingsSwitchWithSubtitleRow(
                            label = s.enableTaskRemindersLabel,
                            subtitle = s.enableTaskRemindersDesc,
                            checked = settings.enableTaskReminders,
                            onCheckedChange = { enabled ->
                                onUpdateSettings { it.copy(enableTaskReminders = enabled) }
                            },
                            testTag = "toggle_task_reminders"
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        SettingsSwitchWithSubtitleRow(
                            label = s.persistentTimerNotificationLabel,
                            subtitle = s.persistentTimerNotificationDesc,
                            checked = settings.persistentTimerNotification,
                            onCheckedChange = { enabled ->
                                onUpdateSettings { it.copy(persistentTimerNotification = enabled) }
                            },
                            testTag = "toggle_persistent_timer_notification"
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        SettingsSwitchWithSubtitleRow(
                            label = s.alarmSoundAndVibrationLabel,
                            subtitle = s.alarmSoundAndVibrationDesc,
                            checked = settings.alarmSoundAndVibration && settings.alarmEnabled,
                            onCheckedChange = { enabled ->
                                onUpdateSettings {
                                    it.copy(alarmSoundAndVibration = enabled, alarmEnabled = enabled)
                                }
                            },
                            testTag = "toggle_alarm_sound_vibration"
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // System Permissions Status & Direct Shortcuts
                        Text(
                            text = s.systemPermissionsStatusLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        PermissionStatusRow(
                            label = s.notificationsPermissionLabel,
                            isGranted = permSnapshot.notificationsGranted,
                            s = s,
                            onClick = {
                                if (!permSnapshot.notificationsGranted) {
                                    onRequestPermissions()
                                    SystemPermissionsHelper.openAppNotificationSettings(context)
                                } else {
                                    SystemPermissionsHelper.openAppNotificationSettings(context)
                                }
                            }
                        )

                        PermissionStatusRow(
                            label = s.exactAlarmsPermissionLabel,
                            isGranted = permSnapshot.exactAlarmsGranted,
                            s = s,
                            onClick = {
                                SystemPermissionsHelper.openExactAlarmSettings(context)
                            }
                        )

                        PermissionStatusRow(
                            label = s.batteryUnrestrictedLabel,
                            isGranted = permSnapshot.ignoringBatteryOptimizations,
                            s = s,
                            onClick = {
                                SystemPermissionsHelper.openIgnoreBatteryOptimizations(context)
                            }
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = onOpenBackgroundPermissionsModal,
                            colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary, contentColor = Color.White),
                            shape = RoundedCornerShape(percent = 50),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_background_permissions_button")
                        ) {
                            Icon(Icons.Default.Security, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = s.openBackgroundPermissionsButton,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 2. Pomodoro Durations
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

                // 3. Automation Toggles
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

                // 4. Sound & Appearance
                item {
                    SettingsSectionCard(title = s.soundAndAppearance) {
                        SettingsSwitchRow(
                            label = s.alarmSound,
                            checked = settings.alarmEnabled && settings.alarmSoundAndVibration,
                            onCheckedChange = { c ->
                                onUpdateSettings { it.copy(alarmEnabled = c, alarmSoundAndVibration = c) }
                            }
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

                // 5. JSON Export/Import & Reset Demo Data
                item {
                    SettingsSectionCard(title = s.dataBackup) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { exportLauncher.launch("focus_todo_backup.json") },
                                colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary, contentColor = Color.White),
                                shape = RoundedCornerShape(percent = 50),
                                modifier = Modifier.weight(1f).testTag("export_json_button")
                            ) {
                                Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(s.exportJson, maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                                shape = RoundedCornerShape(percent = 50),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shape = RoundedCornerShape(percent = 50),
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
fun BackgroundTimerPermissionsDialog(
    uiState: MainUiState,
    s: Strings,
    onDismiss: () -> Unit,
    onToggleLockedInRecents: (Boolean) -> Unit
) {
    BackHandler { onDismiss() }

    val context = LocalContext.current
    val settings = uiState.data.settings
    val perms = uiState.systemPermissions

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
                Icon(Icons.Default.Security, null, tint = CoralPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = s.backgroundTimerPermissionsTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_background_permissions_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = s.cancel)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = s.backgroundTimerPermissionsSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Card 1: Ignore battery optimization
                item {
                    BackgroundPermissionActionCard(
                        icon = Icons.Default.BatteryChargingFull,
                        iconTint = ForestEmerald,
                        title = s.cardIgnoreBatteryTitle,
                        description = s.cardIgnoreBatteryDesc,
                        statusText = if (perms.ignoringBatteryOptimizations) s.statusEnabledBadge else s.statusDisabledBadge,
                        statusEnabled = perms.ignoringBatteryOptimizations,
                        actionLabel = s.openSystemSettingAction,
                        onClick = { SystemPermissionsHelper.openIgnoreBatteryOptimizations(context) },
                        testTag = "card_ignore_battery_optimization"
                    )
                }

                // Card 2: Allow auto-startup (OEM Auto-Start with Transsion/Infinix XOS support)
                item {
                    BackgroundPermissionActionCard(
                        icon = Icons.Default.PlayCircleOutline,
                        iconTint = PriorityLowColor,
                        title = s.cardAllowAutoStartTitle,
                        description = s.cardAllowAutoStartDesc,
                        statusText = null,
                        statusEnabled = false,
                        actionLabel = s.openSystemSettingAction,
                        onClick = { SystemPermissionsHelper.openAutoStartSettings(context) },
                        testTag = "card_allow_auto_startup"
                    )
                }

                // Card 3: Ensure running in background
                item {
                    BackgroundPermissionActionCard(
                        icon = Icons.Default.SettingsApplications,
                        iconTint = SunlightGold,
                        title = s.cardEnsureBackgroundTitle,
                        description = s.cardEnsureBackgroundDesc,
                        statusText = null,
                        statusEnabled = false,
                        actionLabel = s.openSystemSettingAction,
                        onClick = { SystemPermissionsHelper.openAppDetailsSettings(context) },
                        testTag = "card_ensure_running_background"
                    )
                }

                // Card 4: Turn off Power Saving Mode
                item {
                    BackgroundPermissionActionCard(
                        icon = Icons.Default.BatterySaver,
                        iconTint = CoralPrimary,
                        title = s.cardTurnOffPowerSaverTitle,
                        description = s.cardTurnOffPowerSaverDesc,
                        statusText = null,
                        statusEnabled = false,
                        actionLabel = s.openSystemSettingAction,
                        onClick = { SystemPermissionsHelper.openBatterySaverSettings(context) },
                        testTag = "card_turn_off_power_saver"
                    )
                }

                // Card 5: Lock in recent apps (Interactive Confirmed / تم التثبيت toggle)
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (settings.lockedInRecentsConfirmed) ForestEmerald else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onToggleLockedInRecents(!settings.lockedInRecentsConfirmed) }
                            .testTag("card_lock_in_recent_apps")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background( ForestEmerald.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Lock, null, tint = ForestEmerald)
                                }
                                Text(
                                    text = s.cardLockInRecentsTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Text(
                                text = s.cardLockInRecentsDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = s.confirmedLockedLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (settings.lockedInRecentsConfirmed) ForestEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = settings.lockedInRecentsConfirmed,
                                    onCheckedChange = onToggleLockedInRecents,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = ForestEmerald
                                    ),
                                    modifier = Modifier.testTag("toggle_lock_in_recents_confirmed")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundPermissionActionCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    statusText: String?,
    statusEnabled: Boolean,
    actionLabel: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = iconTint)
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (statusText != null) {
                    StatusBadgePill(text = statusText, enabled = statusEnabled)
                }
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = CoralPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = CoralPrimary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun PermissionStatusRow(
    label: String,
    isGranted: Boolean,
    s: Strings,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            StatusBadgePill(
                text = if (isGranted) s.statusEnabledBadge else s.statusDisabledBadge,
                enabled = isGranted
            )
        }
    }
}

@Composable
private fun StatusBadgePill(
    text: String,
    enabled: Boolean
) {
    val color = if (enabled) ForestEmerald else PriorityHighColor
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
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

@Composable
private fun SettingsSwitchWithSubtitleRow(
    label: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CoralPrimary),
            modifier = Modifier.testTag(testTag)
        )
    }
}
