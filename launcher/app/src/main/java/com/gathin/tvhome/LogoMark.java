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
 * glass, solid so none of the logo shows through, in one of three looks: Glass (the house and play mark with the
 * family's name, Whitebrook Road, beside it when there is room), Mark (the mark alone) or Plain (the glass alone). Or as a sports
 * channel's own logo is laid out (Broadcast), as one solid block: "Whitebrook Road" across its top with the picture's tag
 * (HD, UHD) at its end, "sports" large under it, LIVE spaced out at its foot while the game is live (in a wide, low box,
 * in a small box beside "sports"); see-through smoked
 * glass with white letters, as channels' own are, or in the scoreboard's own colours (its look's StripLook). The
 * family's own words, never a channel's name or logo. Kind to the panel:
 * only dark grounds and a soft white mark, never the phone's bright violet-to-coral tile, and an edge of its ground
 * (inset) round everything, so the pixel or two it moves against burn-in never uncovers the logo.
 */
final class LogoMark extends View {
    /** Its looks, as the Scoreboard card names them. */
    enum Look {
        SIGNATURE("Signature"), MATCHDAY("Matchday"), ROOFLINE("Roofline"), VELOCITY("Velocity"), ROUNDEL("Roundel"),
        BLOCK("Solid block"), SMOKED("Smoked glass"), COMPETITION("The competition's logo"),
        GLASS("Glass, with the mark"), MARK("Mark only"), PLAIN("Plain"), BROADCAST("Broadcast, see-through"),
        BROADCAST_BOARD("Broadcast, scoreboard colours");

        /** Laid out as a channel's own logo: three rows, taller than the others. */
        boolean broadcast() {
            return this == BROADCAST || this == BROADCAST_BOARD;
        }

        /** Whitebrook Road's own channel looks (ChannelMark draws them). */
        boolean channel() {
            return ordinal() <= SMOKED.ordinal();
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
    private final Path clipBlock = new Path();
    private int inset;
    private boolean outlined;
    /** The channel looks, drawn when one is chosen; the sport ("f1", else football) for their channel's name. */
    private ChannelMark channel;
    private String sport = "";
    /** The competition's logo for its look (white, for a dark ground) and its short name when there is no logo. */
    private android.graphics.Bitmap comp;
    private String compName = "";
    private final Paint picture = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
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
        setContentDescription(NAME);
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
        if (look.broadcast() || look.channel()) invalidate();
    }

    /** The competition being watched: its logo (light, for a dark ground; null for none) and its short name. */
    void competition(android.graphics.Bitmap logo, String name) {
        comp = logo;
        compName = name == null ? "" : name.trim();
        if (look == Look.COMPETITION) invalidate();
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
    void boardLook(BoardBug.StripLook s) {
        if (s == board) return;
        board = s;
        if (look == Look.BROADCAST_BOARD) invalidate();
    }

    /** Pure: a channel's picture tag from its name (ChannelMark.tagOf): "4K" or "UHD", with " HDR" or not, "HD", or none. */
    static String tagOf(String channel) {
        return ChannelMark.tagOf(channel);
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
        if (look == Look.COMPETITION) {
            competitionLook(cv, w, h);
            ring(cv, w, h, Math.min(w, h) * 0.15f);
            return;
        }
        if (look.channel()) {
            // Whitebrook Road's own channel: its ground over the whole box, the edge the burn-in guard moves in too.
            if (channel == null) channel = new ChannelMark(getContext());
            channel.draw(cv, look, 0, 0, w, h, tag, live, sport);
            ring(cv, w, h, Math.min(w, h) * 0.15f);
            return;
        }
        if (look.broadcast()) {
            broadcast(cv, w, h, look == Look.BROADCAST || board == null);
            ring(cv, w, h, Math.min(w, h) * 0.1f);
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
                float size = fit(NAME, room, ch * 0.44f);
                words.setTextSize(size);
                Paint.FontMetrics fm = words.getFontMetrics();
                cv.drawText(NAME, left, inset + ch / 2 - (fm.ascent + fm.descent) / 2, words);
            }
        }
        ring(cv, w, h, rad);
    }

    /**
     * The competition's own logo over the channel's: a dark block, the logo as large as it fits in the middle (its name
     * in its place while the logo is read, or when it has none), LIVE's red dot in the corner while the game is.
     */
    private void competitionLook(Canvas cv, float w, float h) {
        float rad = Math.min(w, h) * 0.16f;
        r.set(0, 0, w, h);
        fill.setShader(new android.graphics.LinearGradient(0, 0, 0, h, 0xFF1B1F31, 0xFF07090F, android.graphics.Shader.TileMode.CLAMP));
        cv.drawRoundRect(r, rad, rad, fill);
        fill.setShader(null);
        edge.setPathEffect(null);
        float pad = Math.min(w, h) * 0.14f;
        if (comp != null && comp.getWidth() > 0 && comp.getHeight() > 0) {
            float bw = w - 2 * pad, bh = h - 2 * pad, k = Math.min(bw / comp.getWidth(), bh / comp.getHeight());
            float dw = comp.getWidth() * k, dh = comp.getHeight() * k;
            r.set((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f);
            cv.drawBitmap(comp, null, r, picture);
        } else {
            String n = compName.isEmpty() ? "LIVE" : compName.toUpperCase(java.util.Locale.ROOT);
            float size = Math.min(h * 0.42f, fit(n, w - 2 * pad, h * 0.42f));
            words.setTextSize(size);
            words.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics fm = words.getFontMetrics();
            cv.drawText(n, w / 2f, h / 2f - (fm.ascent + fm.descent) / 2f, words);
            words.setTextAlign(Paint.Align.LEFT);
        }
        if (live) {
            float dot = Math.max(2f, h * 0.045f);
            fill.setColor(0xFFFF3B4E);
            cv.drawCircle(w - pad * 0.7f, pad * 0.7f, dot, fill);
        }
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

    /** What Broadcast says: the family's own name over "sports". */
    static final String NAME = "Whitebrook Road", WORD = "sports";

    /**
     * Broadcast: laid out as a sports channel's own logo, as one solid block filling the box: the name across its top
     * band with the picture's tag at the band's end, "sports" large in the middle, LIVE spaced out in its foot while
     * the game is live. See-through (smoked glass, white letters, the top band a lighter grey), or the scoreboard's
     * colours (its ground, the top band in its block).
     */
    private void broadcast(Canvas cv, float w, float h, boolean seeThrough) {
        float cw = w - 2 * inset, ch = h - 2 * inset;
        if (cw <= 0 || ch <= 0) return;
        int ground, band, bandInk, tagBox, tagInk, ink, liveInk;
        if (seeThrough) {
            ground = 0xC20E1014;
            band = 0x40FFFFFF;
            bandInk = 0xF7FFFFFF;
            tagBox = 0x33FFFFFF;
            tagInk = 0xFFFFFFFF;
            ink = 0xFFFFFFFF;
            liveInk = 0xE6FFFFFF;
        } else {
            BoardBug.StripLook s = board;
            ground = 0xFF000000 | s.ground;
            band = (s.block >>> 24) == 0xFF ? s.block : BoardBug.blend(ground, 0xFFFFFFFF, 0.22f);
            bandInk = BoardBug.ink(band);
            tagBox = s.light ? 0x1A0B1640 : 0x33FFFFFF;
            tagInk = s.light ? bandInk : 0xFFFFFFFF;
            ink = s.ink;
            liveInk = s.ink;
        }
        float rad = Math.min(cw, ch) * 0.1f;
        // The one block, filling the box.
        r.set(inset, inset, inset + cw, inset + ch);
        boxes.setColor(ground);
        cv.drawRoundRect(r, rad, rad, boxes);
        cv.save();
        clipBlock.reset();
        clipBlock.addRoundRect(r, rad, rad, Path.Direction.CW);
        cv.clipPath(clipBlock);
        // Its top band: the name, the tag at its end. A wide, low box keeps LIVE beside "sports" rather than under it.
        boolean wide = cw / ch >= 2.3f;
        float band1 = ch * (wide ? 0.36f : 0.3f), pad = Math.min(cw, ch) * 0.08f;
        boxes.setColor(band);
        cv.drawRect(inset, inset, inset + cw, inset + band1, boxes);
        float tagW = 0;
        if (!tag.isEmpty()) {
            float ts = band1 * 0.5f;
            tagW = sizeOf(tag, ts, 0.06f) + band1 * 0.5f;
            boxes.setColor(tagBox);
            r.set(inset + cw - pad - tagW, inset + band1 * 0.16f, inset + cw - pad, inset + band1 * 0.84f);
            cv.drawRoundRect(r, band1 * 0.12f, band1 * 0.12f, boxes);
            line(cv, tag, r.centerX(), inset, band1, ts, 0.06f, tagInk, true);
        }
        float nameRoom = cw - 2 * pad - (tagW > 0 ? tagW + pad * 0.6f : 0);
        float ns = fitSize(NAME, nameRoom, band1 * 0.58f, 0.02f);
        line(cv, NAME, inset + pad, inset, band1, ns, 0.02f, bandInk, false);
        if (wide) {
            // "sports" large from the left, LIVE in a small box at the row's end.
            float mid = ch - band1, ls = mid * 0.28f, liveW = live ? sizeOf("LIVE", ls, 0.2f) + mid * 0.36f : 0f;
            float ms = fitSize(WORD, cw - 2 * pad - (liveW > 0 ? liveW + pad : 0f), mid * 0.7f, 0f);
            line(cv, WORD, inset + pad, inset + band1, mid, ms, 0f, ink, false);
            if (live) {
                boxes.setColor(tagBox);
                r.set(inset + cw - pad - liveW, inset + band1 + mid * 0.27f, inset + cw - pad, inset + band1 + mid * 0.73f);
                cv.drawRoundRect(r, mid * 0.08f, mid * 0.08f, boxes);
                line(cv, "LIVE", r.centerX(), inset + band1, mid, ls, 0.2f, liveInk, true);
            }
        } else {
            // "sports" large in the middle; LIVE spaced out in the foot.
            float foot = ch * 0.24f, mid = ch - band1 - foot;
            float ms = fitSize(WORD, cw - 2 * pad, mid * 0.72f, 0f);
            line(cv, WORD, inset + cw / 2f, inset + band1, mid, ms, 0f, ink, true);
            if (live) line(cv, "LIVE", inset + cw / 2f, inset + band1 + mid, foot * 0.9f, Math.min(foot * 0.62f, fitSize("LIVE", cw - 2 * pad, foot * 0.62f, 0.35f)), 0.35f, liveInk, true);
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
