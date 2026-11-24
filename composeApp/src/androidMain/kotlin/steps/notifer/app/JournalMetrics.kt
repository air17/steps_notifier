package steps.notifer.app

internal data class GoalProgress(val fraction: Float, val remaining: Long)

internal fun correctedTotal(raw: Long, offset: Long): Long =
    if (offset > 0 && raw > Long.MAX_VALUE - offset) Long.MAX_VALUE
    else (raw.coerceAtLeast(0L) + offset).coerceAtLeast(0L)

internal fun goalProgress(steps: Long, goal: Int): GoalProgress? {
    if (goal <= 0) return null
    val towardGoal = steps.coerceIn(0, goal.toLong())
    return GoalProgress(towardGoal.toFloat() / goal, goal - towardGoal)
}
