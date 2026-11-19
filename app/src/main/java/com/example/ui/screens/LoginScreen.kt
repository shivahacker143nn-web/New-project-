package com.example.ui.screens

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.firebase.PhoneOtpState
import com.example.ui.AishuViewModel
import com.example.ui.components.CyberGridBackground
import com.example.ui.components.StatusPill
import com.example.ui.components.TerminalCard
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CardBorderDark
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmberGlow
import com.example.ui.theme.NeonScarlet
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun LoginScreen(
    viewModel: AishuViewModel,
    onAuthenticated: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val isOnline by viewModel.isNetworkAvailable.collectAsStateWithLifecycle()
    val phoneOtpState by viewModel.phoneOtpState.collectAsStateWithLifecycle()
    val authMessage by viewModel.authMessage.collectAsStateWithLifecycle()
    val isBusy by viewModel.isAuthBusy.collectAsStateWithLifecycle()

    var phoneInput by remember { mutableStateOf("+91") }
    var otpInput by remember { mutableStateOf("") }

    CyberGridBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "STEP 3 OF 4 • SIGN IN OR SKIP",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmberGlow
                        )
                        Text(
                            text = "AI SHU ACCESS",
                            style = MaterialTheme.typography.headlineMedium,
                            color = CrimsonPrimary
                        )
                        Text(
                            text = "Optional cloud sync — you can also skip and start immediately.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    StatusPill(
                        text = if (isOnline) "ONLINE" else "OFFLINE",
                        accentColor = if (isOnline) CyberGreen else AmberAlert
                    )
                }

                AnimatedVisibility(visible = !authMessage.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CrimsonContainer.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, AmberAlert.copy(alpha = 0.7f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = authMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AmberAlert,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Option 1: Continue with Google
                Button(
                    onClick = {
                        if (activity != null) {
                            viewModel.signInWithGoogle(activity, onAuthenticated)
                        }
                    },
                    enabled = isOnline && !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("google_signin_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrimsonPrimary,
                        contentColor = TextPrimary
                    )
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = TextPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = TextPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Continue with Google", style = MaterialTheme.typography.titleMedium)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = CardBorderDark)
                    Text("OR", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    HorizontalDivider(modifier = Modifier.weight(1f), color = CardBorderDark)
                }

                // Option 2: Continue with mobile number
                TerminalCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = CrimsonPrimary
                        )
                        Text(
                            text = "CONTINUE WITH MOBILE NUMBER",
                            style = MaterialTheme.typography.labelLarge,
                            color = CrimsonPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Enter your mobile number in international E.164 format (e.g. +919876543210) to verify via Firebase SMS OTP.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = { Text("Mobile Number (+CountryCode)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("phone_number_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    when (val otpState = phoneOtpState) {
                        is PhoneOtpState.Idle,
                        is PhoneOtpState.Error -> {
                            if (otpState is PhoneOtpState.Error) {
                                Text(
                                    text = otpState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NeonScarlet
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            OutlinedButton(
                                onClick = {
                                    if (activity != null) {
                                        viewModel.startPhoneOtpVerification(activity, phoneInput, onAuthenticated)
                                    }
                                },
                                enabled = isOnline && !isBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("send_otp_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, CrimsonPrimary)
                            ) {
                                Text("Continue with Mobile Number", color = TextPrimary)
                            }
                        }

                        PhoneOtpState.SendingCode,
                        PhoneOtpState.Verifying -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = CrimsonPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (otpState is PhoneOtpState.SendingCode) "Sending OTP..." else "Verifying OTP...",
                                    color = ElectricCyan
                                )
                            }
                        }

                        is PhoneOtpState.CodeSent -> {
                            StatusPill(
                                text = "OTP SENT TO ${otpState.phoneNumber}",
                                accentColor = CyberGreen
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = otpInput,
                                onValueChange = { otpInput = it },
                                label = { Text("6-Digit OTP Code") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("otp_code_input")
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        viewModel.verifyPhoneOtp(otpState.verificationId, otpInput, onAuthenticated)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("verify_otp_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                                ) {
                                    Text("Verify & Login")
                                }
                                TextButton(
                                    onClick = { viewModel.resetPhoneOtp() },
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text("Change Number", color = TextSecondary)
                                }
                            }
                        }
                    }
                }

                TerminalCard(borderColor = CardBorderDark) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = CyberGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Privacy & Optional Cloud Sync",
                            style = MaterialTheme.typography.labelLarge,
                            color = CyberGreen
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Signing in syncs your profile and personal rules across devices via Cloud Firestore. Google Gemini AI, voice profiles, and local memories work immediately even if you skip.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Option 3: Skip for now
            OutlinedButton(
                onClick = {
                    viewModel.enterOfflineLocalMode()
                    onAuthenticated()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .height(52.dp)
                    .testTag("skip_login_button"),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (!isOnline) AmberAlert else ElectricCyan.copy(alpha = 0.7f))
            ) {
                Text(
                    text = "Skip for now",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (!isOnline) AmberAlert else ElectricCyan
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Skip login",
                    tint = if (!isOnline) AmberAlert else ElectricCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
