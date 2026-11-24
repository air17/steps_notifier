package steps.notifer.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StrongShakeDetectorTest {
    private val detector = StrongShakeDetector()

    private fun pulse(at: Long, peak: Float = 3f): Boolean {
        detector.sample(at - 20, 1f)
        return detector.sample(at, peak * peak)
    }

    @Test fun `walking and one sustained jolt do not trigger`() {
        for (t in 100L..1000L step 100) assertFalse(pulse(t, 1.7f))
        for (t in 1100L..2000L step 20) assertFalse(detector.sample(t, 9f))
    }

    @Test fun `three distinct strong pulses trigger once then cool down`() {
        assertFalse(pulse(100))
        assertFalse(pulse(300))
        assertTrue(pulse(500))
        for (t in 700L..2500L step 200) assertFalse(pulse(t))
        assertFalse(pulse(6000))
        assertFalse(pulse(6200))
        assertTrue(pulse(6400))
    }

    @Test fun `isolated pulses and rapid sensor chatter do not trigger`() {
        for (t in 100L..5100L step 1000) assertFalse(pulse(t))
        assertFalse(pulse(6000))
        assertFalse(pulse(6030))
        assertFalse(pulse(6060))
    }

    @Test fun `pause discards an incomplete gesture`() {
        assertFalse(pulse(100))
        assertFalse(pulse(300))
        detector.resetGesture()
        assertFalse(pulse(500))
        assertFalse(pulse(700))
        assertTrue(pulse(900))
    }
}
