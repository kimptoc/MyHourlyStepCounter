package com.example.myhourlystepcounter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.health.connect.client.PermissionController
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myhourlystepcounter.ui.theme.MyHourlyStepCounterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyHourlyStepCounterTheme {
                MyHourlyStepCounterApp()
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun MyHourlyStepCounterApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    val sharedViewModel: StepCounterViewModel = viewModel()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // Handle lifecycle events to pause/resume updates
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    android.util.Log.d("MainActivity", "onResume - resuming updates")
                    sharedViewModel.resumeUpdates()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    android.util.Log.d("MainActivity", "onPause - pausing updates")
                    sharedViewModel.pauseUpdates()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            it.icon,
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            when (currentDestination) {
                AppDestinations.HOME -> StepCounterScreen(
                    modifier = Modifier.padding(innerPadding),
                    viewModel = sharedViewModel
                )
                AppDestinations.HISTORY -> StepHistoryScreen(
                    modifier = Modifier.padding(innerPadding),
                    viewModel = sharedViewModel
                )
                AppDestinations.PROFILE -> Greeting(
                    name = "Profile",
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Default.Home),
    HISTORY("History", Icons.Default.List),
    PROFILE("Profile", Icons.Default.AccountBox),
}

@Composable
fun StepCounterScreen(
    modifier: Modifier = Modifier,
    viewModel: StepCounterViewModel
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        android.util.Log.d("DEBUG", "=== STARTUP CHECKS ===")
        android.util.Log.d("DEBUG", "Android Version: ${android.os.Build.VERSION.SDK_INT} (${android.os.Build.VERSION.RELEASE})")
        android.util.Log.d("DEBUG", "Manufacturer: ${android.os.Build.MANUFACTURER}")
        android.util.Log.d("DEBUG", "Model: ${android.os.Build.MODEL}")

        val status = androidx.health.connect.client.HealthConnectClient.getSdkStatus(context)
        android.util.Log.d("DEBUG", "SDK Status: $status (1 = AVAILABLE, 2 = UNAVAILABLE, 3 = UNAVAILABLE_PROVIDER_UPDATE_REQUIRED)")
        android.util.Log.d("DEBUG", "Package manager check...")

        val pm = context.packageManager
        val healthConnectPackage = "com.google.android.apps.healthdata"
        try {
            val info = pm.getPackageInfo(healthConnectPackage, 0)
            android.util.Log.d("DEBUG", "Health Connect app installed: ${info.versionName}")
        } catch (e: Exception) {
            android.util.Log.d("DEBUG", "Health Connect app NOT found (expected if using framework module)")
        }

        // Check if system Health Connect framework module is available
        try {
            val systemPackage = "com.android.healthconnect.controller"
            val systemInfo = pm.getPackageInfo(systemPackage, 0)
            android.util.Log.d("DEBUG", "Framework Health Connect found: ${systemInfo.versionName}")
        } catch (e: Exception) {
            android.util.Log.d("DEBUG", "Framework Health Connect NOT found")
        }

        // Check for Samsung Health as alternative
        try {
            val samsungPackage = "com.sec.android.app.shealth"
            val samsungInfo = pm.getPackageInfo(samsungPackage, 0)
            android.util.Log.d("DEBUG", "Samsung Health found: ${samsungInfo.versionName}")
        } catch (e: Exception) {
            android.util.Log.d("DEBUG", "Samsung Health NOT found")
        }

        android.util.Log.d("DEBUG", "=== END STARTUP CHECKS ===")
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        android.util.Log.d("StepCounterScreen", "========================================")
        android.util.Log.d("StepCounterScreen", "Permission result received")
        android.util.Log.d("StepCounterScreen", "Granted set: $granted")
        android.util.Log.d("StepCounterScreen", "Granted count: ${granted.size}")
        android.util.Log.d("StepCounterScreen", "Required: ${HealthConnectManager.PERMISSIONS}")
        android.util.Log.d("StepCounterScreen", "Contains all required: ${granted.containsAll(HealthConnectManager.PERMISSIONS)}")
        android.util.Log.d("StepCounterScreen", "========================================")
        // Always refresh permissions to check current state
        viewModel.refreshPermissions()
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!state.healthConnectAvailable || state.healthConnectNeedsUpdate) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    text = if (state.healthConnectNeedsUpdate) {
                        "Health Connect Update Required"
                    } else {
                        "Health Connect Required"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Text(
                    text = when {
                        state.healthConnectNeedsUpdate -> "Health Connect is installed but outdated.\nPlease update it from the Play Store to continue."
                        state.healthConnectInstallUri != null -> "Health Connect needs to be installed."
                        else -> "Health Connect is not available on this device."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                state.healthConnectInstallUri?.let { uri ->
                    Button(onClick = {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                data = uri
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.util.Log.e("StepCounterScreen", "Failed to open install URI", e)
                        }
                    }) {
                        Text(if (state.healthConnectNeedsUpdate) "Update Health Connect" else "Install Health Connect")
                    }
                }
            }
            return@Column
        }

        if (!state.permissionsGranted) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    text = "Health Connect permissions required to view step data.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Button(
                    onClick = {
                        android.util.Log.d("StepCounterScreen", "Requesting permissions...")
                        permissionLauncher.launch(HealthConnectManager.PERMISSIONS)
                    },
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text("Grant Permissions")
                }

                Button(onClick = {
                    android.util.Log.d("StepCounterScreen", "Manual refresh triggered")
                    viewModel.refreshPermissions()
                }) {
                    Text("I Already Granted Permissions")
                }

                Button(
                    onClick = {
                        android.util.Log.d("StepCounterScreen", "=== TRYING DIRECT INTENT ===")
                        try {
                            val intent = androidx.health.connect.client.PermissionController
                                .createRequestPermissionResultContract()
                                .createIntent(context, HealthConnectManager.PERMISSIONS)
                            android.util.Log.d("StepCounterScreen", "Intent created: $intent")
                            android.util.Log.d("StepCounterScreen", "Intent action: ${intent.action}")
                            android.util.Log.d("StepCounterScreen", "Intent extras: ${intent.extras}")
                        } catch (e: Exception) {
                            android.util.Log.e("StepCounterScreen", "Failed to create intent", e)
                        }
                    }
                ) {
                    Text("Debug Intent")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Steps to grant permission:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = "1. Open Health Connect settings\n2. Find 'App permissions'\n3. Search for 'MyHourlyStepCounter'\n4. Enable 'Steps' permission\n5. Return here and refresh",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    Button(onClick = {
                        // Try to open Health Connect
                        try {
                            val intent = android.content.Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            android.util.Log.d("StepCounterScreen", "Opened Health Connect settings")
                        } catch (e: Exception) {
                            android.util.Log.e("StepCounterScreen", "Failed to open HC settings", e)
                            // Fallback to system settings
                            try {
                                val fallback = android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                                context.startActivity(fallback)
                            } catch (ex: Exception) {
                                android.util.Log.e("StepCounterScreen", "Failed fallback", ex)
                            }
                        }
                    }) {
                        Text("Open Health Connect")
                    }
                }
            }
            return@Column
        }

        // Current date and time
        Text(
            text = state.currentDateTime,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Current hour steps
        Text(
            text = "Current Hour",
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "${state.hourlySteps}",
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "steps",
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 48.dp)
        )

        // Daily total steps
        Text(
            text = "Today's Total",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Text(
            text = "${state.dailySteps}",
            fontSize = 36.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.secondary
        )

        Text(
            text = "steps",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun StepHistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: StepCounterViewModel
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Today's Step History",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        if (state.stepHistory.isEmpty()) {
            Text(
                text = "No history available yet.\nComplete your first hour to see history!",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(32.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                state.stepHistory.forEach { record ->
                    HourlyStepItem(record)
                }
            }
        }
    }
}

@Composable
fun HourlyStepItem(record: HourlyStepRecord) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = record.hourLabel,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "${record.steps} steps",
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyHourlyStepCounterTheme {
        Greeting("Android")
    }
}