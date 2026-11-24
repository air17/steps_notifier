package steps.notifer.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZonedDateTime

/** Read outside the DataStore transaction; revalidate day, journal and offset in the edit.
 * Manual saves always install their value, even when HC is down. A worker may retry an
 * unavailable past reading using its still-persisted day-specific offset. */
internal suspend fun rollCorrection(
    store: DataStore<Preferences>,
    manual: Long?,
    now: () -> ZonedDateTime,
    allowNeutralBaseline: Boolean = false,
    read: suspend (Instant, Instant) -> Long?,
): Boolean {
    while (true) {
        val at = now()
        val today = at.stepDay()
        val snapshot = store.data.first()
        val existing = snapshot.correction(today)
        val offset = when {
            manual != null -> manual
            existing != null -> existing
            !canSampleBaseline(at.toLocalTime()) -> 0L // neutral offset, not a history entry
            else -> {
                val sample = read(today.start, at.toInstant())
                when {
                    !canSampleBaseline(now().toLocalTime()) -> 0L // live summary arrived late
                    sample != null -> -sample
                    allowNeutralBaseline -> 0L // exhausted retries; still close yesterday if possible
                    else -> null
                }
            }
        }
        val past = snapshot.previousDayToRead(today)
        // A west-to-east trip may make yesterday (in its original zone) still in progress.
        val raw = past?.takeIf { it.start < it.end && it.end <= at.toInstant() }
            ?.let { read(it.start, it.end) }
        var changedDay = false
        val updated = store.edit { prefs ->
            if (now().stepDay() != today) {
                changedDay = true
                return@edit
            }
            // No prospective correction exists yet; leave both halves for a later attempt.
            if (offset == null && prefs.correction(today) == null) return@edit
            if (past != null && raw != null && prefs.previousDayToRead(today) == past) {
                prefs.finishDay(past, raw)
            }
            if (offset != null) prefs.install(today, offset, manual != null)
        }
        if (!changedDay) return updated.correction(today) != null && updated.previousDayToRead(today) == null
        // Midnight/zone change during I/O: never relabel the stale reading with a new date.
    }
}
