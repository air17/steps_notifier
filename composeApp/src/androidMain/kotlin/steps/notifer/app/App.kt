package steps.notifer.app
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
@Composable
fun App(
    viewModel: StepGoalViewModel = viewModel(),
    onRequestHealthPermissions: () -> Unit = {}
) {
    val hour by viewModel.hour.collectAsState()
    val minute by viewModel.minute.collectAsState()
    val stepThreshold by viewModel.stepThreshold.collectAsState()
    val saved by viewModel.saved.collectAsState()
    val currentGoal by viewModel.currentGoal.collectAsState()
    val currentSteps by viewModel.currentSteps.collectAsState()
    val stepsLoading by viewModel.stepsLoading.collectAsState()
    val hcStatus by viewModel.healthConnectStatus.collectAsState()
    val context = LocalContext.current
    val debugData by viewModel.debugData.collectAsState()
    val debugLoading by viewModel.debugLoading.collectAsState()
    val stepsCorrection by viewModel.stepsCorrection.collectAsState()
    // Debug dialog — shows raw Health Connect data, auto-copies to clipboard
    if (debugData != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDebug() },
            title = { Text("HC Debug (copied to clipboard)") },
            text = {
                Box(modifier = Modifier.heightIn(max = 400.dp)) {
                    Text(
                        debugData!!,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissDebug() }) { Text("Close") }
            }
        )
    }
    // Refresh steps and load settings every time the screen becomes active/resumed.
    // This ensures the correction worker is always scheduled (ensureCorrectionWorkerScheduled
    // is called inside loadExisting).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED) {
            viewModel.loadExisting()
            viewModel.refreshSteps()
        }
    }
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeContentPadding()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Step Goal Notifier",
                    style = MaterialTheme.typography.headlineMedium
                )
                // ── Today's Steps Card ──────────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Today's Steps", style = MaterialTheme.typography.titleMedium)
                            if (stepsLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else if (currentSteps != null) {
                                Text(
                                    "${currentSteps!!.toInt()} steps",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${"%.2f".format(NotificationPhrases.stepsToKm(currentSteps!!.toInt()))} km",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                                // Show correction hint if non-zero so user understands the offset
                                val corrVal = stepsCorrection.toLongOrNull() ?: 0L
                                if (corrVal != 0L) {
                                    val sign = if (corrVal > 0) "+" else ""
                                    Text(
                                        "correction: $sign$corrVal steps applied",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                                    )
                                }
                                // Progress bar toward goal
                                val goal = currentGoal
                                if (goal != null && goal.steps > 0) {
                                    val progress = (currentSteps!! / goal.steps.toFloat()).coerceIn(0f, 1f)
                                    Spacer(Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        "${(progress * 100).toInt()}% of ${goal.steps} goal",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            } else {
                                when (hcStatus) {
                                    HealthConnectHelper.Status.NEEDS_INSTALL -> {
                                        Text(
                                            "Health Connect is not installed",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        OutlinedButton(onClick = {
                                            HealthConnectHelper.openHealthConnectPlayStore(context)
                                        }) { Text("Install Health Connect") }
                                    }
                                    HealthConnectHelper.Status.NEEDS_UPDATE -> {
                                        Text(
                                            "Health Connect needs an update",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        OutlinedButton(onClick = {
                                            HealthConnectHelper.openHealthConnectPlayStore(context)
                                        }) { Text("Update Health Connect") }
                                    }
                                    HealthConnectHelper.Status.AVAILABLE -> {
                                        // HC is available but we got null — permission not granted
                                        Text(
                                            "Permission not granted",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        OutlinedButton(onClick = onRequestHealthPermissions) {
                                            Text("Grant Steps Permission")
                                        }
                                    }
                                }
                            }
                        }
                        // Refresh button
                        IconButton(onClick = { viewModel.refreshSteps() }) {
                            Text("↻", style = MaterialTheme.typography.titleLarge)
                        }
                        // Debug button — loads raw HC data and copies to clipboard
                        if (debugLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { viewModel.loadDebugData() }) {
                                Text("🐛", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
                HorizontalDivider()
                // ── Steps Correction Card ───────────────────────────────────
                // The correction is a signed integer added to the raw HC step count.
                // A negative value (e.g. -188) subtracts carry-over steps from the previous day.
                // Automatically set each night at 3:30 AM by CorrectionWorker; can also be
                // edited manually here.
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Steps Correction", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Added to the raw Health Connect reading. " +
                                    "Auto-set at 3:30 AM to cancel carry-over from the previous day. " +
                                    "Edit manually if needed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = stepsCorrection,
                                onValueChange = { viewModel.onStepsCorrectionChange(it.filter { c -> c.isDigit() || c == '-' }) },
                                label = { Text("Correction") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = { viewModel.saveCorrection() }),
                                singleLine = true
                            )
                            Button(onClick = { viewModel.saveCorrection() }) {
                                Text("Apply")
                            }
                        }
                    }
                }
                HorizontalDivider()
                Text("Target Time", style = MaterialTheme.typography.titleMedium)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Hour picker
                    OutlinedTextField(
                        value = hour.toString().padStart(2, '0'),
                        onValueChange = {
                            val h = it.filter { c -> c.isDigit() }.take(2).toIntOrNull()
                            if (h != null && h in 0..23) viewModel.onHourChange(h)
                        },
                        label = { Text("Hour") },
                        modifier = Modifier.width(100.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    Text(":", style = MaterialTheme.typography.headlineMedium)
                    OutlinedTextField(
                        value = minute.toString().padStart(2, '0'),
                        onValueChange = {
                            val m = it.filter { c -> c.isDigit() }.take(2).toIntOrNull()
                            if (m != null && m in 0..59) viewModel.onMinuteChange(m)
                        },
                        label = { Text("Min") },
                        modifier = Modifier.width(100.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = stepThreshold,
                    onValueChange = { viewModel.onStepThresholdChange(it.filter { c -> c.isDigit() }) },
                    label = { Text("Step Threshold") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                Button(
                    onClick = { viewModel.saveAndSchedule() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (saved) "Update Goal" else "Set Goal")
                }
                if (currentGoal != null) {
                    val g = currentGoal!!
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Active Goal", style = MaterialTheme.typography.titleMedium)
                            Text("Time: ${g.hour.toString().padStart(2, '0')}:${g.minute.toString().padStart(2, '0')}")
                            Text("Target: ${g.steps} steps  (${"%.2f".format(NotificationPhrases.stepsToKm(g.steps))} km)")
                        }
                    }
                }
                if (saved) {
                    Text(
                        "✓ Goal saved & worker scheduled!",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
