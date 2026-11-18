package com.example.ui.components

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.blocker.FocusDeviceAdminReceiver
import com.example.blocker.InstalledAppInfo
import com.example.blocker.StrictAppBlockerManager
import com.example.model.TimerPhase
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.ForestEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.viewmodel.MainUiState
import kotlinx.coroutines.launch

@Composable
fun StrictBlockerSetupDialog(
    uiState: MainUiState,
    onDismiss: () -> Unit,
    onSetStrictBlockerEnabled: (Boolean) -> Unit,
    onToggleWhitelistedApp: (String) -> Unit,
    onRefreshBlockerPermissions: () -> Unit
) {
    BackHandler { onDismiss() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val isFocusSessionActivelyRunning =
        uiState.timer.isRunning && uiState.timer.phase == TimerPhase.FOCUS
    val isStrictEnabled = uiState.data.settings.strictBlockerEnabled || uiState.timer.strictMode
    val whitelistedPackages = uiState.data.settings.whitelistedPackages.toSet()
    val permStatus = uiState.strictBlockerPermissions

    var searchQuery by remember { mutableStateOf("") }
    var installedApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(true) }
    var lockWarningBanner by remember { mutableStateOf<String?>(null) }

    val deviceAdminLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onRefreshBlockerPermissions()
    }

    // Refresh permissions whenever returning from Android Settings / Device Admin screen
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshBlockerPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        onRefreshBlockerPermissions()
        isLoadingApps = true
        installedApps = StrictAppBlockerManager.loadInstalledLaunchableApps(context, forceRefresh = false)
        isLoadingApps = false
    }

    val filteredApps = remember(installedApps, searchQuery, whitelistedPackages) {
        val q = searchQuery.trim()
        val base = if (q.isEmpty()) {
            installedApps
        } else {
            installedApps.filter {
                it.appName.contains(q, ignoreCase = true) ||
                    it.packageName.contains(q, ignoreCase = true)
            }
        }
        base.sortedWith(
            compareByDescending<InstalledAppInfo> { it.packageName in whitelistedPackages }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.appName }
        )
    }

    val isFullyReadyAndArmed = permStatus.isFullyReady && isStrictEnabled

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("strict_blocker_setup_dialog"),
        color = DarkBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Header Bar
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
                        .testTag("close_strict_blocker_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "إغلاق",
                        tint = DarkTextPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = CoralPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🛡️ الحظر الصارم وعزل التطبيقات",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkTextPrimary
                    )
                    Text(
                        text = "منع فتح أي تطبيق خارج القائمة المسموحة أثناء جلسات التركيز",
                        fontSize = 11.sp,
                        color = DarkTextSecondary
                    )
                }

                IconButton(
                    onClick = {
                        onRefreshBlockerPermissions()
                        scope.launch {
                            isLoadingApps = true
                            installedApps = StrictAppBlockerManager.loadInstalledLaunchableApps(
                                context,
                                forceRefresh = true
                            )
                            isLoadingApps = false
                        }
                    },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "تحديث",
                        tint = DarkTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Live Readiness Badge when all permissions are granted and Strict Mode is ON
                if (isFullyReadyAndArmed) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ForestEmerald.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, ForestEmerald.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("strict_blocker_ready_badge")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = ForestEmerald,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "✅ الحماية الصارمة جاهزة ومفعلة بالكامل",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ForestEmerald
                                )
                            }
                        }
                    }
                }

                // Master Strict Blocker Toggle Card
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = DarkSurface,
                        border = BorderStroke(
                            1.dp,
                            if (isStrictEnabled) CoralPrimary.copy(alpha = 0.65f) else DarkOutline
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isStrictEnabled) CoralPrimary.copy(alpha = 0.18f)
                                            else DarkSurfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = if (isStrictEnabled) CoralPrimary else DarkTextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "تفعيل الحظر الصارم أثناء جلسات التركيز",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DarkTextPrimary
                                    )
                                    Text(
                                        text = if (isFocusSessionActivelyRunning && isStrictEnabled) {
                                            "🔒 جلسة التركيز جارية الآن — لا يمكن إيقاف الحظر حتى ينتهي المؤقت"
                                        } else {
                                            "إغلاق أي تطبيق غير موجود في القائمة المسموحة تلقائيًا والعودة للشاشة الرئيسية"
                                        },
                                        fontSize = 11.sp,
                                        color = if (isFocusSessionActivelyRunning && isStrictEnabled) {
                                            CoralPrimary
                                        } else {
                                            DarkTextSecondary
                                        }
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Switch(
                                    checked = isStrictEnabled,
                                    onCheckedChange = { requested ->
                                        if (!requested && isFocusSessionActivelyRunning) {
                                            val msg = "🔒 لا يمكن إيقاف الحظر الصارم أثناء جلسة التركيز الجارية! انتظر حتى تنتهي الجلسة."
                                            lockWarningBanner = msg
                                            try {
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            } catch (_: Exception) {
                                            }
                                        } else {
                                            lockWarningBanner = null
                                            onSetStrictBlockerEnabled(requested)
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = CoralPrimary,
                                        uncheckedThumbColor = DarkTextSecondary,
                                        uncheckedTrackColor = DarkSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag("strict_blocker_master_switch")
                                )
                            }

                            if (lockWarningBanner != null) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = CoralPrimary.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, CoralPrimary.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = CoralPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = lockWarningBanner!!,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CoralPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Card 1: الصلاحية 1: مشرف الجهاز (Device Admin — Anti-Uninstall Protection)
                item {
                    PermissionGateCard(
                        stepTitle = "الصلاحية 1: مشرف الجهاز (Device Admin)",
                        description = "تمنع حذف التطبيق أو تعطيل الحماية الصارمة أثناء سريان جلسات التركيز.",
                        instructions = null,
                        isGranted = permStatus.isDeviceAdminGranted,
                        buttonText = "تفعيل مشرف الجهاز",
                        icon = Icons.Default.AdminPanelSettings,
                        buttonTestTag = "enable_device_admin_button",
                        onActionClick = {
                            val componentName = ComponentName(context, FocusDeviceAdminReceiver::class.java)
                            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                                putExtra(
                                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                    "مطلوب لمنع حذف التطبيق أو تجاوز الحظر الصارم أثناء جلسات التركيز."
                                )
                            }
                            try {
                                deviceAdminLauncher.launch(intent)
                            } catch (_: Exception) {
                                try {
                                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                } catch (_: Exception) {
                                    StrictAppBlockerManager.openDeviceAdminActivation(context)
                                }
                            }
                        }
                    )
                }

                // Card 2: الصلاحية 2: خدمات الوصول (Accessibility Service)
                item {
                    PermissionGateCard(
                        stepTitle = "الصلاحية 2: خدمات الوصول (Accessibility Service)",
                        description = "ترصد فتح أي تطبيق غير مسموح به أثناء جلسة التركيز وتخرجه فورًا إلى الشاشة الرئيسية.",
                        instructions = "خطوات التفعيل (Infinix / XOS وأندرويد):\nالإعدادات > ميزات إضافية / إمكانية الوصول > التطبيقات المثبتة > FocusTomato > تفعيل",
                        isGranted = permStatus.isAccessibilityServiceGranted,
                        buttonText = "تفعيل خدمة الوصول",
                        icon = Icons.Default.AccessibilityNew,
                        buttonTestTag = "enable_accessibility_service_button",
                        onActionClick = {
                            StrictAppBlockerManager.openAccessibilitySettings(context)
                        }
                    )
                }

                // Whitelisted Apps Header & Search Bar
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = DarkSurface,
                        border = BorderStroke(1.dp, DarkOutline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Apps,
                                    contentDescription = null,
                                    tint = CoralPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "عزل التطبيقات المسموحة فقط (Whitelist)",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DarkTextPrimary
                                    )
                                    Text(
                                        text = "جميع تطبيقات الهاتف محظورة افتراضيًا أثناء التركيز باستثناء التطبيقات التي تفعلها هنا",
                                        fontSize = 11.sp,
                                        color = DarkTextSecondary
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = CoralPrimary.copy(alpha = 0.18f),
                                    border = BorderStroke(1.dp, CoralPrimary.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "مسموح: ${whitelistedPackages.size}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CoralPrimary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = {
                                    Text(
                                        text = "ابحث عن تطبيق للسماح به (مثل: واتساب، الحاسبة، PDF)...",
                                        fontSize = 12.sp,
                                        color = DarkTextSecondary
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = DarkTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "مسح البحث",
                                                tint = DarkTextSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = DarkSurfaceVariant,
                                    unfocusedContainerColor = DarkSurfaceVariant,
                                    focusedBorderColor = CoralPrimary,
                                    unfocusedBorderColor = DarkOutline,
                                    focusedTextColor = DarkTextPrimary,
                                    unfocusedTextColor = DarkTextPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("whitelist_search_input")
                            )
                        }
                    }
                }

                // Installed Apps List
                if (isLoadingApps) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(color = CoralPrimary, modifier = Modifier.size(30.dp))
                                Text(
                                    text = "جاري فحص التطبيقات المثبتة على الهاتف...",
                                    fontSize = 12.sp,
                                    color = DarkTextSecondary
                                )
                            }
                        }
                    }
                } else if (filteredApps.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "لا توجد تطبيقات مطابقة للبحث",
                                fontSize = 13.sp,
                                color = DarkTextSecondary
                            )
                        }
                    }
                } else {
                    items(filteredApps, key = { it.packageName }) { appInfo ->
                        val isAllowed = appInfo.packageName in whitelistedPackages
                        WhitelistedAppRow(
                            appInfo = appInfo,
                            isAllowed = isAllowed,
                            onToggle = {
                                if (isFocusSessionActivelyRunning && isStrictEnabled && !isAllowed) {
                                    val msg = "🔒 لا يمكن إضافة تطبيقات جديدة للقائمة المسموحة أثناء سريان جلسة التركيز!"
                                    lockWarningBanner = msg
                                    try {
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    } catch (_: Exception) {
                                    }
                                } else {
                                    onToggleWhitelistedApp(appInfo.packageName)
                                }
                            }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun PermissionGateCard(
    stepTitle: String,
    description: String,
    instructions: String?,
    isGranted: Boolean,
    buttonText: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    buttonTestTag: String,
    onActionClick: () -> Unit
) {
    val accentColor = if (isGranted) ForestEmerald else CoralPrimary
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stepTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = if (isGranted) "مفعّل ✅" else "غير مفعّل ⚠️",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = description,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = DarkTextSecondary
            )

            if (!instructions.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkSurfaceVariant,
                    border = BorderStroke(1.dp, DarkOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = instructions,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        color = DarkTextPrimary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Button(
                onClick = onActionClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGranted) DarkSurfaceVariant else CoralPrimary,
                    contentColor = if (isGranted) ForestEmerald else Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag(buttonTestTag)
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.CheckCircle else icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isGranted) "$buttonText (مفعّل)" else buttonText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun WhitelistedAppRow(
    appInfo: InstalledAppInfo,
    isAllowed: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isAllowed) CoralPrimary.copy(alpha = 0.12f) else DarkSurface,
        border = BorderStroke(
            1.dp,
            if (isAllowed) CoralPrimary.copy(alpha = 0.6f) else DarkOutline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (appInfo.iconBitmap != null) {
                Image(
                    bitmap = appInfo.iconBitmap,
                    contentDescription = appInfo.appName,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = null,
                        tint = CoralPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appInfo.appName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = appInfo.packageName,
                    fontSize = 10.sp,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = DarkTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = isAllowed,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = ForestEmerald,
                    uncheckedThumbColor = DarkTextSecondary,
                    uncheckedTrackColor = DarkSurfaceVariant
                )
            )
        }
    }
}
