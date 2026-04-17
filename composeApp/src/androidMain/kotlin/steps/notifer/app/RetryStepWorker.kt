package steps.notifer.app
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.firstOrNull
import java.time.LocalTime
/**
 * Runs every 30 minutes after the main check if the step goal was not met.
 *
 * The loop terminates when:
 *  - Goal is met (auto-detect) → cancel notification, schedule next day's main worker.
 *  - Current time is before the user's target time (i.e., we've rolled past midnight) →
 *    show failure notification, schedule next day.
 *  - User taps "Skip" on any notification → SkipReceiver cancels these workers.
 *
 * Steps correction is applied the same way as in MainStepWorker.
 */
class RetryStepWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val goal = applicationContext.goalFlow().firstOrNull() ?: return Result.failure()
        val now = LocalTime.now()
        // The retry loop is meant to run between T and midnight. If the current time is
        // *before* the target time, it means we've just crossed midnight, so the day is over.
        val targetTime = LocalTime.of(goal.hour, goal.minute)
        if (now.isBefore(targetTime)) {
            NotificationHelper.show(
                applicationContext,
                "Goal Not Met",
                NotificationPhrases.MIDNIGHT_FAILURE,
                showSkip = false
            )
            WorkScheduler.enqueueNextDayMainWorker(applicationContext, goal.hour, goal.minute)
            return Result.success()
        }
        // Read raw steps from Health Connect
        val rawSteps = try {
            HealthConnectHelper.getTodaySteps(applicationContext)
        } catch (e: Exception) {
            Log.e("RetryStepWorker", "Failed to read steps: ${e::class.simpleName}: ${e.message}", e)
            if (runAttemptCount < 3) return Result.retry()
            // Can't read steps — keep the retry loop going rather than silently stopping
            WorkScheduler.enqueueRetryWorker(applicationContext)
            return Result.success()
        }
        // Apply correction offset (same logic as MainStepWorker)
        val correction = applicationContext.stepsCorrectionFlow().firstOrNull() ?: 0L
        val currentSteps = rawSteps + correction
        Log.d("RetryStepWorker", "Steps today: raw=$rawSteps correction=$correction effective=$currentSteps / goal=${goal.steps}")
        if (currentSteps >= goal.steps) {
            // Goal was met between retries — dismiss notification and stop the loop
            NotificationHelper.cancel(applicationContext)
            WorkScheduler.enqueueNextDayMainWorker(applicationContext, goal.hour, goal.minute)
            return Result.success()
        }
        // Still not met — show a retry-flavoured notification and schedule the next retry
        val stepsLeft = (goal.steps - currentSteps).toInt()
        val kmLeft = NotificationPhrases.stepsToKm(stepsLeft)
        val title = NotificationPhrases.retry.random()
        val body = NotificationPhrases.stepsBody(stepsLeft, kmLeft)
        NotificationHelper.show(applicationContext, title, body)
        WorkScheduler.enqueueRetryWorker(applicationContext)
        return Result.success()
    }
}
