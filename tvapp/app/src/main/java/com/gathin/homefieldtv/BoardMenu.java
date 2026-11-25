package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import java.util.Locale;

/**
 * The scoreboard and logo menu over Homefield TV's player: the TV launcher's menu over TiviMate copied (BoardCover, "its
 * menu over the match: OK held"), for the family's "hold down ok when I'm in full screen on the homefield tv app and the
 * scoreboard pop up settings menu comes up ..., and then I can turn the scoreboard and logo on and off just like it is on
 * tivimate". OK held while a channel plays full screen with nothing over the picture opens it (LiveScreen.okLong, then
 * BoardLayer.okLong), on every channel, over a match or not; what it sets with no match on the channel is kept for the
 * next one. It is the only place for the scoreboard's and the logo's settings (Settings has no page for them now).
 *
 * <p>Its look, the launcher's: one panel at the bottom centre, its foot 8 % of the screen's height above the bottom, a
 * dark blue gradient (top left 0xFF141B3A to bottom right 0xFF0A0E22), corners of 28, a 1 px line of white at a quarter
 * (0x40FFFFFF, the launcher's), 22 inside. At its top "Scoreboard and logo · Up and Down, Left and Right, OK · Back to
 * close" and what is kept for the channel on, then the match on it ("No match on this channel now", "Reading the match",
 * "Reading the scores", or the two clubs: BoardLayer.menuLine). Down its left the lines, "Label:  ‹  Value  ›" (menuLines),
 * 21 px Inter semibold: the remote's on white at 20 % with a 3 px white ring, the rest white at 80 %. At its right, 470
 * wide, the logo's cover as it will look, drawn by the cover itself on a pitch's green (a stand-in for the picture), its
 * look's name under it, and small either side under that the board's looks before and after it (the cover is drawn in
 * the board's look: what Left and Right on "Scoreboard look" would bring). On "Logo's look" and "Picture tag on the logo"
 * (the family's "the option to pick what logo I want to use like tivimate": the channel's own logo as it came, then the
 * launcher's looks over TiviMate, drawn by the same CoverLooks, with the picture tag as the launcher's Scoreboard card
 * sets it) the small ones are the logo's looks before and after the one chosen.
 *
 * <p>Its top stays below TOP of the screen's height, where the launcher's menu reaches, so the board and the cover stay in
 * sight as they are switched: the lines that don't fit slide into sight as the remote goes, a small chevron where they go
 * on. The remote is its own while it is up (BoardLayer.takesKeys): Up and Down go along the lines (stopping at the ends,
 * as the launcher's), Left and Right change the one the remote is on, OK does it (a switch or a choice steps on, as Right),
 * Back closes it. It closes itself after MENU_MS without a key, each key starting the wait again, and goes away at once
 * when anything comes over the picture or the screen changes (BoardLayer's gate and stop). The channel keys, the numbers
 * and the volume go on to the live screen.
 */
final class BoardMenu extends View {
    /** What the menu asks of the scoreboard over the player (BoardLayer). */
    interface Host {
        /** The lines' values as they stand now (worked out as it opens and after each key, never while drawing). */
        MenuState state();

        /** Left (-1) or Right (1) on a line: a switch turned over, a choice the one before or after. */
        void step(int line, int step);

        /** OK on a line: true when it did something of its own; false, it steps on as Right does. */
        boolean ok(int line);

        /** The logo's cover drawn at (0, 0), w x h, in the board's look `look` away from the one now (0: as it is now). */
        void cover(Canvas c, float w, float h, int look);

        /** The logo's cover drawn at (0, 0), w x h, in the logo's look `step` away from the one chosen, the board as it is. */
        void logoCover(Canvas c, float w, float h, int step);

        /** The cover's width over its height on this channel, as its place has it. */
        float coverAspect();
    }

    /** It closes itself after this long without a key (the launcher's MENU_MS). */
    static final long MENU_MS = 15_000L;
    /**
     * Its top is never higher than this share of the screen's height (where the launcher's sixteen lines reach): the
     * board top left and the cover top right, in their standard places in any look, stay in sight.
     */
    static final float TOP = 0.14f;

    /** What the menu's lines say: each switch and choice as it stands. */
    static final class MenuState {
        boolean boardShown = true, logoOn = true, lineups = true, ask = true, alerts = true, var = true, swish = true;
        /** Back to the standard places on every channel (else this channel's); the match on the channel live now. */
        boolean every, live;
        /** The board's and the cover's size, as a share of the standard ones. */
        int boardSize = 100, logoSize = 100;
        /** The look chosen; the board drawn now (one of My scoreboards, else the look); the looks either side of it. */
        String look = "", lookNow = "", lookBefore = "", lookAfter = "";
        /** One of My scoreboards in use, "" for none. */
        String mine = "";
        /** The logo's look chosen and those either side of it (BoardPrefs.logoLookWords), its picture tag in words. */
        String logoLook = BoardPrefs.logoLookWords(BoardPrefs.CHANNEL_LOGO), logoBefore = "", logoAfter = "",
                logoTag = com.gathin.broadcast.CoverLooks.tagWords(null, "");
        /** This channel's own logo, the style, the wordmark's words, the background, in words. */
        String logoHere = "As the rest", logoStyle = "1 of 54", logoWhat = "Auto, from the game", logoGround = "Plate";
        /** A general channel (the name alone), the competition's logo beside it, LIVE along its foot. */
        boolean logoNameOnly, logoComp = true, logoLive = true;
        /** Event's extras on: Up next, prayer time, clock and weather, the picture and sound marks. */
        boolean next = true, prayer = true, clock = true, marks = true;
        String timing = "", usual = "", popups = "", sound = "";
        /** The goal animation chosen, in words (GoalLooks.name). */
        String goalLook = com.gathin.broadcast.GoalLooks.name(com.gathin.broadcast.GoalLooks.DEFAULT);
        /** The channel on now ("" when there is none), and the line under the title (BoardLayer.menuLine). */
        String channel = "", note = "";
    }

    /** The lines, in order: the two switches the family uses on TiviMate first, then the rest as the launcher orders them. */
    /**
     * The logo's lines, every choice the launcher gives its logo over TiviMate (its Scoreboard card and its hold-OK menu):
     * the look (every one of them, the channel's own logo first as Homefield TV's came), this channel's own, the style of
     * the 54, the Sports wordmark's words, a sports or general channel, the picture tag, the background, the competition's
     * logo beside it, LIVE along its foot, and Event's four extras.
     */
    static final int M_BOARD = 0, M_LOGO = 1, M_LOGO_LOOK = 2, M_LOGO_HERE = 3, M_LOGO_STYLE = 4, M_LOGO_WHAT = 5,
            M_LOGO_CHANNEL = 6, M_LOGO_TAG = 7, M_LOGO_GROUND = 8, M_LOGO_COMP = 9, M_LOGO_LIVE = 10, M_LOGO_NEXT = 11,
            M_LOGO_PRAYER = 12, M_LOGO_CLOCK = 13, M_LOGO_MARKS = 14, M_LOOK = 15, M_MOVE = 16, M_MOVE_LOGO = 17,
            M_LINEUPS = 18, M_MINE = 19, M_NEW = 20, M_TIMING = 21, M_USUAL = 22, M_NOW = 23, M_ASK = 24, M_ALERTS = 25, M_VAR = 26,
            M_POPUPS = 27, M_GOAL = 28, M_SOUND = 29, M_SWISH = 30, M_RESET = 31;
    static final int LINES = 32;

    /** Whether a line is about the logo's look (the previews under the cover show its looks either side then). */
    static boolean logoLine(int line) {
        return line >= M_LOGO_LOOK && line <= M_LOGO_MARKS;
    }

    /** Event's extra a line switches (LogoExtras), or null for a line that is none of them. */
    static String extraOf(int line) {
        switch (line) {
            case M_LOGO_NEXT: return com.gathin.broadcast.LogoExtras.NEXT;
            case M_LOGO_PRAYER: return com.gathin.broadcast.LogoExtras.PRAYER;
            case M_LOGO_CLOCK: return com.gathin.broadcast.LogoExtras.CLOCK;
            case M_LOGO_MARKS: return com.gathin.broadcast.LogoExtras.MARKS;
            default: return null;
        }
    }

    static final String HEAD = "Scoreboard and logo  ·  Up and Down, Left and Right, OK  ·  Back to close";

    /** Pure: the menu's lines, as the switches stand. */
    static String[] menuLines(MenuState m) {
        return new String[]{
                "Scoreboard:  ‹  " + onOff(m.boardShown) + "  ›",
                "Logo's cover:  ‹  " + onOff(m.logoOn) + "  ›",
                "Logo's look:  ‹  " + m.logoLook + "  ›",
                "This channel's logo:  ‹  " + m.logoHere + "  ›",
                "Style (Whitebrook Road styles):  ‹  " + m.logoStyle + "  ›",
                "On the logo (Whitebrook Road Sports):  ‹  " + m.logoWhat + "  ›",
                "Channel:  ‹  " + (m.logoNameOnly ? "General · Whitebrook Road" : "Sports · Whitebrook Road Sports") + "  ›",
                "Picture tag on the logo:  ‹  " + m.logoTag + "  ›",
                "Logo's background:  ‹  " + m.logoGround + "  ›",
                "Competition logo on the logo:  ‹  " + onOff(m.logoComp) + "  ›",
                "LIVE on the logo:  ‹  " + onOff(m.logoLive) + "  ›",
                "Up next on the logo (Event):  ‹  " + onOff(m.next) + "  ›",
                "Prayer time on the logo (Event):  ‹  " + onOff(m.prayer) + "  ›",
                "Clock and weather on the logo (Event):  ‹  " + onOff(m.clock) + "  ›",
                "Picture and sound marks on the logo (Event):  ‹  " + onOff(m.marks) + "  ›",
                "Scoreboard look:  ‹  " + m.look + "  ›",
                "Move and resize the scoreboard:  ‹  " + m.boardSize + "%  ›  ·  OK: move it with the arrows",
                "Move and resize the logo's cover:  ‹  " + m.logoSize + "%  ›  ·  OK: move it with the arrows",
                "Line-ups on screen:  ‹  " + onOff(m.lineups) + "  ›  ·  OK: show them now",
                "My scoreboards:  ‹  " + (m.mine.isEmpty() ? "None (the look above)" : m.mine) + "  ›  ·  OK: the editor, Generate, Ask AI",
                "New scoreboard  ·  OK: a new one in the editor",
                "Timing on this channel:  ‹  " + m.timing + "  ›  ·  Left sooner, Right later",
                "A channel not lined up yet:  ‹  " + m.usual + "  ›",
                "It's just happened  ·  OK: as a goal, a card or the kick-off shows",
                "Ask at each kick-off:  ‹  " + onOff(m.ask) + "  ›",
                "Alerts beside the board:  ‹  " + onOff(m.alerts) + "  ›",
                "VAR explained:  ‹  " + onOff(m.var) + "  ›",
                "Kick-off and goal pop-ups:  ‹  " + m.popups + "  ›",
                "Goal animation:  ‹  " + m.goalLook + "  ›",
                "Goal sound:  ‹  " + m.sound + "  ›  ·  it plays as it is chosen",
                "Swishes:  ‹  " + onOff(m.swish) + "  ›",
                "Back to the standard places:  ‹  " + (m.every ? "Every channel" : "This channel") + "  ›  ·  OK: put them back"};
    }

    private static String onOff(boolean on) {
        return on ? "On" : "Off";
    }

    /** The header's second line: what is kept for the channel on ("" with none). */
    static String keptFor(String channel) {
        return channel == null || channel.isEmpty() ? "" : "The places, sizes and timing are kept for " + channel;
    }

    /** "A channel not lined up yet": how far behind it is taken to be, the choices Left and Right step through. */
    static final int[] USUAL = {0, 15, 30, 45, 60, 90, 120};

    /** Pure: a delay in words: "Live, no delay", "60 s behind live". */
    static String usualWords(int s) {
        return s <= 0 ? "Live, no delay" : String.format(Locale.UK, "%d s behind live", s);
    }

    /** Pure: the choice step away from the one nearest seconds (1 the next, -1 the one before), stopping at the ends. */
    static int nextUsual(int seconds, int step) {
        int at = 0;
        for (int i = 0; i < USUAL.length; i++) if (Math.abs(USUAL[i] - seconds) < Math.abs(USUAL[at] - seconds)) at = i;
        return USUAL[Math.max(0, Math.min(USUAL.length - 1, at + step))];
    }

    /** Pure: the first of count lines in sight with the remote on line at, shown of them at once. */
    static int menuFirst(int at, int count, int shown) {
        return Math.max(0, Math.min(at - shown / 2, count - shown));
    }

    private final Host host;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable away = this::close;
    boolean open;
    /** The line the remote is on. */
    int at;
    private MenuState state = new MenuState();
    private String[] lines = menuLines(state);

    private final TextPaint headP = Dark.text(19, Type.REGULAR, 0xB3FFFFFF), noteP = Dark.text(17, Type.REGULAR, 0x99FFFFFF),
            rowP = Dark.text(21, Type.SEMIBOLD, 0xFFFFFFFF), labelP = Dark.text(15, Type.REGULAR, 0x99FFFFFF),
            captionP = Dark.text(19, Type.SEMIBOLD, 0xFFFFFFFF), wayP = Dark.text(14, Type.REGULAR, 0x80FFFFFF),
            nameP = Dark.text(15, Type.SEMIBOLD, 0x99FFFFFF);
    private final Paint panel = new Paint(Paint.ANTI_ALIAS_FLAG), edge = Dark.line(0x40FFFFFF, 1f), hi = Dark.fill(0x33FFFFFF),
            ring = new Paint(Paint.ANTI_ALIAS_FLAG), pitch = new Paint(Paint.ANTI_ALIAS_FLAG), hint = Dark.line(0x80FFFFFF, 2f);
    private final RectF r = new RectF(), row = new RectF(), stage = new RectF(), line = new RectF();
    /** The panel's gradient and the stand-in pictures' (made again only when where they are drawn changes). */
    private LinearGradient panelShade, pitchShade;
    private final RectF panelFor = new RectF();
    private float pitchTop = -1, pitchH = -1;

    BoardMenu(Context c, Host host) {
        super(c);
        this.host = host;
        labelP.setLetterSpacing(0.12f);
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(0xFFFFFFFF);
        setVisibility(GONE);
    }

    /** Up, the remote on its first line; the wait to close starts. */
    void show() {
        open = true;
        at = 0;
        setVisibility(VISIBLE);
        refresh();
        poke();
    }

    void close() {
        h.removeCallbacks(away);
        if (!open) return;
        open = false;
        setVisibility(GONE);
    }

    /** The lines worked out again (a switch changed, or the match found). */
    void refresh() {
        if (!open) return;
        state = host.state();
        lines = menuLines(state);
        invalidate();
    }

    /** A key: the wait to close starts again. */
    private void poke() {
        h.removeCallbacks(away);
        h.postDelayed(away, MENU_MS);
    }

    /** Up and Down along, Left and Right to change; false for the rest (the live screen's own). */
    boolean key(int code) {
        poke();
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                at = Math.max(0, Math.min(LINES - 1, at + (code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1)));
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                host.step(at, code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                refresh();
                return true;
            default:
                // Channel up and down, the numbers, the volume: the live screen's own.
                return false;
        }
    }

    /** OK on the line the remote is on: what it does, else a step on (as Right). */
    void ok() {
        poke();
        int line = at;
        if (!host.ok(line)) host.step(line, 1);
        refresh();
    }

    // ---- drawn (design pixels, Ui.f) --------------------------------------------------------------------------------

    /** A line's height as a TextView draws it (its font's padding in). */
    private static float lineH(TextPaint p) {
        Paint.FontMetrics fm = p.getFontMetrics();
        return fm.bottom - fm.top;
    }

    /** From one line to the next. */
    private static float lineStep(TextPaint p) {
        Paint.FontMetrics fm = p.getFontMetrics();
        return fm.descent - fm.ascent;
    }

    /** The preview's height: its label, the cover's stage, its name, and the looks either side under it. */
    private float previewH() {
        return lineH(labelP) + Ui.f(10) + Ui.f(200) + Ui.f(6) + lineH(captionP) + Ui.f(14) + lineH(wayP) + Ui.f(4) + Ui.f(96)
                + Ui.f(6) + lineH(nameP);
    }

    @Override protected void onDraw(Canvas c) {
        if (!open || lines == null) return;
        float w = getWidth(), hh = getHeight(), pad = Ui.f(22);
        String kept = keptFor(state.channel);
        String[] heads = kept.isEmpty() ? new String[]{HEAD} : new String[]{HEAD, kept};
        float headH = lineH(headP) + (heads.length - 1) * lineStep(headP), headW = 0;
        for (String s : heads) headW = Math.max(headW, headP.measureText(s) + Ui.f(20));
        boolean noted = !state.note.isEmpty();
        float noteH = noted ? lineH(noteP) + Ui.f(10) : 0f, noteW = noted ? noteP.measureText(state.note) + Ui.f(20) : 0f;
        float rowH = lineH(rowP) + Ui.f(16), rowStep = rowH + Ui.f(3), pvW = Ui.f(470), gap = Ui.f(18);
        float rowsW = 0;
        for (String s : lines) rowsW = Math.max(rowsW, rowP.measureText(s) + Ui.f(56));
        // Never wider than the screen (a bigger text size): the lines cut to fit.
        rowsW = Math.min(rowsW, w - Ui.f(48) - 2 * pad - gap - pvW);
        // Its top no higher than the launcher's menu reaches (TOP of the height), so the board and the cover in their
        // standard places stay in sight as they are switched: as many lines as fit, the rest along as the remote goes.
        float bottom = hh - hh * 0.08f, ceiling = hh * TOP, room = bottom - ceiling - 2 * pad - headH - Ui.f(12) - noteH;
        int shown = Math.max(1, Math.min(LINES, (int) (room / rowStep)));
        int first = menuFirst(at, LINES, shown);
        float bodyH = Math.max(shown * rowStep, previewH()), bodyW = rowsW + gap + pvW;
        float boxW = Math.min(w - Ui.f(24), Math.max(bodyW, Math.max(headW, noteW)) + 2 * pad);
        float boxH = 2 * pad + headH + Ui.f(12) + noteH + bodyH;
        float left = (w - boxW) / 2f, top = Math.max(Ui.f(12), bottom - boxH);
        r.set(left, top, left + boxW, top + boxH);
        if (panelShade == null || !panelFor.equals(r)) {
            panelShade = new LinearGradient(r.left, r.top, r.right, r.bottom, 0xFF141B3A, 0xFF0A0E22, Shader.TileMode.CLAMP);
            panelFor.set(r);
            panel.setShader(panelShade);
        }
        c.drawRoundRect(r, Ui.f(28), Ui.f(28), panel);
        float e = edge.getStrokeWidth() / 2f;
        line.set(r.left + e, r.top + e, r.right - e, r.bottom - e);
        c.drawRoundRect(line, Ui.f(28), Ui.f(28), edge);

        float x = r.left + pad, y = r.top + pad;
        float base = y - headP.getFontMetrics().top;
        for (String s : heads) {
            c.drawText(Ui.fit(s, headP, boxW - 2 * pad - Ui.f(20)), x + Ui.f(10), base, headP);
            base += lineStep(headP);
        }
        y += headH + Ui.f(12);
        if (noted) {
            c.drawText(Ui.fit(state.note, noteP, boxW - 2 * pad - Ui.f(20)), x + Ui.f(10), y - noteP.getFontMetrics().top, noteP);
            y += noteH;
        }

        // The lines down the left.
        float ringW = Math.max(2f, Math.round(Ui.f(3)));
        ring.setStrokeWidth(ringW);
        for (int i = first; i < first + shown; i++) {
            float rt = y + (i - first) * rowStep + Ui.f(3);
            row.set(x, rt, x + rowsW, rt + rowH);
            boolean on = i == at;
            if (on) {
                c.drawRoundRect(row, Ui.f(16), Ui.f(16), hi);
                row.inset(ringW / 2f, ringW / 2f);
                c.drawRoundRect(row, Ui.f(16), Ui.f(16), ring);
            }
            rowP.setColor(on ? 0xFFFFFFFF : 0xCCFFFFFF);
            c.drawText(Ui.fit(lines[i], rowP, rowsW - Ui.f(56)), x + Ui.f(20), rt + Ui.f(8) - rowP.getFontMetrics().top, rowP);
        }
        // More lines above or below: a small chevron where they go on.
        if (first > 0) Ui.chevron(c, x + rowsW / 2f, y - Ui.f(5), Ui.f(12), Ui.UP, hint);
        if (first + shown < LINES) Ui.chevron(c, x + rowsW / 2f, y + shown * rowStep + Ui.f(6), Ui.f(12), Ui.DOWN, hint);

        // The logo's cover as it will look, at the right.
        preview(c, x + rowsW + gap, y, pvW);
    }

    /** The cover now, on its stand-in picture, its name, and the board's looks either side of it under that. */
    private void preview(Canvas c, float px, float py, float pw) {
        float cx = px + pw / 2f;
        centred(c, "THE LOGO ON THIS CHANNEL", labelP, cx, py, pw);
        float st = py + lineH(labelP) + Ui.f(10);
        stage.set(px, st, px + pw, st + Ui.f(200));
        pitch(c, stage, Ui.f(14));
        // On the logo's own lines, its looks either side under it (what Left and Right on "Logo's look" would bring);
        // elsewhere the board's, the cover drawn in each board look's colours.
        boolean logo = logoLine(at);
        if (state.logoOn) coverIn(c, stage, Ui.f(340), Ui.f(130), 0, false);
        String name = !state.logoOn ? "Off: the channel's own logo shows"
                : (logo ? state.logoLook : state.lookNow) + "  ·  " + state.logoSize + "%";
        float y = stage.bottom + Ui.f(6);
        centred(c, name, captionP, cx, y, pw);
        y += lineH(captionP) + Ui.f(14);
        float colW = (pw - Ui.f(12)) / 2f;
        small(c, px, y, colW, "‹  before", logo ? state.logoBefore : state.lookBefore, -1, logo);
        small(c, px + colW + Ui.f(12), y, colW, "next  ›", logo ? state.logoAfter : state.lookAfter, 1, logo);
    }

    private void small(Canvas c, float x, float y, float cw, String way, String name, int look, boolean logo) {
        float cx = x + cw / 2f;
        centred(c, way, wayP, cx, y, cw);
        float st = y + lineH(wayP) + Ui.f(4);
        stage.set(x, st, x + cw, st + Ui.f(96));
        pitch(c, stage, Ui.f(14));
        coverIn(c, stage, Ui.f(170), Ui.f(66), look, logo);
        centred(c, name, nameP, cx, stage.bottom + Ui.f(6), cw);
    }

    /** A stand-in picture for the cover to sit on: a pitch's green, lighter in its middle, round corners. */
    private void pitch(Canvas c, RectF s, float round) {
        if (pitchShade == null || pitchTop != s.top || pitchH != s.height()) {
            pitchShade = new LinearGradient(0, s.top, 0, s.bottom, PITCH, null, Shader.TileMode.CLAMP);
            pitchTop = s.top;
            pitchH = s.height();
            pitch.setShader(pitchShade);
        }
        c.drawRoundRect(s, round, round, pitch);
    }

    /** The launcher's stand-in for the picture: a pitch's green, top to bottom. */
    private static final int[] PITCH = {0xFF2F7A35, 0xFF4C9A4A, 0xFF2A6A2F};

    /** The cover at its own shape, as big as fits maxW x maxH, in the middle of s: look steps of the board's, or the logo's. */
    private void coverIn(Canvas c, RectF s, float maxW, float maxH, int look, boolean logo) {
        float aspect = Math.max(0.2f, host.coverAspect());
        float cw = Math.min(maxW, maxH * aspect), ch = cw / aspect;
        int saved = c.save();
        c.translate(s.centerX() - cw / 2f, s.centerY() - ch / 2f);
        if (logo) host.logoCover(c, cw, ch, look);
        else host.cover(c, cw, ch, look);
        c.restoreToCount(saved);
    }

    /** One line of words in the middle of a width, its top at top. */
    private static void centred(Canvas c, String s, TextPaint p, float cx, float top, float width) {
        String t = Ui.fit(s, p, width);
        c.drawText(t, cx - p.measureText(t) / 2f, top - p.getFontMetrics().top, p);
    }
}
