package com.appenza.smsapi

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context

/**
 * Optional safety net for a missed SMS broadcast. It is intentionally a system-scheduled
 * job rather than a foreground/background service, so it keeps no process or memory alive.
 */
object RecoveryScheduler {
    private const val JOB_ID = 20101
    private const val RECOVERY_INTERVAL_MILLIS = 6L * 60L * 60L * 1000L
    private const val FLEX_MILLIS = 60L * 60L * 1000L

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        val component = ComponentName(context, SmsRecoveryJobService::class.java)
        val job = JobInfo.Builder(JOB_ID, component)
            .setPeriodic(RECOVERY_INTERVAL_MILLIS, FLEX_MILLIS)
            .setPersisted(true)
            .build()
        scheduler.schedule(job)
    }

    fun cancel(context: Context) {
        context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
    }
}
