package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.gathin.homefieldtv.engine.Catchup;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Recordings;
import com.gathin.homefieldtv.prefs.OtherPrefs;

import java.util.ArrayList;
import java.util.List;

/**
 * Recordings, from the menu, as the family's other app has them: the rail, then the recordings as its History lists
 * programmes (ProgrammeList: each day under its name, the newest first; the programme's title, its day and times with
 * catch-up's circle arrow, Scheduled, Recording, Ready or Gone, the channel's logo and name; a card at the right with
 * its description), "Recordings" at the top right with its settings' round button (Settings → Other → Recording).
 * Empty, its record mark, "No recordings" and how to make one, where the other app has them.
 *
 * <p>What a recording is here: the family's provider allows one stream per account, so a channel can't be recorded
 * while another is watched, and Homefield TV writes no video to the TV. A programme on a channel whose provider keeps
 * catch-up is kept instead as a recording that plays from that archive (engine.Recordings), from a few minutes before
 * to a few minutes after it as Settings says, while the archive keeps it. Recording the channel being watched as it
 * plays would need the player's own stream copied to the TV's storage as it comes in; that isn't done (nothing here
 * writes video, and the channel can change copy part-way), and the screen says so plainly.
 *
 * <p>The TV guide's Record calls record(): one static method.
 */
final class RecordingsScreen extends Screen implements ProgrammeList.Host, DialogCard.Host {
    static final String MARGIN_NOTE = "As far as your provider's catch-up has it.";
    static final String HONEST = "Your provider allows one stream at a time, so a channel can't be recorded while another is "
            + "watched, and nothing is stored on this TV: a recording plays from the channel's catch-up for as long as your "
            + "provider keeps it. Channels without catch-up can't be recorded.";

    private Rail rail;
    private ProgrammeList list;
    private Top top;
    private DialogCard dialog;
    /** Where the remote is: the rail, the list, or the settings' round button. */
    private static final int RAIL = 0, LIST = 1, GEAR = 2;
    private int area = LIST;
    private Recordings.Recording asked;

    RecordingsScreen(TvActivity tv) {
        super(tv);
    }

    // ---- the guide's Record -----------------------------------------------------------------------------------------

    /**
     * Records a programme (the TV guide's Record, its programme menu's): kept as a recording that plays from the
     * channel's catch-up once it has been shown, and said in a word on the screen. True when it is kept (or was
     * already); false, with the reason said, when the channel keeps no catch-up or the programme has gone from it.
     */
    static boolean record(Context c, Channel channel, String title, String description, long start, long end) {
        if (channel == null || start <= 0 || end <= start) return false;
        long now = System.currentTimeMillis();
        Channel archive = Catchup.source(channel);
        int days = archive == null ? 0 : archive.archiveDays;
        if (days <= 0) {
            say(c, channel.name + " keeps no catch-up, so it can't be recorded: your provider allows one stream at a time.");
            return false;
        }
        if (!Recordings.recordable(days, start, end, now)) {
            say(c, "That programme has gone from " + channel.name + "'s catch-up.");
            return false;
        }
        if (Recordings.has(c, channel.key, start)) {
            say(c, "Already in Recordings");
            return true;
        }
        Recordings.add(c, new Recordings.Recording(channel.key, channel.name, channel.logo, title, description, start, end, now));
        say(c, "Recording " + (title == null || title.isEmpty() ? "it" : title) + ": it plays from " + channel.name
                + "'s catch-up, for " + days + (days == 1 ? " day" : " days") + " after it starts.");
        return true;
    }

    private static void say(Context c, String words) {
        Toast.makeText(c, words, Toast.LENGTH_LONG).show();
    }

    // ---- the screen -------------------------------------------------------------------------------------------------

    @Override View build() {
        FrameLayout f = new FrameLayout(tv);
        // No ground of its own: the window's (TvActivity) is the screen's, so it is filled once, not twice.
        rail = new Rail(tv, Rail.RECORDINGS);
        list = new ProgrammeList(tv);
        list.setHost(this);
        top = new Top(tv);
        dialog = new DialogCard(tv);
        dialog.setHost(this);
        f.addView(top, new FrameLayout.LayoutParams(-1, -1));
        f.addView(list, new FrameLayout.LayoutParams(-1, -1));
        f.addView(rail, new FrameLayout.LayoutParams(-1, -1));
        f.addView(dialog, new FrameLayout.LayoutParams(-1, -1));
        fill();
        place();
        return f;
    }

    @Override void shown() {
        tv.live.stop();
        tv.live.place(null);
        fill();
    }

    @Override void tick() {
        fill();
    }

    @Override void channelsChanged() {
        fill();
    }

    /** The recordings as entries, each under its day, the newest first. */
    private void fill() {
        if (list == null) return;
        long now = System.currentTimeMillis();
        Library lib = Library.get(tv);
        List<ProgrammeList.Entry> out = new ArrayList<>();
        for (Recordings.Recording r : Recordings.list(tv, now)) out.add(entry(r, lib, now));
        list.set(out);
        top.empty = out.isEmpty();
        top.invalidate();
        if (out.isEmpty() && area == LIST) area = GEAR;
        place();
    }

    private ProgrammeList.Entry entry(Recordings.Recording r, Library lib, long now) {
        Channel c = lib.byKey(r.key);
        Channel archive = c == null ? null : Catchup.source(c);
        int days = archive == null ? 0 : archive.archiveDays;
        int st = Recordings.state(r, days, now);
        ProgrammeList.Entry e = new ProgrammeList.Entry(r, Ui.day(r.start, now));
        e.title = r.title.isEmpty() ? "A programme on " + r.channel : r.title;
        e.when = ProgrammeList.when(r.start, r.end);
        e.channel = c == null ? r.channel : c.name;
        e.logo = c == null ? r.logo : c.logo;
        e.about = r.about;
        e.archive = true;
        e.now = st == Recordings.RECORDING;
        e.stateLit = st == Recordings.RECORDING;
        switch (st) {
            case Recordings.SCHEDULED:
                e.state = "Scheduled";
                e.cardLine = "Kept from " + e.channel + "'s catch-up once it starts";
                break;
            case Recordings.RECORDING:
                e.state = "Recording";
                e.cardLine = "On now: what has been shown so far plays from the start";
                break;
            case Recordings.READY: {
                long left = Recordings.expires(r, days) - now;
                int d = (int) Math.max(1, Math.ceil(left / 86_400_000.0));
                e.state = d + (d == 1 ? " day left" : " days left");
                e.cardLine = "Ready to watch  ·  in the catch-up for " + d + (d == 1 ? " more day" : " more days");
                break;
            }
            default:
                e.state = "Gone";
                e.cardLine = c == null ? "This channel isn't in your playlists now" : "No longer in " + e.channel + "'s catch-up";
                break;
        }
        return e;
    }

    private void place() {
        if (list == null) return;
        if (list.isEmpty() && area == LIST) area = GEAR;
        rail.setActive(area == RAIL);
        list.setActive(area == LIST);
        top.gearOn = area == GEAR;
        top.invalidate();
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    @Override boolean key(int code, KeyEvent e) {
        if (dialog.showing()) return dialog.key(code);
        switch (area) {
            case RAIL:
                if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    area = list.isEmpty() ? GEAR : LIST;
                    place();
                    return true;
                }
                return rail.key(code) || code == KeyEvent.KEYCODE_DPAD_LEFT;
            case GEAR:
                if (code == KeyEvent.KEYCODE_DPAD_DOWN && !list.isEmpty()) {
                    area = LIST;
                    place();
                } else if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                    area = RAIL;
                    place();
                }
                return true;
            default:
                return list.key(code) || code == KeyEvent.KEYCODE_DPAD_RIGHT;
        }
    }

    @Override void ok() {
        if (dialog.showing()) {
            dialog.ok();
            return;
        }
        switch (area) {
            case RAIL:
                if (rail.focus == Rail.RECORDINGS) {
                    area = list.isEmpty() ? GEAR : LIST;
                    place();
                } else {
                    Rail.go(tv, rail.focus, Rail.RECORDINGS);
                }
                break;
            case GEAR:
                tv.push(new SettingsScreen(tv, "other.recording"));
                break;
            default:
                list.ok();
                break;
        }
    }

    @Override boolean back() {
        if (dialog.showing()) {
            dialog.hide(null);
            return true;
        }
        if (area == RAIL) {
            area = list.isEmpty() ? GEAR : LIST;
            place();
            return true;
        }
        return false;
    }

    @Override public void chosen(ProgrammeList l, ProgrammeList.Entry e) {
        Recordings.Recording r = (Recordings.Recording) e.tag;
        long now = System.currentTimeMillis();
        Channel c = Library.get(tv).byKey(r.key);
        Channel archive = c == null ? null : Catchup.source(c);
        int st = Recordings.state(r, archive == null ? 0 : archive.archiveDays, now);
        if (c == null) {
            say(tv, r.channel + " isn't in your playlists now.");
        } else if (st == Recordings.SCHEDULED) {
            say(tv, "It's on " + Ui.day(r.start, now) + " at " + LiveText.hm(r.start) + ": it can be watched once it starts.");
        } else if (st == Recordings.GONE) {
            say(tv, "It has gone from " + c.name + "'s catch-up.");
        } else {
            // From the archive, as far either side as Settings → Other → Recording says (the archive's own start at most).
            long[] span = Recordings.span(r, OtherPrefs.before(tv), OtherPrefs.after(tv));
            long from = Math.max(span[0], Catchup.earliest(c, now));
            tv.watchFrom(c, from, Math.min(span[1], Math.max(r.end, now)));
        }
    }

    @Override public void delete(ProgrammeList l, ProgrammeList.Entry e) {
        asked = (Recordings.Recording) e.tag;
        List<RowList.Row> answers = new ArrayList<>();
        answers.add(RowList.Row.item("yes", "Delete", ""));
        answers.add(RowList.Row.item("no", "Cancel", ""));
        list.setActive(false);
        dialog.show("Delete this recording?", e.title + "  ·  " + e.when, answers, 1);
    }

    @Override public void answered(DialogCard d, RowList.Row answer) {
        if ("yes".equals(answer.tag) && asked != null) Recordings.remove(tv, asked.id());
        asked = null;
        dialog.hide(null);
        list.setActive(area == LIST);
        fill();
    }

    @Override public void focused(ProgrammeList l, ProgrammeList.Entry e) {}

    @Override public boolean edge(ProgrammeList l, int dir) {
        if (dir == Ui.LEFT) {
            area = RAIL;
            place();
        } else if (dir == Ui.UP) {
            area = GEAR;
            place();
        }
        return true;
    }

    // ---- Settings → Other → Recording -------------------------------------------------------------------------------

    /** The settings page's rows: the margins either side, and what a recording is here. */
    static void settingsRows(TvActivity tv, List<RowList.Row> out) {
        out.add(MoreSettings.pick("rec.before", "Start recording before program start, min", String.valueOf(OtherPrefs.before(tv))));
        out.add(MoreSettings.pick("rec.after", "Stop recording after program end, min", String.valueOf(OtherPrefs.after(tv))));
        out.add(RowList.Row.note(HONEST));
    }

    // ---- what is drawn besides the list ---------------------------------------------------------------------------

    /** "Recordings" at the top right with the settings' round button; empty, the record mark and its words. */
    private static final class Top extends View {
        boolean empty, gearOn;
        private final TextPaint titleP = Type.paint(48f, Type.REGULAR, Palette.TEXT1), noteP = Type.paint(Type.SMALL, Type.REGULAR, Palette.TEXT2);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF area = new RectF();
        private StaticLayout noteL;

        Top(Context c) {
            super(c);
        }

        @Override protected void onDraw(Canvas c) {
            String t = "Recordings";
            titleP.setColor(Palette.TEXT1);
            c.drawText(t, Ui.f(1752) - titleP.measureText(t), Ui.f(97), titleP);
            float cx = Ui.f(1836), cy = Ui.f(78);
            if (gearOn) {
                Focus.circle(c, cx, cy, Ui.f(72));
            } else {
                fill.setColor(Palette.SELECTED);
                c.drawCircle(cx, cy, Ui.f(36), fill);
            }
            RoundButton.glyph(c, Dark.GEAR, cx, cy, Ui.f(36), Palette.TEXT1);
            if (noteL == null) noteL = Ui.layout(HONEST, noteP, Ui.px(ProgrammeList.CARD_W), 6);
            noteP.setColor(Palette.TEXT2);
            if (empty) {
                // Where the other app has its empty state: the right of the screen.
                area.set(Ui.f(1232), 0, Ui.f(1920), getHeight());
                EmptyState.draw(c, area, Dark.RECORD, 120f, "No recordings", "Select a programme in the TV guide and choose Record.");
                Ui.draw(c, noteL, Ui.f(ProgrammeList.CARD_X), Ui.f(880));
            } else {
                Ui.draw(c, noteL, Ui.f(ProgrammeList.CARD_X), Ui.f(880));
            }
        }
    }
}
