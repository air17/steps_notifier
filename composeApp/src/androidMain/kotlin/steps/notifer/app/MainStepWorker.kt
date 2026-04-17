package steps.notifer.app
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.firstOrNull
/**
 * Fires at the user's configured target time (T) to check whether the step goal has been met.
 *
 * The steps correction offset (stored in DataStore) is applied before comparing:
 *   effectiveSteps = rawHCSteps + correction
 * The correction is usually a negative number set by CorrectionWorker at 3:30 AM to
 * cancel out any carry-over steps from the previous day.
 */
class MainStepWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val goal = applicationContext.goalFlow().firstOrNull() ?: return Result.failure()
        // Read raw steps from Health Connect
        val rawSteps = try {
            HealthConnectHelper.getTodaySteps(applicationContext)
        } catch (e: Exception) {
            Log.e("MainStepWorker", "Failed to read steps: ${e::class.simpleName}: ${e.message}", e)
            // Retry up to 3 times before giving up
            if (runAttemptCount < 3) return Result.retry()
            // After retries exhausted, notify user and still start the retry loop
            NotificationHelper.show(
                applicationContext,
                "Step Check Failed",
                "Could not read steps from Health Connect (attempt ${runAttemptCount + 1}). " +
                        "Make sure permissions are granted."
            )
            WorkScheduler.enqueueRetryWorker(applicationContext)
            return Result.success()
        }
        // Apply the user-editable (or auto-set) correction offset
        val correction = applicationContext.stepsCorrectionFlow().firstOrNull() ?: 0L
        val currentSteps = rawSteps + correction
        Log.d("MainStepWorker", "Steps today: raw=$rawSteps correction=$correction effective=$currentSteps / goal=${goal.steps}")
        if (currentSteps >= goal.steps) {
            // Goal met — schedule next day's check and stop
            WorkScheduler.enqueueNextDayMainWorker(applicationContext, goal.hour, goal.minute)
            return Result.success()
        }
        // Goal not met — show notification with steps/km remaining and start the 30-min retry loop
        val stepsLeft = (goal.steps - currentSteps).toInt()
        val kmLeft = NotificationPhrases.stepsToKm(stepsLeft)
        val title = NotificationPhrases.initial.random()
        val body = NotificationPhrases.stepsBody(stepsLeft, kmLeft)
        NotificationHelper.show(applicationContext, title, body)
        WorkScheduler.enqueueRetryWorker(applicationContext)
        return Result.success()
    }
}
