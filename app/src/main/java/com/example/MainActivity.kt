package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.screens.CameraCaptureScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ResultsScreen
import com.example.ui.screens.SavedClipsScreen
import com.example.ui.screens.SelectionCanvasScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.OcrViewModel
import com.example.viewmodel.Screen

class MainActivity : ComponentActivity() {

    private val ocrViewModel: OcrViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val currentScreen by ocrViewModel.currentScreen.collectAsState()

                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "screen_transition"
                    ) { targetScreen ->
                        when (targetScreen) {
                            Screen.HOME -> {
                                HomeScreen(
                                    viewModel = ocrViewModel,
                                    onTakePhotoClick = {
                                        ocrViewModel.navigateTo(Screen.CAMERA)
                                    }
                                )
                            }
                            Screen.CAMERA -> {
                                CameraCaptureScreen(
                                    viewModel = ocrViewModel
                                )
                            }
                            Screen.SELECTION -> {
                                SelectionCanvasScreen(
                                    viewModel = ocrViewModel
                                )
                            }
                            Screen.RESULTS -> {
                                ResultsScreen(
                                    viewModel = ocrViewModel
                                )
                            }
                            Screen.SAVED_CLIPS -> {
                                SavedClipsScreen(
                                    viewModel = ocrViewModel
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
