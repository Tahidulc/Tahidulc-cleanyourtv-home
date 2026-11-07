package com.gathin.tvhomephone.live;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.gathin.tvhomephone.R;

/**
 * The family's own mark over the channel's logo (found on the picture by BugFinder), filling the logo's place, in the
 * look chosen in Settings: Badge (the app's violet-to-coral tile with its house and play mark, the family's name,
 * Whitebrook Road, beside it when there is room), Glass (dark smoked glass with the white mark and the name), Mark (the
 * tile alone, on a dark ground as wide as the logo was), Wordmark (WHITEBROOK ROAD in letters, a coral line under it), or
 * laid out as a sports channel's own logo is (Broadcast), as one solid block: "Whitebrook Road" across its top band with
 * the picture's tag (HD, UHD) at its end, "sports" large under it, LIVE at its foot while the game is live (in a wide,
 * low cover, in a small box beside "sports"): see-through smoked glass with white letters, or in the scoreboard's own
 * colours. The family's own words, never a channel's name or logo. Each other ground is all but solid, so none of the
 * channel's logo shows through it.
 */
final class LogoCover extends View implements BurnGuard.Dims {
    /** The looks, as Settings names them. */
    enum Look {
        OFF("Leave the channel's"), SIGNATURE("Signature"), MATCHDAY("Matchday"), ROOFLINE("Roofline"), VELOCITY("Velocity"),
        ROUNDEL("Roundel"), BLOCK("Solid block"), SMOKED("Smoked glass"), COMPETITION("The competition's logo"), BADGE("Badge"), GLASS("Glass"), MARK("Mark only"),
        WORD("Wordmark"), BROADCAST("Broadcast, see-through"), BROADCAST_BOARD("Broadcast, scoreboard colours");

        /** Laid out as a sports channel's own logo, one solid block. */
        boolean broadcast() {
            return this == BROADCAST || this == BROADCAST_BOARD;
        }

        /** Whitebrook Road's own channel looks (ChannelMark draws them, as the TV does). */
        boolean channel() {
            return ordinal() >= SIGNATURE.ordinal() && ordinal() <= SMOKED.ordinal();
        }

        final String label;

        Look(String label) {
            this.label = label;
        }

        static Look of(String name) {
            for (Look l : values()) if (l.name().equals(name)) return l;
            return BADGE;
        }
    }

    private static final int VIOLET = 0xFF6D28F2, CORAL = 0xFFFF6B6B;
    /** What the covers say: the family's own name (Broadcast over "sports"). */
    static final String NAME = "Whitebrook Road", WORD_MARK = "WHITEBROOK ROAD", SPORTS = "sports";
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), words = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Drawable mark, markOnly;
    private Look look = Look.BADGE;
    /** Broadcast's parts: the picture's tag ("HD", "UHD", "" for none), whether the game is live, the scoreboard's look. */
    private String tag = "HD";
    private boolean live;
    private OverlayBug.StripLook board;
    private final Paint boxes = new Paint(Paint.ANTI_ALIAS_FLAG), letters = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipBlock = new Path();
    /** The channel looks, drawn when one is chosen; the sport ("f1", else football) for Matchday's channel name. */
    private ChannelMark channel;
    private String sport = "";
    /** The competition being watched: its logo and its name (shown while there is no logo). */
    private android.graphics.Bitmap comp;
    private String compName = "";
    private final Paint picture = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    /** The competition's logo beside every look (on unless switched off in Settings). */
    private boolean compOn = true;
    /** How much further the look's ground runs than its own width (the competition's logo's room). */
    private float ext;

    LogoCover(Context c) {
        super(c);
        mark = c.getDrawable(R.drawable.ic_app_front);
        markOnly = c.getDrawable(R.drawable.ic_app_mono);
        words.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        words.setColor(0xFFFFFFFF);
        letters.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        setContentDescription(NAME);
    }

    /** Dimmed against burn-in (BurnGuard): solid, a little darker, so none of the channel's logo shows. */
    @Override public void dim(boolean on) {
        BurnGuard.dimLayer(this, on);
    }

    void look(Look l) {
        look = l;
        invalidate();
    }

    /** The channel's picture tag for Broadcast's box ("HD", "UHD"; "" for none). */
    void tag(String t) {
        String n = t == null ? "" : t;
        if (n.equals(tag)) return;
        tag = n;
        if (look.broadcast() || look.channel()) invalidate();
    }

    /** The competition on the board: its logo (null for none yet) and its name. */
    void competition(android.graphics.Bitmap logo, String name) {
        String n = name == null ? "" : name.trim();
        if (logo == comp && n.equals(compName)) return;
        comp = logo;
        compName = n;
        invalidate();
    }

    /** The competition's logo beside the look, or not. */
    void compOn(boolean on) {
        if (on == compOn) return;
        compOn = on;
        invalidate();
    }

    /** What is on: "f1" for Formula 1, else football (Matchday's channel name). */
    void sport(String s) {
        String n = s == null ? "" : s;
        if (n.equals(sport)) return;
        sport = n;
        if (look.channel()) invalidate();
    }

    /** Whether the game is live (Broadcast's LIVE). */
    void live(boolean on) {
        if (on == live) return;
        live = on;
        if (look.broadcast() || look.channel()) invalidate();
    }

    /** The scoreboard's look, for Broadcast in its colours. */
    void boardLook(OverlayBug.StripLook s) {
        if (s == board) return;
        board = s;
        if (look == Look.BROADCAST_BOARD) invalidate();
    }

    /** Pure: a channel's picture tag from its name (ChannelMark.tagOf): "4K" or "UHD", with " HDR" or not, "HD", or none. */
    static String tagOf(String channel) {
        return ChannelMark.tagOf(channel);
    }

    /** The competition's own logo over the channel's: a dark block, the logo as large as it fits, a red dot while live. */
    private void competitionLook(Canvas cv, float w, float h) {
        float rad = Math.min(w, h) * 0.18f;
        r.set(0, 0, w, h);
        fill.setShader(new LinearGradient(0, 0, 0, h, 0xFA1B1F31, 0xFA07090F, Shader.TileMode.CLAMP));
        cv.drawRoundRect(r, rad, rad, fill);
        fill.setShader(null);
        float pad = Math.min(w, h) * 0.14f;
        if (comp != null && comp.getWidth() > 0 && comp.getHeight() > 0) {
            float k = Math.min((w - 2 * pad) / comp.getWidth(), (h - 2 * pad) / comp.getHeight());
            float dw = comp.getWidth() * k, dh = comp.getHeight() * k;
            r.set((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f);
            cv.drawBitmap(comp, null, r, picture);
        } else {
            String n = compName.isEmpty() ? "LIVE" : compName.toUpperCase(java.util.Locale.ROOT);
            words.setTypeface(ChannelMark.outfit(getContext(), 800));
            words.setTextSize(h * 0.36f);
            float wd = words.measureText(n);
            if (wd > w - 2 * pad) words.setTextSize(h * 0.36f * (w - 2 * pad) / wd);
            words.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics fm = words.getFontMetrics();
            cv.drawText(n, w / 2f, h / 2f - (fm.ascent + fm.descent) / 2f, words);
            words.setTextAlign(Paint.Align.LEFT);
            words.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        }
        if (live) {
            fill.setColor(0xFFFF3B4E);
            cv.drawCircle(w - pad * 0.7f, pad * 0.7f, Math.max(2f, h * 0.045f), fill);
        }
    }

    @Override protected void onDraw(Canvas cv) {
        float full = getWidth(), h = getHeight();
        if (full <= 0 || h <= 0 || look == Look.OFF) return;
        // The competition's logo at the end of the look's own block, when it is on and known: one block, the look's
        // ground running on under it, a fine line between.
        float side = compOn && comp != null && look != Look.COMPETITION ? Math.min(h * 0.86f, full * 0.28f) : 0f;
        ext = side;
        drawLook(cv, full - side, h);
        if (side > 0f) compMark(cv, full - side, full, h);
    }

    /** The competition's logo from x0 to x1 on the look's ground: a fine line before it, the logo (below a top band). */
    private void compMark(Canvas cv, float x0, float x1, float h) {
        float top = 0f;
        if (look == Look.BLOCK) top = h * (x0 / h >= 2.4f ? 0.4f : 0.32f);
        else if (look.broadcast()) top = h * (x0 / h >= 2.3f ? 0.36f : 0.3f);
        float bodyH = h - top;
        fill.setShader(null);
        fill.setColor(0x40FFFFFF);
        cv.drawRect(x0, top + bodyH * 0.2f, x0 + Math.max(1f, h * 0.012f), top + bodyH * 0.8f, fill);
        float pad = Math.min(x1 - x0, bodyH) * 0.2f, bw = x1 - x0 - 2 * pad, bh = bodyH - 2 * pad;
        float k = Math.min(bw / comp.getWidth(), bh / comp.getHeight()), dw = comp.getWidth() * k, dh = comp.getHeight() * k;
        float cx = (x0 + x1) / 2f - (x1 - x0) * 0.04f, cy = top + bodyH / 2f;
        r.set(cx - dw / 2f, cy - dh / 2f, cx + dw / 2f, cy + dh / 2f);
        cv.drawBitmap(comp, null, r, picture);
    }

    /** The look itself in the box w wide (the whole box, or short of the competition's block). */
    private void drawLook(Canvas cv, float w, float h) {
        if (look == Look.COMPETITION) {
            competitionLook(cv, w, h);
            return;
        }
        if (look.channel()) {
            if (channel == null) channel = new ChannelMark(getContext());
            channel.draw(cv, look, 0, 0, w, h, tag, live, sport, ext);
            return;
        }
        if (look.broadcast()) {
            broadcast(cv, w, h, look == Look.BROADCAST || board == null);
            return;
        }
        float rad = Math.min(w, h) * 0.22f;
        r.set(0, 0, w + ext, h);
        switch (look) {
            case GLASS:
                fill.setShader(null);
                fill.setColor(0xFA0B0F24);
                cv.drawRoundRect(r, rad, rad, fill);
                named(cv, w, h, 0xFFFFFFFF, false);
                break;
            case MARK:
                fill.setShader(null);
                fill.setColor(0xFA101014);
                cv.drawRoundRect(r, rad, rad, fill);
                float s = Math.min(h, w) * 0.86f;
                tile(cv, (w - s) / 2, (h - s) / 2, s);
                break;
            case WORD: {
                fill.setShader(null);
                fill.setColor(0xFA000000);
                cv.drawRoundRect(r, rad, rad, fill);
                float size = fit(WORD_MARK, w * 0.84f, h * 0.42f, 0.12f);
                words.setTextSize(size);
                words.setLetterSpacing(0.12f);
                words.setTextAlign(Paint.Align.CENTER);
                Paint.FontMetrics fm = words.getFontMetrics();
                float base = h / 2 - (fm.ascent + fm.descent) / 2 - h * 0.06f;
                cv.drawText(WORD_MARK, w / 2, base, words);
                words.setLetterSpacing(0);
                float lw = words.measureText(WORD_MARK) * 0.5f;
                fill.setShader(new LinearGradient(w / 2 - lw / 2, 0, w / 2 + lw / 2, 0, VIOLET, CORAL, Shader.TileMode.CLAMP));
                cv.drawRoundRect(w / 2 - lw / 2, base + h * 0.1f, w / 2 + lw / 2, base + h * 0.1f + Math.max(2, h * 0.07f), h * 0.04f, h * 0.04f, fill);
                fill.setShader(null);
                break;
            }
            default:
                fill.setShader(new LinearGradient(0, 0, w, h, VIOLET, CORAL, Shader.TileMode.CLAMP));
                cv.drawRoundRect(r, rad, rad, fill);
                fill.setShader(null);
                named(cv, w, h, 0xFFFFFFFF, true);
        }
    }

    /** The white house mark at the left (or alone in the middle when narrow), the name beside it when it fits. */
    private void named(Canvas cv, float w, float h, int ink, boolean onTile) {
        float s = h * 0.9f;
        boolean name = w / h >= 2.2f;
        float x = name ? h * 0.12f : (w - s) / 2;
        drawMark(cv, onTile ? mark : markOnly, x, (h - s) / 2, s, onTile ? 0 : ink);
        if (!name) return;
        float left = x + s * 0.92f, room = w - left - h * 0.22f;
        float size = fit(NAME, room, h * 0.46f, 0f);
        words.setTextSize(size);
        words.setLetterSpacing(0);
        words.setTextAlign(Paint.Align.LEFT);
        words.setColor(ink);
        Paint.FontMetrics fm = words.getFontMetrics();
        cv.drawText(NAME, left, h / 2 - (fm.ascent + fm.descent) / 2, words);
    }

    /**
     * Broadcast: laid out as a sports channel's own logo, as one solid block filling the box: the name across its top
     * band with the picture's tag at the band's end, "sports" large under it, LIVE in its foot while the game is live (a
     * wide, low box keeps LIVE in a small box beside "sports"). See-through (smoked glass, white letters, the band a
     * lighter grey), or the scoreboard's colours (its ground, the band in its block).
     */
    private void broadcast(Canvas cv, float w, float h, boolean seeThrough) {
        int ground, band, bandInk, tagBox, tagInk, ink;
        if (seeThrough) {
            ground = 0xC20E1014;
            band = 0x40FFFFFF;
            bandInk = 0xF7FFFFFF;
            tagBox = 0x33FFFFFF;
            tagInk = 0xFFFFFFFF;
            ink = 0xFFFFFFFF;
        } else {
            OverlayBug.StripLook s = board;
            ground = 0xFF000000 | s.ground;
            band = (s.block >>> 24) == 0xFF ? s.block : OverlayBug.blend(ground, 0xFFFFFFFF, 0.22f);
            bandInk = OverlayBug.ink(band);
            tagBox = s.light ? 0x1A0B1640 : 0x33FFFFFF;
            tagInk = s.light ? bandInk : 0xFFFFFFFF;
            ink = s.ink;
        }
        int liveInk = seeThrough ? 0xE6FFFFFF : ink;
        float rad = Math.min(w, h) * 0.1f;
        r.set(0, 0, w + ext, h);
        boxes.setColor(ground);
        cv.drawRoundRect(r, rad, rad, boxes);
        cv.save();
        clipBlock.reset();
        clipBlock.addRoundRect(r, rad, rad, Path.Direction.CW);
        cv.clipPath(clipBlock);
        boolean wide = w / h >= 2.3f;
        float band1 = h * (wide ? 0.36f : 0.3f), pad = Math.min(w, h) * 0.08f;
        boxes.setColor(band);
        cv.drawRect(0, 0, w + ext, band1, boxes);
        float tagW = 0;
        if (!tag.isEmpty()) {
            float ts = band1 * 0.5f;
            tagW = sizeOf(tag, ts, 0.06f) + band1 * 0.5f;
            boxes.setColor(tagBox);
            r.set(w - pad - tagW, band1 * 0.16f, w - pad, band1 * 0.84f);
            cv.drawRoundRect(r, band1 * 0.12f, band1 * 0.12f, boxes);
            line(cv, tag, r.centerX(), 0, band1, ts, 0.06f, tagInk, true);
        }
        float nameRoom = w - 2 * pad - (tagW > 0 ? tagW + pad * 0.6f : 0);
        line(cv, NAME, pad, 0, band1, fitSize(NAME, nameRoom, band1 * 0.58f, 0.02f), 0.02f, bandInk, false);
        if (wide) {
            float mid = h - band1, ls = mid * 0.28f, liveW = live ? sizeOf("LIVE", ls, 0.2f) + mid * 0.36f : 0f;
            float ms = fitSize(SPORTS, w - 2 * pad - (liveW > 0 ? liveW + pad : 0f), mid * 0.7f, 0f);
            line(cv, SPORTS, pad, band1, mid, ms, 0f, ink, false);
            if (live) {
                boxes.setColor(tagBox);
                r.set(w - pad - liveW, band1 + mid * 0.27f, w - pad, band1 + mid * 0.73f);
                cv.drawRoundRect(r, mid * 0.08f, mid * 0.08f, boxes);
                line(cv, "LIVE", r.centerX(), band1, mid, ls, 0.2f, liveInk, true);
            }
        } else {
            float foot = h * 0.24f, mid = h - band1 - foot;
            line(cv, SPORTS, w / 2f, band1, mid, fitSize(SPORTS, w - 2 * pad, mid * 0.72f, 0f), 0f, ink, true);
            if (live) line(cv, "LIVE", w / 2f, band1 + mid, foot * 0.9f, Math.min(foot * 0.62f, fitSize("LIVE", w - 2 * pad, foot * 0.62f, 0.35f)), 0.35f, liveInk, true);
        }
        cv.restore();
    }

    /** The biggest size up to most at which s (spaced so) fits in room. */
    private float fitSize(String s, float room, float most, float spacing) {
        float wd = sizeOf(s, most, spacing);
        return wd <= room ? most : Math.max(5f, most * room / Math.max(1f, wd));
    }

    private float sizeOf(String s, float size, float spacing) {
        letters.setTextSize(size);
        letters.setLetterSpacing(spacing);
        return letters.measureText(s);
    }

    /** Words in the middle of a row (y, h): centred on x, or from x along. */
    private void line(Canvas cv, String s, float x, float y, float h, float size, float spacing, int colour, boolean centred) {
        letters.setTextSize(size);
        letters.setLetterSpacing(spacing);
        letters.setColor(colour);
        letters.setTextAlign(centred ? Paint.Align.CENTER : Paint.Align.LEFT);
        Paint.FontMetrics fm = letters.getFontMetrics();
        cv.drawText(s, x, y + h / 2 - (fm.ascent + fm.descent) / 2, letters);
    }

    /** The app's own tile: violet to coral, rounded, the house and play mark on it. */
    private void tile(Canvas cv, float x, float y, float s) {
        fill.setShader(new LinearGradient(x, y, x + s, y + s, VIOLET, CORAL, Shader.TileMode.CLAMP));
        cv.drawRoundRect(x, y, x + s, y + s, s * 0.24f, s * 0.24f, fill);
        fill.setShader(null);
        drawMark(cv, mark, x, y, s, 0);
    }

    /**
     * The house mark filling a square of side s at (x, y): the drawable's 108 units hold the house in about 46 of them,
     * so it is drawn larger than the square, centred on it. tint 0: its own colours.
     */
    private void drawMark(Canvas cv, Drawable d, float x, float y, float s, int tint) {
        if (d == null) return;
        float big = s * 108f / 62f;
        int l = Math.round(x + s / 2 - big / 2), t = Math.round(y + s / 2 - big / 2);
        d.setBounds(l, t, l + Math.round(big), t + Math.round(big));
        if (tint != 0) d.setTint(tint);
        d.draw(cv);
    }

    /** The biggest text size up to most at which words fit in room. */
    private float fit(String s, float room, float most, float spacing) {
        words.setLetterSpacing(spacing);
        words.setTextSize(most);
        float wd = words.measureText(s);
        return wd <= room ? most : Math.max(6, most * room / wd);
    }
}
