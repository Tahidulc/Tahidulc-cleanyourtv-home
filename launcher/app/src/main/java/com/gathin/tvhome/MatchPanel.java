package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The Control Centre's Match mode (its tile opens it): a card the Control Centre's size and look, as Notifications'.
 * At the top Match mode on or off for the game chosen, and the game (OK steps through today's games of the followed
 * clubs and competitions, live ones first); then its switches: quiet except this game, the lights down at kick-off and
 * the goal lights (with a test), only on a TV with Tuya lights; then the alerts' timing (InStep): what the channel
 * watched waits, its line-up asked for again at the next kick-off, five seconds sooner or later, and whether to ask at
 * kick-off at all. The arrows move, OK switches, Back closes; what is switched is kept at once.
 */
final class MatchPanel {
    private final HomeV2 h;
    private final Context c;
    private final Typeface heavy, semi, regular;
    private FrameLayout overlay, card, body;
    private boolean open;
    /** Today's games to choose from, and the one chosen. */
    private final List<ScoresTicker.Game> games = new ArrayList<>();
    private int chosen;
    private boolean loading;

    /** One row: its view, its lines, its switch (or none), what it says under its name now, and what OK does. */
    private static final class Row {
        View view;
        TextView sub;
        View track, knob;
        BooleanSupplier on;
        Supplier<String> words;
        Runnable ok;
    }

    private final List<List<Row>> lines = new ArrayList<>();
    private int line, col;

    private static final int CARD_X = 260, CARD_Y = 90, CARD_W = 1400, CARD_H = 900, SIDE = 56;
    private static final int ON = 0xFF34C759, OFF = 0x40FFFFFF, ROW = 0x33050838, ROW_LINE = 0x26FFFFFF;
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("H:mm", Locale.UK);

    MatchPanel(HomeV2 home) {
        h = home;
        c = home.a;
        heavy = c.getResources().getFont(R.font.outfit_extrabold);
        semi = c.getResources().getFont(R.font.inter_semibold);
        regular = c.getResources().getFont(R.font.inter_regular);
    }

    boolean isOpen() {
        return open;
    }

    void show() {
        if (open) return;
        open = true;
        overlay = new FrameLayout(c);
        overlay.setClickable(true);
        overlay.setBackgroundColor(0xB3050816);
        card = new FrameLayout(c);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF0E5B3A, 0xFF0B3F2E, 0xFF07261D});
        g.setCornerRadius(h.px(44));
        g.setStroke(Math.max(1, h.px(1.5f)), 0x40FFFFFF);
        card.setBackground(g);
        card.setClipToOutline(true);
        card.setOutlineProvider(h.round(44));
        body = new FrameLayout(c);
        body.setClipChildren(false);
        card.addView(body, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(h.px(CARD_W), h.px(CARD_H));
        bl.leftMargin = h.px(CARD_X);
        bl.topMargin = h.px(CARD_Y);
        overlay.addView(card, bl);
        h.root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        build();
        overlay.setAlpha(0f);
        overlay.animate().alpha(1f).setDuration(220).setInterpolator(Fluid.EASE).start();
        Fluid.in(card, h.controlButton());
        load();
    }

    void hide() {
        if (!open) return;
        open = false;
        View o = overlay;
        View k = card;
        overlay = null;
        card = null;
        Fluid.out(k, h.controlButton(), null);
        o.animate().alpha(0f).setDuration(220).setInterpolator(Fluid.EASE_IN).withEndAction(() -> h.root.removeView(o)).start();
        h.paint();
    }

    /** Today's games, read off the main thread (the scores' service has them, else they are read). */
    private void load() {
        if (loading) return;
        loading = true;
        h.io.execute(() -> {
            List<ScoresTicker.Game> got;
            try {
                got = GoalWatchService.today(c);
            } catch (RuntimeException e) {
                got = new ArrayList<>();
            }
            List<ScoresTicker.Game> list = choices(got, new HashSet<>(Arrays.asList(SyncHub.starredClubs(c))));
            h.root.post(() -> {
                loading = false;
                if (!open) return;
                games.clear();
                games.addAll(list);
                chosen = 0;
                String now = MatchMode.game(c);
                for (int i = 0; i < games.size(); i++) if (games.get(i).id.equals(now)) chosen = i;
                paint(false);
            });
        });
    }

    /**
     * Pure: the games to choose from, in order: not over, the starred clubs' first, then live before to come, then by
     * kick-off.
     */
    static List<ScoresTicker.Game> choices(List<ScoresTicker.Game> all, Set<String> starred) {
        List<ScoresTicker.Game> out = new ArrayList<>();
        for (ScoresTicker.Game g : all) if (g != null && g.id != null && g.state != 2) out.add(g);
        out.sort((a, b) -> {
            boolean sa = starred.contains(a.homeId) || starred.contains(a.awayId), sb = starred.contains(b.homeId) || starred.contains(b.awayId);
            if (sa != sb) return sa ? -1 : 1;
            if (a.state != b.state) return a.state == 0 ? -1 : b.state == 0 ? 1 : 0;
            return Long.compare(a.kickoff, b.kickoff);
        });
        return out;
    }

    private ScoresTicker.Game game() {
        return games.isEmpty() ? null : games.get(Math.min(chosen, games.size() - 1));
    }

    private String gameWords() {
        if (loading && games.isEmpty()) return "Finding today's games…";
        ScoresTicker.Game g = game();
        if (g == null) return "No games today from your clubs and competitions";
        String when = g.state == 0 ? "Live" + (g.halftime ? " · half time" : "")
                : HM.format(Instant.ofEpochMilli(g.kickoff).atZone(ZoneId.systemDefault()));
        return MatchMode.label(g) + " · " + when + (games.size() > 1 ? "   (" + (chosen + 1) + " of " + games.size() + ")" : "");
    }

    // ---- the page ----------------------------------------------------------------------------------------------------

    private void build() {
        body.removeAllViews();
        lines.clear();
        TextView title = h.text(40, heavy, Color.WHITE);
        title.setText("Match mode");
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2);
        tl.leftMargin = h.px(SIDE);
        tl.topMargin = h.px(36);
        body.addView(title, tl);
        TextView hint = h.text(17, regular, 0x99FFFFFF);
        Keys.set(hint, "OK to choose  ·  Back to close");
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        hl.rightMargin = h.px(SIDE);
        hl.topMargin = h.px(50);
        body.addView(hint, hl);

        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2);
        lp.leftMargin = h.px(SIDE);
        lp.rightMargin = h.px(SIDE);
        lp.topMargin = h.px(110);
        body.addView(list, lp);

        Row onOff = row("Match mode", true);
        onOff.on = () -> MatchMode.active(c);
        onOff.words = () -> MatchMode.active(c) ? "On for " + MatchMode.label(c) + " · off a few minutes after full time"
                : game() == null ? "Choose a game first" : "Off · OK to turn it on for the game below";
        onOff.ok = () -> {
            if (MatchMode.active(c)) MatchMode.stop(c);
            else if (game() != null) MatchMode.start(c, game());
        };
        Row which = row("The game", false);
        which.words = this::gameWords;
        which.ok = () -> {
            if (games.size() < 2) return;
            chosen = (chosen + 1) % games.size();
            // On already: on for the game chosen now.
            if (MatchMode.active(c)) MatchMode.start(c, game());
        };
        add(list, 0, onOff, which);

        heading(list, "WHILE IT'S ON");
        Row quiet = row("Quiet, except this game", true);
        quiet.on = () -> MatchMode.quiet(c);
        quiet.words = () -> "No other pop-ups but the doorbell";
        quiet.ok = () -> MatchMode.setQuiet(c, !MatchMode.quiet(c));
        if (MatchLights.available(c)) {
            Row dim = row("Lights down at kick-off", true);
            dim.on = () -> MatchMode.dims(c);
            dim.words = () -> "To " + MatchMode.DIM_PERCENT + "%, back up at half time and full time";
            dim.ok = () -> MatchMode.setDims(c, !MatchMode.dims(c));
            add(list, 14, quiet, dim);
            Row goals = row("Goal lights", true);
            goals.on = () -> MatchMode.goalLights(c);
            goals.words = () -> "Your club's colours on its goals, a red pulse for a red card";
            goals.ok = () -> MatchMode.setGoalLights(c, !MatchMode.goalLights(c));
            Row test = row("Test the goal lights", false);
            test.words = () -> game() == null ? "In white and gold" : "In " + clubOf(game()) + "'s colours, for a few seconds";
            test.ok = () -> MatchMode.test(c, game());
            add(list, 14, goals, test);
        } else {
            add(list, 14, quiet);
        }

        heading(list, "ALERTS IN STEP WITH YOUR CHANNEL");
        Row timing = row("Line up again at the next kick-off", false);
        timing.words = () -> InStep.describe(c);
        timing.ok = () -> {
            InStep.askAgain(c);
            InStep.setAsking(c, true);
        };
        Row ask = row("Ask at kick-off", true);
        ask.on = () -> InStep.asking(c);
        ask.words = () -> "A card asks for OK as the game kicks off on screen";
        ask.ok = () -> InStep.setAsking(c, !InStep.asking(c));
        add(list, 14, timing, ask);
        Row hop = row("Half-time hop", true);
        hop.on = () -> HalfTimeHop.on(c);
        hop.words = () -> "Another game for the break, back for the second half";
        hop.ok = () -> HalfTimeHop.setOn(c, !HalfTimeHop.on(c));
        Row replay = row("See it again", false);
        replay.words = () -> Replay.available(c) ? "On: OK on a goal's card goes back to it" : "Needs the TV's own debugging allowed once (the on-screen mouse)";
        replay.ok = () -> { if (!Replay.available(c)) LocalAdb.check(c); };
        add(list, 14, hop, replay);
        Row brk = row("Broadcast mode", true);
        brk.on = () -> HalfTimeBreak.on(c);
        brk.words = () -> "At half time, Homefield's own break over the channel: facts, a quiz, prayer times, other games";
        brk.ok = () -> HalfTimeBreak.setOn(c, !HalfTimeBreak.on(c));
        Row hush = row("Channel's sound off in the break", true);
        hush.on = () -> HalfTimeBreak.mutes(c);
        hush.words = () -> "Back on with the match";
        hush.ok = () -> HalfTimeBreak.setMutes(c, !HalfTimeBreak.mutes(c));
        add(list, 14, brk, hush);
        Row see = row("See the break now", false);
        see.words = () -> "For two minutes; OK or Back ends it";
        see.ok = () -> HalfTimeBreak.test(c);
        add(list, 14, see);
        Row sooner = row("Pop-ups 5 s sooner", false);
        sooner.words = () -> "When the score shows before the picture does";
        sooner.ok = () -> InStep.nudge(c, -InStep.STEP_MS);
        Row later = row("Pop-ups 5 s later", false);
        later.words = () -> "When the pop-up comes before the goal";
        later.ok = () -> InStep.nudge(c, InStep.STEP_MS);
        add(list, 14, sooner, later);

        line = 0;
        col = 0;
        paint(true);
    }

    /** The club cheered in a game: the starred one, else the home side. */
    private String clubOf(ScoresTicker.Game g) {
        Set<String> starred = new HashSet<>(Arrays.asList(SyncHub.starredClubs(c)));
        boolean away = "away".equals(MatchMode.side(starred, g.homeId, g.awayId));
        String n = away ? (g.awayName != null ? g.awayName : g.away) : (g.homeName != null ? g.homeName : g.home);
        return n == null ? "the club" : n;
    }

    private void heading(LinearLayout list, String words) {
        TextView head = h.text(15, semi, 0xB3FFFFFF);
        head.setText(words);
        head.setLetterSpacing(0.16f);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-2, -2);
        hp.leftMargin = h.px(4);
        hp.topMargin = h.px(26);
        hp.bottomMargin = h.px(4);
        list.addView(head, hp);
    }

    /** A line of one or two rows across, topGap above it. */
    private void add(LinearLayout list, int topGap, Row... rows) {
        LinearLayout across = new LinearLayout(c);
        across.setClipChildren(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, h.px(92));
        lp.topMargin = h.px(topGap);
        list.addView(across, lp);
        List<Row> mine = new ArrayList<>();
        for (int i = 0; i < rows.length; i++) {
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, -1, 1f);
            if (i > 0) rp.leftMargin = h.px(20);
            across.addView(rows[i].view, rp);
            mine.add(rows[i]);
        }
        if (rows.length == 1) {
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, -1, 1f);
            rp.leftMargin = h.px(20);
            across.addView(new View(c), rp);
        }
        lines.add(mine);
    }

    /** A row: its name, the line under it, and a switch at its end (or a › for an action). */
    private Row row(String name, boolean switched) {
        Row r = new Row();
        LinearLayout t = new LinearLayout(c);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(h.px(24), 0, h.px(20), 0);
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView n = h.text(22, semi, Color.WHITE);
        n.setText(name);
        n.setSingleLine(true);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        words.addView(n);
        TextView s = h.text(15, regular, 0xB3FFFFFF);
        s.setSingleLine(true);
        s.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.topMargin = h.px(4);
        words.addView(s, sl);
        r.sub = s;
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, -2, 1f);
        wl.rightMargin = h.px(14);
        t.addView(words, wl);
        if (switched) {
            FrameLayout track = new FrameLayout(c);
            View knob = new View(c);
            knob.setBackground(h.fill(15, Color.WHITE));
            FrameLayout.LayoutParams kl = new FrameLayout.LayoutParams(h.px(30), h.px(30), Gravity.CENTER_VERTICAL | Gravity.START);
            kl.leftMargin = h.px(4);
            track.addView(knob, kl);
            t.addView(track, new LinearLayout.LayoutParams(h.px(66), h.px(38)));
            r.track = track;
            r.knob = knob;
        } else {
            TextView go = h.text(30, regular, 0xB3FFFFFF);
            go.setText("›");
            t.addView(go);
        }
        r.view = t;
        return r;
    }

    // ---- keys and focus ----------------------------------------------------------------------------------------------

    boolean key(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) hide();
            return true;
        }
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (ok) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) {
                Row r = focused();
                if (r != null && r.ok != null) {
                    r.ok.run();
                    if (open) paint(false);
                }
            }
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN || lines.isEmpty()) return true;
        if (code == KeyEvent.KEYCODE_DPAD_UP && line > 0) { line--; col = Math.min(col, lines.get(line).size() - 1); }
        else if (code == KeyEvent.KEYCODE_DPAD_DOWN && line < lines.size() - 1) { line++; col = Math.min(col, lines.get(line).size() - 1); }
        else if (code == KeyEvent.KEYCODE_DPAD_LEFT && col > 0) col--;
        else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && col < lines.get(line).size() - 1) col++;
        else return true;
        paint(false);
        return true;
    }

    private Row focused() {
        if (line >= lines.size()) return null;
        List<Row> l = lines.get(line);
        return l.isEmpty() ? null : l.get(Math.min(col, l.size() - 1));
    }

    /** Each row as it is now: the focused one ringed in white and a little larger, its words and switch as they are. */
    private void paint(boolean jump) {
        Row f = focused();
        for (List<Row> l : lines) {
            for (Row r : l) {
                boolean on = r == f;
                GradientDrawable bg = h.fill(22, on ? 0x2EFFFFFF : ROW);
                bg.setStroke(on ? h.px(3) : Math.max(1, h.px(1)), on ? Color.WHITE : ROW_LINE);
                r.view.setBackground(bg);
                Fluid.spring(r.view, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_X, on ? 1.03f : 1f, Fluid.STIFF_SMALL, 0.8f);
                Fluid.spring(r.view, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_Y, on ? 1.03f : 1f, Fluid.STIFF_SMALL, 0.8f);
                if (r.words != null) r.sub.setText(r.words.get());
                if (r.track != null && r.on != null) {
                    boolean lit = r.on.getAsBoolean();
                    r.track.setBackground(h.fill(19, lit ? ON : OFF));
                    float x = lit ? h.px(28) : 0f;
                    if (jump) r.knob.setTranslationX(x);
                    else if (r.knob.getTranslationX() != x) r.knob.animate().translationX(x).setDuration(160).setInterpolator(Fluid.EASE).start();
                }
            }
        }
    }
}
