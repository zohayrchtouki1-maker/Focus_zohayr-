package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.i18n.Strings
import com.example.i18n.label
import com.example.model.AppLanguage
import com.example.model.FolderItem
import com.example.model.ProjectItem
import com.example.model.SmartListType
import com.example.ui.theme.CatCalendarTeal
import com.example.ui.theme.CatCompletedGray
import com.example.ui.theme.CatPlannedBlue
import com.example.ui.theme.CatTasksInboxBlue
import com.example.ui.theme.CatTodayGreen
import com.example.ui.theme.CatTomorrowOrange
import com.example.ui.theme.CatWeekPurple
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.DarkIconMuted
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.ForestEmerald
import com.example.ui.theme.PriorityMediumColor
import com.example.ui.theme.ProjectColorPalette
import com.example.viewmodel.MainUiState
import com.example.viewmodel.NavDestination

private val HomeSmartListOrder = listOf(
    SmartListType.TODAY,
    SmartListType.TOMORROW,
    SmartListType.UPCOMING,
    SmartListType.SOMEDAY,
    SmartListType.EVENTS,
    SmartListType.COMPLETED,
    SmartListType.INBOX
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SidebarPanel(
    uiState: MainUiState,
    s: Strings,
    onSelectDestination: (NavDestination) -> Unit,
    onOpenReports: () -> Unit,
    onOpenForest: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddFolder: (String) -> Unit,
    onRenameFolder: (String, String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onToggleFolderExpanded: (String) -> Unit,
    onSaveProject: (String?, String, String, String?) -> Unit,
    onToggleArchiveProject: (String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onAddTag: (String, String) -> Unit,
    onDeleteTag: (String) -> Unit,
    onOpenCalendar: (() -> Unit)? = null,
    onOpenTomatoAi: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showAddFolderDialog by remember { mutableStateOf(false) }
    var editingFolder by remember { mutableStateOf<FolderItem?>(null) }
    var showProjectDialog by remember { mutableStateOf(false) }
    var editingProject by remember { mutableStateOf<ProjectItem?>(null) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val data = uiState.data
    val isArabic = data.settings.language == AppLanguage.AR
    val primaryTextColor = if (data.settings.darkTheme) DarkTextPrimary else MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = if (data.settings.darkTheme) DarkTextSecondary else MaterialTheme.colorScheme.onSurfaceVariant
    val topIconTint = if (data.settings.darkTheme) DarkIconMuted else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Top Bar (RTL):
        // Right side (Start in RTL): circular avatar (40dp) with small emerald status dot next to "النسخ الاحتياطي المحلي مفعل"
        // Left side (End in RTL): minimal monochrome outline icons (22dp, #9E9EA9) for AI, Statistics, Forest, and Settings
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenSettings)
                    .padding(vertical = 4.dp)
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = topIconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(ForestEmerald)
                            .border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape)
                    )
                }

                Column {
                    Text(
                        text = if (isArabic) "النسخ الاحتياطي المحلي مفعل" else s.localSyncReady,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = primaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onOpenTomatoAi,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("nav_tomato_ai_button")
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 23.dp, height = 18.dp)
                            .border(1.4.dp, topIconTint, RoundedCornerShape(5.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "AI",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = topIconTint,
                            lineHeight = 10.sp
                        )
                    }
                }
                IconButton(
                    onClick = onOpenReports,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("nav_reports_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BarChart,
                        contentDescription = s.reports,
                        tint = topIconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(
                    onClick = onOpenForest,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("nav_forest_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Park,
                        contentDescription = s.forest,
                        tint = topIconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("nav_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = s.settings,
                        tint = topIconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Vertical Category List (14dp row padding)
        HomeSmartListOrder.forEach { smartType ->
            val isSelected = uiState.destination == NavDestination.SmartList(smartType)
            val (count, estMins) = TaskFilterUtils.smartListSummary(
                data.tasks,
                smartType,
                data.settings
            )
            val (icon, iconTint) = smartListVisual(smartType)
            val formattedMetric = formatHomeRowSummary(estMins, count, isArabic)

            SidebarNavRow(
                title = smartType.label(s),
                icon = icon,
                iconTint = iconTint,
                summaryText = formattedMetric,
                selected = isSelected,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
                onClick = {
                    if (smartType == SmartListType.EVENTS && onOpenCalendar != null) {
                        onOpenCalendar()
                    } else {
                        onSelectDestination(NavDestination.SmartList(smartType))
                    }
                },
                modifier = Modifier.testTag("smart_list_${smartType.name.lowercase()}")
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
        Spacer(modifier = Modifier.height(6.dp))

        // Folders with nested Projects
        data.folders.forEach { folder ->
            val folderProjects = data.projects.filter { it.folderId == folder.id && !it.isArchived }
            var folderMenuOpen by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onToggleFolderExpanded(folder.id) }
                    .padding(horizontal = 6.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = PriorityMediumColor
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = folder.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = if (folder.isExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = secondaryTextColor
                )
                Box {
                    IconButton(
                        onClick = { folderMenuOpen = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = secondaryTextColor
                        )
                    }
                    DropdownMenu(
                        expanded = folderMenuOpen,
                        onDismissRequest = { folderMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(s.edit) },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = {
                                folderMenuOpen = false
                                editingFolder = folder
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(s.delete) },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = {
                                folderMenuOpen = false
                                onDeleteFolder(folder.id)
                            }
                        )
                    }
                }
            }

            if (folder.isExpanded) {
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    folderProjects.forEach { project ->
                        val activeTasksForProj = data.tasks.filter { it.projectId == project.id && !it.isCompleted }
                        val projMins = activeTasksForProj.sumOf {
                            val rem = (it.estimatedPomodoros - it.completedPomodoros).coerceAtLeast(0)
                            rem * (it.customPomodoroMinutes ?: data.settings.pomodoroMinutes)
                        }
                        ProjectSidebarRow(
                            project = project,
                            summaryText = formatHomeRowSummary(projMins, activeTasksForProj.size, isArabic),
                            selected = uiState.destination == NavDestination.Project(project.id),
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor,
                            s = s,
                            onSelect = { onSelectDestination(NavDestination.Project(project.id)) },
                            onEdit = {
                                editingProject = project
                                showProjectDialog = true
                            },
                            onArchiveToggle = { onToggleArchiveProject(project.id) },
                            onDelete = { onDeleteProject(project.id) }
                        )
                    }
                }
            }
        }

        // Root (unfoldered) Projects
        val rootProjects = data.projects.filter { it.folderId == null && !it.isArchived }
        rootProjects.forEach { project ->
            val activeTasksForProj = data.tasks.filter { it.projectId == project.id && !it.isCompleted }
            val projMins = activeTasksForProj.sumOf {
                val rem = (it.estimatedPomodoros - it.completedPomodoros).coerceAtLeast(0)
                rem * (it.customPomodoroMinutes ?: data.settings.pomodoroMinutes)
            }
            ProjectSidebarRow(
                project = project,
                summaryText = formatHomeRowSummary(projMins, activeTasksForProj.size, isArabic),
                selected = uiState.destination == NavDestination.Project(project.id),
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
                s = s,
                onSelect = { onSelectDestination(NavDestination.Project(project.id)) },
                onEdit = {
                    editingProject = project
                    showProjectDialog = true
                },
                onArchiveToggle = { onToggleArchiveProject(project.id) },
                onDelete = { onDeleteProject(project.id) }
            )
        }

        // Tags list pills if user has tags
        if (data.tags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                data.tags.forEach { tag ->
                    val isSelected = uiState.destination == NavDestination.Tag(tag.id)
                    val tagColor = parseHexColor(tag.colorHex)
                    val count = data.tasks.count { !it.isCompleted && it.tagIds.contains(tag.id) }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) tagColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (isSelected) tagColor else MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectDestination(NavDestination.Tag(tag.id)) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "#${tag.name}",
                                fontSize = 12.sp,
                                color = tagColor,
                                fontWeight = FontWeight.Medium
                            )
                            if (count > 0) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = count.toString(),
                                    fontSize = 11.sp,
                                    color = secondaryTextColor
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = s.delete,
                                tint = secondaryTextColor.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .size(13.dp)
                                    .clickable { onDeleteTag(tag.id) }
                            )
                        }
                    }
                }
            }
        }

        // Archived Projects Accordion
        val archivedProjects = data.projects.filter { it.isArchived }
        if (archivedProjects.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { showArchived = !showArchived }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Archive,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = secondaryTextColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${s.archivedProjects} (${archivedProjects.size})",
                    fontSize = 13.sp,
                    color = secondaryTextColor,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (showArchived) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = secondaryTextColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            if (showArchived) {
                archivedProjects.forEach { project ->
                    val activeCount = data.tasks.count { it.projectId == project.id && !it.isCompleted }
                    ProjectSidebarRow(
                        project = project,
                        summaryText = activeCount.toString(),
                        selected = uiState.destination == NavDestination.Project(project.id),
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor,
                        s = s,
                        onSelect = { onSelectDestination(NavDestination.Project(project.id)) },
                        onEdit = {
                            editingProject = project
                            showProjectDialog = true
                        },
                        onArchiveToggle = { onToggleArchiveProject(project.id) },
                        onDelete = { onDeleteProject(project.id) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Bottom Action Row:
        // Right side (Start in RTL): "+ إنشاء مشروع" in Coral Red #E05246
        // Left side (End in RTL): Add Folder and Add Tag Coral Red outline icons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        editingProject = null
                        showProjectDialog = true
                    }
                    .padding(vertical = 8.dp, horizontal = 4.dp)
                    .testTag("add_project_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = s.newProject,
                    tint = CoralPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isArabic) "إنشاء مشروع" else s.newProject,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = CoralPrimary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { showAddTagDialog = true },
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("add_tag_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocalOffer,
                        contentDescription = s.newTag,
                        tint = CoralPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = { showAddFolderDialog = true },
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("add_folder_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CreateNewFolder,
                        contentDescription = s.newFolder,
                        tint = CoralPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(96.dp))
    }

    // Dialogs
    if (showAddFolderDialog || editingFolder != null) {
        SimpleNameDialog(
            title = if (editingFolder == null) s.newFolder else s.edit,
            label = s.folderName,
            initialValue = editingFolder?.name ?: "",
            s = s,
            onDismiss = {
                showAddFolderDialog = false
                editingFolder = null
            },
            onConfirm = { name ->
                val target = editingFolder
                if (target == null) onAddFolder(name) else onRenameFolder(target.id, name)
                showAddFolderDialog = false
                editingFolder = null
            }
        )
    }

    if (showProjectDialog) {
        ProjectEditorDialog(
            existing = editingProject,
            folders = data.folders,
            s = s,
            onDismiss = {
                showProjectDialog = false
                editingProject = null
            },
            onConfirm = { name, colorHex, folderId ->
                onSaveProject(editingProject?.id, name, colorHex, folderId)
                showProjectDialog = false
                editingProject = null
            }
        )
    }

    if (showAddTagDialog) {
        TagEditorDialog(
            s = s,
            onDismiss = { showAddTagDialog = false },
            onConfirm = { name, colorHex ->
                onAddTag(name, colorHex)
                showAddTagDialog = false
            }
        )
    }
}

private fun formatHomeRowSummary(estMins: Int, count: Int, isArabic: Boolean): String {
    val timeStr = if (estMins >= 60 && estMins % 60 == 0) {
        val h = estMins / 60
        if (isArabic) "$h س" else "${h}h"
    } else if (estMins > 60) {
        val h = estMins / 60
        val m = estMins % 60
        if (isArabic) "$h س $m د" else "${h}h ${m}m"
    } else {
        if (isArabic) "$estMins د" else "${estMins}m"
    }
    return "$timeStr   $count"
}

@Composable
private fun SidebarNavRow(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    summaryText: String,
    selected: Boolean,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f) else Color.Transparent
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = primaryTextColor,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = summaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            color = secondaryTextColor
        )
    }
}

@Composable
private fun ProjectSidebarRow(
    project: ProjectItem,
    summaryText: String,
    selected: Boolean,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    s: Strings,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onArchiveToggle: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val projColor = parseHexColor(project.colorHex)
    val bg = if (selected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onSelect)
            .padding(horizontal = 6.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(projColor)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = project.name,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = primaryTextColor,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = summaryText,
            fontSize = 12.sp,
            color = secondaryTextColor,
            modifier = Modifier.padding(end = 2.dp)
        )
        Box {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = secondaryTextColor
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(s.edit) },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = {
                        menuOpen = false
                        onEdit()
                    }
                )
                DropdownMenuItem(
                    text = { Text(if (project.isArchived) s.unarchive else s.archive) },
                    leadingIcon = {
                        Icon(if (project.isArchived) Icons.Default.Unarchive else Icons.Default.Archive, null)
                    },
                    onClick = {
                        menuOpen = false
                        onArchiveToggle()
                    }
                )
                DropdownMenuItem(
                    text = { Text(s.delete) },
                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    }
                )
            }
        }
    }
}

private fun smartListVisual(type: SmartListType): Pair<ImageVector, Color> = when (type) {
    SmartListType.TODAY -> Icons.Outlined.WbSunny to CatTodayGreen
    SmartListType.TOMORROW -> Icons.Outlined.WbTwilight to CatTomorrowOrange
    SmartListType.UPCOMING -> Icons.Outlined.DateRange to CatWeekPurple
    SmartListType.SOMEDAY -> Icons.Outlined.EventAvailable to CatPlannedBlue
    SmartListType.EVENTS -> Icons.Outlined.GridView to CatCalendarTeal
    SmartListType.COMPLETED -> Icons.Outlined.CheckCircle to CatCompletedGray
    SmartListType.INBOX -> Icons.Outlined.Inbox to CatTasksInboxBlue
}

@Composable
private fun SimpleNameDialog(
    title: String,
    label: String,
    initialValue: String,
    s: Strings,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(s.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.cancel) }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectEditorDialog(
    existing: ProjectItem?,
    folders: List<FolderItem>,
    s: Strings,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String?) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var selectedColor by remember { mutableStateOf(existing?.colorHex ?: ProjectColorPalette.first()) }
    var selectedFolderId by remember { mutableStateOf(existing?.folderId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) s.newProject else s.edit) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(s.projectName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(s.selectColor, style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProjectColorPalette.forEach { hex ->
                        val color = parseHexColor(hex)
                        val isChosen = selectedColor == hex
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isChosen) 2.5.dp else 0.dp,
                                    color = if (isChosen) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isChosen) {
                                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                if (folders.isNotEmpty()) {
                    Text(s.selectFolder, style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val noneSelected = selectedFolderId == null
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (noneSelected) CoralPrimary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedFolderId = null }
                        ) {
                            Text(
                                text = s.noFolder,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        folders.forEach { folder ->
                            val isSel = selectedFolderId == folder.id
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) CoralPrimary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedFolderId = folder.id }
                            ) {
                                Text(
                                    text = folder.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, selectedColor, selectedFolderId) },
                enabled = name.isNotBlank()
            ) {
                Text(s.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.cancel) }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagEditorDialog(
    s: Strings,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(ProjectColorPalette[1]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.newTag) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(s.tagName) },
                    prefix = { Text("#") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(s.selectColor, style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProjectColorPalette.forEach { hex ->
                        val color = parseHexColor(hex)
                        val isChosen = selectedColor == hex
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isChosen) 2.5.dp else 0.dp,
                                    color = if (isChosen) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isChosen) {
                                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, selectedColor) }, enabled = name.isNotBlank()) {
                Text(s.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.cancel) }
        }
    )
}
