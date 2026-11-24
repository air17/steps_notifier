package steps.notifer.app

import androidx.datastore.preferences.core.mutablePreferencesOf
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.*

class DayJournalTest {
    private val day = StepDay(LocalDate.parse("2026-03-08"), ZoneId.of("America/New_York"))
    private val next = day.copy(date = day.date.plusDays(1))
    private val prefs = mutablePreferencesOf()

    @Test fun manualFirstRolloverAndRacingWorkerUseDistinctOffsets() {
        prefs.install(day, -10, manual = false)
        prefs.install(day, -20, manual = true)
        val past = prefs.previousDayToRead(next)!!
        prefs.finishDay(past, 1000)
        prefs.install(next, 33, manual = true)
        prefs.install(next, -500, manual = false)
        prefs.finishDay(past, 2000) // a duplicate worker with a newer HC snapshot
        assertEquals(listOf(DaySteps(day.date, 980)), prefs.history())
        assertEquals(33L, prefs.correction(next))
        assertNull(prefs.previousDayToRead(next))
    }

    @Test fun missingPastReadingCanBeRetriedAfterInstallingManualCorrection() {
        prefs.install(day, -60, manual = true)
        prefs.install(next, -800, manual = true)
        assertTrue(prefs.history().isEmpty())
        prefs.finishDay(prefs.previousDayToRead(next)!!, 900)
        assertEquals(840L, prefs.history().single().steps)
    }

    @Test fun expiredCorrectionIsNotUsedOnMissedDayOrInOtherZone() {
        prefs.install(day, -100, manual = true)
        val later = next.copy(date = next.date.plusDays(1))
        assertNull(prefs.correction(day.copy(zone = ZoneId.of("UTC"))))
        assertNull(prefs.correction(next))
        prefs.finishDay(prefs.previousDayToRead(later)!!, 410)
        prefs.finishDay(prefs.previousDayToRead(next)!!, 200)
        assertEquals(listOf(DaySteps(next.date, 410), DaySteps(day.date, 100)), prefs.history())
    }

    @Test fun legacyOffsetHasNoKnownDayAndRetryMustNotGuess() {
        prefs[PrefsKeys.STEPS_CORRECTION] = -500
        assertNull(prefs.correction(day))
        assertNull(prefs.previousDayToRead(day))
        prefs.install(day, 0, manual = true)
        assertNull(prefs.previousDayToRead(day))
        assertEquals(day, prefs.previousDayToRead(next))
    }

    @Test fun travelReadsYesterdaysOriginalWindowAndOffset() {
        prefs.install(day, -45, manual = true)
        val target = next.copy(zone = ZoneId.of("Europe/Paris"))
        val past = prefs.previousDayToRead(target)!!
        assertEquals(day, past)
        assertEquals(23L, Duration.between(past.start, past.end).toHours())
        prefs.finishDay(past, 80)
        assertEquals(35L, prefs.history().single().steps)
    }

    @Test fun noLiveRunningTotalIsSampledAtLunch() {
        assertTrue(canSampleBaseline(LocalTime.of(3, 30)))
        assertFalse(canSampleBaseline(LocalTime.NOON))
        assertFalse(canSampleBaseline(LocalTime.of(23, 59)))
    }
}
