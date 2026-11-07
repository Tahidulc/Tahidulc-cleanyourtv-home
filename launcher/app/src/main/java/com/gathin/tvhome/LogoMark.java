package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * The cover BoardCover puts over the channel's logo, as the phone's player has it (Homefield's LogoCover): dark smoked
 * glass, solid so none of the logo shows through, in one of three looks: Glass (the Homefield house and play
 * mark with the name beside it when there is room), Mark (the mark alone) or Plain (the glass alone). Or as a sports
 * channel's own logo is laid out (Broadcast): "homefield" over a box with the picture's tag (HD, UHD), "sports" large in
 * the box under it, LIVE spaced out under that while the game is live; see-through, in light grey and smoked boxes as
 * channels' own are, or in the scoreboard's own colours (its look's StripLook). Homefield's words, never a channel's
 * name or logo. Kind to the panel:
 * only dark grounds and a soft white mark, never the phone's bright violet-to-coral tile, and an edge of its ground
 * (inset) round everything, so the pixel or two it moves against burn-in never uncovers the logo.
 */
final class LogoMark extends View {
    /** Its looks, as the Scoreboard card names them. */
    enum Look {
        GLASS("Glass, with the mark"), MARK("Mark only"), PLAIN("Plain"), BROADCAST("Broadcast, see-through"),
        BROADCAST_BOARD("Broadcast, scoreboard colours");

        /** Laid out as a channel's own logo: three rows, taller than the others. */
        boolean broadcast() {
            return this == BROADCAST || this == BROADCAST_BOARD;
        }

        final String label;

        Look(String label) {
            this.label = label;
        }

        static Look of(String name) {
            for (Look l : values()) if (l.name().equals(name)) return l;
            return GLASS;
        }
    }

    /** Solid grounds: none of the channel's logo shows through. */
    private static final int GROUND = 0xFF0B0F24, PLAIN_GROUND = 0xFF07080F, MARK_INK = 0xFFD5DAEA;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), ink = new Paint(Paint.ANTI_ALIAS_FLAG),
            words = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path house = new Path(), play = new Path(), drawn = new Path();
    /** The play button's colour: the cover's own ground, painted (never a hole, so the logo under it never shows). */
    private final Paint button = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix m = new Matrix();
    private Look look = Look.GLASS;
    /** Broadcast's parts: the picture's tag ("HD", "UHD", "" for none), whether the game is live, the scoreboard's look. */
    private String tag = "HD";
    private boolean live;
    private BoardBug.StripLook board;
    private final Paint boxes = new Paint(Paint.ANTI_ALIAS_FLAG), letters = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int inset;
    private boolean outlined;
    /** The ring's dashes, made once for a width of line. */
    private float dashFor = -1;

    LogoMark(Context c) {
        super(c);
        // The phone's mark (its 108-unit icon): a house with a play button in it, drawn with round corners.
        house.moveTo(31, 75);
        house.lineTo(31, 50);
        house.lineTo(54, 31);
        house.lineTo(77, 50);
        house.lineTo(77, 75);
        house.close();
        play.moveTo(48, 51);
        play.lineTo(48, 71);
        play.lineTo(65, 61);
        play.close();
        ink.setColor(MARK_INK);
        ink.setStyle(Paint.Style.FILL_AND_STROKE);
        ink.setStrokeJoin(Paint.Join.ROUND);
        button.setStyle(Paint.Style.FILL_AND_STROKE);
        button.setStrokeJoin(Paint.Join.ROUND);
        words.setTypeface(ScoreCard.outfit(c, 700));
        letters.setTypeface(ScoreCard.outfit(c, 700));
        words.setColor(MARK_INK);
        edge.setStyle(Paint.Style.STROKE);
        edge.setColor(BoardBug.INK);
        setContentDescription("Homefield");
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    void look(Look l) {
        if (l == look) return;
        look = l;
        invalidate();
    }

    void inset(int px) {
        inset = px;
        invalidate();
    }

    /** The channel's picture tag for Broadcast's box ("HD", "UHD"; "" for none). */
    void tag(String t) {
        String n = t == null ? "" : t;
        if (n.equals(tag)) return;
        tag = n;
        if (look.broadcast()) invalidate();
    }

    /** Whether the game is live (Broadcast's LIVE). */
    void live(boolean on) {
        if (on == live) return;
        live = on;
        if (look.broadcast()) invalidate();
    }

    /** The scoreboard's look, for Broadcast in its colours. */
    void boardLook(BoardBug.StripLook s) {
        if (s == board) return;
        board = s;
        if (look == Look.BROADCAST_BOARD) invalidate();
    }

    /** Pure: a channel's picture tag from its name: "UHD" for UHD or 4K, "HD" for HD or FHD, else none. */
    static String tagOf(String channel) {
        String n = channel == null ? "" : " " + channel.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]+", " ") + " ";
        if (n.contains(" UHD ") || n.contains(" 4K ")) return "UHD";
        if (n.contains(" HD ") || n.contains(" FHD ")) return "HD";
        return "";
    }

    /** Ringed while Move and resize has it. */
    void outline(boolean on) {
        if (on == outlined) return;
        outlined = on;
        invalidate();
    }

    @Override protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (look.broadcast()) {
            broadcast(cv, w, h, look == Look.BROADCAST || board == null);
            ring(cv, w, h, Math.min(w, h) * 0.06f);
            return;
        }
        float rad = Math.min(w, h) * 0.18f;
        r.set(0, 0, w, h);
        fill.setColor(look == Look.PLAIN ? PLAIN_GROUND : GROUND);
        cv.drawRoundRect(r, rad, rad, fill);
        float cw = w - 2 * inset, ch = h - 2 * inset;
        if (look != Look.PLAIN && cw > 0 && ch > 0) {
            float s = ch * 0.78f;
            boolean name = look == Look.GLASS && cw / ch >= 2.2f;
            float x = name ? inset + ch * 0.14f : inset + (cw - s) / 2;
            mark(cv, x, inset + (ch - s) / 2, s);
            if (name) {
                float left = x + s * 1.14f, room = inset + cw - left - ch * 0.2f;
                float size = fit("Homefield", room, ch * 0.44f);
                words.setTextSize(size);
                Paint.FontMetrics fm = words.getFontMetrics();
                cv.drawText("Homefield", left, inset + ch / 2 - (fm.ascent + fm.descent) / 2, words);
            }
        }
        ring(cv, w, h, rad);
    }

    /** Ringed while Move and resize has it. */
    private void ring(Canvas cv, float w, float h, float rad) {
        if (!outlined) return;
        float sw = Math.max(2f, Math.min(w, h) * 0.05f);
        if (sw != dashFor) {
            dashFor = sw;
            edge.setStrokeWidth(sw);
            edge.setPathEffect(new DashPathEffect(new float[]{sw * 3, sw * 2}, 0));
        }
        r.set(sw / 2, sw / 2, w - sw / 2, h - sw / 2);
        cv.drawRoundRect(r, rad, rad, edge);
    }

    /**
     * Broadcast: laid out as a sports channel's own logo, filling the box as far as it can at its own proportions and
     * centred in it: "homefield" in a box with the tag's box beside it, "sports" large in the box under them, LIVE
     * spaced out under that while the game is live. See-through (light grey and smoked boxes, white letters), or the
     * scoreboard's colours.
     */
    private void broadcast(Canvas cv, float w, float h, boolean seeThrough) {
        float cw = w - 2 * inset, ch = h - 2 * inset;
        if (cw <= 0 || ch <= 0) return;
        int topBox, topInk, tagBox, tagInk, midBox, midInk, liveBox, liveInk;
        if (seeThrough) {
            topBox = 0xA35C6068;
            topInk = 0xF7FFFFFF;
            tagBox = 0xA35C6068;
            tagInk = 0xF7FFFFFF;
            midBox = 0xD90E0F12;
            midInk = 0xFFFFFFFF;
            liveBox = 0x8C000000;
            liveInk = 0xF2FFFFFF;
        } else {
            BoardBug.StripLook s = board;
            int ground = 0xFF000000 | s.ground;
            int block = (s.block >>> 24) == 0xFF ? s.block : BoardBug.blend(ground, 0xFFFFFFFF, 0.22f);
            topBox = block;
            topInk = BoardBug.ink(block);
            tagBox = ground;
            tagInk = s.ink;
            midBox = ground;
            midInk = s.ink;
            liveBox = ground;
            liveInk = s.ink;
        }
        // Its proportions at a unit of the box's height, then made smaller should that be too wide for the box.
        String top = "homefield", mid = "sports", live = "LIVE";
        float u = ch;
        float[] wd = widths(u, top, mid);
        float need = Math.max(wd[0], wd[1]);
        if (need > cw) u = u * cw / need;
        wd = widths(u, top, mid);
        float width = Math.max(wd[0], wd[1]);
        float x = inset + (cw - width) / 2f, y = inset + (ch - u * 0.98f) / 2f, rad = u * 0.04f;
        // The first row: "homefield" in its box, the tag in a box of its own at the row's end.
        float r1 = u * 0.28f, gap = u * 0.03f, tagW = tag.isEmpty() ? 0 : sizeOf(tag, u * 0.17f, 0.06f) + u * 0.22f;
        boxes.setColor(topBox);
        r.set(x, y, x + width - (tagW > 0 ? tagW + gap : 0), y + r1);
        cv.drawRoundRect(r, rad, rad, boxes);
        line(cv, top, r.left + u * 0.14f, r.top, r1, u * 0.2f, 0f, topInk, false);
        if (tagW > 0) {
            boxes.setColor(tagBox);
            r.set(x + width - tagW, y, x + width, y + r1);
            cv.drawRoundRect(r, rad, rad, boxes);
            line(cv, tag, r.centerX(), r.top, r1, u * 0.17f, 0.06f, tagInk, true);
        }
        // The second: "sports" large in its smoked box.
        float r2 = u * 0.42f, y2 = y + r1 + gap;
        boxes.setColor(midBox);
        r.set(x, y2, x + width, y2 + r2);
        cv.drawRoundRect(r, rad, rad, boxes);
        line(cv, mid, x + width / 2f, y2, r2, u * 0.32f, 0f, midInk, true);
        // LIVE, spaced out under it, while the game is live.
        if (this.live) {
            float r3 = u * 0.22f, y3 = y2 + r2 + gap, lw = sizeOf(live, u * 0.15f, 0.35f) + u * 0.3f;
            if (liveBox != 0) {
                boxes.setColor(liveBox);
                r.set(x + (width - lw) / 2f, y3, x + (width + lw) / 2f, y3 + r3);
                cv.drawRoundRect(r, rad, rad, boxes);
            }
            line(cv, live, x + width / 2f, y3, r3, u * 0.15f, 0.35f, liveInk, true);
        }
    }

    /** Broadcast's two rows' widths at unit u: {the first row with its tag, the second}. */
    private float[] widths(float u, String top, String mid) {
        float tagW = tag.isEmpty() ? 0 : sizeOf(tag, u * 0.17f, 0.06f) + u * 0.22f + u * 0.03f;
        return new float[]{sizeOf(top, u * 0.2f, 0f) + u * 0.28f + tagW, sizeOf(mid, u * 0.32f, 0f) + u * 0.5f};
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

    /** The mark filling a square of side s at (x, y): its house is about 51 of the icon's units across. */
    private void mark(Canvas cv, float x, float y, float s) {
        float k = s / 52f;
        m.reset();
        m.setScale(k, k);
        m.postTranslate(x - 28 * k, y - 28 * k);
        house.transform(m, drawn);
        ink.setStrokeWidth(5 * k);
        cv.drawPath(drawn, ink);
        play.transform(m, drawn);
        button.setColor(GROUND | 0xFF000000);
        button.setStrokeWidth(2 * k);
        cv.drawPath(drawn, button);
    }

    /** The biggest text size up to most at which the words fit in room. */
    private float fit(String s, float room, float most) {
        words.setTextSize(most);
        float wd = words.measureText(s);
        return wd <= room ? most : Math.max(6, most * room / Math.max(1, wd));
    }
}
