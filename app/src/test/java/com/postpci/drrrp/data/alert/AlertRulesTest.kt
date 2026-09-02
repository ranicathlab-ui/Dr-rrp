package com.postpci.drrrp.data.alert

import com.postpci.drrrp.data.local.entity.BleedingEventEntity
import com.postpci.drrrp.data.local.entity.DailyEntryEntity
import com.postpci.drrrp.data.model.AlertSeverity
import com.postpci.drrrp.data.model.BleedingSeverity
import com.postpci.drrrp.data.model.ChestPainType
import com.postpci.drrrp.data.model.NyhaClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class AlertRulesTest {

    @Test
    fun restingHeartRate_normalRange_noAlert() {
        assertNull(AlertRules.checkRestingHeartRate(70))
        assertNull(AlertRules.checkRestingHeartRate(50))
        assertNull(AlertRules.checkRestingHeartRate(90))
    }

    @Test
    fun restingHeartRate_outOfRange_returnsRoutineAlert() {
        val lowAlert = AlertRules.checkRestingHeartRate(45)
        assertNotNull(lowAlert)
        assertEquals(AlertSeverity.ROUTINE, lowAlert?.severity)

        val highAlert = AlertRules.checkRestingHeartRate(105)
        assertNotNull(highAlert)
        assertEquals(AlertSeverity.ROUTINE, highAlert?.severity)
    }

    @Test
    fun bloodPressure_normal_noAlert() {
        assertNull(AlertRules.checkBloodPressure(120, 75))
    }

    @Test
    fun bloodPressure_systolicOutOfRange_returnsRoutineAlert() {
        val hypotensive = AlertRules.checkBloodPressure(85, 55)
        assertNotNull(hypotensive)
        assertEquals(AlertSeverity.ROUTINE, hypotensive?.severity)

        val hypertensive = AlertRules.checkBloodPressure(190, 100)
        assertNotNull(hypertensive)
        assertEquals(AlertSeverity.ROUTINE, hypertensive?.severity)
    }

    @Test
    fun spo2_thresholds() {
        assertNull(AlertRules.checkSpo2(98))
        assertNull(AlertRules.checkSpo2(94))

        val alert = AlertRules.checkSpo2(91)
        assertNotNull(alert)
        assertEquals(AlertSeverity.ROUTINE, alert?.severity)
    }

    @Test
    fun weightGain_overThreeDays() {
        val today = LocalDate.of(2026, 9, 1)
        val pastEntries = listOf(
            DailyEntryEntity(
                id = "1",
                patientId = "p1",
                entryDate = today.minusDays(3),
                weightKg = 70.0,
                createdAt = 0,
                updatedAt = 0,
            ),
            DailyEntryEntity(
                id = "2",
                patientId = "p1",
                entryDate = today.minusDays(2),
                weightKg = 71.0,
                createdAt = 0,
                updatedAt = 0,
            ),
        )

        // Gain of 1.5 kg -> no alert
        assertNull(AlertRules.checkWeightGain(71.5, pastEntries))

        // Gain of 2.5 kg (>2.0 kg) -> routine alert
        val alert = AlertRules.checkWeightGain(72.5, pastEntries)
        assertNotNull(alert)
        assertEquals(AlertSeverity.ROUTINE, alert?.severity)
    }

    @Test
    fun accessSite_symptomsTriggerAlert() {
        assertNull(AlertRules.checkAccessSite(bleeding = false, swelling = false, pain = false, discolouration = false))

        val alert = AlertRules.checkAccessSite(bleeding = true, swelling = false, pain = true, discolouration = false)
        assertNotNull(alert)
        assertEquals(AlertSeverity.ROUTINE, alert?.severity)
    }

    @Test
    fun daptTaken_alertOnMissedDose() {
        assertNull(AlertRules.checkDaptTaken(taken = true))

        val alert = AlertRules.checkDaptTaken(taken = false)
        assertNotNull(alert)
        assertEquals(AlertSeverity.ROUTINE, alert?.severity)
    }

    @Test
    fun chestPain_restIsEmergency_exertionalIsRoutine() {
        assertNull(AlertRules.checkChestPain(0, null))

        val exertional = AlertRules.checkChestPain(2, ChestPainType.EXERTIONAL)
        assertNotNull(exertional)
        assertEquals(AlertSeverity.ROUTINE, exertional?.severity)

        val restPain = AlertRules.checkChestPain(1, ChestPainType.REST)
        assertNotNull(restPain)
        assertEquals(AlertSeverity.EMERGENCY, restPain?.severity)
    }

    @Test
    fun symptomFlags_syncopeAndNearSyncopeAreEmergency() {
        assertNull(AlertRules.checkSymptomFlags(palpitations = false, syncope = false, nearSyncope = false))

        val palpitationsAlert = AlertRules.checkSymptomFlags(palpitations = true, syncope = false, nearSyncope = false)
        assertNotNull(palpitationsAlert)
        assertEquals(AlertSeverity.ROUTINE, palpitationsAlert?.severity)

        val syncopeAlert = AlertRules.checkSymptomFlags(palpitations = false, syncope = true, nearSyncope = false)
        assertNotNull(syncopeAlert)
        assertEquals(AlertSeverity.EMERGENCY, syncopeAlert?.severity)

        val nearSyncopeAlert = AlertRules.checkSymptomFlags(palpitations = false, syncope = false, nearSyncope = true)
        assertNotNull(nearSyncopeAlert)
        assertEquals(AlertSeverity.EMERGENCY, nearSyncopeAlert?.severity)
    }

    @Test
    fun bleedingEvent_medicalAttentionIsEmergency() {
        val minorEvent = BleedingEventEntity(
            id = "b1",
            patientId = "p1",
            timestamp = System.currentTimeMillis(),
            site = "gums",
            severity = BleedingSeverity.MINOR,
            neededMedicalAttention = false,
        )
        val routineAlert = AlertRules.checkBleedingEvent(minorEvent)
        assertNotNull(routineAlert)
        assertEquals(AlertSeverity.ROUTINE, routineAlert?.severity)

        val majorEvent = BleedingEventEntity(
            id = "b2",
            patientId = "p1",
            timestamp = System.currentTimeMillis(),
            site = "access site",
            severity = BleedingSeverity.MAJOR,
            neededMedicalAttention = true,
        )
        val emergencyAlert = AlertRules.checkBleedingEvent(majorEvent)
        assertNotNull(emergencyAlert)
        assertEquals(AlertSeverity.EMERGENCY, emergencyAlert?.severity)
    }

    @Test
    fun breathlessness_nyhaClassEscalation() {
        assertNull(AlertRules.checkBreathlessness(null))
        assertNull(AlertRules.checkBreathlessness(NyhaClass.I))

        val class2 = AlertRules.checkBreathlessness(NyhaClass.II)
        assertNotNull(class2)
        assertEquals(AlertSeverity.ROUTINE, class2?.severity)

        val class3 = AlertRules.checkBreathlessness(NyhaClass.III)
        assertNotNull(class3)
        assertEquals(AlertSeverity.ROUTINE, class3?.severity)

        val class4 = AlertRules.checkBreathlessness(NyhaClass.IV)
        assertNotNull(class4)
        assertEquals(AlertSeverity.EMERGENCY, class4?.severity)
    }

    /**
     * Consolidated regression guard for DR RRP step 8's top priority: the exact four conditions
     * that must escalate to the full-screen emergency takeover, and — just as important — that
     * nothing else does. A future threshold tweak that accidentally flips a check to EMERGENCY
     * (or silently drops one of these four to ROUTINE) fails this test even if the individual
     * per-field tests above happen not to catch it.
     */
    @Test
    fun exactlyTheFourSpecEmergencyConditions_areEmergency_everythingElseIsNot() {
        val emergencyDrafts = listOfNotNull(
            AlertRules.checkChestPain(1, ChestPainType.REST),
            AlertRules.checkSymptomFlags(palpitations = false, syncope = true, nearSyncope = false),
            AlertRules.checkSymptomFlags(palpitations = false, syncope = false, nearSyncope = true),
            AlertRules.checkBleedingEvent(
                BleedingEventEntity(id = "b", patientId = "p1", timestamp = 0, site = "access site", severity = BleedingSeverity.MAJOR, neededMedicalAttention = true),
            ),
            AlertRules.checkBreathlessness(NyhaClass.IV),
        )
        assertEquals(5, emergencyDrafts.size) // 5 calls, one severity check each
        emergencyDrafts.forEach { assertEquals(AlertSeverity.EMERGENCY, it.severity) }

        val everythingElse = listOfNotNull(
            AlertRules.checkRestingHeartRate(45),
            AlertRules.checkBloodPressure(85, 55),
            AlertRules.checkSpo2(91),
            AlertRules.checkAccessSite(bleeding = true, swelling = false, pain = false, discolouration = false),
            AlertRules.checkDaptTaken(false),
            AlertRules.checkChestPain(1, ChestPainType.EXERTIONAL),
            AlertRules.checkSymptomFlags(palpitations = true, syncope = false, nearSyncope = false),
            AlertRules.checkBreathlessness(NyhaClass.II),
            AlertRules.checkBreathlessness(NyhaClass.III),
            AlertRules.checkBleedingEvent(
                BleedingEventEntity(id = "b2", patientId = "p1", timestamp = 0, site = "gums", severity = BleedingSeverity.MINOR, neededMedicalAttention = false),
            ),
        )
        everythingElse.forEach { assertEquals(AlertSeverity.ROUTINE, it.severity) }
    }
}
