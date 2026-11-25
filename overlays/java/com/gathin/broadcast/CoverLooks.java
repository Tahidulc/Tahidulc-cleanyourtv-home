package com.gathin.broadcast;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.Locale;

/**
 * The family's own covers over a channel's logo, every look of them, drawn the same by the TV launcher over TiviMate
 * (LogoMark) and by Homefield TV over its own player (BoardLayer's cover): Whitebrook Road's channel looks and Event
 * (ChannelMark), Whitebrook Road Sports (SportsMark), the 54 styles (BrandStyles) and the Scoreboard Lab's five (LabMarks)
 * on their grounds (their plate, frosted, tempered or smoked glass, see-through); and, drawn here, dark smoked
 * glass, solid so none of the logo shows through, in Glass (the house and play mark with the family's name, Whitebrook
 * Road, beside it when there is room), Mark (the mark alone) or Plain (the glass alone); Broadcast, laid out as a sports
 * channel's own logo as one solid block ("Whitebrook Road" across its top with the picture's tag, HD or UHD, at its
 * end, "sports" large under it, LIVE spaced out at its foot while the game is live; in a wide, low box LIVE in a small box
 * beside "sports"), see-through smoked glass with white letters or in the scoreboard's own colours (its look's
 * StripLook, as Board); the competition's own logo on a dark block; and the Scoreboard Lab's five (LabMarks) on their
 * plates. Every look but the Lab's can have LIVE along its foot while the game is live, and the competition's logo at
 * its end, as one block. The family's own words, never a channel's name or logo; kind to the panel: only dark grounds and
 * a soft white mark, and an edge of its ground (inset) round everything, so the pixel or two it moves against burn-in
 * never uncovers the logo.
 *
 * <p>The code is LogoMark's own drawing moved here as it was, so the launcher's covers look as they did, and Homefield
 * TV's are the same pixels.
 */
public final class CoverLooks {
    /**
     * Every look the cover has, in both apps, in the order the launcher's Scoreboard card and Homefield TV's hold-OK menu
     * step through them, named as they name them: Whitebrook Road's own channel looks (ChannelMark), Event (ChannelMark),
     * the competition's logo, Glass, Mark, Plain, both Broadcasts, Whitebrook Road Sports (SportsMark), its styles
     * (BrandStyles) and the Scoreboard Lab's five (LabMarks).
     */
    public enum Look {
        SIGNATURE("Signature"), MATCHDAY("Matchday"), ROOFLINE("Roofline"), VELOCITY("Velocity"), ROUNDEL("Roundel"),
        BLOCK("Solid block"), FROSTED("Frosted glass"), SMOKED("Smoked glass"), EVENT_NAVY("Event, navy"),
        EVENT_WHITE("Event, white"), COMPETITION("The competition's logo"),
        GLASS("Glass, with the mark"), MARK("Mark only"), PLAIN("Plain"), BROADCAST("Broadcast, see-through"),
        BROADCAST_BOARD("Broadcast, scoreboard colours"), SPORTS("Whitebrook Road Sports"),
        SPORTS_STACKED("Whitebrook Road Sports, stacked"), STYLES("Whitebrook Road styles"),
        /** The Scoreboard Lab's five, to go with its boards (LabMarks draws them; named as LabMarks.Look names them). */
        FLOODLIGHT("Floodlight"), SPLITKIT("Split kit"), TICKER("Ticker"), DUO("Duo pills"), LED("Stadium LED");

        public final String label;

        Look(String label) {
            this.label = label;
        }

        /** Whitebrook Road Sports' wordmark (SportsMark): what is on in it, see-through over the picture if chosen. */
        public boolean sports() {
            return this == SPORTS || this == SPORTS_STACKED;
        }

        /** Event (ChannelMark's): the name and "sports" over the channel's own name, in navy or white, see-through if chosen. */
        public boolean event() {
            return this == EVENT_NAVY || this == EVENT_WHITE;
        }

        /**
         * Drawn whole over the cover by a renderer of its own (the wordmark, Event, the 54 styles, the Scoreboard Lab's
         * five): no LIVE foot, no block beside, see-through over the picture if chosen.
         */
        public boolean own() {
            return sports() || event() || this == STYLES || lab() != null;
        }

        /** The Scoreboard Lab's logo it is (LabMarks draws it, on a plate of its own), null for the others. */
        public LabMarks.Look lab() {
            return ordinal() > STYLES.ordinal() ? LabMarks.Look.of(name()) : null;
        }

        /** Laid out as a channel's own logo: three rows, taller than the others. */
        public boolean broadcast() {
            return this == BROADCAST || this == BROADCAST_BOARD;
        }

        /** Whitebrook Road's own channel looks (ChannelMark draws them). */
        public boolean channel() {
            return ordinal() <= EVENT_WHITE.ordinal();
        }

        /** The look of a name as kept in the settings; Glass for none of these (as the launcher has always kept it). */
        public static Look of(String name) {
            Look l = find(name);
            return l != null ? l : GLASS;
        }

        /** The look of a name as kept in the settings; null for none of these. */
        public static Look find(String name) {
            if (name == null) return null;
            for (Look l : values()) if (l.name().equals(name)) return l;
            return null;
        }
    }

    /** What Broadcast says: the family's own name over "sports". */
    public static final String NAME = "Whitebrook Road", WORD = "sports";
    /** Solid grounds: none of the channel's logo shows through. The mark's soft white. The ring's (the boards' INK). */
    public static final int GROUND = 0xFF0B0F24, PLAIN_GROUND = 0xFF07080F, MARK_INK = 0xFFD5DAEA, RING = 0xFFE6E9F2;

    /** The scoreboard's colours, for Broadcast in them (its look's StripLook: ground, block, words, light or not). */
    public static final class Board {
        public final int ground, block, ink;
        public final boolean light;

        public Board(int ground, int block, int ink, boolean light) {
            this.ground = ground;
            this.block = block;
            this.ink = ink;
            this.light = light;
        }
    }

    /** What a look is drawn with now; the apps set these as the channel and the game have them. */
    public static final class Parts {
        /** The picture's tag ("HD", "UHD", "4K HDR"; "" for none). */
        public String tag = "HD";
        /** The game is live; LIVE along the foot while it is (else in the look itself); the competition's logo at its end. */
        public boolean live, liveBar = true, compOn = true;
        /** The competition's logo (light, for a dark ground; null for none) and its short name when there is no logo. */
        public Bitmap comp;
        public String compName = "";
        /** The scoreboard's colours (Broadcast in them); null: see-through. */
        public Board board;
        /** The family's name alone on the Lab's logos (no SPORTS). */
        public boolean nameOnly;
        /** The edge of ground kept round everything (the burn-in step's room), in pixels. */
        public float inset;
        /** Ringed while it is being moved. */
        public boolean outlined;
        /**
         * The ground of the looks drawn whole (Event, the wordmark, the styles, the Lab's): PLATE, FROST, TEMPERED, SMOKE or
         * CLEAR (see-through over the picture, the channel's logo filled in under it once learned: filled).
         */
        public int ground = PLATE;
        public boolean filled;
        /** What is on, for Whitebrook Road Sports ("premier league", "football", "f1"). */
        public String what = "football";
        /** Which of Whitebrook Road's styles (0 to BrandStyles.COUNT - 1). */
        public int style;
        /** Event's name for the channel ("main event"; "" for none); the sport ("f1", else football) for the channel looks. */
        public String sub = "", sport = "";
        /** Event's picture and sound marks in its name's box (LogoExtras' marks; null for none). */
        public java.util.List<String> marks;
        /** Event's name turned over to the clock (0 the name, 1 the clock): its time, the weather's icon, its temperature. */
        public float turned;
        public String clockHm;
        public android.graphics.drawable.Drawable clockIcon;
        public String clockTemp = "";
        /** The picture behind frosted glass (null: none yet, the glass solid), and where the cover is on the screen. */
        public Frost frost;
        public final int[] onScreen = new int[2];
    }

    /** Event's name turning over to the clock and back (LogoExtras' clock), as both covers keep it. */
    public static final class ClockTurn {
        /** How long the name takes to turn over. */
        public static final long TURN_MS = 500L;
        private boolean on;
        private long at;

        /** The name turned over to the clock (on), or back; its time, the weather's icon (null: none), its temperature, into p. True when it changed. */
        public boolean set(Parts p, boolean turnOn, String hm, android.graphics.drawable.Drawable icon, String temp) {
            String t = temp == null ? "" : temp;
            boolean changed = turnOn != on || turnOn && (!java.util.Objects.equals(hm, p.clockHm) || icon != p.clockIcon
                    || !t.equals(p.clockTemp));
            if (turnOn != on) {
                long now = android.os.SystemClock.uptimeMillis();
                // Turning back part way round: from where it is.
                long done = Math.min(TURN_MS, now - at);
                at = now - (TURN_MS - done);
                on = turnOn;
            }
            if (turnOn) {
                p.clockHm = hm;
                p.clockIcon = icon;
                p.clockTemp = t;
            }
            return changed;
        }

        /** How far turned over to the clock now (0 the name, 1 the clock). */
        public float turned() {
            float k = Math.min(1f, (android.os.SystemClock.uptimeMillis() - at) / (float) TURN_MS);
            return on ? k : 1f - k;
        }
    }

    /** The grounds of the looks drawn whole: their plate, frosted glass, transparent, smoked glass, tempered glass. */
    public static final int PLATE = 0, FROST = 1, CLEAR = 2, SMOKE = 3, TEMPERED = 4;
    /** The grounds in the order they are stepped through: the plate, frosted, tempered and smoked glass, transparent. */
    public static final int[] GROUNDS = {PLATE, FROST, TEMPERED, SMOKE, CLEAR};

    /** Pure: a ground in words, as the launcher's Scoreboard card says it. */
    public static String groundWords(int g) {
        switch (g) {
            case FROST: return "Frosted glass";
            case TEMPERED: return "Tempered glass";
            case SMOKE: return "Smoked glass";
            case CLEAR: return "Transparent";
            default: return "Plate";
        }
    }

    /**
     * The picture behind the frosted glass (FrostPane's softened picture of the screen, the launcher's) and the one before
     * it while the new one fades in, where each lies on the screen.
     */
    public static final class Frost {
        Bitmap now, was;
        android.graphics.BitmapShader nowShader, wasShader;
        final RectF at = new RectF(), wasAt = new RectF();
        long since;

        /** A new picture (null: none, the glass solid again), where it lies on the screen (l, t, r, b, pixels): faded in over the one before. */
        public void set(Bitmap b, float l, float t, float r, float bt) {
            if (b == null) {
                now = was = null;
                nowShader = wasShader = null;
                return;
            }
            boolean fade = now != null;
            was = fade ? now : null;
            wasShader = fade ? nowShader : null;
            wasAt.set(at);
            now = b;
            nowShader = new android.graphics.BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            at.set(l, t, r, bt);
            since = android.os.SystemClock.uptimeMillis();
        }

        public boolean has() {
            return now != null;
        }
    }

    /**
     * A look of the app's own drawn inside the frame (the launcher's channel looks): its body w x h with the ground
     * running on ext further (the competition's block) and down further (LIVE's foot); live as it is to show in the body.
     */
    public interface Body {
        void draw(Canvas cv, float w, float h, float ext, float down, boolean live);

        /** Where the competition's block starts down the body (under a band across its top), in pixels; 0 for its top. */
        default float compTop(float w, float h) {
            return 0f;
        }

        /** LIVE's ink along the foot. */
        default int liveInk() {
            return 0xF2FFFFFF;
        }

        /** LIVE's two colours across it (the Whitebrook Road looks' violet to coral); null for its ink alone. */
        default int[] liveShade() {
            return null;
        }
    }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), ink = new Paint(Paint.ANTI_ALIAS_FLAG),
            words = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG), button = new Paint(Paint.ANTI_ALIAS_FLAG),
            boxes = new Paint(Paint.ANTI_ALIAS_FLAG), letters = new Paint(Paint.ANTI_ALIAS_FLAG),
            picture = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF();
    private final Path house = new Path(), play = new Path(), drawn = new Path(), clipBlock = new Path();
    private final Matrix m = new Matrix();
    private final Typeface bold, black;
    private final Context context;
    private LabMarks labMarks;
    /** The ring's dashes, made once for a width of line. */
    private float dashFor = -1;

    /** bold: the words' Outfit 700; black: LIVE's Outfit 800 (each app's own, the same fonts). */
    public CoverLooks(Context c, Typeface bold, Typeface black) {
        context = c;
        this.bold = bold != null ? bold : Typeface.DEFAULT_BOLD;
        this.black = black != null ? black : this.bold;
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
        words.setTypeface(this.bold);
        letters.setTypeface(this.bold);
        words.setColor(MARK_INK);
        edge.setStyle(Paint.Style.STROKE);
        edge.setColor(RING);
    }

    // ---- the whole cover -------------------------------------------------------------------------------------------

    /** Pure: a channel's picture tag from its name: "4K" (4K, 2160p) or "UHD", with " HDR" when it says HDR (or HDR10, Dolby Vision); "HD" for HD, FHD or 1080p; "" for none. */
    public static String tagOf(String channel) {
        String n = channel == null ? "" : " " + channel.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ") + " ";
        boolean hdr = n.contains(" HDR ") || n.contains(" HDR10 ") || n.contains(" DOLBY VISION ") || n.contains(" DV ");
        String base = n.contains(" 4K ") || n.contains(" 2160P ") ? "4K" : n.contains(" UHD ") ? "UHD"
                : n.contains(" HD ") || n.contains(" FHD ") || n.contains(" 1080P ") ? "HD" : "";
        return hdr && !base.isEmpty() && !base.equals("HD") ? base + " HDR" : base;
    }

    /** The picture tags a cover can be given: as the channel's name says (null), none, or one of these, in order. */
    public static final String[] TAGS = {null, "", "HD", "UHD", "4K", "UHD HDR", "4K HDR"};

    /** Pure: a tag choice in words: "Auto, from the channel (HD)", "None", "UHD". */
    public static String tagWords(String chosen, String channel) {
        if (chosen == null) {
            String t = tagOf(channel);
            return "Auto, from the channel" + (t.isEmpty() ? "" : " (" + t + ")");
        }
        return chosen.isEmpty() ? "None" : chosen;
    }

    /** Pure: the tag shown on a channel of this name for a choice (null: Auto). */
    public static String tagShown(String chosen, String channel) {
        return chosen == null ? tagOf(channel) : chosen;
    }

    /** Pure: the choice step away from chosen (1 the next, -1 the one before), round the list. */
    public static String nextTag(String chosen, int step) {
        int at = 0;
        for (int i = 0; i < TAGS.length; i++) if (java.util.Objects.equals(TAGS[i], chosen)) at = i;
        return TAGS[Math.floorMod(at + step, TAGS.length)];
    }

    /**
     * A whole cover full x whole in look, with these parts, the ring while moved: the looks drawn whole on their ground
     * (own), the rest in the one block with LIVE's foot and the competition's block (frame). True while something in it
     * moves (Event's clock turning over, a frosted picture fading in): to be drawn again on the next frame.
     */
    public boolean draw(Canvas cv, Look look, float full, float whole, Parts p) {
        if (full <= 0 || whole <= 0 || look == null || p == null) return false;
        if (look.own()) {
            boolean moving = own(cv, look, full, whole, p);
            ring(cv, full, whole, Math.min(full, whole) * 0.15f, p.outlined);
            return moving;
        }
        Body body = new Body() {
            @Override public void draw(Canvas c, float w, float h, float ext, float down, boolean live) {
                look(c, look, w, h, ext, down, live, p);
            }

            @Override public float compTop(float lw, float h) {
                // Under the band of a look that has one across its top (Solid block, Broadcast), in the body below it.
                if (look == Look.BLOCK) return h * (lw / h >= 2.4f ? 0.4f : 0.32f);
                if (look.broadcast()) return h * (lw / h >= 2.3f ? 0.36f : 0.3f);
                return 0f;
            }

            @Override public int liveInk() {
                // The scoreboard's ink on Broadcast in its colours; white on the glass and the competition's.
                return look == Look.BROADCAST_BOARD && p.board != null ? p.board.ink : 0xF2FFFFFF;
            }

            @Override public int[] liveShade() {
                // The Whitebrook Road looks' violet to coral (not on their glass).
                return look.channel() && look != Look.SMOKED && look != Look.FROSTED ? new int[]{ChannelMark.LILAC, ChannelMark.PEACH} : null;
            }
        };
        frame(cv, full, whole, p.live, p.liveBar, compShown(look, p), p.comp, body);
        ring(cv, full, whole, Math.min(full, whole) * 0.15f, p.outlined);
        return false;
    }

    // ---- the looks drawn whole: Event, the wordmark, the styles, the Lab's --------------------------------------------

    private ChannelMark channel;
    private BrandStyles brand;
    private SportsMark sportsMark;
    private final Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG), tint = new Paint(Paint.ANTI_ALIAS_FLAG),
            rim = new Paint(Paint.ANTI_ALIAS_FLAG), sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix frostMatrix = new Matrix();
    private float tintFor = -1f;
    private int tintKind = -1;

    /** Whitebrook Road's channel looks and Event (ChannelMark), made when first wanted. */
    private ChannelMark channel(Parts p) {
        if (channel == null) channel = new ChannelMark(context);
        channel.nameOnly(p.nameOnly);
        return channel;
    }

    /** The name's box as Event last drew it (the cover's pixels) into out; false for none. */
    public boolean nameBox(RectF out) {
        return channel != null && channel.nameBox(out);
    }

    /**
     * A look drawn whole over the cover w x h on its ground: see-through over the picture (the channel's own logo filled
     * in under it, LogoFill, the launcher's), on frosted, tempered or smoked glass (the picture behind it softened, or the
     * glass solid while none is in), or on its plate. True while something moves.
     */
    private boolean own(Canvas cv, Look look, float w, float h, Parts p) {
        boolean moving = false;
        boolean clear = p.ground == CLEAR && p.filled;
        // On glass: frosted or smoked as chosen, smoked for Transparent while the channel's logo is learned. Its picture
        // behind it once one is in; till then (or where none can be had) the same glass, solid.
        boolean frosted = !clear && p.ground != PLATE;
        float in = Math.max(p.inset, Math.min(w, h) * (clear ? 0.04f : 0f));
        int kind = p.ground == FROST || p.ground == TEMPERED ? p.ground : SMOKE;
        if (frosted) moving = glass(cv, w, h, kind, p.frost, p.onScreen);
        if (look.event()) {
            ChannelMark ch = channel(p);
            // On its plate (or glass) it fills the whole cover (the channel's logo under it never shows); alone, in from
            // the edge, and white as a channel's own logo is on its picture.
            float pad = clear ? in : 0f;
            // What is on as its own logo (the competition's, when known and switched on), else the channel's words.
            ch.eventLogo(p.compOn ? p.comp : null);
            ch.marks(p.marks);
            ch.clock(p.turned, p.clockHm, p.clockIcon, p.clockTemp);
            if (p.turned > 0f && p.turned < 1f) moving = true;
            // Its own navy on frosted and tempered glass (light glass), white on smoked and over the picture itself.
            boolean white = clear || (frosted ? kind == SMOKE : look == Look.EVENT_WHITE);
            ch.event(cv, pad, pad, w - 2 * pad, h - 2 * pad, p.sub, p.tag, white, clear ? ChannelMark.GROUND_NONE
                    : !frosted ? ChannelMark.GROUND_PLATE : kind == TEMPERED ? ChannelMark.GROUND_CLEAR_GLASS : ChannelMark.GROUND_GLASS);
            return moving;
        }
        if (look == Look.STYLES) {
            if (brand == null) brand = new BrandStyles(context);
            // On its plate the style fills the whole cover (the channel's logo under it never shows).
            float pad = clear ? in : p.inset;
            brand.draw(cv, p.style, pad, pad, w - 2 * pad, h - 2 * pad, !p.nameOnly, p.tag, !clear && !frosted);
            return moving;
        }
        LabMarks.Look lab = look.lab();
        if (lab != null) {
            // Its own plate over the cover, in from the edge when see-through; on its plate, its ground solid under all of
            // the cover first (its plate is less tall where its words want the room, a little see-through in two of the
            // looks), so the channel's logo under it never shows. As the phone's.
            if (!clear) labGround(cv, lab, w, h);
            float pad = clear ? in : p.inset;
            labLook(cv, lab, pad, pad, w - 2 * pad, h - 2 * pad, p.nameOnly, p.tag);
            return moving;
        }
        if (sportsMark == null) sportsMark = new SportsMark(context);
        sportsMark.draw(cv, in, in, w - 2 * in, h - 2 * in, look == Look.SPORTS_STACKED, !p.nameOnly, p.what, p.tag, !clear && !frosted,
                p.compOn ? p.comp : null);
        return moving;
    }

    /**
     * Glass over the cover's whole box (w by h, its plate's round corners), as the Frosted glass and Smoked glass looks
     * are: the picture behind it softened (FrostPane, lined up with the screen wherever the cover stands, the new one faded
     * in over the one before), then frosted silvery with a sheen over its top, or smoked; a fine light edge catching the
     * light along the top. With no picture yet, the same glass solid, so nothing under it shows. True while fading.
     */
    private boolean glass(Canvas cv, float w, float h, int kind, Frost f, int[] onScreen) {
        boolean moving = false;
        float rad = Math.min(w, h) * 0.14f;
        boolean seen = f != null && f.now != null;
        if (seen) {
            float k = Math.min(1f, (android.os.SystemClock.uptimeMillis() - f.since) / 350f);
            if (f.was != null && k < 1f) {
                pane(cv, f.was, f.wasShader, f.wasAt, rad, 255, w, h, onScreen);
                pane(cv, f.now, f.nowShader, f.at, rad, Math.round(255 * k), w, h, onScreen);
                moving = true;
            } else {
                f.was = null;
                f.wasShader = null;
                pane(cv, f.now, f.nowShader, f.at, rad, 255, w, h, onScreen);
            }
        }
        if (tintFor != h || tintKind != (seen ? kind : kind + 10)) {
            tintFor = h;
            tintKind = seen ? kind : kind + 10;
            // Over the picture: frosted, a pale frost with a little of the picture through it; tempered, clear glass with
            // most of the picture through it; smoked, dark. Without a picture: the light (or dark) glass solid.
            int top = kind == SMOKE ? 0x262A36 : 0xF4F7FD, foot = kind == SMOKE ? 0x0D0F16 : 0xDCE4F2;
            int a1, a2;
            if (!seen) {
                a1 = a2 = 0xFF;
            } else if (kind == FROST) {
                a1 = 0xB8;
                a2 = 0xA6;
            } else if (kind == TEMPERED) {
                a1 = 0x52;
                a2 = 0x38;
            } else {
                a1 = 0xB0;
                a2 = 0xC4;
            }
            tint.setShader(new LinearGradient(0, 0, 0, h, (a1 << 24) | top, (a2 << 24) | foot, Shader.TileMode.CLAMP));
            // Frosted: a soft sheen over its top half; tempered: a bright streak across it, as light on a glass pane.
            sheen.setShader(kind == FROST ? new LinearGradient(0, 0, 0, h * 0.55f, 0x4DFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
                    : kind == TEMPERED ? new LinearGradient(0, 0, h * 1.4f, h, new int[]{0x00FFFFFF, 0x38FFFFFF, 0x00FFFFFF,
                    0x00FFFFFF, 0x1FFFFFFF, 0x00FFFFFF}, new float[]{0.1f, 0.22f, 0.34f, 0.42f, 0.5f, 0.58f}, Shader.TileMode.CLAMP) : null);
            rim.setStyle(Paint.Style.STROKE);
            rim.setShader(new LinearGradient(0, 0, 0, h, kind == SMOKE ? 0x59FFFFFF : kind == TEMPERED ? 0xF2FFFFFF : 0xCCFFFFFF,
                    kind == SMOKE ? 0x14FFFFFF : kind == TEMPERED ? 0x66FFFFFF : 0x59FFFFFF, Shader.TileMode.CLAMP));
        }
        r.set(0, 0, w, h);
        cv.drawRoundRect(r, rad, rad, tint);
        if (kind != SMOKE) cv.drawRoundRect(r, rad, rad, sheen);
        // Tempered glass's thick edge: a brighter, wider bevel.
        float line = Math.max(1f, h * (kind == TEMPERED ? 0.026f : kind == FROST ? 0.016f : 0.014f));
        rim.setStrokeWidth(line);
        r.set(line / 2f, line / 2f, w - line / 2f, h - line / 2f);
        cv.drawRoundRect(r, rad - line / 2f, rad - line / 2f, rim);
        return moving;
    }

    /** One frosted picture over the box's round corners (the cover w x h), where it lies on the screen, at alpha. */
    private void pane(Canvas cv, Bitmap b, android.graphics.BitmapShader s, RectF at, float rad, int alpha, float w, float h, int[] onScreen) {
        if (b == null || s == null || b.isRecycled() || at.width() <= 0 || at.height() <= 0) return;
        frostMatrix.setScale(at.width() / b.getWidth(), at.height() / b.getHeight());
        frostMatrix.postTranslate(at.left - onScreen[0], at.top - onScreen[1]);
        s.setLocalMatrix(frostMatrix);
        glass.setShader(s);
        glass.setAlpha(alpha);
        r.set(0, 0, w, h);
        cv.drawRoundRect(r, rad, rad, glass);
        glass.setShader(null);
    }

    /** Whether the competition's logo stands beside the look: switched on, read, and the look is not the logo itself. */
    public static boolean compShown(Look look, Parts p) {
        return p.compOn && p.comp != null && look != Look.COMPETITION;
    }

    /**
     * The one block: while the game is live (and LIVE's foot is on), LIVE along the foot of the whole cover, part of the
     * same block, the look above it (its own LIVE left to the foot); the competition's logo at the end of the look's own
     * block when it is shown, the look's ground running on under it, a fine line between.
     */
    public void frame(Canvas cv, float full, float whole, boolean live, boolean liveBar, boolean comps, Bitmap comp, Body body) {
        float foot = live && liveBar ? Math.max(10f, whole * 0.22f) : 0f, h = whole - foot;
        float side = comps ? Math.min(h * 0.86f, full * 0.28f) : 0f;
        body.draw(cv, full - side, h, side, foot, live && foot <= 0f);
        if (side > 0f) compMark(cv, full - side, full, h, comp, body.compTop(full - side, h));
        if (foot > 0f) liveBar(cv, full, whole, foot, body.liveInk(), body.liveShade());
    }

    /** The look itself in the box w wide (the whole box, or short of the competition's block). */
    public void look(Canvas cv, Look look, float w, float h, float ext, float down, boolean live, Parts p) {
        if (look == Look.COMPETITION) {
            competitionLook(cv, w, h, down, live, p.comp, p.compName);
            return;
        }
        if (look.channel()) {
            // Whitebrook Road's own channel: its ground over the whole box, the edge the burn-in guard moves in too.
            channel(p).draw(cv, look, 0, 0, w, h, p.tag, live, p.sport, ext, down);
            return;
        }
        if (look.broadcast()) {
            broadcast(cv, w, h, ext, down, p.inset, p.tag, live, look == Look.BROADCAST || p.board == null ? null : p.board);
            return;
        }
        house(cv, look, w, h, ext, down, p.inset);
    }

    // ---- Glass, Mark, Plain ------------------------------------------------------------------------------------------

    /** Glass (the mark and, with room, the family's name), Mark (the mark alone) or Plain (the glass alone), w x h. */
    public void house(Canvas cv, Look look, float w, float h, float ext, float down, float inset) {
        float rad = Math.min(w, h) * 0.18f;
        r.set(0, 0, w + ext, h + down);
        fill.setShader(null);
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

    /** The mark filling a square of side s at (x, y): its house is about 51 of the icon's units across. */
    public void mark(Canvas cv, float x, float y, float s) {
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

    // ---- LIVE's foot, the competition's block, the ring --------------------------------------------------------------

    /**
     * LIVE along the cover's foot, as part of the one block: the look's own ground runs on down under it (drawn by the
     * look), a fine light line across, and LIVE spaced out between two short rules, in ink (shaded across in two colours
     * where given).
     */
    public void liveBar(Canvas cv, float w, float h, float foot, int inkColour, int[] shade) {
        fill.setShader(null);
        // The fine line between the look and its foot.
        float line = Math.max(1f, h * 0.008f), in = Math.min(w, h) * 0.1f;
        fill.setColor(0x26FFFFFF);
        cv.drawRect(in, h - foot, w - in, h - foot + line, fill);
        words.setTypeface(black);
        words.setTextSize(foot * 0.5f);
        words.setLetterSpacing(0.42f);
        float tw = words.measureText("LIVE"), cx = w / 2f, cy = h - foot / 2f + line / 2f;
        Shader inkShader = shade == null || shade.length < 2 ? null
                : new LinearGradient(cx - tw / 2f, 0, cx + tw / 2f, 0, shade[0], shade[1], Shader.TileMode.CLAMP);
        // Two short rules either side of it.
        float gap = foot * 0.4f, rule = Math.min(foot * 1.2f, (w - tw) / 2f - gap - in);
        if (rule > foot * 0.3f) {
            fill.setColor((inkColour & 0x00FFFFFF) | 0x59000000);
            cv.drawRect(cx - tw / 2f - gap - rule, cy - line / 2f, cx - tw / 2f - gap, cy + line / 2f, fill);
            cv.drawRect(cx + tw / 2f + gap, cy - line / 2f, cx + tw / 2f + gap + rule, cy + line / 2f, fill);
        }
        words.setColor(inkColour);
        words.setShader(inkShader);
        words.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = words.getFontMetrics();
        // Letter spacing leaves half a space after the last letter: centred on the letters themselves.
        cv.drawText("LIVE", cx + words.getTextSize() * 0.21f, cy - (fm.ascent + fm.descent) / 2f, words);
        words.setShader(null);
        words.setTextAlign(Paint.Align.LEFT);
        words.setLetterSpacing(0f);
        words.setTypeface(bold);
        words.setColor(MARK_INK);
    }

    /** The competition's logo from x0 to x1 on the look's ground, its body from top down: a fine light line before it, the logo in the middle. */
    public void compMark(Canvas cv, float x0, float x1, float h, Bitmap comp, float top) {
        if (comp == null || comp.getWidth() <= 0 || comp.getHeight() <= 0) return;
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

    /** Ringed while it is being moved (Move and resize): a dashed line round it. */
    public void ring(Canvas cv, float w, float h, float rad, boolean outlined) {
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

    // ---- the competition's own logo -----------------------------------------------------------------------------------

    /**
     * The competition's own logo over the channel's: a dark block, the logo as large as it fits in the middle (its name
     * in its place while the logo is read, or when it has none), LIVE's red dot in the corner while the game is.
     */
    public void competitionLook(Canvas cv, float w, float h, float down, boolean live, Bitmap comp, String compName) {
        float rad = Math.min(w, h) * 0.16f;
        r.set(0, 0, w, h + down);
        fill.setShader(new LinearGradient(0, 0, 0, h, 0xFF1B1F31, 0xFF07090F, Shader.TileMode.CLAMP));
        cv.drawRoundRect(r, rad, rad, fill);
        fill.setShader(null);
        edge.setPathEffect(null);
        dashFor = -1;
        float pad = Math.min(w, h) * 0.14f;
        if (comp != null && comp.getWidth() > 0 && comp.getHeight() > 0) {
            float bw = w - 2 * pad, bh = h - 2 * pad, k = Math.min(bw / comp.getWidth(), bh / comp.getHeight());
            float dw = comp.getWidth() * k, dh = comp.getHeight() * k;
            r.set((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f);
            cv.drawBitmap(comp, null, r, picture);
        } else {
            String name = compName == null ? "" : compName.trim();
            String n = name.isEmpty() ? "LIVE" : name.toUpperCase(Locale.ROOT);
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

    // ---- Broadcast -----------------------------------------------------------------------------------------------------

    /** Pure: a blend of a and b, share of the way to b (opaque), as the boards' sums. */
    static int blend(int a, int b, float share) {
        float s = Math.max(0f, Math.min(1f, share));
        int rr = Math.round(((a >> 16) & 0xFF) * (1 - s) + ((b >> 16) & 0xFF) * s);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - s) + ((b >> 8) & 0xFF) * s);
        int bl = Math.round((a & 0xFF) * (1 - s) + (b & 0xFF) * s);
        return 0xFF000000 | rr << 16 | g << 8 | bl;
    }

    /** Pure: dark words on a light colour, the soft white on a dark one, as the boards' sums. */
    static int inkOn(int colour) {
        int rr = (colour >> 16) & 0xFF, g = (colour >> 8) & 0xFF, b = colour & 0xFF;
        return (rr * 299 + g * 587 + b * 114) / 1000 > 150 ? 0xFF0A0D33 : RING;
    }

    /**
     * Pure: Broadcast's colours {ground, band, band's words, tag's box, tag's words, words, LIVE's}: see-through (board
     * null: smoked glass, white letters, the top band a lighter grey), or the scoreboard's (its ground, the top band in
     * its block).
     */
    public static int[] broadcastColours(Board s) {
        if (s == null) return new int[]{0xC20E1014, 0x40FFFFFF, 0xF7FFFFFF, 0x33FFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xE6FFFFFF};
        int ground = 0xFF000000 | s.ground;
        int band = (s.block >>> 24) == 0xFF ? s.block : blend(ground, 0xFFFFFFFF, 0.22f);
        int bandInk = inkOn(band);
        return new int[]{ground, band, bandInk, s.light ? 0x1A0B1640 : 0x33FFFFFF, s.light ? bandInk : 0xFFFFFFFF, s.ink, s.ink};
    }

    /**
     * Broadcast: laid out as a sports channel's own logo, as one solid block filling the box: the name across its top
     * band with the picture's tag at the band's end, "sports" large in the middle, LIVE spaced out in its foot while
     * the game is live. See-through (board null), or the scoreboard's colours.
     */
    public void broadcast(Canvas cv, float w, float h, float ext, float down, float inset, String tag, boolean live, Board board) {
        float cw = w - 2 * inset, ch = h - 2 * inset;
        if (cw <= 0 || ch <= 0) return;
        String t = tag == null ? "" : tag;
        int[] k = broadcastColours(board);
        int ground = k[0], band = k[1], bandInk = k[2], tagBox = k[3], tagInk = k[4], inkC = k[5], liveInk = k[6];
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
        if (!t.isEmpty()) {
            float ts = band1 * 0.5f;
            tagW = sizeOf(t, ts, 0.06f) + band1 * 0.5f;
            boxes.setColor(tagBox);
            r.set(inset + cw - pad - tagW, inset + band1 * 0.16f, inset + cw - pad, inset + band1 * 0.84f);
            cv.drawRoundRect(r, band1 * 0.12f, band1 * 0.12f, boxes);
            line(cv, t, r.centerX(), inset, band1, ts, 0.06f, tagInk, true);
        }
        float nameRoom = cw - 2 * pad - (tagW > 0 ? tagW + pad * 0.6f : 0);
        float ns = fitSize(NAME, nameRoom, band1 * 0.58f, 0.02f);
        line(cv, NAME, inset + pad, inset, band1, ns, 0.02f, bandInk, false);
        if (wide) {
            // "sports" large from the left, LIVE in a small box at the row's end.
            float mid = ch - band1, ls = mid * 0.28f, liveW = live ? sizeOf("LIVE", ls, 0.2f) + mid * 0.36f : 0f;
            float ms = fitSize(WORD, cw - 2 * pad - (liveW > 0 ? liveW + pad : 0f), mid * 0.7f, 0f);
            line(cv, WORD, inset + pad, inset + band1, mid, ms, 0f, inkC, false);
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
            line(cv, WORD, inset + cw / 2f, inset + band1, mid, ms, 0f, inkC, true);
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

    // ---- the Scoreboard Lab's five ---------------------------------------------------------------------------------------

    /**
     * A Scoreboard Lab logo (LabMarks) in the box bw by bh at (x, y): the family's name with SPORTS (alone for a general
     * channel) and the picture's tag, as tall as the box and as wide; where its words want more room than the box has,
     * the tag goes first, then it is drawn less tall, in the middle of the box.
     */
    public void labLook(Canvas cv, LabMarks.Look l, float x, float y, float bw, float bh, boolean nameOnly, String tag) {
        if (bw <= 2 || bh <= 2) return;
        if (labMarks == null) labMarks = new LabMarks(context);
        String sports = nameOnly ? "" : "SPORTS", t = tag == null ? "" : tag;
        float natural = labMarks.width(l, bh, NAME, sports, t);
        if (natural > bw && !t.isEmpty()) {
            t = "";
            natural = labMarks.width(l, bh, NAME, sports, t);
        }
        float lh = labHeight(natural, bw, bh);
        cv.save();
        cv.translate(x, y + (bh - lh) / 2f);
        labMarks.draw(cv, l, bw, lh, NAME, sports, t);
        cv.restore();
    }

    /** A Lab logo's own ground, solid, over the whole cover w by h, its corners as its plate's. */
    public void labGround(Canvas cv, LabMarks.Look l, float w, float h) {
        int[] g = labGround(l);
        float rad = l == LabMarks.Look.DUO ? h / 2f : h * g[1] / 1000f;
        fill.setShader(null);
        fill.setColor(g[0]);
        r.set(0, 0, w, h);
        cv.drawRoundRect(r, rad, rad, fill);
    }

    /** Pure: a Lab logo's ground {its colour, its corners' radius in thousandths of its height} (Floodlight's white plate, the rest dark). */
    public static int[] labGround(LabMarks.Look l) {
        switch (l) {
            case FLOODLIGHT: return new int[]{0xFFF6F7FA, 74};
            case SPLITKIT: return new int[]{0xFF0B0E16, 80};
            case TICKER: return new int[]{0xFF080A10, 80};
            case DUO: return new int[]{0xFF080A10, 500};
            case LED: default: return new int[]{0xFF1A1A1A, 130};
        }
    }

    /** Pure: how tall a Lab logo is drawn in a box bw by bh, natural its width when as tall as the box: the box's height, or less to fit. */
    public static float labHeight(float natural, float bw, float bh) {
        return natural <= bw || natural <= 0f ? bh : bh * bw / natural;
    }
}
