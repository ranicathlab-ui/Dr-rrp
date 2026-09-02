package com.postpci.drrrp.data.alert

import com.postpci.drrrp.data.local.entity.BleedingEventEntity
import com.postpci.drrrp.data.local.entity.DailyEntryEntity
import com.postpci.drrrp.data.model.AlertSeverity
import com.postpci.drrrp.data.model.ChestPainType
import com.postpci.drrrp.data.model.NyhaClass

/** One flagged reading, ready to become an [com.postpci.drrrp.data.local.entity.AlertEntity]. */
data class AlertDraft(
    val fieldKey: String,
    val severity: AlertSeverity,
    val message: String,
    val normalRangeText: String,
)

/**
 * Client-side range checks — see "Alert Logic" in the product spec. Two tiers:
 * [AlertSeverity.ROUTINE] gets a banner + dashboard flag + push notification with a
 * "Contact Dr. Rajaram Prasad" action; [AlertSeverity.EMERGENCY] gets the distinct full-screen
 * escalation, same contact action but as the single, visually primary button — there's no second
 * action to balance it against. A field only ever produces one alert per check.
 *
 * Judgment calls where the spec doesn't fully pin down a rule (documented inline): near-syncope
 * is escalated alongside syncope (both are on the syncope spectrum); palpitations alone is
 * routine, not emergency; exertional chest pain is routine while unrelieved *rest* pain is the
 * emergency case named in the spec; a missed DAPT dose is flagged routine given stent-thrombosis
 * risk.
 */
object AlertRules {

    fun checkRestingHeartRate(bpm: Int): AlertDraft? {
        if (bpm in 50..90) return null
        return AlertDraft("restingHeartRate", AlertSeverity.ROUTINE, "Resting heart rate $bpm bpm is outside the expected range.", "50–90 bpm")
    }

    /** Only systolic has a stated hard flag threshold; diastolic is shown against the <80 target but doesn't trigger on its own. */
    fun checkBloodPressure(systolic: Int, diastolic: Int): AlertDraft? {
        if (systolic < 90 || systolic > 180) {
            return AlertDraft(
                "bpSystolic",
                AlertSeverity.ROUTINE,
                "Blood pressure $systolic/$diastolic mmHg — systolic is outside the safe range.",
                "target <130/80; flag if systolic <90 or >180",
            )
        }
        return null
    }

    fun checkSpo2(percent: Int): AlertDraft? {
        if (percent >= 94) return null
        return AlertDraft("spo2", AlertSeverity.ROUTINE, "SpO2 $percent% is below the safe threshold.", "≥94%")
    }

    /** Needs the last 3 days of entries (not counting today) to detect a rapid-gain trend. */
    fun checkWeightGain(todayKg: Double, entriesLast3Days: List<DailyEntryEntity>): AlertDraft? {
        val earliestInWindow = entriesLast3Days.mapNotNull { it.weightKg }.minOrNull() ?: return null
        val gain = todayKg - earliestInWindow
        if (gain <= 2.0) return null
        return AlertDraft(
            "weight",
            AlertSeverity.ROUTINE,
            "Weight gain of %.1f kg over the last 3 days.".format(gain),
            "flag if gain >2 kg over 3 days",
        )
    }

    fun checkAccessSite(bleeding: Boolean, swelling: Boolean, pain: Boolean, discolouration: Boolean): AlertDraft? {
        if (!bleeding && !swelling && !pain && !discolouration) return null
        val symptoms = listOfNotNull(
            "bleeding".takeIf { bleeding },
            "swelling".takeIf { swelling },
            "pain".takeIf { pain },
            "discolouration".takeIf { discolouration },
        ).joinToString(", ")
        return AlertDraft("accessSiteCheck", AlertSeverity.ROUTINE, "Access-site check flagged: $symptoms.", "no bleeding, swelling, pain, or discolouration")
    }

    fun checkDaptTaken(taken: Boolean): AlertDraft? {
        if (taken) return null
        return AlertDraft("medicationsTaken", AlertSeverity.ROUTINE, "DAPT dose not marked as taken today.", "DAPT must be taken as prescribed")
    }

    /** Chest pain at rest, unrelieved, is the emergency case named in the spec; exertional pain is routine. */
    fun checkChestPain(count: Int, type: ChestPainType?): AlertDraft? {
        if (count <= 0) return null
        return if (type == ChestPainType.REST) {
            AlertDraft("chestPain", AlertSeverity.EMERGENCY, "Chest pain at rest reported ($count episode(s)) — treated as an emergency.", "no chest pain at rest")
        } else {
            AlertDraft("chestPain", AlertSeverity.ROUTINE, "Chest pain reported ($count episode(s)).", "no chest pain")
        }
    }

    /** Syncope and near-syncope both escalate to the emergency tier; palpitations alone is routine. */
    fun checkSymptomFlags(palpitations: Boolean, syncope: Boolean, nearSyncope: Boolean): AlertDraft? {
        if (syncope) return AlertDraft("syncope", AlertSeverity.EMERGENCY, "Syncope (fainting) reported — treated as an emergency.", "no syncope")
        if (nearSyncope) return AlertDraft("syncope", AlertSeverity.EMERGENCY, "Near-syncope reported — treated as an emergency.", "no near-syncope")
        if (palpitations) return AlertDraft("palpitations", AlertSeverity.ROUTINE, "Palpitations reported today.", "no palpitations")
        return null
    }

    fun checkBleedingEvent(event: BleedingEventEntity): AlertDraft? {
        return if (event.neededMedicalAttention) {
            AlertDraft("bleedingEvent", AlertSeverity.EMERGENCY, "Bleeding event at ${event.site} needed medical attention — treated as an emergency.", "no bleeding needing medical attention")
        } else {
            AlertDraft("bleedingEvent", AlertSeverity.ROUTINE, "Bleeding event logged at ${event.site}.", "no bleeding events")
        }
    }

    /** Breathlessness evaluation: Class I is normal; Class II/III is routine; Class IV (at rest) is emergency. */
    fun checkBreathlessness(nyha: NyhaClass?): AlertDraft? {
        if (nyha == null || nyha == NyhaClass.I) return null
        return if (nyha == NyhaClass.IV) {
            AlertDraft("nyhaClass", AlertSeverity.EMERGENCY, "NYHA Class IV breathlessness (symptoms at rest) — treated as an emergency.", "NYHA Class I (no limitation)")
        } else {
            AlertDraft("nyhaClass", AlertSeverity.ROUTINE, "Breathlessness reported today (NYHA Class ${nyha.name}).", "NYHA Class I (no limitation)")
        }
    }
}
