package steps.notifer.app

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PersistableBundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate

private enum class Page { Today, History, Settings }

@Composable
fun App(viewModel: StepGoalViewModel = viewModel(), onRequestHealthPermissions: () -> Unit = {}) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val count by viewModel.currentSteps.collectAsStateWithLifecycle()
    val loading by viewModel.stepsLoading.collectAsStateWithLifecycle()
    val status by viewModel.healthConnectStatus.collectAsStateWithLifecycle()
    val feedback by viewModel.saveStatus.collectAsStateWithLifecycle()
    val report by viewModel.debugData.collectAsStateWithLifecycle()
    val reportLoading by viewModel.debugLoading.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val homeScroll = rememberScrollState()
    var page by rememberSaveable { mutableStateOf(Page.Today) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    val back = { focus.clearFocus(); pickingTime = false; page = Page.Today }
    BackHandler(enabled = page != Page.Today && !pickingTime && report == null) { back() }
    LifecycleResumeEffect(viewModel, context) {
        fun refreshDay() { date = LocalDate.now(); viewModel.refreshSteps() }
        refreshDay()
        viewModel.repairSchedule()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { refreshDay() }
        }
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })
        onPauseOrDispose { context.unregisterReceiver(receiver) }
    }
    ForegroundShakeEffect(enabled = !reportLoading && report == null && !pickingTime) {
        focus.clearFocus(); viewModel.loadDebugData()
    }
    JournalPage {
        when (page) {
            Page.Today -> TodayPage(date, count, loading || settings == null, settings?.goal,
                settings?.goal?.let { clockTime(it.hour, it.minute) }, status, viewModel::refreshSteps,
                requestAccess = {
                    if (status == HealthConnectHelper.Status.AVAILABLE) onRequestHealthPermissions()
                    else HealthConnectHelper.openHealthConnectPlayStore(context)
                }, history = { page = Page.History }, settings = { viewModel.clearFeedback(); page = Page.Settings }, scroll = homeScroll)
            Page.History -> HistoryPage(back)
            Page.Settings -> SettingsPage(settings, feedback, pickingTime, { pickingTime = it }, back,
                viewModel::clearFeedback, viewModel::saveGoal, viewModel::saveCorrection)
        }
        if (reportLoading || report != null) {
            val text = report
            AlertDialog(onDismissRequest = viewModel::dismissDebug,
                title = { Text("Health Connect debug report") },
                text = { Text(text ?: "Reading Health Connect…", Modifier.verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)) },
                confirmButton = { if (text != null) TextButton({
                    val clip = ClipData.newPlainText("Health Connect report", text)
                    clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
                    viewModel.dismissDebug()
                }) { Text("Copy report") } },
                dismissButton = { TextButton(viewModel::dismissDebug) { Text("Close") } })
        }
    }
}
