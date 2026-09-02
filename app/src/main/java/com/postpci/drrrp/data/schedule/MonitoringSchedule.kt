package com.postpci.drrrp.data.schedule

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** How often a field is due once it's past its [ScheduleRule.dailyUntilDay] window. */
enum class Frequency {
    /** Stays daily indefinitely (e.g. the medication checklist, symptom-triggered questions). */
    DAILY,

    /** Monday and Thursday. */
    TWICE_WEEKLY,

    /** No longer due at all past the daily window (e.g. access-site checks, once healed). */
    NONE,
}

/**
 * One row of the monitoring-cadence table: how long [fieldKey] is due every day after PCI,
 * and how often it's due after that. Field keys match [com.postpci.drrrp.data.local.entity.DailyEntryEntity]
 * property names.
 *
 * Cadences marked "spec" come directly from the product requirements; cadences marked
 * "default" weren't specified there and use the same 4-week daily / twice-weekly taper as
 * breathlessness — adjust here if the clinic wants a different schedule per field.
 */
data class ScheduleRule(
    val fieldKey: String,
    val dailyUntilDay: Int,
    val frequencyAfter: Frequency,
)

object MonitoringSchedule {
    const val CHEST_PAIN = "chestPain"
    const val BREATHLESSNESS = "nyhaClass"
    const val RESTING_HEART_RATE = "restingHeartRate"
    const val BLOOD_PRESSURE = "bloodPressure"
    const val WEIGHT = "weight"
    const val SPO2 = "spo2"
    const val ACCESS_SITE_CHECK = "accessSiteCheck"
    const val MEDICATIONS_TAKEN = "medicationsTaken"
    const val ACTIVITY = "activity"
    const val PALPITATIONS_SYNCOPE = "palpitationsSyncope"

    /** Always due, never tapers — daily automatic questions with no stated end date. */
    private const val NO_TAPER = Int.MAX_VALUE

    val rules: List<ScheduleRule> = listOf(
        ScheduleRule(CHEST_PAIN, dailyUntilDay = 28, frequencyAfter = Frequency.TWICE_WEEKLY), // default
        ScheduleRule(BREATHLESSNESS, dailyUntilDay = 28, frequencyAfter = Frequency.TWICE_WEEKLY), // spec
        ScheduleRule(RESTING_HEART_RATE, dailyUntilDay = 28, frequencyAfter = Frequency.TWICE_WEEKLY), // default
        ScheduleRule(BLOOD_PRESSURE, dailyUntilDay = 14, frequencyAfter = Frequency.TWICE_WEEKLY), // spec
        ScheduleRule(WEIGHT, dailyUntilDay = 28, frequencyAfter = Frequency.TWICE_WEEKLY), // default
        ScheduleRule(SPO2, dailyUntilDay = 28, frequencyAfter = Frequency.TWICE_WEEKLY), // spec (daily window); after-cadence defaulted
        ScheduleRule(ACCESS_SITE_CHECK, dailyUntilDay = 7, frequencyAfter = Frequency.NONE), // spec
        ScheduleRule(MEDICATIONS_TAKEN, dailyUntilDay = NO_TAPER, frequencyAfter = Frequency.DAILY),
        ScheduleRule(ACTIVITY, dailyUntilDay = 28, frequencyAfter = Frequency.TWICE_WEEKLY), // default
        ScheduleRule(PALPITATIONS_SYNCOPE, dailyUntilDay = NO_TAPER, frequencyAfter = Frequency.DAILY),
    )

    private val byKey = rules.associateBy { it.fieldKey }

    fun ruleFor(fieldKey: String): ScheduleRule? = byKey[fieldKey]

    /** Days since [pciDate], where PCI day itself is day 0. Negative before the procedure. */
    fun daysPostPci(pciDate: LocalDate, today: LocalDate = LocalDate.now()): Int =
        ChronoUnit.DAYS.between(pciDate, today).toInt()

    /** Whether [fieldKey] is due on [today], given the patient's [pciDate]. */
    fun isDue(fieldKey: String, pciDate: LocalDate, today: LocalDate = LocalDate.now()): Boolean {
        val rule = byKey[fieldKey] ?: return false
        val dayN = daysPostPci(pciDate, today)
        if (dayN < 0) return false
        if (dayN <= rule.dailyUntilDay) return true
        return when (rule.frequencyAfter) {
            Frequency.DAILY -> true
            Frequency.TWICE_WEEKLY -> today.dayOfWeek == DayOfWeek.MONDAY || today.dayOfWeek == DayOfWeek.THURSDAY
            Frequency.NONE -> false
        }
    }

    /** All field keys currently due for a patient, in table order — drives the Today screen grid. */
    fun dueFieldsFor(pciDate: LocalDate, today: LocalDate = LocalDate.now()): List<String> =
        rules.filter { isDue(it.fieldKey, pciDate, today) }.map { it.fieldKey }
}
