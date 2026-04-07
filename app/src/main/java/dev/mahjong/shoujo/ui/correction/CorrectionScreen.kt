package dev.mahjong.shoujo.ui.correction

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mahjong.shoujo.domain.ir.TileSlot

/**
 * Correction screen.
 *
 * Shows the CV model's detected tiles in their spatial order.
 * Low-confidence tiles are visually flagged.
 * The user can tap any tile to change its identity.
 *
 * Phase 1: wire up real CV results from [CorrectionViewModel].
 * Phase 2: connect [CorrectionViewModel.onTileConfirmed] to [CorrectionLogger].
 */
@Composable
fun CorrectionScreen(
    onConfirmed: () -> Unit,
    onBack: () -> Unit,
    viewModel: CorrectionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) onConfirmed()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Tiles") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
        ) {
            Text(
                text = "Check each tile. Highlighted tiles are low-confidence.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(16.dp))

            // Scrollable row of tile slots
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(uiState.slots) { index, slot ->
                    TileSlotCard(
                        slot = slot,
                        onClick = { viewModel.onSlotTapped(index) },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Tile picker shown when a slot is selected
            uiState.editingSlotIndex?.let { idx ->
                TilePicker(
                    currentSlot = uiState.slots[idx],
                    onTileSelected = { tileId -> viewModel.onTileSelected(idx, tileId) },
                    onDismiss     = { viewModel.onDismissEditor() },
                )
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = { viewModel.onConfirmAll() },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.allSlotsConfirmed,
            ) {
                Text("Confirm Hand")
            }
        }
    }
}

@Composable
private fun TileSlotCard(slot: TileSlot, onClick: () -> Unit) {
    val borderColor = when {
        slot.isLowConfidence -> MaterialTheme.colorScheme.error
        slot.wasCorrected    -> MaterialTheme.colorScheme.tertiary
        else                 -> MaterialTheme.colorScheme.outline
    }

    OutlinedCard(
        onClick = onClick,
        border = BorderStroke(2.dp, borderColor),
        modifier = Modifier.width(56.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(4.dp),
        ) {
            Text(
                text = slot.confirmedTile?.toString() ?: slot.modelSuggestion?.toString() ?: "?",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (slot.isLowConfidence) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Low confidence",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@Composable
private fun TilePicker(
    currentSlot: TileSlot,
    onTileSelected: (dev.mahjong.shoujo.cv.api.TileId) -> Unit,
    onDismiss: () -> Unit,
) {
    // TODO(Phase 1): implement a full tile picker grid (4 rows × tiles per suit)
    // For now, a placeholder card
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Tile picker — TODO(Phase 1)")
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    }
}
