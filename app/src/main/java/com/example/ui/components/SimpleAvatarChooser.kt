package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.orb.SetupState

/** Selection is a local preview until the explicit confirmation action. */
@Composable
fun SimpleAvatarChooser(
    state: SetupState, draft: String?, name: String,
    onRoll: () -> Unit, onName: (String) -> Unit,
    onSelectBuiltIn: (String) -> Unit, onConfirmRandom: () -> Unit,
    onContinue: (() -> Unit)? = null,
    selectedChoice: String? = null, onChoose: ((String) -> Unit)? = null,
    busy: Boolean = false, error: String? = null
) {
    var localChoice by remember { mutableStateOf<String?>(null) }
    val choice = if (onChoose != null) selectedChoice else localChoice
    fun choose(value: String) { if (onChoose != null) onChoose(value) else localChoice = value }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("simple_avatar_chooser")) {
        Text("Figur wählen", style = MaterialTheme.typography.titleMedium)
        Text("Wähle eine der fünf Figuren oder würfle ein neues Pixelwesen. Stimme und Erinnerungen bleiben unverändert.")
        AcceptedAvatarFrames.ids.sorted().forEach { id ->
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .clickable(enabled = !busy) { choose(id) }.testTag("choose_$id"),
                shape = RoundedCornerShape(12.dp),
                color = if (choice == id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                contentColor = if (choice == id) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            ) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AcceptedAvatarPixelView(id, 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(id.replace('_', ' '), modifier = Modifier.weight(1f))
                    if (choice == id) Text("Vorschau gewählt")
                }
            }
        }
        OutlinedButton(onClick = { onRoll(); choose("random") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("roll_avatar")) {
            Text("Zufälliges Wesen erzeugen")
        }
        if (choice == "random" && draft != null) AvatarPixelView(draft, 160.dp)
        OutlinedTextField(value = name, onValueChange = onName, label = { Text("Name (nur lokal)") },
            singleLine = true, modifier = Modifier.fillMaxWidth().testTag("avatar_local_name"),
            colors = com.example.ui.theme.simOutlinedTextFieldColors())
        Text(state.selectedCharacter?.let { "Zuletzt vom Orb bestätigt: $it" } ?: "Noch keine Geräteauswahl bestätigt")
        val canWrite = state.connected && state.trusted && state.authorized && state.setupOpen && !state.busy
        Button(onClick = {
            if (onContinue != null) onContinue() else when (choice) {
                "random" -> onConfirmRandom()
                null -> Unit
                else -> onSelectBuiltIn(choice!!)
            }

        }, enabled = choice != null && name.isNotBlank() && canWrite && !busy &&
            (choice != "random" || (draft != null && state.avatarSupported)),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("confirm_avatar_button")) {
            Text(if (onContinue == null) "Auswahl auf Orb bestätigen" else "Auswahl am Orb anfordern und weiter")
        }
        if (busy) Text("Warte auf bestätigten Orb-Readback …")
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
        if (!canWrite) Text("Zum Aktivieren Orb verbinden und das physische Setup-Fenster öffnen. Die Vorschau bleibt lokal.")
        if (choice == "random" && !state.avatarSupported) Text("Diese Firmware unterstützt keinen Transfer neuer Pixelwesen.")
    }
}
