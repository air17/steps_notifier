package steps.notifer.app

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object WorkScheduler {
    const val MAIN_WORKER_TAG = "main_worker"
    const val RETRY_WORKER_TAG = "retry_worker"
    const val CORRECTION_WORKER_TAG = "correction_worker"
    const val KEY_DAY = "scheduled_epoch_day"
    internal const val KEY_SESSION = "goal_session"
    internal const val KEY_DEFICIT = "last_read_below_goal"
    private const val CORRECTION_CHECK_NAME = "correction_check"
    private const val CORRECTION_FOLLOWUP_NAME = "correction_followup"
    private val generation = stringPreferencesKey(KEY_SESSION)
    private val nextMain = longPreferencesKey("next_main_epoch_day")
    private val skipped = longPreferencesKey("skipped_epoch_day")

    // Single-process app. Serialize short scheduling/notification transitions, never HC reads.
    internal val lock = Mutex()

    suspend fun saveAndSchedule(context: Context, goal: GoalData) = lock.withLock {
        val now = ZonedDateTime.now()
        val date = StepSchedule.nextDay(now, goal.time)
        val session = UUID.randomUUID().toString()
        // Commit the goal and the desired schedule together. Repair can replay the intent
        // even if the process dies before WorkManager commits the new request.
        context.dataStore.edit {
            it[PrefsKeys.TARGET_HOUR] = goal.hour
            it[PrefsKeys.TARGET_MINUTE] = goal.minute
            it[PrefsKeys.STEP_THRESHOLD] = goal.steps
            it[generation] = session
            it[nextMain] = date.toEpochDay()
            it.remove(skipped)
        }
        val wm = WorkManager.getInstance(context)
        wm.cancelAllWorkByTag(RETRY_WORKER_TAG).await()
        wm.cancelAllWorkByTag(MAIN_WORKER_TAG).await()
        NotificationHelper.cancel(context)
        enqueueMain(context, session, date, goal.time)
    }

    suspend fun repair(context: Context) {
        enqueueCorrectionCheckWorker(context).await()
        lock.withLock { repairMainLocked(context) }
    }

    internal suspend fun repairMainLocked(context: Context) {
        val goal = context.goalFlow().first() ?: return
        val prefs = context.dataStore.data.first()
        val now = ZonedDateTime.now()
        val session = prefs[generation] ?: UUID.randomUUID().toString()
        // During migration preserve a legacy main already scheduled for tomorrow (e.g.
        // after Skip). Undated legacy retries cannot safely be interpreted after Doze.
        val planned = prefs[nextMain]?.let(LocalDate::ofEpochDay) ?: run {
            val oldMain = WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(MAIN_WORKER_TAG)
                .first().firstOrNull { it.state == WorkInfo.State.ENQUEUED }
            oldMain?.nextScheduleTimeMillis?.takeIf { it != Long.MAX_VALUE }?.let {
                java.time.Instant.ofEpochMilli(it).atZone(now.zone).toLocalDate()
            } ?: now.toLocalDate()
        }
        val date = StepSchedule.repairDay(planned, now.toLocalDate())
        if (prefs[generation] != session || prefs[nextMain] != date.toEpochDay()) {
            context.dataStore.edit { it[generation] = session; it[nextMain] = date.toEpochDay() }
        }
        enqueueMain(context, session, date, goal.time)
    }

    private suspend fun enqueueMain(context: Context, session: String, date: LocalDate, time: LocalTime) {
        val request = OneTimeWorkRequestBuilder<MainStepWorker>()
            .setInputData(workDataOf(KEY_SESSION to session, KEY_DAY to date.toEpochDay()))
            .setInitialDelay(StepSchedule.delay(ZonedDateTime.now(), date, time).toMillis(), TimeUnit.MILLISECONDS)
            .addTag(MAIN_WORKER_TAG).build()
        WorkManager.getInstance(context).enqueueUniqueWork("$MAIN_WORKER_TAG:$session:$date",
            ExistingWorkPolicy.KEEP, request).await()
    }

    internal suspend fun armTomorrowLocked(context: Context, session: String, day: LocalDate, goal: GoalData) {
        val tomorrow = day.plusDays(1)
        context.dataStore.edit { it[nextMain] = tomorrow.toEpochDay() }
        enqueueMain(context, session, tomorrow, goal.time)
    }

    internal suspend fun currentGoalLocked(context: Context, session: String, day: LocalDate): GoalData? {
        val prefs = context.dataStore.data.first()
        if (!StepSchedule.accepts(session, prefs[generation], day, prefs[skipped])) return null
        return context.goalFlow().first()
    }

    internal suspend fun enqueueRetry(context: Context, session: String, day: LocalDate, parent: UUID,
                                      deficit: Boolean, now: ZonedDateTime) {
        val request = OneTimeWorkRequestBuilder<RetryStepWorker>()
            .setInputData(workDataOf(KEY_SESSION to session, KEY_DAY to day.toEpochDay(), KEY_DEFICIT to deficit))
            .setInitialDelay(StepSchedule.retryDelay(now).toMillis(), TimeUnit.MILLISECONDS)
            .addTag(RETRY_WORKER_TAG).build()
        // Distinct from the executing retry, but idempotent if its parent is restarted.
        WorkManager.getInstance(context).enqueueUniqueWork("$RETRY_WORKER_TAG:$parent",
            ExistingWorkPolicy.KEEP, request).await()
    }

    internal suspend fun skip(context: Context, session: String?, day: Long) = lock.withLock {
        if (session == null || context.dataStore.data.first()[generation] != session ||
            day != LocalDate.now().toEpochDay()) return@withLock
        context.dataStore.edit { it[skipped] = day }
        WorkManager.getInstance(context).cancelAllWorkByTag(RETRY_WORKER_TAG).await()
        NotificationHelper.cancel(context)
        // Tomorrow is already armed before any actionable notification is posted.
    }

    /** Non-suspend API for CorrectionWorker. Callers can await the returned commit. */
    fun enqueueCorrectionCheckWorker(context: Context): Operation {
        val now = ZonedDateTime.now()
        val time = LocalTime.of(2, 0)
        val date = StepSchedule.nextDay(now, time)
        return enqueueCorrection(context, date, time, 1, CORRECTION_CHECK_NAME, now)
    }

    fun enqueueCorrectionFollowup(context: Context, date: LocalDate = LocalDate.now()): Operation =
        enqueueCorrection(context, date, LocalTime.of(3, 30), 2, CORRECTION_FOLLOWUP_NAME, ZonedDateTime.now())

    private fun enqueueCorrection(context: Context, date: LocalDate, time: LocalTime, phase: Int,
                                  name: String, now: ZonedDateTime): Operation {
        val request = OneTimeWorkRequestBuilder<CorrectionWorker>()
            .setInitialDelay(StepSchedule.delay(now, date, time).toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(CorrectionWorker.KEY_PHASE to phase, KEY_DAY to date.toEpochDay()))
            .addTag(CORRECTION_WORKER_TAG).build()
        // An overdue phase 1 and its immediate phase 2 can briefly overlap. A
        // date-specific name lets phase 2 arm tomorrow even if phase 1 is still RUNNING.
        return WorkManager.getInstance(context).enqueueUniqueWork("$name:$date", ExistingWorkPolicy.KEEP, request)
    }

    suspend fun isCorrectionWorkerScheduled(context: Context): Boolean = WorkManager.getInstance(context)
        .getWorkInfosByTagFlow(CORRECTION_WORKER_TAG).first().any { !it.state.isFinished }
}

internal val GoalData.time: LocalTime get() = LocalTime.of(hour, minute)
