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
 * config.xml weather_latitude / weather_longitude. Read when Home comes up and every 30 minutes while it is
 * visible, never in the background. Hidden until the first answer arrives.
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
    private final Runnable tick = this::fetch;

    WeatherLine(TextView view, String latitude, String longitude, String place) {
        this.view = view;
        this.latitude = latitude;
        this.longitude = longitude;
        this.place = place;
        view.setVisibility(View.GONE);
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
                + "&current=temperature_2m,weather_code,is_day&timezone=auto";
        io.execute(() -> {
            String line = null;
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
                JSONObject current = new JSONObject(body).getJSONObject("current");
                long temp = Math.round(current.getDouble("temperature_2m"));
                String words = describe(current.getInt("weather_code"));
                line = String.format(Locale.getDefault(), "%d°  ·  %s", temp, words)
                        + (place.isEmpty() ? "" : "  ·  " + place);
            } catch (Exception ignored) {
            }
            final String text = line;
            main.post(() -> {
                if (text != null) {
                    fetchedAt = System.currentTimeMillis();
                    view.setText(text);
                    view.setVisibility(View.VISIBLE);
                }
                if (running) main.postDelayed(tick, text != null ? EVERY_MS : RETRY_MS);
            });
        });
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
