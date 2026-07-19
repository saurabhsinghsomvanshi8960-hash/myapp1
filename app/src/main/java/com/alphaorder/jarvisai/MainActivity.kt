package com.alphaorder.jarvisai

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.alphaorder.jarvisai.data.SettingsDataStore
import com.alphaorder.jarvisai.ui.HomeScreen
import com.alphaorder.jarvisai.ui.SettingsScreen
import com.alphaorder.jarvisai.ui.SettingsState
import com.alphaorder.jarvisai.ui.TestStatus
import com.alphaorder.jarvisai.ui.theme.JarvisAITheme
import com.alphaorder.jarvisai.util.PermissionHelper
import com.alphaorder.jarvisai.viewmodel.JarvisViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: JarvisViewModel by viewModels()

    private var micPermissionGranted = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micPermissionGranted = granted
        if (granted) viewModel.startListening()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        micPermissionGranted = PermissionHelper.hasRecordAudioPermission(this)

        setContent {
            JarvisAITheme {
                val navController = rememberNavController()
                val uiState by viewModel.uiState.collectAsState()
                val context = LocalContext.current
                val settingsDataStore = remember { SettingsDataStore(context) }
                val scope = rememberCoroutineScope()

                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            uiState = uiState,
                            hasMicPermission = micPermissionGranted,
                            onMicTap = {
                                if (uiState.state.name == "LISTENING") {
                                    viewModel.stopListening()
                                } else {
                                    viewModel.startListening()
                                }
                            },
                            onRequestPermission = {
                                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            onSendTyped = viewModel::sendTypedMessage,
                            onSettingsClick = { navController.navigate("settings") },
                            onDismissError = viewModel::clearError
                        )
                    }

                    composable("settings") {
                        var settingsState by remember { mutableStateOf(SettingsState()) }

                        LaunchedEffect(Unit) {
                            settingsState = settingsState.copy(apiKey = settingsDataStore.getApiKey())
                            launch {
                                settingsDataStore.geminiModel.collect {
                                    settingsState = settingsState.copy(model = it)
                                }
                            }
                            launch {
                                settingsDataStore.voiceGender.collect {
                                    settingsState = settingsState.copy(voiceGender = it)
                                }
                            }
                            launch {
                                settingsDataStore.language.collect {
                                    settingsState = settingsState.copy(language = it)
                                }
                            }
                        }

                        SettingsScreen(
                            state = settingsState,
                            onApiKeyChange = { settingsState = settingsState.copy(apiKey = it) },
                            onModelChange = {
                                settingsState = settingsState.copy(model = it)
                                scope.launch { settingsDataStore.setGeminiModel(it) }
                            },
                            onVoiceGenderChange = {
                                settingsState = settingsState.copy(voiceGender = it)
                                scope.launch { settingsDataStore.setVoiceGender(it) }
                            },
                            onLanguageChange = {
                                settingsState = settingsState.copy(language = it)
                                scope.launch { settingsDataStore.setLanguage(it) }
                            },
                            onSave = {
                                settingsDataStore.saveApiKey(settingsState.apiKey)
                                navController.popBackStack()
                            },
                            onTestConnection = {
                                settingsState = settingsState.copy(testStatus = TestStatus.TESTING)
                                scope.launch {
                                    val repo = com.alphaorder.jarvisai.data.GeminiRepository()
                                    val result = repo.testConnection(settingsState.apiKey, settingsState.model)
                                    settingsState = settingsState.copy(
                                        testStatus = if (result is com.alphaorder.jarvisai.data.GeminiResult.Success)
                                            TestStatus.SUCCESS else TestStatus.FAILED
                                    )
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
