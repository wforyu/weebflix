package com.weebflix.app.ui.util

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration

object TvUtils {

    fun isTv(context: Context): Boolean {
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        if (uiMode == Configuration.UI_MODE_TYPE_TELEVISION) return true
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    fun forceLandscapeOnTv(activity: Activity) {
        if (isTv(activity)) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }
}
