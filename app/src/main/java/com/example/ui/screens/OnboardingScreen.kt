package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AgentConfig
import com.example.model.AgentType
import com.example.model.CreatureDna
import com.example.model.CreatureEmotion
import com.example.model.InternetConfig
import com.example.model.InternetConnectionType
import com.example.ui.components.MuseAvatarView
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.OrbWarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    currentStep: Int, // 1: Avatar, 2: Internet, 3: Agent, 4: Done
    draftCharacter: CreatureDna,
    diceRollsLeft: Int,
    internetConfig: InternetConfig,
    agentConfig: AgentConfig,
    onRollDice: () -> Unit,
    onConfirmAvatar: () -> Unit,
    onSelectInternetMode: (InternetConnectionType, ssid: String, pass: String) -> Unit,
    onConfirmInternet: () -> Unit,
    onSaveAgentConfig: (type: AgentType, name: String, address: String, token: String, provider: String, apiKey: String) -> Unit,
    onFinishToDashboard: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "SI-M Orb Einrichtung",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = when (currentStep) {
                                1 -> "Schritt 1 von 3: Charakter erzeugen"
                                2 -> "Schritt 2 von 3: Internet-Verbindung"
                                3 -> "Schritt 3 von 3: Intelligenz & Agent"
                                else -> "Einrichtung abgeschlossen"
                            },
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                },
                navigationIcon = {
                    if (currentStep in 2..3) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("onboarding_back_button")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF0F172A)
                )
            )
        },
        containerColor = Color(0xFFF8FAFC),
        modifier = modifier.testTag("onboarding_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Step Progress Bar
            LinearProgressIndicator(
                progress = {
                    when (currentStep) {
                        1 -> 0.33f
                        2 -> 0.66f
                        3 -> 1.0f
                        else -> 1.0f
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = CyanPrimary,
                trackColor = Color(0xFFE2E8F0)
            )

            AnimatedContent(
                targetState = currentStep,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "onboardingStepAnim",
                modifier = Modifier.weight(1f)
            ) { step ->
                when (step) {
                    1 -> StepAvatarCreation(
                        dna = draftCharacter,
                        rollsLeft = diceRollsLeft,
                        onRollDice = onRollDice,
                        onConfirm = onConfirmAvatar
                    )
                    2 -> StepInternetSetup(
                        config = internetConfig,
                        onSelectMode = onSelectInternetMode,
                        onConfirm = onConfirmInternet
                    )
                    3 -> StepAgentSetup(
                        initialConfig = agentConfig,
                        onSave = onSaveAgentConfig
                    )
                    else -> StepCompleted(
                        dna = draftCharacter,
                        onFinish = onFinishToDashboard
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Step 1: Avatar Creation with 3x Random Roll Limit
// -------------------------------------------------------------
@Composable
private fun StepAvatarCreation(
    dna: CreatureDna,
    rollsLeft: Int,
    onRollDice: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Erschaffe deinen SI-M Charakter",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Dein Orb erhält ein lebendiges 64×64 Pixel-Wesen. Du kannst den Zufalls-Generator bis zu 3x würfeln.",
            fontSize = 13.sp,
            color = Color(0xFF64748B),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Muse Avatar Live Component
        MuseAvatarView(
            dna = dna,
            size = 230.dp,
            showGrid = true,
            emotion = CreatureEmotion.IDLE
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Character Name & Seed Badge
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.shadow(2.dp, RoundedCornerShape(12.dp))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dna.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "• Seed 0x${dna.seed.toString(16).uppercase()}",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Roll Counter Badge
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (rollsLeft > 0) Color(0xFFEFF6FF) else Color(0xFFFEF2F2)
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (rollsLeft > 0) "🎲 Noch $rollsLeft von 3 Versuchen übrig" else "⛔ Alle 3 Würfe aufgebraucht",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (rollsLeft > 0) CyanPrimary else Color(0xFFDC2626)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Randomize Button
        Button(
            onClick = onRollDice,
            enabled = rollsLeft > 0,
            colors = ButtonDefaults.buttonColors(containerColor = OrbWarningAmber),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("dice_random_button")
        ) {
            Icon(Icons.Default.Casino, contentDescription = null, tint = Color.Black)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Zufalls-Wesen würfeln", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Confirm Button
        Button(
            onClick = onConfirm,
            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("confirm_avatar_button")
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Diesen Avatar übernehmen & Weiter", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

// -------------------------------------------------------------
// Step 2: Internet Connection Setup (Hotspot vs Manual Wi-Fi)
// -------------------------------------------------------------
@Composable
private fun StepInternetSetup(
    config: InternetConfig,
    onSelectMode: (InternetConnectionType, String, String) -> Unit,
    onConfirm: () -> Unit
) {
    var selectedType by remember { mutableStateOf(config.connectionType) }
    var wifiSsid by remember { mutableStateOf("Heimnetz-5G") }
    var wifiPassword by remember { mutableStateOf("supersecret") }
    var hotspotSsid by remember { mutableStateOf("SI-M-Phone-Hotspot") }
    var hotspotPassword by remember { mutableStateOf("orbaccess88") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "Orb mit dem Internet verbinden",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Bevor wir den Agenten oder Namen einrichten, benötigt der SI-M Orb Zugang zum Internet.",
            fontSize = 13.sp,
            color = Color(0xFF64748B)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Option 1: 1-Klick Hotspot
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = if (selectedType == InternetConnectionType.HOTSPOT) 2.dp else 1.dp,
                    color = if (selectedType == InternetConnectionType.HOTSPOT) CyanPrimary else Color(0xFFE2E8F0),
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable {
                    selectedType = InternetConnectionType.HOTSPOT
                    onSelectMode(InternetConnectionType.HOTSPOT, hotspotSsid, hotspotPassword)
                }
                .testTag("hotspot_option_button"),
            colors = CardDefaults.cardColors(
                containerColor = if (selectedType == InternetConnectionType.HOTSPOT) Color(0xFFF0F9FF) else Color.White
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedType == InternetConnectionType.HOTSPOT,
                        onClick = {
                            selectedType = InternetConnectionType.HOTSPOT
                            onSelectMode(InternetConnectionType.HOTSPOT, hotspotSsid, hotspotPassword)
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CyanPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.WifiTethering, contentDescription = null, tint = CyanPrimary)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "1-Klick via Handy-Hotspot (Empfohlen)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Automatische Übertragung der Hotspot-Daten an den Orb",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                if (selectedType == InternetConnectionType.HOTSPOT) {
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = hotspotSsid,
                        onValueChange = {
                            hotspotSsid = it
                            onSelectMode(InternetConnectionType.HOTSPOT, hotspotSsid, hotspotPassword)
                        },
                        label = { Text("Hotspot-Name (SSID)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = hotspotPassword,
                        onValueChange = {
                            hotspotPassword = it
                            onSelectMode(InternetConnectionType.HOTSPOT, hotspotSsid, hotspotPassword)
                        },
                        label = { Text("Hotspot-Passwort") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Option 2: Manuelles WLAN
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = if (selectedType == InternetConnectionType.MANUAL_WIFI) 2.dp else 1.dp,
                    color = if (selectedType == InternetConnectionType.MANUAL_WIFI) CyanPrimary else Color(0xFFE2E8F0),
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable {
                    selectedType = InternetConnectionType.MANUAL_WIFI
                    onSelectMode(InternetConnectionType.MANUAL_WIFI, wifiSsid, wifiPassword)
                }
                .testTag("manual_wifi_option_button"),
            colors = CardDefaults.cardColors(
                containerColor = if (selectedType == InternetConnectionType.MANUAL_WIFI) Color(0xFFF0F9FF) else Color.White
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedType == InternetConnectionType.MANUAL_WIFI,
                        onClick = {
                            selectedType = InternetConnectionType.MANUAL_WIFI
                            onSelectMode(InternetConnectionType.MANUAL_WIFI, wifiSsid, wifiPassword)
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF6366F1).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = IndigoAccent)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Manuelle Eingabe eines WLAN",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Mit bestehendem Heimnetzwerk verbinden",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                if (selectedType == InternetConnectionType.MANUAL_WIFI) {
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = wifiSsid,
                        onValueChange = {
                            wifiSsid = it
                            onSelectMode(InternetConnectionType.MANUAL_WIFI, wifiSsid, wifiPassword)
                        },
                        label = { Text("WLAN-Name (SSID)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = wifiPassword,
                        onValueChange = {
                            wifiPassword = it
                            onSelectMode(InternetConnectionType.MANUAL_WIFI, wifiSsid, wifiPassword)
                        },
                        label = { Text("WLAN-Passwort") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onConfirm,
            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("confirm_internet_button")
        ) {
            Text("Verbindung übertragen & Weiter", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

// -------------------------------------------------------------
// Step 3: Agent Connection vs Self-Configured SI-M
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepAgentSetup(
    initialConfig: AgentConfig,
    onSave: (AgentType, String, String, String, String, String) -> Unit
) {
    var agentType by remember { mutableStateOf(initialConfig.agentType) }

    // Existing agent inputs
    var serverAddress by remember { mutableStateOf(initialConfig.serverAddress) }
    var bearerToken by remember { mutableStateOf(initialConfig.bearerToken) }

    // Custom configuration inputs
    var customName by remember { mutableStateOf(initialConfig.agentName) }
    var selectedLlm by remember { mutableStateOf("Gemini 2.5 Flash (Google AI)") }
    var apiKey by remember { mutableStateOf(initialConfig.apiKey) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val llmOptions = listOf(
        "Gemini 2.5 Flash (Google AI)",
        "OpenAI (GPT-4o / GPT-4o-mini)",
        "Anthropic Claude 3.5 Sonnet",
        "Groq (Llama 3.3 70B)",
        "Ollama (Lokales Netzwerk)"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "Intelligenz des Orbs konfigurieren",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Wähle, wie dein SI-M Orb denkt und antwortet.",
            fontSize = 13.sp,
            color = Color(0xFF64748B)
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Type Choice Cards
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { agentType = AgentType.SELF_CONFIGURED }
                    .border(
                        2.dp,
                        if (agentType == AgentType.SELF_CONFIGURED) CyanPrimary else Color(0xFFE2E8F0),
                        RoundedCornerShape(14.dp)
                    )
                    .testTag("agent_type_custom"),
                colors = CardDefaults.cardColors(
                    containerColor = if (agentType == AgentType.SELF_CONFIGURED) Color(0xFFF0F9FF) else Color.White
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("SI-M Konfigurieren", fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Text("Eigener Name & LLM-API", fontSize = 10.sp, color = Color(0xFF64748B), textAlign = TextAlign.Center)
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { agentType = AgentType.EXISTING_AGENT }
                    .border(
                        2.dp,
                        if (agentType == AgentType.EXISTING_AGENT) IndigoAccent else Color(0xFFE2E8F0),
                        RoundedCornerShape(14.dp)
                    )
                    .testTag("agent_type_existing"),
                colors = CardDefaults.cardColors(
                    containerColor = if (agentType == AgentType.EXISTING_AGENT) Color(0xFFEEF2FF) else Color.White
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = IndigoAccent, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Bestehender Agent", fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Text("Hermes / OpenClaw", fontSize = 10.sp, color = Color(0xFF64748B), textAlign = TextAlign.Center)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (agentType == AgentType.SELF_CONFIGURED) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.shadow(2.dp, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "SI-M Persönlichkeit & Name",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ℹ️ Persönlichkeit, Sprachstil und Reaktionen werden im Hintergrund automatisch und einzigartig für deinen Orb generiert.",
                        fontSize = 11.sp,
                        color = Color(0xFF0284C7),
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Name deines SI-M (z.B. Nova, Kira, Atlas)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_name_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("LLM-Anbieter auswählen", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                    Spacer(modifier = Modifier.height(4.dp))

                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedLlm,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            shape = RoundedCornerShape(10.dp)
                        )

                        ExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            llmOptions.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt) },
                                    onClick = {
                                        selectedLlm = opt
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API-Schlüssel (Optional / Cloud-Secret)") },
                        placeholder = { Text("sk-...") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_key_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        } else {
            // Existing agent
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.shadow(2.dp, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Agenten-Endpunkt & Authentifizierung",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Verbinde einen laufenden Agenten (z.B. Hermes, OpenClaw oder benutzerdefinierten Server).",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = serverAddress,
                        onValueChange = { serverAddress = it },
                        label = { Text("Agent Server-Adresse (URL)") },
                        placeholder = { Text("https://agent.local:8080") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("agent_address_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = bearerToken,
                        onValueChange = { bearerToken = it },
                        label = { Text("Bearer Token / Autorisierungsschlüssel") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("agent_token_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = {
                onSave(agentType, customName, serverAddress, bearerToken, selectedLlm, apiKey)
            },
            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("save_agent_button")
        ) {
            Text("Konfiguration speichern & Abschließen", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

// -------------------------------------------------------------
// Step 4: Completion Celebration Screen
// -------------------------------------------------------------
@Composable
private fun StepCompleted(
    dna: CreatureDna,
    onFinish: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color(0xFF22C55E).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF16A34A),
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "SI-M ist einsatzbereit!",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Dein AI Orb ist vollständig gekoppelt, mit dem Internet verbunden und mit '${dna.name}' synchronisiert.",
            fontSize = 13.sp,
            color = Color(0xFF64748B),
            textAlign = TextAlign.Center,
            lineHeight = 19.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        MuseAvatarView(
            dna = dna,
            size = 180.dp,
            emotion = CreatureEmotion.LOVE
        )

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = onFinish,
            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("finish_onboarding_button")
        ) {
            Text("Zum Dashboard", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}
