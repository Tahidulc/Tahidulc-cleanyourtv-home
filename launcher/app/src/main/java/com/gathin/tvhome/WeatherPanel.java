package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Launcher v2's weather, opened with OK on the weather pill: a card over Home in the style of a phone's weather app.
 * Its sky moves with the weather (WeatherViews.Sky). Page one: the temperature now, the next 24 hours, the week with
 * each day's range on one scale, and the details (feels like, humidity, wind, UV, sunrise or sunset, rain). Down to
 * page two: visibility, the barometer, air quality and pollen, the sun's path, what the day is good for (running,
 * fishing, a coat...) and the moon. From Open-Meteo, the same free service as the pill. Back or OK closes it; Left
 * and Right slide the hours; Up and Down turn the page.
 */
final class WeatherPanel {
    private static final long FRESH_MS = 10 * 60_000L;

    private final HomeV2 h;
    private final String latitude, longitude, place;
    private FrameLayout overlay, card, pageOne, pageTwo;
    private View dim, hint;
    private View[] dots;
    private HorizontalScrollView hours;
    private boolean open, loading, twoShown;
    private int pageAt;
    private Now now;
    private long fetchedAt;
    private final List<Animator> running = new ArrayList<>();

    WeatherPanel(HomeV2 h, String latitude, String longitude, String place) {
        this.h = h;
        this.latitude = latitude;
        this.longitude = longitude;
        this.place = place;
    }

    boolean isOpen() {
        return open;
    }

    // ---- the forecast ----------------------------------------------------------------------------------------------

    /** Everything the card shows, read in one go. */
    private static final class Now {
        int code, humidity, windDir, uvMax, rainChance, cloud;
        boolean day;
        double temp, feels, wind, gusts, rain, dew, visibility = Double.NaN, pressure = Double.NaN;
        double aqi = Double.NaN, pm25 = Double.NaN, pollen = Double.NaN;
        String sunrise, sunset, time;
        final List<Hour> hours = new ArrayList<>();
        final List<Day> days = new ArrayList<>();
    }

    private static final class Hour {
        String time;
        int code, rain;
        boolean day;
        double temp;
    }

    private static final class Day {
        LocalDate date;
        int code, rain;
        double high, low;
    }

    private Now read() throws Exception {
        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + latitude + "&longitude=" + longitude
                + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,dew_point_2m,weather_code,is_day,"
                + "wind_speed_10m,wind_direction_10m,wind_gusts_10m,precipitation,pressure_msl,cloud_cover,visibility"
                + "&hourly=temperature_2m,weather_code,precipitation_probability,is_day"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset,uv_index_max"
                + "&forecast_days=7&timezone=auto&wind_speed_unit=mph";
        JSONObject o = new JSONObject(HomeV2.get(url));
        JSONObject c = o.getJSONObject("current");
        Now n = new Now();
        n.time = c.optString("time");
        n.temp = c.optDouble("temperature_2m");
        n.feels = c.optDouble("apparent_temperature");
        n.humidity = c.optInt("relative_humidity_2m");
        n.dew = c.optDouble("dew_point_2m");
        n.code = c.optInt("weather_code");
        n.day = c.optInt("is_day", 1) == 1;
        n.wind = c.optDouble("wind_speed_10m");
        n.gusts = c.optDouble("wind_gusts_10m", n.wind);
        n.windDir = c.optInt("wind_direction_10m");
        n.rain = c.optDouble("precipitation");
        n.pressure = c.optDouble("pressure_msl", Double.NaN);
        n.cloud = c.optInt("cloud_cover");
        n.visibility = c.optDouble("visibility", Double.NaN);
        String nowHour = n.time.length() >= 13 ? n.time.substring(0, 13) : "";
        JSONObject hr = o.getJSONObject("hourly");
        JSONArray ht = hr.getJSONArray("time"), htemp = hr.getJSONArray("temperature_2m"), hcode = hr.getJSONArray("weather_code"),
                hrain = hr.getJSONArray("precipitation_probability"), hday = hr.getJSONArray("is_day");
        int from = 0;
        for (int i = 0; i < ht.length(); i++) if (ht.getString(i).startsWith(nowHour)) { from = i; break; }
        for (int i = from; i < Math.min(ht.length(), from + 24); i++) {
            Hour x = new Hour();
            x.time = i == from ? "Now" : ht.getString(i).substring(11, 13);
            x.temp = htemp.optDouble(i);
            x.code = hcode.optInt(i);
            x.rain = hrain.optInt(i);
            x.day = hday.optInt(i, 1) == 1;
            n.hours.add(x);
        }
        JSONObject d = o.getJSONObject("daily");
        JSONArray dt = d.getJSONArray("time");
        for (int i = 0; i < dt.length(); i++) {
            Day x = new Day();
            x.date = LocalDate.parse(dt.getString(i));
            x.code = d.getJSONArray("weather_code").optInt(i);
            x.high = d.getJSONArray("temperature_2m_max").optDouble(i);
            x.low = d.getJSONArray("temperature_2m_min").optDouble(i);
            x.rain = d.getJSONArray("precipitation_probability_max").optInt(i);
            n.days.add(x);
        }
        n.sunrise = d.getJSONArray("sunrise").optString(0);
        n.sunset = d.getJSONArray("sunset").optString(0);
        n.uvMax = (int) Math.round(d.getJSONArray("uv_index_max").optDouble(0));
        n.rainChance = n.days.isEmpty() ? 0 : n.days.get(0).rain;
        // The air: Open-Meteo's European index, fine particles, and pollen in season (none read is no card's end).
        try {
            JSONObject a = new JSONObject(HomeV2.get("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=" + latitude
                    + "&longitude=" + longitude + "&current=european_aqi,pm2_5,grass_pollen,birch_pollen,alder_pollen&timezone=auto"))
                    .getJSONObject("current");
            n.aqi = a.optDouble("european_aqi", Double.NaN);
            n.pm25 = a.optDouble("pm2_5", Double.NaN);
            double pollen = Double.NaN;
            for (String k : new String[]{"grass_pollen", "birch_pollen", "alder_pollen"}) {
                double v = a.optDouble(k, Double.NaN);
                if (!Double.isNaN(v)) pollen = Double.isNaN(pollen) ? v : Math.max(pollen, v);
            }
            n.pollen = pollen;
        } catch (Exception ignored) {
        }
        test(n);
        if (!n.hours.isEmpty()) { n.hours.get(0).temp = n.temp; n.hours.get(0).code = n.code; }
        return n;
    }

    /**
     * For trying each sky on a TV: a file weather_test in the app's own files on the TV's storage, holding a weather
     * code and 1 (day) or 0 (night), say "95,0", shows that weather instead of the real one.
     */
    private void test(Now n) {
        try {
            File f = new File(h.a.getExternalFilesDir(null), "weather_test");
            if (!f.exists()) return;
            String[] p = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim().split(",");
            n.code = Integer.parseInt(p[0].trim());
            if (p.length > 1) n.day = "1".equals(p[1].trim());
            if (p.length > 2) n.wind = Double.parseDouble(p[2].trim());
        } catch (Exception ignored) {
        }
    }

    // ---- opening, closing, the pages -------------------------------------------------------------------------------

    void show() {
        if (open || loading) return;
        if (now != null && SystemClock.elapsedRealtime() - fetchedAt < FRESH_MS) { build(now); return; }
        loading = true;
        h.io.execute(() -> {
            Now got;
            try {
                got = read();
            } catch (Exception e) {
                got = null;
            }
            Now n = got;
            h.main.post(() -> {
                loading = false;
                if (n == null) {
                    android.widget.Toast.makeText(h.a, "The weather isn't available just now", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                now = n;
                fetchedAt = SystemClock.elapsedRealtime();
                build(n);
            });
        });
    }

    void hide() {
        if (!open) return;
        open = false;
        for (Animator a : running) a.cancel();
        running.clear();
        FrameLayout o = overlay;
        overlay = null;
        dim.animate().alpha(0f).setDuration(260).start();
        card.animate().alpha(0f).scaleX(0.94f).scaleY(0.94f).translationY(h.px(24)).setDuration(260)
                .setInterpolator(HomeV2.SKY).withEndAction(() -> h.root.removeView(o)).start();
    }

    /** Back, OK: closed. Left, Right: the hours slide along. Up, Down: the other page. Every other key stays here. */
    boolean key(KeyEvent e) {
        int code = e.getKeyCode();
        boolean close = code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_ESCAPE;
        if (close) {
            if (e.getAction() == KeyEvent.ACTION_UP) hide();
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (code == KeyEvent.KEYCODE_DPAD_DOWN && pageAt == 0) turn(1);
            else if (code == KeyEvent.KEYCODE_DPAD_UP && pageAt == 1) turn(0);
            else if (pageAt == 0 && hours != null && code == KeyEvent.KEYCODE_DPAD_RIGHT) hours.smoothScrollBy(h.px(HOUR_W * 6), 0);
            else if (pageAt == 0 && hours != null && code == KeyEvent.KEYCODE_DPAD_LEFT) hours.smoothScrollBy(-h.px(HOUR_W * 6), 0);
        }
        return code != KeyEvent.KEYCODE_HOME;
    }

    /** Slides to page p, the other going up (or down) out of the card; page two's pieces come in the first time. */
    private void turn(int p) {
        pageAt = p;
        int span = h.px(CARD_H);
        pageOne.setLayerType(View.LAYER_TYPE_NONE, null);
        pageTwo.setLayerType(View.LAYER_TYPE_NONE, null);
        pageOne.animate().translationY(p == 0 ? 0 : -span).alpha(p == 0 ? 1f : 0f).setDuration(520).setInterpolator(HomeV2.SKY).start();
        pageTwo.animate().translationY(p == 1 ? 0 : span).alpha(p == 1 ? 1f : 0f).setDuration(520).setInterpolator(HomeV2.SKY)
                .withEndAction(() -> { if (h.lite && open) (pageAt == 0 ? pageOne : pageTwo).setLayerType(View.LAYER_TYPE_HARDWARE, null); })
                .start();
        if (p == 1 && !twoShown) { twoShown = true; enterTwo(); }
        for (int i = 0; i < dots.length; i++) {
            dots[i].animate().alpha(i == p ? 1f : 0.35f).scaleX(i == p ? 1f : 0.7f).scaleY(i == p ? 1f : 0.7f).setDuration(240).start();
        }
        hint.animate().alpha(p == 0 ? 1f : 0f).setDuration(200).start();
    }

    // ---- the card --------------------------------------------------------------------------------------------------

    private static final int CARD_X = 200, CARD_Y = 110, CARD_W = 1520, CARD_H = 860, HOUR_W = 76;
    private final List<View> twoEnter = new ArrayList<>();
    private final List<WeatherViews.Growing> twoGrow = new ArrayList<>();

    private void build(Now n) {
        open = true;
        pageAt = 0;
        twoShown = false;
        twoEnter.clear();
        twoGrow.clear();
        overlay = new FrameLayout(h.a);
        dim = new View(h.a);
        dim.setBackgroundColor(0xB3050816);
        dim.setAlpha(0f);
        overlay.addView(dim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        card = new FrameLayout(h.a);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, skyColours(n.code, n.day));
        bg.setCornerRadius(h.px(44));
        card.setBackground(bg);
        card.setClipToOutline(true);
        card.setOutlineProvider(h.round(44));
        double moonAge = WeatherViews.moonAge(System.currentTimeMillis());
        card.addView(new WeatherViews.Sky(h.a, n.code, n.day, n.wind >= 12 || n.gusts >= 25, moonAge, h.lite),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pageOne = new FrameLayout(h.a);
        pageTwo = new FrameLayout(h.a);
        pageTwo.setTranslationY(h.px(CARD_H));
        pageTwo.setAlpha(0f);
        card.addView(pageOne, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        card.addView(pageTwo, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(h.px(CARD_W), h.px(CARD_H));
        cl.leftMargin = h.px(CARD_X);
        cl.topMargin = h.px(CARD_Y);
        overlay.addView(card, cl);
        h.root.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        List<View> enter = buildOne(n);
        buildTwo(n, moonAge);
        // Which page: two dots at the card's right edge; under page one, the way to page two.
        dots = new View[2];
        for (int i = 0; i < 2; i++) {
            View d = new View(h.a);
            d.setBackground(h.fill(6, Color.WHITE));
            d.setAlpha(i == 0 ? 1f : 0.35f);
            d.setScaleX(i == 0 ? 1f : 0.7f);
            d.setScaleY(i == 0 ? 1f : 0.7f);
            put(card, d, CARD_W - 30, CARD_H / 2 - 20 + i * 28, 12, 12);
            dots[i] = d;
        }
        TextView more = h.text(18, h.semi, 0xCCFFFFFF);
        more.setLetterSpacing(0.06f);
        more.setGravity(Gravity.CENTER);
        more.setText("▼   AIR, LIFESTYLE AND THE MOON");
        put(card, more, 540, 814, 920, 30);
        hint = more;
        ValueAnimator nudge = ValueAnimator.ofFloat(0f, 1f);
        nudge.setDuration(1600);
        nudge.setRepeatCount(ValueAnimator.INFINITE);
        nudge.setRepeatMode(ValueAnimator.REVERSE);
        nudge.addUpdateListener(an -> more.setTranslationY(h.px(5) * (float) an.getAnimatedValue()));
        nudge.setStartDelay(1500);
        nudge.start();
        running.add(nudge);

        // In: the dim, then the card rising and growing a little, then each piece in turn.
        dim.animate().alpha(1f).setDuration(300).start();
        card.setAlpha(0f);
        card.setScaleX(0.9f);
        card.setScaleY(0.9f);
        card.setTranslationY(h.px(40));
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(480)
                .setInterpolator(new android.view.animation.OvershootInterpolator(0.7f)).start();
        for (int i = 0; i < enter.size(); i++) {
            View v = enter.get(i);
            v.setAlpha(0f);
            v.setTranslationY(h.px(18));
            v.animate().alpha(1f).translationY(0f).setStartDelay(160 + i * 28L).setDuration(380).setInterpolator(HomeV2.SKY).start();
        }
        // A slower TV: once everything is in, the page kept as one finished picture, so the moving sky under it is
        // all that is drawn again each frame.
        if (h.lite) h.main.postDelayed(() -> { if (open && pageAt == 0) pageOne.setLayerType(View.LAYER_TYPE_HARDWARE, null); },
                1400 + enter.size() * 28L);
    }

    // ---- page one: now, the hours, the week, the details -----------------------------------------------------------

    private List<View> buildOne(Now n) {
        List<View> enter = new ArrayList<>();
        FrameLayout p = pageOne;
        TextView where = h.text(30, h.semi, Color.WHITE);
        where.setText("📍 " + place);
        where.setShadowLayer(h.px(8), 0, h.px(2), 0x40000000);
        put(p, where, 70, 54, 460, 44);
        TextView big = h.text(150, h.displayRegular, Color.WHITE);
        big.setShadowLayer(h.px(12), 0, h.px(3), 0x33000000);
        big.setText(Math.round(n.temp) + "°");
        put(p, big, 60, 104, 320, 170);
        // The weather's own icon beside it, alive: bobbing, the sun turning.
        ImageView icon = new ImageView(h.a);
        icon.setImageResource(WeatherLine.icon(n.code, n.day));
        put(p, icon, 340, 132, 130, 130);
        WeatherViews.alive(icon, WeatherViews.kind(n.code) == 0 && n.day, running);
        enter.add(icon);
        TextView sayNow = h.text(30, h.regular, 0xF2FFFFFF);
        sayNow.setText(WeatherLine.describe(n.code));
        put(p, sayNow, 70, 284, 460, 44);
        TextView hiLo = h.text(26, h.regular, 0xD9FFFFFF);
        if (!n.days.isEmpty()) hiLo.setText("H:" + Math.round(n.days.get(0).high) + "°   L:" + Math.round(n.days.get(0).low) + "°");
        put(p, hiLo, 70, 332, 460, 38);
        enter.add(where);
        enter.add(sayNow);
        enter.add(hiLo);
        int target = (int) Math.round(n.temp);
        ValueAnimator count = ValueAnimator.ofInt(target - 9, target);
        count.setDuration(900);
        count.setStartDelay(120);
        count.setInterpolator(HomeV2.SKY);
        count.addUpdateListener(an -> big.setText(an.getAnimatedValue() + "°"));
        count.start();

        String[][] tiles = details(n);
        for (int i = 0; i < tiles.length; i++) {
            View t = tile(tiles[i]);
            put(p, t, 60 + (i % 2) * 232, 404 + (i / 2) * 146, 216, 132);
            enter.add(t);
        }

        LinearLayout hourCard = glassCard();
        hourCard.addView(caption("🕑  " + summary(n).toUpperCase(Locale.ROOT)));
        hours = new HorizontalScrollView(h.a);
        hours.setHorizontalScrollBarEnabled(false);
        hours.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hours.setFocusable(false);
        hours.setHorizontalFadingEdgeEnabled(true);
        hours.setFadingEdgeLength(h.px(40));
        LinearLayout strip = new LinearLayout(h.a);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < n.hours.size(); i++) {
            Hour x = n.hours.get(i);
            LinearLayout col = new LinearLayout(h.a);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView t = h.text(22, i == 0 ? h.semi : h.regular, Color.WHITE);
            t.setText(x.time);
            col.addView(t);
            ImageView ic = new ImageView(h.a);
            ic.setImageResource(WeatherLine.icon(x.code, x.day));
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(h.px(44), h.px(44));
            il.topMargin = h.px(10);
            col.addView(ic, il);
            TextView r = h.text(17, h.semi, 0xFF8FD3FF);
            r.setText(x.rain >= 20 ? x.rain + "%" : " ");
            col.addView(r);
            TextView tp = h.text(26, h.semi, Color.WHITE);
            tp.setText(Math.round(x.temp) + "°");
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tl.topMargin = h.px(4);
            col.addView(tp, tl);
            strip.addView(col, new LinearLayout.LayoutParams(h.px(HOUR_W), ViewGroup.LayoutParams.WRAP_CONTENT));
            if (i < 12) enter.add(col);
        }
        hours.addView(strip);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.topMargin = h.px(14);
        hourCard.addView(hours, hl);
        put(p, hourCard, 540, 54, 920, 230);

        LinearLayout week = glassCard();
        week.addView(caption("📅  7-DAY FORECAST"));
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (Day x : n.days) { min = Math.min(min, x.low); max = Math.max(max, x.high); }
        DateTimeFormatter dayName = DateTimeFormatter.ofPattern("EEE", Locale.getDefault());
        List<WeatherViews.Range> bars = new ArrayList<>();
        for (int i = 0; i < n.days.size(); i++) {
            Day x = n.days.get(i);
            LinearLayout row = new LinearLayout(h.a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            if (i > 0) {
                View line = new View(h.a);
                line.setBackgroundColor(0x2EFFFFFF);
                week.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, h.px(1))));
            }
            TextView dn = h.text(26, h.semi, Color.WHITE);
            dn.setText(i == 0 ? "Today" : x.date.format(dayName));
            row.addView(dn, new LinearLayout.LayoutParams(h.px(130), ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout ico = new LinearLayout(h.a);
            ico.setOrientation(LinearLayout.VERTICAL);
            ico.setGravity(Gravity.CENTER_HORIZONTAL);
            ImageView ic = new ImageView(h.a);
            ic.setImageResource(WeatherLine.icon(x.code, true));
            ico.addView(ic, new LinearLayout.LayoutParams(h.px(40), h.px(40)));
            if (x.rain >= 20) {
                TextView rp = h.text(15, h.semi, 0xFF8FD3FF);
                rp.setText(x.rain + "%");
                ico.addView(rp);
            }
            row.addView(ico, new LinearLayout.LayoutParams(h.px(90), ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView lo = h.text(26, h.regular, 0xA6FFFFFF);
            lo.setText(Math.round(x.low) + "°");
            lo.setGravity(Gravity.END);
            row.addView(lo, new LinearLayout.LayoutParams(h.px(70), ViewGroup.LayoutParams.WRAP_CONTENT));
            WeatherViews.Range bar = new WeatherViews.Range(h.a, min, max, x.low, x.high, i == 0 ? n.temp : Double.NaN);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, h.px(12), 1f);
            bl.leftMargin = bl.rightMargin = h.px(22);
            row.addView(bar, bl);
            bars.add(bar);
            TextView hi = h.text(26, h.semi, Color.WHITE);
            hi.setText(Math.round(x.high) + "°");
            row.addView(hi, new LinearLayout.LayoutParams(h.px(64), ViewGroup.LayoutParams.WRAP_CONTENT));
            week.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h.px(70)));
            enter.add(row);
        }
        put(p, week, 540, 304, 920, 500);
        for (int i = 0; i < bars.size(); i++) bars.get(i).grow(380 + i * 60L, 700);
        return enter;
    }

    // ---- page two: the air, the sun and moon, what the day is good for ---------------------------------------------

    private void buildTwo(Now n, double moonAge) {
        FrameLayout p = pageTwo;
        TextView head = h.text(30, h.semi, Color.WHITE);
        head.setText("📍 " + place + "   ·   " + Math.round(n.temp) + "°  |  " + WeatherLine.describe(n.code));
        put(p, head, 70, 40, 1300, 44);
        twoEnter.add(head);

        // Visibility.
        double km = Double.isNaN(n.visibility) ? Double.NaN : n.visibility / 1000.0;
        LinearLayout vis = glassCard();
        vis.addView(caption("👁  VISIBILITY"));
        vis.addView(value(Double.isNaN(km) ? "—" : km >= 10 ? Math.round(km) + " km" : String.format(Locale.UK, "%.1f km", km)));
        vis.addView(scaleWords("Low", "High"));
        WeatherViews.Slider vs = new WeatherViews.Slider(h.a, Double.isNaN(km) ? 0f : (float) Math.min(1, km / 24.0),
                new int[]{0x66FFFFFF, 0xE6FFFFFF});
        vis.addView(vs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h.px(22)));
        vis.addView(line(Double.isNaN(km) ? "" : km < 1 ? "Very poor visibility" : km < 4 ? "Poor visibility" : km < 10 ? "Moderate visibility"
                : km < 20 ? "Good visibility" : "Excellent visibility"));
        twoGrow.add(vs);

        // Pressure.
        LinearLayout pres = glassCard();
        pres.addView(caption("🧭  PRESSURE"));
        FrameLayout dial = new FrameLayout(h.a);
        WeatherViews.Gauge g = new WeatherViews.Gauge(h.a, Double.isNaN(n.pressure) ? 1013 : n.pressure);
        dial.addView(g, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout mid = new LinearLayout(h.a);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER);
        TextView hpa = h.text(30, h.semi, Color.WHITE);
        hpa.setText(Double.isNaN(n.pressure) ? "—" : String.valueOf(Math.round(n.pressure)));
        mid.addView(hpa);
        TextView unit = h.text(15, h.regular, 0xB3FFFFFF);
        unit.setText("hPa");
        mid.addView(unit);
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        ml.topMargin = h.px(10);
        dial.addView(mid, ml);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h.px(118));
        dl.topMargin = h.px(4);
        pres.addView(dial, dl);
        pres.addView(line(pressureWords(n.pressure)));
        twoGrow.add(g);

        // The air.
        LinearLayout air = glassCard();
        air.addView(caption("🍃  AIR QUALITY"));
        air.addView(value(Double.isNaN(n.aqi) ? "—" : Math.round(n.aqi) + "  " + aqiWord(n.aqi)));
        WeatherViews.Slider as = new WeatherViews.Slider(h.a, Double.isNaN(n.aqi) ? 0f : (float) Math.min(1, n.aqi / 100.0),
                new int[]{0xFF50F0E6, 0xFF50CCAA, 0xFFF0E641, 0xFFFF5050, 0xFF960032, 0xFF7D2181});
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h.px(22));
        al.topMargin = h.px(14);
        air.addView(as, al);
        String pm = Double.isNaN(n.pm25) ? "" : "Fine particles " + Math.round(n.pm25) + " µg/m³";
        String pol = Double.isNaN(n.pollen) ? "" : "Pollen " + (n.pollen < 10 ? "low" : n.pollen < 50 ? "moderate" : n.pollen < 200 ? "high" : "very high");
        air.addView(line(TextUtils.join("\n", nonEmpty(pm, pol))));
        twoGrow.add(as);

        // The sun's path.
        LinearLayout sun = glassCard();
        sun.addView(caption("🌅  SUN"));
        LocalDateTime rise = parse(n.sunrise), set = parse(n.sunset), nowT = LocalDateTime.now();
        float sunAt = (float) Duration.between(rise, nowT).toMinutes() / Math.max(1, Duration.between(rise, set).toMinutes());
        WeatherViews.SunPath sp = new WeatherViews.SunPath(h.a, sunAt);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h.px(96));
        sl.topMargin = h.px(10);
        sun.addView(sp, sl);
        long light = Duration.between(rise, set).toMinutes();
        sun.addView(line("↑ " + hhmm(n.sunrise) + "      ↓ " + hhmm(n.sunset) + "\n" + light / 60 + "h " + light % 60 + "m of daylight"));
        twoGrow.add(sp);

        View[] top = {vis, pres, air, sun};
        for (int i = 0; i < top.length; i++) {
            put(p, top[i], 60 + i * 356, 104, 334, 262);
            twoEnter.add(top[i]);
        }

        // What the day is good for.
        LinearLayout life = glassCard();
        life.addView(caption("💡  LIFESTYLE"));
        String[][] ideas = lifestyle(n, moonAge);
        LinearLayout grid = new LinearLayout(h.a);
        grid.setOrientation(LinearLayout.VERTICAL);
        for (int r = 0; r < 3; r++) {
            LinearLayout row = new LinearLayout(h.a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 3; c++) {
                String[] idea = ideas[r * 3 + c];
                LinearLayout cell = new LinearLayout(h.a);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                TextView em = h.text(40, h.regular, Color.WHITE);
                em.setText(idea[0]);
                em.setGravity(Gravity.CENTER);
                cell.addView(em, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                TextView words = h.text(21, h.regular, Color.WHITE);
                words.setGravity(Gravity.CENTER);
                words.setMaxLines(2);
                words.setText(idea[1]);
                LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                wl.topMargin = h.px(6);
                cell.addView(words, wl);
                row.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                twoEnter.add(cell);
            }
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            grid.addView(row, rl);
        }
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        gl.topMargin = h.px(16);
        life.addView(grid, gl);
        put(p, life, 60, 390, 1012, 430);

        // The moon.
        FrameLayout moonCard = new FrameLayout(h.a);
        LinearLayout mc = glassCard();
        moonCard.addView(mc, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mc.addView(caption("🌗  MOON PHASE"));
        TextView mn = h.text(34, h.semi, Color.WHITE);
        mn.setSingleLine(true);
        mn.setText(WeatherViews.moonName(moonAge));
        LinearLayout.LayoutParams mnl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mnl.topMargin = h.px(12);
        mc.addView(mn, mnl);
        double toFull = ((14.765 - moonAge) % WeatherViews.SYNODIC + WeatherViews.SYNODIC) % WeatherViews.SYNODIC;
        double toNew = WeatherViews.SYNODIC - moonAge;
        DateTimeFormatter dm = DateTimeFormatter.ofPattern("d MMM", Locale.UK);
        mc.addView(pair("Illumination", Math.round(WeatherViews.illumination(moonAge) * 100) + "%"));
        mc.addView(pair("Next full moon", LocalDate.now().plusDays(Math.round(toFull)).format(dm)));
        mc.addView(pair("Next new moon", LocalDate.now().plusDays(Math.round(toNew)).format(dm)));
        WeatherViews.Moon moon = new WeatherViews.Moon(h.a, moonAge, h.lite);
        // Under its name, beside the dates: the moon as it is tonight.
        FrameLayout.LayoutParams mol = new FrameLayout.LayoutParams(h.px(190), h.px(190), Gravity.BOTTOM | Gravity.END);
        mol.bottomMargin = h.px(10);
        mol.rightMargin = h.px(-6);
        moonCard.addView(moon, mol);
        put(p, moonCard, 1096, 390, 364, 430);
        twoEnter.add(moonCard);
    }

    private void enterTwo() {
        for (int i = 0; i < twoEnter.size(); i++) {
            View v = twoEnter.get(i);
            v.setAlpha(0f);
            v.setTranslationY(h.px(24));
            v.setScaleX(0.94f);
            v.setScaleY(0.94f);
            v.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setStartDelay(200 + i * 35L).setDuration(420)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f)).start();
        }
        for (int i = 0; i < twoGrow.size(); i++) twoGrow.get(i).grow(450 + i * 90L, 1100);
    }

    // ---- words -----------------------------------------------------------------------------------------------------

    private static String pressureWords(double p) {
        if (Double.isNaN(p)) return "";
        if (p < 1000) return "Low pressure, unsettled";
        if (p < 1010) return "Slightly low pressure";
        if (p < 1020) return "Normal pressure, comfortable";
        if (p < 1030) return "Slightly high pressure, comfortable";
        return "High pressure, settled";
    }

    private static String aqiWord(double a) {
        return a < 20 ? "Good" : a < 40 ? "Fair" : a < 60 ? "Moderate" : a < 80 ? "Poor" : a < 100 ? "Very poor" : "Extremely poor";
    }

    /**
     * What the day is good for, the phone weather apps' way: running, fishing, hiking, a coat, catching a cold, aching
     * joints, stargazing, golf and flight delays, judged from the temperature, wind, rain, humidity, cloud, visibility
     * and pressure. {emoji, words}.
     */
    private static String[][] lifestyle(Now n, double moonAge) {
        int k = WeatherViews.kind(n.code);
        double t = n.temp, f = n.feels, w = n.wind;
        int rain = n.rainChance;
        boolean storm = k == 5, wet = k == 3 || storm, snow = k == 4;
        double km = Double.isNaN(n.visibility) ? 20 : n.visibility / 1000.0;
        double p = Double.isNaN(n.pressure) ? 1013 : n.pressure;
        String run = storm || snow || f < -2 || f > 28 || w > 25 ? "Poor" : wet || rain > 50 || w > 15 || f < 4 || f > 22 ? "Fair" : "Good";
        String fish = storm || w > 22 ? "Poor" : p >= 1008 && p <= 1022 && w < 14 && !snow ? "Good" : "Fair";
        String hike = storm || snow || km < 2 || w > 25 ? "Poor" : wet || rain > 40 || w > 16 || t < 3 ? "Fair" : "Good";
        String coat = f < 10 ? "Coat recommended" : f < 16 ? "Light jacket" : "No coat needed";
        double range = n.days.isEmpty() ? 0 : n.days.get(0).high - n.days.get(0).low;
        String cold = (t < 8 && n.humidity > 75) || range > 9 ? "High" : t < 13 || range > 6 ? "Moderate" : "Low";
        String joints = (t < 8 && n.humidity > 80) || p < 1000 ? "High" : n.humidity > 75 || p < 1008 ? "Moderate" : "Low";
        double moonLight = WeatherViews.illumination(moonAge);
        String stars = n.cloud > 70 || wet || snow ? "Poor" : n.cloud > 35 || moonLight > 0.7 ? "Fair" : "Good";
        String golf = storm || snow || w > 22 ? "Poor" : wet || rain > 40 || w > 14 || t < 5 ? "Fair" : "Good";
        String flights = storm || snow || km < 1 || n.gusts > 50 ? "High" : n.gusts > 35 || km < 3 ? "Moderate" : "Low";
        return new String[][]{
                {"🏃", run + " running conditions"},
                {"🎣", fish + " fishing conditions"},
                {"🥾", hike + " hiking conditions"},
                {"🧥", coat},
                {"🤧", cold + " chance of getting a cold"},
                {"🦴", joints + " chance of joint pain"},
                {"🔭", stars + " stargazing conditions"},
                {"⛳", golf + " golfing conditions"},
                {"✈", flights + " chance of flight delays"},
        };
    }

    private static List<String> nonEmpty(String... s) {
        List<String> out = new ArrayList<>();
        for (String x : s) if (x != null && !x.isEmpty()) out.add(x);
        return out;
    }

    // ---- pieces ----------------------------------------------------------------------------------------------------

    private void put(FrameLayout in, View v, int x, int y, int w, int hgt) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(h.px(w), h.px(hgt));
        lp.leftMargin = h.px(x);
        lp.topMargin = h.px(y);
        in.addView(v, lp);
    }

    private LinearLayout glassCard() {
        LinearLayout c = new LinearLayout(h.a);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = h.fill(30, 0x24FFFFFF);
        g.setStroke(Math.max(1, h.px(1.5f)), 0x2EFFFFFF);
        c.setBackground(g);
        c.setPadding(h.px(28), h.px(22), h.px(28), h.px(18));
        return c;
    }

    private TextView caption(String s) {
        TextView t = h.text(18, h.semi, 0xB3FFFFFF);
        t.setLetterSpacing(0.08f);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setText(s);
        return t;
    }

    private TextView value(String s) {
        TextView v = h.text(38, h.semi, Color.WHITE);
        v.setText(s);
        v.setSingleLine(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = h.px(8);
        v.setLayoutParams(lp);
        return v;
    }

    private TextView line(String s) {
        TextView t = h.text(18, h.regular, 0xE6FFFFFF);
        t.setText(s);
        t.setMaxLines(2);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = h.px(10);
        t.setLayoutParams(lp);
        return t;
    }

    private View scaleWords(String low, String high) {
        LinearLayout r = new LinearLayout(h.a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        TextView l = h.text(16, h.regular, 0xB3FFFFFF);
        l.setText(low);
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView hi = h.text(16, h.regular, 0xB3FFFFFF);
        hi.setText(high);
        r.addView(hi);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = h.px(10);
        r.setLayoutParams(lp);
        return r;
    }

    private View pair(String k, String v) {
        LinearLayout r = new LinearLayout(h.a);
        r.setOrientation(LinearLayout.VERTICAL);
        TextView kv = h.text(18, h.regular, 0xB3FFFFFF);
        kv.setText(k);
        r.addView(kv);
        TextView vv = h.text(26, h.semi, Color.WHITE);
        vv.setText(v);
        r.addView(vv);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = h.px(16);
        r.setLayoutParams(lp);
        return r;
    }

    /** A detail: {heading, value, the line under it}. */
    private View tile(String[] d) {
        LinearLayout c = glassCard();
        c.setPadding(h.px(22), h.px(18), h.px(22), h.px(14));
        TextView head = h.text(16, h.semi, 0xA6FFFFFF);
        head.setLetterSpacing(0.08f);
        head.setText(d[0]);
        c.addView(head);
        TextView v = h.text(38, h.semi, Color.WHITE);
        v.setText(d[1]);
        v.setSingleLine(true);
        LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vl.topMargin = h.px(6);
        c.addView(v, vl);
        TextView under = h.text(16, h.regular, 0xCCFFFFFF);
        under.setText(d[2]);
        under.setMaxLines(2);
        LinearLayout.LayoutParams ul = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ul.topMargin = h.px(4);
        c.addView(under, ul);
        return c;
    }

    private static String[][] details(Now n) {
        long feels = Math.round(n.feels), temp = Math.round(n.temp);
        String feelsLine = Math.abs(feels - temp) < 2 ? "Close to the real temperature"
                : feels < temp ? (n.wind >= 8 ? "The wind makes it feel colder" : "Feels colder than it is")
                : "Humidity makes it feel warmer";
        String uvWord = n.uvMax <= 2 ? "Low" : n.uvMax <= 5 ? "Moderate" : n.uvMax <= 7 ? "High" : n.uvMax <= 10 ? "Very high" : "Extreme";
        String rise = hhmm(n.sunrise), set = hhmm(n.sunset);
        boolean beforeSet = n.sunset != null && LocalDateTime.now().isBefore(parse(n.sunset));
        boolean beforeRise = n.sunrise != null && LocalDateTime.now().isBefore(parse(n.sunrise));
        String[] sun = beforeRise ? new String[]{"🌅  SUNRISE", rise, "Sunset: " + set}
                : beforeSet ? new String[]{"🌇  SUNSET", set, "Sunrise: " + rise}
                : new String[]{"🌅  SUNRISE", rise, "Sunset was at " + set};
        String gust = n.gusts >= n.wind + 5 ? "gusty" : "";
        return new String[][]{
                {"🌡  FEELS LIKE", feels + "°", feelsLine},
                {"💧  HUMIDITY", n.humidity + "%", "The dew point is " + Math.round(n.dew) + "° right now"},
                {"💨  WIND", Math.round(n.wind) + " mph", gust.isEmpty() ? "From the " + compass(n.windDir) : "Gusts up to " + Math.round(n.gusts) + " mph"},
                {"☀  UV INDEX", String.valueOf(n.uvMax), uvWord + " today"},
                sun,
                {"🌧  RAIN", n.rainChance + "%", n.rain > 0 ? String.format(Locale.ROOT, "%.1f mm in the last hour", n.rain) : "Chance today"},
        };
    }

    private static LocalDateTime parse(String iso) {
        try {
            return LocalDateTime.parse(iso);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private static String hhmm(String iso) {
        return iso != null && iso.length() >= 16 ? iso.substring(11, 16) : "";
    }

    private static String compass(int deg) {
        String[] n = {"north", "north-northeast", "northeast", "east-northeast", "east", "east-southeast", "southeast", "south-southeast",
                "south", "south-southwest", "southwest", "west-southwest", "west", "west-northwest", "northwest", "north-northwest"};
        return n[Math.round(((deg % 360) + 360) % 360 / 22.5f) % 16];
    }

    /** One line on the hours ahead: when the sky changes, else that it holds. */
    private static String summary(Now n) {
        if (n.hours.isEmpty()) return "Hourly forecast";
        int kind = WeatherViews.kind(n.code);
        for (int i = 1; i < Math.min(n.hours.size(), 13); i++) {
            Hour x = n.hours.get(i);
            if (WeatherViews.kind(x.code) != kind) {
                String what = WeatherLine.describe(x.code);
                return (what.isEmpty() ? "A change" : what) + " expected around " + x.time + ":00";
            }
        }
        String what = WeatherLine.describe(n.code);
        return (what.isEmpty() ? "These" : what) + " conditions for the next 12 hours";
    }

    private static int[] skyColours(int code, boolean day) {
        switch (WeatherViews.kind(code)) {
            case 0: return day ? new int[]{0xFF2F7FD8, 0xFF5BA4EA, 0xFF8CC4F2} : new int[]{0xFF070D24, 0xFF101D45, 0xFF22346A};
            case 1: return day ? (code == 3 ? new int[]{0xFF56708E, 0xFF7890AC, 0xFF97ABC2} : new int[]{0xFF3C79B8, 0xFF6797C6, 0xFF97B6D4})
                    : new int[]{0xFF151C2C, 0xFF243049, 0xFF35435E};
            case 2: return day ? new int[]{0xFF6E7B88, 0xFF8D99A5, 0xFFAAB4BD} : new int[]{0xFF262C35, 0xFF3A424E, 0xFF4E5764};
            case 3: return day ? new int[]{0xFF3E4C5E, 0xFF55667B, 0xFF6F8297} : new int[]{0xFF111824, 0xFF1D2838, 0xFF2D3A4D};
            case 4: return day ? new int[]{0xFF7489A3, 0xFF97A9BE, 0xFFC0CCDA} : new int[]{0xFF1E2636, 0xFF33405A, 0xFF4B5873};
            default: return day ? new int[]{0xFF262B3B, 0xFF383F55, 0xFF4C5470} : new int[]{0xFF0E1018, 0xFF1A1E2C, 0xFF2A3044};
        }
    }
}
