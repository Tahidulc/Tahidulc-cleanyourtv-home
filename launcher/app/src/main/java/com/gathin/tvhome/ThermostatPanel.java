package com.gathin.tvhome;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The thermostat's card (Tuya's, TuyaCloud), opened with OK on the Control Centre's Thermostat tile: in the smart home
 * panel's navy glass, the temperature it is set to large (warm while the heating is on), the room's temperature and
 * what it is doing under it, and its modes in a row below. Left and Right change the target by the device's own step
 * (Up and Down too when it has no modes), OK switches it on or off, Down goes to the modes and OK picks one. Each change
 * shows at once and goes to Tuya as one command once the remote has been still for 0.8 s, off the main thread; refused,
 * it goes back and a short note says why. Read when it opens and every 30 s while it is open, and once when the Control
 * Centre opens (for its tile); never while both are closed. Back closes it.
 */
final class ThermostatPanel {
    private static final int NAVY = 0xF70E1450, WARM = 0xFFFF9F45, WARM_DEEP = 0xFFE5573A, WARM_INK = 0xFF2A1400;
    /** The remote still this long after the last press: the changes go to Tuya. */
    static final long SETTLE_MS = 800L;
    private static final long REFRESH_MS = 30_000L;
    /** After Tuya took a change, how long its readings may lag behind it (a radiator valve wakes only now and then). */
    static final long KEEP_MS = 120_000L;
    /** The Control Centre's tile reads afresh at most this often. */
    private static final long TILE_MS = 15_000L;
    /** What the last read found: nothing yet, the thermostat, none in the project, or Tuya not reachable. */
    private static final int READING = 0, FOUND = 1, NONE_FOUND = 2, FAILED = 3;
    /** Modes to a row. */
    private static final int ACROSS = 4;

    private final HomeV2 h;
    /** The thermostat as Tuya has it (or as last sent and taken): what the changes are measured from. */
    private TuyaCloud.Thermostat t;
    /** What the card shows: the target (raw), on, the mode. */
    private int target = TuyaCloud.NONE;
    private boolean on = true;
    private String mode;
    private int found = READING;
    /** The source line's note (a change refused, a read missed); null: when it was last read. Why a first read failed. */
    private String note, failed;
    private long lastPress, sentAt, readAt, readOk;
    private boolean sending, reading, open;
    /** What to run when the read under way is done (the Control Centre's tile redrawn). */
    private final List<Runnable> waiting = new ArrayList<>();

    private FrameLayout overlay, card, dial;
    private View dim;
    private LinearLayout body, info, modeBox;
    private TextView title, source, message, big, left, right, pill, room, now, modeHead, hint;
    private String hintShown;
    private final List<String> shownModes = new ArrayList<>();
    private final List<List<View>> chipRows = new ArrayList<>();
    /** The rows the remote moves through: the target, then the modes. */
    private final List<List<View>> rows = new ArrayList<>();
    private int row, col;
    private final Runnable flush = this::flush;
    private final Runnable tick = this::tick;

    ThermostatPanel(HomeV2 h) {
        this.h = h;
    }

    boolean isOpen() {
        return open;
    }

    // ---- open and close -------------------------------------------------------------------------------------------

    void show() {
        if (open) return;
        open = true;
        overlay = new FrameLayout(h.a);
        dim = new View(h.a);
        dim.setBackgroundColor(0x8C050816);
        dim.setAlpha(0f);
        overlay.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        card = new FrameLayout(h.a);
        GradientDrawable bg = h.fill(34, NAVY);
        bg.setStroke(Math.max(1, h.px(1.5f)), 0x2EFFFFFF);
        card.setBackground(bg);
        card.setClipToOutline(true);
        card.setOutlineProvider(h.round(34));
        card.setElevation(h.px(24));
        body = new LinearLayout(h.a);
        body.setOrientation(LinearLayout.VERTICAL);
        // The target and a mode in focus grow a little: drawn past their row's edges, not cut off at them.
        body.setClipChildren(false);
        body.setClipToPadding(false);
        body.setPadding(h.px(32), h.px(28), h.px(32), h.px(28));
        card.addView(body, new FrameLayout.LayoutParams(-1, -2));
        // The thermostat arriving after "Reading…": the card's height eases to fit (from just after it has opened).
        FrameLayout o = overlay;
        LinearLayout b = body;
        card.postDelayed(() -> {
            if (overlay != o) return;
            b.setLayoutTransition(SmartPanel.changing());
            o.setLayoutTransition(SmartPanel.changing());
        }, 340);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(h.px(660), -2, Gravity.TOP | Gravity.END);
        cl.rightMargin = h.px(78);
        cl.topMargin = h.px(196);
        overlay.addView(card, cl);
        h.root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        build();
        dim.animate().alpha(1f).setDuration(260).setInterpolator(Fluid.EASE).start();
        Fluid.in(card, h.controlButton());
        // Read now (unless the Control Centre's tile read it a moment ago), then every 30 s while it is open.
        if (found == READING || SystemClock.uptimeMillis() - readAt > 5_000L) read(null);
        h.main.removeCallbacks(tick);
        h.main.postDelayed(tick, REFRESH_MS);
    }

    void hide() {
        if (!open) return;
        open = false;
        h.main.removeCallbacks(tick);
        // A change still waiting goes now, not lost with the card.
        if (!sending && pending()) {
            h.main.removeCallbacks(flush);
            send();
        }
        FrameLayout o = overlay;
        overlay = null;
        dim.animate().alpha(0f).setDuration(220).setInterpolator(Fluid.EASE_IN).start();
        Fluid.out(card, h.controlButton(), () -> h.root.removeView(o));
    }

    private void tick() {
        if (!open) return;
        read(null);
        h.main.postDelayed(tick, REFRESH_MS);
    }

    // ---- the Control Centre's tile --------------------------------------------------------------------------------

    /** The Control Centre opened: the tile's reading afresh (unless read moments ago); redraw puts it on the tile. */
    void fresh(Runnable redraw) {
        if (found != READING && found != FAILED && SystemClock.uptimeMillis() - readAt < TILE_MS) return;
        read(redraw);
    }

    /** The tile's line: the room's temperature, the target, and the mode or Off ("20.5° → 21.5° · Auto"). */
    String glance() {
        if (found == READING) return str(R.string.thermostat_reading);
        if (found == NONE_FOUND) return str(R.string.thermostat_none_short);
        if (t == null) return str(R.string.thermostat_unreachable);
        if (!t.online) return str(R.string.offline);
        boolean off = t.switchCode != null && !on;
        String temp = t.temp == TuyaCloud.NONE ? null : TuyaCloud.degrees(t.temp, t.room, t.fahrenheit, false);
        String set = target == TuyaCloud.NONE ? null : TuyaCloud.degrees(target, t.set, t.fahrenheit, false);
        String s = temp != null && set != null && !off ? h.a.getString(R.string.thermostat_glance, temp, set) : temp != null ? temp : set;
        String more = off ? str(R.string.off) : mode != null && t.modeCode != null ? TuyaCloud.modeName(mode) : null;
        if (s == null) return more != null ? more : str(R.string.on);
        return more == null ? s : h.a.getString(R.string.thermostat_glance_more, s, more);
    }

    /** Lit on the Control Centre while the heating is switched on. */
    boolean heating() {
        return found == FOUND && t != null && t.online && t.switchCode != null && on;
    }

    // ---- reading and sending --------------------------------------------------------------------------------------

    /** Tuya's reading afresh, off the main thread; done (may be null) runs on the main thread once it is in. */
    private void read(Runnable done) {
        if (done != null) waiting.add(done);
        if (reading) return;
        reading = true;
        readAt = SystemClock.uptimeMillis();
        h.io.execute(() -> {
            TuyaCloud.Thermostat got = null;
            String why = null;
            try {
                got = TuyaCloud.thermostat(h.a);
            } catch (Exception e) {
                why = e instanceof IllegalStateException ? TuyaCloud.why(e.getMessage()) : "";
            }
            TuyaCloud.Thermostat fresh = got;
            String refused = why;
            h.main.post(() -> {
                reading = false;
                take(fresh, refused);
                List<Runnable> after = new ArrayList<>(waiting);
                waiting.clear();
                for (Runnable r : after) r.run();
            });
        });
    }

    /** A reading in: shown, unless a change is waiting, on its way or just taken (then what was sent stands). */
    private void take(TuyaCloud.Thermostat fresh, String why) {
        if (why != null) {
            if (t == null) {
                found = FAILED;
                failed = why.isEmpty() ? str(R.string.thermostat_unreachable) : h.a.getString(R.string.thermostat_read_refused, why);
            } else {
                note = str(R.string.thermostat_not_refreshed);
            }
            draw();
            return;
        }
        readOk = System.currentTimeMillis();
        if (fresh == null) {
            t = null;
            target = TuyaCloud.NONE;
            found = NONE_FOUND;
            draw();
            return;
        }
        boolean same = t != null && t.id.equals(fresh.id);
        if (same && keepShown(sending, pending(), sentAt, SystemClock.uptimeMillis(), KEEP_MS)) {
            fresh.target = t.target;
            fresh.on = t.on;
            fresh.mode = t.mode;
            t = fresh;
        } else {
            t = fresh;
            target = fresh.target;
            on = fresh.on;
            mode = fresh.mode;
        }
        found = FOUND;
        if (str(R.string.thermostat_not_refreshed).equals(note)) note = null;
        draw();
    }

    /**
     * Pure: a fresh reading leaves the card's target, switch and mode as they are while a change is waiting or on its
     * way, or for keepMs after Tuya took one (the device's own reading lags behind it).
     */
    static boolean keepShown(boolean sending, boolean waiting, long sentAt, long now, long keepMs) {
        return sending || waiting || (sentAt > 0 && now - sentAt < keepMs);
    }

    /**
     * Pure: how much longer to wait before the changes go to Tuya: -1 when nothing waits, 0 to send now (the remote
     * still for settleMs since its last press), else the rest of that time. One command for all the presses.
     */
    static long settleWait(boolean waiting, long lastPress, long now, long settleMs) {
        if (!waiting) return -1;
        return Math.max(0, settleMs - (now - lastPress));
    }

    /** Something changed on the card that Tuya has not been sent. */
    private boolean pending() {
        return t != null && TuyaCloud.commands(t, target, on, mode).length() > 0;
    }

    /** A press: shown at once, sent once the remote has been still a moment. */
    private void changed() {
        lastPress = SystemClock.uptimeMillis();
        note = null;
        draw();
        h.main.removeCallbacks(flush);
        h.main.postDelayed(flush, SETTLE_MS);
    }

    private void flush() {
        long wait = settleWait(pending(), lastPress, SystemClock.uptimeMillis(), SETTLE_MS);
        // Nothing to send, or a send under way (what is left goes once it is done).
        if (wait < 0 || sending) return;
        if (wait > 0) {
            h.main.postDelayed(flush, wait);
            return;
        }
        send();
    }

    /** What differs from the thermostat, in one command, off the main thread; refused, the card goes back. */
    private void send() {
        TuyaCloud.Thermostat was = t;
        if (was == null) return;
        JSONArray cmds = TuyaCloud.commands(was, target, on, mode);
        if (cmds.length() == 0) return;
        int sentTarget = target == TuyaCloud.NONE ? TuyaCloud.NONE : TuyaCloud.snap(target, was.set);
        boolean sentOn = on;
        String sentMode = mode;
        sending = true;
        h.main.removeCallbacks(flush);
        h.io.execute(() -> {
            String why = TuyaCloud.command(h.a, was.id, cmds);
            h.main.post(() -> {
                sending = false;
                if (t == null) return;
                if (why == null) {
                    // Taken: what was sent is now what the changes are measured from.
                    if (t.setCode != null && sentTarget != TuyaCloud.NONE) t.target = sentTarget;
                    if (t.switchCode != null) t.on = sentOn;
                    if (t.modeCode != null && sentMode != null) t.mode = sentMode;
                    sentAt = SystemClock.uptimeMillis();
                    // Pressed again while it went: that goes too, once the remote is still.
                    if (pending()) h.main.postDelayed(flush, SETTLE_MS);
                } else {
                    target = t.target;
                    on = t.on;
                    mode = t.mode;
                    note = why.isEmpty() ? str(R.string.thermostat_unreachable) : h.a.getString(R.string.thermostat_refused, why);
                }
                draw();
            });
        });
    }

    // ---- the card -------------------------------------------------------------------------------------------------

    private void build() {
        // Its name, and how to close it.
        LinearLayout head = new LinearLayout(h.a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        title = h.text(40, h.semi, Color.WHITE);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView close = h.text(18, h.regular, 0xFFAEB5E8);
        Keys.set(close, str(R.string.thermostat_back));
        LinearLayout.LayoutParams xl = new LinearLayout.LayoutParams(-2, -2);
        xl.leftMargin = h.px(16);
        head.addView(close, xl);
        body.addView(head);

        // THERMOSTAT, and when Tuya last said (or why it didn't).
        LinearLayout kh = new LinearLayout(h.a);
        kh.setGravity(Gravity.CENTER_VERTICAL);
        kh.addView(kicker(str(R.string.thermostat_kicker)));
        source = h.text(15, h.regular, 0x99FFFFFF);
        source.setGravity(Gravity.END);
        source.setMaxLines(2);
        source.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, -2, 1f);
        sl.leftMargin = h.px(16);
        kh.addView(source, sl);
        LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(-1, -2);
        kl.topMargin = h.px(22);
        body.addView(kh, kl);

        // Not read yet, none found, or Tuya not reachable: a line saying so in the target's place.
        message = h.text(20, h.regular, 0xD9FFFFFF);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-1, -2);
        ml.topMargin = h.px(14);
        body.addView(message, ml);

        // The target, large, with ‹ and › either side and on or off at its top right.
        dial = new FrameLayout(h.a);
        dial.setClipToOutline(true);
        dial.setOutlineProvider(h.round(24));
        LinearLayout across = new LinearLayout(h.a);
        across.setGravity(Gravity.CENTER_VERTICAL);
        across.setPadding(h.px(28), 0, h.px(28), 0);
        left = h.text(56, h.regular, Color.WHITE);
        left.setText("‹");
        across.addView(left);
        LinearLayout mid = new LinearLayout(h.a);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER_HORIZONTAL);
        mid.addView(kicker(str(R.string.thermostat_set_to)));
        big = h.text(92, h.semi, Color.WHITE);
        big.setSingleLine(true);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, -2);
        bl.topMargin = h.px(6);
        mid.addView(big, bl);
        across.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));
        right = h.text(56, h.regular, Color.WHITE);
        right.setText("›");
        across.addView(right);
        dial.addView(across, new FrameLayout.LayoutParams(-1, -1));
        pill = h.text(14, h.semi, Color.WHITE);
        pill.setLetterSpacing(0.1f);
        pill.setPadding(h.px(10), h.px(5), h.px(10), h.px(5));
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        pl.topMargin = h.px(14);
        pl.rightMargin = h.px(16);
        dial.addView(pill, pl);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, h.px(196));
        dl.topMargin = h.px(12);
        body.addView(dial, dl);

        // The room's temperature, and what it is doing.
        info = new LinearLayout(h.a);
        room = figure(info, str(R.string.thermostat_room), false);
        now = figure(info, str(R.string.thermostat_now), true);
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(-1, -2);
        il.topMargin = h.px(14);
        body.addView(info, il);

        // The modes, when it has them.
        modeHead = kicker(str(R.string.thermostat_mode));
        LinearLayout.LayoutParams mhl = new LinearLayout.LayoutParams(-2, -2);
        mhl.topMargin = h.px(22);
        body.addView(modeHead, mhl);
        modeBox = new LinearLayout(h.a);
        modeBox.setOrientation(LinearLayout.VERTICAL);
        modeBox.setClipChildren(false);
        LinearLayout.LayoutParams mbl = new LinearLayout.LayoutParams(-1, -2);
        mbl.topMargin = h.px(12);
        body.addView(modeBox, mbl);

        hint = h.text(15, h.regular, 0x8CFFFFFF);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-2, -2);
        hl.topMargin = h.px(20);
        body.addView(hint, hl);

        shownModes.clear();
        chipRows.clear();
        hintShown = null;
        row = 0;
        col = 0;
        draw();
    }

    private TextView kicker(String s) {
        TextView k = h.text(15, h.semi, 0xA6FFFFFF);
        k.setLetterSpacing(0.2f);
        k.setText(s);
        return k;
    }

    /** A box in the row under the target: its heading, and its value (returned). */
    private TextView figure(LinearLayout row, String heading, boolean second) {
        LinearLayout box = new LinearLayout(h.a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(h.px(18), h.px(14), h.px(18), h.px(14));
        box.setBackground(h.fill(18, 0x12FFFFFF));
        box.addView(kicker(heading));
        TextView v = h.text(28, h.semi, Color.WHITE);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(-1, -2);
        vl.topMargin = h.px(6);
        box.addView(v, vl);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, -2, 1f);
        if (second) bl.leftMargin = h.px(12);
        row.addView(box, bl);
        return v;
    }

    /** A chip for each mode, four to a row; built again only when its modes change. */
    private void buildModes(List<String> modes) {
        shownModes.clear();
        shownModes.addAll(modes);
        modeBox.removeAllViews();
        chipRows.clear();
        LinearLayout r = null;
        List<View> focus = null;
        for (int i = 0; i < modes.size(); i++) {
            if (i % ACROSS == 0) {
                r = new LinearLayout(h.a);
                r.setClipChildren(false);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, h.px(60));
                if (i > 0) rl.topMargin = h.px(10);
                modeBox.addView(r, rl);
                focus = new ArrayList<>();
                chipRows.add(focus);
            }
            FrameLayout chip = new FrameLayout(h.a);
            TextView name = h.text(18, h.semi, Color.WHITE);
            name.setText(TuyaCloud.modeName(modes.get(i)));
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setPadding(h.px(8), 0, h.px(8), 0);
            chip.addView(name, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
            chip.setTag(modes.get(i));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -1, 1f);
            if (i % ACROSS > 0) cl.leftMargin = h.px(10);
            r.addView(chip, cl);
            focus.add(chip);
        }
        // The last row filled out, so its chips keep their width.
        for (int i = modes.size() % ACROSS; r != null && i > 0 && i < ACROSS; i++) {
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, -1, 1f);
            sp.leftMargin = h.px(10);
            r.addView(new View(h.a), sp);
        }
    }

    /** Everything on the card from what is known now: the target, on or off, the room, the modes, the note. */
    private void draw() {
        if (!open) return;
        boolean ok = found == FOUND && t != null;
        title.setText(ok ? t.name : str(R.string.thermostat));
        message.setVisibility(ok ? View.GONE : View.VISIBLE);
        int vis = ok ? View.VISIBLE : View.GONE;
        dial.setVisibility(vis);
        info.setVisibility(vis);
        hint.setVisibility(vis);
        rows.clear();
        if (!ok) {
            message.setText(found == READING ? str(R.string.thermostat_reading)
                    : found == NONE_FOUND ? str(TuyaCloud.devicesSeen == 0 ? R.string.thermostat_no_devices : R.string.thermostat_none)
                    : failed != null ? failed : str(R.string.thermostat_unreachable));
            modeHead.setVisibility(View.GONE);
            modeBox.setVisibility(View.GONE);
            source.setText("");
            return;
        }
        boolean modes = t.modeCode != null && t.modes.size() >= 2;
        List<String> want = modes ? t.modes : Collections.emptyList();
        if (!want.equals(shownModes)) buildModes(want);
        modeHead.setVisibility(modes ? View.VISIBLE : View.GONE);
        modeBox.setVisibility(modes ? View.VISIBLE : View.GONE);
        rows.add(Collections.singletonList(dial));
        if (modes) rows.addAll(chipRows);
        row = Math.max(0, Math.min(row, rows.size() - 1));

        // The target: warm while the heating is on, glass when off, faded offline.
        big.setText(TuyaCloud.degrees(target, t.set, t.fahrenheit, false));
        boolean warm = on && t.online;
        GradientDrawable g;
        if (warm) {
            g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{WARM, WARM_DEEP});
            g.setCornerRadius(h.px(24));
        } else {
            g = h.fill(24, t.online ? 0x1AFFFFFF : 0x0AFFFFFF);
            g.setStroke(Math.max(1, h.px(1.5f)), 0x47FFFFFF);
        }
        dial.setBackground(g);
        dial.setAlpha(t.online ? 1f : 0.55f);
        boolean known = target != TuyaCloud.NONE && t.setCode != null;
        left.setAlpha(known && target > t.set.min ? 1f : 0.3f);
        right.setAlpha(known && target < t.set.max ? 1f : 0.3f);
        pill.setVisibility(t.switchCode == null ? View.GONE : View.VISIBLE);
        pill.setText(str(on ? R.string.on : R.string.off).toUpperCase(Locale.getDefault()));
        pill.setBackground(h.fill(8, on ? 0x40FFFFFF : 0x1FFFFFFF));

        room.setText(TuyaCloud.degrees(t.temp, t.room, t.fahrenheit, true));
        now.setText(doing());
        for (List<View> r : chipRows) {
            for (View v : r) {
                boolean picked = v.getTag().equals(mode);
                GradientDrawable cg = h.fill(18, picked ? WARM : 0x1AFFFFFF);
                if (!picked) cg.setStroke(Math.max(1, h.px(1.5f)), 0x33FFFFFF);
                v.setBackground(cg);
                ((TextView) ((FrameLayout) v).getChildAt(0)).setTextColor(picked ? WARM_INK : Color.WHITE);
            }
        }
        source.setText(note != null ? note : readOk > 0
                ? h.a.getString(R.string.thermostat_updated, new java.text.SimpleDateFormat("HH:mm", Locale.UK).format(new java.util.Date(readOk)))
                : "");

        List<String> parts = new ArrayList<>();
        if (t.setCode != null) parts.add(str(modes ? R.string.thermostat_hint_target : R.string.thermostat_hint_target_all));
        if (t.switchCode != null) parts.add(str(R.string.thermostat_hint_power));
        if (modes) parts.add(str(R.string.thermostat_hint_mode));
        String words = TextUtils.join("  ·  ", parts);
        if (!words.equals(hintShown)) {
            hintShown = words;
            Keys.set(hint, words);
        }
        paint();
    }

    /** What it is doing: off, offline, heating, idle, its valve open or closed, else on. */
    private String doing() {
        if (!t.online) return str(R.string.offline);
        if (t.switchCode != null && !on) return str(R.string.off);
        String k = TuyaCloud.stateKind(t.state);
        if ("heating".equals(k)) return str(R.string.thermostat_heating);
        if ("idle".equals(k)) return str(R.string.thermostat_idle);
        if ("open".equals(k)) return str(R.string.thermostat_valve_open);
        if ("closed".equals(k)) return str(R.string.thermostat_valve_closed);
        if (t.state != null && !t.state.isEmpty()) return TuyaCloud.modeName(t.state);
        return str(R.string.on);
    }

    // ---- the remote -----------------------------------------------------------------------------------------------

    /** Back closes; on the target Left and Right change it and OK switches; Down to the modes, OK picks one. */
    boolean key(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (e.getAction() == KeyEvent.ACTION_UP) hide();
            return true;
        }
        if (code == KeyEvent.KEYCODE_HOME) return false;
        if (rows.isEmpty()) return true;
        View cur = current();
        boolean onDial = cur == dial;
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) {
                if (onDial) power();
                else if (cur != null && cur.getTag() instanceof String) pickMode((String) cur.getTag());
            }
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        // Without modes Up and Down change the target as well.
        boolean only = rows.size() == 1;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (onDial && only) step(1);
                else if (row > 0) row--;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (onDial && only) step(-1);
                else if (row < rows.size() - 1) {
                    row++;
                    // Onto the modes at the one it is in.
                    if (onDial) col = Math.max(0, rows.get(row).indexOf(chipFor(mode, rows.get(row))));
                }
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (onDial) step(-1);
                else if (col > 0) col--;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (onDial) step(1);
                else if (col < rows.get(row).size() - 1) col++;
                break;
            default:
                return true;
        }
        paint();
        return true;
    }

    private static View chipFor(String mode, List<View> row) {
        for (View v : row) if (v.getTag() != null && v.getTag().equals(mode)) return v;
        return null;
    }

    /** The target a step up or down (the device's own step), kept in its range. */
    private void step(int by) {
        if (t == null || !t.online || t.setCode == null) return;
        int base = target != TuyaCloud.NONE ? target : t.target;
        int to = TuyaCloud.nudge(base, by, t.set);
        if (to == TuyaCloud.NONE || to == target) return;
        target = to;
        changed();
    }

    private void power() {
        if (t == null || !t.online) return;
        if (t.switchCode == null) {
            note = str(R.string.thermostat_no_switch);
            draw();
            return;
        }
        on = !on;
        changed();
    }

    private void pickMode(String m) {
        if (t == null || !t.online || m.equals(mode)) return;
        mode = m;
        changed();
    }

    private View current() {
        if (row < 0 || row >= rows.size()) return null;
        List<View> r = rows.get(row);
        if (r.isEmpty()) return null;
        col = Math.max(0, Math.min(col, r.size() - 1));
        return r.get(col);
    }

    /** The focus: a white ring, a little larger (the target only a touch, being large). */
    private void paint() {
        View cur = current();
        for (List<View> r : rows) {
            for (View v : r) {
                boolean f = v == cur;
                ((FrameLayout) v).setForeground(f ? h.ring(v == dial ? 24 : 18) : null);
                float s = f ? (v == dial ? 1.03f : 1.05f) : 1f;
                v.animate().scaleX(s).scaleY(s).setDuration(150).setInterpolator(HomeV2.SKY).start();
            }
        }
    }

    private String str(int id) {
        return h.a.getString(id);
    }
}
