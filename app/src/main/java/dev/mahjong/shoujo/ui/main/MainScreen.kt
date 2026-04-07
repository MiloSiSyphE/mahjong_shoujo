package dev.mahjong.shoujo.ui.main

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mahjong.shoujo.cv.api.model.TileRecognitionResult

/**
 * Entry screen.
 *
 * Phase 0: Only [onStartManualEntry] is wired — scoring without any CV.
 * Phase 1: [onImageRecognized] becomes reachable after baseline adapter is implemented.
 */
@Composable
fun MainScreen(
    onStartManualEntry: () -> Unit,
    onImageRecognized: (TileRecognitionResult) -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.onGalleryImageSelected(it) } }

    // Navigate out when recognition completes
    LaunchedEffect(uiState) {
        if (uiState is MainUiState.RecognitionComplete) {
            onImageRecognized((uiState as MainUiState.RecognitionComplete).result)
        }
        if (uiState is MainUiState.ManualEntry) {
            onStartManualEntry()
        }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "麻雀 Scorer",
                style = MaterialTheme.typography.headlineLarge,
            )

            Spacer(Modifier.height(8.dp))

            // Show which model is active (helpful during development)
            if (viewModel.modelIsReady) {
                Text(
                    text = "Model: ${viewModel.modelInfo.modelId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(48.dp))

            when (uiState) {
                is MainUiState.Recognizing -> CircularProgressIndicator()
                is MainUiState.Error -> {
                    Text(
                        text = "Error: ${(uiState as MainUiState.Error).message}",
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(16.dp))
                }
                else -> Unit
            }

            // Phase 1: image-based flow (disabled in Phase 0)
            OutlinedButton(
                onClick = { galleryLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth(),
                enabled = viewModel.modelIsReady,  // grayed out until Phase 1 adapter loads
            ) {
                Icon(Icons.Default.Image, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Load image / screenshot")
            }

            Spacer(Modifier.height(16.dp))

            // Phase 0: always available
            Button(
                onClick = { viewModel.onStartManualEntry() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Enter hand manually")
            }

            if (!viewModel.modelIsReady) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Image recognition not available yet (Phase 1 TODO)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
