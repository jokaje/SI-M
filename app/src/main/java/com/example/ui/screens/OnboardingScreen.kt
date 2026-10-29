package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CreatureDna
import com.example.orb.AgentProtocol
import com.example.orb.SetupState
import com.example.orb.WifiPresentation
import com.example.ui.theme.simOutlinedTextFieldColors
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.IndigoAccent
import com.example.ui.components.SimpleAvatarChooser
import com.example.model.AvatarFavorite
import com.example.ui.theme.MutedText

@Composable
fun OnboardingScreen(
    state: SetupState,
    avatar: CreatureDna,
    draft: String?, avatarName: String, favorites: List<AvatarFavorite>,
    step: Int,
    selectedChoice: String? = null, onChoose: (String) -> Unit = {},
    avatarBusy: Boolean = false, avatarError: String? = null,
    onRollAvatar: () -> Unit,
    onNameAvatar: (String) -> Unit, onTrait: (Int, Int) -> Unit, onColor: (Int, String) -> Unit,
    onSaveFavorite: () -> Unit, onFavorite: (String) -> Unit, onDeleteFavorite: (String) -> Unit,
    onInstall: () -> Unit, onDeleteDevice: () -> Unit,
    onConfirmAvatar: () -> Unit, onSelectFigure: (String) -> Unit,
    onSetStep: (Int) -> Unit,
    onRefreshNetworks: () -> Unit,
    onScanWifi: () -> Unit,
    onSaveWifi: (String, String) -> Long?,
    onForgetWifi: (String) -> Unit,
    onReadAgent: () -> Unit,
    onSaveAgent: (String, String) -> Long?,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        if (avatarBusy) return@BackHandler
        onSetStep(com.example.viewmodel.onboardingBackStep(step))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
            Text(
                text = "Einrichtung",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            Text(
                text = "Schritt $step von 4",
                fontSize = 12.sp,
                color = MutedText
            )
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { step / 4f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = CyanPrimary,
                trackColor = Color(0xFFE2E8F0)
            )
        }

        when (step) {
            1 -> StepAvatar(
                state = state, draft = draft, avatarName = avatarName, favorites = favorites,
                onRollAvatar = onRollAvatar, onNameAvatar = onNameAvatar, onTrait = onTrait,
                onColor = onColor, onSaveFavorite = onSaveFavorite, onFavorite = onFavorite,
                onDeleteFavorite = onDeleteFavorite, onInstall = onInstall, onDeleteDevice = onDeleteDevice,
                onConfirmAvatar = onConfirmAvatar, onSelectFigure = onSelectFigure,
                selectedChoice = selectedChoice, onChoose = onChoose, avatarBusy = avatarBusy, avatarError = avatarError
            )
            2 -> StepWifi(
                state = state,
                onRefreshNetworks = onRefreshNetworks,
                onScanWifi = onScanWifi,
                onSaveWifi = onSaveWifi,
                onForgetWifi = onForgetWifi,
                onContinue = { onSetStep(3) }
            )
            3 -> StepAgent(
                state = state,
                onReadAgent = onReadAgent,
                onSaveAgent = onSaveAgent,
                onContinue = { onSetStep(4) }
            )
            else -> StepDone(
                state = state,
                avatar = avatar,
                onFinish = onFinish
            )
        }
    }
}

@Composable
private fun StepAvatar(
    state: SetupState, draft: String?, avatarName: String, favorites: List<AvatarFavorite>,
    onRollAvatar: () -> Unit, onNameAvatar: (String) -> Unit, onTrait: (Int, Int) -> Unit,
    onColor: (Int, String) -> Unit, onSaveFavorite: () -> Unit, onFavorite: (String) -> Unit,
    onDeleteFavorite: (String) -> Unit, onInstall: () -> Unit, onDeleteDevice: () -> Unit,
    onConfirmAvatar: () -> Unit, onSelectFigure: (String) -> Unit,
    selectedChoice: String?, onChoose: (String) -> Unit, avatarBusy: Boolean, avatarError: String?
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        SimpleAvatarChooser(state, draft, avatarName, onRollAvatar, onNameAvatar,
            onSelectBuiltIn = onSelectFigure, onConfirmRandom = onInstall,
            onContinue = onConfirmAvatar, selectedChoice = selectedChoice, onChoose = onChoose,
            busy = avatarBusy, error = avatarError)
    }
}

@Composable
private fun StepWifi(
    state: SetupState,
    onRefreshNetworks: () -> Unit,
    onScanWifi: () -> Unit,
    onSaveWifi: (String, String) -> Long?,
    onForgetWifi: (String) -> Unit,
    onContinue: () -> Unit
) {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Wifi, contentDescription = null, tint = CyanPrimary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("WLAN auf dem Orb", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Der Orb bekommt sein Netzwerk direkt vom Gerät. Zugangsdaten werden nur über Bluetooth übertragen und nicht in der App gespeichert.",
            fontSize = 12.sp,
            color = MutedText,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Gerätestatus", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF0F172A))
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = WifiPresentation.onboarding(state),
                    fontSize = 12.sp,
                    color = MutedText
                )
                if (!state.savedNetworksKnown || state.savedNetworks.isEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(WifiPresentation.saved(state), fontSize = 12.sp, color = MutedText)
                }
                if (state.savedNetworksKnown && state.savedNetworks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    state.savedNetworks.forEach { name ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(name, fontSize = 12.sp, color = Color(0xFF334155))
                            TextButton(onClick = { onForgetWifi(name) }) {
                                Text("Entfernen", fontSize = 11.sp, color = Color(0xFFB91C1C))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onScanWifi, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Suchen")
            }
            OutlinedButton(onClick = onRefreshNetworks, modifier = Modifier.weight(1f)) {
                Text("Status lesen")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (state.scanning) {
            Text("Der Orb sucht Netzwerke …", fontSize = 12.sp, color = CyanPrimary)
        }

        state.aps.forEach { ap ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { selectedSsid = ap.ssid },
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedSsid == ap.ssid) Color(0xFFE0F2FE) else Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (ap.secure) Icons.Default.Lock else Icons.Default.Wifi,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(ap.ssid, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text("${ap.rssi} dBm", fontSize = 11.sp, color = MutedText)
                }
            }
        }

        if (selectedSsid != null) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                colors = simOutlinedTextFieldColors(),
                value = password,
                onValueChange = { password = it },
                label = { Text("WLAN-Passwort (8–63 Zeichen)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wifi_password_input"),
                shape = RoundedCornerShape(12.dp)
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
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_wifi_button")
            ) {
                Text("WLAN auf dem Orb speichern", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(state.status, fontSize = 12.sp, color = Color(0xFF475569), lineHeight = 17.sp)

        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = onContinue,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(WifiPresentation.continueLabel(state), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StepAgent(
    state: SetupState,
    onReadAgent: () -> Unit,
    onSaveAgent: (String, String) -> Long?,
    onContinue: () -> Unit
) {
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

    val urlValid = AgentProtocol.validUrl(url)
    val tokenValid = AgentProtocol.validToken(token)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Sprachdienst", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Der Orb spricht einen HTTPS-Dienst über die Route /v1/turn-stream an (16-kHz-PCM-Audio). " +
                "Es muss eine eigene Bridge sein, keine beliebige Chat-Adresse.",
            fontSize = 12.sp,
            color = MutedText,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Gerätestatus", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF0F172A))
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when {
                        state.agentUnsupported -> "Diese Firmware unterstützt keine Sprachdienst-Konfiguration."
                        profile == null -> "Noch nicht gelesen."
                        profile.source == "custom" -> "Eigene Bridge auf dem Orb gespeichert."
                        profile.source == "builtin" -> "Alter Firmware-Standard aktiv. Ein Firmware-Update ist nötig, bevor die Anbindung entfernt werden kann."
                        profile.source == "invalid" -> "Gespeicherte Konfiguration ist ungültig."
                        else -> "Kein Sprachdienst eingerichtet."
                    },
                    fontSize = 12.sp,
                    color = MutedText
                )
                if (profile?.source == "custom") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(profile.url, fontSize = 11.sp, color = Color(0xFF334155))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(state.agentStatus, fontSize = 12.sp, color = Color(0xFF475569), lineHeight = 17.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onReadAgent, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Status vom Orb lesen")
        }

        if (!state.agentUnsupported) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                colors = simOutlinedTextFieldColors(),
                value = url,
                onValueChange = { url = it },
                label = { Text("HTTPS-Bridge-Endpoint") },
                placeholder = { Text("https://beispiel.de/v1/turn-stream") },
                singleLine = true,
                isError = url.isNotBlank() && !urlValid,
                supportingText = {
                    if (url.isNotBlank() && !urlValid) Text("Muss eine HTTPS-Adresse sein und auf /v1/turn-stream enden.")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("agent_address_input"),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                colors = simOutlinedTextFieldColors(),
                value = token,
                onValueChange = { token = it },
                label = { Text("Bearer-Zugangsdaten") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                isError = token.isNotBlank() && !tokenValid,
                supportingText = {
                    if (token.isNotBlank() && !tokenValid) Text("32–128 Zeichen, Buchstaben, Ziffern und . _ ~ + / - =")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("agent_token_input"),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    val submitted = Pair(url, token)
                    pendingAgent = onSaveAgent(submitted.first, submitted.second)
                    submittedAgent = submitted
                },
                enabled = urlValid && tokenValid && state.authorized && !state.busy,
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_agent_button")
            ) {
                Text("Auf dem Orb speichern", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Speichern ist noch kein Sprachtest. Der Orb verbindet sich erst beim ersten Gespräch.",
                fontSize = 11.sp,
                color = MutedText
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = onContinue,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(if (profile?.source == "custom") "Weiter" else "Ohne Sprachdienst fortfahren", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StepDone(
    state: SetupState,
    avatar: CreatureDna,
    onFinish: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(Color(0xFFDCFCE7)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(46.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Einrichtung abgeschlossen", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Diese Angaben hat der Orb bestätigt:",
            fontSize = 12.sp,
            color = MutedText
        )
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryRow("Lokales Profil (Stimme/Person unverändert)", avatar.name)
                SummaryRow(
                    "Figur auf dem Orb",
                    state.companionCharacterId ?: state.selectedCharacter ?: "noch nicht abgerufen"
                )
                SummaryRow(
                    "WLAN",
                    WifiPresentation.summary(state)
                )
                SummaryRow(
                    "Sprachdienst",
                    when (state.agentProfile?.source) {
                        "custom" -> "eigene Bridge"
                        "builtin" -> "Firmware-Standard"
                        "invalid" -> "ungültig"
                        else -> "nicht eingerichtet"
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onFinish,
            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Zum Dashboard", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = MutedText)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
    }
}
