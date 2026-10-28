package com.gathin.deliverytotv;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the app keeps (the paired TVs, the apps to watch) and how an order's notification becomes an update for the
 * TVs: its stage read from the words the delivery apps use, its arrival time, and the app's icon.
 */
final class Relay {
    /** The car app: its notifications go to the TVs as they are, not read as an order. */
    static final String TESLA = "com.teslamotors.tesla";

    /** The apps ticked to begin with (Uber Eats, Just Eat, Deliveroo, Tesla); more are picked in the app. */
    static final Set<String> DEFAULT_APPS = new HashSet<>(Arrays.asList(
            "com.ubercab.eats", "com.justeat.app.uk", "com.justeat.app", "com.deliveroo.orderapp", TESLA));

    private static final ExecutorService SEND = Executors.newSingleThreadExecutor();

    private Relay() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("relay", Context.MODE_PRIVATE);
    }

    /** The apps whose notifications are passed on. */
    static Set<String> apps(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.contains("apps")) {
            Set<String> start = new HashSet<>(DEFAULT_APPS);
            // A local delivery app named for the area (Didsbury & Withington Eats) is ticked too.
            PackageManager pm = c.getPackageManager();
            for (android.content.pm.ApplicationInfo a : pm.getInstalledApplications(0)) {
                String label = String.valueOf(pm.getApplicationLabel(a)).toLowerCase(Locale.ROOT);
                if (label.contains("didsbury") || label.contains("withington")) start.add(a.packageName);
            }
            p.edit().putStringSet("apps", start).apply();
        }
        return new HashSet<>(p.getStringSet("apps", new HashSet<>()));
    }

    static void setApps(Context c, Set<String> apps) {
        prefs(c).edit().putStringSet("apps", new HashSet<>(apps)).apply();
    }

    /** The paired TVs: {name, host, port, token}. */
    static List<JSONObject> tvs(Context c) {
        List<JSONObject> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("tvs", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getJSONObject(i));
        } catch (Exception ignored) {
        }
        return out;
    }

    static void saveTvs(Context c, List<JSONObject> tvs) {
        prefs(c).edit().putString("tvs", new JSONArray(tvs).toString()).apply();
    }

    // ---- reading a notification ------------------------------------------------------------------

    /** Stage words, highest stage first: delivered, arriving, on the way, being prepared, placed. */
    private static final String[][] STAGES = {
            {"delivered", "enjoy your meal", "enjoy your order", "enjoy your food", "order complete", "has been dropped off"},
            {"arriving", "nearby", "almost here", "almost there", "around the corner", "outside", "at your door", "is here",
                    "has arrived", "minutes away", "min away", "approaching"},
            {"on the way", "on its way", "on their way", "picked up", "collected", "heading to you", "heading your way",
                    "out for delivery", "en route", "rider is", "courier is", "driver is", "dasher"},
            {"preparing", "being prepared", "being made", "cooking", "in the kitchen", "accepted", "is making", "getting your order ready"},
            {"order placed", "order received", "order confirmed", "confirmed", "we've got your order", "we have your order",
                    "sent to the restaurant", "waiting for the restaurant", "awaiting"}};

    /** The order's stage (4 delivered .. 0 placed) from its words, or -1 when it is not about an order. */
    static int stage(String text) {
        // A promised time is not a stage: "arriving at 19:42" while it is still being made, "will be delivered by".
        String t = NOT_YET.matcher(text.toLowerCase(Locale.ROOT)).replaceAll(" ");
        for (int i = 0; i < STAGES.length; i++) for (String w : STAGES[i]) if (t.contains(w)) return 4 - i;
        return -1;
    }

    private static final Pattern NOT_YET = Pattern.compile(
            "(arriv(ing|es|al)|delivered|delivery)\\s+(at|by|in|around|between|from|time|estimate)\\b"
                    + "|(will|to|should|expected to) be delivered|estimated (arrival|delivery)");
    private static final Pattern CLOCK = Pattern.compile("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\s*([ap]\\.?m\\.?)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RANGE = Pattern.compile("(\\d{1,2})\\s*(?:-|–|to)\\s*(\\d{1,2})\\s*min", Pattern.CASE_INSENSITIVE);
    private static final Pattern MINUTES = Pattern.compile("(\\d{1,2})\\s*min", Pattern.CASE_INSENSITIVE);

    /** When it arrives, as the app says it: "19:42", "7:42 pm", "in 15–25 min"; empty when it does not say. */
    static String eta(String text) {
        Matcher m = CLOCK.matcher(text);
        if (m.find()) return m.group().trim();
        m = RANGE.matcher(text);
        if (m.find()) return "in " + m.group(1) + "–" + m.group(2) + " min";
        m = MINUTES.matcher(text);
        if (m.find()) return "in " + m.group(1) + " min";
        return "";
    }

    private static final Pattern PERCENT = Pattern.compile("(\\b(?:to|limit|limit of|target|up to)\\s+)?\\b(\\d{1,3})\\s?%",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MILES = Pattern.compile("\\b(\\d{1,3})\\s?(?:mi|miles)\\b", Pattern.CASE_INSENSITIVE);

    /** The battery level a car notification gives ("Charging complete: 80%", "42% · charging to 80%"), or -1. */
    static int battery(String text) {
        Matcher m = PERCENT.matcher(text);
        int limit = -1;
        while (m.find()) {
            int v = Integer.parseInt(m.group(2));
            if (v > 100) continue;
            if (m.group(1) == null) return v;
            limit = v;
        }
        // Only "charged to 80%" said: that is where it is when the charge is complete.
        return text.toLowerCase(Locale.ROOT).matches("(?s).*(complete|finished|done).*") ? limit : -1;
    }

    /** The charge limit it is charging to ("charging to 80%"), or -1. */
    static int target(String text) {
        Matcher m = PERCENT.matcher(text);
        while (m.find()) if (m.group(1) != null) return Math.min(100, Integer.parseInt(m.group(2)));
        return -1;
    }

    /** The range the app gives ("263 mi"), or -1. */
    static int miles(String text) {
        Matcher m = MILES.matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    private static final Pattern FROM = Pattern.compile(
            "(?i:order|food|meal|delivery) (?:from|at) ([A-Z0-9][^.!?\\n]{1,40}?)(?=\\s+(?:is|has|was|will|are)\\b|[.!?\\n,]|$)");
    private static final Pattern BY = Pattern.compile(
            "^([A-Z0-9][^.!?\\n]{1,40}?) (?:is preparing|is making|has accepted|accepted your|is cooking|has started|is getting)",
            Pattern.MULTILINE);

    /** The restaurant, when the app names it ("Your order from Nando's is…", "Nando's is preparing…"), or "". */
    static String restaurant(String text) {
        Matcher m = FROM.matcher(text);
        if (m.find()) return m.group(1).trim();
        m = BY.matcher(text);
        if (m.find()) {
            String r = m.group(1).trim();
            String l = r.toLowerCase(Locale.ROOT);
            if (!l.startsWith("your") && !l.startsWith("the restaurant") && !l.startsWith("we") && !l.contains("driver")
                    && !l.contains("courier") && !l.contains("rider")) return r;
        }
        return "";
    }

    // ---- what was ordered (OrderReader) -----------------------------------------------------------

    /** The items OrderReader last saw on an app's basket or order screen: "2× Big Mac". */
    static void saveItems(Context c, String pkg, List<String> items) {
        prefs(c).edit().putString("items:" + pkg, new JSONArray(items).toString())
                .putLong("itemsAt:" + pkg, System.currentTimeMillis()).apply();
    }

    /** Those items, while they are recent enough to be this order's (three hours); empty otherwise. */
    static JSONArray items(Context c, String pkg) {
        SharedPreferences p = prefs(c);
        if (System.currentTimeMillis() - p.getLong("itemsAt:" + pkg, 0) > 3 * 60 * 60_000L) return new JSONArray();
        try {
            return new JSONArray(p.getString("items:" + pkg, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static void clearItems(Context c, String pkg) {
        prefs(c).edit().remove("items:" + pkg).remove("itemsAt:" + pkg).apply();
    }

    /** The restaurant OrderReader saw on screen, kept like the items. */
    static void saveRestaurant(Context c, String pkg, String name) {
        prefs(c).edit().putString("from:" + pkg, name).apply();
    }

    static String savedRestaurant(Context c, String pkg) {
        if (System.currentTimeMillis() - prefs(c).getLong("itemsAt:" + pkg, 0) > 3 * 60 * 60_000L) return "";
        return prefs(c).getString("from:" + pkg, "");
    }

    /** The app's icon as a small PNG, in base64. */
    static String icon(Context c, String pkg) {
        try {
            Drawable d = c.getPackageManager().getApplicationIcon(pkg);
            Bitmap b = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(b);
            d.setBounds(0, 0, 192, 192);
            d.draw(canvas);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    static String label(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            return pkg;
        }
    }

    // ---- home, for the time to home (HomeTrip) ---------------------------------------------------

    static void setHome(Context c, double lat, double lng) {
        prefs(c).edit().putString("homeLat", String.valueOf(lat)).putString("homeLng", String.valueOf(lng)).apply();
    }

    static boolean homeSet(Context c) {
        return prefs(c).contains("homeLat");
    }

    /** Miles in a straight line from home, or -1 with no home set. */
    static double milesFromHome(Context c, double lat, double lng) {
        if (!homeSet(c)) return -1;
        float[] out = new float[1];
        android.location.Location.distanceBetween(lat, lng, Double.parseDouble(prefs(c).getString("homeLat", "0")),
                Double.parseDouble(prefs(c).getString("homeLng", "0")), out);
        return out[0] / 1609.344;
    }

    /** The car's Bluetooth name: the drive starts when the phone connects to it. */
    static String car(Context c) {
        return prefs(c).getString("car", "");
    }

    static void setCar(Context c, String name) {
        prefs(c).edit().putString("car", name).apply();
    }

    /** The time to home, to the TVs that show it (they say so, asked once an hour). */
    static void sendHome(Context c, JSONObject update) {
        Context app = c.getApplicationContext();
        SEND.execute(() -> {
            for (JSONObject tv : tvs(app)) {
                try {
                    String host = tv.getString("host");
                    int port = tv.getInt("port");
                    String key = "features:" + host;
                    SharedPreferences p = prefs(app);
                    if (System.currentTimeMillis() - p.getLong(key + ":at", 0) > 60 * 60_000L) {
                        JSONObject hello = post(host, port, "/hello", new JSONObject(), null);
                        p.edit().putString(key, String.valueOf(hello.optJSONArray("features")))
                                .putLong(key + ":at", System.currentTimeMillis()).apply();
                    }
                    if (!p.getString(key, "").contains("home_eta")) continue;
                    post(host, port, "/delivery", update, tv.getString("token"));
                } catch (Exception ignored) {
                    // that TV is off or away
                }
            }
        });
    }

    // ---- sending --------------------------------------------------------------------------------

    /** The update to every paired TV, off the main thread. */
    static void send(Context c, JSONObject update) {
        Context app = c.getApplicationContext();
        SEND.execute(() -> {
            for (JSONObject tv : tvs(app)) {
                try {
                    post(tv.getString("host"), tv.getInt("port"), "/delivery", update, tv.getString("token"));
                } catch (Exception ignored) {
                    // that TV is off or away: the next update tries again
                }
            }
        });
    }

    /** A POST to a TV: its JSON reply. */
    static JSONObject post(String host, int port, String path, JSONObject body, String token) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("http://" + host + ":" + port + path).openConnection();
        conn.setConnectTimeout(4_000);
        conn.setReadTimeout(6_000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        if (token != null) conn.setRequestProperty("X-Token", token);
        byte[] b = body.toString().getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(b.length);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(b);
        }
        int code = conn.getResponseCode();
        InputStream in = code < 400 ? conn.getInputStream() : conn.getErrorStream();
        ByteArrayOutputStream reply = new ByteArrayOutputStream();
        if (in != null) {
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) reply.write(buf, 0, n);
            in.close();
        }
        conn.disconnect();
        JSONObject json = reply.size() > 0 ? new JSONObject(reply.toString("UTF-8")) : new JSONObject();
        if (code >= 400) throw new Exception(json.optString("error", "HTTP " + code));
        return json;
    }
}
