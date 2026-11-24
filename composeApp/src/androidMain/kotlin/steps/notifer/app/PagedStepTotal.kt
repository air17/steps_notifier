package steps.notifer.app

internal data class SummaryPage(val largestSummary: Long?, val next: String?)

/** Samsung running summaries must not be summed or interpolated; granular sensors must be
 * deduplicated by HC. A partial page set cannot establish which strategy is correct. */
internal suspend fun pagedStepTotal(
    read: suspend (String?) -> SummaryPage,
    aggregate: suspend () -> Long?,
): Long? {
    var token: String? = null
    var max: Long? = null
    do {
        val page = read(token)
        page.largestSummary?.let { max = maxOf(max ?: it, it) }
        token = page.next
    } while (token != null)
    return max ?: aggregate()
}
