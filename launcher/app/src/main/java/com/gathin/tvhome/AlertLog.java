package com.gathin.tvhome;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The pop-ups' own history, kept on the TV (the app's files folder, alerts.log, readable with adb): each one shown, with
 * the time and what it said, and why one was held back or not shown (a goal seen before the first read, another pop-up
 * still up, the screen off, its card failing to build). The last 400 lines are kept.
 */
final class AlertLog {
    private static final ExecutorService WRITE = Executors.newSingleThreadExecutor();
    private static final int KEEP = 400;
    private static Context app;

    private AlertLog() { }

    static void note(Context c, String what) {
        if (c != null && app == null) app = c.getApplicationContext();
        Context a = app;
        if (a == null || what == null) return;
        String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.UK).format(new Date()) + "  " + what.replace('\n', ' ');
        WRITE.execute(() -> {
            try {
                File dir = a.getExternalFilesDir(null);
                if (dir == null) return;
                File f = new File(dir, "alerts.log");
                try (Writer w = new OutputStreamWriter(new FileOutputStream(f, true), StandardCharsets.UTF_8)) { w.write(line + "\n"); }
                if (f.length() > 96_000) {
                    List<String> all = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
                    List<String> last = new ArrayList<>(all.subList(Math.max(0, all.size() - KEEP), all.size()));
                    Files.write(f.toPath(), last, StandardCharsets.UTF_8);
                }
            } catch (Exception ignored) {
                // a history only: nothing depends on it
            }
        });
    }

    /** A game in a few words: "NEW 0-1 EVE". */
    static String game(ScoresTicker.Game g) {
        return g == null ? "?" : g.home + " " + g.homeScore + "-" + g.awayScore + " " + g.away;
    }
}
