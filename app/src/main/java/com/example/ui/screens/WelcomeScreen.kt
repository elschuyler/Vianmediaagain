package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.LogKeeper

@Composable
fun WelcomeScreen(onPermissionsGranted: () -> Unit, onSkip: () -> Unit) {
    var permissionsGranted by remember { mutableStateOf(false) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            LogKeeper.log("Permissions granted by user")
            permissionsGranted = true
            onPermissionsGranted()
        } else {
            LogKeeper.logError("Permissions", "Permissions denied by user")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to Vianbhr Media",
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "To access your local media, we need storage permissions.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = {
                val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    arrayOf(
                        Manifest.permission.READ_MEDIA_AUDIO,
                        Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                } else {
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                LogKeeper.log("Requesting permissions: ${'$'}{permissionsToRequest.joinToString()}")
                permissionLauncher.launch(permissionsToRequest)
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Grant Permissions & Continue")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(onClick = { onSkip() }) {
            Text("Skip for now")
        }
    }
}
