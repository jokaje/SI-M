package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.model.AvatarDraft
import com.example.model.AvatarFavorite
import com.example.orb.AvatarDnaWire
import com.example.orb.SetupState
import com.example.ui.theme.simPalette

/** Local drafts remain editable offline; device controls are capability and window gated. */
@Composable
fun AvatarGeneratorPanel(
    state: SetupState, draft: String?, name: String, favorites: List<AvatarFavorite>,
    onRoll: () -> Unit, onName: (String) -> Unit, onTrait: (Int, Int) -> Unit,
    onColor: (Int, String) -> Unit, onSave: () -> Unit, onFavorite: (String) -> Unit,
    onDeleteFavorite: (String) -> Unit, onInstall: () -> Unit, onDeleteDevice: () -> Unit
) {
    val p = simPalette()
    var deleteDeviceDialog by remember { mutableStateOf(false) }
    var deleteFavoriteId by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Pixelwesen · 64 × 64", style = MaterialTheme.typography.titleMedium)
        Text("Entwurf lokal. Aussehen unabhängig von Persönlichkeit, Stimme und Erinnerungen.")
        if (draft != null) {
            AvatarPixelView(draft, 192.dp)
            val bytes = remember(draft) { AvatarDnaWire.decode(draft) }
            Text("Seed: " + (0..3).fold(0L) { acc, i -> acc or ((bytes[4+i].toLong() and 255) shl (i*8)) })
            Text(if (state.connected && state.selectedCharacter == "custom" && state.avatarDna == draft)
                "Auf Gerät bestätigt (DNA + Auswahl)" else "Nur Entwurf / nicht als aktives Gerät bestätigt")
            OutlinedTextField(value = name, onValueChange = onName, label = { Text("Name (lokal)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            for (i in 0..7) {
                val value = bytes[8+i].toInt() and 255
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${AvatarDraft.traitNames[i]}: $value")
                    Row {
                        TextButton(onClick = { onTrait(i, value-1) }, enabled = value > 0) { Text("−") }
                        TextButton(onClick = { onTrait(i, value+1) }, enabled = value < AvatarDraft.traitMax[i]) { Text("+") }
                    }
                }
            }
            for (i in 0..4) {
                var edit by remember(draft, i) { mutableStateOf(AvatarDraft.color(draft, i)) }
                OutlinedTextField(value = edit, onValueChange = { value ->
                    edit = value.take(7)
                    if (edit.matches(Regex("#[0-9a-fA-F]{6}"))) onColor(i, edit)
                }, label = { Text(AvatarDraft.colorNames[i] + " #RRGGBB") },
                    isError = edit.length == 7 && !edit.matches(Regex("#[0-9a-fA-F]{6}")),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }
        Button(onClick = onRoll, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Neues Wesen würfeln · unbegrenzt") }
        Button(onClick = onSave, enabled = draft != null && name.isNotBlank() && favorites.size < 32,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Als lokalen Favoriten speichern") }
        Text("Favoriten (${favorites.size}/32)")
        favorites.forEach { favorite ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { onFavorite(favorite.id) }) { Text(favorite.name.take(24)) }
                TextButton(onClick = { deleteFavoriteId = favorite.id }) { Text("Entfernen") }
            }
        }
        Text(state.avatarStatus, color = p.body)
        Button(onClick = onInstall, enabled = draft != null && state.connected && state.trusted &&
            state.authorized && state.setupOpen && state.avatarSupported && !state.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Entwurf auf Orb übertragen und aktivieren") }
        if (!state.avatarSupported) Text("Ältere Firmware oder nicht gelesen: lokal weiter gestalten; kein Gerätetransfer.")
        if (!state.setupOpen) Text("Für Gerätetransfer am Orb die physische Orb-Kopplung öffnen.")
        if (state.avatarDna != null) {
            Text("Gerät-Slot: " + if (state.selectedCharacter == "custom") "aktiv" else "gespeichert, andere Figur aktiv")
            OutlinedButton(onClick = { deleteDeviceDialog = true }, enabled = state.authorized && !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Geräte-Avatar löschen") }
        }
    }
    if (deleteDeviceDialog) AlertDialog(onDismissRequest = { deleteDeviceDialog = false },
        title = { Text("Geräte-Avatar löschen?") }, text = { Text("Lokale Favoriten bleiben. Bei aktivem Custom wird Flame gewählt.") },
        confirmButton = { TextButton(onClick = { deleteDeviceDialog = false; onDeleteDevice() }) { Text("Löschen") } },
        dismissButton = { TextButton(onClick = { deleteDeviceDialog = false }) { Text("Abbrechen") } })
    deleteFavoriteId?.let { id -> AlertDialog(onDismissRequest = { deleteFavoriteId = null },
        title = { Text("Favorit entfernen?") },
        confirmButton = { TextButton(onClick = { deleteFavoriteId = null; onDeleteFavorite(id) }) { Text("Entfernen") } },
        dismissButton = { TextButton(onClick = { deleteFavoriteId = null }) { Text("Abbrechen") } }) }
}
