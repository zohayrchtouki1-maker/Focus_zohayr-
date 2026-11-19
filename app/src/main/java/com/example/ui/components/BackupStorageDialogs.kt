package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FocusRepository
import com.example.model.PendingBackupRestorePrompt
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.ForestEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun AllFilesAccessPermissionDialog(
    isArabic: Boolean,
    onGrantAccess: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = DarkSurface,
        modifier = Modifier
            .border(1.dp, CoralPrimary.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
            .testTag("all_files_access_dialog"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(CoralPrimary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = CoralPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = if (isArabic) {
                        "إذن الوصول إلى جميع الملفات"
                    } else {
                        "All Files Access Permission"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextPrimary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (isArabic) {
                        "امنح FocusTomato إذن الوصول إلى جميع الملفات لحفظ واستعادة النسخة الاحتياطية بشكل دائم حتى بعد حذف التطبيق وإعادة تثبيته، وبدمج ذكي بدون تكرار للملفات."
                    } else {
                        "Grant FocusTomato All Files Access so your local backup survives app uninstalls and reinstalls with Full-State Smart Merge and zero duplicate files."
                    },
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = DarkTextSecondary
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DarkSurfaceVariant,
                    border = BorderStroke(1.dp, DarkOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = CoralPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${FocusRepository.BACKUP_DISPLAY_PATH}/${FocusRepository.MASTER_BACKUP_FILE_NAME}",
                            fontSize = 11.sp,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = DarkTextPrimary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onGrantAccess,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CoralPrimary,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("grant_all_files_access_button")
            ) {
                Text(
                    text = if (isArabic) "فتح إعدادات الوصول للملفات" else "Grant All Files Access",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("dismiss_all_files_access_button")
            ) {
                Text(
                    text = if (isArabic) "لاحقاً" else "Later",
                    fontSize = 13.sp,
                    color = DarkTextSecondary
                )
            }
        }
    )
}

@Composable
fun FreshInstallAutoRestoreDialog(
    prompt: PendingBackupRestorePrompt,
    isArabic: Boolean,
    onRestoreAll: () -> Unit,
    onStartFresh: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        shape = RoundedCornerShape(16.dp),
        containerColor = DarkSurface,
        modifier = Modifier
            .border(1.2.dp, ForestEmerald.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .testTag("fresh_install_auto_restore_dialog"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(ForestEmerald.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = null,
                        tint = ForestEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = if (isArabic) {
                        "تم العثور على نسخة احتياطية"
                    } else {
                        "Existing Backup Found"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = ForestEmerald
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (isArabic) {
                        "تم العثور على نسخة احتياطية موجودة على جهازك (${prompt.tasksCount} مهام، ${prompt.sessionsCount} جلسات). هل تريد استعادة كل شيء الآن؟"
                    } else {
                        "Existing backup found on your device (${prompt.tasksCount} tasks, ${prompt.sessionsCount} sessions). Restore everything now?"
                    },
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium,
                    color = DarkTextPrimary
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DarkSurfaceVariant,
                    border = BorderStroke(1.dp, DarkOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = ForestEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = prompt.filePath,
                            fontSize = 11.sp,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = DarkTextSecondary
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onRestoreAll,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CoralPrimary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("restore_all_backup_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isArabic) "استعادة الكل (Restore All)" else "Restore All",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedButton(
                        onClick = onStartFresh,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = DarkSurfaceVariant,
                            contentColor = DarkTextPrimary
                        ),
                        border = BorderStroke(1.dp, DarkOutline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("start_fresh_backup_button")
                    ) {
                        Text(
                            text = if (isArabic) "البدء من جديد (Start Fresh)" else "Start Fresh",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        confirmButton = {}
    )
}
