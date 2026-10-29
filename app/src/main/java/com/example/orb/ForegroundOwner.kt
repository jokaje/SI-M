package com.example.orb

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/** Application-wide foreground boundary; short activity gaps do not own BLE teardown. */
class ForegroundOwner(private val app: Application, private val changed: (Boolean) -> Unit) : Application.ActivityLifecycleCallbacks {
    private val handler = Handler(Looper.getMainLooper())
    private val started = mutableSetOf<Activity>()
    private var foreground = false
    private var generation = 0L
    init { app.registerActivityLifecycleCallbacks(this) }
    override fun onActivityStarted(activity: Activity) {
        started.add(activity)
        generation++
        if (!foreground) { foreground = true; changed(true) }
    }
    override fun onActivityStopped(activity: Activity) {
        started.remove(activity)
        if (started.isNotEmpty()) return
        val epoch = ++generation
        handler.postDelayed({
            if (epoch == generation && started.isEmpty() && foreground) {
                foreground = false
                changed(false)
            }
        }, 750)
    }
    fun close() { generation++; app.unregisterActivityLifecycleCallbacks(this) }
    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
