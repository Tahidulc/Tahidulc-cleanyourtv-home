package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The scores box as the broadcasters' bar: each club's code on a slanted wedge in its own colour, the two scores
 * either side of the competition's logo in the dark middle, and under the middle a tab with the clock in minutes and
 * seconds going on between reads (GameClock; 45:00 and +2 in added time), HT, FT or the kick-off time. A goal plays
 * out as the broadcasters' bar does, in the scoring club's colours: its colour sweeps across from its end with a flash,
 * its code at the left, its crest popping in on a chevron of its second colour where the competition's logo was, GOAL
 * dropping in letter by letter at the right (its O a spinning ball), the clock carrying on under it; then it pulls
 * back and the new number rolls up. Drawn in code at any width.
 */
public final class ScoreBug extends FrameLayout {
    /** How many times as wide as high it is drawn (the clock's tab included). */
    public static final float RATIO = 5.4f;
    private static final long GOAL_MS = 3600, BACK_AT = 2700, ROLL_AT = 3100;

    /** The last score each game was shown with, to know a goal when it comes: {home, away}. */
    private static final Map<String, int[]> LAST = new HashMap<>();

    private final ImageView comp, crest;
    private Game game;
    private String tag = "";
    private boolean logoShown;
    /** The competition's logo drawn by the box itself (a picture of it made off screen, for a notification). */
    private Bitmap logoBitmap;
    private int homeKit = 0xFF2A4FD0, awayKit = 0xFFF4F5FA, homeInk = Color.WHITE, awayInk = 0xFF10142E, homeAlt, awayAlt;
    private String goalSide = "";
    private long goalAt;
    private int goalFrom;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface plain = Typeface.create("sans-serif-condensed", Typeface.BOLD),
            italic = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC);
    private final Path path = new Path();
    private final RectF r = new RectF();
    private Shader middle, gloss, tabFill;
    private float u;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            invalidate();
            if (running()) postDelayed(this, 1000);
        }
    };

    public ScoreBug(Context c) {
        super(c);
        setWillNotDraw(false);
        setClipChildren(false);
        comp = new ImageView(c);
        comp.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(comp, new LayoutParams(0, 0));
        // The scoring club's crest for a goal: loaded here, drawn by the box itself (never shown as a view).
        crest = new ImageView(c);
        crest.setVisibility(INVISIBLE);
        addView(crest, new LayoutParams(0, 0));
        text.setTypeface(plain);
        text.setFontFeatureSettings("tnum");
    }

    public void show(Game g) {
        boolean same = game != null && g != null && game.id != null && game.id.equals(g.id);
        game = g;
        kits(g);
        // The competition's logo for dark grounds (the scoreboard's, else ESPN's by its id); its tag when none loads.
        String logo = Competitions.logo(g);
        tag = g.league == null ? "" : g.league;
        comp.setContentDescription(g.leagueName == null || g.leagueName.isEmpty() ? tag : g.leagueName);
        logoShown = logo != null;
        comp.setVisibility(VISIBLE);
        if (logo != null) {
            Ui.load(comp, logo, null, () -> {
                logoShown = false;
                comp.setVisibility(INVISIBLE);
                invalidate();
            });
        } else {
            Ui.clear(comp);
        }
        // A goal since this game was last shown: it plays out (not the first time it is seen).
        if (g.id != null && g.state != Game.PRE) {
            int[] was;
            synchronized (LAST) {
                was = LAST.get(g.id);
                LAST.put(g.id, new int[]{g.homeScore, g.awayScore});
            }
            if (was != null && (same || isAttachedToWindow())) {
                if (g.homeScore > was[0]) celebrate("home", was[0]);
                else if (g.awayScore > was[1]) celebrate("away", was[1]);
            }
        }
        String[] clock = GameClock.show(g, System.currentTimeMillis());
        setContentDescription(g.homeName + " " + (g.state == Game.PRE ? "v" : g.score()) + " " + g.awayName + ", "
                + (clock != null ? clock[0] + (clock[1].isEmpty() ? "" : " " + clock[1]) : under(g)));
        removeCallbacks(tick);
        if (running() && isAttachedToWindow()) postDelayed(tick, 1000);
        invalidate();
    }

    /** Plays a goal for a side ("home" or "away"), its number rolling up from the one it had. */
    public void celebrate(String side, int from) {
        goalSide = side;
        goalFrom = from;
        goalAt = System.currentTimeMillis();
        loadCrest(side);
        postInvalidateOnAnimation();
    }

    private void loadCrest(String side) {
        if (game == null) return;
        boolean home = "home".equals(side);
        String url = home ? (game.homeLogo != null ? game.homeLogo : Espn.crest(game.homeId))
                : (game.awayLogo != null ? game.awayLogo : Espn.crest(game.awayId));
        Ui.load(crest, url);
    }

    /** For a still picture of the goal at a moment of it (ms since it began), as celebrate() plays it. */
    void goalAt(String side, int from, long since) {
        goalSide = side;
        goalFrom = from;
        goalAt = System.currentTimeMillis() - since;
        loadCrest(side);
    }

    private void kits(Game g) {
        int[] k = Glyphs.kits("", "", g.homeColor, "", "", g.awayColor);
        // Not lost against the dark middle: a near-black club colour lifted.
        homeKit = Glyphs.onDark(k[0]);
        awayKit = Glyphs.onDark(k[1]);
        if (Glyphs.distance(homeKit, awayKit) < 0.5) awayKit = Glyphs.luminance(homeKit) > 0.5 ? 0xFF2A2F45 : 0xFFF2F3F7;
        homeInk = Glyphs.ink(homeKit);
        awayInk = Glyphs.ink(awayKit);
        homeAlt = second(homeKit, g.homeAlt);
        awayAlt = second(awayKit, g.awayAlt);
    }

    /** A club's second colour for the chevron behind its crest: ESPN's when it stands out, else its own made darker. */
    private static int second(int kit, String alt) {
        int a = Glyphs.color(alt, 0);
        if (a != 0 && Glyphs.distance(a, kit) >= 0.45) return a;
        return Color.rgb(Color.red(kit) * 55 / 100, Color.green(kit) * 55 / 100, Color.blue(kit) * 55 / 100);
    }

    private boolean running() {
        return game != null && game.state == Game.LIVE && !game.halftime;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        removeCallbacks(tick);
        if (running()) postDelayed(tick, 1000);
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        float d = getResources().getDisplayMetrics().density;
        int w = MeasureSpec.getSize(widthSpec);
        if (MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED) w = Math.round(64 * d * RATIO);
        int h = Math.round(Math.max(48 * d, Math.min(80 * d, w / RATIO)));
        if (MeasureSpec.getMode(heightSpec) == MeasureSpec.EXACTLY) h = MeasureSpec.getSize(heightSpec);
        int s = Math.round(h * 0.5f);
        comp.measure(MeasureSpec.makeMeasureSpec(s, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(s, MeasureSpec.EXACTLY));
        setMeasuredDimension(w, h);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int right, int b) {
        int w = right - l, h = b - t;
        int s = comp.getMeasuredWidth();
        float cx = w / 2f, cy = 21 * h / 64f;
        comp.layout(Math.round(cx - s / 2f), Math.round(cy - s / 2f), Math.round(cx + s / 2f), Math.round(cy + s / 2f));
        if (changed) shapes(w, h);
    }

    private void shapes(int w, int h) {
        u = h / 64f;
        float barB = 41 * u;
        middle = new LinearGradient(0, 0, 0, barB, new int[]{0xFF3A1458, 0xFF24093C, 0xFF16052A}, null, Shader.TileMode.CLAMP);
        gloss = new LinearGradient(0, 0, 0, barB * 0.55f, new int[]{0x52FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
        tabFill = new LinearGradient(0, barB, 0, h, new int[]{0xFF2B0F45, 0xFF16052A}, null, Shader.TileMode.CLAMP);
    }

    private static float ease(float t) {
        t = Math.max(0, Math.min(1, t));
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        if (u == 0 || middle == null) shapes((int) w, (int) h);
        float barT = 1 * u, barB = 41 * u, slant = 9 * u, mid = (barT + barB) / 2f;
        float midL = w * 0.335f, midR = w * 0.665f;
        long since = goalSide.isEmpty() ? GOAL_MS : System.currentTimeMillis() - goalAt;
        boolean playing = since < GOAL_MS;
        // How far the scoring side's colour has swept across: out (0 to 0.38 s), held, back (2.7 to 3.1 s).
        float sweep = !playing ? 0 : since < 380 ? ease(since / 380f) : since < BACK_AT ? 1 : 1 - ease((since - BACK_AT) / 400f);
        boolean home = "home".equals(goalSide);

        // The shadow.
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x4D000000);
        c.drawRect(0, barT + 2 * u, w, barB + 2 * u, p);

        // The wedges and the codes on them.
        float homeEnd = midL + 18 * u, awayStart = midR - 18 * u;
        if (playing && home) homeEnd += (w + slant - homeEnd) * sweep;
        if (playing && !home) awayStart -= (awayStart + slant) * sweep;
        // The scoring side's wedge last, over the other as it sweeps across.
        if (playing && home) {
            wedge(c, awayStart, w, barT, barB, slant, awayKit, false);
            wedge(c, 0, homeEnd, barT, barB, slant, homeKit, true);
        } else {
            wedge(c, 0, homeEnd, barT, barB, slant, homeKit, true);
            wedge(c, awayStart, w, barT, barB, slant, awayKit, false);
        }
        text.setTypeface(plain);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(23 * u);
        if (game != null && sweep < 0.5f) {
            text.setColor(homeInk);
            c.drawText(code(game.home, game.homeName), midL / 2f, baseline(mid), text);
            text.setColor(awayInk);
            c.drawText(code(game.away, game.awayName), (midR + w) / 2f, baseline(mid), text);
        }

        // The clock's tab under the middle, there through a goal too.
        float tabW = Math.max(64 * u, (midR - midL) * 0.55f);
        r.set(w / 2f - tabW / 2, barB - 6 * u, w / 2f + tabW / 2, h - 1 * u);
        p.setColor(Color.BLACK);
        p.setShader(tabFill);
        c.drawRoundRect(r, 6 * u, 6 * u, p);
        p.setShader(null);
        if (game != null) {
            String[] clock = GameClock.show(game, System.currentTimeMillis());
            text.setTypeface(plain);
            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(Color.WHITE);
            text.setTextSize(16 * u);
            float tabMid = (barB + h) / 2f;
            if (clock != null) {
                c.drawText(clock[0], w / 2f, baseline(tabMid), text);
                if (!clock[1].isEmpty()) {
                    text.setTextSize(13 * u);
                    float aw = text.measureText(clock[1]) + 10 * u, ax = w / 2f + tabW / 2 + 4 * u;
                    r.set(ax, barB + 3 * u, ax + aw, h - 3 * u);
                    p.setColor(0xFFC8102E);
                    c.drawRoundRect(r, 5 * u, 5 * u, p);
                    c.drawText(clock[1], r.centerX(), baseline(r.centerY()), text);
                }
            } else {
                text.setTextSize(game.state == Game.PRE ? 14 * u : 16 * u);
                c.drawText(under(game), w / 2f, baseline(tabMid), text);
            }
        }

        // The middle: dark, the two scores either side of the competition's logo.
        boolean middleShown = sweep < 1;
        comp.setAlpha(middleShown && sweep < 0.6f ? 1f : 0f);
        if (middleShown) {
            path.reset();
            path.moveTo(midL + slant, barT);
            path.lineTo(midR, barT);
            path.lineTo(midR - slant, barB);
            path.lineTo(midL, barB);
            path.close();
            p.setColor(Color.BLACK);
            p.setShader(middle);
            c.drawPath(path, p);
            p.setShader(gloss);
            c.drawPath(path, p);
            p.setShader(null);
            if (game != null) {
                float hx = w / 2f - 30 * u, ax2 = w / 2f + 30 * u;
                text.setColor(Color.WHITE);
                if (game.state == Game.PRE) {
                    text.setTextSize(17 * u);
                    c.drawText("v", hx, baseline(mid), text);
                    c.drawText("v", ax2, baseline(mid), text);
                } else {
                    text.setTextSize(31 * u);
                    number(c, String.valueOf(game.homeScore), hx, mid, playing && home, since);
                    number(c, String.valueOf(game.awayScore), ax2, mid, playing && !home, since);
                }
            }
            if (logoBitmap != null) {
                float half = 16 * u;
                float k = Math.min(2 * half / logoBitmap.getWidth(), 2 * half / logoBitmap.getHeight());
                float bw = logoBitmap.getWidth() * k, bh = logoBitmap.getHeight() * k;
                r.set(w / 2f - bw / 2, mid - bh / 2, w / 2f + bw / 2, mid + bh / 2);
                c.drawBitmap(logoBitmap, null, r, text);
            } else if (!logoShown) {
                text.setColor(Color.WHITE);
                text.setTextSize(11 * u);
                c.drawText(Glyphs.fit(tag, text, 36 * u), w / 2f, baseline(mid), text);
            }
        }

        // The goal itself, as the broadcasters' bar plays it: a flash, the scoring club's code at the left, its crest
        // popping in on a chevron of its second colour where the competition's logo was, and GOAL dropping in letter by
        // letter at the right, its O a football.
        if (playing && game != null) {
            if (since < 240) {
                p.setColor(((int) (140 * (1 - since / 240f)) << 24) | 0xFFFFFF);
                c.drawRect(0, barT, w, barB, p);
            }
            if (sweep > 0.92f) {
                int ink = home ? homeInk : awayInk, alt = home ? homeAlt : awayAlt;
                float fade = since < BACK_AT ? 1 : sweep;
                c.save();
                c.clipRect(0, barT, w, barB);
                // The code.
                text.setTypeface(plain);
                text.setTextAlign(Paint.Align.CENTER);
                text.setTextSize(23 * u);
                text.setColor(ink);
                text.setAlpha(Math.round(255 * fade));
                c.drawText(code(home ? game.home : game.away, home ? game.homeName : game.awayName), midL / 2f, baseline(mid), text);
                // The chevron sliding in, then the crest popping onto it.
                float cin = ease((since - 300) / 300f), cx = w / 2f;
                float shift = (1 - cin) * -50 * u;
                path.reset();
                path.moveTo(cx - 40 * u + shift, barT);
                path.lineTo(cx + 8 * u + shift, barT);
                path.lineTo(cx + 26 * u + shift, mid);
                path.lineTo(cx + 8 * u + shift, barB);
                path.lineTo(cx - 40 * u + shift, barB);
                path.lineTo(cx - 22 * u + shift, mid);
                path.close();
                p.setColor(alt);
                p.setAlpha(Math.round(255 * cin * fade));
                c.drawPath(path, p);
                p.setAlpha(255);
                android.graphics.drawable.Drawable cd = crest.getDrawable();
                float pin = (since - 420) / 330f;
                if (cd != null && pin > 0) {
                    float t = Math.min(1, pin);
                    // Overshoots a little and settles, as a pop.
                    float pop = t < 1 ? (float) (1 + 0.18 * Math.sin(Math.PI * t)) * ease(t) : 1;
                    float half = 17 * u * pop;
                    int iw = Math.max(1, cd.getIntrinsicWidth()), ih = Math.max(1, cd.getIntrinsicHeight());
                    float k = Math.min(2 * half / iw, 2 * half / ih);
                    cd.setBounds(Math.round(cx - iw * k / 2), Math.round(mid - ih * k / 2), Math.round(cx + iw * k / 2), Math.round(mid + ih * k / 2));
                    cd.setAlpha(Math.round(255 * fade));
                    cd.draw(c);
                    cd.setAlpha(255);
                }
                // GOAL, a letter at a time, its O a ball.
                text.setTextSize(25 * u);
                String[] letters = {"G", "O", "A", "L"};
                float[] widths = new float[4];
                float total = 0, gap = 2 * u;
                for (int k = 0; k < 4; k++) {
                    widths[k] = k == 1 ? 21 * u : text.measureText(letters[k]);
                    total += widths[k] + (k < 3 ? gap : 0);
                }
                float x = (midR + w) / 2f - total / 2f + 4 * u;
                text.setTextAlign(Paint.Align.LEFT);
                for (int k = 0; k < 4; k++) {
                    float t = ease((since - 520 - k * 90) / 260f);
                    if (t > 0) {
                        float dy = (1 - t) * -18 * u;
                        if (k == 1) {
                            c.save();
                            c.rotate((1 - t) * -200, x + widths[k] / 2, mid + dy);
                            Glyphs.ball(c, x + widths[k] / 2, mid + dy, 9.5f * u, ink == Color.WHITE ? Color.WHITE : 0xFFF4F5FA, p);
                            c.restore();
                        } else {
                            text.setColor(ink);
                            text.setAlpha(Math.round(255 * t * fade));
                            c.drawText(letters[k], x, baseline(mid) + dy, text);
                        }
                    }
                    x += widths[k] + gap;
                }
                text.setAlpha(255);
                c.restore();
            }
            if (isAttachedToWindow()) postInvalidateOnAnimation();
        }
    }

    /** A slanted wedge in a club's colour, its gloss and a darker line along its foot. */
    private void wedge(Canvas c, float left, float right, float top, float bottom, float slant, int kit, boolean home) {
        if (right <= left) return;
        path.reset();
        if (home) {
            path.moveTo(left, top);
            path.lineTo(right + slant, top);
            path.lineTo(right, bottom);
            path.lineTo(left, bottom);
        } else {
            path.moveTo(left + slant, top);
            path.lineTo(right, top);
            path.lineTo(right, bottom);
            path.lineTo(left, bottom);
        }
        path.close();
        p.setShader(null);
        p.setColor(kit);
        c.drawPath(path, p);
        p.setColor(Color.BLACK);
        p.setShader(gloss);
        c.drawPath(path, p);
        p.setShader(null);
        p.setColor(0x33000000);
        c.drawRect(left, bottom - 2 * u, Math.min(right, getWidth()), bottom, p);
    }

    /** A score, its new number rolling up over the one before it in the last part of a goal. */
    private void number(Canvas c, String now, float x, float mid, boolean rolling, long since) {
        if (!rolling || since < ROLL_AT || since >= GOAL_MS) {
            String shown = rolling && since < ROLL_AT ? String.valueOf(goalFrom) : now;
            c.drawText(shown, x, baseline(mid), text);
            return;
        }
        float t = ease((since - ROLL_AT) / 450f), off = 22 * u;
        c.save();
        c.clipRect(x - 20 * u, 1 * u, x + 20 * u, 41 * u);
        text.setAlpha(Math.round(255 * (1 - t)));
        c.drawText(String.valueOf(goalFrom), x, baseline(mid) - off * t, text);
        text.setAlpha(Math.round(255 * t));
        float pop = 1 + 0.25f * (float) Math.sin(Math.PI * t);
        c.scale(pop, pop, x, mid);
        c.drawText(now, x, baseline(mid) + off * (1 - t), text);
        text.setAlpha(255);
        c.restore();
    }

    /**
     * The box as a picture w pixels wide (its own height), the competition's logo given: for a notification. Made off
     * the main thread; the clock as it stands now.
     */
    public static Bitmap picture(Context c, Game g, Bitmap logo, int w) {
        ScoreBug b = new ScoreBug(c);
        b.game = g;
        b.kits(g);
        b.tag = g.league == null ? "" : g.league;
        b.logoBitmap = logo;
        b.logoShown = false;
        b.comp.setVisibility(INVISIBLE);
        int h = Math.round(w / RATIO);
        b.shapes(w, h);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        b.layout(0, 0, w, h);
        b.draw(cv);
        return out;
    }

    private float baseline(float centre) {
        Paint.FontMetrics fm = text.getFontMetrics();
        return centre - (fm.ascent + fm.descent) / 2f;
    }

    /** A club's three-letter code (ESPN's), or the start of its name. */
    private static String code(String abbreviation, String name) {
        String a = abbreviation == null ? "" : abbreviation.trim();
        if (!a.isEmpty() && a.length() <= 4) return a.toUpperCase(Locale.UK);
        String n = name == null ? "" : name.replaceAll("[^A-Za-z]", "");
        return (n.length() >= 3 ? n.substring(0, 3) : n).toUpperCase(Locale.UK);
    }

    /** The tab's words when the clock is not running: HT, FT, or the kick-off time (with the day when not today). */
    private static String under(Game g) {
        if (g.state == Game.POST) return "FT";
        if (g.halftime) return "HT";
        java.time.ZonedDateTime at = java.time.Instant.ofEpochMilli(g.kickoff).atZone(java.time.ZoneId.systemDefault());
        String time = at.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        return today(g.kickoff) ? time : at.format(java.time.format.DateTimeFormatter.ofPattern("EEE")).toUpperCase(Locale.UK) + " " + time;
    }

    private static boolean today(long ms) {
        return java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                .equals(java.time.LocalDate.now());
    }
}
