package steps.notifer.app

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

@Composable
internal fun ForegroundShakeEffect(enabled: Boolean, onShake: () -> Unit) {
    val context = LocalContext.current
    val detector = remember { StrongShakeDetector() }
    val notify by rememberUpdatedState(onShake)
    if (!enabled) return

    LifecycleResumeEffect(context) {
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var active = true
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(event: SensorEvent) {
                if (!active) return
                val (x, y, z) = event.values
                val gravity = SensorManager.GRAVITY_EARTH
                val gSquared = (x * x + y * y + z * z) / (gravity * gravity)
                if (detector.sample(event.timestamp / 1_000_000, gSquared)) notify()
            }
        }
        if (sensor != null) manager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onPauseOrDispose {
            active = false
            manager?.unregisterListener(listener)
            detector.resetGesture()
        }
    }
}
