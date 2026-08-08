package com.example.gupshup.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.gupshup.R

object ActivityTransitionUtil {
    fun applyFadeTransition(context: Context) {
        if (context is Activity) {
            if (Build.VERSION.SDK_INT >= 34) {
                context.overrideActivityTransition(
                    Activity.OVERRIDE_TRANSITION_OPEN,
                    R.anim.fade_in,
                    R.anim.fade_out
                )
            } else {
                @Suppress("DEPRECATION")
                context.overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            }
        }
    }

    fun applyFadeCloseTransition(context: Context) {
        if (context is Activity) {
            if (Build.VERSION.SDK_INT >= 34) {
                context.overrideActivityTransition(
                    Activity.OVERRIDE_TRANSITION_CLOSE,
                    R.anim.fade_in,
                    R.anim.fade_out
                )
            } else {
                @Suppress("DEPRECATION")
                context.overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            }
        }
    }
}

fun Activity.startActivityWithFade(intent: Intent) {
    startActivity(intent)
    ActivityTransitionUtil.applyFadeTransition(this)
}

fun Activity.finishWithFade() {
    finish()
    ActivityTransitionUtil.applyFadeCloseTransition(this)
}
