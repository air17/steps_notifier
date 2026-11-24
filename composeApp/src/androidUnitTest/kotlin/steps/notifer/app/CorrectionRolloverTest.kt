package steps.notifer.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.time.ZonedDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class CorrectionRolloverTest {
    /** Same atomic update contract as DataStore.edit, with snapshots visible to assertions. */
    private class Store : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        val commits = mutableListOf<Preferences>()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it; commits.add(it) }
    }

    private val night = ZonedDateTime.parse("2026-10-26T03:30:00+01:00[Europe/Berlin]")

    @Test fun firstManualSaveCommitsHistoryAndOffsetTogetherAndRetryDoesNotReadAgain() = runBlocking {
        val store = Store()
        val yesterday = night.minusDays(1).stepDay()
        rollCorrection(store, -75, { night.minusDays(1) }) { _, _ -> null }
        assertTrue(rollCorrection(store, 42, { night }) { start, end ->
            assertEquals(yesterday.start, start)
            assertEquals(yesterday.end, end) // 25-hour DST day, not start + 24h
            1200L
        })
        val commit = store.commits.last()
        assertEquals(listOf(DaySteps(yesterday.date, 1125)), commit.history())
        assertEquals(42L, commit.correction(night.stepDay()))
        assertTrue(rollCorrection(store, null, { night }) { _, _ -> error("redundant HC read") })
    }

    @Test fun manualWinsWhenWorkerReadsCrossAConcurrentSave() = runBlocking {
        val store = Store()
        val started = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val worker = async {
            rollCorrection(store, null, { night }) { _, _ ->
                started.complete(Unit); resume.await(); 900L
            }
        }
        started.await()
        rollCorrection(store, -3, { night }) { _, _ -> 800L }
        resume.complete(Unit)
        assertTrue(worker.await())
        assertEquals(-3L, store.data.value.correction(night.stepDay()))
        assertEquals(800L, store.data.value.history().single().steps)
    }

    @Test fun unavailableYesterdayIsNotZeroAndLateRetryDoesNotReadTodaysLiveSummary() = runBlocking {
        val store = Store()
        assertFalse(rollCorrection(store, -33, { night.minusDays(1) }) { _, _ -> null })
        val late = night.plusHours(11)
        val yesterday = late.minusDays(1).stepDay()
        assertFalse(rollCorrection(store, null, { late }) { _, _ -> null })
        assertTrue(store.data.value.history().isEmpty())
        assertEquals(0L, store.data.value.correction(late.stepDay()))
        assertTrue(rollCorrection(store, null, { late }) { start, end ->
            assertEquals(yesterday.start, start); assertEquals(yesterday.end, end); 100L
        })
        assertEquals(67L, store.data.value.history().single().steps)
    }

    @Test fun summariesOnLaterPagesWinWithoutGranularDoubleCounting() = runBlocking {
        val seen = mutableListOf<String?>()
        val total = pagedStepTotal(read = { token ->
            seen.add(token)
            when (token) {
                null -> SummaryPage(null, "p2")
                "p2" -> SummaryPage(600, "p3")
                else -> SummaryPage(810, null)
            }
        }, aggregate = { error("must not aggregate a running daily summary") })
        assertEquals(listOf(null, "p2", "p3"), seen)
        assertEquals(810L, total)
    }

    @Test fun granularOnlyUsesAggregateAndPreservesMissingMetric() = runBlocking {
        suspend fun total(aggregate: Long?) = pagedStepTotal(
            read = { SummaryPage(null, if (it == null) "more" else null) },
            aggregate = { aggregate },
        )
        assertEquals(525L, total(525))
        assertEquals(0L, total(0))
        assertNull(total(null))
    }
}
