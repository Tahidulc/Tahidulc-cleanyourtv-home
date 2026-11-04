package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.Locale;

/**
 * The scores box as a broadcaster's scoreboard: the competition's logo on its own slanted block at the left, a ball's
 * ring round it; a silver bar with both clubs in capitals and the score in a red plate between them; and under it a
 * navy tab with the clock in minutes and seconds, going on between reads (GameClock), the minutes added beside it in
 * added time, HT, FT, or the kick-off time. Drawn in code at any width, about 5.6 times as wide as it is high.
 */
public final class ScoreBug extends FrameLayout {
    /** How many times as wide as high it is drawn. */
    public static final float RATIO = 5.6f;

    private final ImageView comp;
    private Game game;
    private String tag = "";
    private boolean logoShown;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path block = new Path(), bar = new Path(), tab = new Path(), stripe = new Path();
    private final RectF r = new RectF();
    private Shader blockFill, barFill, tabFill, plateFill, gloss, ballFill, ringFill;
    private CornerPathEffect corners;
    private float u, blockW;
    /** The competition's logo drawn by the box itself (a picture of it made off screen, for a notification). */
    private android.graphics.Bitmap logoBitmap;
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
        text.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        text.setFontFeatureSettings("tnum");
    }

    public void show(Game g) {
        game = g;
        // The competition's logo for dark grounds (the scoreboard's, else ESPN's by its id); its tag when none loads.
        String logo = Competitions.logo(g);
        tag = g.league == null ? "" : g.league;
        comp.setContentDescription(g.leagueName == null || g.leagueName.isEmpty() ? tag : g.leagueName);
        logoShown = logo != null;
        if (logo != null) {
            Ui.load(comp, logo, null, () -> {
                logoShown = false;
                invalidate();
            });
        } else {
            Ui.clear(comp);
        }
        String[] clock = GameClock.show(g, System.currentTimeMillis());
        setContentDescription(g.homeName + " " + (g.state == Game.PRE ? "v" : g.score()) + " " + g.awayName + ", "
                + (clock != null ? clock[0] + (clock[1].isEmpty() ? "" : " " + clock[1]) : under(g)));
        removeCallbacks(tick);
        if (running() && isAttachedToWindow()) postDelayed(tick, 1000);
        invalidate();
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
        int h = Math.round(Math.max(48 * d, Math.min(78 * d, w / RATIO)));
        if (MeasureSpec.getMode(heightSpec) == MeasureSpec.EXACTLY) h = MeasureSpec.getSize(heightSpec);
        int s = Math.round(h * 0.56f);
        comp.measure(MeasureSpec.makeMeasureSpec(s, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(s, MeasureSpec.EXACTLY));
        setMeasuredDimension(w, h);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int right, int b) {
        int h = b - t;
        float cu = h / 64f;
        float cx = h * 0.62f, cy = h / 2f;
        int s = comp.getMeasuredWidth();
        comp.layout(Math.round(cx - s / 2f), Math.round(cy - s / 2f), Math.round(cx + s / 2f), Math.round(cy + s / 2f));
        if (changed) shapes(right - l, h, cu);
    }

    /** The block, the bar, the tab and their fills, for this size. */
    private void shapes(int w, int h, float cu) {
        u = cu;
        blockW = h * 1.38f;
        block.reset();
        block.moveTo(12 * u, 1 * u);
        block.lineTo(blockW - 22 * u, 1 * u);
        block.cubicTo(blockW + 4 * u, 6 * u, blockW + 4 * u, h - 6 * u, blockW - 26 * u, h - 1 * u);
        block.lineTo(1 * u, h - 1 * u);
        block.close();
        float barL = blockW - 30 * u, barT = 4 * u, barB = 38 * u;
        bar.reset();
        bar.moveTo(barL, barT);
        bar.lineTo(w - 2 * u, barT);
        bar.lineTo(w - 14 * u, barB);
        bar.lineTo(barL, barB);
        bar.close();
        stripe.reset();
        stripe.moveTo(blockW - 6 * u, barT);
        stripe.lineTo(blockW + 4 * u, barT);
        stripe.lineTo(blockW - 4 * u, barB);
        stripe.lineTo(blockW - 14 * u, barB);
        stripe.close();
        float tabL = blockW - 34 * u, tabR = blockW + 82 * u, tabB = h - 2 * u;
        tab.reset();
        tab.moveTo(tabL, barB - 1 * u);
        tab.lineTo(tabR + 8 * u, barB - 1 * u);
        tab.lineTo(tabR, tabB);
        tab.lineTo(tabL, tabB);
        tab.close();
        blockFill = new LinearGradient(0, 0, 0, h, new int[]{0xFF3550D8, 0xFF1A2A9C, 0xFF0E1766}, null, Shader.TileMode.CLAMP);
        gloss = new LinearGradient(0, 0, 0, h * 0.5f, new int[]{0x59FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
        barFill = new LinearGradient(0, barT, 0, barB, new int[]{0xFFFFFFFF, 0xFFEDEFF5, 0xFFC9CEDB}, new float[]{0, 0.55f, 1},
                Shader.TileMode.CLAMP);
        tabFill = new LinearGradient(0, barB, 0, tabB, new int[]{0xFF22307F, 0xFF0B1246}, null, Shader.TileMode.CLAMP);
        plateFill = new LinearGradient(0, barT + 3 * u, 0, barB - 3 * u, new int[]{0xFFE21B38, 0xFFA30F27, 0xFF7E0A1D}, null,
                Shader.TileMode.CLAMP);
        float cx = h * 0.62f, cy = h / 2f, rr = h * 0.39f;
        ballFill = new RadialGradient(cx - rr * 0.3f, cy - rr * 0.4f, rr * 1.3f, new int[]{0xFF3A5BE8, 0xFF101C7A}, null, Shader.TileMode.CLAMP);
        ringFill = new LinearGradient(cx, cy - rr, cx, cy + rr, new int[]{0xFFFFFFFF, 0xFF7FE3F0, 0xFFB9C3DB}, null, Shader.TileMode.CLAMP);
        corners = new CornerPathEffect(5 * u);
    }

    @Override protected void onDraw(Canvas c) {
        if (u == 0) shapes(getWidth(), getHeight(), getHeight() / 64f);
        float w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        p.setPathEffect(corners);
        // A soft shadow under it all.
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setColor(0x47000000);
        c.save();
        c.translate(0, 2 * u);
        c.drawPath(bar, p);
        c.drawPath(tab, p);
        c.drawPath(block, p);
        c.restore();
        // The bar with the names and the score (opaque: a shader keeps the paint's own alpha).
        p.setColor(Color.BLACK);
        p.setShader(barFill);
        c.drawPath(bar, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1, 0.9f * u));
        p.setColor(0x668A90A6);
        c.drawPath(bar, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFC8102E);
        c.drawPath(stripe, p);
        // The clock's tab.
        p.setColor(Color.BLACK);
        p.setShader(tabFill);
        c.drawPath(tab, p);
        p.setShader(null);
        // The competition's block, its gloss and the ball's ring round the logo.
        p.setColor(Color.BLACK);
        p.setShader(blockFill);
        c.drawPath(block, p);
        p.setShader(gloss);
        c.drawPath(block, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.6f * u);
        p.setColor(0xCCDDE3F5);
        c.drawPath(block, p);
        float cx = h * 0.62f, cy = h / 2f, rr = h * 0.39f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.BLACK);
        p.setShader(ballFill);
        c.drawCircle(cx, cy, rr, p);
        p.setShader(ringFill);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3 * u);
        c.drawCircle(cx, cy, rr, p);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setPathEffect(null);
        if (logoBitmap != null) {
            float half = h * 0.28f;
            float k = Math.min(2 * half / logoBitmap.getWidth(), 2 * half / logoBitmap.getHeight());
            float bw = logoBitmap.getWidth() * k, bh = logoBitmap.getHeight() * k;
            r.set(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2);
            c.drawBitmap(logoBitmap, null, r, text);
        } else if (!logoShown) {
            text.setColor(Color.WHITE);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(12 * u);
            c.drawText(Glyphs.fit(tag, text, rr * 1.8f), cx, cy + 4 * u, text);
        }
        if (game == null) return;
        // The names and the score's plate.
        float barT = 4 * u, barB = 38 * u, mid = (barT + barB) / 2f;
        String score = game.state == Game.PRE ? "v" : game.homeScore + "-" + game.awayScore;
        text.setTextSize(21 * u);
        float plateW = Math.max(text.measureText(score) + 22 * u, 52 * u);
        float left = blockW + 10 * u, right = w - 20 * u;
        float plateCx = left + (right - left) / 2f;
        r.set(plateCx - plateW / 2, barT + 3 * u, plateCx + plateW / 2, barB - 3 * u);
        p.setColor(Color.BLACK);
        p.setShader(plateFill);
        c.drawRoundRect(r, 7 * u, 7 * u, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(u);
        p.setColor(0x59FFFFFF);
        c.drawRoundRect(r, 7 * u, 7 * u, p);
        p.setStyle(Paint.Style.FILL);
        text.setColor(Color.WHITE);
        text.setTextAlign(Paint.Align.CENTER);
        c.drawText(score, plateCx, baseline(mid), text);
        text.setColor(0xFF0B1446);
        text.setTextSize(20 * u);
        float room = plateCx - plateW / 2 - 8 * u - left;
        text.setTextAlign(Paint.Align.RIGHT);
        c.drawText(name(game.homeName, game.home, room), plateCx - plateW / 2 - 8 * u, baseline(mid), text);
        text.setTextAlign(Paint.Align.LEFT);
        c.drawText(name(game.awayName, game.away, room), plateCx + plateW / 2 + 8 * u, baseline(mid), text);
        // The clock.
        float tabMid = (barB + h - 2 * u) / 2f, tabCx = blockW + 28 * u;
        String[] clock = GameClock.show(game, System.currentTimeMillis());
        text.setColor(Color.WHITE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(19 * u);
        if (clock != null) {
            c.drawText(clock[0], tabCx, baseline(tabMid), text);
            if (!clock[1].isEmpty()) {
                text.setTextSize(15 * u);
                float aw = text.measureText(clock[1]) + 12 * u, ax = blockW + 96 * u;
                r.set(ax, barB + 2 * u, ax + aw, h - 4 * u);
                p.setColor(0xFFC8102E);
                c.drawRoundRect(r, 5 * u, 5 * u, p);
                c.drawText(clock[1], r.centerX(), baseline(r.centerY()), text);
            }
        } else {
            text.setTextSize(game.state == Game.PRE ? 16 * u : 18 * u);
            c.drawText(under(game), tabCx, baseline(tabMid), text);
        }
    }

    /**
     * The box as a picture w pixels wide (its own height), the competition's logo given: for a notification. Made off
     * the main thread; the clock as it stands now.
     */
    public static android.graphics.Bitmap picture(Context c, Game g, android.graphics.Bitmap logo, int w) {
        ScoreBug b = new ScoreBug(c);
        b.game = g;
        b.tag = g.league == null ? "" : g.league;
        b.logoBitmap = logo;
        b.logoShown = false;
        int h = Math.round(w / RATIO);
        b.shapes(w, h, h / 64f);
        android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        b.layout(0, 0, w, h);
        b.draw(cv);
        return out;
    }

    private float baseline(float centre) {
        Paint.FontMetrics fm = text.getFontMetrics();
        return centre - (fm.ascent + fm.descent) / 2f;
    }

    /** A club's name in capitals, or its short tag when the name will not fit. */
    private String name(String full, String shortTag, float room) {
        String n = (full == null ? "" : full).toUpperCase(Locale.UK);
        if (text.measureText(n) <= room) return n;
        String t = (shortTag == null || shortTag.isEmpty() ? n : shortTag).toUpperCase(Locale.UK);
        return Glyphs.fit(t, text, room);
    }

    /** The tab's words when the clock is not running: HT, FT, or the kick-off time (with the day when not today). */
    private static String under(Game g) {
        if (g.state == Game.POST) return "FT";
        if (g.halftime) return "HT";
        java.time.ZonedDateTime at = java.time.Instant.ofEpochMilli(g.kickoff).atZone(java.time.ZoneId.systemDefault());
        String time = at.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        return today(g.kickoff) ? "KO " + time : at.format(java.time.format.DateTimeFormatter.ofPattern("EEE")).toUpperCase(Locale.UK) + " " + time;
    }

    private static boolean today(long ms) {
        return java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                .equals(java.time.LocalDate.now());
    }
}
