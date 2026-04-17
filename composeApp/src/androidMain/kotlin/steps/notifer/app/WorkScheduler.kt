package steps.notifer.app
import android.content.Context
import android.util.Log
import androidx.work.*
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
object WorkScheduler {
    const val MAIN_WORKER_TAG = "main_worker"
    const val RETRY_WORKER_TAG = "retry_worker"
    // Tag for the nightly correction workers (both phase 1 at 2 AM and phase 2 at 3:30 AM)
    const val CORRECTION_WORKER_TAG = "correction_worker"
    // Unique work names used with enqueueUniqueWork so that
    // re-scheduling replaces any existing enqueued instance.
    private const val MAIN_WORK_NAME = "main_worker"
    private const val CORRECTION_CHECK_NAME = "correction_check"
    private const val CORRECTION_FOLLOWUP_NAME = "correction_followup"
    /** Schedule the step-goal check to fire at [hour]:[minute] today or tomorrow (whichever is in the future). */
    fun enqueueMainWorker(context: Context, hour: Int, minute: Int) {
        val now = LocalDateTime.now()
        var target = LocalDate.now().atTime(LocalTime.of(hour, minute))
        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }
        val delay = Duration.between(now, target).toMillis()
        val request = OneTimeWorkRequestBuilder<MainStepWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(MAIN_WORKER_TAG)
            .build()
        // REPLACE: if the goal time is changed, the old pending worker is cancelled
        WorkManager.getInstance(context)
            .enqueueUniqueWork(MAIN_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
    /** Schedule a retry check 30 minutes from now. */
    fun enqueueRetryWorker(context: Context) {
        val request = OneTimeWorkRequestBuilder<RetryStepWorker>()
            .setInitialDelay(30, TimeUnit.MINUTES)
            .addTag(RETRY_WORKER_TAG)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(RETRY_WORKER_TAG, ExistingWorkPolicy.REPLACE, request)
    }
    fun cancelRetryWorkers(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(RETRY_WORKER_TAG)
    }
    /** Schedule the main worker explicitly for the next calendar day (used after goal is met or skipped). */
    fun enqueueNextDayMainWorker(context: Context, hour: Int, minute: Int) {
        val now = LocalDateTime.now()
        val target = LocalDate.now().plusDays(1).atTime(LocalTime.of(hour, minute))
        val delay = Duration.between(now, target).toMillis()
        val request = OneTimeWorkRequestBuilder<MainStepWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(MAIN_WORKER_TAG)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(MAIN_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
    /**
     * Schedule the nightly correction check (Phase 1) to run at 2 AM.
     * Phase 1 simply schedules Phase 2 at 3:30 AM.
     * Phase 2 reads the current HC step count and saves correction = -steps,
     * zeroing out any carry-over from the previous day.
     */
    fun enqueueCorrectionCheckWorker(context: Context) {
        val now = LocalDateTime.now()
        var target = LocalDate.now().atTime(2, 0)
        // If 2 AM already passed today, schedule for tomorrow
        if (!target.isAfter(now)) target = target.plusDays(1)
        val delay = Duration.between(now, target).toMillis()
        val request = OneTimeWorkRequestBuilder<CorrectionWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            // Input data signals phase 1 (the 2 AM trigger)
            .setInputData(workDataOf(CorrectionWorker.KEY_PHASE to 1))
            .addTag(CORRECTION_WORKER_TAG)
            .build()
        // KEEP: don't replace an already-scheduled correction check (avoid duplicate scheduling)
        WorkManager.getInstance(context)
            .enqueueUniqueWork(CORRECTION_CHECK_NAME, ExistingWorkPolicy.KEEP, request)
    }
    /**
     * Schedule the Phase 2 correction worker 90 minutes after Phase 1 fires (2 AM → 3:30 AM).
     * Called from within CorrectionWorker phase 1.
     */
    fun enqueueCorrectionFollowup(context: Context) {
        val request = OneTimeWorkRequestBuilder<CorrectionWorker>()
            .setInitialDelay(90, TimeUnit.MINUTES)
            .setInputData(workDataOf(CorrectionWorker.KEY_PHASE to 2))
            .addTag(CORRECTION_WORKER_TAG)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(CORRECTION_FOLLOWUP_NAME, ExistingWorkPolicy.REPLACE, request)
    }
    /**
     * Check whether the correction check worker is already enqueued or running.
     * Used on app open and boot to avoid double-scheduling.
     * NOTE: Blocks the calling thread — always call from a background dispatcher.
     */
    /**
     * Check whether the correction check worker is already enqueued or running.
     * Suspend function using work-runtime-ktx await() to avoid blocking.
     */
    suspend fun isCorrectionWorkerScheduled(context: Context): Boolean {
        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(CORRECTION_CHECK_NAME)
            .first()
        return infos.any { info ->
            info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.RUNNING
        }
    }
}
