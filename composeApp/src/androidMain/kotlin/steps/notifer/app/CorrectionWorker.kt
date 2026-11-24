package steps.notifer.app

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.await
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException

/** Phase 1 arms the local 03:30 follow-up. Phase 2 shares the manual rollover transaction. */
class CorrectionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        const val KEY_PHASE = "correction_phase"
    }

    override suspend fun doWork(): Result {
        val now = ZonedDateTime.now()
        // Already-enqueued v1.0.1 workers have a phase but no scheduled date.
        val date = LocalDate.ofEpochDay(inputData.getLong(WorkScheduler.KEY_DAY, now.toLocalDate().toEpochDay()))
        return try {
            when (inputData.getInt(KEY_PHASE, 1)) {
                1 -> {
                    WorkScheduler.enqueueCorrectionFollowup(applicationContext, maxOf(date, now.toLocalDate())).await()
                    Result.success()
                }
                2 -> {
                    if (date > now.toLocalDate() || now.toLocalTime() < LocalTime.of(3, 30)) return Result.retry()
                    // After Doze, catch up the completed local day, not the stale scheduled date.
                    val complete = applicationContext.updateDailyCorrection(allowNeutralBaseline = runAttemptCount >= 3)
                    if (!complete && runAttemptCount < 3) return Result.retry()
                    WorkScheduler.enqueueCorrectionCheckWorker(applicationContext).await()
                    Result.success()
                }
                else -> Result.failure()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.w("CorrectionWorker", "Correction or scheduling transaction failed", e)
            Result.retry()
        }
    }
}
