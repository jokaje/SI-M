package com.example.ui.screens

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.example.ui.tr
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withStateAtLeast
import com.example.orb.SetupState
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.simPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    state: SetupState,
    permissions: Array<String>,
    onStartScan: () -> Unit,
    onConnect: (String) -> Unit,
    onConfirmPhysicalPairing: () -> Unit,
    onBack: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = simPalette()
    BackHandler { onBack() }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionsGranted by remember {
        mutableStateOf(permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED })
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionsGranted = permissions.all { result[it] == true } &&
            permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) launcher.launch(permissions)
    }
    // Initial composition and the permission callback can precede Activity.onStart.
    // Scan only after the application foreground owner has observed that boundary.
    LaunchedEffect(permissionsGranted, lifecycleOwner) {
        if (permissionsGranted) lifecycleOwner.lifecycle.withStateAtLeast(Lifecycle.State.STARTED) { onStartScan() }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "radarScan")
    val radarScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarScale"
    )
    val radarAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarAlpha"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Orb verbinden"), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("pairing_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Zurück"))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { if (permissionsGranted) onStartScan() },
                        modifier = Modifier.testTag("pairing_refresh_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = tr("Erneut suchen"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = p.card,
                    titleContentColor = p.ink
                )
            )
        },
        containerColor = p.surface,
        modifier = modifier.testTag("pairing_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = p.successSoft),
                border = androidx.compose.foundation.BorderStroke(1.dp, p.successSoft),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(p.success.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = tr("Anleitung"),
                            tint = p.success,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = tr("Kopplungsfenster am Orb öffnen"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = p.success
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = tr("1. Am Orb Einstellungen → Orb koppeln → Setup starten.\n") +
                                tr("2. Das Fenster bleibt 180 Sekunden offen.\n") +
                                tr("3. Halte das Smartphone in die Nähe und gib den Code vom Orb-Display im Android-Dialog ein."),
                            fontSize = 12.sp,
                            color = p.success,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                contentAlignment = Alignment.Center
            ) {
                if (state.scanning || state.connecting) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .scale(radarScale)
                            .clip(CircleShape)
                            .background(CyanPrimary.copy(alpha = radarAlpha))
                    )
                }

                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .shadow(12.dp, CircleShape, spotColor = CyanPrimary.copy(alpha = 0.4f))
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(p.accent, CyanPrimary)))
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.connecting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(32.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = tr("Bluetooth-Suche"),
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            Text(
                text = when {
                    state.connecting -> tr("Kopplung wird aufgebaut …")
                    state.connected -> tr("Verbunden")
                    state.scanning -> tr("Suche nach SI-M Orbs …")
                    else -> tr("Bereit für die Suche")
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = p.muted,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = tr(state.status),
                fontSize = 12.sp,
                color = if (state.status.contains("fehlt", true) || state.status.contains("nicht", true)) {
                    p.warning
                } else {
                    p.body
                },
                lineHeight = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            )

            if (state.scanDiagnostic.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.scanDiagnostic,
                    fontSize = 10.sp,
                    color = p.body,
                    modifier = Modifier.fillMaxWidth().testTag("scan_diagnostic")
                )
                OutlinedButton(
                    onClick = {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("SI-M SCANDIAG", state.scanDiagnostic))
                    },
                    modifier = Modifier.fillMaxWidth().testTag("copy_scan_diagnostic")
                ) { Text(tr("Scan-Diagnose kopieren (ohne Gerätekennung)")) }
            }

            if (!permissionsGranted) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { launcher.launch(permissions) },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(tr("Bluetooth-Berechtigung erteilen"))
                }
            }

            if (state.needsPhysicalConfirmation) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = p.infoBg),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            tr("Hast du den sechsstelligen Code vom Orb selbst im Android-Dialog eingegeben?"),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = p.infoInk,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onConfirmPhysicalPairing,
                            colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(tr("Codeeingabe bestätigen"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.devices) { device ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(2.dp, RoundedCornerShape(16.dp))
                            .testTag("device_card_${device.address}"),
                        colors = CardDefaults.cardColors(containerColor = p.card),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(p.accentSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bluetooth,
                                    contentDescription = null,
                                    tint = CyanPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "SI-M Orb",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = p.ink
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${device.address} • ${device.rssi} dBm",
                                    fontSize = 12.sp,
                                    color = p.muted
                                )
                            }

                            Button(
                                onClick = { onConnect(device.address) },
                                enabled = !state.connecting,
                                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("connect_button_${device.address}")
                            ) {
                                Text(tr("Koppeln"), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            if (state.connected || state.connecting) {
                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(tr("Verbindung beenden"))
                }
            }
        }
    }
}
