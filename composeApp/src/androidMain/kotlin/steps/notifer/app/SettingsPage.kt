package steps.notifer.app

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Calendar

@Composable
internal fun clockTime(hour: Int, minute: Int): String = DateFormat.getTimeFormat(LocalContext.current)
    .format(Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute) }.time)

@Composable
internal fun SettingsPage(saved: JournalSettings?, status: SaveStatus?, pickingTime: Boolean,
    onPick: (Boolean) -> Unit, onBack: () -> Unit, onEdit: () -> Unit,
    saveGoal: (GoalData) -> Unit, saveCorrection: (Long) -> Unit) {
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        Masthead("Back", onBack)
        Spacer(Modifier.height(24.dp))
        PageTitle("Settings")
        if (saved == null) {
            Note("Loading settings…")
            return@Column
        }
        val goal = saved.goal
        var target by rememberSaveable(goal?.steps) { mutableStateOf((goal?.steps ?: 10000).toString()) }
        var hour by rememberSaveable(goal?.hour) { mutableIntStateOf(goal?.hour ?: 18) }
        var minute by rememberSaveable(goal?.minute) { mutableIntStateOf(goal?.minute ?: 0) }
        var correction by rememberSaveable(saved.correction) { mutableStateOf(saved.correction.toString()) }
        val targetValue = target.toIntOrNull()?.takeIf { it > 0 }
        val offset = correction.toLongOrNull()
        val canEdit = status?.busy != true
        if (pickingTime) {
            val theme = if (isSystemInDarkTheme()) android.R.style.Theme_Material_Dialog_Alert
                else android.R.style.Theme_Material_Light_Dialog_Alert
            DisposableEffect(context, theme) {
                val dialog = TimePickerDialog(context, theme, { _, h, m -> hour = h; minute = m; onEdit() },
                    hour, minute, DateFormat.is24HourFormat(context))
                dialog.setOnDismissListener { onPick(false) }
                dialog.show()
                onDispose { dialog.setOnDismissListener(null); dialog.dismiss() }
            }
        }
        SectionHeading("Daily reminder")
        Note("Check your steps at this time and remind you if you’re below goal.")
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(target, { target = it; onEdit() }, Modifier.fillMaxWidth(), enabled = canEdit,
            label = { Text("Daily goal in steps") }, singleLine = true, isError = targetValue == null,
            supportingText = if (targetValue == null) {{ Text("Enter a positive whole number up to ${Int.MAX_VALUE.grouped()}.") }} else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
        TextButton({ focus.clearFocus(); onPick(true) }, enabled = canEdit) {
            Text("Change check time  ·  ${clockTime(hour, minute)}")
        }
        Button({ targetValue?.let { focus.clearFocus(); saveGoal(GoalData(hour, minute, it)) } },
            enabled = canEdit && targetValue != null) { Text("Save goal and time") }
        Feedback(status, Setting.Goal)
        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        SectionHeading("Today’s correction")
        Note("This offset adjusts the Health Connect count for today only. Use a minus sign to subtract steps, or 0 for no adjustment.")
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(correction, { correction = it; onEdit() }, Modifier.fillMaxWidth(), enabled = canEdit,
            singleLine = true, label = { Text("Step correction") }, isError = offset == null,
            supportingText = if (offset == null) {{ Text("Enter a signed whole number, such as -150.") }} else null,
            // Numeric IMEs do not consistently offer a sign key.
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
        Spacer(Modifier.height(8.dp))
        OutlinedButton({ offset?.let { focus.clearFocus(); saveCorrection(it) } }, enabled = canEdit && offset != null) {
            Text("Apply correction")
        }
        Feedback(status, Setting.Correction)
    }
}

@Composable private fun SectionHeading(text: String) {
    Spacer(Modifier.height(24.dp))
    Text(text, Modifier.semantics { heading() }, fontFamily = FontFamily.Serif,
        style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(4.dp))
}

@Composable private fun Feedback(status: SaveStatus?, field: Setting) {
    if (field == status?.field) Text(status.message, Modifier.padding(top = 6.dp)
        .semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
}
