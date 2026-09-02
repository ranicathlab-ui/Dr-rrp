package com.postpci.drrrp.ui.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpci.drrrp.data.local.DrRrpDatabase
import com.postpci.drrrp.data.schedule.MonitoringSchedule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class TrendsUiState(
    val isLoading: Boolean = true,
    val heartRate: List<ChartPoint> = emptyList(),
    val systolic: List<ChartPoint> = emptyList(),
    val diastolic: List<ChartPoint> = emptyList(),
    val weight: List<ChartPoint> = emptyList(),
    val spo2: List<ChartPoint> = emptyList(),
)

class TrendsViewModel(database: DrRrpDatabase, patientId: String) : ViewModel() {
    val uiState: StateFlow<TrendsUiState> = combine(
        database.patientBaselineDao().observe(patientId),
        database.dailyEntryDao().observeAllForPatient(patientId),
    ) { baseline, entries ->
        val pciDate = baseline?.procedural?.pciDate ?: LocalDate.now()
        fun dayFor(date: LocalDate) = MonitoringSchedule.daysPostPci(pciDate, date)

        TrendsUiState(
            isLoading = false,
            heartRate = entries.mapNotNull { e ->
                e.restingHeartRate?.let { ChartPoint(dayFor(e.entryDate), it.toFloat(), it !in 50..90) }
            },
            systolic = entries.mapNotNull { e ->
                e.bpSystolic?.let { ChartPoint(dayFor(e.entryDate), it.toFloat(), it < 90 || it > 180) }
            },
            diastolic = entries.mapNotNull { e ->
                e.bpDiastolic?.let { ChartPoint(dayFor(e.entryDate), it.toFloat()) }
            },
            weight = entries.mapNotNull { e ->
                e.weightKg?.let { ChartPoint(dayFor(e.entryDate), it.toFloat()) }
            },
            spo2 = entries.mapNotNull { e ->
                e.spo2?.let { ChartPoint(dayFor(e.entryDate), it.toFloat(), it < 94) }
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrendsUiState())
}
