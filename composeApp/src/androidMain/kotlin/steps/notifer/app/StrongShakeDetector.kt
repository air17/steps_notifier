package steps.notifer.app

/** Recognises three separate acceleration peaks rather than one sustained impact. */
internal class StrongShakeDetector {
    private var peaks = 0
    private var firstPeak = 0L
    private var lastPeak = 0L
    private var armed = true
    private var quietUntil = 0L

    fun resetGesture() {
        peaks = 0
        armed = true
    }

    /** [timeMs] is monotonic sensor time; [gSquared] includes gravity (1 at rest). */
    fun sample(timeMs: Long, gSquared: Float): Boolean {
        if (timeMs < quietUntil) return false
        if (gSquared <= 1.5f * 1.5f) armed = true
        if (!armed || gSquared < 2.5f * 2.5f) return false
        armed = false
        if (peaks != 0 && timeMs - lastPeak < 90) return false
        if (peaks == 0 || timeMs - firstPeak > 850) {
            firstPeak = timeMs
            peaks = 0
        }
        lastPeak = timeMs
        if (++peaks < 3) return false
        resetGesture()
        quietUntil = timeMs + 3000
        return true
    }
}
