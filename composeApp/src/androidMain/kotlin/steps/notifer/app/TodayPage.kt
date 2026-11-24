package steps.notifer.app

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun TodayPage(date: LocalDate, count: Long?, loading: Boolean, goal: GoalData?, time: String?,
    status: HealthConnectHelper.Status, refresh: () -> Unit, requestAccess: () -> Unit,
    history: () -> Unit, settings: () -> Unit, scroll: ScrollState = rememberScrollState()) {
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(bottom = 24.dp)) {
        Masthead(if (loading) "Reading…" else "Refresh", refresh, !loading)
        Spacer(Modifier.height(24.dp))
        PageTitle("Today")
        Note(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)))
        Spacer(Modifier.height(34.dp))
        if (count != null && !loading) {
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                BasicText(count.grouped(), Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.displayLarge.copy(fontFamily = FontFamily.Serif,
                        color = MaterialTheme.colorScheme.onSurface, letterSpacing = (-2).sp, lineHeight = 1.em),
                    autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 84.sp, stepSize = 2.sp),
                    maxLines = 1)
                Text("steps today", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Text(if (loading) "Reading steps…" else "Steps unavailable", fontFamily = FontFamily.Serif,
                style = MaterialTheme.typography.headlineMedium)
            if (!loading) {
                Note("Today’s count comes from Health Connect.")
                OutlinedButton(requestAccess) {
                    Text(when (status) {
                        HealthConnectHelper.Status.AVAILABLE -> "Check step access"
                        HealthConnectHelper.Status.NEEDS_UPDATE -> "Update Health Connect"
                        HealthConnectHelper.Status.NEEDS_INSTALL -> "Install Health Connect"
                    })
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        if (goal != null) {
            val progress = count?.takeUnless { loading }?.let { goalProgress(it, goal.steps) }
            progress?.let {
                Box(Modifier.fillMaxWidth().height(3.dp).progressSemantics(it.fraction)
                    .background(MaterialTheme.colorScheme.outlineVariant)) {
                    Box(Modifier.fillMaxWidth(it.fraction).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                }
                Spacer(Modifier.height(10.dp))
                Text(if (it.remaining == 0L) "Daily goal reached" else "${it.remaining.grouped()} steps to go",
                    style = MaterialTheme.typography.titleMedium)
            }
            Note("Goal ${goal.steps.grouped()} steps  ·  Check at $time")
        } else if (!loading) Note("No daily goal set")
        Spacer(Modifier.height(36.dp))
        IndexRow("History", "Past daily totals", history)
        IndexRow("Settings", "Goal, reminder and correction", settings)
        HorizontalDivider()
    }
}

@Preview(name = "Today · paper", showBackground = true, widthDp = 360, heightDp = 760)
@Composable private fun PaperPreview() = SampleToday(false)
@Preview(name = "Today · ink", showBackground = true, widthDp = 360, heightDp = 760)
@Composable private fun InkPreview() = SampleToday(true)
@Composable private fun SampleToday(dark: Boolean) = JournalPage(dark) {
    TodayPage(LocalDate.of(2026, 4, 18), 6432, false, GoalData(18, 0, 10000), "18:00",
        HealthConnectHelper.Status.AVAILABLE, {}, {}, {}, {})
}
