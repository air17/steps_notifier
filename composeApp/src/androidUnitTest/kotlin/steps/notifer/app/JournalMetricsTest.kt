package steps.notifer.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JournalMetricsTest {
    @Test fun readingUsesSignedCorrectionWithoutWrapping() {
        assertEquals(4832L, correctedTotal(5000, -168))
        assertEquals(0L, correctedTotal(10, -50))
        assertEquals(Long.MAX_VALUE, correctedTotal(50, Long.MAX_VALUE))
    }

    @Test fun progressStopsAtGoalAndNeverInventsAMissingGoal() {
        assertNull(goalProgress(0, 0))
        assertEquals(GoalProgress(0.25f, 7500L), goalProgress(2500, 10000))
        assertEquals(GoalProgress(1f, 0L), goalProgress(Long.MAX_VALUE, 10000))
    }
}
