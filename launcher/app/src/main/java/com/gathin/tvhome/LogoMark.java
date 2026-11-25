package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.View;

import com.gathin.broadcast.BrandStyles;
import com.gathin.broadcast.ChannelMark;
import com.gathin.broadcast.CoverLooks;
import com.gathin.broadcast.CoverLooks.Look;

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
 *
 * <p>Every look (CoverLooks.Look: Whitebrook Road's channel looks and Event, the competition's logo, Glass, Mark, Plain,
 * both Broadcasts, Whitebrook Road Sports, the 54 styles, the Scoreboard Lab's five), its grounds and LIVE's foot are
 * drawn by the shared CoverLooks (overlays: with ChannelMark, SportsMark, BrandStyles and LabMarks), which Homefield TV's
 * cover over its own player draws too: the same looks, the same pixels. This view keeps what the launcher feeds it: the
 * frosted picture behind it (FrostPane), the channel's logo filled in under it (LogoFill), Event's clock turning over.
 */
final class LogoMark extends View implements com.gathin.broadcast.LogoExtras.Cover {
    /**
     * Its ground in the looks drawn whole (the wordmark, Event, the styles): its own plate; frosted glass (the picture
     * behind it softened and lightened, FrostPane); or none, over the picture itself with the channel's logo filled in
     * under it (see-through: frosted glass while that logo is learned). CoverLooks' grounds.
     */
    static final int PLATE = CoverLooks.PLATE, FROST = CoverLooks.FROST, CLEAR = CoverLooks.CLEAR, SMOKE = CoverLooks.SMOKE,
            TEMPERED = CoverLooks.TEMPERED;
    /** The grounds in the order they are stepped through: the plate, frosted, tempered and smoked glass, transparent. */
    static final int[] GROUNDS = CoverLooks.GROUNDS;

    /** Every look's drawing, shared with Homefield TV's cover over its own player: the same looks, the same pixels. */
    private final CoverLooks art;
    /** What the look is drawn with now (the tag, LIVE, the competition, the ground, Event's name and clock...). */
    private final CoverLooks.Parts parts = new CoverLooks.Parts();
    private final CoverLooks.Frost frost = new CoverLooks.Frost();
    private Look look = Look.GLASS;
    private BoardBug.StripLook board;
    /** Event's name for the channel ("main event"; "" for none), and the channel's name it was read from. */
    private String subFrom;

    LogoMark(Context c) {
        super(c);
        art = new CoverLooks(c, ScoreCard.outfit(c, 700), ScoreCard.outfit(c, 800));
        parts.frost = frost;
        setContentDescription(NAME);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    void look(Look l) {
        if (l == look) return;
        look = l;
        invalidate();
    }

    void inset(int px) {
        parts.inset = px;
        invalidate();
    }

    /** The channel's picture tag for Broadcast's box ("HD", "UHD"; "" for none). */
    void tag(String t) {
        String n = t == null ? "" : t;
        if (n.equals(parts.tag)) return;
        parts.tag = n;
        if (look.broadcast() || look.channel() || look.own()) invalidate();
    }

    /** The competition being watched: its logo (light, for a dark ground; null for none) and its short name. */
    void competition(android.graphics.Bitmap logo, String name) {
        parts.comp = logo;
        parts.compName = name == null ? "" : name.trim();
        invalidate();
    }

    /** LIVE's bar along the foot while the game is live, or not. */
    void liveBar(boolean on) {
        if (on == parts.liveBar) return;
        parts.liveBar = on;
        invalidate();
    }

    /** The competition's logo beside the look, or not. */
    void compOn(boolean on) {
        if (on == parts.compOn) return;
        parts.compOn = on;
        invalidate();
    }

    /** What is on: "f1" for Formula 1, else football (Matchday's channel name). */
    void sport(String s) {
        String n = s == null ? "" : s;
        if (n.equals(parts.sport)) return;
        parts.sport = n;
        if (look.channel()) invalidate();
    }

    /** What is on, for Whitebrook Road Sports ("premier league", "football", "f1"). */
    void what(String w) {
        String n = w == null || w.isEmpty() ? "football" : w;
        if (n.equals(parts.what)) return;
        parts.what = n;
        if (look.sports()) invalidate();
    }

    /** Event's name for the channel as it is to show ("main event"; "" for none). */
    void sub(String s) {
        String n = s == null ? "" : s;
        if (n.equals(parts.sub)) return;
        parts.sub = n;
        if (look.event()) invalidate();
    }

    /** The channel under the cover, by its name: Event's own name for it (ChannelMark.subOf, read once a channel). */
    void channelName(String name) {
        if (java.util.Objects.equals(name, subFrom)) return;
        subFrom = name;
        sub(ChannelMark.subOf(name));
        marks(marksOn);
    }

    // ---- Event's extras (LogoExtras) ----

    /** The picture and sound marks in the name's box, by the channel's own name (LogoExtras.marks); on or off. */
    private boolean marksOn = true;

    void marks(boolean on) {
        marksOn = on;
        parts.marks = on ? com.gathin.broadcast.LogoExtras.marks(subFrom) : null;
        if (look.event()) invalidate();
    }

    /** The name turned over to the clock (on), or back; its time, the weather's icon (null: none), its temperature. */
    private final CoverLooks.ClockTurn turn = new CoverLooks.ClockTurn();
    static final long TURN_MS = CoverLooks.ClockTurn.TURN_MS;

    @Override public void clock(boolean on, String hm, android.graphics.drawable.Drawable icon, String temp) {
        if (turn.set(parts, on, hm, icon, temp) && look.event()) invalidate();
    }

    @Override public View view() {
        return this;
    }

    /** How far turned over to the clock now (0 the name, 1 the clock). */
    private float turned() {
        return turn.turned();
    }

    /** Whether the look is Event (the one with the name's box the extras go with). */
    boolean eventLook() {
        return look.event();
    }

    /** The name's box as Event last drew it (this view's pixels) into out; false for none. */
    @Override public boolean nameBox(RectF out) {
        return look.event() && art.nameBox(out);
    }

    /** The family's name alone (WHITEBROOK ROAD), or with SPORTS. */
    void nameOnly(boolean on) {
        if (on == parts.nameOnly) return;
        parts.nameOnly = on;
        invalidate();
    }

    /** See-through over the picture, the channel's own logo filled in under it (looks that can be): Transparent, else its plate. */
    void seeThrough(boolean on) {
        ground(on ? CLEAR : PLATE);
    }

    /** Its ground: PLATE, FROST, SMOKE or CLEAR (the looks drawn whole; the others keep their own grounds). */
    void ground(int g) {
        int k = g == FROST || g == CLEAR || g == SMOKE || g == TEMPERED ? g : PLATE;
        if (k == parts.ground) return;
        parts.ground = k;
        if (k == PLATE) frost(null, 0, 0, 0, 0);
        invalidate();
    }

    int ground() {
        return parts.ground;
    }

    /**
     * The frosted glass's picture (FrostPane's, null: none) and where it lies on the screen (l, t, r, b, pixels): faded
     * in over the one before. With none, the plate.
     */
    void frost(Bitmap b, float l, float t, float r, float bt) {
        if (b == null && !frost.has()) return;
        frost.set(b, l, t, r, bt);
        invalidate();
    }

    boolean hasFrost() {
        return frost.has();
    }

    /** Whitebrook Road styles: which of them (0 to BrandStyles.COUNT - 1). */
    void style(int n) {
        int k = BrandStyles.clamp(n);
        if (k == parts.style) return;
        parts.style = k;
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
        return parts.ground == CLEAR;
    }

    boolean wantsPatch() {
        return parts.ground != PLATE && look.own() && getVisibility() == VISIBLE;
    }

    /**
     * Whether the channel's logo is filled in under it (LogoFill): it then stands clear over the picture; else (its
     * shape still being learned, a solid block, no pictures to be had) on its plate.
     */
    void filled(boolean on) {
        if (on == parts.filled) return;
        parts.filled = on;
        invalidate();
    }

    /** Whether the game is live (Broadcast's LIVE). */
    void live(boolean on) {
        if (on == parts.live) return;
        parts.live = on;
        if (look.broadcast() || look.channel()) invalidate();
    }

    /** The scoreboard's look, for Broadcast in its colours. */
    void boardLook(BoardBug.StripLook s) {
        if (s == board) return;
        board = s;
        parts.board = boardOf(s);
        if (look == Look.BROADCAST_BOARD) invalidate();
    }

    /** Pure: a channel's picture tag from its name (ChannelMark.tagOf): "4K" or "UHD", with " HDR" or not, "HD", or none. */
    static String tagOf(String channel) {
        return ChannelMark.tagOf(channel);
    }

    /** Ringed while Move and resize has it. */
    void outline(boolean on) {
        if (on == parts.outlined) return;
        parts.outlined = on;
        invalidate();
    }

    @Override protected void onDraw(Canvas cv) {
        float full = getWidth(), whole = getHeight();
        if (full <= 0 || whole <= 0) return;
        if (frost.has()) getLocationOnScreen(parts.onScreen);
        parts.turned = turned();
        if (art.draw(cv, look, full, whole, parts)) postInvalidateOnAnimation();
    }

    /** Pure: a Lab logo's ground {its colour, its corners' radius in thousandths of its height} (CoverLooks.labGround). */
    static int[] labGround(com.gathin.broadcast.LabMarks.Look l) {
        return CoverLooks.labGround(l);
    }

    /** Pure: how tall a Lab logo is drawn in a box bw by bh (CoverLooks.labHeight). */
    static float labHeight(float natural, float bw, float bh) {
        return CoverLooks.labHeight(natural, bw, bh);
    }

    /** Pure: the scoreboard's colours as the shared covers take them. */
    static CoverLooks.Board boardOf(BoardBug.StripLook s) {
        return s == null ? null : new CoverLooks.Board(s.ground, s.block, s.ink, s.light);
    }

    /** What Broadcast says: the family's own name over "sports". */
    static final String NAME = CoverLooks.NAME, WORD = CoverLooks.WORD;
}
