package steps.notifer.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat

private val LauncherMint = Color(0xFF8DE8D0)
private val DeepMint = Color(0xFF246953)
private val Ink = Color(0xFF192622)
private val Paper = Color(0xFFFAF9F5)
private val Night = Color(0xFF151C1A)
private val PaleInk = Color(0xFFE7EAE3)
private val DayScheme = lightColorScheme(primary = DeepMint, onPrimary = Color.White,
    background = Paper, surface = Paper, onSurface = Ink, onBackground = Ink,
    surfaceContainerHigh = Color(0xFFF1F1EA), onSurfaceVariant = Color(0xFF526059),
    outline = Color(0xFF727C76), outlineVariant = Color(0xFFCCCEC6))
private val NightScheme = darkColorScheme(primary = LauncherMint, onPrimary = Ink,
    background = Night, surface = Night, onSurface = PaleInk, onBackground = PaleInk,
    surfaceContainerHigh = Color(0xFF272F2B), onSurfaceVariant = Color(0xFFAEBAB2),
    outline = Color(0xFF8C9991), outlineVariant = Color(0xFF445149))

@Composable
internal fun JournalPage(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) NightScheme else DayScheme) {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = 560.dp).fillMaxSize().padding(horizontal = 24.dp)) { content() }
            }
        }
    }
}

@Composable
internal fun Masthead(action: String, onClick: () -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(5.dp, 16.dp).background(LauncherMint))
        Text("WALKING JOURNAL", Modifier.weight(1f).padding(start = 10.dp),
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp)
        TextButton(onClick, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface)
}

@Composable
internal fun PageTitle(title: String) {
    Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.displayMedium,
        fontFamily = FontFamily.Serif, letterSpacing = (-1.2).sp)
}

@Composable
internal fun Note(text: String) = Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant,
    style = MaterialTheme.typography.bodyMedium)

@Composable
internal fun IndexRow(title: String, detail: String, onClick: () -> Unit) {
    HorizontalDivider()
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
        .heightIn(min = 64.dp).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = FontFamily.Serif, style = MaterialTheme.typography.titleLarge)
            Note(detail)
        }
        Text("→", Modifier.clearAndSetSemantics {}.padding(start = 12.dp),
            style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    }
}

internal fun Number.grouped(): String = NumberFormat.getIntegerInstance().format(this)
