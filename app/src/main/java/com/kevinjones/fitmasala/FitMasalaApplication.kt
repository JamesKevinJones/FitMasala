package com.kevinjones.fitmasala

import android.app.Application
import android.util.Log
import com.kevinjones.fitmasala.data.photo.MealPhotoPruner
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hilt's application-scoped component root. Registered in the manifest as
 * android:name=".FitMasalaApplication".
 */
@HiltAndroidApp
class FitMasalaApplication : Application() {

    @Inject lateinit var photoPruner: MealPhotoPruner

    /** Work that outlives any screen and must never block app start. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            // Housekeeping: a failure here is retried next start, never a crash.
            try {
                photoPruner.prune()
            } catch (e: Exception) {
                Log.w(TAG, "Meal photo pruning failed; will retry next start", e)
            }
        }
    }

    private companion object {
        const val TAG = "FitMasala"
    }
}
