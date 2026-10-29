package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * The featured game's pitch: the ball moving through ESPN's live play-by-play (every pass, tackle and shot comes with
 * where on the pitch it happened, and a pass with where it went), with a short trail in the colour of the team on the
 * ball. Home attacks to the right. New plays queue up and play out one after another, faster when they pile up.
 */
final class LivePitch extends View {
    /** One touch: from (x1, y1) to (x2, y2) in the home team's 0-100 frame, by home or away, and what it was. */
    static final class Play {
        String id, label;
        float x1, y1, x2, y2;
        boolean home;
    }

    private static final long STEP_MS = 650L;
    private final float d;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayDeque<Play> queue = new ArrayDeque<>();
    private final List<Play> trail = new ArrayList<>();
    private Play current;
    private long currentAt;
    private int homeColor = 0xFFFFFFFF, awayColor = 0xFFFFD54F;
    private float ballX = 50, ballY = 50;
    interface Listener { void onPlay(Play p); }
    private Listener listener;
    private Bitmap grass;

    LivePitch(Context c, float density) {
        super(c);
        d = density;
    }

    void setColors(int home, int away) {
        homeColor = home;
        awayColor = away;
        invalidate();
    }

    void setListener(Listener l) {
        listener = l;
    }

    /** New plays, oldest first; the first batch only shows its last few, so the pitch never replays a whole half. */
    void add(List<Play> plays, boolean first) {
        int from = first ? Math.max(0, plays.size() - 6) : 0;
        for (int i = from; i < plays.size(); i++) queue.add(plays.get(i));
        if (current == null) next();
    }

    void clear() {
        queue.clear();
        trail.clear();
        current = null;
        invalidate();
    }

    private void next() {
        current = queue.poll();
        currentAt = System.currentTimeMillis();
        if (current != null) {
            if (listener != null) listener.onPlay(current);
            postInvalidateOnAnimation();
        }
    }

    private long step() {
        // A backlog plays faster, so the ball catches up with the game.
        return queue.size() > 8 ? STEP_MS / 3 : queue.size() > 3 ? STEP_MS / 2 : STEP_MS;
    }

    private void drawPitch(Canvas c, float w, float h) {
        float r = 10 * d;
        // Grass: a deep green with mown stripes.
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, 0, 0, h, 0xFF1F6B3A, 0xFF17522C, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(0, 0, w, h), r, r, paint);
        paint.setShader(null);
        paint.setColor(0x14FFFFFF);
        for (int i = 0; i < 12; i += 2) c.drawRect(w * i / 12f, 0, w * (i + 1) / 12f, h, paint);
        // Lines: touchlines, halfway, centre circle, both boxes and six-yard boxes, penalty spots.
        float m = 6 * d, L = m, T = m, R = w - m, B = h - m, pw = R - L, ph = B - T;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1, 1.2f * d));
        paint.setColor(0xB3FFFFFF);
        c.drawRect(L, T, R, B, paint);
        c.drawLine(L + pw / 2, T, L + pw / 2, B, paint);
        c.drawCircle(L + pw / 2, T + ph / 2, ph * 0.135f, paint);
        float boxD = pw * 0.157f, boxW = ph * 0.593f, sixD = pw * 0.052f, sixW = ph * 0.269f;
        c.drawRect(L, T + (ph - boxW) / 2, L + boxD, T + (ph + boxW) / 2, paint);
        c.drawRect(R - boxD, T + (ph - boxW) / 2, R, T + (ph + boxW) / 2, paint);
        c.drawRect(L, T + (ph - sixW) / 2, L + sixD, T + (ph + sixW) / 2, paint);
        c.drawRect(R - sixD, T + (ph - sixW) / 2, R, T + (ph + sixW) / 2, paint);
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(L + pw * 0.105f, T + ph / 2, 1.6f * d, paint);
        c.drawCircle(R - pw * 0.105f, T + ph / 2, 1.6f * d, paint);
        c.drawCircle(L + pw / 2, T + ph / 2, 1.6f * d, paint);
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        // The grass and lines never change, so they are drawn once into a bitmap: the ball redraws every frame.
        if (grass == null || grass.getWidth() != (int) w || grass.getHeight() != (int) h) {
            if (w <= 0 || h <= 0) return;
            grass = Bitmap.createBitmap((int) w, (int) h, Bitmap.Config.ARGB_8888);
            drawPitch(new Canvas(grass), w, h);
        }
        c.drawBitmap(grass, 0, 0, null);
        float m = 6 * d, L = m, T = m, R = w - m, B = h - m, pw = R - L, ph = B - T;

        // The trail: the last few touches, fading with age.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(2.2f * d);
        for (int i = 0; i < trail.size(); i++) {
            Play p = trail.get(i);
            int a = 60 + 120 * (i + 1) / Math.max(1, trail.size());
            paint.setColor((a << 24) | ((p.home ? homeColor : awayColor) & 0xFFFFFF));
            c.drawLine(L + pw * p.x1 / 100f, T + ph * p.y1 / 100f, L + pw * p.x2 / 100f, T + ph * p.y2 / 100f, paint);
        }
        // The ball, on its way along the current touch.
        if (current != null) {
            float t = Math.min(1f, (System.currentTimeMillis() - currentAt) / (float) step());
            float e = t * t * (3 - 2 * t);
            ballX = current.x1 + (current.x2 - current.x1) * e;
            ballY = current.y1 + (current.y2 - current.y1) * e;
            paint.setColor(0xF2000000 | ((current.home ? homeColor : awayColor) & 0xFFFFFF));
            c.drawLine(L + pw * current.x1 / 100f, T + ph * current.y1 / 100f, L + pw * ballX / 100f, T + ph * ballY / 100f, paint);
            if (t >= 1f) {
                trail.add(current);
                while (trail.size() > 6) trail.remove(0);
                next();
            } else {
                postInvalidateOnAnimation();
            }
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x66000000);
        c.drawCircle(L + pw * ballX / 100f + d, T + ph * ballY / 100f + d, 4.2f * d, paint);
        paint.setColor(Color.WHITE);
        c.drawCircle(L + pw * ballX / 100f, T + ph * ballY / 100f, 4.2f * d, paint);
    }

    // ---- data ------------------------------------------------------------------------------------

    /**
     * The latest plays of the game, oldest first, in the home team's frame: ESPN gives each team's touches from its
     * own end, so the away team's are turned round. Off the main thread.
     */
    static List<Play> latest(String slug, String id, String homeId) {
        List<Play> out = new ArrayList<>();
        try {
            String base = "https://sports.core.api.espn.com/v2/sports/soccer/leagues/" + slug + "/events/" + id
                    + "/competitions/" + id + "/plays?limit=50";
            JSONObject first = new JSONObject(get(base + "&page=1"));
            int pages = Math.max(1, first.optInt("pageCount", 1));
            List<JSONObject> items = new ArrayList<>();
            // The last page can be nearly empty: the one before it too, for a full set.
            for (int p = Math.max(1, pages - 1); p <= pages; p++) {
                JSONObject page = p == 1 ? first : new JSONObject(get(base + "&page=" + p));
                JSONArray arr = page.optJSONArray("items");
                for (int i = 0; arr != null && i < arr.length(); i++) items.add(arr.getJSONObject(i));
            }
            for (JSONObject x : items) {
                if (!x.has("fieldPositionX")) continue;
                Play p = new Play();
                p.id = x.optString("id");
                String team = x.optJSONObject("team") == null ? "" : x.getJSONObject("team").optString("$ref", "");
                p.home = team.contains("/teams/" + homeId + "?") || team.endsWith("/teams/" + homeId);
                float ax = (float) x.optDouble("fieldPositionX", 50), ay = (float) x.optDouble("fieldPositionY", 50);
                float bx = x.isNull("fieldPosition2X") ? ax : (float) x.optDouble("fieldPosition2X", ax);
                float by = x.isNull("fieldPosition2Y") ? ay : (float) x.optDouble("fieldPosition2Y", ay);
                if (!p.home) { ax = 100 - ax; ay = 100 - ay; bx = 100 - bx; by = 100 - by; }
                p.x1 = clamp(ax); p.y1 = clamp(ay); p.x2 = clamp(bx); p.y2 = clamp(by);
                String clock = x.optJSONObject("clock") == null ? "" : x.getJSONObject("clock").optString("displayValue", "");
                String type = x.optJSONObject("type") == null ? "" : x.getJSONObject("type").optString("text", "");
                String who = "";
                JSONArray parts = x.optJSONArray("participants");
                String text = x.optString("text", "");
                int paren = text.indexOf(" (");
                if (paren > 0) who = GoalWatchService.surname(text.substring(0, paren));
                p.label = (clock.isEmpty() ? "" : clock + "   ") + (who.isEmpty() ? "" : who + "  ·  ") + type;
                out.add(p);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static float clamp(float v) {
        return Math.max(0, Math.min(100, v));
    }

    private static String get(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(10000);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0 && out.size() < 2 * 1024 * 1024) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            conn.disconnect();
        }
    }
}
