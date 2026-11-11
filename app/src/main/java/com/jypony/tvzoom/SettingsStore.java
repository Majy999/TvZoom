package com.jypony.tvzoom;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.UserManager;

final class SettingsStore {
    private static final String PREFS = "tvzoom";
    private static final String KEY_PERCENT = "percent";
    private static final String KEY_RETRY = "retry";
    private static final String KEY_AUTO_RESTORE = "auto_restore";
    private static final String KEY_ADB_KEEP_ALIVE = "adb_keep_alive";

    private SettingsStore() {}

    private static SharedPreferences prefs(Context context) {
        return context.createDeviceProtectedStorageContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void migrateFromCredentialStorage(Context context) {
        SharedPreferences target = prefs(context);
        if (target.contains(KEY_PERCENT)) {
            return;
        }
        UserManager users = context.getSystemService(UserManager.class);
        if (users != null && !users.isUserUnlocked()) {
            return;
        }
        SharedPreferences legacy =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!legacy.contains(KEY_PERCENT)) {
            return;
        }
        target.edit()
                .putInt(KEY_PERCENT, legacy.getInt(KEY_PERCENT, 100))
                .putInt(KEY_RETRY, legacy.getInt(KEY_RETRY, 0))
                .putBoolean(KEY_AUTO_RESTORE,
                        legacy.getBoolean(KEY_AUTO_RESTORE, true))
                .putBoolean(KEY_ADB_KEEP_ALIVE,
                        legacy.getBoolean(KEY_ADB_KEEP_ALIVE, true))
                .commit();
    }

    static int getPercent(Context context) {
        return prefs(context).getInt(KEY_PERCENT, 100);
    }

    static void setPercent(Context context, int percent) {
        prefs(context).edit().putInt(KEY_PERCENT, percent).commit();
    }

    static boolean isAutoRestore(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_RESTORE, true);
    }

    static void setAutoRestore(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_AUTO_RESTORE, enabled).commit();
    }

    static boolean isAdbKeepAlive(Context context) {
        return prefs(context).getBoolean(KEY_ADB_KEEP_ALIVE, true);
    }

    static void setAdbKeepAlive(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ADB_KEEP_ALIVE, enabled).commit();
    }

    static int getRetry(Context context) {
        return prefs(context).getInt(KEY_RETRY, 0);
    }

    static void setRetry(Context context, int retry) {
        prefs(context).edit().putInt(KEY_RETRY, retry).commit();
    }

    static void resetRetry(Context context) {
        setRetry(context, 0);
    }
}
