package steps.notifer.app
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
/**
 * Two-phase nightly worker that auto-sets the steps correction offset.
 *
 * Background: Some step sources (e.g. Samsung Health) keep a running "daily total"
 * that doesn't reset exactly at midnight. There are often a small number of residual
 * steps recorded between 00:00 and ~03:30 that actually belong to the previous day.
 *
 * To compensate, this worker fires in two phases each night:
 *  Phase 1 (2:00 AM) → just schedules Phase 2 with a 90-minute delay.
 *  Phase 2 (3:30 AM) → reads the current step count from Health Connect and
 *                       stores correction = -steps. From that point on, the
 *                       app's effective step count = HC_steps + correction,
 *                       which starts the "real" count from 0.
 *
 * After Phase 2 completes, Phase 1 is re-scheduled for the next night so the
 * process repeats every day automatically.
 */
class CorrectionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    companion object {
        /** WorkManager input data key that controls which phase runs. */
        const val KEY_PHASE = "correction_phase"
    }
    override suspend fun doWork(): Result {
        val phase = inputData.getInt(KEY_PHASE, 1)
        Log.d("CorrectionWorker", "Running phase $phase")
        return when (phase) {
            1 -> runPhase1()
            2 -> runPhase2()
            else -> Result.failure()
        }
    }
    /** Phase 1: fired at 2 AM. Just triggers Phase 2 90 minutes later. */
    private fun runPhase1(): Result {
        WorkScheduler.enqueueCorrectionFollowup(applicationContext)
        Log.d("CorrectionWorker", "Phase 1 done — Phase 2 scheduled at 3:30 AM")
        return Result.success()
    }
    /**
     * Phase 2: fired at 3:30 AM.
     * Reads the step count that Health Connect reports at this early hour.
     * Those steps are residual carry-over from the previous day, so we store
     * their negative as the correction.  For the rest of the day:
     *   displayedSteps = healthConnectSteps + correction
     * which effectively starts from 0 right after midnight.
     */
    private suspend fun runPhase2(): Result {
        val steps = try {
            HealthConnectHelper.getTodaySteps(applicationContext)
        } catch (e: Exception) {
            Log.e("CorrectionWorker", "Phase 2 failed to read steps: ${e.message}", e)
            if (runAttemptCount < 3) return Result.retry()
            // Give up — don't apply a potentially wrong correction
            scheduleNextNight()
            return Result.success()
        }
        val correction = -steps
        Log.d("CorrectionWorker", "Phase 2: steps at 3:30 AM = $steps → correction = $correction")
        applicationContext.saveStepsCorrection(correction)
        // Re-schedule Phase 1 for the next night so the cycle continues
        scheduleNextNight()
        return Result.success()
    }
    /** After Phase 2 finishes, enqueue tomorrow's Phase 1 so the nightly cycle continues. */
    private fun scheduleNextNight() {
        // enqueueCorrectionCheckWorker uses KEEP policy — since today's Phase 1 already ran
        // (its state is SUCCEEDED), there's no active enqueued work, so KEEP schedules a new one.
        WorkScheduler.enqueueCorrectionCheckWorker(applicationContext)
    }
}
