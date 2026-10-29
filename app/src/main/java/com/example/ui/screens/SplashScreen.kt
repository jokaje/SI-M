package com.example.ui.screens

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

@Composable
fun SplashScreen(
    progress: Float,
    statusText: String,
    modifier: Modifier = Modifier
) {
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
                        Color(0xFFFFFFFF),
                        Color(0xFFF0F9FF),
                        Color(0xFFE0F2FE)
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
                                Color(0xFF38BDF8),
                                Color(0xFF0284C7),
                                Color(0xFF0F172A)
                            )
                        )
                    )
                    .border(3.dp, Color(0xFFBAE6FD), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Internal AI Core
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFFFFFFF), Color(0xFF67E8F9), Color(0xFF6366F1))
                            )
                        )
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0284C7))
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "SI-M",
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF0F172A),
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
                text = "Companion für deinen SI-M Orb",
                fontSize = 14.sp,
                color = Color(0xFF64748B),
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
                trackColor = Color(0xFFCBD5E1)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = statusText,
                fontSize = 12.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )
        }
    }
}
