package com.jypony.tvzoom;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;

public final class ApplyJobService extends JobService {
    private static final String TAG = "TvZoomJob";
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
                        BootReceiver.scheduleBoot(ApplyJobService.this, 3000L);
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
        Log.i(TAG, "start job=" + jobId + " percent=" + percent);
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                AdbKeeper.ensureEnabled(ApplyJobService.this);
                ZoomController.Result result = ZoomController.apply(
                        ApplyJobService.this, percent);
                if (result.success) {
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
