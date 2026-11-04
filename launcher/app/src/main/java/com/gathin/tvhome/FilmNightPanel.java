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

import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Film night's settings, opened from a film's panel (TitlePanel) in the place of its saga and cast, as the mock-up
 * draws it: four start times with each one's end (amber when it ends within ten minutes of bed or after it), the
 * evening from 19:00 (or now) to bed with the film's block on it and the time to spare, three switches (the lights dim
 * as it starts, when Tuya's lights are set up; a reminder ten minutes before; pop-ups quiet until it ends) and Set, or
 * Cancel for a night already planned. Up and Down between the rows (Up past the times back to the panel's buttons),
 * Left and Right along them, OK switches or acts, Back closes.
 */
final class FilmNightPanel {
    private static final int PINK = 0xFFFF4FA3, PINK_LIGHT = 0xFFFFB3D6, AMBER = 0xFFFFCF8A, SOFT = 0xFFD4D7EE, NAVY = 0xFF10123A;
    private static final int W = 880, TRACK = 828;

    private final HomeV2 h;
    private final Context c;
    private final Typeface heavy, semi, regular;
    private final PagesV2.Title t;
    private final int runtime;
    private final String[] where;
    private final Consumer<FilmNight.Plan> done;
    private final Runnable closed;
    private final FilmNight.Plan had;
    private final List<LocalDateTime> times;
    private final boolean lights;
    private LinearLayout panel;
    private boolean open;
    private int row, col, chosen;
    private boolean dim, remind = true, quiet = true;
    private final List<View> timeViews = new ArrayList<>();
    private final List<TextView[]> timeWords = new ArrayList<>();
    /** The switch rows in order: their kind ("dim", "remind", "quiet"), view and switch. */
    private final List<Object[]> switches = new ArrayList<>();
    private final List<LinearLayout> buttons = new ArrayList<>();
    private final List<Runnable> acts = new ArrayList<>();
    private FrameLayout track;
    private View block, bedMark;
    private TextView startLabel, endLabel, spareLabel, bedLabel, fromLabel;

    FilmNightPanel(HomeV2 h, PagesV2.Title t, int runtime, JSONObject gb, Consumer<FilmNight.Plan> done, Runnable closed) {
        this.h = h;
        c = h.a;
        this.t = t;
        this.runtime = runtime > 0 ? runtime : 120;
        where = FilmNight.where(h, gb);
        this.done = done;
        this.closed = closed;
        heavy = c.getResources().getFont(R.font.outfit_extrabold);
        semi = c.getResources().getFont(R.font.inter_semibold);
        regular = c.getResources().getFont(R.font.inter_regular);
        had = FilmNight.forFilm(c, t.id);
        lights = TuyaCloud.configured(c);
        times = FilmNightPlan.choices(LocalDateTime.now());
        // A night already planned for this film: its time first among the choices when it is still to come.
        if (had != null) {
            LocalDateTime at = FilmNight.local(had.start);
            if (!times.contains(at) && at.isAfter(LocalDateTime.now())) { times.add(0, at); times.remove(times.size() - 1); }
            chosen = Math.max(0, times.indexOf(at));
            dim = had.dim;
            remind = had.remind;
            quiet = had.quiet;
        } else {
            chosen = 0;
            dim = lights;
        }
    }

    boolean isOpen() {
        return open;
    }

    /** Builds the panel into parent at (x, y) in design pixels. */
    void show(FrameLayout parent, int x, int y) {
        open = true;
        panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(h.px(26), h.px(22), h.px(26), h.px(24));
        GradientDrawable g = h.fill(18, 0x9E242064);
        g.setStroke(Math.max(1, h.px(1.5f)), 0x38FFFFFF);
        panel.setBackground(g);
        panel.setElevation(0);

        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = h.text(14, semi, PINK_LIGHT);
        name.setText("FILM NIGHT  ·  TONIGHT");
        name.setLetterSpacing(0.18f);
        head.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView bed = h.text(15, regular, SOFT);
        bed.setText("Bed at " + bedClock());
        head.addView(bed);
        panel.addView(head);

        LinearLayout row1 = new LinearLayout(c);
        for (int i = 0; i < times.size(); i++) {
            LinearLayout tile = new LinearLayout(c);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);
            TextView big = h.text(26, heavy, Color.WHITE);
            big.setText(times.get(i).format(DateTimeFormatter.ofPattern("HH:mm", Locale.UK)));
            tile.addView(big);
            TextView end = h.text(14, regular, SOFT);
            LocalDateTime e = times.get(i).plusMinutes(runtime);
            end.setText("ends " + e.format(DateTimeFormatter.ofPattern("HH:mm", Locale.UK)));
            tile.addView(end);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, h.px(74), 1f);
            if (i > 0) tl.leftMargin = h.px(12);
            row1.addView(tile, tl);
            timeViews.add(tile);
            timeWords.add(new TextView[]{big, end});
        }
        LinearLayout.LayoutParams r1 = new LinearLayout.LayoutParams(-1, -2);
        r1.topMargin = h.px(18);
        panel.addView(row1, r1);

        // The evening: from 19:00 (or now) to bed, the film's block on it.
        track = new FrameLayout(c);
        track.setBackground(h.fill(8, 0x1FFFFFFF));
        block = new View(c);
        block.setBackground(h.fill(8, PINK));
        track.addView(block, new FrameLayout.LayoutParams(0, h.px(16)));
        bedMark = new View(c);
        bedMark.setBackgroundColor(AMBER);
        track.addView(bedMark, new FrameLayout.LayoutParams(h.px(3), h.px(16)));
        LinearLayout.LayoutParams trl = new LinearLayout.LayoutParams(h.px(TRACK), h.px(16));
        trl.topMargin = h.px(20);
        panel.addView(track, trl);
        FrameLayout labels = new FrameLayout(c);
        fromLabel = label(labels, SOFT, false);
        startLabel = label(labels, SOFT, false);
        endLabel = label(labels, SOFT, false);
        spareLabel = label(labels, Color.WHITE, true);
        bedLabel = label(labels, AMBER, false);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(h.px(TRACK), h.px(22));
        ll.topMargin = h.px(8);
        panel.addView(labels, ll);

        if (lights) addSwitch("dim", "Dim the lights as it starts", "The room's lights, as Cinema mode does");
        addSwitch("remind", "Remind me 10 minutes before", "On Home and over apps");
        addSwitch("quiet", "Quiet pop-ups until it ends", "Your clubs' goals still show");

        LinearLayout bar = new LinearLayout(c);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        addButton(bar, had != null ? "Save film night" : "Set film night", true, this::save);
        if (had != null) addButton(bar, "Cancel film night", false, () -> {
            FilmNight.set(c, null);
            done.accept(null);
            close();
        });
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, h.px(56));
        bl.topMargin = h.px(14);
        panel.addView(bar, bl);
        FilmNight.Plan other = FilmNight.get(c);
        String note = where == null ? t.title + " isn't on this TV's apps: the reminder will just say when it starts"
                : other != null && other.id != t.id && other.end() > System.currentTimeMillis()
                ? "Replaces " + other.title + " at " + FilmNight.clock(other.start)
                : "OK on the reminder opens it in " + (where[2] != null ? where[2] : "its app");
        TextView n = h.text(14, regular, SOFT);
        n.setText(note);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-2, -2);
        nl.topMargin = h.px(10);
        panel.addView(n, nl);

        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(h.px(W), -2);
        pl.leftMargin = h.px(x);
        pl.topMargin = h.px(y);
        parent.addView(panel, pl);
        panel.setAlpha(0f);
        panel.setTranslationY(h.px(24));
        panel.animate().alpha(1f).translationY(0).setDuration(240).setInterpolator(Fluid.EASE).start();
        row = 0;
        col = chosen;
        paint();
    }

    private TextView label(FrameLayout into, int colour, boolean bold) {
        TextView v = h.text(14, bold ? semi : regular, colour);
        v.setSingleLine(true);
        into.addView(v, new FrameLayout.LayoutParams(-2, -2));
        return v;
    }

    private void addSwitch(String kind, String words, String detail) {
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(h.px(12), 0, h.px(14), 0);
        FrameLayout sw = new FrameLayout(c);
        View knob = new View(c);
        knob.setBackground(h.fill(12, Color.WHITE));
        sw.addView(knob, new FrameLayout.LayoutParams(h.px(24), h.px(24), Gravity.CENTER_VERTICAL));
        r.addView(sw, new LinearLayout.LayoutParams(h.px(52), h.px(30)));
        TextView w = h.text(19, semi, Color.WHITE);
        w.setText(words);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, -2, 1f);
        wl.leftMargin = h.px(16);
        r.addView(w, wl);
        TextView d = h.text(15, regular, SOFT);
        d.setText(detail);
        r.addView(d);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, h.px(54));
        rl.topMargin = h.px(switches.isEmpty() ? 14 : 4);
        panel.addView(r, rl);
        switches.add(new Object[]{kind, r, sw, knob});
    }

    private void addButton(LinearLayout bar, String words, boolean main, Runnable act) {
        LinearLayout b = MockKit.pill(h, words, 20, semi, 56, main, null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, h.px(56));
        lp.rightMargin = h.px(14);
        bar.addView(b, lp);
        buttons.add(b);
        acts.add(act);
    }

    private boolean value(String kind) {
        return "dim".equals(kind) ? dim : "remind".equals(kind) ? remind : quiet;
    }

    private void flip(String kind) {
        if ("dim".equals(kind)) dim = !dim;
        else if ("remind".equals(kind)) remind = !remind;
        else quiet = !quiet;
    }

    private String bedClock() {
        int m = FitsTonight.bedMinutes(c) % (24 * 60);
        return String.format(Locale.UK, "%02d:%02d", m / 60, m % 60);
    }

    private void save() {
        FilmNight.Plan p = new FilmNight.Plan();
        p.id = t.id;
        p.title = t.title;
        p.poster = t.poster;
        p.backdrop = t.backdrop;
        p.runtime = runtime;
        p.start = FilmNight.millis(times.get(chosen));
        p.dim = dim && lights;
        p.remind = remind;
        p.quiet = quiet;
        if (where != null) { p.app = where[0]; p.provider = where[1]; p.service = where[2]; }
        FilmNight.set(c, p);
        done.accept(p);
        close();
    }

    void close() {
        if (!open) return;
        open = false;
        View o = panel;
        o.animate().alpha(0f).translationY(h.px(16)).setDuration(170).withEndAction(() -> {
            if (o.getParent() instanceof FrameLayout) ((FrameLayout) o.getParent()).removeView(o);
        }).start();
        closed.run();
    }

    // ---- the remote -------------------------------------------------------------------------------------------------

    /** Rows: the times (0), each switch (1 to n), the buttons (n + 1). */
    private int rows() {
        return switches.size() + 2;
    }

    boolean key(KeyEvent e) {
        int code = e.getKeyCode();
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (e.getAction() == KeyEvent.ACTION_UP) close();
            return true;
        }
        if (ok) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) press();
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        int last = rows() - 1;
        if (code == KeyEvent.KEYCODE_DPAD_UP) {
            if (row == 0) { close(); return true; }
            row--;
            col = row == 0 ? chosen : 0;
        } else if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (row < last) { row++; col = row == 0 ? chosen : 0; }
        } else if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (row == 0 && chosen > 0) { chosen--; col = chosen; }
            else if (row == last && col > 0) col--;
        } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (row == 0 && chosen < times.size() - 1) { chosen++; col = chosen; }
            else if (row == last && col < buttons.size() - 1) col++;
        }
        paint();
        return true;
    }

    private void press() {
        int last = rows() - 1;
        if (row == 0) { row = last; col = 0; paint(); return; }
        if (row == last) { if (col < acts.size()) acts.get(col).run(); return; }
        flip((String) switches.get(row - 1)[0]);
        paint();
    }

    private void paint() {
        int bedMin = FitsTonight.bedMinutes(c);
        for (int i = 0; i < timeViews.size(); i++) {
            boolean on = i == chosen;
            long spare = FilmNightPlan.spare(times.get(i), runtime, bedMin);
            GradientDrawable g = h.fill(14, on ? Color.WHITE : 0x14FFFFFF);
            if (row == 0 && on) g.setStroke(h.px(3), PINK);
            timeViews.get(i).setBackground(g);
            timeWords.get(i)[0].setTextColor(on ? NAVY : Color.WHITE);
            timeWords.get(i)[1].setTextColor(FilmNightPlan.tight(spare) ? (on ? 0xFFB26A00 : AMBER) : on ? 0xFF3B3F6E : SOFT);
        }
        // The evening's bar for the time chosen.
        LocalDateTime start = times.get(chosen), now = LocalDateTime.now();
        LocalDateTime bed = FilmNightPlan.bed(start, bedMin);
        LocalDateTime from = start.toLocalDate().atTime(19, 0);
        if (start.getHour() < 5) from = from.minusDays(1);
        if (now.isAfter(from)) from = now.withSecond(0).withNano(0);
        LocalDateTime end = start.plusMinutes(runtime), to = end.isAfter(bed) ? end : bed;
        float span = Math.max(1, java.time.Duration.between(from, to).toMinutes());
        float k = h.px(TRACK) / span;
        int bx = Math.round(java.time.Duration.between(from, start).toMinutes() * k);
        int bw = Math.max(h.px(8), Math.round(runtime * k));
        FrameLayout.LayoutParams bp = (FrameLayout.LayoutParams) block.getLayoutParams();
        bp.leftMargin = bx;
        bp.width = bw;
        block.setLayoutParams(bp);
        long spare = FilmNightPlan.spare(start, runtime, bedMin);
        block.setBackground(h.fill(8, FilmNightPlan.tight(spare) ? 0xFFE07A3A : PINK));
        int bedX = Math.round(java.time.Duration.between(from, bed).toMinutes() * k);
        FrameLayout.LayoutParams mp = (FrameLayout.LayoutParams) bedMark.getLayoutParams();
        mp.leftMargin = Math.max(0, Math.min(h.px(TRACK) - h.px(3), bedX));
        bedMark.setLayoutParams(mp);
        DateTimeFormatter f = DateTimeFormatter.ofPattern("HH:mm", Locale.UK);
        place(fromLabel, from.format(f), 0, false);
        place(startLabel, start.format(f) + "  " + t.title, bx, false);
        place(endLabel, end.format(f), bx + bw, true);
        spareLabel.setTextColor(FilmNightPlan.tight(spare) ? AMBER : Color.WHITE);
        if (!end.isAfter(bed)) {
            place(spareLabel, FilmNightPlan.spareWords(spare), -1, false);
            place(bedLabel, bed.format(f) + " bed", h.px(TRACK), true);
        } else {
            // Past bed: bed's label at its mark, how far past it just before.
            place(bedLabel, bed.format(f) + " bed", bedX, true);
            spareLabel.setText(FilmNightPlan.spareWords(spare));
            spareLabel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            int at = ((FrameLayout.LayoutParams) bedLabel.getLayoutParams()).leftMargin - spareLabel.getMeasuredWidth() - h.px(18);
            place(spareLabel, spareLabel.getText().toString(), Math.max(0, at), false);
        }
        // The switches.
        for (int i = 0; i < switches.size(); i++) {
            Object[] s = switches.get(i);
            boolean v = value((String) s[0]), focus = row == i + 1;
            LinearLayout r = (LinearLayout) s[1];
            GradientDrawable rg = h.fill(12, focus ? 0x24FFFFFF : 0);
            if (focus) rg.setStroke(h.px(2), Color.WHITE);
            r.setBackground(rg);
            ((View) s[2]).setBackground(h.fill(15, v ? PINK : 0x4DFFFFFF));
            View knob = (View) s[3];
            FrameLayout.LayoutParams kl = (FrameLayout.LayoutParams) knob.getLayoutParams();
            kl.gravity = Gravity.CENTER_VERTICAL | (v ? Gravity.END : Gravity.START);
            kl.leftMargin = kl.rightMargin = h.px(3);
            knob.setLayoutParams(kl);
        }
        int last = rows() - 1;
        for (int i = 0; i < buttons.size(); i++) MockKit.paint(h, buttons.get(i), row == last && i == col, 56);
    }

    /**
     * A label under the evening's bar at x (its right edge there when alignEnd; -1: just after the end's label), kept
     * inside the bar's width.
     */
    private void place(TextView v, String words, int x, boolean alignEnd) {
        v.setText(words);
        v.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int w = v.getMeasuredWidth();
        int at;
        if (x < 0) {
            FrameLayout.LayoutParams ep = (FrameLayout.LayoutParams) endLabel.getLayoutParams();
            at = ep.leftMargin + endLabel.getMeasuredWidth() + h.px(18);
        } else at = alignEnd ? x - w : x;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
        lp.leftMargin = Math.max(0, Math.min(h.px(TRACK) - w, at));
        v.setLayoutParams(lp);
    }
}
