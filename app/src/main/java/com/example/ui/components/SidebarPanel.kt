package com.example.ui.components

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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.WbSunny
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
import com.example.i18n.Strings
import com.example.i18n.formatDuration
import com.example.i18n.label
import com.example.model.FolderItem
import com.example.model.ProjectItem
import com.example.model.SmartListType
import com.example.model.TagItem
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.ForestEmerald
import com.example.ui.theme.PriorityLowColor
import com.example.ui.theme.PriorityMediumColor
import com.example.ui.theme.ProjectColorPalette
import com.example.ui.theme.SunlightGold
import com.example.viewmodel.MainUiState
import com.example.viewmodel.NavDestination

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

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 14.dp)
    ) {
        // Profile / Local Sync Header
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(CoralPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        TomatoIcon(filled = true, size = 22.dp, tint = Color.White)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = s.appTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = ForestEmerald,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = s.localSyncReady,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Top Bar Quick Buttons: Reports, Forest, Settings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SidebarTopActionChip(
                        icon = Icons.Default.BarChart,
                        label = s.reports,
                        tint = PriorityLowColor,
                        onClick = onOpenReports,
                        modifier = Modifier.weight(1f).testTag("nav_reports_button")
                    )
                    SidebarTopActionChip(
                        icon = Icons.Default.Park,
                        label = s.forest,
                        tint = ForestEmerald,
                        onClick = onOpenForest,
                        modifier = Modifier.weight(1f).testTag("nav_forest_button")
                    )
                    SidebarTopActionChip(
                        icon = Icons.Default.Settings,
                        label = s.settings,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onOpenSettings,
                        modifier = Modifier.weight(1f).testTag("nav_settings_button")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Smart Lists Section
        Text(
            text = s.smartLists,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )

        SmartListType.entries.forEach { smartType ->
            val isSelected = uiState.destination == NavDestination.SmartList(smartType)
            val (count, estMins) = TaskFilterUtils.smartListSummary(
                data.tasks,
                smartType,
                data.settings
            )
            val (icon, iconTint) = smartListVisual(smartType)

            SidebarNavRow(
                title = smartType.label(s),
                icon = icon,
                iconTint = iconTint,
                count = count,
                subtitleTime = if (estMins > 0 && smartType != SmartListType.COMPLETED) {
                    formatDuration(estMins, s)
                } else null,
                selected = isSelected,
                onClick = { onSelectDestination(NavDestination.SmartList(smartType)) },
                modifier = Modifier.testTag("smart_list_${smartType.name.lowercase()}")
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(10.dp))

        // Folders & Projects Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = s.foldersAndProjects,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { showAddFolderDialog = true },
                modifier = Modifier.size(36.dp).testTag("add_folder_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CreateNewFolder,
                    contentDescription = s.newFolder,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = {
                    editingProject = null
                    showProjectDialog = true
                },
                modifier = Modifier.size(36.dp).testTag("add_project_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = s.newProject,
                    modifier = Modifier.size(20.dp),
                    tint = CoralPrimary
                )
            }
        }

        // Folders with nested Projects
        data.folders.forEach { folder ->
            val folderProjects = data.projects.filter { it.folderId == folder.id && !it.isArchived }
            var folderMenuOpen by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onToggleFolderExpanded(folder.id) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (folder.isExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = PriorityMediumColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                        val activeCount = data.tasks.count { it.projectId == project.id && !it.isCompleted }
                        ProjectSidebarRow(
                            project = project,
                            activeCount = activeCount,
                            selected = uiState.destination == NavDestination.Project(project.id),
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
            val activeCount = data.tasks.count { it.projectId == project.id && !it.isCompleted }
            ProjectSidebarRow(
                project = project,
                activeCount = activeCount,
                selected = uiState.destination == NavDestination.Project(project.id),
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

        // Archived Projects Accordion
        val archivedProjects = data.projects.filter { it.isArchived }
        if (archivedProjects.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { showArchived = !showArchived }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Archive,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${s.archivedProjects} (${archivedProjects.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (showArchived) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
            if (showArchived) {
                archivedProjects.forEach { project ->
                    ProjectSidebarRow(
                        project = project,
                        activeCount = data.tasks.count { it.projectId == project.id && !it.isCompleted },
                        selected = uiState.destination == NavDestination.Project(project.id),
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

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(10.dp))

        // Tags Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = s.tags,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { showAddTagDialog = true },
                modifier = Modifier.size(34.dp).testTag("add_tag_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = s.newTag,
                    modifier = Modifier.size(18.dp),
                    tint = CoralPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 6.dp)
        ) {
            data.tags.forEach { tag ->
                val isSelected = uiState.destination == NavDestination.Tag(tag.id)
                val tagColor = parseHexColor(tag.colorHex)
                val count = data.tasks.count { !it.isCompleted && it.tagIds.contains(tag.id) }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) tagColor.copy(alpha = 0.22f) else tagColor.copy(alpha = 0.1f),
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, tagColor) else null,
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
                            style = MaterialTheme.typography.labelMedium,
                            color = tagColor,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (count > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = tagColor.copy(alpha = 0.8f)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = s.delete,
                            tint = tagColor.copy(alpha = 0.55f),
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { onDeleteTag(tag.id) }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(80.dp))
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

@Composable
private fun SidebarTopActionChip(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SidebarNavRow(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    count: Int,
    subtitleTime: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else Color.Transparent
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(19.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) CoralPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitleTime != null) {
            Text(
                text = subtitleTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 6.dp)
            )
        }
        if (count > 0) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) CoralPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ProjectSidebarRow(
    project: ProjectItem,
    activeCount: Int,
    selected: Boolean,
    s: Strings,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onArchiveToggle: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val projColor = parseHexColor(project.colorHex)
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(projColor)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = project.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (activeCount > 0) {
            Text(
                text = activeCount.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 2.dp)
            )
        }
        Box {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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
    SmartListType.TODAY -> Icons.Default.Today to ForestEmerald
    SmartListType.TOMORROW -> Icons.Default.WbSunny to SunlightGold
    SmartListType.UPCOMING -> Icons.Default.DateRange to PriorityLowColor
    SmartListType.SOMEDAY -> Icons.Default.AllInclusive to Color(0xFF8B5CF6)
    SmartListType.EVENTS -> Icons.Default.Event to CoralPrimary
    SmartListType.INBOX -> Icons.AutoMirrored.Filled.List to Color(0xFF0EA5E9)
    SmartListType.COMPLETED -> Icons.Default.CheckCircle to Color(0xFF64748B)
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
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { selectedFolderId = null }
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
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { selectedFolderId = folder.id }
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
    var selectedColor by remember { mutableStateOf(ProjectColorPalette[3]) }

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
