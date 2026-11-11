package com.jypony.tvzoom;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

final class AdbKeeper {
    private static final String TAG = "TvZoomAdb";
    private static final String ADB_ENABLED = "adb_enabled";

    private AdbKeeper() {}

    static final class Result {
        final boolean success;
        final String message;

        Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    static Result ensureEnabled(Context context) {
        try {
            Settings.Global.putInt(
                    context.getContentResolver(), ADB_ENABLED, 1);
            int enabled = Settings.Global.getInt(
                    context.getContentResolver(), ADB_ENABLED, 0);
            if (enabled != 1) {
                return new Result(false, "adb_enabled=" + enabled);
            }
            Log.i(TAG, "adb_enabled=1");
            return new Result(true, "adb_enabled=1");
        } catch (SecurityException e) {
            Log.w(TAG, "cannot write adb_enabled", e);
            return new Result(false,
                    "缺少 WRITE_SECURE_SETTINGS，请执行一次 pm grant");
        } catch (Throwable t) {
            Log.w(TAG, "cannot enable adb", t);
            return new Result(false, t.toString());
        }
    }
}
