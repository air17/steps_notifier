package steps.notifer.app
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
object NotificationHelper {
    const val CHANNEL_ID = "step_goal_channel"
    const val NOTIFICATION_ID = 1001
    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Step Goal Notifications",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications about your daily step goal"
        }
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }
    fun show(context: Context, title: String, body: String, showSkip: Boolean = true) {
        createChannel(context)
        // Tapping the notification opens MainActivity.
        // FLAG_IMMUTABLE is required on API 23+; FLAG_UPDATE_CURRENT reuses an existing PendingIntent.
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            // Attach the tap-to-open intent to the notification body
            .setContentIntent(openPending)
        if (showSkip) {
            // "Skip" action cancels the retry loop and schedules tomorrow's check
            val skipIntent = Intent(context, SkipReceiver::class.java)
            val skipPending = PendingIntent.getBroadcast(
                context, 0, skipIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Skip", skipPending)
        }
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, builder.build())
    }
    fun cancel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(NOTIFICATION_ID)
    }
}
