package com.biddrive.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biddrive.app.data.api.NetworkClient
import com.biddrive.app.data.models.RegisterRequest
import com.biddrive.app.data.repository.BidDriveRepository
import com.biddrive.app.ui.components.AutoRickshawIcon
import com.biddrive.app.ui.components.MotorcycleIcon
import com.biddrive.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    repository: BidDriveRepository,
    onAuthSuccess: () -> Unit
) {
    var isRegister by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("PASSENGER") }
    var licensePlate by remember { mutableStateOf("") }
    var selectedCarType by remember { mutableStateOf("Auto") }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showServerConfig by remember { mutableStateOf(false) }

    var showOtpDialog by remember { mutableStateOf(false) }
    var pendingEmail by remember { mutableStateOf("") }
    var testOtpCode by remember { mutableStateOf<String?>(null) }
    var isOtpLoading by remember { mutableStateOf(false) }
    var otpErrorMessage by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        containerColor = PureWhite,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(RapidoYellow),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = TextOnYellow,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("BidDrive Core", color = TextPrimary, fontWeight = FontWeight.ExtraBold)
                    }
                },
                actions = {
                    IconButton(onClick = { showServerConfig = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "Config Server", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PureWhite)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(PureWhite)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AutoRickshawIcon(size = 56.dp)
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (isRegister) "Create Your Account" else "Welcome to BidDrive",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )
            Text(
                text = if (isRegister) "Join the reverse-auction mobility network" else "Smarter rides with real-time captain bidding",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            // Quick Auto-Fill Demo Personas
            Text("Quick Test Personas:", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        isRegister = false
                        email = "passenger1@test.com"
                        password = "password123"
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RapidoYellow)
                ) {
                    Text("Rider", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = {
                        isRegister = false
                        email = "driver1@test.com"
                        password = "password123"
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BargainGreenDark),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BargainGreen)
                ) {
                    Text("Auto Captain", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (errorMessage != null) {
                Surface(
                    color = ErrorRedLight,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = ErrorRed,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (isRegister) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Role selector
                Text("Account Type (Locked After Signup)", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedRole == "PASSENGER",
                        onClick = { selectedRole = "PASSENGER" },
                        label = { Text("Rider / Passenger") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RapidoYellowLight,
                            selectedLabelColor = TextOnYellow
                        )
                    )
                    FilterChip(
                        selected = selectedRole == "DRIVER",
                        onClick = { selectedRole = "DRIVER" },
                        label = { Text("Captain / Driver") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BargainGreenLight,
                            selectedLabelColor = BargainGreenDark
                        )
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (selectedRole == "DRIVER") {
                    OutlinedTextField(
                        value = licensePlate,
                        onValueChange = { licensePlate = it },
                        label = { Text("License Plate (e.g. TN 11 AB 4821)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Vehicle Category", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Auto", "Bike", "Sedan", "SUV").forEach { car ->
                            FilterChip(
                                selected = selectedCarType == car,
                                onClick = { selectedCarType = car },
                                label = { Text(car, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RapidoYellowLight,
                                    selectedLabelColor = TextOnYellow
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password (min 8 chars)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        errorMessage = "Please enter email and password"
                        return@Button
                    }
                    isLoading = true
                    errorMessage = null

                    coroutineScope.launch {
                        val result = if (isRegister) {
                            repository.register(
                                RegisterRequest(
                                    name = name.ifBlank { "User" },
                                    email = email,
                                    password = password,
                                    phoneNumber = phone.ifBlank { "9876543210" },
                                    role = selectedRole,
                                    licensePlate = if (selectedRole == "DRIVER") licensePlate.ifBlank { "TN 11 AB 4821" } else null,
                                    carType = if (selectedRole == "DRIVER") selectedCarType else null
                                )
                            )
                        } else {
                            repository.login(email, password)
                        }

                        isLoading = false
                        result.onSuccess { auth ->
                            if (auth.status == "PENDING_VERIFICATION") {
                                pendingEmail = auth.email ?: email
                                testOtpCode = auth.testOtp
                                otpErrorMessage = null
                                showOtpDialog = true
                            } else {
                                onAuthSuccess()
                            }
                        }.onFailure { err ->
                            errorMessage = err.message ?: "Authentication failed"
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RapidoYellow),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = TextOnYellow)
                } else {
                    Text(
                        text = if (isRegister) "Sign Up as ${if (selectedRole == "DRIVER") "Captain" else "Rider"}" else "Sign In",
                        color = TextOnYellow,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = {
                isRegister = !isRegister
                errorMessage = null
            }) {
                Text(
                    text = if (isRegister) "Already have an account? Sign In" else "Don't have an account? Sign Up",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showServerConfig) {
        var tempUrl by remember { mutableStateOf(NetworkClient.getBaseUrl()) }
        AlertDialog(
            onDismissRequest = { showServerConfig = false },
            title = { Text("Backend Server Configuration", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Change Spring Boot backend API address:", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tempUrl,
                        onValueChange = { tempUrl = it },
                        label = { Text("Base URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { tempUrl = NetworkClient.DEFAULT_EMULATOR_URL }) {
                            Text("10.0.2.2 (Emulator)", fontSize = 11.sp)
                        }
                        TextButton(onClick = { tempUrl = NetworkClient.DEFAULT_LOCALHOST_URL }) {
                            Text("localhost", fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        NetworkClient.setBaseUrl(tempUrl)
                        showServerConfig = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RapidoYellow)
                ) {
                    Text("Save", color = TextOnYellow, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerConfig = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    if (showOtpDialog) {
        OtpVerificationDialog(
            email = pendingEmail,
            testOtp = testOtpCode,
            isLoading = isOtpLoading,
            errorMessage = otpErrorMessage,
            onDismiss = { showOtpDialog = false },
            onVerify = { code ->
                coroutineScope.launch {
                    isOtpLoading = true
                    otpErrorMessage = null
                    val res = repository.verifyOtp(pendingEmail, code)
                    isOtpLoading = false
                    res.onSuccess {
                        showOtpDialog = false
                        onAuthSuccess()
                    }.onFailure { err ->
                        otpErrorMessage = err.message ?: "Verification failed"
                    }
                }
            },
            onResend = {
                coroutineScope.launch {
                    val res = repository.resendOtp(pendingEmail)
                    res.onSuccess { auth ->
                        testOtpCode = auth.testOtp
                        otpErrorMessage = null
                    }.onFailure { err ->
                        otpErrorMessage = err.message ?: "Failed to resend code"
                    }
                }
            }
        )
    }
}
