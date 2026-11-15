package com.gathin.broadcast;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The shot map that slides out beside the scoreboard at half time and full time (TV and phone): a small pitch, the home
 * side shooting to the right and the away side to the left, a dot for every shot (bigger the better the chance, by its
 * expected goals), solid for one on target, a ring for one off it, gold with a glow for a goal; over it the half, under
 * it each side's shots, on target and expected goals. From FotMob's match details (content.shotmap.shots: x 0 to 105
 * towards the goal, y 0 to 68). Drawn, not an image.
 */
public final class ShotMap {
    private ShotMap() { }

    /** One shot: its side, where it was taken (FotMob's x 0..105 towards the goal attacked, y 0..68), how good, what came of it. */
    public static final class Shot {
        public boolean home, firstHalf;
        public float x, y;
        public double xg;
        /** "goal", "saved", "miss", "blocked", "post". */
        public String kind = "miss";
        public String player = "";
        public int minute;
    }

    /** Pure: the shots in FotMob's match content (an own goal counted for the side it went in for); none when it has none. */
    public static List<Shot> parse(JSONObject content, int homeTeamId) {
        List<Shot> out = new ArrayList<>();
        JSONObject sm = content == null ? null : content.optJSONObject("shotmap");
        JSONArray shots = sm == null ? null : sm.optJSONArray("shots");
        for (int i = 0; shots != null && i < shots.length(); i++) {
            JSONObject s = shots.optJSONObject(i);
            if (s == null || !s.has("x") || !s.has("y")) continue;
            Shot o = new Shot();
            boolean own = s.optBoolean("isOwnGoal");
            boolean byHome = s.optInt("teamId") == homeTeamId;
            o.home = own != byHome;
            o.x = (float) s.optDouble("x");
            o.y = (float) s.optDouble("y");
            o.xg = s.isNull("expectedGoals") ? 0 : s.optDouble("expectedGoals", 0);
            String t = s.optString("eventType");
            o.kind = "Goal".equals(t) ? "goal" : s.optBoolean("isBlocked") ? "blocked" : "AttemptSaved".equals(t) ? "saved"
                    : "Post".equals(t) ? "post" : "miss";
            if (own) {
                // Turned in at the other end: drawn where it went in, a goal for the side it counts for.
                o.kind = "goal";
                o.x = 104f;
                o.y = 34f;
            }
            o.player = s.optString("lastName", s.optString("playerName"));
            o.minute = s.optInt("min");
            o.firstHalf = "FirstHalf".equals(s.optString("period")) || o.minute <= 45 && !"SecondHalf".equals(s.optString("period"));
            out.add(o);
        }
        return out;
    }

    /** Pure: the first half's shots only (half time), or every shot. */
    public static List<Shot> of(List<Shot> all, boolean firstHalfOnly) {
        List<Shot> out = new ArrayList<>();
        for (Shot s : all) if (!firstHalfOnly || s.firstHalf) out.add(s);
        return out;
    }

    /** Pure: a side's {shots, on target (goals and saved), goals} and its expected goals. */
    public static int[] count(List<Shot> shots, boolean home) {
        int n = 0, on = 0, goals = 0;
        for (Shot s : shots) {
            if (s.home != home) continue;
            n++;
            if ("goal".equals(s.kind) || "saved".equals(s.kind)) on++;
            if ("goal".equals(s.kind)) goals++;
        }
        return new int[]{n, on, goals};
    }

    public static double xg(List<Shot> shots, boolean home) {
        double t = 0;
        for (Shot s : shots) if (s.home == home) t += s.xg;
        return t;
    }

    /** Pure: where a shot sits on the drawn pitch, 0..1 across (home shooting right) and down. */
    public static float[] place(Shot s) {
        float fx = Math.max(0f, Math.min(1f, s.x / 105f)), fy = Math.max(0f, Math.min(1f, s.y / 68f));
        return s.home ? new float[]{fx, fy} : new float[]{1f - fx, 1f - fy};
    }

    private static final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    /**
     * The card w by h: its ground (the board's), "SHOT MAP" and the half, the pitch with every shot, each side's line
     * under it in its colour ({three letters, colour} each).
     */
    public static Bitmap draw(List<Shot> shots, int w, int h, int ground, String title, String homeCode, int homeColour,
                              String awayCode, int awayColour) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        float r = h * 0.06f;
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(ground);
        cv.drawRoundRect(new RectF(0, 0, w, h), r, r, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, h * 0.004f));
        p.setColor(0x38FFFFFF);
        cv.drawRoundRect(new RectF(0.5f, 0.5f, w - 0.5f, h - 0.5f), r, r, p);
        float pad = h * 0.06f;
        // Title row.
        float th = h * 0.12f;
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        p.setTextSize(th * 0.62f);
        p.setLetterSpacing(0.12f);
        p.setColor(0xFFFFFFFF);
        p.setTextAlign(Paint.Align.LEFT);
        float ty = pad + th * 0.72f;
        cv.drawText("SHOT MAP", pad, ty, p);
        p.setColor(0xA6FFFFFF);
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        p.setTextSize(th * 0.5f);
        p.setTextAlign(Paint.Align.RIGHT);
        cv.drawText(title.toUpperCase(Locale.UK), w - pad, ty, p);
        // The pitch: 105 by 68, as wide as the card allows.
        float top = pad + th + h * 0.03f, foot = h * 0.24f;
        float ph = h - top - foot - pad * 0.5f, pw = Math.min(w - 2 * pad, ph * 105f / 68f);
        ph = pw * 68f / 105f;
        float px = (w - pw) / 2f, py = top;
        p.setShader(new LinearGradient(0, py, 0, py + ph, 0xFF1F5A35, 0xFF174A2B, Shader.TileMode.CLAMP));
        cv.drawRoundRect(new RectF(px, py, px + pw, py + ph), h * 0.015f, h * 0.015f, p);
        p.setShader(null);
        // Mown stripes.
        p.setColor(0x10FFFFFF);
        for (int i = 0; i < 10; i += 2) cv.drawRect(px + pw * i / 10f, py, px + pw * (i + 1) / 10f, py + ph, p);
        // Its lines.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, h * 0.0045f));
        p.setColor(0x8CFFFFFF);
        float sx = pw / 105f, sy = ph / 68f;
        cv.drawRect(px, py, px + pw, py + ph, p);
        cv.drawLine(px + pw / 2f, py, px + pw / 2f, py + ph, p);
        cv.drawCircle(px + pw / 2f, py + ph / 2f, 9.15f * sx, p);
        for (int side = 0; side < 2; side++) {
            float gx = side == 0 ? px : px + pw, dir = side == 0 ? 1 : -1;
            cv.drawRect(Math.min(gx, gx + dir * 16.5f * sx), py + (34 - 20.16f) * sy, Math.max(gx, gx + dir * 16.5f * sx), py + (34 + 20.16f) * sy, p);
            cv.drawRect(Math.min(gx, gx + dir * 5.5f * sx), py + (34 - 9.16f) * sy, Math.max(gx, gx + dir * 5.5f * sx), py + (34 + 9.16f) * sy, p);
        }
        // The shots: the misses first, the goals on top.
        float unit = Math.min(sx, sy);
        for (int pass = 0; pass < 2; pass++) {
            for (Shot s : shots) {
                boolean goal = "goal".equals(s.kind);
                if (goal != (pass == 1)) continue;
                float[] at = place(s);
                float cx = px + at[0] * pw, cy = py + at[1] * ph;
                float rad = unit * (1.1f + (float) Math.sqrt(Math.max(0.01, s.xg)) * 3.4f);
                int col = s.home ? homeColour : awayColour;
                if (goal) {
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(0x55FFD34D);
                    cv.drawCircle(cx, cy, rad * 1.9f, p);
                    p.setColor(0xFFFFD34D);
                    cv.drawCircle(cx, cy, rad, p);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(Math.max(1f, rad * 0.22f));
                    p.setColor(0xFFFFFFFF);
                    cv.drawCircle(cx, cy, rad, p);
                } else if ("saved".equals(s.kind)) {
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(lift(col));
                    cv.drawCircle(cx, cy, rad, p);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(Math.max(1f, rad * 0.18f));
                    p.setColor(0xCCFFFFFF);
                    cv.drawCircle(cx, cy, rad, p);
                } else {
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(Math.max(1f, rad * 0.32f));
                    p.setColor(("blocked".equals(s.kind) ? 0x99000000 : 0xFF000000) | (lift(col) & 0xFFFFFF));
                    cv.drawCircle(cx, cy, rad * 0.85f, p);
                }
            }
        }
        // Each side's line under it, the home side first: its colour, its letters, shots and on target, its expected
        // goals at the right; all of it a little smaller where the card is narrow.
        p.setStyle(Paint.Style.FILL);
        // One size for both lines: the largest at which the longer fits.
        float ls = foot * 0.30f;
        for (int side = 0; side < 2; side++) {
            int[] c = count(shots, side == 0);
            String mid = c[0] + (c[0] == 1 ? " shot" : " shots") + " · " + c[1] + " on target";
            String xgWords = "xG " + String.format(Locale.UK, "%.2f", xg(shots, side == 0));
            for (int k = 0; k < 8; k++) {
                p.setTextSize(ls);
                p.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                float need = ls * 1.3f + p.measureText("WWWW") + p.measureText(xgWords);
                p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                need += p.measureText(mid) + ls;
                if (need <= pw) break;
                ls *= 0.9f;
            }
        }
        for (int side = 0; side < 2; side++) {
            boolean home = side == 0;
            int[] c = count(shots, home);
            float ly = py + ph + foot * (home ? 0.44f : 0.86f);
            String code = home ? homeCode : awayCode;
            String mid = c[0] + (c[0] == 1 ? " shot" : " shots") + " · " + c[1] + " on target";
            String xgWords = "xG " + String.format(Locale.UK, "%.2f", xg(shots, home));
            float dot = ls * 0.34f;
            p.setColor(lift(home ? homeColour : awayColour));
            cv.drawCircle(px + dot, ly - ls * 0.34f, dot, p);
            p.setTextAlign(Paint.Align.LEFT);
            p.setLetterSpacing(0.02f);
            p.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            p.setTextSize(ls);
            p.setColor(0xFFFFFFFF);
            cv.drawText(code, px + dot * 3.2f, ly, p);
            float cw = p.measureText("WWWW");
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            p.setColor(0xD9FFFFFF);
            cv.drawText(mid, px + dot * 3.2f + cw, ly, p);
            p.setTextAlign(Paint.Align.RIGHT);
            p.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            p.setColor(0xFFFFFFFF);
            cv.drawText(xgWords, px + pw, ly, p);
        }
        p.setTextAlign(Paint.Align.LEFT);
        p.setLetterSpacing(0f);
        return b;
    }

    /** A colour lifted to show on the dark pitch (a black or navy kit as a soft grey-blue). */
    static int lift(int c) {
        float[] hsv = new float[3];
        Color.colorToHSV(c | 0xFF000000, hsv);
        if (hsv[2] < 0.55f) hsv[2] = 0.75f;
        if (hsv[1] < 0.15f && hsv[2] < 0.9f) hsv[2] = 0.92f;
        return Color.HSVToColor(hsv);
    }
}
