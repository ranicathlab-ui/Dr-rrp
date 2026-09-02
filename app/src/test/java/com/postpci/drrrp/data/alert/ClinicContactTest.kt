package com.postpci.drrrp.data.alert

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Trivial by design — but this is exactly the kind of value a typo or an accidental revert
 * silently breaks for cardiac patients in an emergency, so it gets a named regression guard
 * rather than relying on someone noticing during manual testing.
 */
class ClinicContactTest {
    @Test
    fun phoneNumber_isDrRajaramPrasadsNumber() {
        assertEquals("9894184664", ClinicContact.PHONE_NUMBER)
    }

    @Test
    fun contactLabel_namesDrRajaramPrasad_notAGenericClinicLabel() {
        assertEquals("Contact Dr. Rajaram Prasad", ClinicContact.CONTACT_LABEL)
    }
}
