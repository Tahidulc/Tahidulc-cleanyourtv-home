package com.gathin.tvhomephone;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Why the app last closed unexpectedly, kept on this phone so the family can pass it on: the kind of error and where
 * in the app it happened (the classes and lines), never the error's own words, which could hold a stream's address or
 * a login. The next time the app opens it says so once, with a button to copy the details.
 */
public final class CrashNote {
    private CrashNote() {}

    private static final String PREFS = "crash_note";
    private static final int FRAMES = 14, CAUSES = 3;

    /** Noted before the phone's own handling (which closes the app as before). */
    static void install(Context c) {
        Context app = c.getApplicationContext();
        Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                prefs(app).edit().putString("note", describe(t.getName(), e, BuildConfig.VERSION_NAME))
                        .putLong("at", System.currentTimeMillis()).commit();
            } catch (Throwable ignored) {
                // nothing more to do
            }
            if (before != null) before.uncaughtException(t, e);
        });
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The error in words to pass on: the version, the thread, each error's kind and where it was. Pure. */
    static String describe(String thread, Throwable e, String version) {
        StringBuilder b = new StringBuilder("Homefield ").append(version).append(", thread ").append(thread);
        Throwable at = e;
        for (int n = 0; at != null && n < CAUSES; n++, at = at.getCause()) {
            b.append('\n').append(n == 0 ? "" : "caused by ").append(at.getClass().getName());
            StackTraceElement[] st = at.getStackTrace();
            for (int i = 0; i < Math.min(FRAMES, st.length); i++) {
                StackTraceElement f = st[i];
                b.append("\n  at ").append(f.getClassName()).append('.').append(f.getMethodName())
                        .append(f.getLineNumber() > 0 ? ":" + f.getLineNumber() : "");
            }
        }
        return b.toString();
    }

    /** Once, after an unexpected close: what happened, and the details to copy. */
    static void offer(Activity a) {
        SharedPreferences p = prefs(a);
        String note = p.getString("note", null);
        if (note == null) return;
        p.edit().remove("note").apply();
        try {
            new MaterialAlertDialogBuilder(a).setTitle("Homefield closed unexpectedly")
                    .setMessage("Sorry about that. Copy the details and send them over, and it can be fixed.")
                    .setPositiveButton("Copy details", (d, w) -> {
                        ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Homefield", note));
                        Toast.makeText(a, "Copied", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Not now", null).show();
        } catch (RuntimeException ignored) {
            // not now
        }
    }
}
