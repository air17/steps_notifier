package steps.notifer.app
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.time.LocalDate
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
    fun show(context: Context, title: String, body: String, showSkip: Boolean = true,
             session: String? = null, day: LocalDate? = null) {
        createChannel(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        if ((Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) ||
            !nm.areNotificationsEnabled() || nm.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) {
            Log.w("StepNotification", "Notification permission, app notifications or channel disabled")
            return
        }
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
        if (showSkip && session != null && day != null) {
            // Action is part of PendingIntent identity. A stale Skip cannot affect a new goal/day.
            val skipIntent = Intent(context, SkipReceiver::class.java).apply {
                action = "steps.notifer.app.SKIP.$session.$day"
                putExtra(WorkScheduler.KEY_SESSION, session)
                putExtra(WorkScheduler.KEY_DAY, day.toEpochDay())
            }
            val skipPending = PendingIntent.getBroadcast(
                context, 0, skipIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Skip", skipPending)
        }
        try {
            nm.notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            Log.w("StepNotification", "Permission changed before notification could be posted", e)
        }
    }
    fun cancel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(NOTIFICATION_ID)
    }
}
