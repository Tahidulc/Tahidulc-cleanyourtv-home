package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Catchup;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.LogoPack;
import com.gathin.homefieldtv.engine.Reminders;
import com.gathin.homefieldtv.prefs.ChannelEdits;
import com.gathin.homefieldtv.prefs.Look;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Catch-up's archive in the 07 look (the approved still 27): a channel's programmes by day, reached from its menu in
 * the guide ("‹ Guide" in the shell's place of the tabs), over the blurred picture of the programme the remote is on.
 * At the top the channel (its logo on its tile, "104 Ember · Catch-up" and how far back its archive goes), under it the
 * days as pills (weekday over date, the day of the remote's programme white); down the left its programmes, each a row
 * 84 high on glass with its picture fitted round its faces, its time, its title and its length (the one on now with
 * "Start over" in amber, one the archive keeps with the catch-up mark, a bell on one with a reminder, one the archive no
 * longer keeps quieter), the remote's row white and lifted, the list sliding to keep it in sight; at the right the
 * programme's picture (870 x 489, faces whole) over a frosted card with its day and times, its title, two lines about it
 * and what OK does (Play from Start, Watch Live or Remind Me), My List and its description.
 *
 * <p>Up and Down go through the programmes, across the days (each day's name over its first); Right (or Up from the
 * first) goes to the days, where Left and Right go to another day and Down or OK come back. OK plays a programme already
 * shown from the archive (CatchupScreen, as the guide does), watches the one on now, and opens the small menu under one
 * to come (Remind, Record…); OK held opens the whole menu; Play/pause its description. Back goes back to the guide.
 * Built from the channel's own schedule only.
 */
final class ArchiveScreen extends Screen implements GuideMenu.Host, DialogCard.Host {
    // Design pixels (the still's .cu-*).
    static final float HEAD_Y = 136f, DAYS_Y = 252f, DAY_H = 64f, LIST_X = 90f, LIST_W = 820f, LIST_Y = 346f, ROW = 84f, PITCH = 92f,
            HEADER_H = 52f, SLOT = 4 * PITCH, PIC_X = 960f, PIC_Y = 252f, PIC_W = 870f, PIC_H = 489f, CARD_Y = 770f, CARD_H = 270f;
    static final long SCROLL_MS = 200;
    private static final int HEADER = 0, PROGRAMME = 1;

    private final Channel ch;
    private final long at;
    private Guide.Schedule s;
    /** The rows (a day's name, or a programme by its index in the schedule) and the days (their first row). */
    private final List<int[]> rows = new ArrayList<>();
    /** Each row's top in the list (design px from its first), worked out with the rows. */
    private float[] rowY = new float[0];
    private final List<Day> days = new ArrayList<>();
    private int focus = -1;
    private boolean inDays;
    private Page page;
    private GuideMenu menu;
    private DialogCard dialog;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable askPicture = () -> {
        if (page != null) {
            page.settled = true;
            page.groundNow();
            page.invalidate();
        }
    };

    /** One day of the list. */
    private static final class Day {
        final LocalDate date;
        final int firstRow;
        final String name, weekday, dayMonth, caps;

        Day(LocalDate date, int firstRow, String name, String weekday, String dayMonth, String caps) {
            this.date = date;
            this.firstRow = firstRow;
            this.name = name;
            this.weekday = weekday;
            this.dayMonth = dayMonth;
            this.caps = caps;
        }
    }

    /** c's archive, the remote on the programme on at time t (or the next one after it). */
    ArchiveScreen(TvActivity tv, Channel c, long t) {
        super(tv);
        this.ch = c;
        this.at = t;
    }

    @Override View build() {
        FrameLayout f = new FrameLayout(tv);
        page = new Page(tv);
        menu = new GuideMenu(tv);
        menu.setHost(this);
        dialog = new DialogCard(tv);
        dialog.setHost(this);
        f.addView(page, new FrameLayout.LayoutParams(-1, -1));
        f.addView(menu, new FrameLayout.LayoutParams(-1, -1));
        f.addView(dialog, new FrameLayout.LayoutParams(-1, -1));
        make(at);
        return f;
    }

    // ---- 07's shell: reached from the guide ----------------------------------------------------------------------

    @Override boolean shell() {
        return true;
    }

    @Override String backLine() {
        return "Guide";
    }

    /** The rows and days from the channel's schedule (one channel's programmes: a few days of them), the remote at t. */
    private void make(long t) {
        rows.clear();
        days.clear();
        s = Library.get(tv).schedule(ch.key);
        long now = System.currentTimeMillis();
        ZoneId z = ZoneId.systemDefault();
        LocalDate today = Instant.ofEpochMilli(now).atZone(z).toLocalDate(), last = null;
        int n = s == null ? 0 : s.start.length;
        for (int i = 0; i < n; i++) {
            LocalDate d = Instant.ofEpochMilli(s.start[i]).atZone(z).toLocalDate();
            if (!d.equals(last)) {
                String name = Ui.day(s.start[i], now);
                String weekday = d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.UK);
                String dayMonth = d.getDayOfMonth() + " " + d.getMonth().getDisplayName(TextStyle.SHORT, Locale.UK);
                boolean isToday = d.equals(today);
                String caps = (isToday ? "Today" : d.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.UK) + " " + d.getDayOfMonth() + " "
                        + d.getMonth().getDisplayName(TextStyle.FULL, Locale.UK)).toUpperCase(Locale.UK);
                days.add(new Day(d, rows.size(), isToday ? "Today" : name, isToday ? "Today" : weekday,
                        isToday ? weekday + " " + d.getDayOfMonth() : dayMonth, caps));
                rows.add(new int[]{HEADER, days.size() - 1});
                last = d;
            }
            rows.add(new int[]{PROGRAMME, i, days.size() - 1});
        }
        rowY = new float[rows.size() + 1];
        float y = 0f;
        for (int k = 0; k < rows.size(); k++) {
            rowY[k] = y;
            y += rows.get(k)[0] == HEADER ? HEADER_H : PITCH;
        }
        rowY[rows.size()] = y;
        int want = -1;
        if (n > 0) {
            int i = s.at(t);
            if (i < 0) i = s.after(t);
            if (i < 0) i = n - 1;
            want = rowOf(i);
        }
        focus = want;
        if (page != null) page.jump();
    }

    private int rowOf(int programme) {
        for (int k = 0; k < rows.size(); k++) if (rows.get(k)[0] == PROGRAMME && rows.get(k)[1] == programme) return k;
        return -1;
    }

    /** The programme the remote is on (its index in the schedule), or -1. */
    private int programme() {
        return focus >= 0 && focus < rows.size() && rows.get(focus)[0] == PROGRAMME ? rows.get(focus)[1] : -1;
    }

    private int dayOfFocus() {
        return focus >= 0 && focus < rows.size() ? rows.get(focus)[rows.get(focus)[0] == PROGRAMME ? 2 : 1] : 0;
    }

    // ---- the screen ---------------------------------------------------------------------------------------------

    @Override void shown() {
        // A full screen of its own: the stream let go, as other screens do (the guide plays it again).
        tv.live.stop();
        tv.live.place(null);
        page.invalidate();
    }

    @Override void hidden() {
        h.removeCallbacks(askPicture);
        if (menu.isOpen()) menu.close();
    }

    @Override void tick() {
        page.invalidate();
    }

    @Override void channelsChanged() {
        int i = programme();
        long t = i >= 0 && s != null ? s.start[i] : at;
        Library lib = Library.get(tv);
        if (lib.byKey(ch.key) == null) return;
        make(t);
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    private boolean asking() {
        return dialog.getVisibility() == View.VISIBLE;
    }

    @Override boolean key(int code, KeyEvent e) {
        if (asking()) return dialog.key(code);
        if (menu.isOpen()) return menu.key(code);
        boolean first = e == null || e.getRepeatCount() == 0;
        if (inDays) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    toDay(dayOfFocus() + (code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1));
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    toList();
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    return true;
                default:
                    break;
            }
        }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN: {
                int dir = code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1;
                if (!move(dir, 1) && dir < 0 && !days.isEmpty()) toDays();
                return true;
            }
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
                move(-1, 6);
                return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                move(1, 6);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (!days.isEmpty()) toDays();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return true;
            case KeyEvent.KEYCODE_MENU:
                if (first) openCard();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (first) openAbout();
                return true;
            default:
                return false;
        }
    }

    private void toDays() {
        inDays = true;
        page.invalidate();
    }

    private void toList() {
        inDays = false;
        page.invalidate();
    }

    /** Steps programmes up or down (passing the days' names), no wrap-around; false when there was none to go to. */
    private boolean move(int dir, int steps) {
        int k = focus, to = focus;
        for (int done = 0; done < steps; ) {
            k += dir;
            if (k < 0 || k >= rows.size()) break;
            if (rows.get(k)[0] == PROGRAMME) {
                to = k;
                done++;
            }
        }
        if (to != focus && to >= 0) {
            focus = to;
            page.moved();
            return true;
        }
        return false;
    }

    /** Another day: its programme on now (today), else its first. */
    private void toDay(int d) {
        if (d < 0 || d >= days.size()) return;
        long now = System.currentTimeMillis();
        int to = days.get(d).firstRow + 1;
        int i = s == null ? -1 : s.at(now);
        int on = i >= 0 ? rowOf(i) : -1;
        if (on >= 0 && rows.get(on)[2] == d) to = on;
        if (to < rows.size() && rows.get(to)[0] == PROGRAMME) {
            focus = to;
            page.moved();
        }
    }

    @Override void ok() {
        if (asking()) {
            dialog.ok();
            return;
        }
        if (menu.isOpen()) {
            menu.ok();
            return;
        }
        if (inDays) {
            toList();
            return;
        }
        int i = programme();
        if (i < 0) return;
        long now = System.currentTimeMillis();
        long a = s.start[i], b = s.stop[i];
        if (a > now) {
            openPopup();
        } else if (b > now) {
            GuideActions.watch(tv, ch);
        } else if (Catchup.has(ch, a, now)) {
            GuideActions.watchFrom(tv, ch, a, b);
        } else {
            android.widget.Toast.makeText(tv, "Your provider no longer keeps this programme", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    @Override void okLong() {
        if (asking() || menu.isOpen()) return;
        if (inDays) {
            ok();
            return;
        }
        openCard();
    }

    @Override boolean back() {
        if (asking()) {
            dialog.hide(null);
            return true;
        }
        if (menu.isOpen()) {
            menu.close();
            return true;
        }
        if (inDays) {
            toList();
            return true;
        }
        return false;
    }

    // ---- the menus ------------------------------------------------------------------------------------------------

    /** The remote's row as it is drawn (screen pixels). */
    private RectF rowRect() {
        float top = page.rowTop(focus);
        return new RectF(Ui.f(LIST_X - 12f), Ui.f(top - 4f), Ui.f(LIST_X + LIST_W + 12f), Ui.f(top + ROW + 4f));
    }

    private long[] times() {
        int i = programme();
        return i < 0 ? null : new long[]{s.start[i], s.stop[i]};
    }

    private void openPopup() {
        long[] p = times();
        if (p == null) return;
        menu.openPopup(GuideActions.popup(tv, ch, p[0], s.title[programme()]), rowRect(), LIST_X);
    }

    private void openCard() {
        menu.openCard(GuideActions.card(tv, ch, s, programme(), false), 0, rowRect());
    }

    private void openAbout() {
        long now = System.currentTimeMillis();
        int i = programme();
        String[] a = GuideActions.about(ch, s, i, now);
        float done = i >= 0 && s.start[i] <= now && now < s.stop[i] ? LiveText.progress(now, s.start[i], s.stop[i]) : -1f;
        menu.openAbout(a[0], a[1], done, a[2], a[3], rowRect());
    }

    @Override public void chosen(GuideMenu m, String tag) {
        if (GuideActions.ABOUT.equals(tag)) {
            openAbout();
            return;
        }
        int i = programme();
        String title = i >= 0 ? s.title[i] : ChannelEdits.name(ch), desc = i >= 0 ? s.desc[i] : "";
        menu.close();
        boolean ask = GuideActions.act(tv, tag, ch, title, desc, times());
        page.invalidate();
        if (ask && tv.isTop(this)) GuideActions.ask(tv, dialog);
    }

    @Override public void answered(DialogCard d, RowList.Row answer) {
        d.hide(null);
        GuideActions.answered(tv, answer);
    }

    // ---- the page -----------------------------------------------------------------------------------------------

    /** The whole page in one view: the ground, the channel, the days, the programmes, the picture and the card. */
    private final class Page extends View {
        private final Ground ground = new Ground(this);
        private final Frost card = new Frost(Look07.MAT), glass = new Frost(Look07.GLASS);
        private final PicTile thumbs = new PicTile(this), picture = new PicTile(this);
        private final Lift07 lift = new Lift07();
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint nameP = Type.paint(Type.TITLE40, Type.BOLD, Look07.L1), subP = Type.paint(21f, Type.MEDIUM, Look07.L2);
        private final TextPaint dayP = Type.paint(21f, Type.MEDIUM, Look07.L2), dateP = Type.paint(15f, Type.SEMIBOLD, Look07.L3);
        private final TextPaint timeP = Type.clock(23f, Type.BOLD, Look07.L1), titleP = Type.paint(23f, Type.SEMIBOLD, Look07.L1);
        private final TextPaint lenP = Type.paint(17f, Type.MEDIUM, Look07.L3), markP = Type.paint(16f, Type.SEMIBOLD, Look07.AMBER);
        private final TextPaint headP = Type.label(Type.LABEL, Look07.L3), kickP = Type.label(16f, Look07.L3);
        private final TextPaint cardTitleP = Type.paint(36f, Type.BOLD, Look07.L1), cardP = Type.paint(19f, Type.REGULAR, Look07.L2);
        private final TextFit titles = new TextFit(titleP, 128), lens = new TextFit(lenP, 128), cardTitles = new TextFit(cardTitleP, 32);
        private final RectF r = new RectF(), t = new RectF();
        private final Runnable redraw = this::invalidate;
        /** Each row's second line, kept by row and minute (nothing made a frame). */
        private String[] lenText = new String[0];
        private long[] lenMinute = new long[0];
        /** The list's scroll (design px) and the days', gliding on the display's frame clock. */
        private final Glide listGlide = new Glide(SCROLL_MS), dayGlide = new Glide(SCROLL_MS);
        boolean settled;
        private String cardFor = "", headFor = "", headText = "", subText = "";
        private StaticLayout cardDesc;
        private String kicker = "";

        Page(Context c) {
            super(c);
            ring.setStyle(Paint.Style.STROKE);
            Shadows.prepare(Look07.SH_ROW, 24f);
            Shadows.prepare(Look07.SH_PANEL, Look07.R_PANEL);
        }

        /** The list's scroll that keeps row k in sight, at most four rows down (design px). */
        private float scrollFor(int k) {
            return k < 0 || k >= rowY.length ? 0f : Math.max(0f, rowY[k] - SLOT);
        }

        /** Row k's top on the screen now (design px). */
        float rowTop(int k) {
            return k < 0 || k >= rowY.length ? LIST_Y : LIST_Y + rowY[k] - listGlide.value();
        }

        /** In place at once (opening, the lists made again). */
        void jump() {
            lenText = new String[0];
            listGlide.jump(scrollFor(focus));
            dayGlide.jump(0f);
            settled = Exec.inline;
            groundNow();
            h.removeCallbacks(askPicture);
            if (!Exec.inline) h.postDelayed(askPicture, ProgrammePictures.REST_MS);
            invalidate();
        }

        /** The remote moved: the list slides to it; the pictures asked for once it rests. */
        void moved() {
            listGlide.to(scrollFor(focus));
            settled = Exec.inline;
            h.removeCallbacks(askPicture);
            if (!Exec.inline) h.postDelayed(askPicture, ProgrammePictures.REST_MS);
            invalidate();
        }

        /** The ground: the programme's own picture once known, else the channel's logo. */
        void groundNow() {
            int i = programme();
            String pic = i < 0 ? "" : settled && !Exec.inline ? ProgrammePictures.get(getContext(), ch, s, i, () -> {
                groundNow();
                invalidate();
            }) : ProgrammePictures.peek(ch, s, i);
            ground.set(pic != null && !pic.isEmpty() ? pic : LogoPack.url(ch));
        }

        @Override protected void onDraw(Canvas c) {
            long now = System.currentTimeMillis();
            boolean moving = ground.draw(c, getWidth(), getHeight());
            moving |= listGlide.step() | dayGlide.step();
            int i = programme();
            moving |= lift.step(inDays ? null : i >= 0 ? rows.get(focus) : null);
            drawHead(c);
            if (rows.isEmpty()) {
                r.set(0, Ui.f(LIST_Y), Ui.f(PIC_X - 20f), getHeight());
                EmptyState.draw(c, r, Dark.TV, 96f, "No programmes", "The guide has nothing for this channel yet");
            } else {
                drawDays(c);
                drawList(c, now);
                if (i >= 0) drawDetail(c, i, now);
            }
            if (moving) postInvalidateOnAnimation();
        }

        /** The channel: its logo on its tile, "104 Ember · Catch-up", how far back its archive goes. */
        private void drawHead(Canvas c) {
            r.set(Ui.f(LIST_X), Ui.f(HEAD_Y), Ui.f(LIST_X + 132f), Ui.f(HEAD_Y + 88f));
            if (!Logos.draw(c, ch, r, redraw)) Icons07.draw(c, Icons07.TV, r.centerX(), r.centerY(), Ui.f(40f), Look07.L3);
            String key = ch.key + (Look.numbers ? "|n" : "|");
            float tx = Ui.f(LIST_X + 132f + 22f);
            if (!key.equals(headFor)) {
                headFor = key;
                int n = Library.get(getContext()).number(ch);
                headText = Ui.fit((Look.numbers && n > 0 ? n + " " : "") + ChannelEdits.name(ch) + " · Catch-up", nameP, Ui.f(PIC_X + PIC_W) - tx);
                Channel kept = Catchup.source(ch);
                subText = kept == null ? "No catch-up for this channel"
                        : "Keeps programmes for " + kept.archiveDays + (kept.archiveDays == 1 ? " day" : " days") + " · OK plays one from its start";
            }
            c.drawText(headText, tx, Ui.f(HEAD_Y + 44f), nameP);
            Icons07.draw(c, Icons07.CATCHUP, tx + Ui.f(10f), Ui.f(HEAD_Y + 72f), Ui.f(20f), Look07.L2);
            c.drawText(subText, tx + Ui.f(28f), Ui.f(HEAD_Y + 79f), subP);
        }

        /** The days as pills: weekday over date, the remote's day white (lifted while the remote is on them). */
        private void drawDays(Canvas c) {
            int sel = dayOfFocus();
            float x = Ui.f(LIST_X), cy = Ui.f(DAYS_Y + DAY_H / 2f), hh = Ui.f(DAY_H) / 2f, pad = Ui.f(22f);
            // Only the days that fit in the list's width, ending with the remote's.
            int from = 0;
            float total = 0f;
            for (int d = sel; d >= 0; d--) {
                total += pillW(days.get(d), pad) + Ui.f(8f);
                if (total > Ui.f(LIST_W + 20f)) break;
                from = d;
            }
            for (int d = from; d < days.size(); d++) {
                Day day = days.get(d);
                float w = pillW(day, pad);
                if (x + w > Ui.f(LIST_X + LIST_W + 40f)) break;
                r.set(x, cy - hh, x + w, cy + hh);
                boolean on = d == sel;
                if (on) {
                    if (inDays) {
                        c.save();
                        c.scale(1.05f, 1.05f, r.centerX(), r.centerY());
                        Shadows.draw(c, r, hh, Look07.SH_ROW, 1f);
                    }
                    fill.setColor(Look07.FOCUS_FILL);
                    c.drawRoundRect(r, hh, hh, fill);
                } else {
                    glass.draw(c, r, hh, 1f);
                }
                dayP.setColor(on ? Look07.INK : Look07.L2);
                dateP.setColor(on ? Look07.INK2 : Look07.L3);
                float mid = r.centerX();
                c.drawText(day.weekday, mid - dayP.measureText(day.weekday) / 2f, cy - Ui.f(2f), dayP);
                c.drawText(day.dayMonth, mid - dateP.measureText(day.dayMonth) / 2f, cy + Ui.f(19f), dateP);
                if (on && inDays) c.restore();
                x += w + Ui.f(8f);
            }
        }

        private float pillW(Day d, float pad) {
            return Math.max(dayP.measureText(d.weekday), dateP.measureText(d.dayMonth)) + pad * 2f;
        }

        /** The programmes, each a row on glass; the remote's white and lifted; each day's name over its first. */
        private void drawList(Canvas c, long now) {
            c.save();
            c.clipRect(0, Ui.f(LIST_Y - 8f), Ui.f(PIC_X - 20f), getHeight());
            int nowAt = s == null ? -1 : s.at(now);
            long kept = Catchup.earliest(ch, now);
            boolean bells = Reminders.any(getContext());
            for (int k = 0; k < rows.size(); k++) {
                float top = rowTop(k);
                if (top > 1080f) break;
                if (top + PITCH < LIST_Y - 8f) continue;
                int[] row = rows.get(k);
                if (row[0] == HEADER) {
                    c.drawText(days.get(row[1]).caps, Ui.f(LIST_X + 12f), Ui.f(top + 34f), headP);
                    continue;
                }
                if (k != focus) drawRow(c, k, row, top, now, nowAt, kept, bells);
            }
            if (focus >= 0 && rows.get(focus)[0] == PROGRAMME) drawRow(c, focus, rows.get(focus), rowTop(focus), now, nowAt, kept, bells);
            c.restore();
        }

        private void drawRow(Canvas c, int k, int[] row, float top, long now, int nowAt, long kept, boolean bells) {
            int i = row[1];
            boolean here = k == focus;
            float up = here ? Math.max(0f, lift.of(row)) : 0f;
            boolean white = here && !inDays;
            r.set(Ui.f(LIST_X), Ui.f(top), Ui.f(LIST_X + LIST_W), Ui.f(top + ROW));
            float rad = Ui.f(24f);
            c.save();
            if (white) {
                float g = 1f + 0.025f * Math.min(1.1f, up);
                c.scale(g, g, r.centerX(), r.centerY());
                Shadows.draw(c, r, rad, Look07.SH_ROW, Math.min(1f, up));
                fill.setColor(Look07.FOCUS_FILL);
                c.drawRoundRect(r, rad, rad, fill);
            } else if (here) {
                fill.setColor(Look07.SELECTED);
                c.drawRoundRect(r, rad, rad, fill);
            } else {
                glass.draw(c, r, rad, 1f);
            }
            boolean over = s.stop[i] <= now, on = i == nowAt, gone = over && (s.start[i] < kept || Catchup.source(ch) == null);
            // Its picture, fitted round its faces (or the channel's logo).
            float cy = r.centerY();
            t.set(r.left + Ui.f(12f), cy - Ui.f(30f), r.left + Ui.f(12f + 107f), cy + Ui.f(30f));
            thumbs.draw(c, t, Ui.f(12f), ch, s, i, PicTile.COVER, true, gone, settled);
            // Its time, title and length; its mark at the right.
            int ink = white ? Look07.INK : gone ? Look07.L3 : Look07.L1;
            timeP.setColor(ink);
            c.drawText(Words.hm(s.start[i]), r.left + Ui.f(137f), Type.centred(timeP, cy), timeP);
            float tx = r.left + Ui.f(225f), right = r.right - Ui.f(20f);
            float markW = 0f;
            if (on) {
                markP.setColor(white ? Look07.INK : Look07.AMBER);
                markW = Ui.f(18f + 8f) + markP.measureText("Start over");
                Icons07.draw(c, Icons07.PLAY, right - markW + Ui.f(9f), cy, Ui.f(18f), white ? Look07.INK : Look07.AMBER);
                c.drawText("Start over", right - markP.measureText("Start over"), Type.centred(markP, cy), markP);
            } else if (over && !gone) {
                markW = Ui.f(20f);
                Icons07.draw(c, Icons07.CATCHUP, right - Ui.f(10f), cy, Ui.f(20f), white ? Look07.INK2 : Look07.L3);
            } else if (bells && s.start[i] > now && Reminders.has(getContext(), ch.key, s.start[i])) {
                markW = Ui.f(20f);
                Icons07.draw(c, Icons07.BELL, right - Ui.f(10f), cy, Ui.f(20f), white ? Look07.INK : Look07.AMBER);
            }
            float room = right - markW - Ui.f(16f) - tx;
            titleP.setColor(ink);
            titles.line(c, s.title[i], tx, cy - Ui.f(4f), room);
            lenP.setColor(white ? Look07.INK2 : Look07.L3);
            lens.line(c, lengthWords(k, i, now, on), tx, cy + Ui.f(21f), room);
            c.restore();
        }

        /** "30m", "On now · 1h 00m left": a row's second line (kept for its row, made again each minute on now). */
        private String lengthWords(int k, int i, long now, boolean on) {
            if (lenText.length != rows.size()) {
                lenText = new String[rows.size()];
                lenMinute = new long[rows.size()];
            }
            long minute = on ? now / 60_000L : -1L;
            if (lenText[k] != null && lenMinute[k] == minute) return lenText[k];
            String w;
            if (on) {
                w = "On now · " + GuideGrid.leftWords((int) Math.max(1L, (s.stop[i] - now + 59_999L) / 60_000L));
            } else {
                long m = Math.max(1L, (s.stop[i] - s.start[i] + 30_000L) / 60_000L);
                w = m >= 60 ? m / 60 + "h" + (m % 60 > 0 ? " " + m % 60 + "m" : "") : m + "m";
            }
            lenText[k] = w;
            lenMinute[k] = minute;
            return w;
        }

        /** The programme's picture (faces whole) over its frosted card: its day and times, title, two lines and what OK does. */
        private void drawDetail(Canvas c, int i, long now) {
            r.set(Ui.f(PIC_X), Ui.f(PIC_Y), Ui.f(PIC_X + PIC_W), Ui.f(PIC_Y + PIC_H));
            float rad = Ui.f(Look07.R_PANEL);
            Shadows.draw(c, r, rad, Look07.SH_PANEL, 1.2f);
            picture.draw(c, r, rad, ch, s, i, PicTile.COVER, true, false, settled);
            float sw = Math.max(1f, Ui.f(1f));
            ring.setStrokeWidth(sw);
            ring.setColor(0x24FFFFFF);
            c.drawRoundRect(r.left + sw / 2f, r.top + sw / 2f, r.right - sw / 2f, r.bottom - sw / 2f, rad, rad, ring);

            r.set(Ui.f(PIC_X), Ui.f(CARD_Y), Ui.f(PIC_X + PIC_W), Ui.f(CARD_Y + CARD_H));
            card.draw(c, r, rad, 1f);
            String key = ch.key + "|" + s.start[i];
            if (!key.equals(cardFor)) {
                cardFor = key;
                String day = Ui.day(s.start[i], now);
                kicker = (day + " · " + LiveText.hm(s.start[i]) + " – " + LiveText.hm(s.stop[i])).toUpperCase(Locale.UK);
                cardDesc = s.desc[i].isEmpty() ? null : StaticLayout.Builder.obtain(s.desc[i], 0, s.desc[i].length(), cardP, Ui.px(PIC_W - 68f))
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.4f / 1.21f).setIncludePad(false).setMaxLines(2)
                        .setEllipsize(TextUtils.TruncateAt.END).build();
            }
            float tx = r.left + Ui.f(34f);
            c.drawText(kicker, tx, r.top + Ui.f(28f + 15f), kickP);
            cardTitles.line(c, s.title[i], tx, r.top + Ui.f(28f + 23f + 8f + 35f), r.width() - Ui.f(68f));
            if (cardDesc != null) Ui.draw(c, cardDesc, tx, r.top + Ui.f(28f + 23f + 8f + 44f + 6f));
            // What OK does, My List, its description.
            boolean over = s.stop[i] <= now, on = s.start[i] <= now && !over;
            String main = on ? "Watch Live" : over ? "Play from Start" : "Remind Me";
            int icon = on ? Icons07.PLAY : over ? Icons07.RESTART : Icons07.BELL;
            float bcy = r.bottom - Ui.f(26f + 35f), bx = tx;
            bx += Pills.button(c, bx, bcy, main, icon, !inDays, false) + Ui.f(14f);
            bx += Pills.button(c, bx, bcy, "My List", Icons07.PLUS, false, false) + Ui.f(14f);
            Pills.circle(c, bx + Ui.f(35f), bcy, Icons07.INFO, false, false);
        }
    }
}
