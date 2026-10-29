package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.update.GithubUpdate
import com.example.update.UpdateManifest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AppUpdateCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val updater = remember(context) { GithubUpdate(context) }
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    var manifest by remember { mutableStateOf<UpdateManifest?>(null) }
    var downloaded by remember { mutableStateOf<File?>(null) }
    var message by remember { mutableStateOf("GitHub-Releases prüfen") }
    var progress by remember { mutableIntStateOf(0) }
    DisposableEffect(updater) { onDispose { job?.cancel() } }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("App-Update", style = MaterialTheme.typography.titleMedium, color = Color(0xFF102033))
            Text(message, color = Color(0xFF334155))
            manifest?.let { Text("Version ${it.versionName}: ${it.releaseNotes}", color = Color(0xFF334155)) }
            if (job?.isActive == true && progress > 0) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = job?.isActive != true, onClick = {
                    manifest = null; downloaded = null; progress = 0
                    job = scope.launch {
                        message = "Prüfe GitHub…"
                        try { manifest = updater.check(); message = "Update verfügbar" }
                        catch (e: Exception) { message = e.message ?: "Prüfung fehlgeschlagen" }
                    }
                }) { Text("Jetzt prüfen") }
                if (job?.isActive == true) OutlinedButton(onClick = { job?.cancel(); updater.cleanup(); downloaded = null; message = "Abgebrochen" }) { Text("Abbrechen") }
            }
            manifest?.let { selected ->
                if (downloaded == null) Button(enabled = job?.isActive != true, onClick = {
                    job = scope.launch {
                        message = "Lade APK…"
                        try { downloaded = updater.download(selected) { progress = it }; message = "APK geprüft. Installation nur auf Knopfdruck." }
                        catch (e: Exception) { downloaded = null; message = e.message ?: "Download fehlgeschlagen" }
                    }
                }) { Text("APK herunterladen") }
                downloaded?.let { apk -> Button(onClick = {
                    try { message = if (updater.install(apk, selected)) "Android-Installation geöffnet" else "Installationen aus dieser Quelle erlauben, dann erneut Installieren drücken" }
                    catch (e: Exception) { updater.cleanup(); downloaded = null; message = e.message ?: "APK-Prüfung fehlgeschlagen" }
                }) { Text("Installieren") } }
            }
        }
    }
}
