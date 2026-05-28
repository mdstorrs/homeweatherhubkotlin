package com.storrs.homeweatherhub

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val versionName = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(0)
            ).versionName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }
    }.getOrNull() ?: "0.0.1"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "About") },
        text = {
            Column {
                Text(
                    text = "Home Weather Hub",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Version $versionName")
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Personal weather station dashboard.")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}
