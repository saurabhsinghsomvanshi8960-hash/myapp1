package com.alphaorder.jarvisai.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.alphaorder.jarvisai.ui.components.OrbAnimation
import com.alphaorder.jarvisai.ui.theme.JarvisBackground
import com.alphaorder.jarvisai.ui.theme.JarvisCyan
import com.alphaorder.jarvisai.viewmodel.JarvisState
import com.alphaorder.jarvisai.viewmodel.JarvisUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: JarvisUiState,
    hasMicPermission: Boolean,
    onMicTap: () -> Unit,
    onRequestPermission: () -> Unit,
    onSendTyped: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onDismissError: () -> Unit
) {
    var typedText by remember { mutableStateOf("") }

    Scaffold(
        containerColor = JarvisBackground,
        topBar = {
            TopAppBar(
                title = { Text("JARVIS", color = JarvisCyan) },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = JarvisCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = JarvisBackground)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(JarvisBackground)
                .padding(padding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Status label
            Text(
                text = statusLabel(uiState.state),
                color = Color.Gray,
                style = MaterialTheme.typography.labelSmall
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Orb
            OrbAnimation(
                state = uiState.state,
                micLevel = uiState.micLevel,
                modifier = Modifier.weight(0.5f, fill = false)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Live partial transcript while listening
            AnimatedVisibility(visible = uiState.partialText.isNotBlank()) {
                Text(
                    text = uiState.partialText,
                    color = JarvisCyan,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Error / offline banner
            AnimatedVisibility(visible = uiState.errorMessage != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A0000))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.errorMessage.orEmpty(),
                            color = Color(0xFFFF8A8A),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onDismissError) {
                            Text("OK", color = JarvisCyan)
                        }
                    }
                }
            }

            // Response / conversation area, typewriter-ish fade-in via Crossfade
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter
            ) {
                Crossfade(targetState = uiState.responseText, label = "response_fade") { text ->
                    Text(
                        text = text,
                        color = Color(0xFFE6F7FF),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }

            // Text input fallback + mic button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = typedText,
                    onValueChange = { typedText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message…", color = Color.Gray) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (typedText.isNotBlank()) {
                            onSendTyped(typedText)
                            typedText = ""
                        }
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                if (typedText.isNotBlank()) {
                    IconButton(onClick = {
                        onSendTyped(typedText)
                        typedText = ""
                    }) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = JarvisCyan)
                    }
                } else {
                    FloatingActionButton(
                        onClick = {
                            if (hasMicPermission) onMicTap() else onRequestPermission()
                        },
                        containerColor = if (uiState.state == JarvisState.LISTENING) JarvisCyan else Color(0xFF001D3D)
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Mic",
                            tint = if (uiState.state == JarvisState.LISTENING) JarvisBackground else JarvisCyan
                        )
                    }
                }
            }
        }
    }
}

private fun statusLabel(state: JarvisState): String = when (state) {
    JarvisState.IDLE -> "Idle"
    JarvisState.LISTENING -> "Listening…"
    JarvisState.THINKING -> "Thinking…"
    JarvisState.SPEAKING -> "Speaking…"
    JarvisState.OFFLINE -> "Offline"
    JarvisState.ERROR -> "Error"
}
