package com.example.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import com.example.ui.tr
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.simOutlinedTextFieldColors
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.simPalette

@Composable
fun AuthScreen(
    hasAccount: Boolean,
    initialEmail: String = "",
    initialName: String = "",
    error: String? = null,
    busy: Boolean = false,
    onAuthenticate: (email: String, name: String, passcode: String, register: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val p = simPalette()
    var email by remember(initialEmail) { mutableStateOf(initialEmail) }
    var password by remember { mutableStateOf("") }
    var name by remember(initialName) { mutableStateOf(initialName) }
    var passwordVisible by remember { mutableStateOf(false) }

    val register = !hasAccount

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        p.card,
                        p.surface,
                        p.cardAlt
                    )
                )
            )
            .testTag("auth_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .shadow(16.dp, CircleShape, spotColor = CyanPrimary.copy(alpha = 0.4f))
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                // brand logo gradient: identical in both modes on purpose
                                Color(0xFF38BDF8),
                                Color(0xFF0284C7),
                                Color(0xFF0F172A)
                            )
                        )
                    )
                    .border(2.dp, p.infoInk, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(CyanPrimary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "SI-M",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = p.ink,
                letterSpacing = 1.sp
            )

            Text(
                text = "Super Intelligence Mobile Companion",
                fontSize = 13.sp,
                color = p.muted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(20.dp), spotColor = Color(0x1A000000)),
                colors = CardDefaults.cardColors(containerColor = p.card),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = if (register) tr("Lokales Profil anlegen") else tr("Profil entsperren"),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = p.ink
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (register) {
                        OutlinedTextField(
                colors = simOutlinedTextFieldColors(),
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(tr("Dein Name")) },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Name") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_name_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    OutlinedTextField(
                colors = simOutlinedTextFieldColors(),
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(tr("E-Mail-Adresse")) },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = tr("E-Mail")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_email_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                colors = simOutlinedTextFieldColors(),
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(if (register) tr("Passwort (min. 6 Zeichen)") else tr("Passwort")) },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = tr("Passwort")) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = tr("Passwort anzeigen")
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_password_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (error != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = error,
                            color = p.danger,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { onAuthenticate(email, name, password, register) },
                        enabled = !busy && email.isNotBlank() && password.length >= 6 && (!register || name.isNotBlank()),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("auth_submit_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = when {
                                busy -> tr("Bitte warten …")
                                register -> tr("Profil anlegen")
                                else -> tr("Entsperren")
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = tr("Das Profil bleibt auf diesem Gerät. Das Passwort wird nur als Prüfsumme im Android-Keystore gespeichert."),
                fontSize = 11.sp,
                color = p.muted,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}
