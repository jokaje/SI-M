package com.example.ui.components

import com.example.ui.tr
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
        Text(tr("Pixelwesen · 64 × 64"), style = MaterialTheme.typography.titleMedium)
        Text(tr("Entwurf lokal. Aussehen unabhängig von Persönlichkeit, Stimme und Erinnerungen."))
        if (draft != null) {
            AvatarPixelView(draft, 192.dp)
            val bytes = remember(draft) { AvatarDnaWire.decode(draft) }
            Text("Seed: " + (0..3).fold(0L) { acc, i -> acc or ((bytes[4+i].toLong() and 255) shl (i*8)) })
            Text(if (state.connected && state.selectedCharacter == "custom" && state.avatarDna == draft)
                tr("Auf Gerät bestätigt (DNA + Auswahl)") else tr("Nur Entwurf / nicht als aktives Gerät bestätigt"))
            OutlinedTextField(value = name, onValueChange = onName, label = { Text(tr("Name (lokal)")) },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            for (i in 0..7) {
                val value = bytes[8+i].toInt() and 255
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${tr(AvatarDraft.traitNames[i])}: $value")
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
                }, label = { Text(tr(AvatarDraft.colorNames[i]) + " #RRGGBB") },
                    isError = edit.length == 7 && !edit.matches(Regex("#[0-9a-fA-F]{6}")),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }
        Button(onClick = onRoll, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(tr("Neues Wesen würfeln · unbegrenzt")) }
        Button(onClick = onSave, enabled = draft != null && name.isNotBlank() && favorites.size < 32,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(tr("Als lokalen Favoriten speichern")) }
        Text(com.example.ui.trTemplate("favoriteCount", favorites.size))
        favorites.forEach { favorite ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { onFavorite(favorite.id) }) { Text(favorite.name.take(24)) }
                TextButton(onClick = { deleteFavoriteId = favorite.id }) { Text(tr("Entfernen")) }
            }
        }
        Text(tr(state.avatarStatus), color = p.body)
        Button(onClick = onInstall, enabled = draft != null && state.connected && state.trusted &&
            state.authorized && state.setupOpen && state.avatarSupported && !state.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(tr("Entwurf auf Orb übertragen und aktivieren")) }
        if (!state.avatarSupported) Text(tr("Ältere Firmware oder nicht gelesen: lokal weiter gestalten; kein Gerätetransfer."))
        if (!state.setupOpen) Text(tr("Für Gerätetransfer am Orb die physische Orb-Kopplung öffnen."))
        if (state.avatarDna != null) {
            Text(tr("Gerät-Slot: ") + if (state.selectedCharacter == "custom") tr("aktiv") else tr("gespeichert, andere Figur aktiv"))
            OutlinedButton(onClick = { deleteDeviceDialog = true }, enabled = state.authorized && !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(tr("Geräte-Avatar löschen")) }
        }
    }
    if (deleteDeviceDialog) AlertDialog(onDismissRequest = { deleteDeviceDialog = false },
        title = { Text(tr("Geräte-Avatar löschen?")) }, text = { Text(tr("Lokale Favoriten bleiben. Bei aktivem Custom wird Flame gewählt.")) },
        confirmButton = { TextButton(onClick = { deleteDeviceDialog = false; onDeleteDevice() }) { Text(tr("Löschen")) } },
        dismissButton = { TextButton(onClick = { deleteDeviceDialog = false }) { Text(tr("Abbrechen")) } })
    deleteFavoriteId?.let { id -> AlertDialog(onDismissRequest = { deleteFavoriteId = null },
        title = { Text(tr("Favorit entfernen?")) },
        confirmButton = { TextButton(onClick = { deleteFavoriteId = null; onDeleteFavorite(id) }) { Text(tr("Entfernen")) } },
        dismissButton = { TextButton(onClick = { deleteFavoriteId = null }) { Text(tr("Abbrechen")) } }) }
}
