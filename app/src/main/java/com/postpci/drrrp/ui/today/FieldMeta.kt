package com.postpci.drrrp.ui.today

import com.postpci.drrrp.data.schedule.MonitoringSchedule

data class FieldMeta(val label: String, val rangeText: String)

/** Display label + inline normal-range text for each due-field card on the Today grid. */
val fieldMetaByKey: Map<String, FieldMeta> = mapOf(
    MonitoringSchedule.CHEST_PAIN to FieldMeta("Chest pain", "none expected"),
    MonitoringSchedule.BREATHLESSNESS to FieldMeta("Breathlessness (NYHA)", "class I–II expected"),
    MonitoringSchedule.RESTING_HEART_RATE to FieldMeta("Resting heart rate", "50–90 bpm"),
    MonitoringSchedule.BLOOD_PRESSURE to FieldMeta("Blood pressure", "target <130/80"),
    MonitoringSchedule.WEIGHT to FieldMeta("Weight", "flag if +2kg in 3 days"),
    MonitoringSchedule.SPO2 to FieldMeta("SpO2", "≥94%"),
    MonitoringSchedule.ACCESS_SITE_CHECK to FieldMeta("Access-site check", "no bleeding/swelling/pain"),
    MonitoringSchedule.ACTIVITY to FieldMeta("Activity", "steps or minutes walked"),
    MonitoringSchedule.PALPITATIONS_SYNCOPE to FieldMeta("Palpitations / syncope", "none expected"),
)
