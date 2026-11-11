package com.jypony.tvzoom;

import android.content.Context;
import android.os.UserManager;
import android.util.Base64;

import com.cgutman.adblib.AdbBase64;
import com.cgutman.adblib.AdbConnection;
import com.cgutman.adblib.AdbCrypto;
import com.cgutman.adblib.AdbStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

final class ZoomController {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 5555;
    private static final String DEX = "/data/local/tmp/tvzoom.dex";
    private static final AdbBase64 BASE64 = new AdbBase64() {
        @Override
        public String encodeToString(byte[] data) {
            return Base64.encodeToString(data, Base64.NO_WRAP);
        }
    };

    private ZoomController() {}

    static final class Result {
        final boolean success;
        final String message;

        Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    static Result apply(Context context, int percent) {
        AdbConnection connection = null;
        try {
            AdbCrypto crypto = loadOrCreateCrypto(context);
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(HOST, PORT), 8000);
            socket.setSoTimeout(20000);
            connection = AdbConnection.create(socket, crypto);
            connection.connect();

            String action = percent >= 100
                    ? "reset"
                    : "set " + percent;
            String command = "CLASSPATH=" + DEX
                    + " app_process /system/bin tvzoom.TvZoom " + action
                    + "; echo __TVZOOM_EXIT:$?";
            AdbStream stream = connection.open("shell:" + command);
            StringBuilder output = new StringBuilder();
            try {
                while (true) {
                    byte[] chunk = stream.read();
                    output.append(new String(chunk, StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) {
                // The shell stream closes after the command exits.
            }
            String text = output.toString().trim();
            boolean success = text.contains("__TVZOOM_EXIT:0")
                    && (text.contains("displayRect=") || text.contains("reset to"));
            return new Result(success, text);
        } catch (Throwable t) {
            return new Result(false, t.toString());
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static synchronized AdbCrypto loadOrCreateCrypto(Context context)
            throws Exception {
        Context device = context.createDeviceProtectedStorageContext();
        File dir = new File(device.getFilesDir(), "adb");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("cannot create " + dir);
        }
        if (!new File(dir, "adbkey").isFile()) {
            UserManager users = context.getSystemService(UserManager.class);
            if (users == null || users.isUserUnlocked()) {
                copyKeyPair(new File(context.getFilesDir(), "adb"), dir);
            }
        }
        File privateKey = new File(dir, "adbkey");
        File publicKey = new File(dir, "adbkey.pub");
        if (privateKey.isFile() && publicKey.isFile()) {
            return AdbCrypto.loadAdbKeyPair(BASE64, privateKey, publicKey);
        }
        AdbCrypto crypto = AdbCrypto.generateAdbKeyPair(BASE64);
        crypto.saveAdbKeyPair(privateKey, publicKey);
        return crypto;
    }

    private static void copyKeyPair(File fromDir, File toDir) throws IOException {
        File fromPrivate = new File(fromDir, "adbkey");
        File fromPublic = new File(fromDir, "adbkey.pub");
        if (!fromPrivate.isFile() || !fromPublic.isFile()) {
            return;
        }
        if (!toDir.exists() && !toDir.mkdirs()) {
            throw new IOException("cannot create " + toDir);
        }
        copy(fromPrivate, new File(toDir, "adbkey"));
        copy(fromPublic, new File(toDir, "adbkey.pub"));
    }

    private static void copy(File from, File to) throws IOException {
        try (FileInputStream in = new FileInputStream(from);
             FileOutputStream out = new FileOutputStream(to)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }
}
