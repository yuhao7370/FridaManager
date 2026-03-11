package com.yuhao7370.fridamanager.ui.screens.versions

import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuhao7370.fridamanager.BuildConfig
import com.yuhao7370.fridamanager.R
import com.yuhao7370.fridamanager.model.DownloadTask
import com.yuhao7370.fridamanager.model.DownloadTaskStatus
import com.yuhao7370.fridamanager.model.InstallPhase
import com.yuhao7370.fridamanager.model.InstallProgress
import com.yuhao7370.fridamanager.model.InstalledFridaVersion
import com.yuhao7370.fridamanager.model.RemoteFridaVersion
import com.yuhao7370.fridamanager.ui.components.MiCard
import com.yuhao7370.fridamanager.ui.components.MiConfirmDialog
import com.yuhao7370.fridamanager.ui.components.MiDangerButton
import com.yuhao7370.fridamanager.ui.components.MiEmptyState
import com.yuhao7370.fridamanager.ui.components.MiLoadingState
import com.yuhao7370.fridamanager.ui.components.MiPrimaryButton
import com.yuhao7370.fridamanager.ui.components.MiScaffold
import com.yuhao7370.fridamanager.ui.components.MiSectionHeader
import com.yuhao7370.fridamanager.ui.components.MiSecondaryButton
import com.yuhao7370.fridamanager.ui.components.MiSegmentedControl
import com.yuhao7370.fridamanager.ui.components.MiStatusChip
import com.yuhao7370.fridamanager.ui.components.MiStatusTone
import com.yuhao7370.fridamanager.ui.components.MiToastEffect
import com.yuhao7370.fridamanager.ui.components.MiTopBar
import com.yuhao7370.fridamanager.ui.components.MiValueRow
import com.yuhao7370.fridamanager.ui.LocalHostActivity
import com.yuhao7370.fridamanager.ui.theme.MiTheme
import kotlin.math.roundToInt

private data class RemoteMajorGroup(
    val majorKey: String,
    val releases: List<RemoteFridaVersion>
)

private enum class VersionsModule { LOCAL, REMOTE, DOWNLOADS }

@Composable
fun VersionsScreen(viewModel: VersionsViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = MiTheme.spacing
    val activity = LocalHostActivity.current
    var deletingVersion by remember { mutableStateOf<InstalledFridaVersion?>(null) }
    var selectedModule by rememberSaveable { mutableStateOf(VersionsModule.LOCAL) }
    val expandedMajor = remember { mutableStateMapOf<String, Boolean>() }
    val expandedRelease = remember { mutableStateMapOf<String, Boolean>() }

    val remoteGroups = remember(uiState.remote) { groupRemoteByMajor(uiState.remote) }
    val installedVersions = remember(uiState.installed) { uiState.installed.map { it.version }.toSet() }
    val activeDownloadVersions = remember(uiState.downloads) {
        uiState.downloads.filter { !it.isTerminal }.map { it.version }.toSet()
    }

    MiToastEffect(
        message = uiState.messageRes?.let { stringResource(it) } ?: uiState.message,
        onConsumed = viewModel::dismissMessage
    )

    val launchImportDocument = rememberOpenDocumentLauncher(activity) { uri ->
        if (uri != null) viewModel.import(uri)
    }

    MiScaffold(
        topBar = {
            MiTopBar(
                title = stringResource(R.string.versions_title),
                subtitle = stringResource(
                    R.string.versions_top_subtitle,
                    uiState.abiInfo?.fridaAssetTag ?: stringResource(R.string.common_unknown)
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = spacing.lg,
                end = spacing.lg,
                top = innerPadding.calculateTopPadding() + spacing.sm,
                bottom = innerPadding.calculateBottomPadding() + spacing.xxl
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            item {
                MiCard(tonal = true) {
                    MiSectionHeader(
                        title = stringResource(R.string.versions_module_title),
                        subtitle = stringResource(R.string.versions_module_subtitle)
                    )
                    MiSegmentedControl(
                        options = listOf(
                            stringResource(R.string.versions_module_local),
                            stringResource(R.string.versions_module_remote),
                            stringResource(R.string.versions_module_downloads)
                        ),
                        selectedIndex = when (selectedModule) {
                            VersionsModule.LOCAL -> 0
                            VersionsModule.REMOTE -> 1
                            VersionsModule.DOWNLOADS -> 2
                        },
                        onSelected = { index ->
                            selectedModule = when (index) {
                                0 -> VersionsModule.LOCAL
                                1 -> VersionsModule.REMOTE
                                else -> VersionsModule.DOWNLOADS
                            }
                        }
                    )
                }
            }

            when (selectedModule) {
                VersionsModule.LOCAL -> {
                    item {
                        MiCard {
                            MiSectionHeader(
                                title = stringResource(R.string.versions_local_tools_title),
                                subtitle = stringResource(R.string.versions_local_tools_subtitle)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                            ) {
                                MiSecondaryButton(
                                    text = stringResource(R.string.versions_import_file),
                                    modifier = Modifier.weight(1f),
                                    onClick = launchImportDocument
                                )
                                MiSecondaryButton(
                                    text = stringResource(R.string.versions_refresh_remote),
                                    modifier = Modifier.weight(1f),
                                    onClick = viewModel::refreshRemote
                                )
                            }
                            uiState.installProgress?.let { progress ->
                                InstallProgressBlock(
                                    progress = progress,
                                    busyVersion = uiState.busyVersion,
                                    onCancel = viewModel::cancelInstall
                                )
                            }
                        }
                    }

                    item {
                        MiSectionHeader(
                            title = stringResource(R.string.versions_installed_title),
                            subtitle = stringResource(
                                R.string.versions_installed_subtitle,
                                uiState.installed.size
                            )
                        )
                    }

                    if (uiState.installed.isEmpty()) {
                        item {
                            MiCard {
                                MiEmptyState(
                                    title = stringResource(R.string.versions_empty_installed),
                                    subtitle = stringResource(R.string.versions_empty_installed_hint),
                                    icon = Icons.Rounded.Inventory2
                                )
                            }
                        }
                    } else {
                        items(uiState.installed) { version ->
                            InstalledVersionCard(
                                version = version,
                                isBusy = uiState.busyVersion == version.version,
                                onSwitch = { viewModel.switch(version) },
                                onDelete = { deletingVersion = version }
                            )
                        }
                    }
                }

                VersionsModule.REMOTE -> {
                    item {
                        MiCard {
                            MiSectionHeader(
                                title = stringResource(R.string.versions_remote_tools_title),
                                subtitle = stringResource(R.string.versions_remote_tools_subtitle)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                            ) {
                                MiSecondaryButton(
                                    text = stringResource(R.string.versions_refresh_remote),
                                    modifier = Modifier.weight(1f),
                                    onClick = viewModel::refreshRemote
                                )
                                MiPrimaryButton(
                                    text = stringResource(R.string.versions_quick_download_button),
                                    modifier = Modifier.weight(1f),
                                    onClick = viewModel::quickDownloadByVersionInput,
                                    enabled = uiState.quickVersionInput.isNotBlank() &&
                                        uiState.busyVersion == null
                                )
                            }
                            OutlinedTextField(
                                value = uiState.quickVersionInput,
                                onValueChange = viewModel::onQuickVersionInputChanged,
                                singleLine = true,
                                label = { Text(stringResource(R.string.versions_quick_version_label)) },
                                placeholder = {
                                    Text(stringResource(R.string.versions_quick_version_placeholder))
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Text,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { viewModel.quickDownloadByVersionInput() }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = spacing.sm)
                            )
                        }
                    }

                    item {
                        MiSectionHeader(
                            title = stringResource(R.string.versions_remote_title),
                            subtitle = stringResource(
                                R.string.versions_remote_subtitle,
                                uiState.remote.size
                            )
                        )
                    }

                    if (uiState.loadingRemote) {
                        item {
                            MiCard {
                                MiLoadingState(
                                    label = if (BuildConfig.DEBUG) {
                                        uiState.loadingRemoteDebugMessage ?: stringResource(R.string.versions_loading_remote)
                                    } else {
                                        stringResource(R.string.versions_loading_remote)
                                    }
                                )
                            }
                        }
                    } else if (remoteGroups.isEmpty()) {
                        item {
                            MiCard {
                                MiEmptyState(
                                    title = stringResource(R.string.versions_empty_remote),
                                    subtitle = stringResource(R.string.versions_empty_remote_hint),
                                    icon = Icons.Rounded.Sync
                                )
                            }
                        }
                    } else {
                        items(remoteGroups) { group ->
                            val isMajorExpanded = expandedMajor[group.majorKey] ?: false
                            RemoteMajorGroupCard(
                                group = group,
                                currentAbiTag = uiState.abiInfo?.fridaAssetTag,
                                installedVersions = installedVersions,
                                busyVersion = activeDownloadVersions,
                                majorExpanded = isMajorExpanded,
                                onToggleMajor = {
                                    expandedMajor[group.majorKey] = !isMajorExpanded
                                },
                                isReleaseExpanded = { version ->
                                    expandedRelease["${group.majorKey}:$version"] ?: false
                                },
                                onToggleRelease = { version ->
                                    val key = "${group.majorKey}:$version"
                                    expandedRelease[key] = !(expandedRelease[key] ?: false)
                                },
                                onInstall = { release -> viewModel.download(release) }
                            )
                        }
                    }
                }

                VersionsModule.DOWNLOADS -> {
                    item {
                        MiCard {
                            MiSectionHeader(
                                title = stringResource(R.string.versions_downloads_title),
                                subtitle = stringResource(
                                    R.string.versions_downloads_subtitle,
                                    uiState.downloads.count { !it.isTerminal },
                                    uiState.downloads.size
                                )
                            )
                            MiSecondaryButton(
                                text = stringResource(R.string.versions_downloads_clear_finished),
                                onClick = viewModel::clearFinishedDownloadTasks,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    if (uiState.downloads.isEmpty()) {
                        item {
                            MiCard {
                                MiEmptyState(
                                    title = stringResource(R.string.versions_downloads_empty),
                                    subtitle = stringResource(R.string.versions_downloads_empty_hint),
                                    icon = Icons.Rounded.Download
                                )
                            }
                        }
                    } else {
                        items(uiState.downloads) { task ->
                            DownloadTaskCard(
                                task = task,
                                onCancel = { viewModel.cancelDownload(task.id) },
                                onRetry = { viewModel.retryDownload(task.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    deletingVersion?.let { pendingDelete ->
        MiConfirmDialog(
            title = stringResource(R.string.versions_delete_confirm_title, pendingDelete.version),
            message = stringResource(R.string.versions_delete_confirm_message),
            confirmText = stringResource(R.string.common_delete),
            onConfirm = {
                viewModel.delete(pendingDelete)
                deletingVersion = null
            },
            onDismiss = { deletingVersion = null }
        )
    }
}

@Composable
private fun InstallProgressBlock(
    progress: InstallProgress,
    busyVersion: String?,
    onCancel: () -> Unit
) {
    val spacing = MiTheme.spacing
    val fraction = progress.progressFraction()
    LinearProgressIndicator(
        progress = { fraction ?: 0f },
        modifier = Modifier
            .padding(top = spacing.md)
            .fillMaxWidth()
    )
    Text(
        text = progress.message ?: progress.phase.name,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = spacing.sm)
    )
    if (progress.phase == InstallPhase.DOWNLOADING) {
        Text(
            text = when {
                fraction != null -> stringResource(
                    R.string.versions_progress_format,
                    (fraction * 100f).roundToInt(),
                    formatSize(progress.downloadedBytes),
                    formatSize(progress.totalBytes)
                )

                else -> stringResource(
                    R.string.versions_progress_downloaded_only,
                    formatSize(progress.downloadedBytes)
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = spacing.xs)
        )
    }
    busyVersion?.let { activeVersion ->
        Text(
            text = stringResource(R.string.versions_downloading_target, activeVersion),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = spacing.xs)
        )
    }
    MiDangerButton(
        text = stringResource(R.string.common_cancel),
        onClick = onCancel,
        modifier = Modifier
            .padding(top = spacing.sm)
            .fillMaxWidth()
    )
}

@Composable
private fun InstalledVersionCard(
    version: InstalledFridaVersion,
    isBusy: Boolean,
    onSwitch: () -> Unit,
    onDelete: () -> Unit
) {
    val spacing = MiTheme.spacing
    MiCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(version.version, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = version.binaryPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = spacing.xxs)
                )
            }
            MiStatusChip(
                label = when {
                    !version.isValid -> stringResource(R.string.versions_status_broken)
                    version.isActive -> stringResource(R.string.versions_status_active)
                    else -> stringResource(R.string.versions_status_installed)
                },
                tone = when {
                    !version.isValid -> MiStatusTone.ERROR
                    version.isActive -> MiStatusTone.SUCCESS
                    else -> MiStatusTone.NORMAL
                }
            )
        }

        MiValueRow(
            title = stringResource(R.string.versions_source),
            value = version.source.name
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            MiSecondaryButton(
                text = if (isBusy) {
                    stringResource(R.string.versions_working)
                } else {
                    stringResource(R.string.versions_switch)
                },
                enabled = !isBusy && version.isValid && !version.isActive,
                modifier = Modifier.weight(1f),
                onClick = onSwitch
            )
            MiDangerButton(
                text = stringResource(R.string.common_delete),
                enabled = !isBusy,
                modifier = Modifier.weight(1f),
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun RemoteMajorGroupCard(
    group: RemoteMajorGroup,
    currentAbiTag: String?,
    installedVersions: Set<String>,
    busyVersion: Set<String>,
    majorExpanded: Boolean,
    onToggleMajor: () -> Unit,
    isReleaseExpanded: (String) -> Boolean,
    onToggleRelease: (String) -> Unit,
    onInstall: (RemoteFridaVersion) -> Unit
) {
    val spacing = MiTheme.spacing
    MiCard(tonal = true) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleMajor),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (group.majorKey.all { it.isDigit() }) {
                        stringResource(R.string.versions_major_group_format, group.majorKey)
                    } else {
                        group.majorKey
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.versions_count_format, group.releases.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = if (majorExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (majorExpanded) {
            Column(
                modifier = Modifier.padding(top = spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                group.releases.forEach { release ->
                    RemoteVersionExpandableCard(
                        release = release,
                        currentAbiTag = currentAbiTag,
                        installed = installedVersions.contains(release.version),
                        isBusy = busyVersion.contains(release.version),
                        expanded = isReleaseExpanded(release.version),
                        onToggle = { onToggleRelease(release.version) },
                        onInstall = { onInstall(release) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoteVersionExpandableCard(
    release: RemoteFridaVersion,
    currentAbiTag: String?,
    installed: Boolean,
    isBusy: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onInstall: () -> Unit
) {
    val match = release.matchingAsset(currentAbiTag)
    val spacing = MiTheme.spacing
    MiCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(release.version, style = MaterialTheme.typography.titleMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                MiStatusChip(
                    label = when {
                        installed -> stringResource(R.string.versions_status_installed)
                        match == null -> stringResource(R.string.versions_status_incompatible)
                        else -> stringResource(R.string.versions_status_available)
                    },
                    tone = when {
                        installed -> MiStatusTone.SUCCESS
                        match == null -> MiStatusTone.WARNING
                        else -> MiStatusTone.INFO
                    }
                )
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null
                )
            }
        }

        if (expanded) {
            MiValueRow(
                title = stringResource(R.string.versions_published),
                value = release.publishedAt
            )
            MiValueRow(
                title = stringResource(R.string.versions_asset),
                value = match?.name ?: stringResource(R.string.versions_no_matching_asset)
            )
            MiPrimaryButton(
                text = if (isBusy) {
                    stringResource(R.string.versions_downloading)
                } else {
                    stringResource(R.string.versions_download)
                },
                enabled = !isBusy && match != null,
                onClick = onInstall,
                modifier = Modifier
                    .padding(top = spacing.sm)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTask,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    val spacing = MiTheme.spacing
    val progress = task.progressFraction()
    MiCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(task.version, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = task.assetName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = spacing.xxs)
                )
            }
            MiStatusChip(
                label = task.status.label(),
                tone = task.status.tone()
            )
        }

        LinearProgressIndicator(
            progress = { progress ?: 0f },
            modifier = Modifier
                .padding(top = spacing.sm)
                .fillMaxWidth()
        )

        val bytesText = if (task.totalBytes > 0L) {
            stringResource(
                R.string.versions_progress_format,
                ((progress ?: 0f) * 100f).roundToInt(),
                formatSize(task.downloadedBytes),
                formatSize(task.totalBytes)
            )
        } else {
            stringResource(
                R.string.versions_progress_downloaded_only,
                formatSize(task.downloadedBytes)
            )
        }
        Text(
            text = bytesText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = spacing.xs)
        )

        Text(
            text = task.detailText(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = spacing.xs)
        )

        if (task.speedBytesPerSec > 0L) {
            Text(
                text = stringResource(
                    R.string.versions_download_speed_format,
                    formatSize(task.speedBytesPerSec)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = spacing.xs)
            )
        }

        if (!task.message.isNullOrBlank()) {
            Text(
                text = task.message.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = spacing.xs)
            )
        }

        if (!task.isTerminal) {
            MiDangerButton(
                text = stringResource(R.string.common_cancel),
                onClick = onCancel,
                modifier = Modifier
                    .padding(top = spacing.sm)
                    .fillMaxWidth()
            )
        } else if (task.status == DownloadTaskStatus.FAILED || task.status == DownloadTaskStatus.CANCELED) {
            MiSecondaryButton(
                text = stringResource(R.string.common_retry),
                onClick = onRetry,
                modifier = Modifier
                    .padding(top = spacing.sm)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DownloadTaskStatus.label(): String {
    return when (this) {
        DownloadTaskStatus.QUEUED -> stringResource(R.string.versions_download_status_queued)
        DownloadTaskStatus.DOWNLOADING -> stringResource(R.string.versions_download_status_downloading)
        DownloadTaskStatus.INSTALLING -> stringResource(R.string.versions_download_status_installing)
        DownloadTaskStatus.COMPLETED -> stringResource(R.string.versions_download_status_completed)
        DownloadTaskStatus.FAILED -> stringResource(R.string.versions_download_status_failed)
        DownloadTaskStatus.CANCELED -> stringResource(R.string.versions_download_status_canceled)
    }
}

private fun DownloadTaskStatus.tone(): MiStatusTone {
    return when (this) {
        DownloadTaskStatus.QUEUED -> MiStatusTone.NORMAL
        DownloadTaskStatus.DOWNLOADING -> MiStatusTone.INFO
        DownloadTaskStatus.INSTALLING -> MiStatusTone.INFO
        DownloadTaskStatus.COMPLETED -> MiStatusTone.SUCCESS
        DownloadTaskStatus.FAILED -> MiStatusTone.ERROR
        DownloadTaskStatus.CANCELED -> MiStatusTone.WARNING
    }
}

@Composable
private fun DownloadTask.detailText(): String {
    return when (status) {
        DownloadTaskStatus.QUEUED -> stringResource(R.string.versions_download_detail_queued)
        DownloadTaskStatus.DOWNLOADING -> {
            if (speedBytesPerSec > 0L || downloadedBytes > 0L) {
                stringResource(R.string.versions_download_detail_downloading)
            } else {
                stringResource(R.string.versions_download_detail_starting)
            }
        }

        DownloadTaskStatus.INSTALLING -> when (phase) {
            InstallPhase.DECOMPRESSING -> stringResource(R.string.versions_download_detail_decompressing)
            else -> stringResource(R.string.versions_download_detail_installing)
        }

        DownloadTaskStatus.COMPLETED -> stringResource(R.string.versions_download_detail_completed)
        DownloadTaskStatus.CANCELED -> stringResource(R.string.versions_download_detail_canceled)
        DownloadTaskStatus.FAILED -> message ?: stringResource(R.string.versions_download_detail_failed)
    }
}

private fun groupRemoteByMajor(remote: List<RemoteFridaVersion>): List<RemoteMajorGroup> {
    if (remote.isEmpty()) return emptyList()
    val grouped = remote.groupBy { majorVersionKey(it.version) }
    val sortedMajorKeys = grouped.keys.sortedWith(
        compareByDescending<String> { it.toIntOrNull() ?: Int.MIN_VALUE }
            .thenByDescending { it }
    )

    return sortedMajorKeys.map { major ->
        val releases = grouped.getValue(major)
            .sortedWith { left, right -> compareVersionDesc(left.version, right.version) }
        RemoteMajorGroup(majorKey = major, releases = releases)
    }
}

private fun majorVersionKey(version: String): String {
    return version.substringBefore('.').ifBlank { "other" }
}

private fun compareVersionDesc(left: String, right: String): Int {
    val leftParts = parseVersionParts(left)
    val rightParts = parseVersionParts(right)
    val maxLen = maxOf(leftParts.size, rightParts.size)
    repeat(maxLen) { index ->
        val l = leftParts.getOrElse(index) { 0 }
        val r = rightParts.getOrElse(index) { 0 }
        if (l != r) return r.compareTo(l)
    }
    return right.compareTo(left)
}

private fun parseVersionParts(version: String): List<Int> {
    return version.split('.', '-', '_')
        .mapNotNull { it.toIntOrNull() }
}

private fun InstallProgress.progressFraction(): Float? {
    if (totalBytes <= 0L) return null
    val ratio = downloadedBytes.toFloat() / totalBytes.toFloat()
    return ratio.coerceIn(0f, 1f)
}

private fun DownloadTask.progressFraction(): Float? {
    if (totalBytes <= 0L) return null
    val ratio = downloadedBytes.toFloat() / totalBytes.toFloat()
    return ratio.coerceIn(0f, 1f)
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024L) return "${bytes}B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format("%.1fKB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format("%.1fMB", mb)
    val gb = mb / 1024.0
    return String.format("%.2fGB", gb)
}

@Composable
private fun rememberOpenDocumentLauncher(
    activity: ComponentActivity,
    onResult: (android.net.Uri?) -> Unit
): () -> Unit {
    val latestOnResult by rememberUpdatedState(onResult)
    var launcher by remember { mutableStateOf<ActivityResultLauncher<Array<String>>?>(null) }

    DisposableEffect(activity) {
        val key = "frida_manager_open_document_${activity.hashCode()}"
        val registered = activity.activityResultRegistry.register(
            key,
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            latestOnResult(uri)
        }
        launcher = registered
        onDispose {
            registered.unregister()
            launcher = null
        }
    }

    return remember(activity) {
        { launcher?.launch(arrayOf("*/*")) }
    }
}
