package com.example.ui.screens.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.screens.PlayerSettingsScreen

/**
 * Decoupled Player Settings dialog wrapping PlayerSettingsScreen.
 */
@Composable
fun PlayerSettingsDialog(
    onDismissRequest: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        PlayerSettingsScreen(onNavigateBack = onDismissRequest)
    }
}
