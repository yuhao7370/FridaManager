package com.yuhao7370.fridamanager.ui.components

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

@Composable
fun MiToastEffect(
    message: String?,
    onConsumed: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(message) {
        val resolved = message?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        Toast.makeText(context, resolved, Toast.LENGTH_SHORT).show()
        onConsumed()
    }
}
