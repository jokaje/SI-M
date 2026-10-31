package com.example.ui.screens

import com.example.ui.tr
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.CyanPrimaryLight
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.simPalette

@Composable
fun SplashScreen(
    progress: Float,
    statusText: String,
    modifier: Modifier = Modifier
) {
    val p = simPalette()
    val infiniteTransition = rememberInfiniteTransition(label = "splashPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        p.card,
                        p.surface,
                        p.accentSoft
                    )
                )
            )
            .testTag("splash_container"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Glowing AI Orb Logo
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .scale(pulseScale)
                    .shadow(32.dp, CircleShape, spotColor = CyanPrimary.copy(alpha = 0.5f))
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                // brand logo gradient: identical in both modes on purpose
                                Color(0xFF38BDF8),
                                Color(0xFF0284C7),
                                Color(0xFF0F172A)
                            )
                        )
                    )
                    .border(3.dp, p.infoInk, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Internal AI Core
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFFFFFFF), p.accent, p.accentAlt)
                            )
                        )
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(p.accent)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "SI-M",
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                color = p.ink,
                letterSpacing = 2.sp
            )

            Text(
                text = "SUPER INTELLIGENCE MOBILE",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = IndigoAccent,
                letterSpacing = 1.5.sp
            )

            Text(
                text = tr("Companion für deinen SI-M Orb"),
                fontSize = 14.sp,
                color = p.muted,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .testTag("splash_progress"),
                color = CyanPrimary,
                trackColor = p.muted
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = statusText,
                fontSize = 12.sp,
                color = p.muted,
                textAlign = TextAlign.Center
            )
        }
    }
}
