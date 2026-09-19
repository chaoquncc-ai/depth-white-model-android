package com.chaoqun.depthwhite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chaoqun.depthwhite.ui.ConvertScreen
import com.chaoqun.depthwhite.ui.theme.DepthWhiteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DepthWhiteTheme {
                ConvertScreen()
            }
        }
    }
}
