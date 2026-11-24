package steps.notifer.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SkipReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val ctx = context.applicationContext
        val session = intent?.getStringExtra(WorkScheduler.KEY_SESSION)
        val day = intent?.getLongExtra(WorkScheduler.KEY_DAY, Long.MIN_VALUE) ?: Long.MIN_VALUE
        runPending { WorkScheduler.skip(ctx, session, day) }
    }
}
