package com.jypony.tvzoom;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

public final class ApplyJobService extends JobService {
    private static final String TAG = "TvZoomJob";
    private static final AtomicBoolean SCALE_APPLYING = new AtomicBoolean(false);
    private volatile Thread worker;

    @Override
    public boolean onStartJob(final JobParameters params) {
        final int jobId = params.getJobId();
        if (BootReceiver.isAdbJob(jobId)) {
            Log.i(TAG, "start adb job=" + jobId);
            worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    AdbKeeper.Result result = AdbKeeper.ensureEnabled(ApplyJobService.this);
                    Log.i(TAG, "adb job=" + jobId + " success=" + result.success
                            + " " + result.message);
                    if (SettingsStore.isAutoRestore(ApplyJobService.this)
                            && SettingsStore.getPercent(ApplyJobService.this) < 100) {
                        BootReceiver.scheduleScaleRetry(
                                ApplyJobService.this, jobId, 3000L);
                    }
                    jobFinished(params, false);
                }
            }, "tvzoom-adb");
            worker.start();
            return true;
        }
        if (!SettingsStore.isAutoRestore(this)) {
            Log.i(TAG, "auto restore disabled");
            jobFinished(params, false);
            return false;
        }
        final int percent = SettingsStore.getPercent(this);
        final int offsetX = SettingsStore.getOffsetX(this);
        final int offsetY = SettingsStore.getOffsetY(this);
        if (!SCALE_APPLYING.compareAndSet(false, true)) {
            Log.i(TAG, "scale apply already running; skip job=" + jobId);
            jobFinished(params, false);
            return false;
        }
        Log.i(TAG, "start job=" + jobId + " percent=" + percent
                + " offset=" + offsetX + "," + offsetY);
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    AdbKeeper.ensureEnabled(ApplyJobService.this);
                    ZoomController.Result result = ZoomController.apply(
                            ApplyJobService.this, percent, offsetX, offsetY);
                    if (result.success) {
                        if (percent >= 100) {
                            SettingsStore.clearOffsets(ApplyJobService.this);
                        } else {
                            SettingsStore.setOffsets(
                                    ApplyJobService.this,
                                    result.offsetX, result.offsetY);
                        }
                        SettingsStore.resetRetry(ApplyJobService.this);
                        Log.i(TAG, "applied " + percent + "% job=" + jobId);
                        jobFinished(params, false);
                        return;
                    }
                    int retry = SettingsStore.getRetry(ApplyJobService.this) + 1;
                    SettingsStore.setRetry(ApplyJobService.this, retry);
                    Log.w(TAG, "apply failed (" + retry + ") job=" + jobId
                            + ": " + result.message);
                    jobFinished(params, retry < 10);
                } finally {
                    SCALE_APPLYING.set(false);
                }
            }
        }, "tvzoom-apply");
        worker.start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Thread thread = worker;
        if (thread != null) {
            thread.interrupt();
        }
        return true;
    }
}
