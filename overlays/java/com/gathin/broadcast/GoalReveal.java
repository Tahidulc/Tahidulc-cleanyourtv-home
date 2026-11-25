package com.gathin.broadcast;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/**
 * The new goal animations (GoalLooks' Club animations and Generic), drawn inside the scoreboard over the scorers' part of
 * it (their name, the score and on to the board's far end, the competition's block and the clock left as they are; or
 * the whole board where a part makes no sense), in five stages:
 * <ol>
 *   <li>a bright sweep of the club's own two colours runs along the strip from the scorers' name, covering name and
 *   score;</li>
 *   <li>their crest appears edge-on in the middle and turns round to face us, then slides beside their letters as the
 *   sweep moves off and the strip goes dark again (a dark of the club's colour);</li>
 *   <li>what the club is known by comes in on the rest of the strip: a silhouette (an emoji drawn in the device's own
 *   emoji font and tinted, or one of GoalShapes), or a phrase, letter by letter;</li>
 *   <li>it turns into GOAL!: the motif goes as the letters come in spread wide and pull together over a panel lit in the
 *   club's colours, a small burst, held;</li>
 *   <li>the letters dim, the sweep runs along again, and as it moves off the board is back with the new score (the board
 *   draws the old one under the animation until the sweep covers it: newScore).</li>
 * </ol>
 * The timeline is data (the constants below, pure functions of t for the tests); one instance per board, its paints,
 * shaders, paths, the motif's picture and every word's measure made once per goal (prepare), nothing made per frame.
 * The goal's sound and when it plays are the boards' as ever (Sfx, the goal shown when it reaches the picture).
 */
public final class GoalReveal {
    // ---- the timeline, in ms from the goal ------------------------------------------------------------------------------

    /** Stage 1: the sweep in from the scorers' name, across it all by SWEEP_IN. */
    public static final long SWEEP_IN = 520;
    /** Stage 2: the crest edge-on from CREST_FROM, a line growing to its height by CREST_EDGE, then turning to face us by CREST_FACED. */
    public static final long CREST_FROM = 300, CREST_EDGE = 520, CREST_FACED = 1100;
    /** The sweep moving off as the strip goes dark again, the crest sliding beside the letters meanwhile. */
    public static final long DARK_FROM = 1250, DARK_MS = 460;
    /** Stage 3: the motif coming in. */
    public static final long MOTIF_FROM = 1600, MOTIF_MS = 650;
    /** Stage 4: the motif going, GOAL!'s letters coming in spread and pulling together, its panel lighting, the burst. */
    public static final long MORPH_FROM = 2750, MORPH_MS = 300, LETTERS_FROM = 2900, LETTERS_MS = 440, PANEL_MS = 380,
            BURST_FROM = 3300, BURST_MS = 450;
    /** Stage 5: the letters dimming, the sweep along again (the board's new score under it from SCORE_AT), then off. */
    public static final long DIM_FROM = 3750, DIM_MS = 200, SWEEP2_FROM = 3950, SWEEP2_MS = 450, SCORE_AT = SWEEP2_FROM + SWEEP2_MS,
            OUT_FROM = 4450, OUT_MS = 450;
    /** The whole animation. */
    public static final long TOTAL_MS = OUT_FROM + OUT_MS;

    /** The stages, and where each begins. */
    public static final int STAGE_SWEEP = 0, STAGE_CREST = 1, STAGE_MOTIF = 2, STAGE_GOAL = 3, STAGE_OUT = 4, STAGE_DONE = 5;
    private static final long[] STAGES = {0, CREST_FROM, MOTIF_FROM, MORPH_FROM, DIM_FROM, TOTAL_MS};

    /** Pure: the stage t ms into the goal (STAGE_DONE after it, and before it). */
    public static int stage(long t) {
        if (t < 0 || t >= TOTAL_MS) return STAGE_DONE;
        int s = 0;
        for (int i = 0; i < STAGES.length; i++) if (t >= STAGES[i]) s = i;
        return s;
    }

    /** Pure: when each stage begins, in ms. */
    public static long stageAt(int stage) {
        return STAGES[Math.max(0, Math.min(STAGES.length - 1, stage))];
    }

    /** Pure: whether the board shows the new score t ms in: not under the animation until the second sweep covers it. */
    public static boolean newScore(long t) {
        return t < 0 || t >= SCORE_AT;
    }

    /** Pure: whether the dark strip is down (the first sweep has covered it all, the second not yet). */
    public static boolean dark(long t) {
        return t >= SWEEP_IN && t < SCORE_AT;
    }

    /** Pure: where the sweep starts, as a share of the area from the scorers' end, t ms in (0 to 1). */
    public static float bandFrom(long t) {
        if (t >= DARK_FROM && t < DARK_FROM + DARK_MS) return inOut(span(t, DARK_FROM, DARK_FROM + DARK_MS));
        if (t >= OUT_FROM && t < OUT_FROM + OUT_MS) return inOut(span(t, OUT_FROM, OUT_FROM + OUT_MS));
        return 0f;
    }

    /** Pure: where the sweep ends, as a share of the area from the scorers' end (bandFrom to this is covered; none when equal). */
    public static float bandTo(long t) {
        if (t < 0) return 0f;
        if (t < SWEEP_IN) return out(span(t, 0, SWEEP_IN));
        if (t < DARK_FROM + DARK_MS) return 1f;
        if (t < SWEEP2_FROM) return 0f;
        if (t < SCORE_AT) return out(span(t, SWEEP2_FROM, SCORE_AT));
        if (t < OUT_FROM + OUT_MS) return 1f;
        return 0f;
    }

    /** Pure: how far the edge-on crest's line has grown (0 to 1). */
    public static float crestEdge(long t) {
        return span(t, CREST_FROM, CREST_EDGE);
    }

    /** Pure: the crest's turn in degrees, 90 edge-on to 0 facing us (past it a little, and back). */
    public static float crestTurn(long t) {
        if (t < CREST_EDGE) return 90f;
        return 90f * (1f - back(span(t, CREST_EDGE, CREST_FACED)));
    }

    /** Pure: the crest on its way from the middle (0) to beside the letters (1). */
    public static float crestSlide(long t) {
        return inOut(span(t, DARK_FROM, DARK_FROM + DARK_MS - 40));
    }

    /** Pure: how far the motif has come in (0 to 1). */
    public static float motifIn(long t) {
        return span(t, MOTIF_FROM, MOTIF_FROM + MOTIF_MS);
    }

    /** Pure: how far the motif has gone (0 to 1, turning into GOAL!). */
    public static float motifOut(long t) {
        return span(t, MORPH_FROM, MORPH_FROM + MORPH_MS);
    }

    /** Pure: how far GOAL!'s letters have pulled together (0 spread wide, 1 together). */
    public static float letters(long t) {
        return span(t, LETTERS_FROM, LETTERS_FROM + LETTERS_MS);
    }

    /** Pure: the spread between GOAL!'s letters at a stage of their pull (1 wide; past 0 a little, then 0). */
    public static float spread(float pull) {
        return 1f - back(pull);
    }

    /** Pure: how far GOAL!'s panel is lit, out from its middle (0 to 1). */
    public static float panel(long t) {
        return out(span(t, LETTERS_FROM, LETTERS_FROM + PANEL_MS));
    }

    /** Pure: how far the burst is (0 to 1), -1 when it is not on. */
    public static float burst(long t) {
        return t >= BURST_FROM && t < BURST_FROM + BURST_MS ? span(t, BURST_FROM, BURST_FROM + BURST_MS) : -1f;
    }

    /** Pure: the scorers' letters' strength on the dark strip: full, then dimming before the second sweep. */
    public static float codeAlpha(long t) {
        return 1f - 0.65f * inOut(span(t, DIM_FROM, DIM_FROM + DIM_MS));
    }

    /** Pure: when the family's swish (on its own switch) starts, so its loudest moment lands as the new score is uncovered. */
    public static long swishAt() {
        return Math.max(0, OUT_FROM + 150 - Sfx.SCORE_PEAK_MS);
    }

    static float span(long t, long a, long b) {
        if (t <= a) return 0f;
        if (t >= b) return 1f;
        return (t - a) / (float) (b - a);
    }

    static float out(float x) {
        float u = 1f - Math.max(0f, Math.min(1f, x));
        return 1f - u * u * u;
    }

    static float inOut(float x) {
        float t = Math.max(0f, Math.min(1f, x));
        return t * t * (3f - 2f * t);
    }

    /** Ease out past the end a little and back (a pull that lands). */
    static float back(float x) {
        float t = Math.max(0f, Math.min(1f, x)) - 1f;
        float c1 = 1.5f, c3 = c1 + 1f;
        return 1f + c3 * t * t * t + c1 * t * t;
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    private static final String[] GOAL = {"G", "O", "A", "L", "!"};
    private static final int WHITE = 0xFFFFFFFF;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG),
            image = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Matrix m = new Matrix(), edgeAt = new Matrix();
    private final float[] src = new float[8], dst = new float[8];
    /** The moving edge's light: a soft white streak, moved along by its matrix. */
    private final LinearGradient edgeGlow = new LinearGradient(0, 0, 1, 0, new int[]{0x00FFFFFF, 0x99FFFFFF, 0x00FFFFFF}, null,
            Shader.TileMode.CLAMP);
    private final Path shape = new Path();
    private final Paint.FontMetrics fm = new Paint.FontMetrics();

    // What it was made for (prepare), and what it made.
    private ClubGoals.Club club;
    private String code = "";
    private Bitmap crest, emoji;
    private boolean right;
    private float w, h;
    private Typeface face;
    private LinearGradient sweep, lit, panelShade;
    private float stripW, codeC, codeSize, crestS, crestC, crestBig, regionL, regionR, regionC, goalSize, panelL;
    /**
     * Where the crest settles and the motif and GOAL! stand, up and down; the top of GOAL!'s panel; the room's height for
     * the motif. A strip has them all across its middle; a board nearer square (tall: a competition's two-row look) has the
     * crest settling at the top and the motif and GOAL! across its lower half.
     */
    private float crestY, regionY, panelTop, roomH;
    private boolean tall;
    private final float[] goalX = new float[GOAL.length];
    private float goalHalf;
    private boolean showCode;
    private char kind;
    private float shapeW, shapeH, emojiS, phraseSize;
    private String[] phrase = new String[0];
    private float[] phraseX = new float[0];
    private int stripColour, shade;
    private String initial = "";

    /**
     * Made ready for a goal: the club's animation (ClubGoals: its own, or the generic), the scorers' letters as the board
     * shows them, their crest (null: a disc in their colours with their initial), the scorers' end on the right (an away
     * side drawn from the right), the area's size and the board's typeface. Kept when called again with the same; the
     * paths, shaders, the motif's picture and the words' measures are made here, never per frame.
     */
    public void prepare(ClubGoals.Club c, String letters, Bitmap crestImage, boolean fromRight, float width, float height,
            Typeface typeface) {
        String l = letters == null ? "" : letters;
        if (c == club && l.equals(code) && crestImage == crest && fromRight == right && Math.abs(width - w) < 0.5f
                && Math.abs(height - h) < 0.5f && typeface == face) return;
        club = c;
        code = l;
        crest = crestImage;
        right = fromRight;
        w = Math.max(1f, width);
        h = Math.max(1f, height);
        face = typeface;
        text.setTypeface(face);
        text.setTextAlign(Paint.Align.CENTER);
        initial = l.isEmpty() ? "" : l.substring(0, 1);
        // the sweep, from the scorers' end across: the club's two colours; GOAL!'s panel lit in them
        float x0 = right ? w : 0f, x1 = right ? 0f : w;
        sweep = new LinearGradient(x0, 0, x1, 0, new int[]{c.sweep0, BoardColours.mix(c.sweep0, c.sweep1, 0.5f), c.sweep1}, null,
                Shader.TileMode.CLAMP);
        lit = new LinearGradient(0, 0, 0, h, new int[]{0x33FFFFFF, 0x00FFFFFF, 0x00000000, 0x33000000},
                new float[]{0f, 0.45f, 0.6f, 1f}, Shader.TileMode.CLAMP);
        stripColour = BoardColours.contrast(c.sweep0, c.ground) >= BoardColours.contrast(c.sweep1, c.ground) ? c.sweep0 : c.sweep1;
        shade = BoardColours.mix(c.motifInk, c.ground, 0.55f);
        layout();
        float pl = X(right ? w : panelL), pr = X(right ? panelL : w);
        panelShade = new LinearGradient(right ? pr : pl, 0, right ? pl : pr, 0, c.sweep0, c.sweep1, Shader.TileMode.CLAMP);
        motif();
    }

    /** Where everything sits, measured from the scorers' end (X() turns it round for an away side drawn from the right). */
    private void layout() {
        stripW = h * 0.09f;
        codeSize = h * 0.46f;
        tall = w < h * 2.6f;
        if (tall) {
            tallLayout();
            return;
        }
        crestY = regionY = h / 2f;
        panelTop = 0f;
        roomH = h;
        text.setLetterSpacing(0.02f);
        text.setTextSize(codeSize);
        float codeW = code.isEmpty() ? 0f : text.measureText(code);
        crestS = h * 0.74f;
        crestBig = h * 0.86f;
        showCode = codeW > 0;
        float codeL = stripW + h * 0.2f;
        float crestL = showCode ? codeL + codeW + h * 0.2f : stripW + h * 0.2f;
        regionL = crestL + crestS + h * 0.24f;
        regionR = w - h * 0.24f;
        if (showCode && regionR - regionL < h * 1.6f) {
            // too narrow for the letters as well: the crest stands for them
            showCode = false;
            crestL = stripW + h * 0.2f;
            regionL = crestL + crestS + h * 0.24f;
        }
        codeC = codeL + codeW / 2f;
        crestC = crestL + crestS / 2f;
        regionC = (regionL + Math.max(regionL + 1, regionR)) / 2f;
        float regionW = Math.max(1f, regionR - regionL);
        // GOAL!: each letter's middle from the word's, as big as the room allows
        text.setLetterSpacing(0.06f);
        text.setTextSize(100f);
        float total = text.measureText("GOAL!");
        goalSize = Math.min(h * 0.6f, regionW * 0.86f / Math.max(1f, total) * 100f);
        text.setTextSize(goalSize);
        float at = -text.measureText("GOAL!") / 2f;
        for (int i = 0; i < GOAL.length; i++) {
            float lw = text.measureText(GOAL[i]);
            goalX[i] = at + lw / 2f;
            at += lw;
        }
        goalHalf = -goalX[0] + goalSize * 0.4f;
        panelL = regionL - h * 0.12f;
        text.setLetterSpacing(0f);
    }

    /** A board nearer square: no letters (the board's own show round it), the crest up top, the rest across the lower half. */
    private void tallLayout() {
        showCode = false;
        stripW = Math.min(stripW, w * 0.05f);
        crestBig = Math.min(h * 0.62f, w * 0.5f);
        crestS = Math.min(h * 0.42f, w * 0.4f);
        crestC = w / 2f;
        crestY = h * 0.28f;
        roomH = h * 0.44f;
        regionY = h * 0.75f;
        regionL = stripW + w * 0.06f;
        regionR = w - w * 0.06f;
        regionC = (regionL + regionR) / 2f;
        panelL = 0f;
        panelTop = h * 0.53f;
        float regionW = Math.max(1f, regionR - regionL);
        text.setLetterSpacing(0.06f);
        text.setTextSize(100f);
        float total = text.measureText("GOAL!");
        goalSize = Math.min(roomH * 0.8f, regionW * 0.86f / Math.max(1f, total) * 100f);
        text.setTextSize(goalSize);
        float at = -text.measureText("GOAL!") / 2f;
        for (int i = 0; i < GOAL.length; i++) {
            float lw = text.measureText(GOAL[i]);
            goalX[i] = at + lw / 2f;
            at += lw;
        }
        goalHalf = -goalX[0] + goalSize * 0.4f;
        text.setLetterSpacing(0f);
    }

    /** The motif made for its room: a shape's path, an emoji's tinted picture, a phrase's letters measured. */
    private void motif() {
        ClubGoals.Club c = club;
        float regionW = Math.max(1f, regionR - regionL);
        kind = c.kind();
        String what = c.what();
        if (kind == 'e' && !emojiPicture(what, Math.round(Math.min(roomH * 0.9f, regionW * 0.9f)))) kind = 's';
        if (kind == 'p') {
            text.setLetterSpacing(0.04f);
            text.setTextSize(100f);
            float total = text.measureText(what);
            phraseSize = Math.max(roomH * 0.24f, Math.min(roomH * 0.5f, regionW * 0.92f / Math.max(1f, total) * 100f));
            text.setTextSize(phraseSize);
            int n = what.codePointCount(0, what.length());
            phrase = new String[n];
            phraseX = new float[n];
            float at = -text.measureText(what) / 2f;
            for (int i = 0, k = 0; i < what.length(); k++) {
                int cp = what.codePointAt(i);
                String s = new String(Character.toChars(cp));
                float lw = text.measureText(s);
                phrase[k] = s;
                phraseX[k] = at + lw / 2f;
                at += lw;
                i += Character.charCount(cp);
            }
            text.setLetterSpacing(0f);
        } else if (kind == 's') {
            String name = c.kind() == 's' ? what : "ball";
            float[] size = GoalShapes.fit(GoalShapes.has(name) ? name : "ball", shape, regionW * 0.9f, roomH * 0.88f, right);
            shapeW = size[0];
            shapeH = size[1];
        }
    }

    /**
     * An emoji as a silhouette in the club's colour: drawn once in the device's emoji font (its own colours), then each
     * pixel toned from the motif's colour (its light parts) to a deeper one of it (its dark parts), so it reads as one
     * picture in the club's colour with its features kept. False when the font has none for it.
     */
    private boolean emojiPicture(String codes, int size) {
        StringBuilder sb = new StringBuilder();
        for (String part : codes.split("-")) {
            try {
                sb.appendCodePoint(Integer.parseInt(part, 16));
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        int first = sb.codePointAt(0);
        if (first < 0x1F000 && sb.indexOf("\uFE0F") < 0) sb.append('\uFE0F');
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(size * 0.8f);
        p.setTextAlign(Paint.Align.CENTER);
        String s = sb.toString();
        if (!p.hasGlyph(s)) {
            // an older emoji font without the sequence (the black cat): its first picture alone
            s = new String(Character.toChars(first)) + (first < 0x1F000 ? "\uFE0F" : "");
            if (!p.hasGlyph(s)) return false;
        }
        size = Math.max(8, size);
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint.FontMetrics pm = p.getFontMetrics();
        cv.drawText(s, size / 2f, size / 2f - (pm.ascent + pm.descent) / 2f, p);
        int[] px = new int[size * size];
        b.getPixels(px, 0, size, 0, 0, size, size);
        int any = 0, ink = club.motifInk, deep = shade;
        for (int i = 0; i < px.length; i++) {
            int a = px[i] >>> 24;
            if (a == 0) continue;
            any++;
            int rr = (px[i] >> 16) & 0xFF, gg = (px[i] >> 8) & 0xFF, bb = px[i] & 0xFF;
            float lum = (0.299f * rr + 0.587f * gg + 0.114f * bb) / 255f;
            float k = inOut((lum - 0.1f) / 0.6f);
            px[i] = (a << 24) | (BoardColours.mix(deep, ink, k) & 0xFFFFFF);
        }
        if (any < size) {
            b.recycle();
            return false;
        }
        b.setPixels(px, 0, size, 0, 0, size, size);
        if (emoji != null && emoji != b) emoji.recycle();
        emoji = b;
        emojiS = size;
        return true;
    }

    /** x measured from the scorers' end, on the area. */
    private float X(float u) {
        return right ? w - u : u;
    }

    /** Drawn into the area at (x, y), t ms into the goal (prepare first); nothing outside it. */
    public void draw(Canvas cv, float x, float y, long t) {
        if (club == null || t < 0 || t >= TOTAL_MS) return;
        cv.save();
        cv.translate(x, y);
        cv.clipRect(0, 0, w, h);
        boolean settled = t >= DARK_FROM + DARK_MS;
        if (dark(t)) {
            fill.setShader(null);
            fill.setColor(club.ground);
            cv.drawRect(0, 0, w, h, fill);
            // the club's colour at its end, as the board's own strip
            fill.setColor(stripColour);
            r.set(X(right ? stripW : 0), 0, X(right ? 0 : stripW), h);
            cv.drawRect(r, fill);
            if (showCode && t >= DARK_FROM) {
                text.setTextSize(codeSize);
                text.setLetterSpacing(0.02f);
                text.setColor(WHITE);
                text.setAlpha(Math.round(255 * codeAlpha(t)));
                words(cv, code, X(codeC), h / 2f);
                text.setAlpha(255);
            }
            if (settled) crestAt(cv, t);
            goalPanel(cv, t);
            motifAt(cv, t);
            goalWord(cv, t);
        }
        // the sweep, and the light on its moving edge
        float from = bandFrom(t), to = bandTo(t);
        if (to - from > 0.001f) {
            float a = from * w, b = to * w;
            r.set(right ? w - b : a, 0, right ? w - a : b, h);
            fill.setShader(sweep);
            cv.drawRect(r, fill);
            fill.setShader(lit);
            cv.drawRect(r, fill);
            fill.setShader(null);
            boolean leaving = from > 0.001f;
            boolean moving = leaving || to < 0.999f;
            if (moving) {
                float edge = X(leaving ? a : b), gw = h * 1.1f;
                edgeAt.setScale(gw, 1f);
                edgeAt.postTranslate(edge - gw / 2f, 0f);
                edgeGlow.setLocalMatrix(edgeAt);
                line.setShader(edgeGlow);
                cv.drawRect(edge - gw / 2f, 0, edge + gw / 2f, h, line);
                line.setShader(null);
            }
        }
        if (!settled && t >= CREST_FROM) crestAt(cv, t);
        cv.restore();
    }

    /** The crest: edge-on as a line, turning to face us in the middle, sliding beside the letters, then still. */
    private void crestAt(Canvas cv, long t) {
        float slide = crestSlide(t);
        float cx = X(w / 2f + (crestC - w / 2f) * slide);
        float s = crestBig + (crestS - crestBig) * slide, cy = h / 2f + (crestY - h / 2f) * slide;
        float turn = crestTurn(t);
        double rad = Math.toRadians(turn);
        float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);
        if (Math.abs(cos) < 0.35f && t < CREST_FACED) {
            // its edge, a line growing to the crest's height, in GOAL!'s ink (it reads on the sweep)
            float grow = crestEdge(t), lh = s * 1.1f * grow, lw = Math.max(1.5f, h * 0.05f);
            line.setShader(null);
            line.setColor(club.ink);
            line.setAlpha(Math.round(255 * Math.min(1f, (0.35f - Math.abs(cos)) / 0.2f)));
            cv.drawRect(cx - lw / 2f, cy - lh / 2f, cx + lw / 2f, cy + lh / 2f, line);
            line.setAlpha(255);
        }
        if (Math.abs(cos) < 0.04f) return;
        float half = s / 2f, d = s * 3f;
        // the four corners turned about the upright through its middle, seen in perspective
        for (int i = 0; i < 4; i++) {
            float dx = i == 0 || i == 3 ? -half : half, dy = i < 2 ? -half : half;
            float z = dx * sin, k = d / (d + z);
            src[2 * i] = cx + dx;
            src[2 * i + 1] = cy + dy;
            dst[2 * i] = cx + dx * cos * k;
            dst[2 * i + 1] = cy + dy * k;
        }
        if (!m.setPolyToPoly(src, 0, dst, 0, 4)) return;
        cv.save();
        cv.concat(m);
        if (crest != null) {
            float k = s / Math.max(crest.getWidth(), crest.getHeight());
            float bw = crest.getWidth() * k, bh = crest.getHeight() * k;
            r.set(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f);
            cv.drawBitmap(crest, null, r, image);
        } else {
            // a disc in the club's colours with its initial
            fill.setShader(null);
            fill.setColor(club.sweep0);
            cv.drawCircle(cx, cy, half * 0.94f, fill);
            line.setShader(null);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(half * 0.12f);
            line.setColor(club.sweep1 == club.sweep0 ? WHITE : club.sweep1);
            cv.drawCircle(cx, cy, half * 0.86f, line);
            line.setStyle(Paint.Style.FILL);
            text.setTextSize(s * 0.5f);
            text.setLetterSpacing(0f);
            text.setColor(club.ink);
            words(cv, initial, cx, cy);
        }
        cv.restore();
    }

    /** GOAL!'s panel, lit in the club's colours out from its middle; its flash at the burst. */
    private void goalPanel(Canvas cv, long t) {
        float p = panel(t);
        if (p <= 0f) return;
        float l = X(panelL), rr = X(w), mid = (l + rr) / 2f, half = Math.abs(rr - l) / 2f * p;
        r.set(mid - half, panelTop, mid + half, h);
        fill.setShader(panelShade);
        cv.drawRect(r, fill);
        fill.setShader(lit);
        cv.drawRect(r, fill);
        fill.setShader(null);
        float b = burst(t);
        if (b >= 0f) {
            fill.setColor(WHITE);
            fill.setAlpha(Math.round(90 * (1f - b)));
            cv.drawRect(r, fill);
            fill.setAlpha(255);
        }
    }

    /** The motif: coming in from the far side, held with a little life, then going (bigger, fading) into GOAL!. */
    private void motifAt(Canvas cv, long t) {
        float in = motifIn(t), gone = motifOut(t);
        if (in <= 0f || gone >= 1f) return;
        float regionW = Math.max(1f, regionR - regionL), cx = X(regionC), cy = regionY;
        float ease = out(in), alpha = Math.min(1f, in * 1.6f) * (1f - gone) * (1f - gone);
        float away = (right ? -1f : 1f) * (1f - ease) * regionW * 0.45f;
        float held = t > MOTIF_FROM + MOTIF_MS ? (float) Math.sin((t - MOTIF_FROM - MOTIF_MS) / 160.0) * 0.025f : 0f;
        float scale = (0.86f + 0.14f * back(in)) * (1f + 0.22f * gone) + held;
        int colour = BoardColours.mix(club.motifInk, WHITE, gone);
        if (kind == 'p') {
            text.setTextSize(phraseSize);
            text.setLetterSpacing(0f);
            int n = phrase.length;
            for (int i = 0; i < n; i++) {
                // letter by letter, in from the far side
                float q = Math.max(0f, Math.min(1f, (in - i * 0.5f / Math.max(1, n)) / 0.5f));
                if (q <= 0f) continue;
                float lx = cx + phraseX[i] * scale + (right ? -1f : 1f) * (1f - out(q)) * roomH * 0.7f;
                text.setColor(colour);
                text.setAlpha(Math.round(255 * Math.min(1f, q * 1.5f) * (1f - gone) * (1f - gone)));
                words(cv, phrase[i], lx, cy);
            }
            text.setAlpha(255);
            return;
        }
        cv.save();
        cv.translate(cx + away, cy);
        cv.scale(scale, scale);
        if (kind == 'e' && emoji != null) {
            image.setAlpha(Math.round(255 * alpha));
            float half = emojiS / 2f;
            r.set(-half, -half, half, half);
            if (right) cv.scale(-1f, 1f);
            cv.drawBitmap(emoji, null, r, image);
            image.setAlpha(255);
        } else {
            // the football rolls in; the other shapes slide
            if (club.kind() != 's' || "ball".equals(club.what())) cv.rotate((float) Math.toDegrees(away / Math.max(1f, shapeH / 2f)));
            fill.setShader(null);
            fill.setColor(colour);
            fill.setAlpha(Math.round(255 * alpha));
            cv.drawPath(shape, fill);
            fill.setAlpha(255);
        }
        cv.restore();
    }

    /** GOAL!: its letters spread wide pulling together, the burst round it, then held. */
    private void goalWord(Canvas cv, long t) {
        float pull = letters(t);
        if (pull <= 0f) return;
        float spread = spread(pull), size = 0.72f + 0.28f * out(pull), b = burst(t);
        float pop = b >= 0f ? 1f + 0.1f * (float) Math.sin(Math.PI * b) : 1f;
        float cx = X(regionC), cy = regionY;
        text.setTextSize(goalSize * size * pop);
        text.setLetterSpacing(0f);
        text.setColor(club.ink);
        text.setAlpha(Math.round(255 * Math.min(1f, pull / 0.3f)));
        for (int i = 0; i < GOAL.length; i++) {
            float lx = cx + goalX[i] * size * pop + spread * (i - 2f) * goalSize * 0.9f;
            words(cv, GOAL[i], lx, cy);
        }
        text.setAlpha(255);
        if (b >= 0f) {
            // the burst: short rays out from round the word, fading
            line.setShader(null);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setStrokeWidth(Math.max(1.5f, roomH * 0.05f));
            line.setColor(club.ink);
            line.setAlpha(Math.round(230 * (1f - b)));
            float rx = goalHalf + roomH * 0.08f, ry = roomH * 0.36f, k0 = 1f + 0.25f * out(b), len = roomH * 0.2f * (1f - b * 0.6f);
            for (int i = 0; i < 10; i++) {
                double a = Math.PI * 2 * i / 10 + Math.PI / 10;
                float ux = (float) Math.cos(a), uy = (float) Math.sin(a);
                float x0 = cx + ux * rx * k0, y0 = cy + uy * ry * k0;
                cv.drawLine(x0, y0, x0 + ux * len, y0 + uy * len, line);
            }
            line.setAlpha(255);
            line.setStyle(Paint.Style.FILL);
        }
    }

    private void words(Canvas cv, String s, float cx, float cy) {
        text.getFontMetrics(fm);
        cv.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2f, text);
    }

    /** Let go of the motif's picture (the board is gone). */
    public void release() {
        if (emoji != null) emoji.recycle();
        emoji = null;
        club = null;
    }
}
