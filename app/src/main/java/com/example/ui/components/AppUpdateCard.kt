package com.example.ui.components

import com.example.ui.tr
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
import com.example.ui.theme.simPalette

@Composable
fun AppUpdateCard() {
    val p = simPalette()
    val context = androidx.compose.ui.platform.LocalContext.current
    val updater = remember(context) { GithubUpdate(context) }
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    var manifest by remember { mutableStateOf<UpdateManifest?>(null) }
    var downloaded by remember { mutableStateOf<File?>(null) }
    var message by remember { mutableStateOf(tr("GitHub-Releases prüfen")) }
    var progress by remember { mutableIntStateOf(0) }
    DisposableEffect(updater) { onDispose { job?.cancel() } }
    Card(colors = CardDefaults.cardColors(containerColor = p.card), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(tr("App-Update"), style = MaterialTheme.typography.titleMedium, color = p.ink)
            Text(tr(message), color = p.body)
            manifest?.let { Text("${tr("Version")} ${it.versionName}: ${it.releaseNotes}", color = p.body) }
            if (job?.isActive == true && progress > 0) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = job?.isActive != true, onClick = {
                    manifest = null; downloaded = null; progress = 0
                    job = scope.launch {
                        message = "Prüfe GitHub…"
                        try { manifest = updater.check(); message = "Update verfügbar" }
                        catch (e: Exception) { message = e.message ?: tr("Prüfung fehlgeschlagen") }
                    }
                }) { Text(tr("Jetzt prüfen")) }
                if (job?.isActive == true) OutlinedButton(onClick = { job?.cancel(); updater.cleanup(); downloaded = null; message = "Abgebrochen" }) { Text(tr("Abbrechen")) }
            }
            manifest?.let { selected ->
                if (downloaded == null) Button(enabled = job?.isActive != true, onClick = {
                    job = scope.launch {
                        message = "Lade APK…"
                        try { downloaded = updater.download(selected) { progress = it }; message = "APK geprüft. Installation nur auf Knopfdruck." }
                        catch (e: Exception) { downloaded = null; message = e.message ?: tr("Download fehlgeschlagen") }
                    }
                }) { Text(tr("APK herunterladen")) }
                downloaded?.let { apk -> Button(onClick = {
                    try { message = if (updater.install(apk, selected)) tr("Android-Installation geöffnet") else tr("Installationen aus dieser Quelle erlauben, dann erneut Installieren drücken") }
                    catch (e: Exception) { updater.cleanup(); downloaded = null; message = e.message ?: tr("APK-Prüfung fehlgeschlagen") }
                }) { Text(tr("Installieren")) } }
            }
        }
    }
}
