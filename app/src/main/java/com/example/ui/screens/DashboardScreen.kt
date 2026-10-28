package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CreatureDna
import com.example.model.CreatureEmotion
import com.example.model.InternetConfig
import com.example.model.OrbDevice
import com.example.model.OrbSettings
import com.example.model.ScreenTimeout
import com.example.model.SoundProfile
import com.example.ui.components.MuseAvatarView
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.OrbGazeEmerald
import com.example.ui.theme.OrbHeartPink
import com.example.ui.theme.OrbWarningAmber
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    pairedOrb: OrbDevice?,
    characters: List<CreatureDna>,
    activeCharacterIndex: Int,
    orbSettings: OrbSettings,
    internetConfig: InternetConfig,
    activeTab: Int,
    onStartPairing: () -> Unit,
    onSelectCharacter: (Int) -> Unit,
    onSetEmotion: (CreatureEmotion) -> Unit,
    onOpenCameraEye: () -> Unit,
    onUpdateVolume: (Float) -> Unit,
    onUpdateBrightness: (Float) -> Unit,
    onUpdateSoundProfile: (SoundProfile) -> Unit,
    onUpdateScreenTimeout: (ScreenTimeout) -> Unit,
    onToggleAdaptiveBrightness: () -> Unit,
    onToggleWifi: () -> Unit,
    onToggleBluetooth: () -> Unit,
    onTogglePixelGrid: () -> Unit,
    onDisconnectOrb: () -> Unit,
    onSelectTab: (Int) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(listOf(Color(0xFF38BDF8), CyanPrimary))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("SI-M", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                            Text(
                                text = if (pairedOrb != null) "Orb Verbunden • ${pairedOrb.name}" else "Kein Orb gekoppelt",
                                fontSize = 11.sp,
                                color = if (pairedOrb != null) OrbGazeEmerald else Color(0xFF64748B)
                            )
                        }
                    }
                },
                actions = {
                    if (pairedOrb != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.BatteryFull,
                                    contentDescription = "Akku",
                                    tint = OrbGazeEmerald,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "${pairedOrb.batteryPercent}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF334155)
                                )
                            }
                        }
                    }

                    IconButton(onClick = onLogout, modifier = Modifier.testTag("dashboard_logout_button")) {
                        Icon(
                            Icons.Default.PowerSettingsNew,
                            contentDescription = "Abmelden",
                            tint = Color(0xFF64748B)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF0F172A)
                )
            )
        },
        bottomBar = {
            if (pairedOrb != null) {
                NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = activeTab == 0,
                        onClick = { onSelectTab(0) },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                        label = { Text("Dashboard & System") },
                        modifier = Modifier.testTag("nav_tab_dashboard")
                    )
                    NavigationBarItem(
                        selected = activeTab == 1,
                        onClick = { onSelectTab(1) },
                        icon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, contentDescription = "Gesperrt", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(Icons.Default.AutoAwesome, contentDescription = "Labor")
                            }
                        },
                        label = { Text("Neuer Charakter") },
                        modifier = Modifier.testTag("nav_tab_lab")
                    )
                }
            }
        },
        containerColor = Color(0xFFF8FAFC),
        modifier = modifier.testTag("dashboard_screen")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (pairedOrb == null) {
                // ---------------------------------------------------------
                // STATE 1: NO ORB CONNECTED YET (Prominent Call to Connect)
                // ---------------------------------------------------------
                NoOrbConnectedView(onStartPairing = onStartPairing)
            } else {
                // ---------------------------------------------------------
                // STATE 2: ORB CONNECTED - Active Tabs
                // ---------------------------------------------------------
                if (activeTab == 0) {
                    DashboardActiveView(
                        pairedOrb = pairedOrb,
                        characters = characters,
                        activeCharacterIndex = activeCharacterIndex,
                        orbSettings = orbSettings,
                        internetConfig = internetConfig,
                        onSelectCharacter = onSelectCharacter,
                        onSetEmotion = onSetEmotion,
                        onOpenCameraEye = onOpenCameraEye,
                        onUpdateVolume = onUpdateVolume,
                        onUpdateBrightness = onUpdateBrightness,
                        onUpdateSoundProfile = onUpdateSoundProfile,
                        onUpdateScreenTimeout = onUpdateScreenTimeout,
                        onToggleAdaptiveBrightness = onToggleAdaptiveBrightness,
                        onToggleWifi = onToggleWifi,
                        onToggleBluetooth = onToggleBluetooth,
                        onTogglePixelGrid = onTogglePixelGrid,
                        onDisconnectOrb = onDisconnectOrb
                    )
                } else {
                    // TAB 2: Locked Character Lab
                    LockedCharacterLabView()
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Empty State: No Orb Connected Yet
// -------------------------------------------------------------
@Composable
private fun NoOrbConnectedView(onStartPairing: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .shadow(24.dp, CircleShape, spotColor = CyanPrimary.copy(alpha = 0.35f))
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFFE0F2FE), Color(0xFFBAE6FD), Color(0xFFF0F9FF))
                    )
                )
                .border(2.dp, CyanPrimary.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = null,
                tint = CyanPrimary,
                modifier = Modifier.size(54.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Kein SI-M Orb gekoppelt",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Kopple jetzt deinen SI-M AI Orb, um das 64×64 Display, System-Einstellungen und die Handy-Kamera als Auge freizuschalten.",
            fontSize = 13.sp,
            color = Color(0xFF64748B),
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onStartPairing,
            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(52.dp)
                .testTag("connect_orb_primary_button")
        ) {
            Icon(Icons.Default.Bluetooth, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Orb verbinden", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

// -------------------------------------------------------------
// Active Dashboard View
// -------------------------------------------------------------
@Composable
private fun DashboardActiveView(
    pairedOrb: OrbDevice,
    characters: List<CreatureDna>,
    activeCharacterIndex: Int,
    orbSettings: OrbSettings,
    internetConfig: InternetConfig,
    onSelectCharacter: (Int) -> Unit,
    onSetEmotion: (CreatureEmotion) -> Unit,
    onOpenCameraEye: () -> Unit,
    onUpdateVolume: (Float) -> Unit,
    onUpdateBrightness: (Float) -> Unit,
    onUpdateSoundProfile: (SoundProfile) -> Unit,
    onUpdateScreenTimeout: (ScreenTimeout) -> Unit,
    onToggleAdaptiveBrightness: () -> Unit,
    onToggleWifi: () -> Unit,
    onToggleBluetooth: () -> Unit,
    onTogglePixelGrid: () -> Unit,
    onDisconnectOrb: () -> Unit
) {
    val activeChar = characters.getOrNull(activeCharacterIndex) ?: CreatureDna.defaultCreature()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Character Switching Carousel (if multiple characters)
        if (characters.size > 1) {
            Text(
                text = "Vorhandene Charaktere wechseln",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF64748B),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(characters) { idx, char ->
                    val isSelected = idx == activeCharacterIndex
                    Card(
                        modifier = Modifier
                            .clickable { onSelectCharacter(idx) }
                            .border(
                                2.dp,
                                if (isSelected) CyanPrimary else Color(0xFFE2E8F0),
                                RoundedCornerShape(12.dp)
                            )
                            .testTag("switch_character_$idx"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFFE0F2FE) else Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = char.name,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) CyanPrimary else Color(0xFF334155)
                            )
                        }
                    }
                }
            }
        }

        // Top Hero: Live Creature Avatar
        MuseAvatarView(
            dna = activeChar,
            size = 210.dp,
            showGrid = orbSettings.pixelGridHardware,
            emotion = activeChar.emotion
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Character Name & Interactive Pet Hint
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = activeChar.name,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFCE7F3)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.TouchApp, contentDescription = null, tint = OrbHeartPink, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Tippen zum Streicheln", fontSize = 10.sp, color = OrbHeartPink, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Quick Emotion Switcher Carousel
        Text(
            text = "Emotion am Orb testen",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF94A3B8)
        )
        Spacer(modifier = Modifier.height(4.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(CreatureEmotion.values().size) { i ->
                val em = CreatureEmotion.values()[i]
                val selected = em == activeChar.emotion
                FilterChip(
                    selected = selected,
                    onClick = { onSetEmotion(em) },
                    label = { Text("${em.icon} ${em.label}", fontSize = 11.sp) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("emotion_chip_${em.name}")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // -------------------------------------------------------------
        // FEATURE: HANDY-KAMERA ALS "AUGE" BENUTZEN
        // -------------------------------------------------------------
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(18.dp), spotColor = CyanPrimary.copy(alpha = 0.25f))
                .clickable { onOpenCameraEye() }
                .testTag("camera_eye_banner_card"),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF38BDF8), CyanPrimary))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Kamera-Auge",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Handy-Kamera als 'Auge'",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E))
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Orb sieht live durch das Smartphone mit Vision HUD",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                Button(
                    onClick = onOpenCameraEye,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("launch_camera_eye_button")
                ) {
                    Text("Starten", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // -------------------------------------------------------------
        // ORB SYSTEM EINSTELLUNGEN
        // -------------------------------------------------------------
        Text(
            text = "Orb System-Einstellungen",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color(0xFF0F172A),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        )

        // Volume Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = CyanPrimary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Ton & Lautstärke", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    Text(
                        "${(orbSettings.volume * 100).roundToInt()}%",
                        fontWeight = FontWeight.Bold,
                        color = CyanPrimary,
                        fontSize = 14.sp
                    )
                }

                Slider(
                    value = orbSettings.volume,
                    onValueChange = onUpdateVolume,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("volume_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = CyanPrimary,
                        activeTrackColor = CyanPrimary
                    )
                )

                // Sound Profiles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SoundProfile.values().forEach { profile ->
                        val isSel = profile == orbSettings.soundProfile
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) Color(0xFFE0F2FE) else Color(0xFFF1F5F9))
                                .clickable { onUpdateSoundProfile(profile) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = profile.label.split(" ").first(),
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) CyanPrimary else Color(0xFF475569)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Brightness Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Brightness6, contentDescription = null, tint = OrbWarningAmber)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Display-Helligkeit", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    Text(
                        "${(orbSettings.brightness * 100).roundToInt()}%",
                        fontWeight = FontWeight.Bold,
                        color = OrbWarningAmber,
                        fontSize = 14.sp
                    )
                }

                Slider(
                    value = orbSettings.brightness,
                    onValueChange = onUpdateBrightness,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("brightness_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = OrbWarningAmber,
                        activeTrackColor = OrbWarningAmber
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Automatische Helligkeit", fontSize = 12.sp, color = Color(0xFF475569))
                    Switch(
                        checked = orbSettings.adaptiveBrightness,
                        onCheckedChange = { onToggleAdaptiveBrightness() },
                        colors = SwitchDefaults.colors(checkedThumbColor = OrbWarningAmber)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Screen Timeout Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = IndigoAccent)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Bildschirm-Timeout", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ScreenTimeout.values().forEach { timeout ->
                        val isSel = timeout == orbSettings.screenTimeout
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) Color(0xFFEEF2FF) else Color(0xFFF1F5F9))
                                .clickable { onUpdateScreenTimeout(timeout) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = timeout.label.split(" ").first(),
                                fontSize = 10.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) IndigoAccent else Color(0xFF475569),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Connectivity Toggles Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = CyanPrimary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("WLAN & Internet", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(internetConfig.ssid, fontSize = 11.sp, color = Color(0xFF64748B))
                        }
                    }
                    Switch(
                        checked = orbSettings.wifiEnabled,
                        onCheckedChange = { onToggleWifi() },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyanPrimary)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null, tint = IndigoAccent)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Bluetooth Link", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Verbunden mit ${pairedOrb.name}", fontSize = 11.sp, color = Color(0xFF64748B))
                        }
                    }
                    Switch(
                        checked = orbSettings.bluetoothEnabled,
                        onCheckedChange = { onToggleBluetooth() },
                        colors = SwitchDefaults.colors(checkedThumbColor = IndigoAccent)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GridOn, contentDescription = null, tint = Color(0xFF0F172A))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("64×64 Pixel Raster", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Hardware-Matrix Optik", fontSize = 11.sp, color = Color(0xFF64748B))
                        }
                    }
                    Switch(
                        checked = orbSettings.pixelGridHardware,
                        onCheckedChange = { onTogglePixelGrid() }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Disconnect Button
        OutlinedButton(
            onClick = onDisconnectOrb,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = Color(0xFFDC2626))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Orb trennen", color = Color(0xFFDC2626), fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// -------------------------------------------------------------
// TAB 2: Locked Character Lab (Gesperrt)
// -------------------------------------------------------------
@Composable
private fun LockedCharacterLabView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .shadow(20.dp, CircleShape, spotColor = Color(0xFF9333EA).copy(alpha = 0.4f))
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFFE9D5FF), Color(0xFFD8B4FE), Color(0xFFFAF5FF))
                    )
                )
                .border(2.dp, Color(0xFFC084FC), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = "Gesperrt",
                tint = Color(0xFF9333EA),
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E8FF)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "ZWEITER TAB • VORERST GESPERRT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF7E22CE),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Weitere Charaktere erzeugen",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Das Charakter-Synthese-Labor wird mit dem kommenden SI-M Firmware Update v2.5 für deinen AI Orb freigeschaltet. Dein aktueller Begleiter bleibt voll aktiv.",
            fontSize = 13.sp,
            color = Color(0xFF64748B),
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
