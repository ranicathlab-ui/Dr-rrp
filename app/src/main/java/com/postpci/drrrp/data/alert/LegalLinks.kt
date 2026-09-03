package com.postpci.drrrp.data.alert

/**
 * Hosted Privacy Policy / Terms & Conditions pages — required by Google Play's Health Apps
 * declaration and Data Safety section (a working, publicly-accessible URL both inside the app
 * and in Play Console), and linked to from [com.postpci.drrrp.ui.onboarding.DisclaimerScreen]
 * and the Profile screen. Update these constants if the pages are ever moved.
 */
object LegalLinks {
    const val PRIVACY_POLICY_URL = "https://dr-rrp-aasai-backend.onrender.com/privacy-policy"
    const val TERMS_URL = "https://dr-rrp-aasai-backend.onrender.com/terms"

    const val PRIVACY_POLICY_TEXT = """DR RRP — Privacy Policy
Aasai Health Centre, Salem, Tamil Nadu, India (Dr. A. Rajaram Prasad)

1. Introduction
DR RRP ("the Application") is a cardiac post-procedure recovery monitoring system developed for Aasai Health Centre, Salem, India, under Dr. A. Rajaram Prasad. We are committed to protecting the privacy, confidentiality, and security of patient health data.

2. Information We Collect
• Patient Demographics: Name, age, sex, contact number, comorbidities, and home medications.
• Clinical Baseline Data: Procedural details (PCI date, stent specifications, STEMI territory), laboratory values, discharge vitals, and medication regimens.
• Daily Recovery Logs: Resting heart rate, blood pressure, SpO2, body weight, chest pain episodes, breathlessness levels, access-site status, activity levels, and medication adherence.
• Communication & Chat Data: Direct in-app messages between patients/caregivers and clinic staff.

3. How We Use Your Information
• To monitor post-procedure recovery and identify out-of-range vitals or symptoms requiring medical attention.
• To enable direct communication between patients, caregivers, and authorized cardiology staff at Aasai Health Centre.
• To send automated alert notifications regarding recovery milestones and vital checks.

4. Data Storage & Security
• On-device data is encrypted at rest using AES-256 SQLCipher encryption.
• Data in transit is encrypted using Secure Sockets Layer / Transport Layer Security (TLS/HTTPS).
• Role-based access control ensures patient health data is accessible only by authorized clinical staff and linked caregivers.

5. Data Deletion Rights
Patients have the right to request complete deletion of their account and health data at any time via the Profile section of the Application or by contacting Aasai Health Centre directly.

Aasai Health Centre
Salem, Tamil Nadu, India
Director: Dr. A. Rajaram Prasad
Phone: +91 98941 84664"""

    const val TERMS_TEXT = """DR RRP — Terms & Conditions
Aasai Health Centre, Salem, Tamil Nadu, India (Dr. A. Rajaram Prasad)

1. Acceptance of Terms
By registering or using the DR RRP application, you agree to comply with these Terms & Conditions.

2. Critical Medical Disclaimer — Not an Emergency Response System
DR RRP is a recovery monitoring and follow-up communication tool. It is NOT an automated real-time emergency dispatch or emergency triage system. If you experience severe chest pain, extreme breathlessness, sudden cold sweats, or loss of consciousness, CALL EMERGENCY SERVICES (108) OR PROCEED TO THE NEAREST HOSPITAL IMMEDIATELY.

3. Intended Use
The Application is designed exclusively for post-PCI (angioplasty) patients under the care of Aasai Health Centre, Salem. It facilitates routine recovery tracking, vital log entries, and non-emergency communication with clinical staff.

4. Account Confidentiality
Users are responsible for maintaining the confidentiality of their login credentials.

5. Revisions to Terms
Aasai Health Centre reserves the right to update these terms to reflect medical, legal, or technological improvements.

Aasai Health Centre
Salem, Tamil Nadu, India
Director: Dr. A. Rajaram Prasad
Phone: +91 98941 84664"""
}
