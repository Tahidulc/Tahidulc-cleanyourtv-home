package com.gathin.tvhome;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The current weather beside the date ("11° · Light rain · Manchester"), from Open-Meteo (no key), for the
 * config.xml weather_latitude / weather_longitude, and from MET Norway's forecast when Open-Meteo does not answer (it
 * turns callers away when busy). Read when Home comes up and every 30 minutes while it is visible, never in the
 * background; after a miss, again in a minute, then two, then every five. The last reading is kept, so Home starting
 * afresh (an update, a crash) shows it at once if it is under three hours old; hidden only with none at all.
 */
final class WeatherLine {
    private static final long EVERY_MS = 30 * 60_000L;
    private static final long RETRY_MS = 5 * 60_000L;

    private final TextView view;
    private final String latitude, longitude, place;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private long fetchedAt;
    private boolean running;
    /** Misses in a row, for the wait before the next try. */
    private int misses;
    private static final long KEEP_MS = 3 * 60 * 60_000L;
    private final Runnable tick = this::fetch;
    /** The next five days: {date, weather code, high, low, rain %}. */
    private volatile java.util.List<String[]> days = new java.util.ArrayList<>();
    private android.widget.PopupWindow popup;

    /**
     * The next five days in a glass panel under the weather line: day, icon, high and low, chance of rain.
     * Back or OK closes it.
     */
    void showForecast() {
        if (days.isEmpty() || (popup != null && popup.isShowing())) return;
        android.content.Context ctx = view.getContext();
        float density = ctx.getResources().getDisplayMetrics().density;
        android.widget.LinearLayout panel = new android.widget.LinearLayout(ctx);
        panel.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        panel.setBackgroundResource(R.drawable.glass_panel);
        int pad = Math.round(18 * density);
        panel.setPadding(pad, pad, pad, pad);
        java.time.format.DateTimeFormatter dayName = java.time.format.DateTimeFormatter.ofPattern("EEE", Locale.getDefault());
        for (String[] d : days) {
            android.widget.LinearLayout col = new android.widget.LinearLayout(ctx);
            col.setOrientation(android.widget.LinearLayout.VERTICAL);
            col.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            col.setPadding(Math.round(14 * density), 0, Math.round(14 * density), 0);
            col.setMinimumWidth(Math.round(78 * density));   // room for "☂ 100%"
            col.addView(text(ctx, java.time.LocalDate.parse(d[0]).format(dayName).toUpperCase(Locale.getDefault()), 13, 0xB3FFFFFF));
            android.widget.ImageView icon = new android.widget.ImageView(ctx);
            icon.setImageResource(icon(Integer.parseInt(d[1]), true));
            android.widget.LinearLayout.LayoutParams ilp = new android.widget.LinearLayout.LayoutParams(
                    Math.round(34 * density), Math.round(34 * density));
            ilp.topMargin = ilp.bottomMargin = Math.round(8 * density);
            col.addView(icon, ilp);
            col.addView(text(ctx, d[2] + "°", 20, 0xFFFFFFFF));
            col.addView(text(ctx, d[3] + "°", 14, 0x99FFFFFF));
            if (!d[4].isEmpty()) col.addView(text(ctx, "☂ " + d[4] + "%", 12, 0xFF7CC4FF));
            panel.addView(col);
        }
        popup = new android.widget.PopupWindow(panel, android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        popup.setElevation(12 * density);
        panel.setFocusable(true);
        panel.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == android.view.KeyEvent.ACTION_UP && (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == android.view.KeyEvent.KEYCODE_ENTER)) {
                popup.dismiss();
                return true;
            }
            return false;
        });
        popup.showAsDropDown(view, 0, Math.round(10 * density));
        panel.requestFocus();
    }

    void dismissForecast() {
        if (popup != null) popup.dismiss();
    }

    private static TextView text(android.content.Context ctx, String s, float sp, int color) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Fonts.get(ctx, R.font.outfit_regular));
        t.setGravity(android.view.Gravity.CENTER);
        t.setSingleLine(true);
        return t;
    }

    WeatherLine(TextView view, String latitude, String longitude, String place) {
        this.view = view;
        this.latitude = latitude;
        this.longitude = longitude;
        this.place = place;
        view.setVisibility(View.GONE);
        // The last reading at once, while a new one is fetched.
        android.content.SharedPreferences p = view.getContext().getSharedPreferences("weather_line", android.content.Context.MODE_PRIVATE);
        long at = p.getLong("at", 0);
        if (at > 0 && System.currentTimeMillis() - at < KEEP_MS) {
            show(p.getLong("temp", 0), p.getInt("code", 3), p.getBoolean("day", true));
            fetchedAt = at;
        }
    }

    /** "11°  ·  Manchester", with its icon, on the pill. */
    private void show(long temp, int code, boolean isDay) {
        String text = String.format(Locale.getDefault(), "%d°", temp) + (place.isEmpty() ? "" : "  ·  " + place);
        view.setText(text);
        view.setContentDescription(describe(code) + ", " + text);
        int iconRes = light ? lightIcon(code, isDay) : icon(code, isDay);
        android.graphics.drawable.Drawable d = view.getContext().getDrawable(iconRes);
        if (d != null) {
            int size = Math.round(view.getTextSize() * 1.6f);
            d.setBounds(0, 0, size, size);
        }
        view.setCompoundDrawablePadding(Math.round(view.getTextSize() * 0.6f));
        view.setCompoundDrawables(d, null, null, null);
        view.setVisibility(View.VISIBLE);
    }

    /** {temperature, WMO code, 1 by day} from MET Norway's forecast for the next hour; null when it does not answer. */
    private long[] metNorway() {
        try {
            String url = String.format(Locale.ROOT, "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.4f&lon=%.4f",
                    Double.parseDouble(latitude), Double.parseDouble(longitude));
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(8000);
            // MET Norway asks every caller to say who it is.
            conn.setRequestProperty("User-Agent", "TVHome launcher (com.gathin.tvhome; github.com/Gathin23/cleanyourtv)");
            String body;
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0 && out.size() < 512 * 1024) out.write(buf, 0, n);
                body = out.toString("UTF-8");
            } finally {
                conn.disconnect();
            }
            JSONObject now = new JSONObject(body).getJSONObject("properties").getJSONArray("timeseries").getJSONObject(0)
                    .getJSONObject("data");
            double temp = now.getJSONObject("instant").getJSONObject("details").getDouble("air_temperature");
            JSONObject next = now.optJSONObject("next_1_hours");
            if (next == null) next = now.optJSONObject("next_6_hours");
            String symbol = next == null ? "cloudy" : next.getJSONObject("summary").optString("symbol_code", "cloudy");
            return new long[]{Math.round(temp), wmo(symbol), symbol.endsWith("_night") ? 0 : 1};
        } catch (Exception e) {
            return null;
        }
    }

    /** MET Norway's symbol ("partlycloudy_day", "lightrainshowers_night") as the nearest WMO code. */
    static int wmo(String symbol) {
        String s = symbol.replaceAll("_(day|night|polartwilight)$", "");
        if (s.contains("thunder")) return 95;
        if (s.startsWith("clearsky")) return 0;
        if (s.startsWith("fair")) return 1;
        if (s.startsWith("partlycloudy")) return 2;
        if (s.equals("fog")) return 45;
        if (s.contains("snowshowers")) return 85;
        if (s.contains("snow")) return s.startsWith("heavy") ? 75 : s.startsWith("light") ? 71 : 73;
        if (s.contains("sleet")) return 66;
        if (s.contains("rainshowers")) return s.startsWith("heavy") ? 82 : 80;
        if (s.contains("rain")) return s.startsWith("heavy") ? 65 : s.startsWith("light") ? 61 : 63;
        return 3;
    }

    void start() {
        if (latitude.isEmpty() || longitude.isEmpty()) return;
        running = true;
        long age = System.currentTimeMillis() - fetchedAt;
        main.removeCallbacks(tick);
        if (age >= EVERY_MS) fetch();
        else main.postDelayed(tick, EVERY_MS - age);
    }

    void stop() {
        running = false;
        main.removeCallbacks(tick);
    }

    void close() {
        stop();
        io.shutdownNow();
    }

    private void fetch() {
        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + latitude + "&longitude=" + longitude
                + "&current=temperature_2m,weather_code,is_day"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                + "&forecast_days=6&timezone=auto";
        io.execute(() -> {
            long[] got = null;
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(8000);
                String body;
                try (InputStream in = conn.getInputStream()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) > 0 && out.size() < 64 * 1024) out.write(buf, 0, n);
                    body = out.toString("UTF-8");
                } finally {
                    conn.disconnect();
                }
                JSONObject json = new JSONObject(body);
                JSONObject current = json.getJSONObject("current");
                JSONObject daily = json.optJSONObject("daily");
                if (daily != null) {
                    java.util.List<String[]> next = new java.util.ArrayList<>();
                    org.json.JSONArray t = daily.getJSONArray("time"), code = daily.getJSONArray("weather_code"),
                            hi = daily.getJSONArray("temperature_2m_max"), lo = daily.getJSONArray("temperature_2m_min"),
                            rain = daily.optJSONArray("precipitation_probability_max");
                    for (int d = 1; d < t.length() && next.size() < 5; d++) {   // tomorrow on: today is on screen
                        next.add(new String[]{t.getString(d), String.valueOf(code.getInt(d)),
                                String.valueOf(Math.round(hi.getDouble(d))), String.valueOf(Math.round(lo.getDouble(d))),
                                rain == null || rain.isNull(d) ? "" : String.valueOf(rain.getInt(d))});
                    }
                    days = next;
                }
                got = new long[]{Math.round(current.getDouble("temperature_2m")), current.getInt("weather_code"),
                        current.optInt("is_day", 1)};
            } catch (Exception ignored) {
                // busy or unreachable: MET Norway next
            }
            if (got == null) got = metNorway();
            final long[] reading = got;
            main.post(() -> {
                if (reading != null) {
                    misses = 0;
                    fetchedAt = System.currentTimeMillis();
                    show(reading[0], (int) reading[1], reading[2] == 1);
                    view.getContext().getSharedPreferences("weather_line", android.content.Context.MODE_PRIVATE).edit()
                            .putLong("at", fetchedAt).putLong("temp", reading[0]).putInt("code", (int) reading[1])
                            .putBoolean("day", reading[2] == 1).apply();
                } else {
                    misses++;
                }
                if (running) main.postDelayed(tick, reading != null ? EVERY_MS : misses == 1 ? 60_000L : misses == 2 ? 120_000L : RETRY_MS);
            });
        });
    }

    /** The same icon drawn for a light background (the silver pills, the alerts): grey clouds, colours kept. */
    static int lightIcon(int code, boolean day) {
        if (code <= 1) return day ? R.drawable.wx_clear_light : R.drawable.wx_night_light;
        if (code == 2) return R.drawable.wx_partly_light;
        if (code == 3) return R.drawable.wx_cloud_light;
        if (code == 45 || code == 48) return R.drawable.wx_fog_light;
        if (code >= 95) return R.drawable.wx_storm_light;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return R.drawable.wx_snow_light;
        if (code >= 51) return R.drawable.wx_rain_light;
        return R.drawable.wx_cloud_light;
    }

    /** On a silver pill: the light-background icons. */
    private volatile boolean light;

    void useLightIcons(boolean on) {
        light = on;
    }

    /** The icon for a WMO weather code; clear skies get the moon at night. */
    static int icon(int code, boolean day) {
        if (code <= 1) return day ? R.drawable.wx_clear : R.drawable.wx_night;
        if (code == 2) return R.drawable.wx_partly;
        if (code == 3) return R.drawable.wx_cloud;
        if (code == 45 || code == 48) return R.drawable.wx_fog;
        if (code >= 95) return R.drawable.wx_storm;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return R.drawable.wx_snow;
        if (code >= 51) return R.drawable.wx_rain;
        return R.drawable.wx_cloud;
    }

    /** WMO weather interpretation codes, as Open-Meteo documents them. */
    static String describe(int code) {
        switch (code) {
            case 0: return "Clear";
            case 1: return "Mainly clear";
            case 2: return "Partly cloudy";
            case 3: return "Overcast";
            case 45: case 48: return "Fog";
            case 51: case 53: case 55: return "Drizzle";
            case 56: case 57: return "Freezing drizzle";
            case 61: return "Light rain";
            case 63: return "Rain";
            case 65: return "Heavy rain";
            case 66: case 67: return "Freezing rain";
            case 71: case 73: case 75: case 77: return "Snow";
            case 80: case 81: case 82: return "Showers";
            case 85: case 86: return "Snow showers";
            case 95: case 96: case 99: return "Thunderstorm";
            default: return "";
        }
    }
}
