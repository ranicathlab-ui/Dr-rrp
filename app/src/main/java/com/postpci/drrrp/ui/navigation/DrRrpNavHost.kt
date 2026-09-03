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
import com.postpci.drrrp.ui.splash.SplashScreen
import com.postpci.drrrp.ui.staff.StaffShell

object Routes {
    const val SPLASH = "splash"
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
 * Single nav graph, routed by [com.postpci.drrrp.data.auth.AuthGateway.currentUser] and
 * [com.postpci.drrrp.data.auth.AuthGateway.isSessionRestored].
 * Displays [SplashScreen] while cold-start session restoration is in progress to prevent
 * any unwanted login screen flashing.
 */
@Composable
fun DrRrpNavHost(application: DrRrpApplication, navController: NavHostController = rememberNavController()) {
    val currentUser by application.authGateway.currentUser.collectAsState()
    val isSessionRestored by application.authGateway.isSessionRestored.collectAsState()

    val startDestination = remember {
        when {
            !isSessionRestored -> Routes.SPLASH
            application.disclaimerPreferences.hasAcknowledged -> Routes.LOGIN
            else -> Routes.DISCLAIMER
        }
    }

    LaunchedEffect(isSessionRestored, currentUser) {
        if (!isSessionRestored) return@LaunchedEffect
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
        composable(Routes.SPLASH) {
            SplashScreen()
        }
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
