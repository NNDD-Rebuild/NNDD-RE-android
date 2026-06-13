package com.nndd_re

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nndd_re.data.repository.NnddRepository
import com.nndd_re.ui.PlayerUiState
import com.nndd_re.ui.SharedViewModel
import com.nndd_re.ui.screen.library.LibraryScreen
import com.nndd_re.ui.screen.player.PlayerOverlay
import com.nndd_re.ui.screen.settings.SettingsScreen
import com.nndd_re.ui.theme.NNDDRETheme
import com.nndd_re.util.ServerPreferences

class MainActivity : ComponentActivity() {
    var isPlayerActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NNDDRETheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NnddApp()
                }
            }
        }
    }
}

@Composable
fun NnddApp() {
    val ctx = LocalContext.current
    val activity = ctx as? androidx.activity.ComponentActivity
    val navController = rememberNavController()
    val sharedVm: SharedViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        viewModelStoreOwner = activity ?: return
    )

    val savedHost by ServerPreferences.hostFlow(ctx).collectAsState(initial = "")
    val savedPort by ServerPreferences.portFlow(ctx).collectAsState(initial = 12345)

    val startDest = if (savedHost.isNotEmpty()) "library" else "settings"
    val repo = remember(savedHost, savedPort) {
        if (savedHost.isNotEmpty()) NnddRepository(savedHost, savedPort) else null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = startDest) {
            composable("settings") {
                SettingsScreen(
                    onConnected = {
                        navController.navigate("library") {
                            popUpTo("settings") { inclusive = true }
                        }
                    }
                )
            }
            composable("library") {
                val r = repo
                if (r == null) {
                    navController.navigate("settings") { popUpTo("library") { inclusive = true } }
                } else {
                    LibraryScreen(
                        repo = r,
                        onVideoClick = { id ->
                            sharedVm.currentVideoId = id
                            sharedVm.playerUiState = PlayerUiState.FULL
                        },
                        onSettings = { navController.navigate("settings") }
                    )
                }
            }
        }

        val r = repo
        val vid = sharedVm.currentVideoId
        if (r != null && vid != null && sharedVm.playerUiState != PlayerUiState.HIDDEN) {
            PlayerOverlay(
                repo = r,
                videoId = vid,
                sharedVm = sharedVm,
                onClose = { sharedVm.playerUiState = PlayerUiState.HIDDEN }
            )
        }
    }
}
