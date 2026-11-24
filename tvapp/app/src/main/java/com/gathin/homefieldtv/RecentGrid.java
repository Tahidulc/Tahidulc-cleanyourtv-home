package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.prefs.ChannelEdits;

import java.util.ArrayList;
import java.util.List;

/**
 * The live overlay's recently watched channels, as the family chose them (the mock-up "C with programme pictures": "I
 * want the recently watched channels section to be redesigned and show 10 channels underneath the information panel"):
 * under the info card (InfoCard.STACK_Y), a line saying what it is ("Recently watched", "10 channels", and at the right
 * "Down past the second row for settings"), then the channels last watched on this TV, newest first, never the one
 * playing, up to ten, in two rows of five cards (1824 wide from x 48, 14 apart, 120 high, rounded 20), and under them
 * what the keys do ("OK to watch · Left and Right along a row · Down to the second row"). A channel joins them once it
 * has played 10 seconds (Library.recent).
 *
 * <p>Each card is the programme on now on its channel: the programme's own picture filling the card (Pics.FILL: its
 * faces whole; asked for once the overlay rests), else the logo's own colour (while the picture comes, or where the
 * programme has only the channel's own picture: the logo is at the top left already); a dark shade from the left (as
 * deep as 94 %, 84 % at 46 % across, 30 % at 74 %, nearly nothing at the right) so the words read; the channel's logo on
 * its colour at the top left (ChannelLook, 84 x 56, rounded 12); beside it the family's name for the channel (15,
 * quiet) and the programme's title (19, semibold); what is left of it at the foot's left (15) and how far through it is
 * along the foot (5 px, inside the card's corners). The remote's card lifts toward you (Lift07, to 1.04) with a 4 px
 * white ring and a soft shadow: no white fill, so its picture stays seen.
 *
 * <pre>
 *   set(recent, at)       the channels (the first MAX of them), the remote on card at
 *   key(code) / ok()      Left and Right along a row, stopping at its ends; Down from the first row to the second (the
 *                         same column, else its last card), Up back; Up off the first row and Down off the last go to
 *                         the host (edge, with the middle of the remote's column); OK: the host tunes to the channel
 *   focusNear(x)          the remote onto the last row's card nearest design x (Up from the settings' pills)
 * </pre>
 *
 * Nothing is made a frame: kept paints and the shade's gradient (a unit box stretched across), words cut from kept
 * widths (TextFit), the channels' names kept from set() and the time left kept for each card by the minute; a card's
 * picture is read once at the card's size and drawn through a kept shader (Shaders), so the Lite tier's FILL is Pics'.
 */
final class RecentGrid extends View {
    static final float X = 48f, W = 1824f, GAP = 14f, TOP = 734f, CARD_H = 120f, RADIUS = 20f;
    static final int COLS = 5, MAX = 10;
    static final float CARD_W = (W - (COLS - 1) * GAP) / COLS;
    /** The line over the cards: its middle; the words' left and right ends across the overlay (design px). */
    static final float HEAD_Y = 703f, TEXT_X = 82f, TEXT_R = 1838f;
    /** The keys' line: its middle this far under the cards' foot. */
    static final float HINT_BELOW = 45f;
    /**
     * In a card (design px from its top left): the logo's tile, the words beside it (the name's and the title's
     * baselines, cut to WORDS_W), the time left's baseline, the progress line's height along the foot.
     */
    static final float PAD = 14f, LOGO_W = 84f, LOGO_H = 56f, LOGO_R = 12f, WORDS_X = PAD + LOGO_W + 14f, WORDS_W = 170f,
            NAME_Y = 34f, TITLE_Y = 60f, LEFT_Y = 100f, LINE_H = 5f;
    /** The remote's card: how much bigger, the white ring just outside its edge, its shadow (0 16 40 at 60 %). */
    static final float LIFT = 1.04f, RING = 4f;
    static final float[] SH_LIFT = {16f, 40f, 0f, 153f};
    /** The words' shade: its colour, its strength across the card at SHADE_AT. */
    static final int SHADE = 0x0A0C12;
    static final int[] SHADE_ALPHA = {0xF0, 0xD6, 0x4D, 0x0A};
    static final float[] SHADE_AT = {0f, 0.46f, 0.74f, 1f};
    /** A card with no colour of its own (Behind logos: None): the slate. */
    static final int EMPTY = 0xFF1E2430;
    static final String HEAD = "Recently watched", DOWN_TWO = "Down past the second row for settings", DOWN_ONE = "Down for settings",
            KEYS_TWO = "OK to watch · Left and Right along a row · Down to the second row",
            KEYS_ONE = "OK to watch · Left and Right along the row · Down for settings",
            NO_GUIDE = "No programme information", NOTHING_NOW = "Nothing in the guide now";

    interface Host {
        void tune(Channel c);

        /** Up or Down off the grid (Ui.UP, Ui.DOWN) from the column whose middle is design x: true when the screen took it. */
        boolean edge(int dir, float x);
    }

    private final Host host;
    private final List<Channel> chans = new ArrayList<>(MAX);
    private final String[] names = new String[MAX], lefts = new String[MAX];
    private final int[] leftFor = new int[MAX];
    private int focus;
    private String count = "";
    private final Lift07 lift = new Lift07();
    private final Shaders shaders = new Shaders();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), shade = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), done = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chevron = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint headP = Type.paint(24f, Type.BOLD, Look07.L1), countP = Type.paint(20f, Type.REGULAR, 0x99FFFFFF);
    private final TextPaint downP = Type.paint(20f, Type.REGULAR, 0xB3FFFFFF), keysP = Type.paint(21f, Type.REGULAR, 0x99FFFFFF);
    private final TextPaint nameP = Type.paint(15f, Type.REGULAR, 0xADFFFFFF), titleP = Type.paint(19f, Type.SEMIBOLD, Look07.L1);
    private final TextPaint leftP = Type.clock(15f, Type.REGULAR, 0xBFFFFFFF);
    private final TextFit nameFit = new TextFit(nameP, 32), titleFit = new TextFit(titleP, 32);
    private final RectF box = new RectF(), cell = new RectF();
    private final Runnable redraw = this::invalidate;

    RecentGrid(Context c, Host host) {
        super(c);
        this.host = host;
        int[] colours = new int[SHADE_ALPHA.length];
        for (int i = 0; i < colours.length; i++) colours[i] = SHADE_ALPHA[i] << 24 | SHADE;
        // A unit box stretched across each card (made once: nothing made a frame).
        shade.setShader(new LinearGradient(0, 0, 1, 0, colours, SHADE_AT, Shader.TileMode.CLAMP));
        track.setColor(0x38FFFFFF);
        done.setColor(Look07.L1);
        ring.setStyle(Paint.Style.STROKE);
        chevron.setStyle(Paint.Style.STROKE);
        chevron.setStrokeCap(Paint.Cap.ROUND);
        chevron.setStrokeJoin(Paint.Join.ROUND);
        Shadows.prepare(SH_LIFT, RADIUS);
    }

    /** The channels (the first MAX; the host leaves the one playing out), the remote on card at. */
    void set(List<Channel> channels, int at) {
        chans.clear();
        if (channels != null) for (int i = 0; i < channels.size() && chans.size() < MAX; i++) chans.add(channels.get(i));
        for (int i = 0; i < MAX; i++) {
            names[i] = i < chans.size() ? ChannelEdits.name(chans.get(i)) : "";
            leftFor[i] = -1;
        }
        int n = chans.size();
        count = n == 1 ? "1 channel" : n + " channels";
        focus = Math.max(0, Math.min(at, n - 1));
        invalidate();
    }

    int count() {
        return chans.size();
    }

    int focus() {
        return focus;
    }

    /** The channel on the remote's card, null with none. */
    Channel focused() {
        return focus < chans.size() ? chans.get(focus) : null;
    }

    private int rows() {
        return (chans.size() + COLS - 1) / COLS;
    }

    /** Pure: the middle of column col of cols across the overlay, each as wide as cols across 1824 with GAP between. */
    static float middle(int col, int cols) {
        float w = (W - (cols - 1) * GAP) / cols;
        return X + col * (w + GAP) + w / 2f;
    }

    /** Pure: the column of cols (as middle lays them) whose middle is nearest design x. */
    static int nearest(float x, int cols) {
        float w = (W - (cols - 1) * GAP) / cols;
        int k = Math.round((x - X - w / 2f) / (w + GAP));
        return Math.max(0, Math.min(cols - 1, k));
    }

    /** The remote onto the last row's card nearest design x (from the settings' first row, Up). */
    void focusNear(float x) {
        int n = chans.size();
        if (n == 0) return;
        focus = Math.min(n - 1, (rows() - 1) * COLS + nearest(x, COLS));
        invalidate();
    }

    boolean key(int code) {
        int n = chans.size(), row = focus / COLS, col = focus % COLS;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (col > 0) move(focus - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (col < COLS - 1 && focus + 1 < n) move(focus + 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (row + 1 < rows()) {
                    move(Math.min(focus + COLS, n - 1));
                    return true;
                }
                return host.edge(Ui.DOWN, middle(col, COLS));
            case KeyEvent.KEYCODE_DPAD_UP:
                if (row > 0) {
                    move(focus - COLS);
                    return true;
                }
                return host.edge(Ui.UP, middle(col, COLS));
            default:
                return false;
        }
    }

    private void move(int to) {
        focus = to;
        invalidate();
    }

    void ok() {
        Channel c = focused();
        if (c != null) host.tune(c);
    }

    // ---- drawing --------------------------------------------------------------------------------------------------

    @Override protected void onDraw(Canvas c) {
        boolean moving = lift.step(focused());
        int n = chans.size();
        long now = System.currentTimeMillis();
        Library lib = Library.get(getContext());
        drawHead(c);
        // The resting cards first, then the lifted ones over them (their shadow and ring over their neighbours').
        for (int pass = 0; pass < 2; pass++) {
            for (int k = 0; k < n; k++) {
                boolean up = Math.abs(lift.of(chans.get(k))) > 0.001f;
                if (up == (pass == 1)) drawCard(c, k, now, lib, moving);
            }
        }
        float foot = TOP + Math.max(1, rows()) * (CARD_H + GAP) - GAP;
        c.drawText(rows() > 1 ? KEYS_TWO : KEYS_ONE, Ui.f(TEXT_X), Type.centred(keysP, Ui.f(foot + HINT_BELOW)), keysP);
        if (moving) postInvalidateOnAnimation();
    }

    /** "Recently watched" and how many at the left; where Down goes at the right, after a chevron. */
    private void drawHead(Canvas c) {
        float cy = Ui.f(HEAD_Y), base = Type.centred(headP, cy), x = Ui.f(TEXT_X);
        c.drawText(HEAD, x, base, headP);
        c.drawText(count, x + headP.measureText(HEAD) + Ui.f(14f), base, countP);
        String down = rows() > 1 ? DOWN_TWO : DOWN_ONE;
        float dw = downP.measureText(down), dx = Ui.f(TEXT_R) - dw;
        c.drawText(down, dx, Type.centred(downP, cy), downP);
        chevron.setColor(0xB3FFFFFF);
        chevron.setStrokeWidth(Math.max(1f, Ui.f(2.2f)));
        Ui.chevron(c, dx - Ui.f(10f + 11f), cy, Ui.f(5.5f), Ui.DOWN, chevron);
    }

    /** "40 min left" for card k, made again only when its minute changes. */
    private String left(int k, long ms) {
        int m = (int) Math.max(1L, (ms + 59_999L) / 60_000L);
        if (m != leftFor[k]) {
            leftFor[k] = m;
            lefts[k] = GuideGrid.leftWords(m);
        }
        return lefts[k];
    }

    private void drawCard(Canvas c, int k, long now, Library lib, boolean moving) {
        Channel ch = chans.get(k);
        float w = Ui.f(CARD_W), h = Ui.f(CARD_H), rad = Ui.f(RADIUS);
        float x = Ui.f(X + (k % COLS) * (CARD_W + GAP)), y = Ui.f(TOP + (k / COLS) * (CARD_H + GAP));
        float up = Math.max(0f, lift.of(ch)), on = Math.min(1f, up);
        Guide.Schedule s = lib.schedule(ch.key);
        int i = s == null ? -1 : s.at(now);
        // The programme's own picture (asked for once the overlay rests); the channel's own picture is its colour here.
        String url = i < 0 ? "" : moving ? ProgrammePictures.peek(ch, s, i) : ProgrammePictures.get(getContext(), ch, s, i, redraw);
        boolean picture = url != null && !url.isEmpty() && !url.startsWith(Pics.ON_COLOUR);
        Bitmap b = picture ? Pics.get(url, Math.round(w), Math.round(h), Pics.FILL, redraw) : null;
        c.save();
        c.translate(x, y);
        float sc = 1f + (LIFT - 1f) * up;
        if (sc != 1f) c.scale(sc, sc, w / 2f, h / 2f);
        box.set(0, 0, w, h);
        if (on > 0.01f) Shadows.draw(c, box, rad, SH_LIFT, on);
        int base = ChannelLook.colour(ch);
        fill.setColor(base == 0 ? EMPTY : base | 0xFF000000);
        c.drawRoundRect(box, rad, rad, fill);
        if (b != null) c.drawRoundRect(box, rad, rad, shaders.paint(b, w, h, 0f));
        // The words' shade from the left: the unit box stretched across the card, its corners kept round.
        c.save();
        c.scale(w, 1f);
        c.drawRoundRect(0, 0, 1f, h, rad / w, rad, shade);
        c.restore();
        // The channel's logo on its colour at the top left.
        cell.set(Ui.f(PAD), Ui.f(PAD), Ui.f(PAD + LOGO_W), Ui.f(PAD + LOGO_H));
        ChannelLook.cell(c, cell, Ui.f(LOGO_R), ch, 0f, 0, null, false, redraw);
        // Its name and the programme's title beside it.
        float wx = Ui.f(WORDS_X), room = Math.min(Ui.f(WORDS_W), w - wx - Ui.f(PAD));
        nameFit.line(c, names[k], wx, Ui.f(NAME_Y), room);
        titleFit.line(c, i >= 0 ? s.title[i] : s == null ? NO_GUIDE : NOTHING_NOW, wx, Ui.f(TITLE_Y), room);
        if (i >= 0) {
            c.drawText(left(k, s.stop[i] - now), Ui.f(PAD), Ui.f(LEFT_Y), leftP);
            if (com.gathin.homefieldtv.prefs.Look.progress) {
                // How far through, along the foot inside the card's corners (the card's shape cut to the line).
                float top = h - Ui.f(LINE_H), p = LiveText.progress(now, s.start[i], s.stop[i]);
                c.save();
                c.clipRect(0, top, w, h);
                c.drawRoundRect(box, rad, rad, track);
                c.clipRect(0, top, w * p, h);
                c.drawRoundRect(box, rad, rad, done);
                c.restore();
            }
        }
        if (on > 0.01f) {
            // The remote's card: a white ring just outside its edge.
            float rw = Ui.f(RING), o = rw / 2f;
            box.set(-o, -o, w + o, h + o);
            ring.setStrokeWidth(rw);
            ring.setColor(Palette.alpha(Look07.L1, Math.round(255 * on)));
            c.drawRoundRect(box, rad + o, rad + o, ring);
        }
        c.restore();
    }
}
