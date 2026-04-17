package steps.notifer.app
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
/**
 * Receives the BOOT_COMPLETED broadcast so that all WorkManager tasks are
 * re-registered after a device restart.
 *
 * WorkManager normally survives reboots on its own, but on some devices or
 * after force-stops the scheduled workers can be lost. Re-enqueueing them here
 * (using KEEP/REPLACE policies inside WorkScheduler) is safe and idempotent:
 *  - Main step-goal worker: re-scheduled for the user's target time.
 *  - Correction check worker: only scheduled if not already present (KEEP policy).
 *
 * Requires android.permission.RECEIVE_BOOT_COMPLETED in the manifest.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        // BroadcastReceivers have a very short window to complete work.
        // We launch a coroutine on IO to do the DataStore read without blocking the main thread.
        // The coroutine is tied to the process lifetime (not an Activity/ViewModel), which is
        // acceptable here since we immediately delegate to WorkManager.
        CoroutineScope(Dispatchers.IO).launch {
            val goal = context.goalFlow().firstOrNull()
            if (goal != null) {
                // Re-queue the main step-goal worker (REPLACE policy ensures correctness)
                WorkScheduler.enqueueMainWorker(context, goal.hour, goal.minute)
            }
            // Re-queue the nightly correction check if it isn't already scheduled
            if (!WorkScheduler.isCorrectionWorkerScheduled(context)) {
                WorkScheduler.enqueueCorrectionCheckWorker(context)
            }
        }
    }
}
