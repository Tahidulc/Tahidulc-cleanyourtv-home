package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
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
        BLOCK("Solid block"), FROSTED("Frosted glass"), SMOKED("Smoked glass"), EVENT_NAVY("Event, navy"),
        EVENT_WHITE("Event, white"), COMPETITION("The competition's logo"),
        GLASS("Glass, with the mark"), MARK("Mark only"), PLAIN("Plain"), BROADCAST("Broadcast, see-through"),
        BROADCAST_BOARD("Broadcast, scoreboard colours"), SPORTS("Whitebrook Road Sports"),
        SPORTS_STACKED("Whitebrook Road Sports, stacked"), STYLES("Whitebrook Road styles");

        /** Whitebrook Road Sports' wordmark (SportsMark): what is on in it, see-through over the picture if chosen. */
        boolean sports() {
            return this == SPORTS || this == SPORTS_STACKED;
        }

        /** Event (ChannelMark's): the name and "sports" over the channel's own name, in navy or white, see-through if chosen. */
        boolean event() {
            return this == EVENT_NAVY || this == EVENT_WHITE;
        }

        /**
         * Drawn whole over the cover by a renderer of its own (the wordmark, Event, the 54 styles): no LIVE foot, no block
         * beside, see-through over the picture if chosen.
         */
        boolean own() {
            return sports() || event() || this == STYLES;
        }

        /** Laid out as a channel's own logo: three rows, taller than the others. */
        boolean broadcast() {
            return this == BROADCAST || this == BROADCAST_BOARD;
        }

        /** Whitebrook Road's own channel looks (ChannelMark draws them). */
        boolean channel() {
            return ordinal() <= EVENT_WHITE.ordinal();
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
    /** The competition's logo beside every look (on unless switched off on the Scoreboard card). */
    private boolean compOn = true;
    /** LIVE along the cover's foot while the game is live (on unless switched off on the Scoreboard card). */
    private boolean liveBar = true;
    private android.graphics.Typeface liveFace;
    /** How much further the look's ground runs than its own width (the competition's logo's room). */
    private float ext;
    /** How much further down the look's ground runs than its own height (LIVE's foot). */
    private float down;
    /** The ring's dashes, made once for a width of line. */
    private float dashFor = -1;
    /** Whitebrook Road Sports: what is on ("premier league"), the name with SPORTS or alone, see-through or on its plate. */
    private SportsMark sportsMark;
    private String what = "football";
    private boolean nameOnly;
    /**
     * Its ground in the looks drawn whole (the wordmark, Event, the styles): its own plate; frosted glass (the picture
     * behind it softened and lightened, FrostPane); or none, over the picture itself with the channel's logo filled in
     * under it (see-through: frosted glass while that logo is learned).
     */
    static final int PLATE = 0, FROST = 1, CLEAR = 2, SMOKE = 3;
    /** The grounds in the order they are stepped through: the plate, frosted glass, smoked glass, transparent. */
    static final int[] GROUNDS = {PLATE, FROST, SMOKE, CLEAR};
    private int ground = PLATE;
    /** See-through: the channel's logo filled in under it (LogoFill), so it stands clear over the picture. */
    private boolean filled;
    /** The frosted glass's picture (FrostPane), the one before it while the new one fades in, where each lies on the screen. */
    private Bitmap frost, frostWas;
    private BitmapShader frostShader, frostWasShader;
    private final RectF frostAt = new RectF(), frostWasAt = new RectF();
    private long frostSince;
    private final int[] onScreen = new int[2];
    private final Matrix frostMatrix = new Matrix();
    private final Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG), tint = new Paint(Paint.ANTI_ALIAS_FLAG),
            rim = new Paint(Paint.ANTI_ALIAS_FLAG), sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float tintFor = -1f;
    private int tintKind = -1;
    /** Whitebrook Road styles: the renderer and the style chosen (0 to BrandStyles.COUNT - 1). */
    private BrandStyles brand;
    private int styleNo;
    /** Event's name for the channel ("main event"; "" for none), and the channel's name it was read from. */
    private String sub = "", subFrom;

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
        if (look.broadcast() || look.channel() || look.own()) invalidate();
    }

    /** The competition being watched: its logo (light, for a dark ground; null for none) and its short name. */
    void competition(android.graphics.Bitmap logo, String name) {
        comp = logo;
        compName = name == null ? "" : name.trim();
        invalidate();
    }

    /** LIVE's bar along the foot while the game is live, or not. */
    void liveBar(boolean on) {
        if (on == liveBar) return;
        liveBar = on;
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

    /** What is on, for Whitebrook Road Sports ("premier league", "football", "f1"). */
    void what(String w) {
        String n = w == null || w.isEmpty() ? "football" : w;
        if (n.equals(what)) return;
        what = n;
        if (look.sports()) invalidate();
    }

    /** Event's name for the channel as it is to show ("main event"; "" for none). */
    void sub(String s) {
        String n = s == null ? "" : s;
        if (n.equals(sub)) return;
        sub = n;
        if (look.event()) invalidate();
    }

    /** The channel under the cover, by its name: Event's own name for it (ChannelMark.subOf, read once a channel). */
    void channelName(String name) {
        if (java.util.Objects.equals(name, subFrom)) return;
        subFrom = name;
        sub(ChannelMark.subOf(name));
    }

    /** The family's name alone (WHITEBROOK ROAD), or with SPORTS. */
    void nameOnly(boolean on) {
        if (on == nameOnly) return;
        nameOnly = on;
        if (channel != null) channel.nameOnly(on);
        invalidate();
    }

    /** See-through over the picture, the channel's own logo filled in under it (looks that can be): Transparent, else its plate. */
    void seeThrough(boolean on) {
        ground(on ? CLEAR : PLATE);
    }

    /** Its ground: PLATE, FROST, SMOKE or CLEAR (the looks drawn whole; the others keep their own grounds). */
    void ground(int g) {
        int k = g == FROST || g == CLEAR || g == SMOKE ? g : PLATE;
        if (k == ground) return;
        ground = k;
        if (k == PLATE) frost(null, 0, 0, 0, 0);
        invalidate();
    }

    int ground() {
        return ground;
    }

    /**
     * The frosted glass's picture (FrostPane's, null: none) and where it lies on the screen (l, t, r, b, pixels): faded
     * in over the one before. With none, the plate.
     */
    void frost(Bitmap b, float l, float t, float r, float bt) {
        if (b == null) {
            if (frost == null) return;
            frost = frostWas = null;
            frostShader = frostWasShader = null;
            invalidate();
            return;
        }
        boolean fade = frost != null;
        frostWas = fade ? frost : null;
        frostWasShader = fade ? frostShader : null;
        frostWasAt.set(frostAt);
        frost = b;
        frostShader = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        frostAt.set(l, t, r, bt);
        frostSince = SystemClock.uptimeMillis();
        invalidate();
    }

    boolean hasFrost() {
        return frost != null;
    }

    /** Whitebrook Road styles: which of them (0 to BrandStyles.COUNT - 1). */
    void style(int n) {
        int k = BrandStyles.clamp(n);
        if (k == styleNo) return;
        styleNo = k;
        if (look == Look.STYLES) invalidate();
    }

    /** Whether it wants the channel's logo filled in under it now (LogoShape): see-through, in a look that can be. */
    String lookName() {
        return look.name();
    }

    boolean lookCanSeeThrough() {
        return look.own();
    }

    boolean seeThroughSet() {
        return ground == CLEAR;
    }

    boolean wantsPatch() {
        return ground != PLATE && look.own() && getVisibility() == VISIBLE;
    }

    /**
     * Whether the channel's logo is filled in under it (LogoFill): it then stands clear over the picture; else (its
     * shape still being learned, a solid block, no pictures to be had) on its plate.
     */
    void filled(boolean on) {
        if (on == filled) return;
        filled = on;
        invalidate();
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
        float full = getWidth(), whole = getHeight();
        if (full <= 0 || whole <= 0) return;
        if (look.own()) {
            ownLook(cv, full, whole);
            ring(cv, full, whole, Math.min(full, whole) * 0.15f);
            return;
        }
        // While the game is live: LIVE along the foot of the whole cover, part of the same block, the look above it (its
        // own LIVE left to the foot).
        float foot = live && liveBar ? Math.max(10f, whole * 0.22f) : 0f, h = whole - foot;
        down = foot;
        boolean wasLive = live;
        if (foot > 0f) live = false;
        // The competition's logo at the end of the look's own block, when it is on and known: one block, the look's
        // ground running on under it, a fine line between.
        float side = compShown() ? Math.min(h * 0.86f, full * 0.28f) : 0f;
        ext = side;
        drawLook(cv, full - side, h);
        if (side > 0f) compMark(cv, full - side, full, h);
        if (foot > 0f) liveBar(cv, full, whole, foot);
        live = wasLive;
        ring(cv, full, whole, Math.min(full, whole) * 0.15f);
    }

    /**
     * LIVE along the cover's foot, as part of the one block: the look's own ground runs on down under it (drawn by the
     * look), a fine light line across, and LIVE spaced out between two short rules,
     * in the look's own colours (the Whitebrook Road looks' violet to coral, white on the glass and the competition's,
     * the scoreboard's ink on Broadcast in its colours).
     */
    private void liveBar(Canvas cv, float w, float h, float foot) {
        int ink = 0xF2FFFFFF;
        android.graphics.Shader inkShader = null;
        if (look == Look.BROADCAST_BOARD && board != null) ink = board.ink;
        fill.setShader(null);
        // The fine line between the look and its foot.
        float line = Math.max(1f, h * 0.008f), in = Math.min(w, h) * 0.1f;
        fill.setColor(0x26FFFFFF);
        cv.drawRect(in, h - foot, w - in, h - foot + line, fill);
        if (liveFace == null) liveFace = ScoreCard.outfit(getContext(), 800);
        words.setTypeface(liveFace);
        words.setTextSize(foot * 0.5f);
        words.setLetterSpacing(0.42f);
        float tw = words.measureText("LIVE"), cx = w / 2f, cy = h - foot / 2f + line / 2f;
        if (look.channel() && look != Look.SMOKED && look != Look.FROSTED) {
            inkShader = new android.graphics.LinearGradient(cx - tw / 2f, 0, cx + tw / 2f, 0, ChannelMark.LILAC, ChannelMark.PEACH,
                    android.graphics.Shader.TileMode.CLAMP);
        }
        // Two short rules either side of it.
        float gap = foot * 0.4f, rule = Math.min(foot * 1.2f, (w - tw) / 2f - gap - in);
        if (rule > foot * 0.3f) {
            fill.setColor((ink & 0x00FFFFFF) | 0x59000000);
            cv.drawRect(cx - tw / 2f - gap - rule, cy - line / 2f, cx - tw / 2f - gap, cy + line / 2f, fill);
            cv.drawRect(cx + tw / 2f + gap, cy - line / 2f, cx + tw / 2f + gap + rule, cy + line / 2f, fill);
        }
        words.setColor(ink);
        words.setShader(inkShader);
        words.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = words.getFontMetrics();
        // Letter spacing leaves half a space after the last letter: centred on the letters themselves.
        cv.drawText("LIVE", cx + words.getTextSize() * 0.21f, cy - (fm.ascent + fm.descent) / 2f, words);
        words.setShader(null);
        words.setTextAlign(Paint.Align.LEFT);
        words.setLetterSpacing(0f);
        words.setTypeface(ScoreCard.outfit(getContext(), 700));
        words.setColor(MARK_INK);
    }

    /** Whether the competition's logo stands beside the look: switched on, read, and the look is not the logo itself. */
    private boolean compShown() {
        return compOn && comp != null && look != Look.COMPETITION;
    }

    /** The competition's logo from x0 to x1 on the look's ground: a fine light line before it, the logo in the middle. */
    private void compMark(Canvas cv, float x0, float x1, float h) {
        // Under the band of a look that has one across its top (Solid block, Broadcast), in the body below it.
        float lw = x0, top = 0f;
        if (look == Look.BLOCK) top = h * (lw / h >= 2.4f ? 0.4f : 0.32f);
        else if (look.broadcast()) top = h * (lw / h >= 2.3f ? 0.36f : 0.3f);
        float bodyH = h - top;
        fill.setShader(null);
        fill.setColor(0x40FFFFFF);
        cv.drawRect(x0, top + bodyH * 0.2f, x0 + Math.max(1f, h * 0.012f), top + bodyH * 0.8f, fill);
        float pad = Math.min(x1 - x0, bodyH) * 0.2f, bw = x1 - x0 - 2 * pad, bh = bodyH - 2 * pad;
        float k = Math.min(bw / comp.getWidth(), bh / comp.getHeight()), dw = comp.getWidth() * k, dh = comp.getHeight() * k;
        float cx = (x0 + x1) / 2f + Math.max(1f, h * 0.006f) - (x1 - x0) * 0.04f, cy = top + bodyH / 2f;
        r.set(cx - dw / 2f, cy - dh / 2f, cx + dw / 2f, cy + dh / 2f);
        cv.drawBitmap(comp, null, r, picture);
    }

    /**
     * Whitebrook Road Sports: see-through over the picture (the channel's own logo filled in under it, LogoFill), or on
     * its plate while that cannot be; the competition's logo in it.
     */
    private void ownLook(Canvas cv, float w, float h) {
        boolean clear = ground == CLEAR && filled;
        // On glass: frosted or smoked as chosen, smoked for Transparent while the channel's logo is learned. Its picture
        // behind it once one is in; till then (or where none can be had) the same glass, solid.
        boolean frosted = !clear && ground != PLATE;
        float in = Math.max(inset, Math.min(w, h) * (clear ? 0.04f : 0f));
        if (frosted) glass(cv, w, h, ground == FROST ? FROST : SMOKE);
        if (look.event()) {
            if (channel == null) {
                channel = new ChannelMark(getContext());
                channel.nameOnly(nameOnly);
            }
            // On its plate (or glass) it fills the whole cover (the channel's logo under it never shows); alone, in from
            // the edge, and white as a channel's own logo is on its picture.
            float p = clear ? in : 0f;
            channel.event(cv, p, p, w - 2 * p, h - 2 * p, sub, tag, look == Look.EVENT_WHITE || clear || frosted,
                    clear ? ChannelMark.GROUND_NONE : frosted ? ChannelMark.GROUND_GLASS : ChannelMark.GROUND_PLATE);
            return;
        }
        if (look == Look.STYLES) {
            if (brand == null) brand = new BrandStyles(getContext());
            // On its plate the style fills the whole cover (the channel's logo under it never shows).
            float p = clear ? in : inset;
            brand.draw(cv, styleNo, p, p, w - 2 * p, h - 2 * p, !nameOnly, tag, !clear && !frosted);
            return;
        }
        if (sportsMark == null) sportsMark = new SportsMark(getContext());
        sportsMark.draw(cv, in, in, w - 2 * in, h - 2 * in, look == Look.SPORTS_STACKED, !nameOnly, what, tag, !clear && !frosted,
                compOn ? comp : null);
    }

    /**
     * Glass over the cover's whole box (w by h, its plate's round corners), as the Frosted glass and Smoked glass looks
     * are: the picture behind it softened (FrostPane, lined up with the screen wherever the cover stands, the new one faded
     * in over the one before), then frosted silvery with a sheen over its top, or smoked; a fine light edge catching the
     * light along the top. With no picture yet, the same glass solid, so nothing under it shows.
     */
    private void glass(Canvas cv, float w, float h, int kind) {
        float rad = Math.min(w, h) * 0.14f;
        boolean seen = frost != null;
        if (seen) {
            getLocationOnScreen(onScreen);
            float k = Math.min(1f, (SystemClock.uptimeMillis() - frostSince) / 350f);
            if (frostWas != null && k < 1f) {
                pane(cv, frostWas, frostWasShader, frostWasAt, rad, 255);
                pane(cv, frost, frostShader, frostAt, rad, Math.round(255 * k));
                postInvalidateOnAnimation();
            } else {
                frostWas = null;
                frostWasShader = null;
                pane(cv, frost, frostShader, frostAt, rad, 255);
            }
        }
        if (tintFor != h || tintKind != (seen ? kind : kind + 10)) {
            tintFor = h;
            tintKind = seen ? kind : kind + 10;
            // Over the picture: most of the glass's own colour, a little of the picture through it. Without: solid.
            int top = kind == FROST ? 0x9AA5BA : 0x262A36, foot = kind == FROST ? 0x5D677D : 0x0D0F16;
            int a1 = seen ? (kind == FROST ? 0xB3 : 0xB0) : 0xFF, a2 = seen ? (kind == FROST ? 0xBF : 0xC4) : 0xFF;
            tint.setShader(new android.graphics.LinearGradient(0, 0, 0, h, (a1 << 24) | top, (a2 << 24) | foot, Shader.TileMode.CLAMP));
            sheen.setShader(kind == FROST ? new android.graphics.LinearGradient(0, 0, 0, h * 0.55f, 0x47FFFFFF, 0x00FFFFFF,
                    Shader.TileMode.CLAMP) : null);
            rim.setStyle(Paint.Style.STROKE);
            rim.setShader(new android.graphics.LinearGradient(0, 0, 0, h, kind == FROST ? 0xB3FFFFFF : 0x59FFFFFF,
                    kind == FROST ? 0x4DFFFFFF : 0x14FFFFFF, Shader.TileMode.CLAMP));
        }
        r.set(0, 0, w, h);
        cv.drawRoundRect(r, rad, rad, tint);
        if (kind == FROST) cv.drawRoundRect(r, rad, rad, sheen);
        float line = Math.max(1f, h * (kind == FROST ? 0.016f : 0.014f));
        rim.setStrokeWidth(line);
        r.set(line / 2f, line / 2f, w - line / 2f, h - line / 2f);
        cv.drawRoundRect(r, rad - line / 2f, rad - line / 2f, rim);
    }

    /** One frosted picture over the box's round corners, where it lies on the screen, at alpha. */
    private void pane(Canvas cv, Bitmap b, BitmapShader s, RectF at, float rad, int alpha) {
        if (b == null || s == null || b.isRecycled() || at.width() <= 0 || at.height() <= 0) return;
        frostMatrix.setScale(at.width() / b.getWidth(), at.height() / b.getHeight());
        frostMatrix.postTranslate(at.left - onScreen[0], at.top - onScreen[1]);
        s.setLocalMatrix(frostMatrix);
        glass.setShader(s);
        glass.setAlpha(alpha);
        r.set(0, 0, getWidth(), getHeight());
        cv.drawRoundRect(r, rad, rad, glass);
        glass.setShader(null);
    }

    /** The look itself in the box w wide (the whole box, or short of the competition's block). */
    private void drawLook(Canvas cv, float w, float h) {
        if (look == Look.COMPETITION) {
            competitionLook(cv, w, h);
            return;
        }
        if (look.channel()) {
            // Whitebrook Road's own channel: its ground over the whole box, the edge the burn-in guard moves in too.
            if (channel == null) {
                channel = new ChannelMark(getContext());
                channel.nameOnly(nameOnly);
            }
            channel.draw(cv, look, 0, 0, w, h, tag, live, sport, ext, down);
            return;
        }
        if (look.broadcast()) {
            broadcast(cv, w, h, look == Look.BROADCAST || board == null);
            return;
        }
        float rad = Math.min(w, h) * 0.18f;
        r.set(0, 0, w + ext, h + down);
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
    }

    /**
     * The competition's own logo over the channel's: a dark block, the logo as large as it fits in the middle (its name
     * in its place while the logo is read, or when it has none), LIVE's red dot in the corner while the game is.
     */
    private void competitionLook(Canvas cv, float w, float h) {
        float rad = Math.min(w, h) * 0.16f;
        r.set(0, 0, w, h + down);
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
        r.set(inset, inset, inset + cw + ext, inset + ch + down);
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
        cv.drawRect(inset, inset, inset + cw + ext, inset + band1, boxes);
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
