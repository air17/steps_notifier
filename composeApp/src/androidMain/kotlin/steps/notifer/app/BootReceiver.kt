package steps.notifer.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val ctx = context.applicationContext
            runPending { WorkScheduler.repair(ctx) }
        }
    }
}

internal fun BroadcastReceiver.runPending(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try { block() } catch (e: Exception) {
            Log.e("StepSchedule", "Receiver could not complete schedule transition", e)
        } finally { pending.finish() }
    }
}
