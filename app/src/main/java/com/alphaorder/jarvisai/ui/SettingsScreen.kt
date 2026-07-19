package com.alphaorder.jarvisai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.alphaorder.jarvisai.ui.theme.JarvisBackground
import com.alphaorder.jarvisai.ui.theme.JarvisCyan

data class SettingsState(
    val apiKey: String = "",
    val model: String = "gemini-2.5-flash",
    val voiceGender: String = "female",
    val language: String = "en-IN",
    val testStatus: TestStatus = TestStatus.IDLE
)

enum class TestStatus { IDLE, TESTING, SUCCESS, FAILED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsState,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onVoiceGenderChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onSave: () -> Unit,
    onTestConnection: () -> Unit,
    onBack: () -> Unit
) {
    var keyVisible by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = JarvisBackground,
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = JarvisCyan) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = JarvisCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = JarvisBackground)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
        ) {
            Text("Gemini API Key", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = state.apiKey,
                onValueChange = onApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Paste your Gemini API key", color = Color.Gray) },
                singleLine = true,
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle visibility",
                            tint = JarvisCyan
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarvisCyan,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text("Gemini Model", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.height(6.dp))
            SingleChoiceRow(
                options = listOf(
                    "gemini-2.5-flash-lite" to "Flash (fast)",
                    "gemini-2.5-flash" to "Pro (smarter)"
                ),
                selected = state.model,
                onSelect = onModelChange
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text("Voice", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.height(6.dp))
            SingleChoiceRow(
                options = listOf("female" to "Female", "male" to "Male"),
                selected = state.voiceGender,
                onSelect = onVoiceGenderChange
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text("Language", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.height(6.dp))
            SingleChoiceRow(
                options = listOf("hi-IN" to "Hindi", "en-IN" to "English"),
                selected = state.language,
                onSelect = onLanguageChange
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisBackground)
            ) {
                Text("Save")
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onTestConnection,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.testStatus != TestStatus.TESTING
            ) {
                Text(
                    when (state.testStatus) {
                        TestStatus.IDLE -> "Test Connection"
                        TestStatus.TESTING -> "Testing…"
                        TestStatus.SUCCESS -> "✅ Connected"
                        TestStatus.FAILED -> "❌ Failed — Retry"
                    },
                    color = JarvisCyan
                )
            }
        }
    }
}

@Composable
private fun SingleChoiceRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = JarvisCyan,
                    selectedLabelColor = JarvisBackground
                )
            )
        }
    }
}
