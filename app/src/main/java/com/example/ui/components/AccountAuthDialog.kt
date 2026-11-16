package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.CloudSyncStatus
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
    firebaseProjectId: String,
    language: AppLanguage,
    onDismiss: () -> Unit,
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (email: String, password: String) -> Unit,
    onSignOut: () -> Unit,
    onManualSync: () -> Unit,
    onClearError: () -> Unit
) {
    BackHandler { onDismiss() }

    val isArabic = language == AppLanguage.AR
    var isSignUpMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val primaryRoyalBlue = MaterialTheme.colorScheme.primary
    val cardBg = MaterialTheme.colorScheme.surface
    val borderCol = MaterialTheme.colorScheme.outline
    val secondaryBtnBg = MaterialTheme.colorScheme.secondaryContainer
    val secondaryBtnText = MaterialTheme.colorScheme.onSecondaryContainer

    val (badgeIcon, badgeTint, badgeText) = when (cloudSyncStatus) {
        CloudSyncStatus.SYNCED -> Triple(
            Icons.Default.CloudDone,
            ForestEmerald,
            "Synced • Cloud Firestore ($firebaseProjectId)"
        )
        CloudSyncStatus.SYNCING -> Triple(
            Icons.Default.CloudSync,
            primaryRoyalBlue,
            "Syncing... • Cloud Firestore ($firebaseProjectId)"
        )
        CloudSyncStatus.SYNC_FAILED -> Triple(
            Icons.Default.CloudOff,
            Color(0xFFEF4444),
            "Sync Failed: ${cloudSyncErrorMessage ?: "Unknown error"}"
        )
        CloudSyncStatus.UNAUTHENTICATED -> Triple(
            Icons.Default.CloudQueue,
            primaryRoyalBlue,
            if (isArabic) "سجل الدخول لمزامنة مهامك ومشاريعك عبر جميع أجهزتك ($firebaseProjectId)"
            else "Sign in to sync tasks & stats across devices ($firebaseProjectId)"
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
                                text = when {
                                    currentUserEmail != null -> if (isArabic) "حسابي والمزامنة السحابية" else "My Account & Cloud Sync"
                                    isSignUpMode -> if (isArabic) "إنشاء حساب جديد" else "Create Account"
                                    else -> if (isArabic) "تسجيل الدخول" else "Sign In"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isArabic) {
                                    "مزامنة سحابية فورية عبر Firebase Firestore ($firebaseProjectId)"
                                } else {
                                    "Real-time cloud sync via Firebase Firestore ($firebaseProjectId)"
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
                        // Signed-in Profile View
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
                                            text = if (isArabic) "البريد الإلكتروني المسجل" else "Signed-in Email",
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
                                if (currentUserId != null) {
                                    Text(
                                        text = "Firestore: users/$currentUserId",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        color = primaryRoyalBlue
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
                        // Sign In / Sign Up Mode Toggle Pills
                        Surface(
                            shape = CircleShape,
                            color = secondaryBtnBg,
                            border = BorderStroke(1.dp, borderCol),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp)
                            ) {
                                listOf(
                                    false to (if (isArabic) "تسجيل الدخول" else "Sign In"),
                                    true to (if (isArabic) "إنشاء حساب" else "Create Account")
                                ).forEach { (signUpTab, tabTitle) ->
                                    val selected = isSignUpMode == signUpTab
                                    Surface(
                                        shape = CircleShape,
                                        color = if (selected) primaryRoyalBlue else Color.Transparent,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(CircleShape)
                                            .clickable {
                                                isSignUpMode = signUpTab
                                                onClearError()
                                            }
                                            .testTag(if (signUpTab) "tab_sign_up" else "tab_sign_in")
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(vertical = 10.dp)
                                        ) {
                                            Text(
                                                text = tabTitle,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = if (selected) Color.White else secondaryBtnText
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = email,
                            onValueChange = {
                                email = it
                                if (authErrorMessage != null) onClearError()
                            },
                            label = { Text(if (isArabic) "البريد الإلكتروني" else "Email Address") },
                            leadingIcon = {
                                Icon(Icons.Default.Email, contentDescription = null, tint = primaryRoyalBlue)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_email_input")
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                if (authErrorMessage != null) onClearError()
                            },
                            label = { Text(if (isArabic) "كلمة المرور (6 أحرف على الأقل)" else "Password (min 6 chars)") },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = primaryRoyalBlue)
                            },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_password_input")
                        )

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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onDismiss,
                                enabled = !isAuthLoading,
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = secondaryBtnBg,
                                    contentColor = secondaryBtnText
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("auth_cancel_button")
                            ) {
                                Text(
                                    text = if (isArabic) "إلغاء" else "Cancel",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = {
                                    if (isSignUpMode) {
                                        onSignUp(email, password)
                                    } else {
                                        onSignIn(email, password)
                                    }
                                },
                                enabled = !isAuthLoading,
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = primaryRoyalBlue,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("auth_submit_button")
                            ) {
                                if (isAuthLoading) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Text(
                                        text = if (isSignUpMode) {
                                            if (isArabic) "إنشاء حساب" else "Sign Up"
                                        } else {
                                            if (isArabic) "دخول" else "Sign In"
                                        },
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
    }
}
