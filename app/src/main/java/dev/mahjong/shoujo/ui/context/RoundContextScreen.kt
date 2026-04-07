package dev.mahjong.shoujo.ui.context

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Collects all situational information needed for scoring:
 *   - Round wind / seat wind
 *   - Honba count, riichi sticks on table
 *   - Dora indicators and ura-dora indicators
 *   - Win type (tsumo / ron)
 *   - Riichi flags (riichi, double-riichi, ippatsu)
 *   - Special win conditions (haitei, houtei, rinshan, chankan)
 *
 * The VM validates the combination before enabling the Score button.
 * (e.g., ippatsu requires riichi; haitei and houtei are mutually exclusive)
 */
@Composable
fun RoundContextScreen(
    onScore: () -> Unit,
    onBack: () -> Unit,
    viewModel: RoundContextViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.readyToScore) {
        if (state.readyToScore) onScore()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Round Context") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Wind selection ─────────────────────────────────────────────────
            WindSelector(
                label = "Round Wind",
                selected = state.roundWind,
                onSelect = viewModel::onRoundWindChanged,
            )
            WindSelector(
                label = "Seat Wind",
                selected = state.seatWind,
                onSelect = viewModel::onSeatWindChanged,
            )

            // ── Win type ───────────────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.isTsumo,
                    onClick  = { viewModel.onIsTsumoChanged(true) },
                    label    = { Text("Tsumo") },
                )
                FilterChip(
                    selected = !state.isTsumo,
                    onClick  = { viewModel.onIsTsumoChanged(false) },
                    label    = { Text("Ron") },
                )
            }

            // ── Counters ───────────────────────────────────────────────────────
            CounterRow("Honba", state.honbaCount, viewModel::onHonbaChanged)
            CounterRow("Riichi sticks on table", state.riichiSticks, viewModel::onRiichiSticksChanged)

            // ── Riichi flags ───────────────────────────────────────────────────
            FlagRow("Riichi",         state.isRiichi,       viewModel::onRiichiChanged)
            FlagRow("Double Riichi",  state.isDoubleRiichi, viewModel::onDoubleRiichiChanged)
            FlagRow("Ippatsu",        state.isIppatsu,      viewModel::onIppatsuChanged)

            // ── Special wins ───────────────────────────────────────────────────
            FlagRow("Haitei (last wall draw)", state.isHaitei, viewModel::onHaiteiChanged)
            FlagRow("Houtei (last discard)",   state.isHoutei, viewModel::onHouteiChanged)
            FlagRow("Rinshan (after kan)",     state.isRinshan, viewModel::onRinshanChanged)
            FlagRow("Chankan (rob a kan)",     state.isChankan, viewModel::onChankanChanged)

            // ── Dora indicators ────────────────────────────────────────────────
            Text("Dora Indicators", style = MaterialTheme.typography.labelLarge)
            // TODO(Phase 0): add dora indicator tile picker (reuse TilePicker from CorrectionScreen)
            Text("(dora picker — TODO)", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text("Ura Dora Indicators", style = MaterialTheme.typography.labelLarge)
            // TODO(Phase 0): add ura-dora picker (only shown when isRiichi)

            Spacer(Modifier.height(16.dp))

            // Validation error
            state.validationError?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick  = viewModel::onSubmit,
                modifier = Modifier.fillMaxWidth(),
                enabled  = state.validationError == null,
            ) {
                Text("Calculate Score")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Small reusable input widgets ───────────────────────────────────────────────

@Composable
private fun WindSelector(
    label: String,
    selected: dev.mahjong.shoujo.domain.model.Wind,
    onSelect: (dev.mahjong.shoujo.domain.model.Wind) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            dev.mahjong.shoujo.domain.model.Wind.entries.forEach { wind ->
                FilterChip(
                    selected = selected == wind,
                    onClick  = { onSelect(wind) },
                    label    = { Text(wind.name.take(1)) },  // E / S / W / N
                )
            }
        }
    }
}

@Composable
private fun CounterRow(label: String, value: Int, onChanged: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row {
            TextButton(onClick = { if (value > 0) onChanged(value - 1) }) { Text("−") }
            Text("$value", modifier = Modifier.padding(horizontal = 8.dp))
            TextButton(onClick = { onChanged(value + 1) }) { Text("+") }
        }
    }
}

@Composable
private fun FlagRow(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChanged)
    }
}
