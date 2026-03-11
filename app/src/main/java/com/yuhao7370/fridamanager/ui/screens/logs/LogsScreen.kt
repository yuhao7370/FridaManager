package com.yuhao7370.fridamanager.ui.screens.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yuhao7370.fridamanager.R
import com.yuhao7370.fridamanager.model.LogSource
import com.yuhao7370.fridamanager.ui.components.MiCard
import com.yuhao7370.fridamanager.ui.components.MiPrimaryButton
import com.yuhao7370.fridamanager.ui.components.MiScaffold
import com.yuhao7370.fridamanager.ui.components.MiSectionHeader
import com.yuhao7370.fridamanager.ui.components.MiSegmentedControl
import com.yuhao7370.fridamanager.ui.components.MiSwitchRow
import com.yuhao7370.fridamanager.ui.components.MiToastEffect
import com.yuhao7370.fridamanager.ui.components.MiTopBar
import com.yuhao7370.fridamanager.ui.theme.MiTheme

@Composable
fun LogsScreen(viewModel: LogsViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = MiTheme.spacing
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.setScreenActive(true)
                Lifecycle.Event.ON_STOP -> viewModel.setScreenActive(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setScreenActive(false)
        }
    }

    LaunchedEffect(uiState.selectedSource) {
        viewModel.refreshNow()
    }

    MiToastEffect(
        message = uiState.messageRes?.let { stringResource(it) },
        onConsumed = viewModel::dismissMessage
    )

    MiScaffold(
        topBar = {
            MiTopBar(
                title = stringResource(R.string.logs_title),
                subtitle = stringResource(R.string.logs_top_subtitle)
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
                        title = stringResource(R.string.logs_controls_title),
                        subtitle = stringResource(R.string.logs_controls_subtitle)
                    )
                    MiSegmentedControl(
                        options = listOf(
                            stringResource(R.string.logs_tab_controller),
                            stringResource(R.string.logs_tab_stdout),
                            stringResource(R.string.logs_tab_stderr)
                        ),
                        selectedIndex = when (uiState.selectedSource) {
                            LogSource.CONTROLLER -> 0
                            LogSource.FRIDA_STDOUT -> 1
                            LogSource.FRIDA_STDERR -> 2
                        },
                        onSelected = { index ->
                            viewModel.selectSource(
                                when (index) {
                                    0 -> LogSource.CONTROLLER
                                    1 -> LogSource.FRIDA_STDOUT
                                    else -> LogSource.FRIDA_STDERR
                                }
                            )
                        }
                    )
                    MiSwitchRow(
                        title = stringResource(R.string.logs_auto_refresh),
                        checked = uiState.autoRefresh,
                        onCheckedChange = viewModel::setAutoRefresh
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        MiPrimaryButton(
                            text = stringResource(R.string.logs_refresh),
                            modifier = Modifier.weight(1f),
                            onClick = viewModel::refreshNow
                        )
                        MiPrimaryButton(
                            text = stringResource(R.string.logs_clear),
                            modifier = Modifier.weight(1f),
                            onClick = viewModel::clearSelected
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        MiPrimaryButton(
                            text = stringResource(R.string.logs_copy),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                copyToClipboard(
                                    context = context,
                                    content = uiState.content,
                                    label = context.getString(R.string.logs_clipboard_label)
                                )
                            }
                        )
                        MiPrimaryButton(
                            text = stringResource(R.string.logs_export),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                exportLog(
                                    context = context,
                                    content = uiState.content,
                                    chooserTitle = context.getString(R.string.logs_export_chooser)
                                )
                            }
                        )
                    }
                }
            }

            item {
                MiCard(modifier = Modifier.fillMaxWidth()) {
                    MiSectionHeader(
                        title = stringResource(R.string.logs_content_title),
                        subtitle = stringResource(R.string.logs_content_subtitle)
                    )
                    Text(
                        text = if (uiState.content.isBlank()) {
                            stringResource(R.string.logs_empty_placeholder)
                        } else {
                            uiState.content
                        },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .padding(spacing.md)
                            .heightIn(min = spacing.xxl * 6, max = 420.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, content: String, label: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, content))
}

private fun exportLog(context: Context, content: String, chooserTitle: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, content)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}
