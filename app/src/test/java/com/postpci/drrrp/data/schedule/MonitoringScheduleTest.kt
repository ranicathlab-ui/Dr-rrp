package com.postpci.drrrp.data.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MonitoringScheduleTest {

    @Test
    fun daysPostPci_calculatesCorrectDelta() {
        val pciDate = LocalDate.of(2026, 8, 20)
        val sameDay = LocalDate.of(2026, 8, 20)
        val threeDaysLater = LocalDate.of(2026, 8, 23)

        assertEquals(0, MonitoringSchedule.daysPostPci(pciDate, sameDay))
        assertEquals(3, MonitoringSchedule.daysPostPci(pciDate, threeDaysLater))
    }

    @Test
    fun accessSiteCheck_dueOnlyFirstSevenDays() {
        val pciDate = LocalDate.of(2026, 8, 20)

        // Day 3 post-PCI -> due
        assertTrue(MonitoringSchedule.isDue(MonitoringSchedule.ACCESS_SITE_CHECK, pciDate, LocalDate.of(2026, 8, 23)))

        // Day 7 post-PCI -> due
        assertTrue(MonitoringSchedule.isDue(MonitoringSchedule.ACCESS_SITE_CHECK, pciDate, LocalDate.of(2026, 8, 27)))

        // Day 10 post-PCI -> not due
        assertFalse(MonitoringSchedule.isDue(MonitoringSchedule.ACCESS_SITE_CHECK, pciDate, LocalDate.of(2026, 8, 30)))
    }

    @Test
    fun bloodPressure_dailyFirst14Days_thenTwiceWeekly() {
        val pciDate = LocalDate.of(2026, 8, 1) // Day 0

        // Day 10 post-PCI (Aug 11) -> daily window -> due
        assertTrue(MonitoringSchedule.isDue(MonitoringSchedule.BLOOD_PRESSURE, pciDate, LocalDate.of(2026, 8, 11)))

        // Day 20 post-PCI: Monday Aug 31 -> due (twice-weekly Monday/Thursday rule)
        val mondayAug31 = LocalDate.of(2026, 8, 31)
        assertTrue(MonitoringSchedule.isDue(MonitoringSchedule.BLOOD_PRESSURE, pciDate, mondayAug31))

        // Day 21 post-PCI: Tuesday Sep 1 -> not due
        val tuesdaySep1 = LocalDate.of(2026, 9, 1)
        assertFalse(MonitoringSchedule.isDue(MonitoringSchedule.BLOOD_PRESSURE, pciDate, tuesdaySep1))
    }

    @Test
    fun medicationsAndSymptoms_alwaysDueDaily() {
        val pciDate = LocalDate.of(2026, 5, 1)
        val monthsLater = LocalDate.of(2026, 9, 1)

        assertTrue(MonitoringSchedule.isDue(MonitoringSchedule.MEDICATIONS_TAKEN, pciDate, monthsLater))
        assertTrue(MonitoringSchedule.isDue(MonitoringSchedule.PALPITATIONS_SYNCOPE, pciDate, monthsLater))
    }

    @Test
    fun dueFieldsFor_returnsNonEmptyList() {
        val pciDate = LocalDate.of(2026, 8, 28)
        val today = LocalDate.of(2026, 9, 1)

        val due = MonitoringSchedule.dueFieldsFor(pciDate, today)
        assertTrue(due.contains(MonitoringSchedule.MEDICATIONS_TAKEN))
        assertTrue(due.contains(MonitoringSchedule.ACCESS_SITE_CHECK)) // Day 4 post-PCI
        assertTrue(due.contains(MonitoringSchedule.BLOOD_PRESSURE))
    }
}
