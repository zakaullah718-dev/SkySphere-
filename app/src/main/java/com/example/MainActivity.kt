package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.data.repository.WeatherRepository
import com.example.ui.navigation.MainScreenShell
import com.example.ui.theme.SkySphereTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Single global repository source
        val repository = WeatherRepository()

        setContent {
            // State-driven theme configuration managed from settings
            var darkTheme by remember { mutableStateOf(true) }

            SkySphereTheme(darkTheme = darkTheme) {
                MainScreenShell(
                    repository = repository,
                    darkTheme = darkTheme,
                    onThemeToggle = { darkTheme = it },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
