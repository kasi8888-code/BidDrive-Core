package com.biddrive.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biddrive.app.data.api.NetworkClient
import com.biddrive.app.data.repository.BidDriveRepository
import com.biddrive.app.ui.auth.AuthScreen
import com.biddrive.app.ui.components.AppHeaderBar
import com.biddrive.app.ui.driver.DriverScreen
import com.biddrive.app.ui.passenger.PassengerScreen
import com.biddrive.app.ui.theme.BidDriveTheme
import com.biddrive.app.ui.theme.DarkBackground

class MainActivity : ComponentActivity() {

    private val repository by lazy { BidDriveRepository() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BidDriveTheme {
                BidDriveMainApp(repository = repository)
            }
        }
    }
}

@Composable
fun BidDriveMainApp(repository: BidDriveRepository) {
    val currentUser by repository.currentUser.collectAsState()
    val activeRole by repository.activeRole.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = DarkBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (currentUser == null) {
                AuthScreen(
                    repository = repository,
                    onAuthSuccess = {
                        // Handled reactively by currentUser state
                    }
                )
            } else {
                val userRole = currentUser?.role ?: activeRole
                Column(modifier = Modifier.fillMaxSize()) {
                    AppHeaderBar(
                        currentRole = userRole,
                        userEmail = currentUser?.email,
                        onSettingsClick = { showSettingsDialog = true },
                        onLogoutClick = { repository.logout() }
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        if (userRole == "PASSENGER") {
                            PassengerScreen(repository = repository)
                        } else {
                            DriverScreen(repository = repository)
                        }
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        var tempUrl by remember { mutableStateOf(NetworkClient.getBaseUrl()) }
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Server Configuration") },
            text = {
                Column {
                    Text("Spring Boot Backend API URL:", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tempUrl,
                        onValueChange = { tempUrl = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { tempUrl = NetworkClient.DEFAULT_EMULATOR_URL }) {
                            Text("10.0.2.2")
                        }
                        TextButton(onClick = { tempUrl = NetworkClient.DEFAULT_LOCALHOST_URL }) {
                            Text("localhost")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    NetworkClient.setBaseUrl(tempUrl)
                    showSettingsDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
