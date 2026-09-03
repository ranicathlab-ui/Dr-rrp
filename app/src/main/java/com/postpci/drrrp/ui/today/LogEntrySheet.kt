package com.postpci.drrrp.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.postpci.drrrp.ui.common.bringIntoViewOnFocus
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.postpci.drrrp.data.alert.AlertRules
import com.postpci.drrrp.data.local.entity.DailyEntryEntity
import com.postpci.drrrp.data.model.ChestPainType
import com.postpci.drrrp.data.model.NyhaClass
import com.postpci.drrrp.data.schedule.MonitoringSchedule
import com.postpci.drrrp.ui.theme.AccentYellowGold
import com.postpci.drrrp.ui.theme.AlertRed
import com.postpci.drrrp.ui.theme.BorderHairline
import com.postpci.drrrp.ui.theme.SurfaceCard
import com.postpci.drrrp.ui.theme.TextPrimary
import com.postpci.drrrp.ui.theme.TextSecondary

/**
 * One focused field at a time, per the Log Entry flow spec — not one giant form. Validates
 * client-side and shows the flag inline immediately, before the value is even saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogEntrySheet(fieldKey: String, todayEntry: DailyEntryEntity?, viewModel: TodayViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceCard) {
        // Scrollable + imePadding: without these, a field with several inputs (blood pressure,
        // access-site's four toggles, activity's two fields) plus the keyboard covering the
        // lower half of the screen could push the Save button below the visible area with no way
        // to reach it — exactly the "no submit button after entering vitals" bug this fixes.
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = fieldMetaByKey[fieldKey]?.label ?: fieldKey,
                style = MaterialTheme.typography.headlineSmall,
                color = AccentYellowGold,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Normal range: ${fieldMetaByKey[fieldKey]?.rangeText ?: ""}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
            )

            when (fieldKey) {
                MonitoringSchedule.RESTING_HEART_RATE -> HeartRateEntry(todayEntry, viewModel, onDismiss)
                MonitoringSchedule.BLOOD_PRESSURE -> BloodPressureEntry(todayEntry, viewModel, onDismiss)
                MonitoringSchedule.SPO2 -> Spo2Entry(todayEntry, viewModel, onDismiss)
                MonitoringSchedule.WEIGHT -> WeightEntry(todayEntry, viewModel, onDismiss)
                MonitoringSchedule.ACCESS_SITE_CHECK -> AccessSiteEntry(todayEntry, viewModel, onDismiss)
                MonitoringSchedule.CHEST_PAIN -> ChestPainEntry(todayEntry, viewModel, onDismiss)
                MonitoringSchedule.ACTIVITY -> ActivityEntry(todayEntry, viewModel, onDismiss)
                MonitoringSchedule.PALPITATIONS_SYNCOPE -> SymptomFlagsEntry(todayEntry, viewModel, onDismiss)
                MonitoringSchedule.BREATHLESSNESS -> NyhaEntry(todayEntry, viewModel, onDismiss)
                else -> Text("Not yet implemented.", color = TextSecondary)
            }
        }
    }
}

@Composable
private fun InlineFlag(message: String?) {
    if (message == null) return
    Text(text = message, color = AlertRed, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun numberFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedBorderColor = AccentYellowGold,
    unfocusedBorderColor = BorderHairline,
    focusedLabelColor = AccentYellowGold,
    unfocusedLabelColor = TextSecondary,
    cursorColor = AccentYellowGold,
)

@Composable
private fun InlineValidationWarning(message: String) {
    Text(text = message, color = AlertRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun SaveButton(enabled: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = AccentYellowGold, contentColor = androidx.compose.ui.graphics.Color(0xFF241A00)),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    ) { Text("Save") }
}

@Composable
private fun HeartRateEntry(todayEntry: DailyEntryEntity?, viewModel: TodayViewModel, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(todayEntry?.restingHeartRate?.toString().orEmpty()) }
    val value = text.toIntOrNull()
    val isValidRange = value != null && value in 30..250
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.filter(Char::isDigit) },
        label = { Text("Heart rate (bpm)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = numberFieldColors(),
        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
    )
    if (value != null && !isValidRange) {
        InlineValidationWarning("Enter a valid heart rate between 30 and 250 bpm.")
    }
    InlineFlag(if (isValidRange) AlertRules.checkRestingHeartRate(value!!)?.message else null)
    SaveButton(enabled = isValidRange) { viewModel.submitRestingHeartRate(value!!); onDismiss() }
}

@Composable
private fun BloodPressureEntry(todayEntry: DailyEntryEntity?, viewModel: TodayViewModel, onDismiss: () -> Unit) {
    var systolic by remember { mutableStateOf(todayEntry?.bpSystolic?.toString().orEmpty()) }
    var diastolic by remember { mutableStateOf(todayEntry?.bpDiastolic?.toString().orEmpty()) }
    val sys = systolic.toIntOrNull()
    val dia = diastolic.toIntOrNull()
    val sysValid = sys != null && sys in 50..300
    val diaValid = dia != null && dia in 30..200
    val relationValid = sys != null && dia != null && sys > dia
    val isValid = sysValid && diaValid && relationValid

    OutlinedTextField(
        value = systolic,
        onValueChange = { systolic = it.filter(Char::isDigit) },
        label = { Text("Systolic (mmHg)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = numberFieldColors(),
        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
    )
    OutlinedTextField(
        value = diastolic,
        onValueChange = { diastolic = it.filter(Char::isDigit) },
        label = { Text("Diastolic (mmHg)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = numberFieldColors(),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).bringIntoViewOnFocus(),
    )
    if (sys != null && !sysValid) InlineValidationWarning("Systolic must be between 50 and 300 mmHg.")
    if (dia != null && !diaValid) InlineValidationWarning("Diastolic must be between 30 and 200 mmHg.")
    if (sysValid && diaValid && !relationValid) InlineValidationWarning("Systolic must be greater than diastolic.")

    InlineFlag(if (isValid) AlertRules.checkBloodPressure(sys!!, dia!!)?.message else null)
    SaveButton(enabled = isValid) { viewModel.submitBloodPressure(sys!!, dia!!); onDismiss() }
}

@Composable
private fun Spo2Entry(todayEntry: DailyEntryEntity?, viewModel: TodayViewModel, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(todayEntry?.spo2?.toString().orEmpty()) }
    val value = text.toIntOrNull()
    val isValid = value != null && value in 50..100
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.filter(Char::isDigit) },
        label = { Text("SpO2 (%)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = numberFieldColors(),
        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
    )
    if (value != null && !isValid) InlineValidationWarning("SpO2 must be between 50% and 100%.")
    InlineFlag(if (isValid) AlertRules.checkSpo2(value!!)?.message else null)
    SaveButton(enabled = isValid) { viewModel.submitSpo2(value!!); onDismiss() }
}

@Composable
private fun WeightEntry(todayEntry: DailyEntryEntity?, viewModel: TodayViewModel, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(todayEntry?.weightKg?.toString().orEmpty()) }
    val value = text.toDoubleOrNull()
    val isValid = value != null && value in 20.0..300.0
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' } },
        label = { Text("Weight (kg)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = numberFieldColors(),
        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
    )
    if (value != null && !isValid) InlineValidationWarning("Weight must be between 20 kg and 300 kg.")
    Text(
        "Flag appears on save if this is a gain of more than 2kg over the last 3 days.",
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
        modifier = Modifier.padding(top = 8.dp),
    )
    SaveButton(enabled = isValid) { viewModel.submitWeight(value!!); onDismiss() }
}

@Composable
private fun AccessSiteEntry(todayEntry: DailyEntryEntity?, viewModel: TodayViewModel, onDismiss: () -> Unit) {
    var bleeding by remember { mutableStateOf(todayEntry?.accessSiteBleeding ?: false) }
    var swelling by remember { mutableStateOf(todayEntry?.accessSiteSwelling ?: false) }
    var pain by remember { mutableStateOf(todayEntry?.accessSitePain ?: false) }
    var discolouration by remember { mutableStateOf(todayEntry?.accessSiteDiscolouration ?: false) }
    ToggleRow("Bleeding", bleeding) { bleeding = it }
    ToggleRow("Swelling", swelling) { swelling = it }
    ToggleRow("Pain", pain) { pain = it }
    ToggleRow("Discolouration", discolouration) { discolouration = it }
    InlineFlag(AlertRules.checkAccessSite(bleeding, swelling, pain, discolouration)?.message)
    SaveButton(enabled = true) { viewModel.submitAccessSite(bleeding, swelling, pain, discolouration); onDismiss() }
}

@Composable
private fun ChestPainEntry(todayEntry: DailyEntryEntity?, viewModel: TodayViewModel, onDismiss: () -> Unit) {
    var countText by remember { mutableStateOf((todayEntry?.chestPainCount ?: 0).toString()) }
    var type by remember { mutableStateOf(todayEntry?.chestPainType) }
    val count = countText.toIntOrNull() ?: 0
    OutlinedTextField(
        value = countText,
        onValueChange = { countText = it.filter(Char::isDigit) },
        label = { Text("Episodes today") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = numberFieldColors(),
        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
    )
    Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChipChoice("At rest", type == ChestPainType.REST) { type = ChestPainType.REST }
        ChipChoice("Exertional", type == ChestPainType.EXERTIONAL) { type = ChestPainType.EXERTIONAL }
    }
    InlineFlag(AlertRules.checkChestPain(count, type)?.message)
    SaveButton(enabled = count == 0 || type != null) { viewModel.submitChestPain(count, type); onDismiss() }
}

@Composable
private fun ActivityEntry(todayEntry: DailyEntryEntity?, viewModel: TodayViewModel, onDismiss: () -> Unit) {
    var minutesText by remember { mutableStateOf((todayEntry?.stepsOrMinutesWalked ?: 0).toString()) }
    var symptom by remember { mutableStateOf(todayEntry?.symptomThatStoppedActivity.orEmpty()) }
    val minutes = minutesText.toIntOrNull()
    val isValid = minutes != null && minutes in 0..1440
    OutlinedTextField(
        value = minutesText,
        onValueChange = { minutesText = it.filter(Char::isDigit) },
        label = { Text("Steps or minutes walked") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = numberFieldColors(),
        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
    )
    if (minutes != null && !isValid) InlineValidationWarning("Minutes walked must be between 0 and 1440.")
    OutlinedTextField(
        value = symptom,
        onValueChange = { symptom = it },
        label = { Text("Symptom that stopped activity (optional)") },
        colors = numberFieldColors(),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).bringIntoViewOnFocus(),
    )
    SaveButton(enabled = isValid) {
        viewModel.submitActivity(minutes!!, symptom.ifBlank { null })
        onDismiss()
    }
}

@Composable
private fun SymptomFlagsEntry(todayEntry: DailyEntryEntity?, viewModel: TodayViewModel, onDismiss: () -> Unit) {
    var palpitations by remember { mutableStateOf(todayEntry?.palpitations ?: false) }
    var syncope by remember { mutableStateOf(todayEntry?.syncope ?: false) }
    var nearSyncope by remember { mutableStateOf(todayEntry?.nearSyncope ?: false) }
    ToggleRow("Palpitations", palpitations) { palpitations = it }
    ToggleRow("Syncope (fainting)", syncope) { syncope = it }
    ToggleRow("Near-syncope", nearSyncope) { nearSyncope = it }
    InlineFlag(AlertRules.checkSymptomFlags(palpitations, syncope, nearSyncope)?.message)
    if (syncope || nearSyncope) {
        Text(
            "This will trigger the emergency escalation screen.",
            color = AlertRed,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    SaveButton(enabled = true) { viewModel.submitSymptomFlags(palpitations, syncope, nearSyncope); onDismiss() }
}

@Composable
private fun NyhaEntry(todayEntry: DailyEntryEntity?, viewModel: TodayViewModel, onDismiss: () -> Unit) {
    var selectedClass by remember { mutableStateOf(todayEntry?.nyhaClass ?: NyhaClass.I) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Select your level of breathlessness:", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipChoice("Class I: No limitation with ordinary activity", selectedClass == NyhaClass.I) { selectedClass = NyhaClass.I }
            ChipChoice("Class II: Slight limitation with ordinary activity", selectedClass == NyhaClass.II) { selectedClass = NyhaClass.II }
            ChipChoice("Class III: Marked limitation with mild activity", selectedClass == NyhaClass.III) { selectedClass = NyhaClass.III }
            ChipChoice("Class IV: Severe symptoms even at rest", selectedClass == NyhaClass.IV) { selectedClass = NyhaClass.IV }
        }
        InlineFlag(AlertRules.checkBreathlessness(selectedClass)?.message)
        SaveButton(enabled = true) {
            viewModel.submitBreathlessness(selectedClass)
            onDismiss()
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = AccentYellowGold, checkedTrackColor = AccentYellowGold.copy(alpha = 0.4f)),
        )
    }
}

@Composable
private fun ChipChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentYellowGold, selectedLabelColor = androidx.compose.ui.graphics.Color(0xFF241A00)),
    )
}
