package steps.notifer.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun HistoryPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val history by remember(context) { context.historyFlow() }.collectAsStateWithLifecycle(initialValue = null)
    val days = history
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Masthead("Back", onBack)
            Spacer(Modifier.height(24.dp))
            PageTitle("History")
            Note("Completed days, newest first.")
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
        }
        when {
            days == null -> item { Spacer(Modifier.height(24.dp)); Note("Loading past days…") }
            days.isEmpty() -> item {
                Spacer(Modifier.height(24.dp))
                Text("No entries yet", fontFamily = FontFamily.Serif, style = MaterialTheme.typography.headlineSmall)
                Note("Daily totals appear here when a completed day is saved.")
            }
            else -> items(days, key = { it.date.toEpochDay() }) { day ->
                Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(vertical = 14.dp)) {
                    Text(day.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                        fontFamily = FontFamily.Serif, style = MaterialTheme.typography.titleLarge)
                    Note("${day.steps.grouped()} steps")
                }
                HorizontalDivider()
            }
        }
    }
}
