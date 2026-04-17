package steps.notifer.app
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
class StepGoalViewModel(application: Application) : AndroidViewModel(application) {
    private val context get() = getApplication<Application>()
    private val _hour = MutableStateFlow(18)
    val hour: StateFlow<Int> = _hour
    private val _minute = MutableStateFlow(0)
    val minute: StateFlow<Int> = _minute
    private val _stepThreshold = MutableStateFlow("10000")
    val stepThreshold: StateFlow<String> = _stepThreshold
    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved
    // Raw HC reading without correction applied (used for display of raw+corrected separately)
    private val _rawSteps = MutableStateFlow<Long?>(null)
    // Effective steps after applying correction (what is shown on screen and used in workers)
    private val _currentSteps = MutableStateFlow<Long?>(null)
    val currentSteps: StateFlow<Long?> = _currentSteps
    private val _stepsLoading = MutableStateFlow(false)
    val stepsLoading: StateFlow<Boolean> = _stepsLoading
    private val _healthConnectStatus = MutableStateFlow(HealthConnectHelper.Status.AVAILABLE)
    val healthConnectStatus: StateFlow<HealthConnectHelper.Status> = _healthConnectStatus
    private val _debugData = MutableStateFlow<String?>(null)
    val debugData: StateFlow<String?> = _debugData
    private val _debugLoading = MutableStateFlow(false)
    val debugLoading: StateFlow<Boolean> = _debugLoading
    // Steps correction: positive adds, negative subtracts.
    // Displayed as a signed integer string in the UI (e.g., "-150" or "0").
    private val _stepsCorrection = MutableStateFlow("0")
    val stepsCorrection: StateFlow<String> = _stepsCorrection
    val currentGoal: StateFlow<GoalData?> = context.goalFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun onHourChange(h: Int) { _hour.value = h }
    fun onMinuteChange(m: Int) { _minute.value = m }
    fun onStepThresholdChange(s: String) { _stepThreshold.value = s }
    fun onStepsCorrectionChange(s: String) { _stepsCorrection.value = s }
    fun saveAndSchedule() {
        val steps = _stepThreshold.value.toIntOrNull() ?: return
        viewModelScope.launch {
            context.saveGoal(_hour.value, _minute.value, steps)
            WorkScheduler.enqueueMainWorker(context, _hour.value, _minute.value)
            _saved.value = true
        }
    }
    /** Save the correction immediately when the user changes the field (on focus-loss or button). */
    fun saveCorrection() {
        val correction = _stepsCorrection.value.toLongOrNull() ?: return
        viewModelScope.launch {
            context.saveStepsCorrection(correction)
            // Re-compute the effective steps with the new correction
            val raw = _rawSteps.value
            if (raw != null) _currentSteps.value = raw + correction
        }
    }
    fun loadExisting() {
        viewModelScope.launch {
            val goal = context.goalFlow().firstOrNull()
            if (goal != null) {
                _hour.value = goal.hour
                _minute.value = goal.minute
                _stepThreshold.value = goal.steps.toString()
                _saved.value = true
            }
            // Load persisted correction value
            val correction = context.stepsCorrectionFlow().firstOrNull() ?: 0L
            _stepsCorrection.value = correction.toString()
            // Ensure the nightly correction worker is scheduled (idempotent via KEEP policy)
            ensureCorrectionWorkerScheduled()
        }
    }
    fun refreshSteps() {
        val status = HealthConnectHelper.getSdkStatus(context)
        _healthConnectStatus.value = status
        if (status != HealthConnectHelper.Status.AVAILABLE) return
        viewModelScope.launch {
            _stepsLoading.value = true
            try {
                val raw = HealthConnectHelper.getTodaySteps(context)
                _rawSteps.value = raw
                // Apply stored correction offset when displaying
                val correction = context.stepsCorrectionFlow().firstOrNull() ?: 0L
                _currentSteps.value = raw + correction
            } catch (e: Exception) {
                _currentSteps.value = null
            } finally {
                _stepsLoading.value = false
            }
        }
    }
    fun loadDebugData() {
        if (_healthConnectStatus.value != HealthConnectHelper.Status.AVAILABLE) return
        viewModelScope.launch {
            _debugLoading.value = true
            try {
                val report = HealthConnectHelper.getDebugReport(context)
                _debugData.value = report
                // Also copy to clipboard automatically
                copyToClipboard(report)
            } catch (e: Exception) {
                _debugData.value = "Error: ${e.message}"
            } finally {
                _debugLoading.value = false
            }
        }
    }
    fun dismissDebug() { _debugData.value = null }
    private fun copyToClipboard(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("HC Debug", text))
    }
    /**
     * Checks on a background thread whether the correction check worker is already enqueued.
     * Schedules it if not. Called from loadExisting() which runs on every screen open.
     */
    private fun ensureCorrectionWorkerScheduled() {
        viewModelScope.launch(Dispatchers.IO) {
            // isCorrectionWorkerScheduled uses blocking .get() — must run on IO dispatcher
            if (!WorkScheduler.isCorrectionWorkerScheduled(context)) {
                WorkScheduler.enqueueCorrectionCheckWorker(context)
            }
        }
    }
}
