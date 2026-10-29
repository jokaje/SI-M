package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ThemeMode
import com.example.model.CreatureDna
import com.example.orb.AgentProtocol
import com.example.orb.SetupState
import com.example.orb.WifiPresentation
import com.example.ui.theme.simOutlinedTextFieldColors
import com.example.ui.theme.simPalette
import com.example.ui.components.AvatarGeneratorPanel
import com.example.ui.components.SimpleAvatarChooser
import com.example.ui.components.AppUpdateCard
import com.example.ui.components.AvatarPixelView
import com.example.ui.components.AcceptedAvatarPixelView
import com.example.ui.components.AcceptedAvatarFrames
import com.example.model.AvatarFavorite
import kotlin.math.roundToInt

/** One dashboard tab: icon only, the label stays as accessibility description. */
private data class DashTab(val label: String, val icon: ImageVector, val tag: String)

private val DashTabs = listOf(
    DashTab("System", Icons.Default.Settings, "tab_system"),
    DashTab("Figuren", Icons.Default.Groups, "tab_figures"),
    DashTab("Netzwerk", Icons.Default.Public, "tab_network"),
    DashTab("Sprachdienst", Icons.Default.Flag, "tab_agent"),
    DashTab("App", Icons.Default.SystemUpdate, "tab_app"),
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: SetupState,
    avatar: CreatureDna,
    draft: String?, avatarName: String, favorites: List<AvatarFavorite>,
    onRollAvatar: () -> Unit, onNameAvatar: (String) -> Unit, onTrait: (Int, Int) -> Unit,
    onColor: (Int, String) -> Unit, onSaveFavorite: () -> Unit, onFavorite: (String) -> Unit,
    onDeleteFavorite: (String) -> Unit, onInstall: () -> Unit, onDeleteDevice: () -> Unit,
    displayName: String,
    trustedAddress: String?,
    onboardingCompleted: Boolean,
    onPair: () -> Unit,
    onReconnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefreshAll: () -> Unit,
    onRefreshSettings: () -> Unit,
    onVolume: (Int) -> Unit,
    onBrightness: (Int) -> Unit,
    onSleep: (Int) -> Unit,
    onRefreshCatalog: () -> Unit,
    onSelectFigure: (String) -> Unit,
    onRefreshNetworks: () -> Unit,
    onScanWifi: () -> Unit,
    onSaveWifi: (String, String) -> Long?,
    onForgetWifi: (String) -> Unit,
    onReadAgent: () -> Unit,
    onSaveAgent: (String, String) -> Long?,
    onRemoveAgent: () -> Unit,
    onOpenCamera: () -> Unit,
    onStartOnboarding: () -> Unit,
    onLogout: () -> Unit,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by remember { mutableStateOf(0) }
    val p = simPalette()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SI-M", fontWeight = FontWeight.Bold)
                        Text(displayName.ifBlank { "Lokales Profil" }, fontSize = 11.sp, color = p.muted)
                    }
                },
                actions = {
                    IconButton(onClick = onLogout, modifier = Modifier.testTag("logout_button")) {
                        Icon(Icons.Default.Logout, contentDescription = "Abmelden")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = p.card,
                    titleContentColor = p.ink,
                    actionIconContentColor = p.body
                )
            )
        },
        containerColor = p.surface,
        modifier = modifier.testTag("dashboard_screen")
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            DeviceHeader(
                state = state,
                avatar = avatar,
                draft = draft,
                trustedAddress = trustedAddress,
                onPair = onPair,
                onReconnect = onReconnect,
                onDisconnect = onDisconnect,
                onOpenCamera = onOpenCamera
            )

            if (!onboardingCompleted) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = p.infoBg),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Die Einrichtung ist noch nicht abgeschlossen.",
                            fontSize = 12.sp,
                            color = p.infoInk,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onStartOnboarding) { Text("Fortsetzen", color = p.accent) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TabRow(
                selectedTabIndex = tab,
                containerColor = p.card,
                contentColor = p.accent,
                indicator = { positions ->
                    if (tab < positions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(positions[tab]),
                            color = p.accent
                        )
                    }
                },
                divider = {}
            ) {
                DashTabs.forEachIndexed { index, item ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        modifier = Modifier
                            .testTag(item.tag)
                            .padding(vertical = 10.dp),
                        icon = {
                            Icon(
                                item.icon,
                                contentDescription = item.label,
                                modifier = Modifier.size(24.dp),
                                tint = if (tab == index) p.accent else p.muted
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = DashTabs[tab].label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = p.muted
            )

            Spacer(modifier = Modifier.height(8.dp))

            when (tab) {
                0 -> SystemPanel(
                    state = state,
                    avatar = avatar,
                    onRefreshAll = onRefreshAll,
                    onRefreshSettings = onRefreshSettings,
                    onVolume = onVolume,
                    onBrightness = onBrightness,
                    onSleep = onSleep,
                    themeMode = themeMode,
                    onThemeMode = onThemeMode
                )
                1 -> {
                    var expert by remember { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !expert, onClick = { expert = false },
                            label = { Text("Einfach") }, modifier = Modifier.testTag("simple_tab"))
                        FilterChip(selected = expert, onClick = { expert = true },
                            label = { Text("Experte") }, modifier = Modifier.testTag("expert_tab"))
                    }
                    if (expert) {
                        FiguresPanel(state = state, onRefreshCatalog = onRefreshCatalog, onSelectFigure = onSelectFigure)
                        Spacer(modifier = Modifier.height(12.dp))
                        AvatarGeneratorPanel(state, draft, avatarName, favorites, onRollAvatar, onNameAvatar, onTrait,
                            onColor, onSaveFavorite, onFavorite, onDeleteFavorite, onInstall, onDeleteDevice)
                    } else SimpleAvatarChooser(state, draft, avatarName, onRollAvatar, onNameAvatar,
                        onSelectBuiltIn = onSelectFigure, onConfirmRandom = onInstall)
                }
                2 -> NetworkPanel(
                    state = state,
                    onRefreshNetworks = onRefreshNetworks,
                    onScanWifi = onScanWifi,
                    onSaveWifi = onSaveWifi,
                    onForgetWifi = onForgetWifi
                )
                3 -> AgentPanel(
                    state = state,
                    onReadAgent = onReadAgent,
                    onSaveAgent = onSaveAgent,
                    onRemoveAgent = onRemoveAgent
                )
                else -> AppUpdateCard()
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DeviceHeader(
    state: SetupState,
    avatar: CreatureDna,
    draft: String?,
    trustedAddress: String?,
    onPair: () -> Unit,
    onReconnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenCamera: () -> Unit
) {
    val p = simPalette()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = p.card),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val confirmedId = if (state.connected && state.trusted) state.companionCharacterId else state.selectedCharacter
                val activeMode = when (state.companionMode) { "listening" -> 1; "speaking" -> 2; else -> 0 }
                when {
                    confirmedId == "custom" && state.avatarDna != null -> AvatarPixelView(state.avatarDna, 104.dp)
                    confirmedId in AcceptedAvatarFrames.ids -> AcceptedAvatarPixelView(checkNotNull(confirmedId), 104.dp, mode = activeMode)
                    confirmedId == null && draft != null -> AvatarPixelView(draft, 104.dp)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (confirmedId != null) "Orb-Figur: $confirmedId${if (state.connected && state.trusted) " · verifiziert" else " · zuletzt bestätigt"}" else "Lokaler Entwurf · nicht auf Gerät bestätigt", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when {
                            state.connecting -> "Verbindung wird aufgebaut …"
                            state.connected && state.authorized -> "Verbunden · Setup-Fenster offen"
                            state.connected -> "Verbunden · nur lesbar"
                            else -> "Nicht verbunden"
                        },
                        fontSize = 12.sp,
                        color = if (state.connected) p.success else p.muted,
                        fontWeight = FontWeight.Medium
                    )
                    if (state.connected) {
                        Text(
                            state.companionCharacterId?.let { "Figur: $it" } ?: "Figur: nicht gelesen",
                            fontSize = 11.sp,
                            color = p.muted
                        )
                        if (state.companionMode != null) {
                            Text("Modus: ${state.companionMode}", fontSize = 11.sp, color = p.muted)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (!state.connected) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onPair,
                        colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Orb verbinden")
                    }
                    if (trustedAddress != null) {
                        OutlinedButton(onClick = onReconnect, shape = RoundedCornerShape(10.dp)) {
                            Text("Erneut")
                        }
                    }
                }
                if (trustedAddress != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Bekannter Orb: $trustedAddress. Verifizierte Verbindung ist auch bei geschlossenem Setup-Fenster nur lesbar möglich. Für Änderungen am Orb physisches Setup öffnen.",
                        fontSize = 11.sp,
                        color = p.muted
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenCamera, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kamera")
                    }
                    OutlinedButton(onClick = onDisconnect, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                        Text("Trennen")
                    }
                }
            }

            if (!state.authorized && state.connected) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Am Orb das physische Kopplungsfenster starten. Ohne offenes Fenster sind keine Änderungen möglich.",
                    fontSize = 11.sp,
                    color = p.warning,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(state.status, fontSize = 11.sp, color = p.body, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun SystemPanel(
    state: SetupState,
    avatar: CreatureDna,
    onRefreshAll: () -> Unit,
    onRefreshSettings: () -> Unit,
    onVolume: (Int) -> Unit,
    onBrightness: (Int) -> Unit,
    onSleep: (Int) -> Unit,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit
) {
    val p = simPalette()
    val settings = state.settings
    var volume by remember { mutableFloatStateOf(settings?.volume?.toFloat() ?: 50f) }
    var brightness by remember { mutableFloatStateOf(settings?.brightness?.toFloat() ?: 70f) }
    LaunchedEffect(settings) {
        volume = settings?.volume?.toFloat() ?: 50f
        brightness = settings?.brightness?.toFloat() ?: 70f
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InfoCard("Gerät") {
            InfoRow("Setup-Fenster", if (state.setupOpen) "offen" else "geschlossen")
            InfoRow("Besitz bestätigt", if (state.trusted) "ja" else "nein")
            InfoRow("Sicherheitsdienst", if (state.companionSupported) "verfügbar" else "nicht vorhanden")
            InfoRow("Letzter Abruf", if (settings == null) "ausstehend" else "erfolgreich")
            if (state.managementError != null) {
                Text(state.managementError, fontSize = 11.sp, color = p.warning)
            }
        }

        InfoCard("Darstellung") {
            Text(
                "Hell, dunkel oder wie das System. Die Auswahl gilt für die ganze App.",
                fontSize = 11.sp,
                color = p.muted,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ThemeChip("System", Icons.Default.SettingsBrightness, ThemeMode.SYSTEM, themeMode, onThemeMode, "theme_system")
                ThemeChip("Hell", Icons.Default.LightMode, ThemeMode.LIGHT, themeMode, onThemeMode, "theme_light")
                ThemeChip("Dunkel", Icons.Default.DarkMode, ThemeMode.DARK, themeMode, onThemeMode, "theme_dark")
            }
        }

        InfoCard("Anzeige & Ton") {
            Text("Lautstärke (Vorschau beim Ziehen): ${if (settings == null) "nicht gelesen" else "${volume.roundToInt()} %"}", fontSize = 12.sp, color = p.body)
            Slider(
                value = volume,
                onValueChange = { volume = it },
                onValueChangeFinished = {
                    onVolume(volume.roundToInt())
                    volume = settings?.volume?.toFloat() ?: volume
                },
                valueRange = 10f..80f,
                enabled = state.authorized && settings != null && !state.busy
            )
            Text("Helligkeit (Vorschau beim Ziehen): ${if (settings == null) "nicht gelesen" else "${brightness.roundToInt()} %"}", fontSize = 12.sp, color = p.body)
            Slider(
                value = brightness,
                onValueChange = { brightness = it },
                onValueChangeFinished = {
                    onBrightness(brightness.roundToInt())
                    brightness = settings?.brightness?.toFloat() ?: brightness
                },
                valueRange = 10f..100f,
                enabled = state.authorized && settings != null && !state.busy
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("Ruhemodus nach", fontSize = 12.sp, color = p.body)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0 to "Aus", 30 to "30 s", 120 to "2 min", 600 to "10 min").forEach { (sec, label) ->
                    FilterChip(
                        selected = settings?.sleepSeconds == sec,
                        onClick = { onSleep(sec) },
                        enabled = state.authorized && settings != null && !state.busy,
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
                onClick = onRefreshSettings,
                enabled = state.authorized && !state.busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Einstellungen vom Orb lesen") }
        }

        InfoCard("Profil (unverändert)") {
            InfoRow("Persönlichkeit", avatar.profile.personalityProfile)
            InfoRow("Sprachstil", avatar.profile.speechStyle)
            Text("Das visuelle Pixelwesen kann separat auf den Orb übertragen werden; dieses Profil bleibt lokal unverändert.", fontSize = 11.sp, color = p.muted)
        }

        OutlinedButton(
            onClick = onRefreshAll,
            enabled = state.authorized && !state.busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Alles neu vom Orb lesen")
        }
    }
}

@Composable
private fun ThemeChip(
    label: String,
    icon: ImageVector,
    mode: ThemeMode,
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    tag: String
) {
    val p = simPalette()
    FilterChip(
        selected = current == mode,
        onClick = { onSelect(mode) },
        modifier = Modifier.testTag(tag),
        label = { Text(label, fontSize = 11.sp) },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (current == mode) p.accent else p.muted
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = p.accentSoft,
            selectedLabelColor = p.ink,
            labelColor = p.body
        )
    )
}

@Composable
private fun FiguresPanel(
    state: SetupState,
    onRefreshCatalog: () -> Unit,
    onSelectFigure: (String) -> Unit
) {
    val p = simPalette()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InfoCard("Figuren auf dem Orb") {
            if (state.characters.isEmpty()) {
                Text("Vorschau der fünf eingebauten Figuren · Geräteauswahl erst nach Katalogabruf", fontSize = 11.sp, color = p.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    AcceptedAvatarFrames.ids.sorted().forEach { id ->
                        AcceptedAvatarPixelView(id, 44.dp, modifier = Modifier.weight(1f))
                    }
                }
            }
            when {
                state.catalogUnsupported -> Text(
                    "Diese Firmware meldet keinen Figurenkatalog. WLAN und Einstellungen bleiben nutzbar.",
                    fontSize = 12.sp,
                    color = p.warning
                )
                state.characters.isEmpty() -> Text(
                    "Noch keine Figuren gelesen.",
                    fontSize = 12.sp,
                    color = p.muted
                )
                else -> state.characters.forEach { figure ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = state.authorized && !state.busy) { onSelectFigure(figure.id) }
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (figure.id in AcceptedAvatarFrames.ids) {
                            AcceptedAvatarPixelView(figure.id, 52.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(figure.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
                            Text(figure.id, fontSize = 11.sp, color = p.muted)
                        }
                        if (figure.id == state.selectedCharacter) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "ausgewählt", tint = p.success)
                        } else {
                            Text("Auswählen", fontSize = 12.sp, color = p.accent, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(state.characterStatus, fontSize = 11.sp, color = p.body)
        }

        Button(
            onClick = onRefreshCatalog,
            enabled = state.authorized && !state.busy,
            colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Katalog vom Orb lesen")
        }
        Text(
            "Die Auswahl wird direkt auf dem Orb gespeichert und anschließend zurückgelesen.",
            fontSize = 11.sp,
            color = p.muted
        )
    }
}

@Composable
private fun NetworkPanel(
    state: SetupState,
    onRefreshNetworks: () -> Unit,
    onScanWifi: () -> Unit,
    onSaveWifi: (String, String) -> Long?,
    onForgetWifi: (String) -> Unit
) {
    val p = simPalette()
    var selectedSsid by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var pendingWifi by remember { mutableStateOf<Long?>(null) }
    var submittedWifi by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(state.writeOutcome?.request) {
        val result = state.writeOutcome
        if (result?.kind == "wifi" && result.request == pendingWifi) {
            if (result.confirmed && submittedWifi == Pair(selectedSsid, password)) password = ""
            pendingWifi = null
            submittedWifi = null
        }
    }

    LaunchedEffect(Unit) { onRefreshNetworks() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InfoCard("Verbindung") {
            InfoRow("Aktuell", WifiPresentation.current(state))
            InfoRow("Status", WifiPresentation.connection(state))
            if (!state.savedNetworksKnown || state.savedNetworks.isEmpty())
                Text(WifiPresentation.saved(state), fontSize = 12.sp, color = p.muted)
            if (state.savedNetworksKnown && state.savedNetworks.isNotEmpty()) {
                Text("Gespeichert", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = p.body)
                state.savedNetworks.forEach { name ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(name, fontSize = 12.sp, color = p.body)
                        TextButton(onClick = { onForgetWifi(name) }, enabled = state.authorized && !state.busy) {
                            Text("Entfernen", fontSize = 11.sp, color = p.danger)
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onScanWifi, enabled = state.authorized && !state.busy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Netzwerke suchen")
            }
            OutlinedButton(onClick = onRefreshNetworks, enabled = state.authorized && !state.busy, modifier = Modifier.weight(1f)) {
                Text("Status lesen")
            }
        }

        if (state.aps.isNotEmpty()) {
            InfoCard("Gefundene Netzwerke (max. 4)") {
                state.aps.forEach { ap ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedSsid == ap.ssid) p.accentSoft else Color.Transparent)
                            .clickable { selectedSsid = ap.ssid }
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (ap.secure) Icons.Default.Lock else Icons.Default.Wifi,
                            contentDescription = null,
                            tint = p.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(ap.ssid, fontSize = 13.sp, color = p.ink, modifier = Modifier.weight(1f))
                        Text("${ap.rssi} dBm", fontSize = 11.sp, color = p.muted)
                    }
                }
            }
        }

        if (selectedSsid != null) {
            InfoCard("Netzwerk speichern") {
                OutlinedTextField(
                    colors = simOutlinedTextFieldColors(),
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("WLAN-Passwort (8–63 Zeichen)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val ssid = selectedSsid ?: return@Button
                        val submitted = password
                        pendingWifi = onSaveWifi(ssid, submitted)
                        submittedWifi = Pair(ssid, submitted)
                    },
                    enabled = password.length in 8..63 && state.authorized && !state.busy,
                    colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Speichern") }
            }
        }

        Text(state.status, fontSize = 11.sp, color = p.body)
    }
}

@Composable
private fun AgentPanel(
    state: SetupState,
    onReadAgent: () -> Unit,
    onSaveAgent: (String, String) -> Long?,
    onRemoveAgent: () -> Unit
) {
    val p = simPalette()
    val profile = state.agentProfile
    var url by remember { mutableStateOf(profile?.url.orEmpty()) }
    LaunchedEffect(profile?.url) {
        if (url.isBlank() && profile?.url != null) url = profile.url
    }
    var token by remember { mutableStateOf("") }
    var pendingAgent by remember { mutableStateOf<Long?>(null) }
    var submittedAgent by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(state.writeOutcome?.request) {
        val result = state.writeOutcome
        if (result?.kind == "agent" && result.request == pendingAgent) {
            if (result.confirmed && submittedAgent == Pair(url, token)) token = ""
            pendingAgent = null
            submittedAgent = null
        }
    }

    LaunchedEffect(Unit) { onReadAgent() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InfoCard("Sprachdienst am Orb") {
            InfoRow("Quelle", when (profile?.source) {
                "custom" -> "eigene Bridge"
                "builtin" -> "Firmware-Standard"
                "invalid" -> "ungültig"
                "none" -> "nicht eingerichtet"
                else -> "nicht gelesen"
            })
            if (profile?.source == "custom") InfoRow("Endpoint", profile.url)
            Text(state.agentStatus, fontSize = 11.sp, color = p.body, lineHeight = 16.sp)
        }

        if (!state.agentUnsupported) {
            InfoCard("HTTPS-Bridge") {
                Text(
                    "Nur eine Bridge für die Route /v1/turn-stream (16-kHz-PCM). Endpoint und Zugangsdaten werden gemeinsam auf dem Orb ersetzt.",
                    fontSize = 11.sp,
                    color = p.muted,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    colors = simOutlinedTextFieldColors(),
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Endpoint") },
                    placeholder = { Text("https://beispiel.de/v1/turn-stream") },
                    singleLine = true,
                    isError = url.isNotBlank() && !AgentProtocol.validUrl(url),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    colors = simOutlinedTextFieldColors(),
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Bearer-Zugangsdaten") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = token.isNotBlank() && !AgentProtocol.validToken(token),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        val submitted = Pair(url, token)
                        pendingAgent = onSaveAgent(submitted.first, submitted.second)
                        submittedAgent = submitted
                    },
                    enabled = AgentProtocol.validUrl(url) && AgentProtocol.validToken(token) &&
                        state.authorized && !state.busy && !state.agentUnsupported,
                    colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Auf dem Orb speichern") }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = onRemoveAgent,
                    enabled = state.authorized && !state.busy && profile != null &&
                        profile.source != "builtin" && profile.source != "invalid",
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Anbindung entfernen") }
            }
        }

        OutlinedButton(onClick = onReadAgent, enabled = state.authorized && !state.busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Status lesen")
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    val p = simPalette()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = p.card),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.ink)
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val p = simPalette()
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = p.muted)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = p.ink)
    }
}
