package com.yuhao7370.fridamanager.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuhao7370.fridamanager.R
import com.yuhao7370.fridamanager.model.LanguagePreference
import com.yuhao7370.fridamanager.model.ThemePreference
import com.yuhao7370.fridamanager.ui.components.MiCard
import com.yuhao7370.fridamanager.ui.components.MiScaffold
import com.yuhao7370.fridamanager.ui.components.MiSectionHeader
import com.yuhao7370.fridamanager.ui.components.MiSegmentedControl
import com.yuhao7370.fridamanager.ui.components.MiToastEffect
import com.yuhao7370.fridamanager.ui.components.MiTopBar
import com.yuhao7370.fridamanager.ui.theme.MiTheme

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenAbout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = MiTheme.spacing
    val settings = uiState.settings

    MiToastEffect(
        message = uiState.message?.let { message ->
            when (message) {
                SettingsMessage.SAVED -> viewModelTextSaved()
            }
        },
        onConsumed = viewModel::dismissMessage
    )

    MiScaffold(
        topBar = {
            MiTopBar(
                title = stringResource(R.string.settings_title),
                subtitle = stringResource(R.string.settings_top_subtitle)
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
                MiCard {
                    MiSectionHeader(
                        title = stringResource(R.string.settings_appearance)
                    )
                    MiSegmentedControl(
                        options = listOf(
                            stringResource(R.string.settings_theme_system),
                            stringResource(R.string.settings_theme_light),
                            stringResource(R.string.settings_theme_dark)
                        ),
                        selectedIndex = when (settings.themePreference) {
                            ThemePreference.FOLLOW_SYSTEM -> 0
                            ThemePreference.LIGHT -> 1
                            ThemePreference.DARK -> 2
                        },
                        onSelected = { index ->
                            viewModel.updateTheme(
                                when (index) {
                                    1 -> ThemePreference.LIGHT
                                    2 -> ThemePreference.DARK
                                    else -> ThemePreference.FOLLOW_SYSTEM
                                }
                            )
                        }
                    )
                }
            }

            item {
                MiCard {
                    MiSectionHeader(
                        title = stringResource(R.string.settings_language_title)
                    )
                    MiSegmentedControl(
                        options = listOf(
                            stringResource(R.string.settings_language_system),
                            stringResource(R.string.settings_language_english),
                            stringResource(R.string.settings_language_simplified_chinese)
                        ),
                        selectedIndex = when (settings.languagePreference) {
                            LanguagePreference.FOLLOW_SYSTEM -> 0
                            LanguagePreference.ENGLISH -> 1
                            LanguagePreference.SIMPLIFIED_CHINESE -> 2
                        },
                        onSelected = { index ->
                            viewModel.updateLanguage(
                                when (index) {
                                    1 -> LanguagePreference.ENGLISH
                                    2 -> LanguagePreference.SIMPLIFIED_CHINESE
                                    else -> LanguagePreference.FOLLOW_SYSTEM
                                }
                            )
                        }
                    )
                }
            }

            item {
                MiCard {
                    MiSectionHeader(
                        title = stringResource(R.string.settings_runtime_defaults),
                        subtitle = stringResource(R.string.settings_runtime_subtitle)
                    )
                    OutlinedTextField(
                        value = settings.defaultHost,
                        onValueChange = viewModel::updateHost,
                        label = { Text(stringResource(R.string.settings_default_host)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = settings.defaultPort.toString(),
                        onValueChange = viewModel::updatePort,
                        label = { Text(stringResource(R.string.settings_default_port)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = spacing.sm)
                    )
                }
            }

            item {
                MiCard {
                    MiSectionHeader(
                        title = stringResource(R.string.settings_logs_network),
                        subtitle = stringResource(R.string.settings_logs_network_subtitle)
                    )
                    OutlinedTextField(
                        value = settings.logRetentionKb.toString(),
                        onValueChange = viewModel::updateLogRetention,
                        label = { Text(stringResource(R.string.settings_log_retention)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = settings.githubApiBaseUrl,
                        onValueChange = viewModel::updateGithubBaseUrl,
                        label = { Text(stringResource(R.string.settings_github_base_url)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = spacing.sm)
                    )
                }
            }

            item {
                MiCard(
                    tonal = true,
                    modifier = Modifier.clickable(onClick = onOpenAbout)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.md)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_about),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.settings_about_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun viewModelTextSaved(): String = stringResource(R.string.common_saved)
