package com.jypony.tvzoom;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "TvZoomBoot";
    private static final int BOOT_JOB_ID = 7301;
    private static final int WATCHDOG_JOB_ID = 7302;
    private static final int ADB_BOOT_JOB_ID = 7310;
    private static final int ADB_WATCHDOG_JOB_ID = 7311;
    private static final int ADB_RETRY_JOB_BASE = 7320;
    private static final long[] ADB_BOOT_DELAYS_MS = {5000L, 20000L, 60000L, 180000L};
    private static final long BOOT_DELAY_MS = 10000L;
    private static final long WATCHDOG_PERIOD_MS = 15L * 60L * 1000L;
    private static final long ADB_WATCHDOG_PERIOD_MS = 15L * 60L * 1000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i(TAG, "received " + action);
        if (!isBootAction(action)) {
            return;
        }
        if (!Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
            SettingsStore.migrateFromCredentialStorage(context);
        }
        if (SettingsStore.isAutoRestore(context)) {
            SettingsStore.resetRetry(context);
            scheduleBoot(context, BOOT_DELAY_MS);
            scheduleWatchdog(context);
        } else {
            Log.i(TAG, "auto restore disabled; cancelling scale jobs");
            cancelScaleJobs(context);
        }
        scheduleAdbKeeper(context);
    }

    private static boolean isBootAction(String action) {
        return Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_USER_UNLOCKED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "android.intent.action.HTC_QUICKBOOT_POWERON".equals(action);
    }

    static void schedule(Context context, long delayMs) {
        scheduleBoot(context, delayMs);
    }

    static void scheduleBoot(Context context, long delayMs) {
        if (!SettingsStore.isAutoRestore(context)) {
            return;
        }
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) {
            return;
        }
        JobInfo.Builder builder = new JobInfo.Builder(
                BOOT_JOB_ID, new ComponentName(context, ApplyJobService.class))
                .setMinimumLatency(delayMs)
                .setOverrideDeadline(delayMs + 120000L)
                .setPersisted(true)
                .setBackoffCriteria(30000L, JobInfo.BACKOFF_POLICY_LINEAR);
        int result = scheduler.schedule(builder.build());
        Log.i(TAG, "schedule boot job delay=" + delayMs + " result=" + result);
    }

    static void scheduleWatchdog(Context context) {
        if (!SettingsStore.isAutoRestore(context)
                || SettingsStore.getPercent(context) >= 100) {
            cancelWatchdog(context);
            return;
        }
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) {
            return;
        }
        JobInfo.Builder builder = new JobInfo.Builder(
                WATCHDOG_JOB_ID, new ComponentName(context, ApplyJobService.class))
                .setPeriodic(WATCHDOG_PERIOD_MS)
                .setPersisted(true)
                .setBackoffCriteria(60000L, JobInfo.BACKOFF_POLICY_LINEAR);
        int result = scheduler.schedule(builder.build());
        Log.i(TAG, "schedule watchdog result=" + result);
    }

    static void scheduleAdbKeeper(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) {
            return;
        }
        if (!SettingsStore.isAdbKeepAlive(context)) {
            cancelAdbKeeper(context);
            return;
        }
        for (int i = 0; i < ADB_BOOT_DELAYS_MS.length; i++) {
            long delay = ADB_BOOT_DELAYS_MS[i];
            JobInfo job = new JobInfo.Builder(
                    ADB_RETRY_JOB_BASE + i,
                    new ComponentName(context, ApplyJobService.class))
                    .setMinimumLatency(delay)
                    .setOverrideDeadline(delay + 120000L)
                    .setPersisted(true)
                    .build();
            int result = scheduler.schedule(job);
            Log.i(TAG, "schedule adb retry delay=" + delay + " result=" + result);
        }
        JobInfo watchdog = new JobInfo.Builder(
                ADB_WATCHDOG_JOB_ID,
                new ComponentName(context, ApplyJobService.class))
                .setPeriodic(ADB_WATCHDOG_PERIOD_MS)
                .setPersisted(true)
                .build();
        int result = scheduler.schedule(watchdog);
        Log.i(TAG, "schedule adb watchdog result=" + result);
    }

    static void cancelAdbKeeper(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) {
            return;
        }
        scheduler.cancel(ADB_WATCHDOG_JOB_ID);
        for (int i = 0; i < ADB_BOOT_DELAYS_MS.length; i++) {
            scheduler.cancel(ADB_RETRY_JOB_BASE + i);
        }
    }

    static void cancelWatchdog(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler != null) {
            scheduler.cancel(WATCHDOG_JOB_ID);
        }
    }

    static void cancelScaleJobs(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler != null) {
            scheduler.cancel(BOOT_JOB_ID);
            scheduler.cancel(WATCHDOG_JOB_ID);
        }
    }

    static boolean isAdbJob(int jobId) {
        return jobId == ADB_WATCHDOG_JOB_ID
                || (jobId >= ADB_RETRY_JOB_BASE
                && jobId < ADB_RETRY_JOB_BASE + ADB_BOOT_DELAYS_MS.length);
    }
}
