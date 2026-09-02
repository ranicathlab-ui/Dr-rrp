package com.postpci.drrrp.ui.staff.wizard

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.postpci.drrrp.data.local.entity.Demographics
import com.postpci.drrrp.data.local.entity.LabsAndVitals
import com.postpci.drrrp.data.local.entity.MedicationsAndFollowUp
import com.postpci.drrrp.data.local.entity.ProceduralDetails
import com.postpci.drrrp.data.local.entity.Social
import com.postpci.drrrp.data.model.AccessSite
import com.postpci.drrrp.data.model.CulpritVessel
import com.postpci.drrrp.data.model.KillipClass
import com.postpci.drrrp.data.model.PreferredLanguage
import com.postpci.drrrp.data.model.Sex
import com.postpci.drrrp.data.model.SmartphoneLiteracy
import com.postpci.drrrp.data.model.SmokingStatus
import com.postpci.drrrp.data.model.StemiTerritory
import com.postpci.drrrp.data.model.ThrombusBurden
import com.postpci.drrrp.data.model.TimiFlow
import com.postpci.drrrp.ui.common.FormChipGroup
import com.postpci.drrrp.ui.common.FormDateField
import com.postpci.drrrp.ui.common.FormDecimalField
import com.postpci.drrrp.ui.common.FormNumberField
import com.postpci.drrrp.ui.common.FormSectionLabel
import com.postpci.drrrp.ui.common.FormTextField
import com.postpci.drrrp.ui.common.FormToggle
import com.postpci.drrrp.ui.common.WizardNavButtons

@Composable
fun DemographicsStep(initial: Demographics, showBack: Boolean, onBack: () -> Unit, onNext: (Demographics) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var age by remember { mutableStateOf(initial.age?.toString().orEmpty()) }
    var sex by remember { mutableStateOf(initial.sex) }
    var contact by remember { mutableStateOf(initial.contactNumber) }
    var comorbidities by remember { mutableStateOf(initial.comorbidities) }
    var currentMeds by remember { mutableStateOf(initial.currentMedications) }

    Column {
        FormTextField("Full name", name) { name = it }
        FormNumberField("Age", age) { age = it }
        FormChipGroup("Sex", Sex.entries, sex, { it.name }) { sex = it }
        FormTextField("Contact number", contact) { contact = it }
        FormTextField("Comorbidities (semicolon-separated)", comorbidities) { comorbidities = it }
        FormTextField("Current medications (pre-PCI, semicolon-separated)", currentMeds) { currentMeds = it }
        WizardNavButtons(showBack, "Next: Procedural", onBack) {
            onNext(Demographics(name, age.toIntOrNull(), sex, contact, comorbidities, currentMeds))
        }
    }
}

@Composable
fun ProceduralStep(initial: ProceduralDetails, onBack: () -> Unit, onNext: (ProceduralDetails) -> Unit) {
    var pciDate by remember { mutableStateOf(initial.pciDate) }
    var territory by remember { mutableStateOf(initial.stemiTerritory) }
    var onsetToDoor by remember { mutableStateOf(initial.symptomOnsetToDoorMinutes?.toString().orEmpty()) }
    var doorToBalloon by remember { mutableStateOf(initial.doorToBalloonMinutes?.toString().orEmpty()) }
    var culprit by remember { mutableStateOf(initial.culpritVessel) }
    var preTimi by remember { mutableStateOf(initial.preProceduralTimiFlow) }
    var postTimi by remember { mutableStateOf(initial.postProceduralTimiFlow) }
    var stentCount by remember { mutableStateOf(initial.stentCount?.toString().orEmpty()) }
    var stentType by remember { mutableStateOf(initial.stentType.orEmpty()) }
    var stentLength by remember { mutableStateOf(initial.stentLengthMm?.toString().orEmpty()) }
    var stentDiameter by remember { mutableStateOf(initial.stentDiameterMm?.toString().orEmpty()) }
    var thrombus by remember { mutableStateOf(initial.thrombusBurden) }
    var noReflow by remember { mutableStateOf(initial.noReflow) }
    var nonCulprit by remember { mutableStateOf(initial.nonCulpritDiseasePresent) }
    var stagedDate by remember { mutableStateOf(initial.stagedPciDate) }
    var accessSite by remember { mutableStateOf(initial.accessSite) }
    var accessComplications by remember { mutableStateOf(initial.accessSiteComplications.orEmpty()) }
    var mr by remember { mutableStateOf(initial.mechanicalComplicationMr) }
    var vsr by remember { mutableStateOf(initial.mechanicalComplicationVsr) }
    var freeWall by remember { mutableStateOf(initial.mechanicalComplicationFreeWallRupture) }
    var effusion by remember { mutableStateOf(initial.mechanicalComplicationEffusion) }
    var vtVf by remember { mutableStateOf(initial.arrhythmiaVtVf) }
    var avBlock by remember { mutableStateOf(initial.arrhythmiaAvBlock) }
    var tempPacing by remember { mutableStateOf(initial.arrhythmiaTempPacing) }

    Column {
        FormDateField("PCI date", pciDate) { pciDate = it }
        FormChipGroup("STEMI territory", StemiTerritory.entries, territory, { it.name.replace('_', ' ') }) { territory = it }
        FormNumberField("Symptom onset-to-door (minutes)", onsetToDoor) { onsetToDoor = it }
        FormNumberField("Door-to-balloon (minutes)", doorToBalloon) { doorToBalloon = it }
        FormChipGroup("Culprit vessel", CulpritVessel.entries, culprit, { it.name }) { culprit = it }
        FormChipGroup("Pre-procedural TIMI flow", TimiFlow.entries, preTimi, { it.name.replace("GRADE_", "") }) { preTimi = it }
        FormChipGroup("Post-procedural TIMI flow", TimiFlow.entries, postTimi, { it.name.replace("GRADE_", "") }) { postTimi = it }
        FormNumberField("Stent count", stentCount) { stentCount = it }
        FormTextField("Stent type", stentType) { stentType = it }
        FormDecimalField("Stent length (mm)", stentLength) { stentLength = it }
        FormDecimalField("Stent diameter (mm)", stentDiameter) { stentDiameter = it }
        FormChipGroup("Thrombus burden", ThrombusBurden.entries, thrombus, { it.name }) { thrombus = it }
        FormToggle("No-reflow", noReflow) { noReflow = it }
        FormToggle("Non-culprit disease present", nonCulprit) { nonCulprit = it }
        if (nonCulprit) FormDateField("Staged PCI date", stagedDate) { stagedDate = it }
        FormChipGroup("Access site", AccessSite.entries, accessSite, { it.name }) { accessSite = it }
        FormTextField("Access-site complications", accessComplications) { accessComplications = it }
        FormSectionLabel("Mechanical complications")
        FormToggle("Mitral regurgitation (MR)", mr) { mr = it }
        FormToggle("VSR", vsr) { vsr = it }
        FormToggle("Free-wall rupture", freeWall) { freeWall = it }
        FormToggle("Pericardial effusion", effusion) { effusion = it }
        FormSectionLabel("In-hospital arrhythmias")
        FormToggle("VT / VF", vtVf) { vtVf = it }
        FormToggle("AV block", avBlock) { avBlock = it }
        FormToggle("Temporary pacing", tempPacing) { tempPacing = it }
        WizardNavButtons(true, "Next: Labs & Vitals", onBack) {
            onNext(
                ProceduralDetails(
                    pciDate, territory, onsetToDoor.toIntOrNull(), doorToBalloon.toIntOrNull(), culprit, preTimi, postTimi,
                    stentCount.toIntOrNull(), stentType.ifBlank { null }, stentLength.toDoubleOrNull(), stentDiameter.toDoubleOrNull(),
                    thrombus, noReflow, nonCulprit, stagedDate, accessSite, accessComplications.ifBlank { null },
                    mr, vsr, freeWall, effusion, vtVf, avBlock, tempPacing,
                ),
            )
        }
    }
}

@Composable
fun LabsStep(initial: LabsAndVitals, onBack: () -> Unit, onNext: (LabsAndVitals) -> Unit) {
    var troponin by remember { mutableStateOf(initial.peakTroponin?.toString().orEmpty()) }
    var ckMb by remember { mutableStateOf(initial.peakCkMb?.toString().orEmpty()) }
    var lvef by remember { mutableStateOf(initial.lvefPercent?.toString().orEmpty()) }
    var echoDate by remember { mutableStateOf(initial.echoDate) }
    var killip by remember { mutableStateOf(initial.killipClass) }
    var creatinine by remember { mutableStateOf(initial.baselineCreatinine?.toString().orEmpty()) }
    var egfr by remember { mutableStateOf(initial.egfr?.toString().orEmpty()) }
    var contrastVolume by remember { mutableStateOf(initial.contrastVolumeMl?.toString().orEmpty()) }
    var haemoglobin by remember { mutableStateOf(initial.dischargeHaemoglobin?.toString().orEmpty()) }
    var platelets by remember { mutableStateOf(initial.plateletCount?.toString().orEmpty()) }
    var ldl by remember { mutableStateOf(initial.ldlC?.toString().orEmpty()) }
    var statinDose by remember { mutableStateOf(initial.statinDose.orEmpty()) }
    var hba1c by remember { mutableStateOf(initial.hba1c?.toString().orEmpty()) }
    var sbp by remember { mutableStateOf(initial.dischargeSystolicBp?.toString().orEmpty()) }
    var dbp by remember { mutableStateOf(initial.dischargeDiastolicBp?.toString().orEmpty()) }
    var hr by remember { mutableStateOf(initial.dischargeHeartRate?.toString().orEmpty()) }
    var weight by remember { mutableStateOf(initial.dischargeWeightKg?.toString().orEmpty()) }
    var ecgRhythm by remember { mutableStateOf(initial.dischargeEcgRhythm.orEmpty()) }
    var ecgQrs by remember { mutableStateOf(initial.dischargeEcgQrsMs?.toString().orEmpty()) }
    var ecgSt by remember { mutableStateOf(initial.dischargeEcgStChanges.orEmpty()) }
    var ecgQWaves by remember { mutableStateOf(initial.dischargeEcgQWaves) }

    Column {
        FormDecimalField("Peak troponin", troponin) { troponin = it }
        FormDecimalField("Peak CK-MB", ckMb) { ckMb = it }
        FormNumberField("LVEF (%)", lvef) { lvef = it }
        FormDateField("Echo date", echoDate) { echoDate = it }
        FormChipGroup("Killip class", KillipClass.entries, killip, { it.name }) { killip = it }
        FormDecimalField("Baseline creatinine", creatinine) { creatinine = it }
        FormDecimalField("eGFR", egfr) { egfr = it }
        FormNumberField("Contrast volume (mL)", contrastVolume) { contrastVolume = it }
        FormDecimalField("Discharge haemoglobin", haemoglobin) { haemoglobin = it }
        FormNumberField("Platelet count", platelets) { platelets = it }
        FormDecimalField("LDL-C", ldl) { ldl = it }
        FormTextField("Statin dose", statinDose) { statinDose = it }
        FormDecimalField("HbA1c (if diabetic)", hba1c) { hba1c = it }
        FormSectionLabel("Discharge vitals")
        FormNumberField("Systolic BP", sbp) { sbp = it }
        FormNumberField("Diastolic BP", dbp) { dbp = it }
        FormNumberField("Heart rate", hr) { hr = it }
        FormDecimalField("Weight (kg)", weight) { weight = it }
        FormSectionLabel("Discharge ECG")
        FormTextField("Rhythm", ecgRhythm) { ecgRhythm = it }
        FormNumberField("QRS (ms)", ecgQrs) { ecgQrs = it }
        FormTextField("ST changes", ecgSt) { ecgSt = it }
        FormToggle("Q waves present", ecgQWaves) { ecgQWaves = it }
        WizardNavButtons(true, "Next: Medications", onBack) {
            onNext(
                LabsAndVitals(
                    troponin.toDoubleOrNull(), ckMb.toDoubleOrNull(), lvef.toIntOrNull(), echoDate, killip,
                    creatinine.toDoubleOrNull(), egfr.toDoubleOrNull(), contrastVolume.toIntOrNull(),
                    haemoglobin.toDoubleOrNull(), platelets.toIntOrNull(), ldl.toDoubleOrNull(), statinDose.ifBlank { null },
                    hba1c.toDoubleOrNull(), sbp.toIntOrNull(), dbp.toIntOrNull(), hr.toIntOrNull(), weight.toDoubleOrNull(),
                    ecgRhythm.ifBlank { null }, ecgQrs.toIntOrNull(), ecgSt.ifBlank { null }, ecgQWaves,
                ),
            )
        }
    }
}

@Composable
fun MedicationsStep(initial: MedicationsAndFollowUp, onBack: () -> Unit, onNext: (MedicationsAndFollowUp) -> Unit) {
    var daptAgent by remember { mutableStateOf(initial.daptAgent.orEmpty()) }
    var daptDose by remember { mutableStateOf(initial.daptDose.orEmpty()) }
    var daptDuration by remember { mutableStateOf(initial.daptPlannedDurationMonths?.toString().orEmpty()) }
    var betaBlocker by remember { mutableStateOf(initial.betaBlockerDose.orEmpty()) }
    var aceiArbArni by remember { mutableStateOf(initial.aceiArbArniDose.orEmpty()) }
    var mra by remember { mutableStateOf(initial.mraDose.orEmpty()) }
    var sglt2i by remember { mutableStateOf(initial.sglt2iDose.orEmpty()) }
    var rehab by remember { mutableStateOf(initial.cardiacRehabReferred) }
    var opdDate by remember { mutableStateOf(initial.opdFollowUpDate) }
    var echoFollowUp by remember { mutableStateOf(initial.echoFollowUpDate) }
    var lipidRecheck by remember { mutableStateOf(initial.lipidRecheckDate) }

    Column {
        FormSectionLabel("DAPT")
        FormTextField("DAPT agent", daptAgent) { daptAgent = it }
        FormTextField("DAPT dose", daptDose) { daptDose = it }
        FormNumberField("Planned duration (months)", daptDuration) { daptDuration = it }
        FormSectionLabel("Discharge doses")
        FormTextField("Beta-blocker", betaBlocker) { betaBlocker = it }
        FormTextField("ACEi / ARB / ARNI", aceiArbArni) { aceiArbArni = it }
        FormTextField("MRA", mra) { mra = it }
        FormTextField("SGLT2i", sglt2i) { sglt2i = it }
        FormToggle("Cardiac rehab referred", rehab) { rehab = it }
        FormSectionLabel("Scheduled follow-ups")
        FormDateField("OPD follow-up date", opdDate) { opdDate = it }
        FormDateField("Echo follow-up date", echoFollowUp) { echoFollowUp = it }
        FormDateField("Lipid recheck date", lipidRecheck) { lipidRecheck = it }
        WizardNavButtons(true, "Next: Social", onBack) {
            onNext(
                MedicationsAndFollowUp(
                    daptAgent.ifBlank { null }, daptDose.ifBlank { null }, daptDuration.toIntOrNull(),
                    betaBlocker.ifBlank { null }, aceiArbArni.ifBlank { null }, mra.ifBlank { null }, sglt2i.ifBlank { null },
                    rehab, opdDate, echoFollowUp, lipidRecheck,
                ),
            )
        }
    }
}

@Composable
fun SocialStep(initial: Social, onBack: () -> Unit, onFinish: (Social) -> Unit) {
    var smoking by remember { mutableStateOf(initial.smokingStatus) }
    var occupation by remember { mutableStateOf(initial.occupation.orEmpty()) }
    var returnToWork by remember { mutableStateOf(initial.expectedReturnToWorkDate) }
    var livesAlone by remember { mutableStateOf(initial.livesAlone ?: false) }
    var hasCaregiver by remember { mutableStateOf(initial.hasCaregiver ?: false) }
    var literacy by remember { mutableStateOf(initial.smartphoneLiteracy) }
    var language by remember { mutableStateOf(initial.preferredLanguage) }

    Column {
        FormChipGroup("Smoking status", SmokingStatus.entries, smoking, { it.name }) { smoking = it }
        FormTextField("Occupation", occupation) { occupation = it }
        FormDateField("Expected return-to-work date", returnToWork) { returnToWork = it }
        FormToggle("Lives alone", livesAlone) { livesAlone = it }
        FormToggle("Has caregiver", hasCaregiver) { hasCaregiver = it }
        FormChipGroup("Smartphone literacy", SmartphoneLiteracy.entries, literacy, { it.name }) { literacy = it }
        FormChipGroup("Preferred language", PreferredLanguage.entries, language, { it.name }) { language = it }
        WizardNavButtons(true, "Finish baseline", onBack) {
            onFinish(Social(smoking, occupation.ifBlank { null }, returnToWork, livesAlone, hasCaregiver, literacy, language))
        }
    }
}
