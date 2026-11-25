package com.gathin.homefieldtv;

import android.content.Context;
import android.os.Bundle;

import com.gathin.broadcast.LogoExtras;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Where the logo's extras come from in Homefield TV (LogoExtras, the launcher's own over TiviMate, shared): Up next from
 * this app's own guide for the channel playing; the prayer times and the clock's weather from the TV launcher (its
 * provider, call "logo_extras": the mosque's times and Home's last reading, the same the launcher's logo says), read off
 * the main thread now and then and kept; with no launcher to ask, no prayer pill and the clock without the weather.
 */
final class LogoHere {
    private LogoHere() {}

    /** The launcher's answers are read again this often. */
    static final long READ_MS = 60_000L;

    /** The channel playing (its key), for Up next. */
    static volatile String channelKey;

    /** Who answers: the launcher's provider (a fake in the tests); null when no launcher answered. */
    static volatile BiFunction<Context, Bundle, Bundle> launcher = LogoHere::ask;

    private static volatile Bundle last;
    private static volatile long readAt;
    private static volatile android.graphics.drawable.Drawable icon;
    private static volatile int iconCode = -1;

    private static Bundle ask(Context c, Bundle q) {
        try {
            return c.getApplicationContext().getContentResolver().call(android.net.Uri.parse(com.gathin.broadcast.BoardAlert.LAUNCHER_SYNC),
                    "logo_extras", null, q);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Read the launcher's answer again when it is older than READ_MS (main thread; the read itself off it). */
    static void refresh(Context c) {
        long now = System.currentTimeMillis();
        if (now - readAt < READ_MS) return;
        readAt = now;
        Context app = c.getApplicationContext();
        Exec.io(() -> {
            Bundle b = null;
            try {
                b = launcher.apply(app, new Bundle());
            } catch (RuntimeException ignored) {
                // none this time
            }
            last = b;
        });
    }

    /** Pure: the programmes of a schedule from now to within ahead, {title, start} each, in order. */
    static List<Object[]> around(Guide.Schedule s, long now, long within) {
        List<Object[]> out = new ArrayList<>();
        if (s == null || s.start == null) return out;
        for (int i = 0; i < s.start.length; i++) {
            if (s.stop[i] <= now || s.start[i] > now + within) continue;
            out.add(new Object[]{s.title[i] == null ? "" : s.title[i], s.start[i]});
        }
        return out;
    }

    static final LogoExtras.Source SOURCE = new LogoExtras.Source() {
        @Override public List<Object[]> around(Context c, long now, long within) {
            String key = channelKey;
            if (key == null || key.isEmpty()) return new ArrayList<>();
            return LogoHere.around(Library.get(c).schedule(key), now, within);
        }

        @Override public boolean prayerOn(Context c) {
            Bundle b = last;
            return b != null && b.getBoolean("prayer_on", false);
        }

        @Override public Object[] nextPrayer(Context c) {
            Bundle b = last;
            if (b == null || b.getString("prayer") == null) return null;
            return new Object[]{b.getString("prayer"), b.getLong("prayer_at"), b.getString("prayer_time", "")};
        }

        @Override public long[] weather(Context c, long now) {
            Bundle b = last;
            if (b == null || !b.containsKey("temp")) return null;
            return LogoExtras.weatherOf(b.getLong("weather_at"), b.getLong("temp"), b.getInt("code", 3), b.getBoolean("day", true), now);
        }

        @Override public android.graphics.drawable.Drawable icon(Context c, int code, boolean day) {
            Bundle b = last;
            int k = code * 2 + (day ? 1 : 0);
            if (icon != null && iconCode == k) return icon;
            byte[] png = b == null ? null : b.getByteArray("icon");
            android.graphics.Bitmap bm = png == null ? null : android.graphics.BitmapFactory.decodeByteArray(png, 0, png.length);
            icon = bm == null ? null : new android.graphics.drawable.BitmapDrawable(c.getResources(), bm);
            iconCode = k;
            return icon;
        }
    };

    /** Forgotten (the tests). */
    static void reset() {
        last = null;
        readAt = 0;
        icon = null;
        iconCode = -1;
    }
}
