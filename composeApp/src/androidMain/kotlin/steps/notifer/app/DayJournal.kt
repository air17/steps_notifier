package steps.notifer.app

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class DaySteps(val date: LocalDate, val steps: Long)

internal data class StepDay(val date: LocalDate, val zone: ZoneId) {
    val start get() = date.atStartOfDay(zone).toInstant()
    val end get() = date.plusDays(1).atStartOfDay(zone).toInstant()
}

internal fun ZonedDateTime.stepDay() = StepDay(toLocalDate(), zone)

// A late job cannot reconstruct a 03:30 Samsung running summary from its current count.
// Give WorkManager a small delivery window; otherwise leave legitimate activity untouched.
internal fun canSampleBaseline(time: LocalTime): Boolean =
    time >= LocalTime.of(3, 30) && time < LocalTime.of(3, 45)

private const val HISTORY = "journal.total."
private val firstKnownDay = longPreferencesKey("journal.first_known_day")
private fun offsetPrefix(date: LocalDate) = "journal.offset.${date.toEpochDay()}:"
private val StepDay.offsetKey get() = longPreferencesKey(offsetPrefix(date) + zone.id)
private fun historyKey(date: LocalDate) = longPreferencesKey(HISTORY + date.toEpochDay())

internal fun Preferences.correction(day: StepDay): Long? = this[day.offsetKey]

internal fun MutablePreferences.install(day: StepDay, offset: Long, manual: Boolean) {
    // v1.0.1 did not record a date, zone or source. The old offset might be weeks old.
    if (this[PrefsKeys.STEPS_CORRECTION] != null) {
        remove(PrefsKeys.STEPS_CORRECTION)
        this[firstKnownDay] = day.date.toEpochDay()
    }
    if (manual || correction(day) == null) this[day.offsetKey] = offset
}

internal fun Preferences.previousDayToRead(today: StepDay): StepDay? {
    val date = today.date.minusDays(1)
    if (this[historyKey(date)] != null || this[PrefsKeys.STEPS_CORRECTION] != null ||
        date.toEpochDay() < (this[firstKnownDay] ?: Long.MIN_VALUE)) return null

    val local = StepDay(date, today.zone)
    if (correction(local) != null) return local
    // After travel prefer the original day's boundaries. Do not guess between several
    // foreign zones. On a missed day no correction was installed: the effective offset was 0.
    val prefix = offsetPrefix(date)
    val zones = asMap().keys.map { it.name }.filter { it.startsWith(prefix) }
    return when (zones.size) {
        0 -> local
        1 -> StepDay(date, ZoneId.of(zones.single().removePrefix(prefix)))
        else -> null
    }
}

internal fun MutablePreferences.finishDay(day: StepDay, raw: Long) {
    val key = historyKey(day.date)
    if (this[key] != null) return
    val offset = correction(day) ?: 0L
    this[key] = if (offset > 0 && raw > Long.MAX_VALUE - offset) Long.MAX_VALUE
        else (raw + offset).coerceAtLeast(0L)
}

internal fun Preferences.history(): List<DaySteps> = asMap().mapNotNull { (key, value) ->
    if (!key.name.startsWith(HISTORY) || value !is Long) return@mapNotNull null
    DaySteps(LocalDate.ofEpochDay(key.name.removePrefix(HISTORY).toLong()), value)
}.sortedByDescending { it.date }
