package com.sonora.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.rememberNavController
import com.sonora.music.ui.navigation.SonoraNavHost
import com.sonora.music.ui.theme.LocalSonoraColors
import com.sonora.music.ui.theme.SonoraTheme
import com.sonora.music.viewmodel.MainViewModel

@UnstableApi
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var hasStoragePermission by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_AUDIO] == true
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }

        hasStoragePermission = audioGranted
        if (audioGranted) {
            viewModel.rescanLibrary()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkInitialPermission()

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()

            SonoraTheme(themeMode = themeMode) {
                val colors = LocalSonoraColors.current
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = colors.background
                ) {
                    val navController = rememberNavController()
                    SonoraNavHost(
                        navController = navController,
                        viewModel = viewModel,
                        hasStoragePermission = hasStoragePermission,
                        onRequestPermission = { requestMediaPermissions() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkInitialPermission()
    }

    private fun checkInitialPermission() {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

        hasStoragePermission = granted
        if (granted) {
            viewModel.rescanLibrary()
        }
    }

    private fun requestMediaPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        permissionLauncher.launch(permissions.toTypedArray())
    }
}
