package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import com.gathin.broadcast.LogoExtras;

import java.time.ZoneId;
import java.util.List;

/**
 * Where the logo's extras come from on the launcher (LogoExtras, shared with Homefield TV): Up next from Sky's guide for
 * the channel tuned (TvGuide, InStep), the prayer times from the family's mosque (Prayer), and the clock's weather from
 * Home's last reading (WeatherLine's, under three hours old). The same answers go to Homefield TV for its own logo
 * (SyncTokenProvider, call "logo_extras"), so both covers say the same.
 */
final class LogoExtrasHere {
    private LogoExtrasHere() { }

    static final LogoExtras.Source SOURCE = new LogoExtras.Source() {
        @Override public List<Object[]> around(Context c, long now, long within) {
            String[] channels = c.getResources().getStringArray(R.array.sport_tv_channels);
            return TvGuide.around(channels, InStep.tunedNames(c), now, within);
        }

        @Override public boolean prayerOn(Context c) {
            return Prayer.on(c);
        }

        @Override public Object[] nextPrayer(Context c) {
            return prayer(c);
        }

        @Override public long[] weather(Context c, long now) {
            return LogoExtrasHere.weather(c, now);
        }

        @Override public android.graphics.drawable.Drawable icon(Context c, int code, boolean day) {
            return iconOf(c, WeatherLine.icon(code, day));
        }
    };

    /** The weather's icons, each made once (the clock compares the one it has with the one it is given). */
    private static final java.util.Map<Integer, android.graphics.drawable.Drawable> ICONS = new java.util.HashMap<>();

    static android.graphics.drawable.Drawable iconOf(Context c, int res) {
        if (res == 0) return null;
        android.graphics.drawable.Drawable d = ICONS.get(res);
        if (d == null) {
            d = c.getDrawable(res);
            ICONS.put(res, d);
        }
        return d;
    }

    /** The next prayer as the extras take it: {label, begins (epoch ms), its time}; null for none. */
    static Object[] prayer(Context c) {
        Prayer.Slot s = Prayer.next(c);
        if (s == null || s.begins == null) return null;
        return new Object[]{s.label(), s.begins.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), Prayer.hm(s.begins)};
    }

    /** Home's last weather reading: {temperature, code, 1 by day}; null when there is none under three hours old. */
    static long[] weather(Context c, long now) {
        SharedPreferences p = c.getSharedPreferences("weather_line", Context.MODE_PRIVATE);
        return LogoExtras.weatherOf(p.getLong("at", 0), p.getLong("temp", 0), p.getInt("code", 3), p.getBoolean("day", true), now);
    }
}
