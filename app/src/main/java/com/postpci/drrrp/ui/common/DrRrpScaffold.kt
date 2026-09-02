package com.postpci.drrrp.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.postpci.drrrp.ui.theme.HeaderDeepBlue
import com.postpci.drrrp.ui.theme.TextPrimary
import com.postpci.drrrp.ui.theme.appBackground

/**
 * Shared screen chrome: the deep-blue header bar and the app's standard dark background.
 * Every screen except the role home screens shows a back button per the accessibility spec;
 * the hardware back button always works regardless (handled by the nav graph itself).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrRrpScaffold(
    title: String,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = false,
    onBack: () -> Unit = {},
    actions: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(title, color = TextPrimary) },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                    }
                },
                actions = { actions() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HeaderDeepBlue),
            )
        },
    ) { innerPadding ->
        content(Modifier.appBackground().padding(innerPadding))
    }
}
