package com.example.ui.components

import android.os.Build
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.viewmodel.LockPurpose

@Composable
fun SecurityAuthView(
    purpose: LockPurpose,
    savedPin: String,
    isBiometricEnabled: Boolean,
    onSuccess: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var inputPin by remember { mutableStateOf("") }
    var pinVisible by remember { mutableStateOf(false) }
    val maxPinLength = 4

    // Header label corresponding to purpose
    val purposeLabel = when (purpose) {
        LockPurpose.INCOGNITO -> "Gizli Sekmeleri Aç"
        LockPurpose.HISTORY -> "Geçmişi Görüntüle"
        LockPurpose.DOWNLOADS -> "İndirilen Videoları Gör"
        else -> "G-Tarayıcı Güvenlik Girişi"
    }

    // Trigger biometric authentication dynamically
    val triggerBiometric = {
        val activity = context as? FragmentActivity
        if (activity != null && isBiometricEnabled) {
            val executor = ContextCompat.getMainExecutor(context)
            val biometricPrompt = BiometricPrompt(activity, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                    }

                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        Toast.makeText(context, "Doğrulama Başarılı!", Toast.LENGTH_SHORT).show()
                        onSuccess()
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        Toast.makeText(context, "Biyometrik doğrulama okunamadı.", Toast.LENGTH_SHORT).show()
                    }
                })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("G-Tarayıcı Doğrulaması")
                .setSubtitle(purposeLabel)
                .setNegativeButtonText("İptal")
                .build()

            biometricPrompt.authenticate(promptInfo)
        } else {
            Toast.makeText(context, "Biyometrik doğrulama sistemde aktif değil.", Toast.LENGTH_SHORT).show()
        }
    }

    // Auto trigger biometric if configured
    LaunchedEffect(Unit) {
        if (isBiometricEnabled) {
            triggerBiometric()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("security_auth_prompt")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Card Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 40.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Kilit",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = purposeLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Lütfen 4 haneli PIN kodunuzu girin veya parmak izinizi kullanın.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            // PIN feedback view (dots)
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 32.dp)
            ) {
                for (i in 0 until maxPinLength) {
                    val isActive = i < inputPin.length
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .size(16.dp)
                            .background(
                                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape
                            )
                    )
                }
            }

            // Numeric Keypad Grid Layout
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                for (row in 0..2) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(28.dp)
                    ) {
                        for (col in 1..3) {
                            val num = row * 3 + col
                            KeypadButton(text = num.toString()) {
                                if (inputPin.length < maxPinLength) {
                                    inputPin += num.toString()
                                    // Check pin complete
                                    if (inputPin.length == maxPinLength) {
                                        val actualRequiredPin = savedPin.ifEmpty { "1234" } // fallback default if unset
                                        if (inputPin == actualRequiredPin) {
                                            onSuccess()
                                            inputPin = ""
                                        } else {
                                            Toast.makeText(context, "PIN kodu hatalı! Tekrar deneyin.", Toast.LENGTH_SHORT).show()
                                            inputPin = ""
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Row with Biometrics, 0, and Backspace
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    // Left element: Biometric fingerprint trigger
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                if (isBiometricEnabled) MaterialTheme.colorScheme.surfaceVariant
                                else Color.Transparent
                            )
                            .clickable(enabled = isBiometricEnabled) { triggerBiometric() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isBiometricEnabled) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Biyometrik",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Center 0
                    KeypadButton(text = "0") {
                        if (inputPin.length < maxPinLength) {
                            inputPin += "0"
                            if (inputPin.length == maxPinLength) {
                                val actualRequiredPin = savedPin.ifEmpty { "1234" }
                                if (inputPin == actualRequiredPin) {
                                    onSuccess()
                                    inputPin = ""
                                } else {
                                    Toast.makeText(context, "PIN kodu hatalı!", Toast.LENGTH_SHORT).show()
                                    inputPin = ""
                                }
                            }
                        }
                    }

                    // Right element: Backspace eraser
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                if (inputPin.isNotEmpty()) {
                                    inputPin = inputPin.dropLast(1)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Backspace,
                            contentDescription = "Sil",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Cancel action bottom text
            TextButton(
                onClick = onCancel,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .testTag("cancel_auth_button")
            ) {
                Text(
                    text = "Geri Dön",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun KeypadButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .testTag("keypad_$text"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
