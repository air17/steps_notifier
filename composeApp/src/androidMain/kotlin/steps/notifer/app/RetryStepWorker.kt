package steps.notifer.app

import android.content.Context
import androidx.work.WorkerParameters

class RetryStepWorker(context: Context, params: WorkerParameters) :
    StepCheckWorker(context, params, isRetry = true)
