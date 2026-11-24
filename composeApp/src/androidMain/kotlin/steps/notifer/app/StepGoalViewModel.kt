package steps.notifer.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

internal data class JournalSettings(val goal: GoalData?, val correction: Long)
internal enum class Setting { Goal, Correction }
internal data class SaveStatus(val field: Setting, val busy: Boolean, val message: String)

class StepGoalViewModel(application: Application) : AndroidViewModel(application) {
    private val context get() = getApplication<Application>()
    // These are persisted values, never editor drafts. Null until both reads finish.
    internal val settings = combine(context.goalFlow(), context.stepsCorrectionFlow(), ::JournalSettings)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val rawSteps = MutableStateFlow<Long?>(null)
    val currentSteps = combine(rawSteps, settings) { raw, saved ->
        if (raw != null && saved != null) correctedTotal(raw, saved.correction) else null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _stepsLoading = MutableStateFlow(true)
    val stepsLoading = _stepsLoading.asStateFlow()
    private val _healthConnectStatus = MutableStateFlow(HealthConnectHelper.Status.AVAILABLE)
    val healthConnectStatus = _healthConnectStatus.asStateFlow()
    private val _debugData = MutableStateFlow<String?>(null)
    val debugData = _debugData.asStateFlow()
    private val _debugLoading = MutableStateFlow(false)
    val debugLoading = _debugLoading.asStateFlow()
    private val _saveStatus = MutableStateFlow<SaveStatus?>(null)
    internal val saveStatus = _saveStatus.asStateFlow()
    private var refreshJob: Job? = null
    private var reportJob: Job? = null

    internal fun saveGoal(goal: GoalData) = save(Setting.Goal,
        "Goal saved. Reminder scheduled.", "Could not confirm the reminder. Try saving the goal again."
    ) { WorkScheduler.saveAndSchedule(context, goal) }

    internal fun saveCorrection(value: Long) = save(Setting.Correction,
        "Correction applied to today.", "Could not apply the correction. Try again."
    ) { context.saveStepsCorrection(value) }

    private fun save(field: Setting, success: String, failure: String, action: suspend () -> Unit) {
        if (_saveStatus.value?.busy == true) return
        _saveStatus.value = SaveStatus(field, true, "Saving…")
        viewModelScope.launch {
            val message = try {
                action()
                success
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e("StepSettings", "Could not finish saving $field", e)
                failure
            }
            _saveStatus.value = SaveStatus(field, false, message)
        }
    }

    internal fun clearFeedback() { if (_saveStatus.value?.busy != true) _saveStatus.value = null }

    fun refreshSteps() {
        refreshJob?.cancel()
        val status = HealthConnectHelper.getSdkStatus(context)
        _healthConnectStatus.value = status
        _stepsLoading.value = status == HealthConnectHelper.Status.AVAILABLE
        rawSteps.value = null
        if (!_stepsLoading.value) return
        refreshJob = viewModelScope.launch {
            rawSteps.value = try {
                HealthConnectHelper.getTodaySteps(context)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                null
            }
            _stepsLoading.value = false
        }
    }

    fun loadDebugData() {
        if (_debugLoading.value || _debugData.value != null) return
        _debugLoading.value = true
        reportJob = viewModelScope.launch {
            try {
                _debugData.value = HealthConnectHelper.getDebugReport(context)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _debugData.value = "Error: ${e.message}"
            } finally {
                _debugLoading.value = false
            }
        }
    }
    fun dismissDebug() {
        reportJob?.cancel()
        _debugData.value = null
        _debugLoading.value = false
    }

    fun repairSchedule() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                WorkScheduler.repair(context)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e("StepSchedule", "Could not repair scheduled work", e)
            }
        }
    }
}
