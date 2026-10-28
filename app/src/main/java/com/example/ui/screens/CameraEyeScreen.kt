package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.CreatureDna
import com.example.ui.theme.CyanPrimary
import kotlin.math.sin

@Composable
fun CameraEyeScreen(
    activeCharacter: CreatureDna,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onClose() }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var torchEnabled by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "hudPulse")
    val scanLineY by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanLine"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("camera_eye_screen")
    ) {
        if (hasCameraPermission) {
            // Live CameraX Feed
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            val cameraSelector = CameraSelector.Builder()
                                .requireLensFacing(lensFacing)
                                .build()

                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview
                            )
                        } catch (_: Exception) {
                            // Fallback gracefully
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Permission request or simulated sensory HUD
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF070B14)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Kamera-Zugriff für 'SI-M Auge' erforderlich",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "SI-M nutzt deine Smartphone-Kamera als visuelles Organ, um Objekte, Räume und Gesichter für den Orb wahrzunehmen.",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Kamera-Erlaubnis erteilen")
                    }
                }
            }
        }

        // Futuristic AI Vision HUD Overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Corner brackets
            val bracketLen = 42f
            val bracketColor = Color(0xFF38BDF8)
            val strokeW = 3f

            // Top Left
            drawLine(bracketColor, Offset(40f, 80f), Offset(40f + bracketLen, 80f), strokeWidth = strokeW)
            drawLine(bracketColor, Offset(40f, 80f), Offset(40f, 80f + bracketLen), strokeWidth = strokeW)

            // Top Right
            drawLine(bracketColor, Offset(w - 40f, 80f), Offset(w - 40f - bracketLen, 80f), strokeWidth = strokeW)
            drawLine(bracketColor, Offset(w - 40f, 80f), Offset(w - 40f, 80f + bracketLen), strokeWidth = strokeW)

            // Bottom Left
            drawLine(bracketColor, Offset(40f, h - 140f), Offset(40f + bracketLen, h - 140f), strokeWidth = strokeW)
            drawLine(bracketColor, Offset(40f, h - 140f), Offset(40f, h - 140f - bracketLen), strokeWidth = strokeW)

            // Bottom Right
            drawLine(bracketColor, Offset(w - 40f, h - 140f), Offset(w - 40f - bracketLen, h - 140f), strokeWidth = strokeW)
            drawLine(bracketColor, Offset(w - 40f, h - 140f), Offset(w - 40f, h - 140f - bracketLen), strokeWidth = strokeW)

            // Center target reticle
            val cx = w * 0.5f
            val cy = h * 0.48f
            drawCircle(bracketColor.copy(alpha = 0.5f), radius = 48f, center = Offset(cx, cy), style = Stroke(width = 2f))
            drawCircle(bracketColor, radius = 4f, center = Offset(cx, cy))
            drawLine(bracketColor.copy(alpha = 0.6f), Offset(cx - 65f, cy), Offset(cx - 30f, cy), strokeWidth = 2f)
            drawLine(bracketColor.copy(alpha = 0.6f), Offset(cx + 30f, cy), Offset(cx + 65f, cy), strokeWidth = 2f)
            drawLine(bracketColor.copy(alpha = 0.6f), Offset(cx, cy - 65f), Offset(cx, cy - 30f), strokeWidth = 2f)
            drawLine(bracketColor.copy(alpha = 0.6f), Offset(cx, cy + 30f), Offset(cx, cy + 65f), strokeWidth = 2f)

            // Animated Laser Scan Line
            val ly = h * scanLineY
            drawLine(
                color = Color(0x7706B6D4),
                start = Offset(50f, ly),
                end = Offset(w - 50f, ly),
                strokeWidth = 2.5f
            )
        }

        // Top HUD Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x99000000))
                    .testTag("camera_eye_close_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Schließen", tint = Color.White)
            }

            // Stream Status Badge
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xBB0A0F1D)),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22C55E))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SI-M AUGE • LIVE STREAM AN ORB",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                }
            }

            Row {
                IconButton(
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0x99000000))
                ) {
                    Icon(Icons.Default.Cameraswitch, contentDescription = "Kamera wechseln", tint = Color.White)
                }
            }
        }

        // Bottom HUD Telemetry Overlay
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.BottomCenter),
            colors = CardDefaults.cardColors(containerColor = Color(0xCC090E1A)),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x4438BDF8))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SI-M Begleiter: ${activeCharacter.name}",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Objekt-Erkennung & Visuelle Orientierung aktiv",
                            fontSize = 11.sp,
                            color = Color(0xFF38BDF8)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x3306B6D4))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "1080p • 60 FPS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF67E8F9),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Latenz: 14 ms (BLE + Wi-Fi Direct)", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    Text(text = "FOV: 78° Weitwinkel", fontSize = 11.sp, color = Color(0xFF94A3B8))
                }
            }
        }
    }
}
