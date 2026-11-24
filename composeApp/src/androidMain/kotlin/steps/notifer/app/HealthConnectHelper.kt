package steps.notifer.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object HealthConnectHelper {

    enum class Status { AVAILABLE, NEEDS_INSTALL, NEEDS_UPDATE }

    fun getSdkStatus(context: Context): Status = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> Status.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Status.NEEDS_UPDATE
        else -> Status.NEEDS_INSTALL
    }

    fun isAvailable(context: Context) = getSdkStatus(context) == Status.AVAILABLE

    /** Opens Play Store / Health Connect onboarding to install or update. */
    fun openHealthConnectPlayStore(context: Context) {
        val uri = Uri.parse(
            "market://details?id=com.google.android.apps.healthdata" +
                    "&url=healthconnect%3A%2F%2Fonboarding"
        )
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    suspend fun getTodaySteps(context: Context): Long {
        val (start, now) = todayRange()
        return readTotal(context, start, now) ?: 0L
    }

    /** A missing aggregate means no measurement. Only live callers default it to zero. */
    internal suspend fun readTotal(context: Context, start: Instant, end: Instant): Long? {
        val client = HealthConnectClient.getOrCreate(context)
        val range = TimeRangeFilter.between(start, end)
        return pageSummaries(client, range, start, end) {
            client.aggregate(AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL), timeRangeFilter = range
            ))[StepsRecord.COUNT_TOTAL]
        }
    }

    private fun StepsRecord.isSummary(start: Instant, end: Instant): Boolean =
        startTime >= start && startTime < end && Duration.between(startTime, endTime).toHours() >= 20

    private suspend fun pageSummaries(
        client: HealthConnectClient, range: TimeRangeFilter, start: Instant, end: Instant,
        onRecords: (List<StepsRecord>) -> Unit = {}, aggregate: suspend () -> Long?,
    ): Long? = pagedStepTotal(read = { token ->
        val page = client.readRecords(ReadRecordsRequest(
            recordType = StepsRecord::class, timeRangeFilter = range, pageToken = token
        ))
        onRecords(page.records)
        // A running summary which started yesterday isn't today's live total.
        val max = page.records.asSequence().filter { it.isSummary(start, end) }.maxOfOrNull { it.count }
        SummaryPage(max, page.pageToken)
    }, aggregate = aggregate)

    /** Full debug report copied to clipboard when the 🐛 button is pressed. */
    suspend fun getDebugReport(context: Context): String {
        val client = HealthConnectClient.getOrCreate(context)
        val (startOfDay, now) = todayRange()
        val tz = ZoneId.systemDefault()
        val fmt = DateTimeFormatter.ofPattern("HH:mm:ss")

        val sb = StringBuilder()
        sb.appendLine("=== Health Connect Step Debug ===")
        sb.appendLine("Date      : ${LocalDate.now()}")
        sb.appendLine("Timezone  : $tz")
        sb.appendLine("Window    : ${ZonedDateTime.ofInstant(startOfDay, tz).format(fmt)}" +
                " → ${ZonedDateTime.ofInstant(now, tz).format(fmt)}")
        sb.appendLine()

        // 1. Single aggregate
        val singleAgg = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startOfDay, now)
            )
        )
        val aggTotal = singleAgg[StepsRecord.COUNT_TOTAL] ?: 0L
        sb.appendLine("--- Aggregate (single call) ---")
        sb.appendLine("Total: $aggTotal steps")
        sb.appendLine()

        // 2. 30-min bucket aggregate
        val buckets = client.aggregateGroupByDuration(
            AggregateGroupByDurationRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startOfDay, now),
                timeRangeSlicer = Duration.ofMinutes(30)
            )
        )
        val bucketTotal = buckets.sumOf { b -> b.result[StepsRecord.COUNT_TOTAL] ?: 0L }
        sb.appendLine("--- Aggregate (30-min buckets) ---")
        sb.appendLine("Total: $bucketTotal steps")
        sb.appendLine("Buckets (non-zero only):")
        buckets
            .filter { b -> (b.result[StepsRecord.COUNT_TOTAL] ?: 0L) > 0 }
            .forEach { b ->
                val s = ZonedDateTime.ofInstant(b.startTime, tz).format(fmt)
                val e = ZonedDateTime.ofInstant(b.endTime, tz).format(fmt)
                sb.appendLine("  $s–$e : ${b.result[StepsRecord.COUNT_TOTAL]} steps")
            }
        sb.appendLine()

        // 3. Raw records
        val records = mutableListOf<StepsRecord>()
        pageSummaries(client, TimeRangeFilter.between(startOfDay, now), startOfDay, now,
            onRecords = { records.addAll(it) }, aggregate = { aggTotal })
        val rawSum = records.sumOf { r -> r.count }

        val dailySummaries = records.filter { it.isSummary(startOfDay, now) }
        sb.appendLine("--- Raw Records (${records.size} total, sum=$rawSum) ---")
        sb.appendLine("Daily-summary records (>=20 h): ${dailySummaries.size}")
        if (dailySummaries.isNotEmpty()) {
            sb.appendLine(">>> USING daily-summary max = ${dailySummaries.maxOf { r -> r.count }} <<<")
        } else {
            sb.appendLine(">>> USING single aggregate = $aggTotal <<<")
        }
        sb.appendLine()

        val bySource = records.groupBy { r -> r.metadata.dataOrigin.packageName }
        bySource.forEach { (pkg, recs) ->
            sb.appendLine("  Source: $pkg")
            sb.appendLine("  Records: ${recs.size}, Sum: ${recs.sumOf { r -> r.count }}")
            recs.sortedBy { r -> r.startTime }.forEach { r ->
                val s = ZonedDateTime.ofInstant(r.startTime, tz).format(fmt)
                val e = ZonedDateTime.ofInstant(r.endTime, tz).format(fmt)
                val tag = if (r.isSummary(startOfDay, now))
                    " [DAILY SUMMARY]" else ""
                sb.appendLine("    $s–$e : ${r.count}$tag")
            }
        }

        return sb.toString()
    }

    private fun todayRange(): Pair<Instant, Instant> {
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val startOfDay = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
        val endTime = maxOf(now, startOfDay.plusSeconds(1))
        return startOfDay to endTime
    }
}
