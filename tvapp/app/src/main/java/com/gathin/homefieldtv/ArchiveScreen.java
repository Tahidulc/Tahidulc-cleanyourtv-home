package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
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
import com.gathin.homefieldtv.engine.Reminders;
import com.gathin.homefieldtv.prefs.Categories;
import com.gathin.homefieldtv.prefs.Look;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Catch-up's archive: a channel's programmes by day, as TiviMate 5 shows them (its public picture, SPEC.md §2.5, in the
 * family's colours and focus). Along the top the channel (its logo on its tile, its number and name, how far back its
 * archive goes) and the date and the clock at the right. Down the left its programmes, each day under its name in the
 * light accent, a row 75 high with the time and the title, the remote's row always at the same height in the focus
 * pill with a ▶ on one that plays, the list sliding under it and fading at its ends (the programme on now in the light
 * accent, one the archive no longer keeps greyed, a bell on one with a reminder). Beside them the days, weekday over
 * date, today's in the light accent and the remote's day in a pill level with its programme. At the right the
 * programme's picture (Sky's, else the channel's logo) over a card with its title, its day and times and what it is
 * about.
 *
 * <p>Up and Down go through the programmes, across the days; Right goes to the days and Left (or OK) back, Up and Down
 * there going to another day. OK plays a programme already shown from the archive (CatchupScreen, as the guide does),
 * watches the one on now, and opens the small menu under one to come (Remind, Record…); OK held opens the whole menu.
 * Back goes back to the guide. Built from the channel's own schedule only.
 */
final class ArchiveScreen extends Screen implements GuideMenu.Host, DialogCard.Host {
    // Design pixels.
    static final float HEAD_H = 112f, LIST_W = 960f, DAYS_X = 960f, DETAIL_X = 1146f, ROW = 75f, FOCUS_TOP = 502f, DAY_H = 96f,
            DAY_PITCH = 104f, PIC_X = 1186f, PIC_Y = 112f, PIC_W = 694f, PIC_H = 390f, CARD_Y = 526f, FADE = 130f;
    static final long SCROLL_MS = 200;
    private static final int HEADER = 0, PROGRAMME = 1;

    private final Channel ch;
    private final long at;
    private Guide.Schedule s;
    /** The rows (a day's name, or a programme by its index in the schedule) and the days (their first row). */
    private final List<int[]> rows = new ArrayList<>();
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
            page.invalidate();
        }
    };

    /** One day of the list. */
    private static final class Day {
        final LocalDate date;
        final int firstRow;
        final String name, weekday, dayMonth;

        Day(LocalDate date, int firstRow, String name, String weekday, String dayMonth) {
            this.date = date;
            this.firstRow = firstRow;
            this.name = name;
            this.weekday = weekday;
            this.dayMonth = dayMonth;
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
        // No ground of its own: the window's (TvActivity) is the screen's, so it is filled once, not twice.
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
                days.add(new Day(d, rows.size(), d.equals(today) ? "Today" : name, weekday, dayMonth));
                rows.add(new int[]{HEADER, days.size() - 1});
                last = d;
            }
            rows.add(new int[]{PROGRAMME, i, days.size() - 1});
        }
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
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN: {
                int dir = code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1;
                if (inDays) toDay(dayOfFocus() + dir);
                else move(dir, 1);
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
                if (!inDays && !days.isEmpty()) {
                    inDays = true;
                    page.invalidate();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (inDays) {
                    inDays = false;
                    page.invalidate();
                }
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

    /** Steps programmes up or down (passing the days' names), no wrap-around. */
    private void move(int dir, int steps) {
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
        }
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
            inDays = false;
            page.invalidate();
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
            inDays = false;
            page.invalidate();
            return true;
        }
        return false;
    }

    // ---- the menus ------------------------------------------------------------------------------------------------

    /** The remote's row as it is drawn (screen pixels): the focus pill's place. */
    private RectF rowRect() {
        return new RectF(Ui.f(16f), Ui.f(FOCUS_TOP), Ui.f(LIST_W - 16f), Ui.f(FOCUS_TOP + ROW));
    }

    private long[] times() {
        int i = programme();
        return i < 0 ? null : new long[]{s.start[i], s.stop[i]};
    }

    private void openPopup() {
        long[] p = times();
        if (p == null) return;
        RectF row = rowRect();
        row.left += Ui.f(3f);
        menu.openPopup(GuideActions.popup(tv, ch, p[0], s.title[programme()]), row, 16f);
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
        String title = i >= 0 ? s.title[i] : ch.name, desc = i >= 0 ? s.desc[i] : "";
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

    /** The whole page in one view: the channel, the programmes, the days, the picture and the card. */
    private final class Page extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final Paint fadeTop = new Paint(), fadeFoot = new Paint();
        private final TextPaint nameP = Type.paint(Type.ROW, Type.MEDIUM, Palette.TEXT1), subP = Type.paint(Type.SMALL, Type.REGULAR, Palette.TEXT2);
        private final TextPaint clockP = Type.paint(Type.ROW, Type.MEDIUM, Palette.ACCENT_LIGHT), headP = Type.paint(Type.SMALL, Type.MEDIUM, Palette.ACCENT_LIGHT);
        private final TextPaint timeP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT2), titleP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT2);
        private final TextPaint dayP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT2);
        private final TextPaint cardTitleP = Type.paint(Type.ITEM, Type.MEDIUM, Palette.TEXT1), cardP = Type.paint(Type.SMALL, Type.REGULAR, Palette.TEXT2);
        private final RectF r = new RectF();
        private final Path clip = new Path();
        private final Runnable redraw = this::invalidate;
        /** The list's place (rows, fractional while it slides to the remote's row) and the days' (days). */
        private float listAt, dayAt;
        boolean settled;
        private int fadeFor = -1;
        private String cardFor = "";
        private StaticLayout cardTitle, cardDesc;

        Page(Context c) {
            super(c);
        }

        /** The list's and the days' places, gliding on the display's frame clock (a held key glides on smoothly). */
        private final Glide listGlide = new Glide(SCROLL_MS, 75f), dayGlide = new Glide(SCROLL_MS, 75f);

        /** In place at once (opening, the lists made again). */
        void jump() {
            listGlide.jump(Math.max(0, focus));
            dayGlide.jump(dayOfFocus());
            listAt = listGlide.value();
            dayAt = dayGlide.value();
            settled = Exec.inline;
            h.removeCallbacks(askPicture);
            if (!Exec.inline) h.postDelayed(askPicture, ProgrammePictures.REST_MS);
            invalidate();
        }

        /** The remote moved: the list and the days slide to it; the picture asked for once it rests. */
        void moved() {
            listGlide.to(focus);
            dayGlide.to(dayOfFocus());
            listAt = listGlide.value();
            dayAt = dayGlide.value();
            settled = Exec.inline;
            h.removeCallbacks(askPicture);
            if (!Exec.inline) h.postDelayed(askPicture, ProgrammePictures.REST_MS);
            invalidate();
        }

        private boolean step() {
            boolean moving = listGlide.step() | dayGlide.step();
            listAt = listGlide.value();
            dayAt = dayGlide.value();
            return moving;
        }

        @Override protected void onDraw(Canvas c) {
            long now = System.currentTimeMillis();
            boolean moving = step();
            drawHead(c, now);
            fill.setColor(Palette.PANEL);
            c.drawRect(0, Ui.f(HEAD_H), Ui.f(DETAIL_X), getHeight(), fill);
            fill.setColor(Palette.DIVIDER);
            c.drawRect(Ui.f(DAYS_X), Ui.f(HEAD_H), Ui.f(DAYS_X + 2f), getHeight(), fill);
            if (rows.isEmpty()) {
                r.set(0, Ui.f(HEAD_H), Ui.f(LIST_W), getHeight());
                EmptyState.draw(c, r, Dark.TV, 96f, "No programmes", "The guide has nothing for this channel yet");
            } else {
                drawList(c, now);
                drawDays(c, now);
                drawFades(c);
                drawDetail(c, now);
            }
            if (moving) postInvalidateOnAnimation();
        }

        /** The channel along the top: its logo on its tile, its number and name, its archive; the date and the clock. */
        private void drawHead(Canvas c, long now) {
            fill.setColor(Palette.HEADER);
            c.drawRect(0, 0, Ui.f(DETAIL_X), Ui.f(HEAD_H), fill);
            float tx = Ui.f(32f), ty = Ui.f(20f), tw = Ui.f(108f), th = Ui.f(72f);
            Bitmap logo = ch.logo.isEmpty() ? null : Pics.get(ch.logo, Ui.px(108f), Ui.px(72f), Pics.logoMode(), redraw);
            if (logo != null) {
                c.drawBitmap(logo, tx + (tw - logo.getWidth()) / 2f, ty + (th - logo.getHeight()) / 2f, bmp);
            } else {
                r.set(tx, ty, tx + tw, ty + th);
                fill.setColor(Pics.tiles ? 0xFF000000 : Palette.SELECTED);
                c.drawRoundRect(r, Ui.f(8f), Ui.f(8f), fill);
                RoundButton.glyph(c, Dark.TV, r.centerX(), r.centerY(), Ui.f(36f), Palette.TEXT3);
            }
            Library lib = Library.get(getContext());
            String name = (Look.numbers ? lib.number(ch) + "  " : "") + ch.name;
            nameP.setColor(Palette.TEXT1);
            c.drawText(Ui.fit(name, nameP, Ui.f(DETAIL_X - 200f)), Ui.f(164f), Ui.f(54f), nameP);
            Channel kept = Catchup.source(ch);
            String sub = kept == null ? "No catch-up for this channel"
                    : "Catch-up: up to " + kept.archiveDays + (kept.archiveDays == 1 ? " day" : " days");
            subP.setColor(Palette.TEXT2);
            c.drawText(Ui.fit(sub + "  •  " + Categories.label(getContext(), ch.group), subP, Ui.f(DETAIL_X - 200f)), Ui.f(164f), Ui.f(90f), subP);
            clockP.setColor(Palette.ACCENT_LIGHT);
            String clock = Ui.dateClock(now);
            c.drawText(clock, Ui.f(PIC_X + PIC_W) - clockP.measureText(clock), Ui.f(64f), clockP);
        }

        private void drawList(Canvas c, long now) {
            c.save();
            c.clipRect(0, Ui.f(HEAD_H), Ui.f(LIST_W), getHeight());
            int nowAt = s == null ? -1 : s.at(now);
            long kept = Catchup.earliest(ch, now);
            boolean bells = Reminders.any(getContext());
            int from = Math.max(0, (int) Math.floor(listAt - (FOCUS_TOP - HEAD_H) / ROW) - 1);
            for (int k = from; k < rows.size(); k++) {
                float top = FOCUS_TOP + (k - listAt) * ROW;
                if (top > 1080f) break;
                if (top + ROW < HEAD_H) continue;
                int[] row = rows.get(k);
                float cy = Ui.f(top + ROW / 2f);
                if (row[0] == HEADER) {
                    headP.setColor(Palette.ACCENT_LIGHT);
                    c.drawText(days.get(row[1]).name, Ui.f(48f), Type.centred(headP, cy), headP);
                    continue;
                }
                int i = row[1];
                boolean here = k == focus;
                if (here) {
                    r.set(Ui.f(16f), Ui.f(top), Ui.f(LIST_W - 16f), Ui.f(top + ROW));
                    if (inDays) Focus.selected(c, r);
                    else Focus.pill(c, r);
                }
                boolean over = s.stop[i] <= now, on = i == nowAt, gone = over && s.start[i] < kept;
                int ink = here ? Palette.TEXT1 : on ? Palette.ACCENT_LIGHT : over && (gone || Catchup.source(ch) == null) ? Palette.TEXT3 : Palette.TEXT2;
                timeP.setColor(ink);
                titleP.setColor(ink);
                c.drawText(LiveText.hm(s.start[i]), Ui.f(48f), Type.centred(timeP, cy), timeP);
                boolean plays = on || over && !gone && Catchup.source(ch) != null;
                float right = LIST_W - 40f;
                if (here && plays) {
                    RoundButton.glyph(c, Dark.PLAY, Ui.f(LIST_W - 52f), cy, Ui.f(28f), Palette.TEXT1);
                    right -= 40f;
                } else if (bells && s.start[i] > now && Reminders.has(getContext(), ch.key, s.start[i])) {
                    fill.setColor(here ? Palette.TEXT1 : Palette.TEXT2);
                    Dark.bell(c, Ui.f(LIST_W - 52f), cy, Ui.f(12f), fill);
                    right -= 40f;
                }
                c.drawText(Ui.fit(s.title[i], titleP, Ui.f(right - 160f)), Ui.f(160f), Type.centred(titleP, cy), titleP);
            }
            c.restore();
        }

        /** The days, weekday over date: the remote's day in a pill level with its programme, today's in the light accent. */
        private void drawDays(Canvas c, long now) {
            c.save();
            c.clipRect(Ui.f(DAYS_X + 2f), Ui.f(HEAD_H), Ui.f(DETAIL_X), getHeight());
            LocalDate today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate();
            int sel = dayOfFocus();
            float centre = FOCUS_TOP + ROW / 2f;
            for (int d = 0; d < days.size(); d++) {
                float cy = centre + (d - dayAt) * DAY_PITCH;
                if (cy - DAY_H / 2f > 1080f || cy + DAY_H / 2f < HEAD_H) continue;
                Day day = days.get(d);
                r.set(Ui.f(DAYS_X + 18f), Ui.f(cy - DAY_H / 2f), Ui.f(DETAIL_X - 16f), Ui.f(cy + DAY_H / 2f));
                if (d == sel) {
                    if (inDays) Focus.pill(c, r);
                    else Focus.selected(c, r);
                }
                int ink = d == sel ? Palette.TEXT1 : day.date.equals(today) ? Palette.ACCENT_LIGHT : Palette.TEXT2;
                dayP.setColor(ink);
                float mx = r.centerX();
                c.drawText(day.weekday, mx - dayP.measureText(day.weekday) / 2f, Type.centred(dayP, Ui.f(cy - 17f)), dayP);
                c.drawText(day.dayMonth, mx - dayP.measureText(day.dayMonth) / 2f, Type.centred(dayP, Ui.f(cy + 17f)), dayP);
            }
            c.restore();
        }

        /** The list and the days fading into the panel at their top and foot. */
        private void drawFades(Canvas c) {
            int h = getHeight();
            if (fadeFor != h) {
                int p = Palette.PANEL | 0xFF000000;
                fadeTop.setShader(new LinearGradient(0, Ui.f(HEAD_H), 0, Ui.f(HEAD_H + FADE), p, p & 0x00FFFFFF, Shader.TileMode.CLAMP));
                fadeFoot.setShader(new LinearGradient(0, h - Ui.f(FADE), 0, h, p & 0x00FFFFFF, p, Shader.TileMode.CLAMP));
                fadeFor = h;
            }
            c.drawRect(0, Ui.f(HEAD_H), Ui.f(DETAIL_X), Ui.f(HEAD_H + FADE), fadeTop);
            c.drawRect(0, h - Ui.f(FADE), Ui.f(DETAIL_X), h, fadeFoot);
            fill.setColor(Palette.DIVIDER);
            c.drawRect(Ui.f(DAYS_X), Ui.f(HEAD_H), Ui.f(DAYS_X + 2f), h, fill);
        }

        /** The programme's picture (Sky's, else the channel's logo) over its card: title, day and times, what it is about. */
        private void drawDetail(Canvas c, long now) {
            int i = programme();
            if (i < 0) return;
            r.set(Ui.f(PIC_X), Ui.f(PIC_Y), Ui.f(PIC_X + PIC_W), Ui.f(PIC_Y + PIC_H));
            fill.setColor(0xFF000000);
            c.drawRoundRect(r, Ui.f(16f), Ui.f(16f), fill);
            String url = settled ? ProgrammePictures.get(getContext(), ch, s, i, redraw) : ProgrammePictures.peek(ch, s, i);
            Bitmap pic = url == null || url.isEmpty() ? null : Pics.get(url, Ui.px(PIC_W), Ui.px(PIC_H), Pics.CROP, redraw);
            if (pic != null) {
                clip.reset();
                clip.addRoundRect(r, Ui.f(16f), Ui.f(16f), Path.Direction.CW);
                c.save();
                c.clipPath(clip);
                c.drawBitmap(pic, null, r, bmp);
                c.restore();
            } else {
                Bitmap logo = ch.logo.isEmpty() ? null : Pics.get(ch.logo, Ui.px(300f), Ui.px(180f), Pics.logoMode(), redraw);
                if (logo != null) c.drawBitmap(logo, r.centerX() - logo.getWidth() / 2f, r.centerY() - logo.getHeight() / 2f, bmp);
                else RoundButton.glyph(c, Dark.TV, r.centerX(), r.centerY(), Ui.f(96f), Palette.TEXT3);
            }

            // The card.
            int w = Ui.px(PIC_W - 56f);
            String key = ch.key + "|" + s.start[i] + "|" + w;
            if (!key.equals(cardFor)) {
                cardTitle = layout(s.title[i], cardTitleP, w, 2);
                cardDesc = s.desc[i].isEmpty() ? null : layout(s.desc[i], cardP, w, 9);
                cardFor = key;
            }
            String day = Ui.day(s.start[i], now), span = LiveText.span(s.start[i], s.stop[i]);
            String when = ("Today".equals(day) ? "" : day + ", ") + span;
            boolean over = s.stop[i] <= now, on = s.start[i] <= now && !over;
            String state = on ? "On now" : over ? (Catchup.has(ch, s.start[i], now) ? "Catch-up" : "")
                    : Reminders.has(getContext(), ch.key, s.start[i]) ? "Reminder set" : "";
            float top = Ui.f(CARD_Y), pad = Ui.f(28f);
            float body = cardTitle.getHeight() + Ui.f(16f + 30f) + (cardDesc == null ? 0f : Ui.f(16f) + cardDesc.getHeight());
            r.set(Ui.f(PIC_X), top, Ui.f(PIC_X + PIC_W), top + pad * 2f + body);
            fill.setColor(Palette.PANEL);
            c.drawRoundRect(r, Ui.f(12f), Ui.f(12f), fill);
            cardTitleP.setColor(Palette.TEXT1);
            Ui.draw(c, cardTitle, r.left + pad, top + pad);
            float base = top + pad + cardTitle.getHeight() + Ui.f(16f + 24f);
            cardP.setColor(Palette.TEXT2);
            c.drawText(when, r.left + pad, base, cardP);
            if (!state.isEmpty()) {
                float x = r.left + pad + cardP.measureText(when) + Ui.f(24f);
                if (over && !state.isEmpty()) {
                    RoundButton.glyph(c, Dark.HISTORY, x + Ui.f(12f), base - Ui.f(8f), Ui.f(24f), Palette.ACCENT_LIGHT);
                    x += Ui.f(32f);
                }
                cardP.setColor(on || over ? Palette.ACCENT_LIGHT : Palette.TEXT2);
                c.drawText(state, x, base, cardP);
                cardP.setColor(Palette.TEXT2);
            }
            if (cardDesc != null) Ui.draw(c, cardDesc, r.left + pad, base + Ui.f(22f));
        }

        private StaticLayout layout(String text, TextPaint p, int width, int lines) {
            return StaticLayout.Builder.obtain(text, 0, text.length(), p, Math.max(1, width)).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.08f).setIncludePad(false).setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).build();
        }
    }
}
