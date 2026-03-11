package com.yuhao7370.fridamanager.ui.screens.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.yuhao7370.fridamanager.R
import com.yuhao7370.fridamanager.BuildConfig
import com.yuhao7370.fridamanager.ui.components.MiCard
import com.yuhao7370.fridamanager.ui.components.MiScaffold
import com.yuhao7370.fridamanager.ui.components.MiSectionHeader
import com.yuhao7370.fridamanager.ui.components.MiTopBar
import com.yuhao7370.fridamanager.ui.components.MiValueRow
import com.yuhao7370.fridamanager.ui.theme.MiTheme

@Composable
fun AboutScreen() {
    val spacing = MiTheme.spacing
    MiScaffold(
        topBar = {
            MiTopBar(
                title = stringResource(R.string.about_title),
                subtitle = stringResource(R.string.about_top_subtitle)
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
                MiCard(tonal = true, modifier = Modifier.fillMaxWidth()) {
                    MiSectionHeader(title = stringResource(R.string.about_app_name_title))
                    MiValueRow(
                        title = stringResource(R.string.settings_package),
                        value = BuildConfig.APPLICATION_ID
                    )
                    MiValueRow(
                        title = stringResource(R.string.settings_version),
                        value = BuildConfig.VERSION_NAME
                    )
                }
            }
            item {
                MiCard(modifier = Modifier.fillMaxWidth()) {
                    MiSectionHeader(title = stringResource(R.string.about_links_title))
                    MiValueRow(
                        title = stringResource(R.string.about_github_title),
                        value = stringResource(R.string.about_github_placeholder)
                    )
                }
            }
        }
    }
}
