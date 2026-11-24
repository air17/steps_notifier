package steps.notifer.app

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** Calendar days identify checks; elapsed time determines a WorkManager delay. */
internal object StepSchedule {
    fun nextDay(now: ZonedDateTime, time: LocalTime): LocalDate {
        val today = now.toLocalDate()
        return if (today.atTime(time).atZone(now.zone).isAfter(now)) today else today.plusDays(1)
    }

    fun repairDay(planned: LocalDate, today: LocalDate) = maxOf(planned, today)

    fun delay(now: ZonedDateTime, day: LocalDate, time: LocalTime): Duration =
        maxOf(Duration.ZERO, Duration.between(now, day.atTime(time).atZone(now.zone)))

    fun retryDelay(now: ZonedDateTime): Duration = minOf(Duration.ofMinutes(30),
        Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(now.zone)))

    enum class Timing { EARLY, DUE, EXPIRED }

    fun timing(day: LocalDate, now: ZonedDateTime, time: LocalTime): Timing = when {
        day < now.toLocalDate() -> Timing.EXPIRED
        day > now.toLocalDate() || !delay(now, day, time).isZero -> Timing.EARLY
        else -> Timing.DUE
    }

    fun accepts(session: String, current: String?, day: LocalDate, skipped: Long?): Boolean =
        session == current && day.toEpochDay() != skipped

    fun reportExpired(day: LocalDate, now: ZonedDateTime, time: LocalTime): Boolean =
        now.toLocalDate() == day.plusDays(1) && now.toLocalTime() < time
}
