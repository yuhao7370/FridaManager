package com.yuhao7370.fridamanager.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import com.yuhao7370.fridamanager.ui.FridaManagerRoot
import com.yuhao7370.fridamanager.ui.LocalAppContainer
import com.yuhao7370.fridamanager.ui.LocalHostActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as FridaManagerApp
        setContent {
            CompositionLocalProvider(
                LocalAppContainer provides app.container,
                LocalHostActivity provides this
            ) {
                FridaManagerRoot()
            }
        }
    }
}
