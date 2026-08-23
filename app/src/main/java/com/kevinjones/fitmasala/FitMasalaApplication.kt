package com.kevinjones.fitmasala

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Hilt's application-scoped component root. Registered in the manifest as
 * android:name=".FitMasalaApplication".
 */
@HiltAndroidApp
class FitMasalaApplication : Application()
