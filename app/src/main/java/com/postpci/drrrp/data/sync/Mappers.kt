package com.postpci.drrrp.data.sync

import com.postpci.drrrp.data.local.entity.AlertEntity
import com.postpci.drrrp.data.local.entity.BleedingEventEntity
import com.postpci.drrrp.data.local.entity.DailyEntryEntity
import com.postpci.drrrp.data.local.entity.Demographics
import com.postpci.drrrp.data.local.entity.LabsAndVitals
import com.postpci.drrrp.data.local.entity.MedicationsAndFollowUp
import com.postpci.drrrp.data.local.entity.MessageEntity
import com.postpci.drrrp.data.local.entity.PatientBaselineEntity
import com.postpci.drrrp.data.local.entity.ProceduralDetails
import com.postpci.drrrp.data.local.entity.Social
import com.postpci.drrrp.data.model.AccessSite
import com.postpci.drrrp.data.model.AlertSeverity
import com.postpci.drrrp.data.model.AlertSourceType
import com.postpci.drrrp.data.model.ChestPainType
import com.postpci.drrrp.data.model.CulpritVessel
import com.postpci.drrrp.data.model.KillipClass
import com.postpci.drrrp.data.model.NyhaClass
import com.postpci.drrrp.data.model.PreferredLanguage
import com.postpci.drrrp.data.model.Sex
import com.postpci.drrrp.data.model.SmokingStatus
import com.postpci.drrrp.data.model.StemiTerritory
import com.postpci.drrrp.data.model.SyncStatus
import com.postpci.drrrp.data.model.ThrombusBurden
import com.postpci.drrrp.data.model.TimiFlow
import com.postpci.drrrp.data.model.UserRole
import com.postpci.drrrp.data.sync.dto.AlertDto
import com.postpci.drrrp.data.sync.dto.BaselineDto
import com.postpci.drrrp.data.sync.dto.BleedingEventDto
import com.postpci.drrrp.data.sync.dto.DailyEntryDto
import com.postpci.drrrp.data.sync.dto.DemographicsDto
import com.postpci.drrrp.data.sync.dto.LabsAndVitalsDto
import com.postpci.drrrp.data.sync.dto.MedicationsDto
import com.postpci.drrrp.data.sync.dto.MessageDto
import com.postpci.drrrp.data.sync.dto.ProceduralDto
import com.postpci.drrrp.data.sync.dto.SocialDto
import java.time.LocalDate

/** Null-safe `String?.let(Enum::valueOf)` — every wire enum is sent/read as its Kotlin `.name`. */
private inline fun <reified E : Enum<E>> String?.toEnumOrNull(): E? =
    this?.let { raw -> enumValues<E>().firstOrNull { it.name == raw } }

fun DailyEntryEntity.toDto() = DailyEntryDto(
    id = id, patientId = patientId, entryDate = entryDate.toString(), loggedByCaregiver = loggedByCaregiver,
    chestPainCount = chestPainCount, chestPainType = chestPainType?.name, nyhaClass = nyhaClass?.name,
    restingHeartRate = restingHeartRate, bpSystolic = bpSystolic, bpDiastolic = bpDiastolic, weightKg = weightKg,
    spo2 = spo2, accessSiteBleeding = accessSiteBleeding, accessSiteSwelling = accessSiteSwelling,
    accessSitePain = accessSitePain, accessSiteDiscolouration = accessSiteDiscolouration,
    medicationsTaken = medicationsTaken, daptTaken = daptTaken, stepsOrMinutesWalked = stepsOrMinutesWalked,
    symptomThatStoppedActivity = symptomThatStoppedActivity, palpitations = palpitations, syncope = syncope,
    nearSyncope = nearSyncope, createdAt = createdAt, updatedAt = updatedAt,
)

fun BleedingEventEntity.toDto() = BleedingEventDto(
    id = id, patientId = patientId, timestamp = timestamp, site = site, severity = severity.name,
    neededMedicalAttention = neededMedicalAttention, notes = notes, loggedByCaregiver = loggedByCaregiver,
)

fun AlertEntity.toDto() = AlertDto(
    id = id, patientId = patientId, sourceType = sourceType.name, sourceId = sourceId, fieldKey = fieldKey,
    severity = severity.name, message = message, normalRangeText = normalRangeText, createdAt = createdAt,
    reviewed = reviewed, reviewedAt = reviewedAt, reviewedByStaffId = reviewedByStaffId,
)

fun MessageEntity.toDto() = MessageDto(
    id = id, patientId = patientId, senderRole = senderRole.name, senderId = senderId, senderName = senderName,
    text = text, timestamp = timestamp,
)

fun PatientBaselineEntity.toDto() = BaselineDto(
    patientId = patientId,
    demographics = DemographicsDto(
        name = demographics.name, age = demographics.age, sex = demographics.sex?.name,
        contactNumber = demographics.contactNumber, comorbidities = demographics.comorbidities,
        currentMedications = demographics.currentMedications,
    ),
    procedural = ProceduralDto(
        pciDate = procedural.pciDate?.toString(), stemiTerritory = procedural.stemiTerritory?.name,
        symptomOnsetToDoorMinutes = procedural.symptomOnsetToDoorMinutes, doorToBalloonMinutes = procedural.doorToBalloonMinutes,
        culpritVessel = procedural.culpritVessel?.name, preProceduralTimiFlow = procedural.preProceduralTimiFlow?.name,
        postProceduralTimiFlow = procedural.postProceduralTimiFlow?.name, stentCount = procedural.stentCount,
        stentType = procedural.stentType, stentLengthMm = procedural.stentLengthMm, stentDiameterMm = procedural.stentDiameterMm,
        thrombusBurden = procedural.thrombusBurden?.name, noReflow = procedural.noReflow,
        nonCulpritDiseasePresent = procedural.nonCulpritDiseasePresent, stagedPciDate = procedural.stagedPciDate?.toString(),
        accessSite = procedural.accessSite?.name, accessSiteComplications = procedural.accessSiteComplications,
        mechanicalComplicationMr = procedural.mechanicalComplicationMr, mechanicalComplicationVsr = procedural.mechanicalComplicationVsr,
        mechanicalComplicationFreeWallRupture = procedural.mechanicalComplicationFreeWallRupture,
        mechanicalComplicationEffusion = procedural.mechanicalComplicationEffusion, arrhythmiaVtVf = procedural.arrhythmiaVtVf,
        arrhythmiaAvBlock = procedural.arrhythmiaAvBlock, arrhythmiaTempPacing = procedural.arrhythmiaTempPacing,
    ),
    labsAndVitals = LabsAndVitalsDto(
        peakTroponin = labsAndVitals.peakTroponin, peakCkMb = labsAndVitals.peakCkMb, lvefPercent = labsAndVitals.lvefPercent,
        echoDate = labsAndVitals.echoDate?.toString(), killipClass = labsAndVitals.killipClass?.name,
        baselineCreatinine = labsAndVitals.baselineCreatinine, egfr = labsAndVitals.egfr,
        contrastVolumeMl = labsAndVitals.contrastVolumeMl, dischargeHaemoglobin = labsAndVitals.dischargeHaemoglobin,
        plateletCount = labsAndVitals.plateletCount, ldlC = labsAndVitals.ldlC, statinDose = labsAndVitals.statinDose,
        hba1c = labsAndVitals.hba1c, dischargeSystolicBp = labsAndVitals.dischargeSystolicBp,
        dischargeDiastolicBp = labsAndVitals.dischargeDiastolicBp, dischargeHeartRate = labsAndVitals.dischargeHeartRate,
        dischargeWeightKg = labsAndVitals.dischargeWeightKg, dischargeEcgRhythm = labsAndVitals.dischargeEcgRhythm,
        dischargeEcgQrsMs = labsAndVitals.dischargeEcgQrsMs, dischargeEcgStChanges = labsAndVitals.dischargeEcgStChanges,
        dischargeEcgQWaves = labsAndVitals.dischargeEcgQWaves,
    ),
    medicationsAndFollowUp = MedicationsDto(
        daptAgent = medicationsAndFollowUp.daptAgent, daptDose = medicationsAndFollowUp.daptDose,
        daptPlannedDurationMonths = medicationsAndFollowUp.daptPlannedDurationMonths,
        betaBlockerDose = medicationsAndFollowUp.betaBlockerDose, aceiArbArniDose = medicationsAndFollowUp.aceiArbArniDose,
        mraDose = medicationsAndFollowUp.mraDose, sglt2iDose = medicationsAndFollowUp.sglt2iDose,
        cardiacRehabReferred = medicationsAndFollowUp.cardiacRehabReferred,
        opdFollowUpDate = medicationsAndFollowUp.opdFollowUpDate?.toString(),
        echoFollowUpDate = medicationsAndFollowUp.echoFollowUpDate?.toString(),
        lipidRecheckDate = medicationsAndFollowUp.lipidRecheckDate?.toString(),
    ),
    social = SocialDto(
        smokingStatus = social.smokingStatus?.name, occupation = social.occupation,
        expectedReturnToWorkDate = social.expectedReturnToWorkDate?.toString(), livesAlone = social.livesAlone,
        hasCaregiver = social.hasCaregiver, smartphoneLiteracy = social.smartphoneLiteracy?.name,
        preferredLanguage = social.preferredLanguage?.name,
    ),
    isFinalized = isFinalized, createdAt = createdAt, updatedAt = updatedAt,
)

// ---- Reverse (pull-sync) mappers — server response -> local Room entity. Every pulled record is
// stamped SyncStatus.SYNCED: it just came from the server, so by definition there's nothing
// pending about it. See SyncManager.pullPatient. ----

fun DailyEntryDto.toEntity() = DailyEntryEntity(
    id = id, patientId = patientId, entryDate = LocalDate.parse(entryDate), loggedByCaregiver = loggedByCaregiver,
    chestPainCount = chestPainCount, chestPainType = chestPainType.toEnumOrNull<ChestPainType>(), nyhaClass = nyhaClass.toEnumOrNull<NyhaClass>(),
    restingHeartRate = restingHeartRate, bpSystolic = bpSystolic, bpDiastolic = bpDiastolic, weightKg = weightKg,
    spo2 = spo2, accessSiteBleeding = accessSiteBleeding, accessSiteSwelling = accessSiteSwelling,
    accessSitePain = accessSitePain, accessSiteDiscolouration = accessSiteDiscolouration,
    medicationsTaken = medicationsTaken, daptTaken = daptTaken, stepsOrMinutesWalked = stepsOrMinutesWalked,
    symptomThatStoppedActivity = symptomThatStoppedActivity, palpitations = palpitations, syncope = syncope,
    nearSyncope = nearSyncope, createdAt = createdAt, updatedAt = updatedAt, syncStatus = SyncStatus.SYNCED,
)

fun AlertDto.toEntity() = AlertEntity(
    id = id, patientId = patientId, sourceType = sourceType.toEnumOrNull<AlertSourceType>() ?: AlertSourceType.DAILY_ENTRY,
    sourceId = sourceId, fieldKey = fieldKey, severity = severity.toEnumOrNull<AlertSeverity>() ?: AlertSeverity.INFO,
    message = message, normalRangeText = normalRangeText, createdAt = createdAt, reviewed = reviewed,
    reviewedAt = reviewedAt, reviewedByStaffId = reviewedByStaffId, syncStatus = SyncStatus.SYNCED,
)

fun MessageDto.toEntity() = MessageEntity(
    id = id, patientId = patientId, senderRole = senderRole.toEnumOrNull<UserRole>() ?: UserRole.STAFF, senderId = senderId,
    senderName = senderName, text = text, timestamp = timestamp, syncStatus = SyncStatus.SYNCED,
)

fun BaselineDto.toEntity() = PatientBaselineEntity(
    patientId = patientId,
    demographics = Demographics(
        name = demographics.name, age = demographics.age, sex = demographics.sex.toEnumOrNull<Sex>(),
        contactNumber = demographics.contactNumber, comorbidities = demographics.comorbidities,
        currentMedications = demographics.currentMedications,
    ),
    procedural = ProceduralDetails(
        pciDate = procedural.pciDate?.let(LocalDate::parse), stemiTerritory = procedural.stemiTerritory.toEnumOrNull<StemiTerritory>(),
        symptomOnsetToDoorMinutes = procedural.symptomOnsetToDoorMinutes, doorToBalloonMinutes = procedural.doorToBalloonMinutes,
        culpritVessel = procedural.culpritVessel.toEnumOrNull<CulpritVessel>(), preProceduralTimiFlow = procedural.preProceduralTimiFlow.toEnumOrNull<TimiFlow>(),
        postProceduralTimiFlow = procedural.postProceduralTimiFlow.toEnumOrNull<TimiFlow>(), stentCount = procedural.stentCount,
        stentType = procedural.stentType, stentLengthMm = procedural.stentLengthMm, stentDiameterMm = procedural.stentDiameterMm,
        thrombusBurden = procedural.thrombusBurden.toEnumOrNull<ThrombusBurden>(), noReflow = procedural.noReflow,
        nonCulpritDiseasePresent = procedural.nonCulpritDiseasePresent, stagedPciDate = procedural.stagedPciDate?.let(LocalDate::parse),
        accessSite = procedural.accessSite.toEnumOrNull<AccessSite>(), accessSiteComplications = procedural.accessSiteComplications,
        mechanicalComplicationMr = procedural.mechanicalComplicationMr, mechanicalComplicationVsr = procedural.mechanicalComplicationVsr,
        mechanicalComplicationFreeWallRupture = procedural.mechanicalComplicationFreeWallRupture,
        mechanicalComplicationEffusion = procedural.mechanicalComplicationEffusion, arrhythmiaVtVf = procedural.arrhythmiaVtVf,
        arrhythmiaAvBlock = procedural.arrhythmiaAvBlock, arrhythmiaTempPacing = procedural.arrhythmiaTempPacing,
    ),
    labsAndVitals = LabsAndVitals(
        peakTroponin = labsAndVitals.peakTroponin, peakCkMb = labsAndVitals.peakCkMb, lvefPercent = labsAndVitals.lvefPercent,
        echoDate = labsAndVitals.echoDate?.let(LocalDate::parse), killipClass = labsAndVitals.killipClass.toEnumOrNull<KillipClass>(),
        baselineCreatinine = labsAndVitals.baselineCreatinine, egfr = labsAndVitals.egfr,
        contrastVolumeMl = labsAndVitals.contrastVolumeMl, dischargeHaemoglobin = labsAndVitals.dischargeHaemoglobin,
        plateletCount = labsAndVitals.plateletCount, ldlC = labsAndVitals.ldlC, statinDose = labsAndVitals.statinDose,
        hba1c = labsAndVitals.hba1c, dischargeSystolicBp = labsAndVitals.dischargeSystolicBp,
        dischargeDiastolicBp = labsAndVitals.dischargeDiastolicBp, dischargeHeartRate = labsAndVitals.dischargeHeartRate,
        dischargeWeightKg = labsAndVitals.dischargeWeightKg, dischargeEcgRhythm = labsAndVitals.dischargeEcgRhythm,
        dischargeEcgQrsMs = labsAndVitals.dischargeEcgQrsMs, dischargeEcgStChanges = labsAndVitals.dischargeEcgStChanges,
        dischargeEcgQWaves = labsAndVitals.dischargeEcgQWaves,
    ),
    medicationsAndFollowUp = MedicationsAndFollowUp(
        daptAgent = medicationsAndFollowUp.daptAgent, daptDose = medicationsAndFollowUp.daptDose,
        daptPlannedDurationMonths = medicationsAndFollowUp.daptPlannedDurationMonths,
        betaBlockerDose = medicationsAndFollowUp.betaBlockerDose, aceiArbArniDose = medicationsAndFollowUp.aceiArbArniDose,
        mraDose = medicationsAndFollowUp.mraDose, sglt2iDose = medicationsAndFollowUp.sglt2iDose,
        cardiacRehabReferred = medicationsAndFollowUp.cardiacRehabReferred,
        opdFollowUpDate = medicationsAndFollowUp.opdFollowUpDate?.let(LocalDate::parse),
        echoFollowUpDate = medicationsAndFollowUp.echoFollowUpDate?.let(LocalDate::parse),
        lipidRecheckDate = medicationsAndFollowUp.lipidRecheckDate?.let(LocalDate::parse),
    ),
    social = Social(
        smokingStatus = social.smokingStatus.toEnumOrNull<SmokingStatus>(), occupation = social.occupation,
        expectedReturnToWorkDate = social.expectedReturnToWorkDate?.let(LocalDate::parse), livesAlone = social.livesAlone,
        hasCaregiver = social.hasCaregiver, smartphoneLiteracy = null, preferredLanguage = social.preferredLanguage.toEnumOrNull<PreferredLanguage>(),
    ),
    // A pulled baseline is by definition fully staff-authored already; step tracking only matters
    // locally on the staff device mid-wizard, so it's not part of the wire format.
    lastCompletedWizardStep = 4, isFinalized = isFinalized, createdAt = createdAt, updatedAt = updatedAt,
    syncStatus = SyncStatus.SYNCED,
)
