package com.postpci.drrrp.data.sync.dto

import kotlinx.serialization.Serializable

/** Wire shape for `POST /patient/baseline` and `PUT /patient/baseline/{patientId}`. */
@Serializable
data class BaselineDto(
    val patientId: String,
    val demographics: DemographicsDto,
    val procedural: ProceduralDto,
    val labsAndVitals: LabsAndVitalsDto,
    val medicationsAndFollowUp: MedicationsDto,
    val social: SocialDto,
    val isFinalized: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class DemographicsDto(
    val name: String,
    val age: Int? = null,
    val sex: String? = null,
    val contactNumber: String,
    val email: String = "",
    val comorbidities: String = "",
    val currentMedications: String = "",
)

@Serializable
data class ProceduralDto(
    val pciDate: String? = null,
    val stemiTerritory: String? = null,
    val symptomOnsetToDoorMinutes: Int? = null,
    val doorToBalloonMinutes: Int? = null,
    val culpritVessel: String? = null,
    val preProceduralTimiFlow: String? = null,
    val postProceduralTimiFlow: String? = null,
    val stentCount: Int? = null,
    val stentType: String? = null,
    val stentLengthMm: Double? = null,
    val stentDiameterMm: Double? = null,
    val thrombusBurden: String? = null,
    val noReflow: Boolean = false,
    val nonCulpritDiseasePresent: Boolean = false,
    val stagedPciDate: String? = null,
    val accessSite: String? = null,
    val accessSiteComplications: String? = null,
    val mechanicalComplicationMr: Boolean = false,
    val mechanicalComplicationVsr: Boolean = false,
    val mechanicalComplicationFreeWallRupture: Boolean = false,
    val mechanicalComplicationEffusion: Boolean = false,
    val arrhythmiaVtVf: Boolean = false,
    val arrhythmiaAvBlock: Boolean = false,
    val arrhythmiaTempPacing: Boolean = false,
)

@Serializable
data class LabsAndVitalsDto(
    val peakTroponin: Double? = null,
    val peakCkMb: Double? = null,
    val lvefPercent: Int? = null,
    val echoDate: String? = null,
    val killipClass: String? = null,
    val baselineCreatinine: Double? = null,
    val egfr: Double? = null,
    val contrastVolumeMl: Int? = null,
    val dischargeHaemoglobin: Double? = null,
    val plateletCount: Int? = null,
    val ldlC: Double? = null,
    val statinDose: String? = null,
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

@Serializable
data class MedicationsDto(
    val daptAgent: String? = null,
    val daptDose: String? = null,
    val daptPlannedDurationMonths: Int? = null,
    val betaBlockerDose: String? = null,
    val aceiArbArniDose: String? = null,
    val mraDose: String? = null,
    val sglt2iDose: String? = null,
    val cardiacRehabReferred: Boolean = false,
    val opdFollowUpDate: String? = null,
    val echoFollowUpDate: String? = null,
    val lipidRecheckDate: String? = null,
)

@Serializable
data class SocialDto(
    val smokingStatus: String? = null,
    val occupation: String? = null,
    val expectedReturnToWorkDate: String? = null,
    val livesAlone: Boolean? = null,
    val hasCaregiver: Boolean? = null,
    val smartphoneLiteracy: String? = null,
    val preferredLanguage: String? = null,
)

/** Response for `GET /patient/{patientId}?cursor=&limit=` — baseline, one page of daily entries,
 *  and this patient's alerts. Alerts ride along here rather than a separate endpoint since the
 *  client always needs both together (see PatientCareRepository/AlertsViewModel). */
@Serializable
data class PatientDetailResponse(
    val baseline: BaselineDto?,
    val dailyEntries: List<DailyEntryDto>,
    val alerts: List<AlertDto> = emptyList(),
    val nextCursor: String? = null,
)

/** One row of `GET /staff/patients?search=&sort=`. */
@Serializable
data class PatientListItemDto(
    val patientId: String,
    val name: String,
    val age: Int? = null,
    val pciDate: String? = null,
    val lastAlertSeverity: String? = null,
    val lastAlertAt: Long? = null,
    /** Server-computed: due fields today but nothing logged since yesterday. */
    val hasMissedEntry: Boolean = false,
)
