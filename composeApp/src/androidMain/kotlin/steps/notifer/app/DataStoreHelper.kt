package steps.notifer.app
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
// Single DataStore instance per app (preferencesDataStore is a property delegate backed by a file)
val Context.dataStore by preferencesDataStore(name = "step_goal_prefs")
object PrefsKeys {
    val TARGET_HOUR = intPreferencesKey("target_hour")
    val TARGET_MINUTE = intPreferencesKey("target_minute")
    val STEP_THRESHOLD = intPreferencesKey("step_threshold")
    // Steps correction: a positive or negative offset added to the raw Health Connect reading.
    // Automatically set to the negative of the 3:30 AM step count each night (see CorrectionWorker)
    // so that residual "carry-over" steps from the previous day are cancelled out.
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
    dataStore.edit { it[PrefsKeys.STEPS_CORRECTION] = correction }
}
data class GoalData(val hour: Int, val minute: Int, val steps: Int)
fun Context.goalFlow(): Flow<GoalData?> = dataStore.data.map { prefs ->
    val h = prefs[PrefsKeys.TARGET_HOUR] ?: return@map null
    val m = prefs[PrefsKeys.TARGET_MINUTE] ?: return@map null
    val s = prefs[PrefsKeys.STEP_THRESHOLD] ?: return@map null
    GoalData(h, m, s)
}
/** Emits the current steps correction value (default 0). */
fun Context.stepsCorrectionFlow(): Flow<Long> =
    dataStore.data.map { it[PrefsKeys.STEPS_CORRECTION] ?: 0L }
