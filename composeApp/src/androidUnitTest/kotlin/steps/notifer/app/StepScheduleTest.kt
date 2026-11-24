package steps.notifer.app

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StepScheduleTest {
    private val evening = LocalTime.of(18, 0)
    private val day = LocalDate.parse("2025-03-08")
    private fun now(value: String) = ZonedDateTime.parse(value)

    @Test fun editedGoalUsesNextFutureOccurrence() {
        assertEquals(day, StepSchedule.nextDay(now("2025-03-08T17:59Z"), evening))
        assertEquals(day.plusDays(1), StepSchedule.nextDay(now("2025-03-08T18:00Z"), evening))
    }

    @Test fun nextDailyCheckKeepsWallTimeAcrossSpringForward() {
        val start = now("2025-03-08T18:00-05:00[America/New_York]")
        assertEquals(Duration.ofHours(23), StepSchedule.delay(start, day.plusDays(1), evening))
    }

    @Test fun overdueMainCatchesUpTodayNotTomorrow() {
        val late = now("2025-03-12T20:00Z")
        val recovered = StepSchedule.repairDay(day, late.toLocalDate())
        assertEquals(late.toLocalDate(), recovered)
        assertEquals(Duration.ZERO, StepSchedule.delay(late, recovered, evening))
        assertEquals(day.plusDays(5), StepSchedule.repairDay(day.plusDays(5), late.toLocalDate()))
    }

    @Test fun retryIsDueAtMidnightIfThirtyMinutesWouldCrossIt() {
        assertEquals(Duration.ofMinutes(5), StepSchedule.retryDelay(now("2025-03-08T23:55Z")))
        assertEquals(Duration.ofMinutes(30), StepSchedule.retryDelay(now("2025-03-08T18:00Z")))
    }

    @Test fun dozePastAnotherTargetIsExpiredEvenThoughTimeOfDayLooksDue() {
        assertEquals(StepSchedule.Timing.EXPIRED, StepSchedule.timing(day, now("2025-03-09T19:00Z"), evening))
        assertEquals(StepSchedule.Timing.DUE, StepSchedule.timing(day, now("2025-03-08T19:00Z"), evening))
        assertEquals(StepSchedule.Timing.EARLY, StepSchedule.timing(day, now("2025-03-08T17:59Z"), evening))
    }

    @Test fun skipOrEditDuringReadInvalidatesResultWithoutSuppressingTomorrow() {
        assertFalse(StepSchedule.accepts("old", "edited", day, null))
        assertFalse(StepSchedule.accepts("same", "same", day, day.toEpochDay()))
        assertTrue(StepSchedule.accepts("same", "same", day.plusDays(1), day.toEpochDay()))
    }

    @Test fun delayedRetryDoesNotOverwriteNewGoalAlert() {
        assertTrue(StepSchedule.reportExpired(day, now("2025-03-09T00:12Z"), evening))
        assertFalse(StepSchedule.reportExpired(day, now("2025-03-09T18:00Z"), evening))
        assertFalse(StepSchedule.reportExpired(day, now("2025-03-11T10:00Z"), evening))
        assertFalse(StepSchedule.reportExpired(day, now("2025-03-09T00:00Z"), LocalTime.MIDNIGHT))
    }
}
