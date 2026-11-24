package steps.notifer.app

import android.content.Context
import androidx.work.WorkerParameters

class MainStepWorker(context: Context, params: WorkerParameters) :
    StepCheckWorker(context, params, isRetry = false)
