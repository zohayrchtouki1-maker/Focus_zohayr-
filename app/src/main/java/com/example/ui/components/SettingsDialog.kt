package com.example.ui.components

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.BackupImportMode
import com.example.data.FocusRepository
import com.example.i18n.Strings
import com.example.model.AppBackupState
import com.example.model.AppLanguage
import com.example.model.AppSettings
import com.example.notifications.SystemPermissionsHelper
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.ForestEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PriorityHighColor
import com.example.ui.theme.PriorityLowColor
import com.example.ui.theme.SunlightGold
import com.example.viewmodel.MainUiState
import java.io.File
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
    onCreateShareableBackupFile: () -> File,
    onParseBackupPreview: (String) -> AppBackupState?,
    onImportBackupState: (AppBackupState, BackupImportMode) -> Unit,
    onResetDemoData: () -> Unit
) {
    BackHandler { onDismiss() }

    val context = LocalContext.current
    val settings = uiState.data.settings
    val isArabic = settings.language == AppLanguage.AR
    val permSnapshot = uiState.systemPermissions
    var backupFeedback by remember { mutableStateOf<String?>(null) }
    var backupIsError by remember { mutableStateOf(false) }
    var pendingImportState by remember { mutableStateOf<AppBackupState?>(null) }

    var expandPomodoroSection by remember { mutableStateOf(false) }
    var expandProjectsSection by remember { mutableStateOf(false) }
    var expandGeneralSection by remember { mutableStateOf(false) }
    var expandAppearanceSection by remember { mutableStateOf(false) }
    var showUserGuideDialog by remember { mutableStateOf(false) }

    val defaultBackupFileName = remember { FocusRepository.generateBackupFileName() }

    val exportSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val json = onExportJson()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
                backupIsError = false
                backupFeedback = if (isArabic) {
                    "تم حفظ النسخة الاحتياطية بنجاح ($defaultBackupFileName)"
                } else {
                    "Backup saved successfully ($defaultBackupFileName)"
                }
            } catch (_: Exception) {
                backupIsError = true
                backupFeedback = s.backupError
            }
        }
    }

    fun triggerShareBackupSheet() {
        try {
            val backupFile = onCreateShareableBackupFile()
            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, backupFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, backupFile.name)
                putExtra(
                    Intent.EXTRA_TEXT,
                    if (isArabic) {
                        "نسخة احتياطية لتطبيق Focus To-Do (${backupFile.name})"
                    } else {
                        "Focus To-Do Backup (${backupFile.name})"
                    }
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(
                shareIntent,
                if (isArabic) "تصدير ومشاركة النسخة الاحتياطية" else "Export & Share Backup"
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            backupIsError = false
            backupFeedback = if (isArabic) {
                "تم تجهيز ملف النسخة الاحتياطية (${backupFile.name}) للمشاركة أو الحفظ"
            } else {
                "Backup file (${backupFile.name}) ready to share or save"
            }
        } catch (_: Exception) {
            exportSaveLauncher.launch(FocusRepository.generateBackupFileName())
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
                val parsed = if (raw != null) onParseBackupPreview(raw) else null
                if (parsed != null) {
                    pendingImportState = parsed
                    backupIsError = false
                } else {
                    backupIsError = true
                    backupFeedback = if (isArabic) {
                        "ملف النسخة الاحتياطية غير صالح أو تالف."
                    } else {
                        "Invalid or corrupted backup JSON file."
                    }
                }
            } catch (_: Exception) {
                backupIsError = true
                backupFeedback = s.backupError
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top bar: Right-aligned `>` + "الإعدادات"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("close_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = s.cancel,
                        tint = DarkTextPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Text(
                    text = s.settingsTitle,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }

            // Grouped dark cards (#1C1C21, 12dp radius) separated by 12dp #121215 gaps
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Group 1: Local Backup & Restore (النسخ الاحتياطي والاستيراد)
                item {
                    GroupedSettingsCard {
                        // Top profile/status header row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(contentAlignment = Alignment.BottomEnd) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(DarkSurfaceVariant)
                                        .border(1.dp, DarkOutline, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = DarkTextSecondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(11.dp)
                                        .clip(CircleShape)
                                        .background(ForestEmerald)
                                        .border(1.5.dp, DarkSurface, CircleShape)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isArabic) "النسخ الاحتياطي والاستيراد" else "Backup & Restore",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DarkTextPrimary
                                )
                                Text(
                                    text = if (isArabic) {
                                        "النسخ الاحتياطي المحلي مفعل ($defaultBackupFileName)"
                                    } else {
                                        "Local JSON Backup Active ($defaultBackupFileName)"
                                    },
                                    fontSize = 11.sp,
                                    color = DarkTextSecondary
                                )
                            }
                        }

                        HorizontalDivider(color = DarkOutline, thickness = 0.8.dp)

                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 1. Export Backup (تصدير نسخة احتياطية)
                            Button(
                                onClick = { triggerShareBackupSheet() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CoralPrimary,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("export_json_button")
                            ) {
                                Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Export Backup (تصدير نسخة احتياطية)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }

                            // Direct Save to Device Downloads option
                            OutlinedButton(
                                onClick = {
                                    exportSaveLauncher.launch(FocusRepository.generateBackupFileName())
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = DarkSurfaceVariant,
                                    contentColor = DarkTextPrimary
                                ),
                                border = BorderStroke(1.dp, DarkOutline),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("save_backup_file_button")
                            ) {
                                Icon(Icons.Default.SaveAlt, null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isArabic) {
                                        "حفظ ملف النسخة في الجهاز ($defaultBackupFileName)"
                                    } else {
                                        "Save Backup File to Device ($defaultBackupFileName)"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }

                            // 2. Import Backup (استيراد نسخة احتياطية)
                            OutlinedButton(
                                onClick = {
                                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = DarkSurfaceVariant,
                                    contentColor = DarkTextPrimary
                                ),
                                border = BorderStroke(1.dp, CoralPrimary.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("import_json_button")
                            ) {
                                Icon(Icons.Default.Upload, null, tint = CoralPrimary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Import Backup (استيراد نسخة احتياطية)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkTextPrimary,
                                    maxLines = 1
                                )
                            }

                            // Reset Demo / Local Data
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onResetDemoData()
                                        backupIsError = false
                                        backupFeedback = if (isArabic) {
                                            "تمت إعادة ضبط البيانات بنجاح."
                                        } else {
                                            "Data reset completed successfully."
                                        }
                                    }
                                    .padding(vertical = 6.dp)
                                    .testTag("reset_demo_button"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.RestartAlt,
                                    null,
                                    tint = DarkTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = s.resetDemoData,
                                    fontSize = 12.sp,
                                    color = DarkTextSecondary
                                )
                            }

                            if (backupFeedback != null) {
                                val bannerColor = if (backupIsError) PriorityHighColor else ForestEmerald
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = bannerColor.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, bannerColor.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("backup_feedback_banner")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = bannerColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = backupFeedback!!,
                                            fontSize = 12.sp,
                                            color = bannerColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Group 2: مؤقت بومودورو + إدارة المشاريع
                item {
                    GroupedSettingsCard {
                        SettingsChevronRow(
                            title = if (isArabic) "مؤقت بومودورو" else s.pomodoroSettings,
                            valueText = "${settings.pomodoroMinutes} ${s.minutesShort}",
                            onClick = { expandPomodoroSection = !expandPomodoroSection }
                        )

                        AnimatedVisibility(visible = expandPomodoroSection) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DarkBackground.copy(alpha = 0.45f))
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
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
                                HorizontalDivider(color = DarkOutline)
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

                        HorizontalDivider(color = DarkOutline, thickness = 0.8.dp)

                        SettingsChevronRow(
                            title = if (isArabic) "إدارة المشاريع" else "Manage Projects",
                            valueText = "${uiState.data.projects.size}",
                            onClick = { expandProjectsSection = !expandProjectsSection }
                        )

                        AnimatedVisibility(visible = expandProjectsSection) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DarkBackground.copy(alpha = 0.45f))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                uiState.data.projects.forEach { proj ->
                                    val taskCount = uiState.data.tasks.count { it.projectId == proj.id && !it.isCompleted }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(parseHexColor(proj.colorHex))
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = proj.name,
                                            fontSize = 13.sp,
                                            color = DarkTextPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "$taskCount",
                                            fontSize = 12.sp,
                                            color = DarkTextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Group 3: عام, المظهر, أذونات المؤقت في الخلفية, دليل المستخدم
                item {
                    GroupedSettingsCard {
                        // عام (General & Notifications)
                        SettingsChevronRow(
                            title = if (isArabic) "عام" else "General",
                            onClick = { expandGeneralSection = !expandGeneralSection }
                        )

                        AnimatedVisibility(visible = expandGeneralSection) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DarkBackground.copy(alpha = 0.45f))
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SettingsSwitchWithSubtitleRow(
                                    label = s.enableTaskRemindersLabel,
                                    subtitle = s.enableTaskRemindersDesc,
                                    checked = settings.enableTaskReminders,
                                    onCheckedChange = { enabled ->
                                        onUpdateSettings { it.copy(enableTaskReminders = enabled) }
                                    },
                                    testTag = "toggle_task_reminders"
                                )
                                SettingsSwitchWithSubtitleRow(
                                    label = s.persistentTimerNotificationLabel,
                                    subtitle = s.persistentTimerNotificationDesc,
                                    checked = settings.persistentTimerNotification,
                                    onCheckedChange = { enabled ->
                                        onUpdateSettings { it.copy(persistentTimerNotification = enabled) }
                                    },
                                    testTag = "toggle_persistent_timer_notification"
                                )
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

                                HorizontalDivider(color = DarkOutline)

                                PermissionStatusRow(
                                    label = s.notificationsPermissionLabel,
                                    isGranted = permSnapshot.notificationsGranted,
                                    s = s,
                                    onClick = {
                                        if (!permSnapshot.notificationsGranted) {
                                            onRequestPermissions()
                                        }
                                        SystemPermissionsHelper.openAppNotificationSettings(context)
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

                                HorizontalDivider(color = DarkOutline)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = s.languageLabel,
                                        fontSize = 13.sp,
                                        color = DarkTextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(AppLanguage.AR to s.arabicLang, AppLanguage.EN to s.englishLang).forEach { (lang, lbl) ->
                                            val active = settings.language == lang
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (active) CoralPrimary else DarkSurfaceVariant,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { onUpdateSettings { it.copy(language = lang) } }
                                            ) {
                                                Text(
                                                    text = lbl,
                                                    fontSize = 12.sp,
                                                    color = if (active) Color.White else DarkTextPrimary,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = DarkOutline, thickness = 0.8.dp)

                        // المظهر (Appearance)
                        SettingsChevronRow(
                            title = if (isArabic) "المظهر" else "Appearance",
                            valueText = if (settings.darkTheme) (if (isArabic) "داكن" else "Dark") else (if (isArabic) "فاتح" else "Light"),
                            onClick = { expandAppearanceSection = !expandAppearanceSection }
                        )

                        AnimatedVisibility(visible = expandAppearanceSection) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DarkBackground.copy(alpha = 0.45f))
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                SettingsSwitchRow(
                                    label = s.darkMode,
                                    checked = settings.darkTheme,
                                    onCheckedChange = { c -> onUpdateSettings { it.copy(darkTheme = c) } }
                                )
                            }
                        }

                        HorizontalDivider(color = DarkOutline, thickness = 0.8.dp)

                        // أذونات المؤقت في الخلفية (Background Timer Permissions)
                        SettingsChevronRow(
                            title = if (isArabic) "أذونات المؤقت في الخلفية" else "Background Timer Permissions",
                            onClick = onOpenBackgroundPermissionsModal,
                            modifier = Modifier.testTag("open_background_permissions_button")
                        )

                        HorizontalDivider(color = DarkOutline, thickness = 0.8.dp)

                        // دليل المستخدم (User Guide)
                        SettingsChevronRow(
                            title = if (isArabic) "دليل المستخدم" else "User Guide",
                            onClick = { showUserGuideDialog = true }
                        )
                    }
                }

                // Group 4: الإصدار (18.0 on left)
                item {
                    GroupedSettingsCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 15.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isArabic) "الإصدار" else "Version",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = DarkTextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "18.0",
                                fontSize = 13.sp,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = DarkTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }

    // Confirmation Dialog for Import Backup: Merge vs Replace
    pendingImportState?.let { importedState ->
        AlertDialog(
            onDismissRequest = { pendingImportState = null },
            shape = RoundedCornerShape(14.dp),
            containerColor = DarkSurface,
            title = {
                Text(
                    text = if (isArabic) {
                        "استيراد النسخة الاحتياطية (Import Backup)"
                    } else {
                        "Import Backup (استيراد نسخة احتياطية)"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (isArabic) {
                            "يحتوي ملف النسخة الاحتياطية على ${importedState.tasks.size} مهمة، و ${importedState.projects.size} مشروع، و ${importedState.focusRecords.size} سجل تركيز.\nكيف ترغب في استعادة البيانات؟"
                        } else {
                            "This backup contains ${importedState.tasks.size} tasks, ${importedState.projects.size} projects, and ${importedState.focusRecords.size} focus records.\nHow would you like to restore the data?"
                        },
                        fontSize = 13.sp,
                        color = DarkTextSecondary
                    )

                    // Option 1: Merge (دمج مع المهام الحالية)
                    Button(
                        onClick = {
                            onImportBackupState(importedState, BackupImportMode.MERGE)
                            pendingImportState = null
                            backupIsError = false
                            backupFeedback = if (isArabic) {
                                "تم دمج النسخة الاحتياطية مع المهام الحالية بنجاح!"
                            } else {
                                "Backup merged with current tasks successfully!"
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CoralPrimary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("confirm_import_merge_button")
                    ) {
                        Icon(Icons.Default.MergeType, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Merge (دمج مع المهام الحالية)",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Option 2: Replace (استبدال الكل)
                    OutlinedButton(
                        onClick = {
                            onImportBackupState(importedState, BackupImportMode.REPLACE)
                            pendingImportState = null
                            backupIsError = false
                            backupFeedback = if (isArabic) {
                                "تم استبدال البيانات واستعادة النسخة الاحتياطية بنجاح!"
                            } else {
                                "All data replaced from backup successfully!"
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = DarkSurfaceVariant,
                            contentColor = DarkTextPrimary
                        ),
                        border = BorderStroke(1.dp, DarkOutline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("confirm_import_replace_button")
                    ) {
                        Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Replace (استبدال الكل)",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { pendingImportState = null },
                    modifier = Modifier.testTag("cancel_import_dialog_button")
                ) {
                    Text(s.cancel, color = DarkTextSecondary)
                }
            }
        )
    }

    if (showUserGuideDialog) {
        AlertDialog(
            onDismissRequest = { showUserGuideDialog = false },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(14.dp),
            title = {
                Text(
                    text = if (isArabic) "دليل المستخدم" else "User Guide",
                    color = DarkTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isArabic) {
                            "1. تقنية بومودورو: قسّم عملك إلى جلسات تركيز (25 دقيقة) تليها استراحة قصيرة (5 دقائق).\n" +
                                "2. إدارة المهام: نظّم مهامك في قوائم اليوم، غدًا، هذا الأسبوع، أو داخل مشاريع ومجلدات مخصصة.\n" +
                                "3. التقويم والإحصائيات: تابع المهام المجدولة والمكتملة بالساعة وراقب تقدمك اليومي والأسبوعي.\n" +
                                "4. النسخ الاحتياطي المحلي: صدّر ملف JSON في أي وقت لحفظ نسخة احتياطية كاملة أو استيرادها مع خيار الدمج أو الاستبدال."
                        } else {
                            "1. Pomodoro Technique: Break work into 25-minute focus sessions followed by 5-minute breaks.\n" +
                                "2. Task Management: Organize tasks into Today, Tomorrow, This Week, or custom Projects & Folders.\n" +
                                "3. Calendar & Statistics: Track scheduled and completed tasks hourly and monitor daily goals.\n" +
                                "4. Local Backup: Export a complete JSON backup anytime and import with Merge or Replace."
                        },
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = DarkTextSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showUserGuideDialog = false }) {
                    Text(if (isArabic) "حسناً" else "Got it", color = CoralPrimary)
                }
            }
        )
    }
}

@Composable
private fun GroupedSettingsCard(
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, DarkOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun SettingsChevronRow(
    title: String,
    valueText: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = DarkTextPrimary,
            modifier = Modifier.weight(1f)
        )
        if (valueText != null) {
            Text(
                text = valueText,
                fontSize = 12.sp,
                color = DarkTextSecondary,
                modifier = Modifier.padding(end = 6.dp)
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = DarkTextSecondary,
            modifier = Modifier.size(20.dp)
        )
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
        color = DarkBackground
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
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("close_background_permissions_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = s.cancel,
                        tint = DarkTextPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Icon(Icons.Default.Security, null, tint = CoralPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = s.backgroundTimerPermissionsTitle,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
            HorizontalDivider(color = DarkOutline)

            LazyColumn(
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = s.backgroundTimerPermissionsSubtitle,
                        fontSize = 13.sp,
                        color = DarkTextSecondary
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

                // Card 2: Allow auto-startup
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

                // Card 5: Lock in recent apps
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurface,
                        border = BorderStroke(
                            1.dp,
                            if (settings.lockedInRecentsConfirmed) ForestEmerald else DarkOutline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onToggleLockedInRecents(!settings.lockedInRecentsConfirmed) }
                            .testTag("card_lock_in_recent_apps")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(ForestEmerald.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Lock, null, tint = ForestEmerald)
                                }
                                Text(
                                    text = s.cardLockInRecentsTitle,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkTextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Text(
                                text = s.cardLockInRecentsDesc,
                                fontSize = 12.sp,
                                color = DarkTextSecondary
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = s.confirmedLockedLabel,
                                    fontSize = 13.sp,
                                    color = if (settings.lockedInRecentsConfirmed) ForestEmerald else DarkTextSecondary,
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
        shape = RoundedCornerShape(12.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, DarkOutline),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = iconTint)
                }
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (statusText != null) {
                    StatusBadgePill(text = statusText, enabled = statusEnabled)
                }
            }
            Text(
                text = description,
                fontSize = 12.sp,
                color = DarkTextSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = actionLabel,
                    fontSize = 12.sp,
                    color = CoralPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = CoralPrimary,
                    modifier = Modifier.size(14.dp)
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
        color = DarkSurfaceVariant,
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
                fontSize = 13.sp,
                color = DarkTextPrimary,
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
            fontSize = 11.sp,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
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
            Text(label, fontSize = 13.sp, color = DarkTextPrimary)
            Text(
                text = "$value $unit".trim(),
                fontSize = 13.sp,
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
            fontSize = 13.sp,
            color = DarkTextPrimary,
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
                fontSize = 13.sp,
                color = DarkTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = DarkTextSecondary
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
