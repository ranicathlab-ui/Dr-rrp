package com.postpci.drrrp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.postpci.drrrp.DrRrpApplication
import com.postpci.drrrp.data.model.UserRole
import com.postpci.drrrp.ui.auth.LoginScreen
import com.postpci.drrrp.ui.home.CaregiverHome
import com.postpci.drrrp.ui.home.PatientHome
import com.postpci.drrrp.ui.onboarding.DisclaimerScreen
import com.postpci.drrrp.ui.staff.StaffShell

object Routes {
    const val DISCLAIMER = "disclaimer"
    const val LOGIN = "login"
    const val PATIENT_HOME = "patient_home"
    const val CAREGIVER_HOME = "caregiver_home"
    const val STAFF_HOME = "staff_home"
}

private fun UserRole.homeRoute(): String = when (this) {
    UserRole.PATIENT -> Routes.PATIENT_HOME
    UserRole.CAREGIVER -> Routes.CAREGIVER_HOME
    UserRole.STAFF -> Routes.STAFF_HOME
}

/**
 * Single nav graph, routed by [com.postpci.drrrp.data.auth.AuthGateway.currentUser]: signed out
 * shows the login graph, signed in jumps straight to that role's home graph and clears the back
 * stack so "back" from home never returns to the login screen.
 *
 * A device that hasn't yet acknowledged [DisclaimerScreen] sees that instead of the login screen
 * — required by Google Play's health-app review before any sign-in is reachable. It's gated by
 * [com.postpci.drrrp.data.onboarding.DisclaimerPreferences], device-scoped (not per-account), so
 * it only ever shows once per install.
 */
@Composable
fun DrRrpNavHost(application: DrRrpApplication, navController: NavHostController = rememberNavController()) {
    val currentUser by application.authGateway.currentUser.collectAsState()

    // Read once at first composition, not inside the effect below, so the very first frame
    // already renders the right screen instead of flashing the wrong one before the effect fires.
    val startDestination = remember {
        if (application.disclaimerPreferences.hasAcknowledged) Routes.LOGIN else Routes.DISCLAIMER
    }

    LaunchedEffect(currentUser) {
        val target = when {
            currentUser != null -> currentUser!!.role.homeRoute()
            !application.disclaimerPreferences.hasAcknowledged -> Routes.DISCLAIMER
            else -> Routes.LOGIN
        }
        navController.navigate(target) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.DISCLAIMER) {
            DisclaimerScreen(application = application) {
                navController.navigate(Routes.LOGIN) {
                    popUpTo(Routes.DISCLAIMER) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
        composable(Routes.LOGIN) {
            LoginScreen(application = application, modifier = Modifier)
        }
        composable(Routes.PATIENT_HOME) {
            PatientHome(application = application)
        }
        composable(Routes.CAREGIVER_HOME) {
            CaregiverHome(application = application)
        }
        composable(Routes.STAFF_HOME) {
            StaffShell(application = application)
        }
    }
}
