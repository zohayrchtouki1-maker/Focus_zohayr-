package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.CloudSyncStatus
import com.example.data.DriveScopes
import com.example.data.FocusRepository
import com.example.model.AppLanguage
import com.example.ui.theme.ForestEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun AccountAuthDialog(
    currentUserEmail: String?,
    currentUserId: String?,
    cloudSyncStatus: CloudSyncStatus,
    cloudSyncErrorMessage: String?,
    isAuthLoading: Boolean,
    authErrorMessage: String?,
    language: AppLanguage,
    onDismiss: () -> Unit,
    onSignInWithGoogle: () -> Unit,
    onSignOut: () -> Unit,
    onManualSync: () -> Unit
) {
    BackHandler { onDismiss() }

    val isArabic = language == AppLanguage.AR
    val primaryRoyalBlue = MaterialTheme.colorScheme.primary
    val cardBg = MaterialTheme.colorScheme.surface
    val borderCol = MaterialTheme.colorScheme.outline
    val secondaryBtnBg = MaterialTheme.colorScheme.secondaryContainer
    val secondaryBtnText = MaterialTheme.colorScheme.onSecondaryContainer

    val (badgeIcon, badgeTint, badgeText) = when (cloudSyncStatus) {
        CloudSyncStatus.SYNCED -> Triple(
            Icons.Default.CloudDone,
            ForestEmerald,
            "Synced • Google Drive appDataFolder (${FocusRepository.DRIVE_BACKUP_FILE_NAME})"
        )
        CloudSyncStatus.SYNCING -> Triple(
            Icons.Default.CloudSync,
            primaryRoyalBlue,
            "Syncing... • Google Drive appDataFolder (${FocusRepository.DRIVE_BACKUP_FILE_NAME})"
        )
        CloudSyncStatus.SYNC_FAILED -> Triple(
            Icons.Default.CloudOff,
            Color(0xFFEF4444),
            "Sync Failed: ${cloudSyncErrorMessage ?: "Unknown error"}"
        )
        CloudSyncStatus.UNAUTHENTICATED -> Triple(
            Icons.Default.CloudQueue,
            primaryRoyalBlue,
            if (isArabic) {
                "سجل الدخول بحساب Google لمزامنة مهامك تلقائياً في Google Drive (appDataFolder)"
            } else {
                "Sign in with Google to sync tasks & sessions to Google Drive (appDataFolder)"
            }
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("account_auth_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = cardBg,
                border = BorderStroke(1.dp, borderCol),
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header row with close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(primaryRoyalBlue.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = primaryRoyalBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (currentUserEmail != null) {
                                    if (isArabic) "حساب Google والمزامنة السحابية" else "Google Account & Drive Sync"
                                } else {
                                    if (isArabic) "تسجيل الدخول عبر Google" else "Sign in with Google"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isArabic) {
                                    "نسخ احتياطي ومزامنة تلقائية في مجلد Google Drive المخفي (appDataFolder)"
                                } else {
                                    "Automatic cloud backup & sync in hidden Google Drive appDataFolder"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("close_auth_dialog_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = if (isArabic) "إغلاق" else "Close",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Cloud Sync Status Badge
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = badgeTint.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, badgeTint.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            if (cloudSyncStatus == CloudSyncStatus.SYNCING) {
                                CircularProgressIndicator(
                                    color = badgeTint,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = badgeIcon,
                                    contentDescription = null,
                                    tint = badgeTint,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeTint,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (currentUserEmail != null) {
                        // Signed-in Google Profile View
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, borderCol),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isArabic) "حساب Google المتصل" else "Connected Google Account",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = currentUserEmail,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Button(
                                        onClick = onManualSync,
                                        enabled = cloudSyncStatus != CloudSyncStatus.SYNCING,
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = primaryRoyalBlue,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier.testTag("manual_cloud_sync_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Sync,
                                            contentDescription = "Sync Now",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Sync Now",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    text = "appDataFolder/${FocusRepository.DRIVE_BACKUP_FILE_NAME}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = primaryRoyalBlue
                                )
                                if (currentUserId != null) {
                                    Text(
                                        text = "Scope: ${DriveScopes.DRIVE_APPDATA}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onDismiss,
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = secondaryBtnBg,
                                    contentColor = secondaryBtnText
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text(
                                    text = if (isArabic) "العودة للمهام" else "Back to Tasks",
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = onSignOut,
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("auth_logout_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isArabic) "تسجيل الخروج" else "Log Out",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        // Google Drive AppData Info Card
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, borderCol),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (isArabic) {
                                        "المزامنة السحابية عبر Google Drive (appDataFolder)"
                                    } else {
                                        "Google Drive AppData Cloud Backup"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isArabic) {
                                        "يتم حفظ جميع المهام والمشاريع والمجلدات وجلسات التركيز والإعدادات تلقائياً في ملف focus_todo_backup.json داخل مساحة التطبيقات الخاصة بحسابك في Google Drive لاستعادتها فورياً عند إعادة تثبيت التطبيق أو فتحه من أي جهاز آخر."
                                    } else {
                                        "All tasks, projects, folders, focus sessions, and settings are automatically saved to focus_todo_backup.json inside your private Google Drive appDataFolder and restored across app reinstalls."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Scope: ${DriveScopes.DRIVE_APPDATA}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = primaryRoyalBlue
                                )
                            }
                        }

                        if (!authErrorMessage.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFEF4444).copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = authErrorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFEF4444),
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }

                        // Primary "Sign in with Google" Button
                        Button(
                            onClick = onSignInWithGoogle,
                            enabled = !isAuthLoading,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryRoyalBlue,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("google_sign_in_button")
                        ) {
                            if (isAuthLoading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Syncing...",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isArabic) {
                                        "تسجيل الدخول باستخدام Google (Sign in with Google)"
                                    } else {
                                        "Sign in with Google"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Button(
                            onClick = onDismiss,
                            enabled = !isAuthLoading,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = secondaryBtnBg,
                                contentColor = secondaryBtnText
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("auth_cancel_button")
                        ) {
                            Text(
                                text = if (isArabic) "إلغاء" else "Cancel",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
