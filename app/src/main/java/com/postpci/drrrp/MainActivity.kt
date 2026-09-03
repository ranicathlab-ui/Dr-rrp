package com.postpci.drrrp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.postpci.drrrp.ui.navigation.DrRrpNavHost
import com.postpci.drrrp.ui.theme.BackgroundNearBlack
import com.postpci.drrrp.ui.theme.DrRrpTheme

class MainActivity : ComponentActivity() {
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way — see DrRrpMessagingService's notify() guard */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent {
            DrRrpTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BackgroundNearBlack),
                ) {
                    DrRrpNavHost(
                        application = application as DrRrpApplication,
                    )
                }
            }
        }
    }

    /** POST_NOTIFICATIONS is a runtime permission from API 33 onward — without it, FCM alert
     * pushes (DrRrpMessagingService) would silently never show. Below 33 it's granted at install
     * time, so there's nothing to request. */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val alreadyGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!alreadyGranted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
