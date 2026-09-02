package com.postpci.drrrp.data.alert

/**
 * Dr. A. Rajaram Prasad's contact line at Aasai Health Centre, Salem. Every "contact" action in
 * the app — routine-alert banner, the emergency-escalation screen, the Profile emergency-contact
 * card — dials this same number; there is deliberately no separate emergency-services number.
 */
object ClinicContact {
    const val PHONE_NUMBER = "9894184664"

    /** Shared label for every dial action in the app — keep this the single source of the wording. */
    const val CONTACT_LABEL = "Contact Dr. Rajaram Prasad"
}
