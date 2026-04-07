package dev.mahjong.shoujo.ui.result

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mahjong.shoujo.domain.model.LimitHand
import dev.mahjong.shoujo.domain.model.ScoringResult
import dev.mahjong.shoujo.domain.model.YakuResult

/**
 * Displays the full scoring output:
 *   - Limit hand banner (mangan / haneman / baiman / yakuman)
 *   - Yaku list with han values
 *   - Dora + ura-dora count
 *   - Fu (when applicable)
 *   - Total han
 *   - Points (ron amount or tsumo breakdown)
 *   - Step-by-step explanation
 */
@Composable
fun ScoringResultScreen(
    onNewHand: () -> Unit,
    viewModel: ScoringResultViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Score") }) },
        bottomBar = {
            Button(
                onClick  = onNewHand,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text("New Hand")
            }
        }
    ) { innerPadding ->
        when (val state = uiState) {
            is ScoringResultUiState.Loading -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is ScoringResultUiState.Ready -> ResultContent(
                result      = state.result,
                innerPadding = innerPadding,
            )

            is ScoringResultUiState.Error -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = "Error: ${state.message}",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ResultContent(result: ScoringResult, innerPadding: PaddingValues) {
    LazyColumn(
        contentPadding = innerPadding,
        modifier        = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Limit hand banner
        result.limitHand?.let { limit ->
            item {
                LimitHandBanner(limit)
            }
        }

        // Yaku list
        item { Text("Yaku", style = MaterialTheme.typography.titleMedium) }
        if (result.yakuList.isEmpty()) {
            item {
                Text(
                    "No yaku — chombo!",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            items(result.yakuList) { yakuResult ->
                YakuRow(yakuResult)
            }
        }

        // Dora
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Dora")
                Text("${result.doraCount} han")
            }
        }
        if (result.uraDoraCount > 0) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Ura Dora")
                    Text("${result.uraDoraCount} han")
                }
            }
        }

        item { HorizontalDivider() }

        // Totals
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total Han", style = MaterialTheme.typography.titleMedium)
                Text("${result.totalHan} han", style = MaterialTheme.typography.titleMedium)
            }
        }
        result.fu?.let { fu ->
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Fu")
                    Text("$fu fu")
                }
            }
        }

        item { HorizontalDivider() }

        // Points
        item {
            Text("Points", style = MaterialTheme.typography.titleMedium)
        }
        result.ronPayment?.let {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Ron")
                    Text("$it pts")
                }
            }
        }
        result.tsumoPayments?.let { tp ->
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Dealer pays")
                    Text("${tp.dealerPays} pts")
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Each non-dealer pays")
                    Text("${tp.nonDealerPays} pts")
                }
            }
        }

        item { HorizontalDivider() }

        // Explanation
        item { Text("Explanation", style = MaterialTheme.typography.titleMedium) }
        items(result.explanation) { line ->
            Text(line, style = MaterialTheme.typography.bodySmall)
        }

        item { Spacer(Modifier.height(80.dp)) } // bottom button clearance
    }
}

@Composable
private fun LimitHandBanner(limit: LimitHand) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(
                text  = limit.japaneseName,
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}

@Composable
private fun YakuRow(yakuResult: YakuResult) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(yakuResult.yaku.japaneseName)
            Text(
                yakuResult.yaku.englishName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("${yakuResult.han} han")
    }
}
