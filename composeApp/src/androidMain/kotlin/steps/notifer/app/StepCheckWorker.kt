package steps.notifer.app

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock

abstract class StepCheckWorker(context: Context, params: WorkerParameters, private val isRetry: Boolean) :
    CoroutineWorker(context, params) {
    final override suspend fun doWork(): Result = try {
        runCheck()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // DataStore or WorkManager commit failed. Never acknowledge a lost handoff.
        Log.e("StepCheck", "Could not complete schedule transition", e)
        Result.retry()
    }

    private suspend fun runCheck(): Result {
        val ctx = applicationContext
        val session = inputData.getString(WorkScheduler.KEY_SESSION)
        val epoch = inputData.getLong(WorkScheduler.KEY_DAY, Long.MIN_VALUE)
        if (session == null || epoch == Long.MIN_VALUE) {
            // Work enqueued by a previous app version has no reliable calendar identity.
            WorkScheduler.lock.withLock { WorkScheduler.repairMainLocked(ctx) }
            return Result.success()
        }
        val day = LocalDate.ofEpochDay(epoch)
        WorkScheduler.lock.withLock {
            val goal = WorkScheduler.currentGoalLocked(ctx, session, day) ?: return Result.success()
            val now = ZonedDateTime.now()
            when (StepSchedule.timing(day, now, goal.time)) {
                StepSchedule.Timing.EARLY -> return Result.retry() // clock moved backwards
                StepSchedule.Timing.EXPIRED -> {
                    if (!isRetry) WorkScheduler.repairMainLocked(ctx)
                    reportExpiry(day, goal, now)
                    return Result.success()
                }
                StepSchedule.Timing.DUE -> Unit
            }
            if (!isRetry) WorkScheduler.armTomorrowLocked(ctx, session, day, goal)
        }

        val effective = try {
            HealthConnectHelper.getTodaySteps(ctx) + ctx.stepsCorrectionFlow().first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("StepCheck", "Step count unavailable; retaining daily schedule", e)
            null // Unknown, not zero and not evidence that the goal was missed.
        }
        WorkScheduler.lock.withLock {
            // The user could have edited or skipped the goal while HC was reading.
            val goal = WorkScheduler.currentGoalLocked(ctx, session, day) ?: return Result.success()
            val now = ZonedDateTime.now()
            if (day != now.toLocalDate()) {
                reportExpiry(day, goal, now)
                return Result.success()
            }
            if (effective != null && effective >= goal.steps) {
                NotificationHelper.cancel(ctx)
                return Result.success()
            }
            WorkScheduler.enqueueRetry(ctx, session, day, id, effective != null, now)
            if (effective == null) {
                NotificationHelper.show(ctx, "Step Check Unavailable",
                    "Could not read steps. Open the app to check Health Connect and background read access.",
                    session = session, day = day)
            } else {
                val left = (goal.steps.toLong() - effective.coerceAtLeast(0)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                val title = (if (isRetry) NotificationPhrases.retry else NotificationPhrases.initial).random()
                NotificationHelper.show(ctx, title, NotificationPhrases.stepsBody(left, NotificationPhrases.stepsToKm(left)),
                    session = session, day = day)
            }
        }
        return Result.success()
    }

    private fun reportExpiry(day: LocalDate, goal: GoalData, now: ZonedDateTime) {
        if (isRetry && inputData.getBoolean(WorkScheduler.KEY_DEFICIT, false) &&
            StepSchedule.reportExpired(day, now, goal.time)) {
            NotificationHelper.show(applicationContext, "Goal Not Met", NotificationPhrases.MIDNIGHT_FAILURE, showSkip = false)
        }
    }
}
