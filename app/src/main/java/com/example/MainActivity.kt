package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.compose.runtime.collectAsState
import com.example.data.repository.WeatherRepository
import com.example.ui.navigation.MainScreenShell
import com.example.ui.theme.SkySphereTheme
import com.example.worker.WeatherNotificationManager
import com.example.worker.WeatherWorkerScheduler

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            WeatherNotificationManager.createNotificationChannel(applicationContext)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Create notification channel for weather alerts and summaries
        WeatherNotificationManager.createNotificationChannel(applicationContext)

        // Schedule background WorkManager task for periodic weather updates
        WeatherWorkerScheduler.schedulePeriodicWeatherUpdates(applicationContext)

        // Request POST_NOTIFICATIONS runtime permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        // Single global repository source
        val repository = WeatherRepository(applicationContext)

        setContent {
            // State-driven theme configuration managed from settings
            val currentAppTheme by repository.appTheme.collectAsState()
            var darkTheme by remember { mutableStateOf(true) }

            SkySphereTheme(themeId = currentAppTheme, darkTheme = darkTheme) {
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

