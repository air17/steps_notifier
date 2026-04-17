package steps.notifer.app

object NotificationPhrases {

    val initial = listOf(
        "Time to step it up!",
        "Your daily goal is waiting!",
        "Let's get moving!",
        "Crush that step count!",
        "A quick walk finishes it!",
        "Almost there, keep going!",
        "Don't stop now!",
        "Lace up those shoes!",
        "Finish strong today!",
        "The finish line is close!",
        "Step to it!",
        "Your body will thank you!",
        "Just a little further!",
        "Time for a quick stroll!",
        "Let's hit that target!"
    )

    val retry = listOf(
        "Don't forget about your goal!",
        "Still falling short, let's go!",
        "Tick-tock, keep walking!",
        "The day isn't over yet!",
        "Get back on track!"
    )

    const val MIDNIGHT_FAILURE =
        "Midnight reached. Goal not met today. Rest up and try again tomorrow!"

    fun stepsBody(stepsLeft: Int, kmLeft: Double): String =
        "You have $stepsLeft steps left. It's just ${"%.2f".format(kmLeft)} km!"

    /** Kilometers = Steps × 0.000762 */
    fun stepsToKm(steps: Int): Double = steps * 0.000762
}
