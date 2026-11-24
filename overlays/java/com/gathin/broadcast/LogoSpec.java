package com.gathin.broadcast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * A channel logo the family makes themselves (the logo picker's Make my own, offline or with Ask AI): a small versioned
 * JSON design that LogoArt draws into a crisp see-through picture, never an image from a model. Its words (one or two
 * lines), a layout (a wordmark, a monogram, the words stacked, a badge, a roundel), a bundled typeface, its colours (a
 * ground or none, the words, an accent, the second line's), a shape behind it, one simple mark from a fixed set and
 * where it sits, italic, an outline round the words.
 *
 * <p>parse() never throws: what can't be read is a safe value, the words kept to letters, digits and a few signs, and
 * readable on what they sit on (a logo with no ground is for the dark tiles the apps put logos on, so its words are
 * kept light).
 */
public final class LogoSpec {
    public static final int VERSION = 1;
    /** At most this many characters on the first line, and on the second. */
    public static final int TEXT_MAX = 14, SUB_MAX = 18;

    public enum Layout {
        WORDMARK("Wordmark"), MONOGRAM("Monogram"), STACKED("Stacked"), BADGE("Badge"), ROUNDEL("Roundel");

        public final String label;

        Layout(String label) {
            this.label = label;
        }
    }

    public enum Shape {
        NONE("None"), ROUNDED("Rounded"), PILL("Pill"), CIRCLE("Circle"), SHIELD("Shield"), HEXAGON("Hexagon"), SLANT("Slant");

        public final String label;

        Shape(String label) {
            this.label = label;
        }
    }

    /** The simple vector marks LogoArt draws (nothing else can be asked for). */
    public enum Mark {
        NONE, STAR, BOLT, BALL, PLAY, WAVE, CHEVRON, CROWN, DIAMOND, SUN, HOUSE, RINGS
    }

    public enum Side {
        LEFT, TOP, RIGHT
    }

    public String text = "TV", sub = "";
    public Layout layout = Layout.WORDMARK;
    public BoardSpec.Font font = BoardSpec.Font.OUTFIT;
    /** The ground behind the words (0: none, see-through), the words, the accent (the mark, a rule), the second line. */
    public int ground = 0xFF0B1640, ink = 0xFFFFFFFF, accent = 0xFFF5C518, subInk = 0xFFC9D1F0;
    public Shape shape = Shape.ROUNDED;
    public Mark mark = Mark.NONE;
    public Side side = Side.LEFT;
    public boolean italic, outline;

    public LogoSpec copy() {
        return parse(toJson());
    }

    private static <E extends Enum<E>> E pick(Class<E> type, String s, E fallback) {
        if (s == null) return fallback;
        String n = s.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        for (E e : type.getEnumConstants()) if (e.name().equals(n)) return e;
        return fallback;
    }

    /** The words kept to what a logo shows: letters, digits, spaces and & + - . ! ' (no control characters), short. */
    static String words(String s, int max) {
        String t = s == null ? "" : s.replaceAll("[^\\p{L}\\p{N} &+\\-.!']", "").trim().replaceAll("\\s+", " ");
        return t.length() > max ? t.substring(0, max).trim() : t;
    }

    public static LogoSpec parse(String json) {
        if (json == null) return new LogoSpec().validated();
        try {
            return from(new JSONObject(json));
        } catch (Exception e) {
            return new LogoSpec().validated();
        }
    }

    public static LogoSpec from(JSONObject o) {
        LogoSpec s = new LogoSpec();
        if (o == null) return s.validated();
        try {
            s.text = o.optString("text", s.text);
            s.sub = o.optString("sub", "");
            s.layout = pick(Layout.class, o.optString("layout", null), s.layout);
            s.font = BoardSpec.Font.of(o.optString("font", null), s.font);
            JSONObject c = o.optJSONObject("colours");
            if (c == null) c = o.optJSONObject("colors");
            if (c != null) {
                Object g = c.opt("ground");
                if (g instanceof String && ((String) g).trim().isEmpty()) s.ground = 0;
                else if (g instanceof String) s.ground = BoardColours.parse((String) g, s.ground);
                else if (g == JSONObject.NULL) s.ground = 0;
                s.ink = BoardColours.parse(c.optString("ink", null), s.ink);
                s.accent = BoardColours.parse(c.optString("accent", null), s.accent);
                s.subInk = BoardColours.parse(c.optString("sub", null), s.subInk);
            }
            s.shape = pick(Shape.class, o.optString("shape", null), s.shape);
            s.mark = pick(Mark.class, o.optString("mark", null), s.mark);
            s.side = pick(Side.class, o.optString("markSide", null), s.side);
            s.italic = o.optBoolean("italic", false);
            s.outline = o.optBoolean("outline", false);
        } catch (RuntimeException e) {
            // what was read stands
        }
        return s.validated();
    }

    /** Itself made safe to draw: words kept short and plain (never empty), colours solid, the words readable. */
    public LogoSpec validated() {
        text = words(text, TEXT_MAX);
        if (text.isEmpty()) text = "TV";
        sub = words(sub, SUB_MAX);
        if (layout == null) layout = Layout.WORDMARK;
        if (font == null) font = BoardSpec.Font.OUTFIT;
        if (shape == null) shape = Shape.NONE;
        if (mark == null) mark = Mark.NONE;
        if (side == null) side = Side.LEFT;
        if (ground != 0) ground = BoardColours.opaque(ground);
        ink = BoardColours.opaque(ink);
        accent = BoardColours.opaque(accent);
        subInk = BoardColours.opaque(subInk);
        // No ground: the logo sits on the apps' dark tiles, so its words are light enough to read there.
        int under = ground != 0 && shape != Shape.NONE ? ground : 0xFF20232B;
        ink = BoardColours.legible(ink, under, 4.5);
        subInk = BoardColours.legible(subInk, under, 3.0);
        accent = BoardColours.legible(accent, under, 2.0);
        return this;
    }

    /** What the words sit on: its ground in its shape, else the apps' dark logo tiles. */
    public int under() {
        return ground != 0 && shape != Shape.NONE ? ground : 0xFF20232B;
    }

    public JSONObject json() {
        JSONObject o = new JSONObject();
        try {
            o.put("v", VERSION).put("text", text).put("sub", sub).put("layout", layout.name()).put("font", font.name());
            o.put("colours", new JSONObject().put("ground", ground == 0 ? "" : BoardColours.hex(ground)).put("ink", BoardColours.hex(ink))
                    .put("accent", BoardColours.hex(accent)).put("sub", BoardColours.hex(subInk)));
            o.put("shape", shape.name()).put("mark", mark.name()).put("markSide", side.name()).put("italic", italic).put("outline", outline);
        } catch (JSONException e) {
            // plain values
        }
        return o;
    }

    public String toJson() {
        return json().toString();
    }

    /** The schema Ask AI's logo answer is held to (structured outputs: every property required, no others). */
    public static JSONObject schema() {
        try {
            JSONObject colours = new JSONObject().put("type", "object")
                    .put("properties", new JSONObject()
                            .put("ground", str("The shape's fill #RRGGBB, or \"\" for none (see-through, on a dark tile)"))
                            .put("ink", str("The words #RRGGBB"))
                            .put("accent", str("The mark and any rule #RRGGBB"))
                            .put("sub", str("The second line #RRGGBB")))
                    .put("required", new JSONArray().put("ground").put("ink").put("accent").put("sub"))
                    .put("additionalProperties", false);
            JSONObject p = new JSONObject()
                    .put("v", new JSONObject().put("type", "integer").put("description", "Always 1"))
                    .put("text", str("The main words, at most 14 characters: the channel's name or its initials"))
                    .put("sub", str("A short second line (e.g. SPORTS, HD, MOVIES), at most 18 characters, or \"\""))
                    .put("layout", enumOf(Layout.values()))
                    .put("font", enumOf(BoardSpec.Font.values()))
                    .put("colours", colours)
                    .put("shape", enumOf(Shape.values()))
                    .put("mark", enumOf(Mark.values()))
                    .put("markSide", enumOf(Side.values()))
                    .put("italic", new JSONObject().put("type", "boolean"))
                    .put("outline", new JSONObject().put("type", "boolean"));
            JSONArray req = new JSONArray();
            for (String k : new String[]{"v", "text", "sub", "layout", "font", "colours", "shape", "mark", "markSide", "italic", "outline"}) {
                req.put(k);
            }
            return new JSONObject().put("type", "object").put("properties", p).put("required", req).put("additionalProperties", false);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JSONObject str(String d) throws JSONException {
        return new JSONObject().put("type", "string").put("description", d);
    }

    private static JSONObject enumOf(Enum<?>[] values) throws JSONException {
        JSONArray a = new JSONArray();
        for (Enum<?> e : values) a.put(e.name());
        return new JSONObject().put("type", "string").put("enum", a);
    }
}
