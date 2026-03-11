package com.yuhao7370.fridamanager.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.yuhao7370.fridamanager.R
import com.yuhao7370.fridamanager.model.RuntimeStatus
import com.yuhao7370.fridamanager.ui.components.MiCard
import com.yuhao7370.fridamanager.ui.components.MiPrimaryButton
import com.yuhao7370.fridamanager.ui.components.MiScaffold
import com.yuhao7370.fridamanager.ui.components.MiSectionHeader
import com.yuhao7370.fridamanager.ui.components.MiSecondaryButton
import com.yuhao7370.fridamanager.ui.components.MiStatusChip
import com.yuhao7370.fridamanager.ui.components.MiStatusTone
import com.yuhao7370.fridamanager.ui.components.MiTopBar
import com.yuhao7370.fridamanager.ui.components.MiValueRow
import com.yuhao7370.fridamanager.ui.theme.MiTheme

@Composable
fun HomeScreen(
    viewModel: HomeViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = MiTheme.spacing
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.setMonitoringEnabled(true)
                Lifecycle.Event.ON_STOP -> viewModel.setMonitoringEnabled(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setMonitoringEnabled(false)
        }
    }

    MiScaffold(
        topBar = {
            MiTopBar(
                title = stringResource(R.string.home_title),
                subtitle = stringResource(R.string.home_top_subtitle)
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        MiSectionHeader(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.home_runtime_overview_title),
                            subtitle = uiState.runtime.activeVersion
                                ?: stringResource(R.string.common_not_selected)
                        )
                        MiStatusChip(
                            label = uiState.runtime.status.displayLabel(),
                            tone = uiState.runtime.status.toTone()
                        )
                    }
                    uiState.message?.takeIf { it.isNotBlank() }?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = spacing.xs)
                        )
                    }
                }
            }

            item {
                MiCard {
                    MiSectionHeader(
                        title = stringResource(R.string.home_controls_title),
                        subtitle = stringResource(R.string.home_controls_subtitle)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        MiPrimaryButton(
                            text = stringResource(R.string.home_start),
                            onClick = viewModel::start,
                            enabled = !uiState.inProgress,
                            modifier = Modifier.weight(1f)
                        )
                        MiSecondaryButton(
                            text = stringResource(R.string.home_stop),
                            onClick = viewModel::stop,
                            enabled = !uiState.inProgress,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = spacing.sm)
                    ) {
                        MiSecondaryButton(
                            text = stringResource(R.string.home_restart),
                            onClick = viewModel::restart,
                            enabled = !uiState.inProgress,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                MiCard {
                    MiSectionHeader(
                        title = stringResource(R.string.home_details_title),
                        subtitle = stringResource(R.string.home_details_subtitle)
                    )
                    MiValueRow(
                        stringResource(R.string.home_status),
                        uiState.runtime.status.displayLabel()
                    )
                    MiValueRow(
                        stringResource(R.string.home_active_version),
                        uiState.runtime.activeVersion ?: stringResource(R.string.common_not_selected)
                    )
                    MiValueRow(
                        stringResource(R.string.home_pid),
                        uiState.runtime.pid?.toString() ?: stringResource(R.string.common_na)
                    )
                    MiValueRow(stringResource(R.string.home_host), uiState.settings.defaultHost)
                    MiValueRow(stringResource(R.string.home_port), uiState.settings.defaultPort.toString())
                    MiValueRow(
                        stringResource(R.string.home_abi),
                        uiState.abiInfo?.fridaAssetTag ?: stringResource(R.string.common_unknown)
                    )
                    MiValueRow(
                        stringResource(R.string.home_last_error),
                        uiState.runtime.lastError?.message
                            ?: uiState.runtime.lastError?.type?.name
                            ?: stringResource(R.string.common_none)
                    )
                }
            }
        }
    }
}

@Composable
private fun RuntimeStatus.displayLabel(): String {
    return when (this) {
        RuntimeStatus.RUNNING -> stringResource(R.string.runtime_status_running)
        RuntimeStatus.STARTING -> stringResource(R.string.runtime_status_starting)
        RuntimeStatus.STOPPING -> stringResource(R.string.runtime_status_stopping)
        RuntimeStatus.STOPPED -> stringResource(R.string.runtime_status_stopped)
        RuntimeStatus.ERROR -> stringResource(R.string.runtime_status_error)
    }
}

private fun RuntimeStatus.toTone(): MiStatusTone = when (this) {
    RuntimeStatus.RUNNING -> MiStatusTone.SUCCESS
    RuntimeStatus.STARTING, RuntimeStatus.STOPPING -> MiStatusTone.INFO
    RuntimeStatus.ERROR -> MiStatusTone.ERROR
    RuntimeStatus.STOPPED -> MiStatusTone.NORMAL
}
