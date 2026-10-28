package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * The car's latest news beside the weather pill: the last notification the car app (Tesla) sent the phone, which the
 * phone passed on (DeliveryLink). "⚡ 80% · Charging complete", gone after twelve hours with nothing new.
 */
final class CarPill {
    private static final String PREFS = "car";
    private static final long FRESH_MS = 12 * 60 * 60_000L;

    private final TextView pill;
    private final SharedPreferences prefs;
    private final SharedPreferences.OnSharedPreferenceChangeListener changed;

    /** Saves the latest update for the pill (and for Home when it comes back to the front). */
    static void save(Context c, String title, String status, int battery) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("title", title).putString("status", status).putInt("battery", battery)
                .putLong("at", System.currentTimeMillis()).apply();
    }

    static void clear(Context c) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    /** A pill styled like the weather's, put on the same line, just after it. */
    CarPill(TextView weather) {
        Context c = weather.getContext();
        float d = c.getResources().getDisplayMetrics().density;
        ViewGroup column = (ViewGroup) weather.getParent();
        int at = column.indexOfChild(weather);
        ViewGroup.LayoutParams weatherLp = weather.getLayoutParams();
        column.removeView(weather);
        LinearLayout line = new LinearLayout(c);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.addView(weather, weatherLp);
        column.addView(line, at, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        pill = new TextView(c);
        if (weather.getBackground() != null && weather.getBackground().getConstantState() != null)
            pill.setBackground(weather.getBackground().getConstantState().newDrawable().mutate());
        pill.setTextColor(weather.getTextColors().getDefaultColor());
        pill.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, weather.getTextSize());
        pill.setTypeface(weather.getTypeface());
        pill.setLetterSpacing(0.04f);
        pill.setSingleLine(true);
        pill.setEllipsize(android.text.TextUtils.TruncateAt.END);
        pill.setMaxWidth(Math.round(360 * d));
        pill.setGravity(android.view.Gravity.CENTER_VERTICAL);
        pill.setPadding(weather.getPaddingLeft() + Math.round(2 * d), weather.getPaddingTop(), weather.getPaddingRight(),
                weather.getPaddingBottom());
        pill.setVisibility(View.GONE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Math.round(6 * d);
        lp.leftMargin = Math.round(10 * d);
        line.addView(pill, lp);

        prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        changed = (p, key) -> pill.post(this::refresh);
        prefs.registerOnSharedPreferenceChangeListener(changed);
        refresh();
    }

    void refresh() {
        long at = prefs.getLong("at", 0);
        if (at == 0 || System.currentTimeMillis() - at > FRESH_MS) { pill.setVisibility(View.GONE); return; }
        String title = prefs.getString("title", ""), status = prefs.getString("status", "");
        int battery = prefs.getInt("battery", -1);
        String what = !title.isEmpty() && title.length() <= 32 ? title : status;
        boolean charging = (title + " " + status).toLowerCase(Locale.ROOT).contains("charg");
        StringBuilder s = new StringBuilder(charging ? "⚡  " : "🚗  ");
        if (battery >= 0) s.append(battery).append("%  ·  ");
        s.append(what);
        pill.setText(s);
        pill.setVisibility(View.VISIBLE);
    }

    void close() {
        prefs.unregisterOnSharedPreferenceChangeListener(changed);
    }
}
