package com.postpci.drrrp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.postpci.drrrp.ui.staff.StaffShell

object Routes {
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
 */
@Composable
fun DrRrpNavHost(application: DrRrpApplication, navController: NavHostController = rememberNavController()) {
    val currentUser by application.authGateway.currentUser.collectAsState()

    LaunchedEffect(currentUser) {
        val target = currentUser?.role?.homeRoute() ?: Routes.LOGIN
        navController.navigate(target) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    NavHost(navController = navController, startDestination = Routes.LOGIN) {
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
