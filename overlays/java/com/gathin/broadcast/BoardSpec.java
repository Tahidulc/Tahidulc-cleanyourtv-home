package com.gathin.broadcast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * One of the family's own scoreboards (the scoreboard editor, My scoreboards, the generator and Ask AI): a small,
 * versioned design as JSON, drawn by the boards the apps already have (the phone's OverlayBug, the TV launcher's
 * BoardBug, later Homefield TV's), so a goal, the pop-outs beside the board, added time and the kick-off strap all
 * come with it.
 *
 * <p>It names the layout (one of the first nine looks, the ones drawn from a palette), its colours (the panel, the
 * competition's block, the score's cell, the clock's cell, the words on each, a quieter word colour, an accent,
 * added time's red, which is AddedTime.RED unless set, and the added time's tab), the corners, a border, a finish
 * (gloss, flat or frosted), a typeface of the bundled ones, how the crests sit, and the clubs' colour strips.
 *
 * <p>parse() never throws and never gives back a board that cannot be drawn or read: anything missing, of the wrong
 * kind or out of range falls back to a safe value (Classic's own), the words are kept legible on what they sit on
 * (at least FLOOR contrast), and the panel is always solid, so the channel's own scoreboard under it never shows.
 * A newer version's spec is read for what this one knows.
 */
public final class BoardSpec {
    /** This version of the spec; a spec without "v" is taken as version 1. */
    public static final int VERSION = 1;
    /** The least contrast a board's words keep on what they sit on (WCAG; 4.5 is what the generator aims for). */
    public static final double FLOOR = 3.0, AIM = 4.5;
    /** At most this long, a board's name. */
    public static final int NAME_MAX = 40;

    /** The layouts a custom board can take: the looks the boards draw from a palette (in the pickers' order). */
    public enum Base {
        CLASSIC("Classic"), GLASS("Glass"), LIGHT("Light"), CLUB("Club colours"), MINIMAL("Minimal"),
        CRESTS("Crests and time"), STACKED("Stacked"), CARD("Card"), FROSTED("Frosted glass");

        public final String label;

        Base(String label) {
            this.label = label;
        }

        public static Base of(String s, Base fallback) {
            if (s == null) return fallback;
            String n = s.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
            for (Base b : values()) if (b.name().equals(n) || b.label.equalsIgnoreCase(s.trim())) return b;
            return fallback;
        }
    }

    /** The board's surface: a sheen across its top (gloss), none (flat), or frost falling darker with a fine light edge. */
    public enum Finish {
        GLOSS("Gloss"), FLAT("Flat"), FROSTED("Frosted");

        public final String label;

        Finish(String label) {
            this.label = label;
        }

        public static Finish of(String s, Finish fallback) {
            if (s == null) return fallback;
            for (Finish f : values()) if (f.name().equalsIgnoreCase(s.trim())) return f;
            return fallback;
        }
    }

    /** The bundled typefaces (overlays/assets/broadcast/fonts, SIL Open Font Licence), and the system's bold. */
    public enum Font {
        OUTFIT("Outfit", "broadcast/fonts/outfit.ttf"), KHAND("Khand", "broadcast/fonts/Khand-700.ttf"),
        BARLOW("Barlow Condensed", "broadcast/fonts/BarlowCondensed-700.ttf"),
        BARLOW_HEAVY("Barlow Condensed Heavy", "broadcast/fonts/BarlowCondensed-800.ttf"),
        BARLOW_SEMI("Barlow Semi Condensed", "broadcast/fonts/BarlowSemiCondensed-700.ttf"),
        MONTSERRAT("Montserrat", "broadcast/fonts/Montserrat-800.ttf"), INTER("Inter", "broadcast/fonts/Inter-600.ttf"),
        RUBIK("Rubik", "broadcast/fonts/Rubik-600.ttf"), SYSTEM("System bold", null);

        public final String label;
        /** Its file in the apps' assets, or null for the system's own bold. */
        public final String asset;

        Font(String label, String asset) {
            this.label = label;
            this.asset = asset;
        }

        public static Font of(String s, Font fallback) {
            if (s == null) return fallback;
            String n = s.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
            for (Font f : values()) if (f.name().equals(n) || f.label.equalsIgnoreCase(s.trim())) return f;
            return fallback;
        }
    }

    /** How the crests sit: as they are, on a light disc, or in a ring of the accent. */
    public enum Crest {
        PLAIN("As they are"), DISC("On a disc"), RING("In a ring");

        public final String label;

        Crest(String label) {
            this.label = label;
        }

        public static Crest of(String s, Crest fallback) {
            if (s == null) return fallback;
            for (Crest c : values()) if (c.name().equalsIgnoreCase(s.trim())) return c;
            return fallback;
        }
    }

    /** The colours' names in the JSON, in the editor's order. */
    public static final String[] COLOURS = {"panel", "panelFoot", "comp", "score", "scoreFoot", "clock", "text", "scoreText",
            "clockText", "dim", "accent", "stoppage", "added"};

    public String name = "My scoreboard";
    public Base base = Base.CLASSIC;
    /** The panel (the board's ground; solid), and the colour it falls to at its foot (0: one colour). */
    public int panel = 0xFF0B1640, panelFoot;
    /** The competition's block, the score's cell (and its foot), the clock's cell; cells may be see-through over the panel. */
    public int comp = 0xFF070C26, score = 0xFF2443B4, scoreFoot = 0xFF142A78, clock = 0xFF050816;
    /** The words: the clubs' (on the panel), the score's (on its cell), the clock's (on its cell), the quieter ones. */
    public int text = BoardColours.INK, scoreText = BoardColours.INK, clockText = BoardColours.INK, dim = 0xFFA9B1D6;
    /** The accent (the ring round crests, the line along the foot), added time's red, the added time's tab. */
    public int accent = 0xFFF5C518, stoppage = AddedTime.RED, added = AddedTime.RED;
    /** The corners' radius as a share of the board's height (0 square to 0.5 a pill). */
    public float radius = 0.14f;
    /** A border round the board: its width as a share of the height (0: none, at most 0.1), and its colour. */
    public float borderWidth;
    public int border = 0x8CFFFFFF;
    public Finish finish = Finish.FLAT;
    public Font font = Font.OUTFIT;
    public Crest crest = Crest.PLAIN;
    /** The clubs' colour strips beside their names; a fine accent line along the board's foot. */
    public boolean strips = true, accentLine;

    public BoardSpec() {
    }

    /** A copy. */
    public BoardSpec copy() {
        return parse(toJson());
    }

    // ---- reading -------------------------------------------------------------------------------------------------------

    /** A spec from JSON text: never null, never throws; whatever cannot be read is the safe default. */
    public static BoardSpec parse(String json) {
        if (json == null) return new BoardSpec().validated();
        try {
            return from(new JSONObject(json));
        } catch (Exception e) {
            return new BoardSpec().validated();
        }
    }

    /** A spec from its JSON object: never null, never throws. */
    public static BoardSpec from(JSONObject o) {
        BoardSpec s = new BoardSpec();
        if (o == null) return s.validated();
        try {
            s.name = name(o.optString("name", s.name));
            s.base = Base.of(o.optString("base", null), Base.CLASSIC);
            s.defaultsFor(s.base);
            JSONObject c = o.optJSONObject("colours");
            if (c == null) c = o.optJSONObject("colors");
            if (c != null) {
                s.panel = colour(c, "panel", s.panel);
                s.panelFoot = colourOrNone(c, "panelFoot", s.panelFoot);
                s.comp = colour(c, "comp", s.comp);
                s.score = colour(c, "score", s.score);
                s.scoreFoot = colourOrNone(c, "scoreFoot", s.score);
                s.clock = colour(c, "clock", s.clock);
                s.text = colour(c, "text", s.text);
                s.scoreText = colour(c, "scoreText", s.scoreText);
                s.clockText = colour(c, "clockText", s.clockText);
                s.dim = colour(c, "dim", BoardColours.mix(s.text, s.panel, 0.32f));
                s.accent = colour(c, "accent", s.accent);
                s.stoppage = colour(c, "stoppage", AddedTime.RED);
                s.added = colour(c, "added", s.stoppage);
            }
            s.radius = number(o, "radius", s.radius);
            Object b = o.opt("border");
            if (b instanceof JSONObject) {
                s.borderWidth = number((JSONObject) b, "width", 0f);
                s.border = colour((JSONObject) b, "colour", s.border);
            } else {
                s.borderWidth = number(o, "borderWidth", s.borderWidth);
                if (b instanceof String) s.border = BoardColours.parse((String) b, s.border);
            }
            s.finish = Finish.of(o.optString("finish", null), s.finish);
            s.font = Font.of(o.optString("font", null), s.font);
            s.crest = Crest.of(o.optString("crest", null), s.crest);
            if (o.has("strips")) s.strips = o.optBoolean("strips", s.strips);
            if (o.has("accentLine")) s.accentLine = o.optBoolean("accentLine", s.accentLine);
        } catch (RuntimeException e) {
            // what was read so far stands; the rest is the default
        }
        return s.validated();
    }

    /** The look's own colours and shape as a custom board's start (the editor's "pick a base"). */
    public static BoardSpec of(Base b) {
        BoardSpec s = new BoardSpec();
        s.base = b;
        s.name = b.label;
        s.defaultsFor(b);
        return s.validated();
    }

    /** This base's own colours (as the boards draw it), finish and corners. */
    void defaultsFor(Base b) {
        panelFoot = 0;
        strips = true;
        finish = Finish.FLAT;
        borderWidth = 0;
        switch (b) {
            case GLASS:
                panel = 0xFF151B36; comp = 0xFF151B36; score = 0x40FFFFFF; scoreFoot = 0x1CFFFFFF; clock = 0x47000000;
                text = scoreText = clockText = BoardColours.INK; dim = 0xFFC3CAE6; radius = 0.5f; finish = Finish.GLOSS;
                break;
            case LIGHT:
                panel = 0xFFE3E7EE; comp = 0xFF0B1640; score = scoreFoot = 0xFF0B1640; clock = 0xFFD0D6E2;
                text = clockText = 0xFF0B1640; scoreText = BoardColours.INK; dim = 0xFF5A6280; radius = 0.14f;
                break;
            case CLUB:
                panel = 0xFF0E0E10; comp = 0xFF000000; score = 0xFF1A1A1C; scoreFoot = 0xFF000000; clock = 0xFF000000;
                text = scoreText = clockText = BoardColours.INK; dim = 0xFFB0B0B8; radius = 0.14f; strips = false;
                break;
            case MINIMAL:
                panel = 0xFF070912; comp = 0xFF070912; score = scoreFoot = 0xFF070912; clock = 0xFF070912;
                text = scoreText = BoardColours.INK; clockText = 0xFFDDE3FF; dim = 0xFFA9B1D6; radius = 0.25f;
                break;
            case CRESTS:
                panel = 0xFF0A1238; comp = 0xFF070C26; score = 0xFF2443B4; scoreFoot = 0xFF142A78; clock = 0xFF050816;
                text = scoreText = clockText = BoardColours.INK; dim = 0xFFA9B1D6; radius = 0.14f;
                break;
            case STACKED:
                panel = 0xFF0E1330; comp = 0xFF070C26; score = scoreFoot = 0xFF1E2A66; clock = 0xFF050816;
                text = scoreText = clockText = BoardColours.INK; dim = 0xFFA9B1D6; radius = 0.08f;
                break;
            case CARD:
                panel = 0xFFF1F3F7; panelFoot = 0xFFD3D8E1; comp = 0xFFF1F3F7; score = 0xFF2A3DC9; scoreFoot = 0xFF0F1B7E;
                clock = 0xFF0F1B7E; text = 0xFF0B1640; scoreText = clockText = BoardColours.INK; dim = 0xFF4E5778; radius = 0.08f;
                break;
            case FROSTED:
                panel = 0xFF9AA5BA; panelFoot = 0xFF5D677D; comp = 0xFF9AA5BA; score = 0x3DFFFFFF; scoreFoot = 0x1FFFFFFF;
                clock = 0x24000000; text = scoreText = clockText = 0xFFFFFFFF; dim = 0xFFE3E8F2; radius = 0.3f;
                finish = Finish.FROSTED;
                break;
            default:
                panel = 0xFF0B1640; comp = 0xFF070C26; score = 0xFF2443B4; scoreFoot = 0xFF142A78; clock = 0xFF050816;
                text = scoreText = clockText = BoardColours.INK; dim = 0xFFA9B1D6; radius = 0.14f;
        }
        accent = 0xFFF5C518;
        stoppage = added = AddedTime.RED;
    }

    private static String name(String n) {
        String s = n == null ? "" : n.replaceAll("[\\x00-\\x1F\\x7F]", " ").trim().replaceAll("\\s+", " ");
        if (s.isEmpty()) s = "My scoreboard";
        return s.length() > NAME_MAX ? s.substring(0, NAME_MAX).trim() : s;
    }

    private static int colour(JSONObject o, String k, int fallback) {
        Object v = o.opt(k);
        if (v instanceof String) return BoardColours.parse((String) v, fallback);
        if (v instanceof Number) return ((Number) v).intValue();
        return fallback;
    }

    /** A colour, or 0 for "none" ("" or null given), or fallback when absent or unreadable. */
    private static int colourOrNone(JSONObject o, String k, int fallback) {
        if (!o.has(k)) return fallback;
        Object v = o.opt(k);
        if (v == null || v == JSONObject.NULL) return 0;
        if (v instanceof String && ((String) v).trim().isEmpty()) return 0;
        return colour(o, k, fallback);
    }

    private static float number(JSONObject o, String k, float fallback) {
        Object v = o.opt(k);
        if (v instanceof Number) {
            double d = ((Number) v).doubleValue();
            return Double.isNaN(d) || Double.isInfinite(d) ? fallback : (float) d;
        }
        if (v instanceof String) {
            try {
                float f = Float.parseFloat(((String) v).trim());
                return Float.isNaN(f) || Float.isInfinite(f) ? fallback : f;
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }

    // ---- keeping it safe -----------------------------------------------------------------------------------------------

    /**
     * Itself, made safe to draw: the panel solid, its foot solid (or none), the corners and the border in range, every
     * word colour solid and readable (FLOOR) on what it sits on, the quieter words a little softer but still readable.
     */
    public BoardSpec validated() {
        name = name(name);
        if (base == null) base = Base.CLASSIC;
        if (finish == null) finish = Finish.FLAT;
        if (font == null) font = Font.OUTFIT;
        if (crest == null) crest = Crest.PLAIN;
        panel = BoardColours.opaque(panel);
        if (panelFoot != 0) panelFoot = BoardColours.opaque(panelFoot);
        if (BoardColours.alpha(comp) < 0x40) comp = BoardColours.opaque(panel);
        if (BoardColours.alpha(score) < 0x18) score = BoardColours.withAlpha(score, 0x18);
        if (scoreFoot == 0) scoreFoot = score;
        if (BoardColours.alpha(clock) < 0x10 && base != Base.MINIMAL) clock = BoardColours.withAlpha(clock, 0x10);
        radius = clamp(radius, 0f, 0.5f);
        borderWidth = clamp(borderWidth, 0f, 0.1f);
        if (borderWidth > 0 && BoardColours.alpha(border) < 0x30) border = BoardColours.withAlpha(border, 0x30);
        int ground = panelMid();
        text = BoardColours.legible(text, ground, FLOOR);
        dim = BoardColours.legible(BoardColours.opaque(dim), ground, FLOOR);
        int scoreGround = BoardColours.over(BoardColours.mix(score, scoreFoot, 0.5f), ground);
        scoreText = BoardColours.legible(scoreText, scoreGround, FLOOR);
        int clockGround = BoardColours.over(clock, ground);
        clockText = BoardColours.legible(clockText, clockGround, FLOOR);
        accent = BoardColours.opaque(accent);
        stoppage = BoardColours.opaque(stoppage);
        added = BoardColours.opaque(added);
        return this;
    }

    private static float clamp(float v, float lo, float hi) {
        if (Float.isNaN(v)) return lo;
        return Math.max(lo, Math.min(hi, v));
    }

    /** The panel's colour half way down (its foot mixed in), as the words see it. */
    public int panelMid() {
        return panelFoot == 0 ? panel : BoardColours.mix(panel, panelFoot, 0.5f);
    }

    /** What the score's figures sit on, solid (the cell over the panel). */
    public int scoreGround() {
        return BoardColours.over(BoardColours.mix(score, scoreFoot, 0.5f), panelMid());
    }

    /** What the clock's figures sit on, solid. */
    public int clockGround() {
        return BoardColours.over(clock, panelMid());
    }

    /** The worst contrast of its words on what they sit on: the clubs', the score's, the clock's. */
    public double leastContrast() {
        double a = BoardColours.contrast(text, panelMid());
        double b = BoardColours.contrast(scoreText, scoreGround());
        double c = base == Base.MINIMAL ? a : BoardColours.contrast(clockText, clockGround());
        double d = BoardColours.contrast(0xFFFFFFFF, stoppage);
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    /** Whether the panel is light (dark words on it). */
    public boolean light() {
        return BoardColours.luminance(panelMid()) > 0.4;
    }

    /** The panel falling to this at its foot: its own foot, or (Frosted) a deeper frost; 0 for one colour. */
    public int groundFoot() {
        if (panelFoot != 0) return panelFoot;
        if (finish == Finish.FROSTED) return BoardColours.value(panel, 0.66f);
        return 0;
    }

    /** A sheen across its top: gloss and frost have one. */
    public boolean sheen() {
        return finish != Finish.FLAT;
    }

    /** The edge round it: the border's width as a share of the height (a frosted board's fine light one when none is set). */
    public float edgeWidth() {
        if (borderWidth > 0) return borderWidth;
        return finish == Finish.FROSTED ? 0.025f : 0f;
    }

    public int edgeColour() {
        return borderWidth > 0 ? border : 0x8CFFFFFF;
    }

    // ---- writing -------------------------------------------------------------------------------------------------------

    public JSONObject json() {
        JSONObject o = new JSONObject();
        try {
            o.put("v", VERSION);
            o.put("name", name);
            o.put("base", base.name());
            JSONObject c = new JSONObject();
            c.put("panel", BoardColours.hex(panel));
            c.put("panelFoot", panelFoot == 0 ? "" : BoardColours.hex(panelFoot));
            c.put("comp", BoardColours.hex(comp));
            c.put("score", BoardColours.hex(score));
            c.put("scoreFoot", BoardColours.hex(scoreFoot));
            c.put("clock", BoardColours.hex(clock));
            c.put("text", BoardColours.hex(text));
            c.put("scoreText", BoardColours.hex(scoreText));
            c.put("clockText", BoardColours.hex(clockText));
            c.put("dim", BoardColours.hex(dim));
            c.put("accent", BoardColours.hex(accent));
            c.put("stoppage", BoardColours.hex(stoppage));
            c.put("added", BoardColours.hex(added));
            o.put("colours", c);
            o.put("radius", round3(radius));
            o.put("border", new JSONObject().put("width", round3(borderWidth)).put("colour", BoardColours.hex(border)));
            o.put("finish", finish.name());
            o.put("font", font.name());
            o.put("crest", crest.name());
            o.put("strips", strips);
            o.put("accentLine", accentLine);
        } catch (JSONException e) {
            // every value above is plain
        }
        return o;
    }

    public String toJson() {
        return json().toString();
    }

    private static double round3(float f) {
        return Math.round(f * 1000) / 1000.0;
    }

    /** A colour by its JSON name (the editor's slots). */
    public int colour(String key) {
        switch (key) {
            case "panel": return panel;
            case "panelFoot": return panelFoot;
            case "comp": return comp;
            case "score": return score;
            case "scoreFoot": return scoreFoot;
            case "clock": return clock;
            case "text": return text;
            case "scoreText": return scoreText;
            case "clockText": return clockText;
            case "dim": return dim;
            case "accent": return accent;
            case "stoppage": return stoppage;
            case "added": return added;
            case "border": return border;
            default: return 0;
        }
    }

    /** A colour set by its JSON name (then validated() again by the caller, when it is done). */
    public void colour(String key, int c) {
        switch (key) {
            case "panel": panel = c; break;
            case "panelFoot": panelFoot = c; break;
            case "comp": comp = c; break;
            case "score": score = c; scoreFoot = BoardColours.value(c, 0.62f); break;
            case "scoreFoot": scoreFoot = c; break;
            case "clock": clock = c; break;
            case "text": text = c; break;
            case "scoreText": scoreText = c; break;
            case "clockText": clockText = c; break;
            case "dim": dim = c; break;
            case "accent": accent = c; break;
            case "stoppage": stoppage = c; break;
            case "added": added = c; break;
            case "border": border = c; break;
            default:
        }
    }

    /** The colours' slots as the editor names them. */
    public static String label(String key) {
        switch (key) {
            case "panel": return "Panel";
            case "panelFoot": return "Panel's foot";
            case "comp": return "Competition block";
            case "score": return "Score cell";
            case "scoreFoot": return "Score cell's foot";
            case "clock": return "Clock cell";
            case "text": return "Names";
            case "scoreText": return "Score";
            case "clockText": return "Clock";
            case "dim": return "Quiet words";
            case "accent": return "Accent";
            case "stoppage": return "Added time red";
            case "added": return "Added time tab";
            case "border": return "Border";
            default: return key;
        }
    }

    /** The slots the editor offers (the feet follow their cells unless set). */
    public static final String[] EDITABLE = {"panel", "comp", "score", "clock", "text", "scoreText", "clockText", "accent",
            "stoppage", "added", "border"};

    // ---- what the strap pop-up draws it with -------------------------------------------------------------------------

    /** The colours the kick-off strap draws its custom look in (strap.js S.custom), as CSS. */
    public JSONObject strapJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("base", base.name());
            o.put("panel", css(panel));
            o.put("panelFoot", css(groundFoot() == 0 ? panel : groundFoot()));
            o.put("comp", css(comp));
            o.put("score", css(score));
            o.put("scoreFoot", css(scoreFoot));
            o.put("clock", css(clock));
            o.put("ink", css(text));
            o.put("scoreInk", css(scoreText));
            o.put("clockInk", css(clockText));
            o.put("dim", css(dim));
            o.put("accent", css(accent));
            o.put("radius", round3(radius));
            o.put("border", round3(edgeWidth()));
            o.put("borderColour", css(edgeColour()));
            o.put("sheen", sheen());
            o.put("strips", strips && base != Base.CLUB);
            o.put("club", base == Base.CLUB);
            o.put("accentLine", accentLine);
            o.put("font", strapFamily(font));
            o.put("weight", font == Font.INTER || font == Font.RUBIK ? 600 : font == Font.BARLOW_SEMI ? 700 : 800);
        } catch (JSONException e) {
            // plain values
        }
        return o;
    }

    /** The strap page's own name for a typeface (strap.html's @font-face), "" for the page's Outfit. */
    static String strapFamily(Font f) {
        switch (f) {
            case MONTSERRAT: return "CBMont";
            case INTER: return "CBInter";
            case RUBIK: return "CBRubik";
            case BARLOW: case BARLOW_HEAVY: return "CBBarlowC";
            case BARLOW_SEMI: return "CBBarlowS";
            default: return "";
        }
    }

    /** A colour as CSS: #rrggbb, or rgba() for a see-through one. */
    static String css(int c) {
        int a = c >>> 24;
        if (a == 0xFF) return String.format(Locale.ROOT, "#%06x", c & 0xFFFFFF);
        return String.format(Locale.ROOT, "rgba(%d,%d,%d,%.3f)", (c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, a / 255f);
    }

    // ---- the JSON schema Ask AI's answer is held to (structured outputs) ------------------------------------------------

    /**
     * The spec's JSON schema for the Messages API's structured outputs (output_config.format): every property required,
     * no others; colours as "#RRGGBB" strings (panelFoot "" for none), numbers checked here afterwards (the schema has no
     * ranges), the names from this class's enums.
     */
    public static JSONObject schema() {
        try {
            JSONObject colours = new JSONObject().put("type", "object");
            JSONObject cp = new JSONObject();
            JSONArray cr = new JSONArray();
            for (String k : COLOURS) {
                cp.put(k, new JSONObject().put("type", "string").put("description", colourHint(k)));
                cr.put(k);
            }
            colours.put("properties", cp).put("required", cr).put("additionalProperties", false);
            JSONObject border = new JSONObject().put("type", "object")
                    .put("properties", new JSONObject()
                            .put("width", new JSONObject().put("type", "number").put("description", "Share of the board's height, 0 (none) to 0.1"))
                            .put("colour", new JSONObject().put("type", "string").put("description", "#RRGGBB or #AARRGGBB")))
                    .put("required", new JSONArray().put("width").put("colour")).put("additionalProperties", false);
            JSONObject p = new JSONObject()
                    .put("v", new JSONObject().put("type", "integer").put("description", "Always 1"))
                    .put("name", new JSONObject().put("type", "string").put("description", "A short name for the board, at most 40 characters"))
                    .put("base", enumOf(Base.values(), "The layout"))
                    .put("colours", colours)
                    .put("radius", new JSONObject().put("type", "number").put("description", "Corner radius as a share of the board's height, 0 to 0.5"))
                    .put("border", border)
                    .put("finish", enumOf(Finish.values(), "The surface"))
                    .put("font", enumOf(Font.values(), "The typeface"))
                    .put("crest", enumOf(Crest.values(), "How the club crests sit"))
                    .put("strips", new JSONObject().put("type", "boolean").put("description", "Each club's colour strip beside its name"))
                    .put("accentLine", new JSONObject().put("type", "boolean").put("description", "A fine accent line along the board's foot"));
            JSONArray req = new JSONArray();
            for (String k : new String[]{"v", "name", "base", "colours", "radius", "border", "finish", "font", "crest", "strips", "accentLine"}) {
                req.put(k);
            }
            return new JSONObject().put("type", "object").put("properties", p).put("required", req).put("additionalProperties", false);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JSONObject enumOf(Enum<?>[] values, String description) throws JSONException {
        JSONArray a = new JSONArray();
        for (Enum<?> e : values) a.put(e.name());
        return new JSONObject().put("type", "string").put("enum", a).put("description", description);
    }

    private static String colourHint(String k) {
        switch (k) {
            case "panelFoot": return "The panel's colour at its foot for a vertical gradient (#RRGGBB), or \"\" for one flat colour";
            case "score": case "scoreFoot": case "clock":
                return "#RRGGBB, or #AARRGGBB for a see-through cell over the panel";
            default: return "#RRGGBB";
        }
    }
}
