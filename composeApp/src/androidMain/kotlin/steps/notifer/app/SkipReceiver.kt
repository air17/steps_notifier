package steps.notifer.app
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
/**
 * Handles the "Skip" action button on step-goal notifications.
 *
 * When the user taps Skip:
 *  1. All pending retry workers are cancelled (so no more 30-minute follow-up checks today).
 *  2. The current notification is dismissed.
 *  3. The next day's main worker is enqueued at the user's target time.
 *
 * This is a BroadcastReceiver; it must complete quickly. Heavy work (DataStore read) is
 * done on a background coroutine that immediately hands off to WorkManager.
 */
class SkipReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Cancel all retry workers tagged "retry_worker"
        WorkScheduler.cancelRetryWorkers(context)
        // Clear the notification from the shade
        NotificationHelper.cancel(context)
        // Enqueue next day's main worker (needs goal from DataStore — do on IO thread)
        CoroutineScope(Dispatchers.IO).launch {
            val goal = context.goalFlow().firstOrNull()
            if (goal != null) {
                WorkScheduler.enqueueNextDayMainWorker(context, goal.hour, goal.minute)
            }
        }
    }
}
