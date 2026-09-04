package com.postpci.drrrp.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.postpci.drrrp.data.model.AccessSite
import com.postpci.drrrp.data.model.CulpritVessel
import com.postpci.drrrp.data.model.KillipClass
import com.postpci.drrrp.data.model.PreferredLanguage
import com.postpci.drrrp.data.model.Sex
import com.postpci.drrrp.data.model.SmartphoneLiteracy
import com.postpci.drrrp.data.model.SmokingStatus
import com.postpci.drrrp.data.model.StemiTerritory
import com.postpci.drrrp.data.model.SyncStatus
import com.postpci.drrrp.data.model.ThrombusBurden
import com.postpci.drrrp.data.model.TimiFlow
import java.time.LocalDate

/**
 * Recorded once at onboarding, editable only by clinic staff. Grouped into five embedded
 * sections that mirror the five steps of the staff baseline wizard, so the wizard can save
 * (and resume) one section at a time without touching the others.
 */
@Entity(tableName = "patient_baseline")
data class PatientBaselineEntity(
    @PrimaryKey val patientId: String,

    @Embedded(prefix = "demo_") val demographics: Demographics,
    @Embedded(prefix = "proc_") val procedural: ProceduralDetails,
    @Embedded(prefix = "labs_") val labsAndVitals: LabsAndVitals,
    @Embedded(prefix = "meds_") val medicationsAndFollowUp: MedicationsAndFollowUp,
    @Embedded(prefix = "soc_") val social: Social,

    /** Wizard step (0-4) staff last completed, so onboarding can be exited and resumed. */
    val lastCompletedWizardStep: Int = -1,
    val isFinalized: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
)

data class Demographics(
    val name: String = "",
    val age: Int? = null,
    val sex: Sex? = null,
    val contactNumber: String = "",
    /** Optional — when set, the patient logs in with this real address instead of a synthetic
     *  one, and gets a Firebase password-reset email sent to it right after the invite is
     *  created (see FirebaseAuthGateway.createPatientInvite). Left blank, invite creation falls
     *  back to the synthetic-email + on-screen temporary password flow, for patients without
     *  email access. */
    val email: String = "",
    /** Freeform, staff-entered semicolon-separated list. */
    val comorbidities: String = "",
    /** Home medications prior to PCI — distinct from the discharge regimen below. */
    val currentMedications: String = "",
)

data class ProceduralDetails(
    /** Anchor date for "Day N post-PCI" and the whole monitoring schedule. */
    val pciDate: LocalDate? = null,
    val stemiTerritory: StemiTerritory? = null,
    val symptomOnsetToDoorMinutes: Int? = null,
    val doorToBalloonMinutes: Int? = null,
    val culpritVessel: CulpritVessel? = null,
    val preProceduralTimiFlow: TimiFlow? = null,
    val postProceduralTimiFlow: TimiFlow? = null,
    val stentCount: Int? = null,
    val stentType: String? = null,
    val stentLengthMm: Double? = null,
    val stentDiameterMm: Double? = null,
    val thrombusBurden: ThrombusBurden? = null,
    val noReflow: Boolean = false,
    val nonCulpritDiseasePresent: Boolean = false,
    val stagedPciDate: LocalDate? = null,
    val accessSite: AccessSite? = null,
    val accessSiteComplications: String? = null,
    val mechanicalComplicationMr: Boolean = false,
    val mechanicalComplicationVsr: Boolean = false,
    val mechanicalComplicationFreeWallRupture: Boolean = false,
    val mechanicalComplicationEffusion: Boolean = false,
    val arrhythmiaVtVf: Boolean = false,
    val arrhythmiaAvBlock: Boolean = false,
    val arrhythmiaTempPacing: Boolean = false,
)

data class LabsAndVitals(
    val peakTroponin: Double? = null,
    val peakCkMb: Double? = null,
    val lvefPercent: Int? = null,
    val echoDate: LocalDate? = null,
    val killipClass: KillipClass? = null,
    val baselineCreatinine: Double? = null,
    val egfr: Double? = null,
    val contrastVolumeMl: Int? = null,
    val dischargeHaemoglobin: Double? = null,
    val plateletCount: Int? = null,
    val ldlC: Double? = null,
    val statinDose: String? = null,
    /** Only meaningful if the patient is diabetic; leave null otherwise. */
    val hba1c: Double? = null,
    val dischargeSystolicBp: Int? = null,
    val dischargeDiastolicBp: Int? = null,
    val dischargeHeartRate: Int? = null,
    val dischargeWeightKg: Double? = null,
    val dischargeEcgRhythm: String? = null,
    val dischargeEcgQrsMs: Int? = null,
    val dischargeEcgStChanges: String? = null,
    val dischargeEcgQWaves: Boolean = false,
)

data class MedicationsAndFollowUp(
    val daptAgent: String? = null,
    val daptDose: String? = null,
    val daptPlannedDurationMonths: Int? = null,
    val betaBlockerDose: String? = null,
    val aceiArbArniDose: String? = null,
    val mraDose: String? = null,
    val sglt2iDose: String? = null,
    val cardiacRehabReferred: Boolean = false,
    val opdFollowUpDate: LocalDate? = null,
    val echoFollowUpDate: LocalDate? = null,
    val lipidRecheckDate: LocalDate? = null,
    val nextFollowUpDate: Long? = null,
    val nextEchoDate: Long? = null,
    val followUpStatus: String? = null, // "PENDING", "ATTENDED", "RESCHEDULED", "UNREACHABLE", "UNWELL", "OTHER"
    val followUpReason: String? = null,
)

data class Social(
    val smokingStatus: SmokingStatus? = null,
    val occupation: String? = null,
    val expectedReturnToWorkDate: LocalDate? = null,
    val livesAlone: Boolean? = null,
    val hasCaregiver: Boolean? = null,
    val smartphoneLiteracy: SmartphoneLiteracy? = null,
    val preferredLanguage: PreferredLanguage? = null,
)
