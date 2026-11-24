package steps.notifer.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

// Single DataStore instance per app (preferencesDataStore is a property delegate backed by a file)
val Context.dataStore by preferencesDataStore(name = "step_goal_prefs")
object PrefsKeys {
    val TARGET_HOUR = intPreferencesKey("target_hour")
    val TARGET_MINUTE = intPreferencesKey("target_minute")
    val STEP_THRESHOLD = intPreferencesKey("step_threshold")
    // Undated v1.0.1 key: retained only to detect migration, never used as an offset.
    val STEPS_CORRECTION = longPreferencesKey("steps_correction")
}

suspend fun Context.saveGoal(hour: Int, minute: Int, steps: Int) {
    dataStore.edit {
        it[PrefsKeys.TARGET_HOUR] = hour
        it[PrefsKeys.TARGET_MINUTE] = minute
        it[PrefsKeys.STEP_THRESHOLD] = steps
    }
}

/** Persist the steps correction offset. Negative values subtract from the HC reading. */
suspend fun Context.saveStepsCorrection(correction: Long) {
    updateDailyCorrection(correction)
}

data class GoalData(val hour: Int, val minute: Int, val steps: Int)
fun Context.goalFlow(): Flow<GoalData?> = dataStore.data.map { prefs ->
    val h = prefs[PrefsKeys.TARGET_HOUR] ?: return@map null
    val m = prefs[PrefsKeys.TARGET_MINUTE] ?: return@map null
    val s = prefs[PrefsKeys.STEP_THRESHOLD] ?: return@map null
    GoalData(h, m, s)
}

/** Omit unknown historical data; never turn denied permission or a missing metric into 0. */
internal suspend fun Context.updateDailyCorrection(
    manual: Long? = null,
    allowNeutralBaseline: Boolean = false,
): Boolean = rollCorrection(dataStore, manual, { ZonedDateTime.now() }, allowNeutralBaseline) { start, end ->
    try {
        HealthConnectHelper.readTotal(this, start, end)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (unavailable: Exception) {
        Log.w("DayJournal", "Health Connect reading unavailable", unavailable)
        null
    }
}

fun Context.historyFlow(): Flow<List<DaySteps>> = dataStore.data.map { it.history() }.distinctUntilChanged()

/** Also refresh long-lived collectors at midnight or when the user changes time/zone. */
fun Context.stepsCorrectionFlow(): Flow<Long> = combine(dataStore.data, calendarChanges()) { prefs, _ ->
    prefs.correction(ZonedDateTime.now().stepDay()) ?: 0L
}.distinctUntilChanged()

private fun Context.calendarChanges(): Flow<Unit> = callbackFlow {
    val context = applicationContext
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { trySend(Unit) }
    }
    // All three are protected system broadcasts.
    context.registerReceiver(receiver, IntentFilter().apply {
        addAction(Intent.ACTION_DATE_CHANGED)
        addAction(Intent.ACTION_TIME_CHANGED)
        addAction(Intent.ACTION_TIMEZONE_CHANGED)
    })
    trySend(Unit)
    awaitClose { context.unregisterReceiver(receiver) }
}
