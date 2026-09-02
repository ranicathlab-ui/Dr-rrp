package com.postpci.drrrp.ui.trends

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.postpci.drrrp.DrRrpApplication
import com.postpci.drrrp.ui.common.DrRrpScaffold
import com.postpci.drrrp.ui.theme.AccentYellowGold
import com.postpci.drrrp.ui.theme.BorderHairline
import com.postpci.drrrp.ui.theme.HeaderBrightBlue
import com.postpci.drrrp.ui.theme.SurfaceCard
import com.postpci.drrrp.ui.theme.TextPrimary

private data class TrendCard(val title: String, val series: List<ChartSeries>, val normalRange: ClosedFloatingPointRange<Float>?)

/** BP, heart rate, weight, SpO2 — one scrollable chart per metric, each independently swipeable. */
@Composable
fun TrendsScreen(application: DrRrpApplication, patientId: String) {
    val viewModel: TrendsViewModel = viewModel(
        // Namespaced by class, not bare patientId — see EmergencyGateViewModel's comment on
        // ViewModelStore key collisions across sibling ViewModels sharing the same store.
        key = "Trends:$patientId",
        factory = viewModelFactory { initializer { TrendsViewModel(application.database, patientId) } },
    )
    val state by viewModel.uiState.collectAsState()

    val cards = listOf(
        TrendCard(
            "Blood pressure (mmHg)",
            listOf(
                ChartSeries("Systolic", AccentYellowGold, state.systolic),
                ChartSeries("Diastolic", HeaderBrightBlue, state.diastolic),
            ),
            90f..180f,
        ),
        TrendCard("Resting heart rate (bpm)", listOf(ChartSeries("HR", AccentYellowGold, state.heartRate)), 50f..90f),
        TrendCard("Weight (kg)", listOf(ChartSeries("Weight", AccentYellowGold, state.weight)), null),
        TrendCard("SpO2 (%)", listOf(ChartSeries("SpO2", AccentYellowGold, state.spo2)), 94f..100f),
    )

    DrRrpScaffold(title = "Trends") { modifier ->
        LazyColumn(modifier = modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            items(cards) { card ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .background(SurfaceCard, RoundedCornerShape(16.dp))
                        .border(1.dp, BorderHairline, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                ) {
                    Text(card.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    if (card.series.size > 1) {
                        Row(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                            card.series.forEach { s ->
                                LegendDot(s)
                            }
                        }
                    }
                    LineChart(
                        series = card.series,
                        normalRange = card.normalRange,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(series: ChartSeries) {
    Row(modifier = Modifier.padding(end = 16.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp, end = 6.dp)
                .size(10.dp)
                .background(series.color, RoundedCornerShape(50)),
        )
        Text(series.label, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
    }
}
