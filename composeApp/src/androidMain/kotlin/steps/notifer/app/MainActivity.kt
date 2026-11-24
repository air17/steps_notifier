package steps.notifer.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var requestingHealth = false
    private var requestingNotifications = false
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            requestingNotifications = false
            if (!granted) message("Reminders are blocked. Enable notifications in app settings.")
            requestHealthConnectPermissions()
        }
    private val healthPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        requestingHealth = false
        if (HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND !in granted) {
            message("Without background step access, scheduled checks may not read your steps.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        NotificationHelper.createChannel(this)
        setContent { App(onRequestHealthPermissions = { requestHealthConnectPermissions() }) }
        requestingNotifications = savedInstanceState?.getBoolean("requesting_notifications") == true
        if (savedInstanceState == null) {
            // Never open the HC activity over the notification permission dialog or
            // relaunch it from onStart immediately after the user denies a permission.
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestingNotifications = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else requestHealthConnectPermissions()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("requesting_notifications", requestingNotifications)
        super.onSaveInstanceState(outState)
    }

    @OptIn(androidx.health.connect.client.feature.ExperimentalFeatureAvailabilityApi::class)
    fun requestHealthConnectPermissions() {
        if (requestingHealth || requestingNotifications) return
        requestingHealth = true
        lifecycleScope.launch {
            try {
                if (HealthConnectClient.getSdkStatus(this@MainActivity) != HealthConnectClient.SDK_AVAILABLE) {
                    requestingHealth = false
                    return@launch
                }
                val client = HealthConnectClient.getOrCreate(this@MainActivity)
                val supportsBackground = client.features.getFeatureStatus(
                    HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND
                ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
                val required = buildSet {
                    add(HealthPermission.getReadPermission(StepsRecord::class))
                    if (supportsBackground) add(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)
                }
                val granted = client.permissionController.getGrantedPermissions()
                if (!supportsBackground) message("Health Connect on this device does not support background step access.")
                if (!granted.containsAll(required)) healthPermissionLauncher.launch(required)
                else requestingHealth = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                requestingHealth = false
                Log.w("HealthConnect", "Could not request step permissions", e)
                message("Could not request step access. Check Health Connect settings.")
            }
        }
    }

    private fun message(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
