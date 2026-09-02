package com.postpci.drrrp.ui.today

import com.postpci.drrrp.data.local.entity.AlertEntity
import com.postpci.drrrp.data.local.entity.DailyEntryEntity
import java.time.LocalDate

data class MedItem(val key: String, val label: String, val isDapt: Boolean = false)

data class TodayUiState(
    val isLoading: Boolean = true,
    val hasBaseline: Boolean = false,
    val patientName: String = "",
    val pciDate: LocalDate? = null,
    val dayNPostPci: Int? = null,
    /** Field keys due today per the monitoring schedule, in table order. */
    val dueFields: List<String> = emptyList(),
    val todayEntry: DailyEntryEntity? = null,
    val medications: List<MedItem> = emptyList(),
    val unreviewedAlerts: List<AlertEntity> = emptyList(),
)
