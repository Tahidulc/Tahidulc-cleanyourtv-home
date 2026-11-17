package com.gathin.homefieldtv;

import android.content.Context;
import android.widget.Toast;

import com.gathin.homefieldtv.engine.Catchup;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.LogoPack;
import com.gathin.homefieldtv.engine.Reminders;
import com.gathin.homefieldtv.prefs.ChannelEdits;
import com.gathin.homefieldtv.prefs.Parental;

import java.util.ArrayList;
import java.util.List;

/**
 * What the TV guide's and the archive's menus offer for a programme and its channel (GuideMenu's lines, as the family's
 * TiviMate lists them, DEVICE.md §2.8), and what each does. Homefield's own behind them: its reminders (a card a minute
 * before), its catch-up (CatchupScreen, from the provider's archive), its recordings (kept from the channel's catch-up:
 * RecordingsScreen.record), My list's programmes, its favourites and its parental lock.
 */
final class GuideActions {
    private GuideActions() {}

    static final String SEARCH = "search", SETTINGS = "settings", WATCH = "watch", START = "start", REMIND = "remind",
            UNREMIND = "unremind", RECORD = "record", MY_LIST = "mylist", ABOUT = "about", FAVOURITE = "fav", ARCHIVE = "archive",
            BLOCK = "block";

    /**
     * OK on a programme to come: Remind (Cancel reminder once set), Record (on a channel with catch-up: a recording
     * plays from it), Add to (or Remove from) My list, Programme description.
     */
    static List<GuideMenu.Item> popup(Context c, Channel ch, long start, String title) {
        List<GuideMenu.Item> out = new ArrayList<>();
        out.add(remindItem(c, ch, start));
        if (Catchup.source(ch) != null) out.add(GuideMenu.Item.item(RECORD, "Record"));
        out.add(myListItem(c, title));
        out.add(GuideMenu.Item.item(ABOUT, "Programme description"));
        return out;
    }

    private static GuideMenu.Item remindItem(Context c, Channel ch, long start) {
        return Reminders.has(c, ch.key, start) ? GuideMenu.Item.item(UNREMIND, "Cancel reminder") : GuideMenu.Item.item(REMIND, "Remind");
    }

    private static GuideMenu.Item myListItem(Context c, String title) {
        return GuideMenu.Item.item(MY_LIST, MyListScreen.hasProgram(c, title) ? "Remove from My list" : "Add to My list");
    }

    /** The card without Move channel (the archive's). */
    static List<GuideMenu.Item> card(Context c, Channel ch, Guide.Schedule s, int i, boolean archiveLink) {
        return card(c, ch, s, i, archiveLink, false);
    }

    /**
     * OK held: Search and Settings; under the programme's name what can be done with it (to come: Remind; on now: Watch
     * from start where the archive has its start; shown: Watch, from the archive; and Record wherever the channel's
     * catch-up can keep it), Add to (or Remove from) My list and Programme description; under the channel's name (the
     * family's own, where given) TiviMate's Add to (or Remove from) Favourites, Block channel and Hide channel, then
     * Rename channel, Change logo, Move channel (movable: the guide's lists but Recently watched) and Homefield's
     * Catch-up (the archive's programmes by day, where the provider keeps them; not from the archive view itself):
     * ChannelMenu.
     */
    static List<GuideMenu.Item> card(Context c, Channel ch, Guide.Schedule s, int i, boolean archiveLink, boolean movable) {
        long now = System.currentTimeMillis();
        List<GuideMenu.Item> out = new ArrayList<>();
        out.add(GuideMenu.Item.icon(SEARCH, Dark.SEARCH, "Search"));
        out.add(GuideMenu.Item.icon(SETTINGS, Dark.GEAR, "Settings"));
        if (s != null && i >= 0 && i < s.start.length) {
            long a = s.start[i], b = s.stop[i];
            boolean kept = Catchup.source(ch) != null;
            out.add(GuideMenu.Item.section(s.title[i]));
            if (a > now) {
                out.add(remindItem(c, ch, a));
            } else if (b > now) {
                if (Catchup.has(ch, a, now) && now - a >= LiveTv.LIVE_EDGE_MS) out.add(GuideMenu.Item.item(START, "Watch from start"));
            } else if (Catchup.has(ch, a, now)) {
                out.add(GuideMenu.Item.item(WATCH, "Watch"));
            } else {
                kept = false;
            }
            if (kept) out.add(GuideMenu.Item.item(RECORD, "Record"));
            out.add(myListItem(c, s.title[i]));
            out.add(GuideMenu.Item.item(ABOUT, "Programme description"));
        }
        out.add(GuideMenu.Item.section(ChannelEdits.name(ch)));
        ChannelMenu.items(c, ch, archiveLink, movable, out);
        return out;
    }

    // ---- what they do -----------------------------------------------------------------------------------------------

    /**
     * What a menu's line does for programme p ({start, end}, null for none; its title and description) on channel c (all
     * but Programme description, which the screen shows itself); true when the over-apps question is due (the first
     * reminder: ask()).
     */
    static boolean act(TvActivity tv, String tag, Channel c, String title, String desc, long[] p) {
        long a = p == null ? 0 : p[0], b = p == null ? 0 : p[1];
        switch (tag) {
            case SEARCH:
                search(tv, title);
                return false;
            case SETTINGS:
                tv.openSettings();
                return false;
            case WATCH:
            case START:
                if (p != null) watchFrom(tv, c, a, b);
                return false;
            case REMIND:
                return p != null && remind(tv, c, title, a, b);
            case UNREMIND:
                if (p != null) unremind(tv, c, title, a);
                return false;
            case RECORD:
                // Kept from the channel's catch-up (Recordings), its toast saying so or why not.
                if (p != null) RecordingsScreen.record(tv, c, title, desc, a, b);
                return false;
            case MY_LIST:
                myList(tv, title);
                return false;
            case FAVOURITE:
                favourite(tv, c);
                return false;
            case ARCHIVE:
                archive(tv, c, p == null ? System.currentTimeMillis() : a);
                return false;
            case BLOCK:
                block(tv, c);
                return false;
            default:
                // Hide, Rename, Change logo: the channel's edits (Move channel is the guide's own).
                if (ChannelMenu.handles(tag)) ChannelMenu.act(tv, tag, c);
                return false;
        }
    }

    /** The first reminder: whether its card may pop up over other apps (Android's own page), asked once. */
    static void ask(TvActivity tv, DialogCard d) {
        ReminderCard.asked(tv);
        List<RowList.Row> answers = new ArrayList<>();
        answers.add(RowList.Row.item("open", "Open Android's page", null));
        answers.add(RowList.Row.item("no", "Not now", null));
        d.show("Reminders over other apps", ReminderCard.ASK, answers, 0);
    }

    /** The question answered: Android's page opened (or, where the TV has none, a notification instead). */
    static void answered(TvActivity tv, RowList.Row answer) {
        if ("open".equals(answer.tag) && !ReminderCard.openSettings(tv)) {
            Toast.makeText(tv, "This TV has no such page: reminders come as a notification instead", Toast.LENGTH_LONG).show();
        }
    }

    /** A channel full screen, after its PIN (Back from the player comes back to the guide: TvActivity.toGuide). */
    static void watch(TvActivity tv, Channel c) {
        tv.watch(c);
    }

    /** A programme from its start, from the provider's archive: CatchupScreen over the channel's live screen, as before. */
    static void watchFrom(TvActivity tv, Channel c, long start, long end) {
        tv.watchFrom(c, start, end);
    }

    /** A reminder a minute before it starts (its card over whatever is on); true when the over-apps question is due. */
    static boolean remind(TvActivity tv, Channel c, String title, long start, long end) {
        Reminders.add(tv, new Reminders.Reminder(c.key, ChannelEdits.name(c), LogoPack.url(c), title, start, end));
        ReminderAlarm.schedule(tv);
        Toast.makeText(tv, "Reminder set: " + title + ", " + Ui.day(start, System.currentTimeMillis()) + " " + LiveText.hm(start),
                Toast.LENGTH_SHORT).show();
        return ReminderCard.shouldAsk(tv);
    }

    static void unremind(TvActivity tv, Channel c, String title, long start) {
        Reminders.remove(tv, c.key, start);
        ReminderAlarm.schedule(tv);
        Toast.makeText(tv, "Reminder cancelled: " + title, Toast.LENGTH_SHORT).show();
    }

    /** Add to My list (My TV programmes, by its title), or taken off it. */
    static void myList(TvActivity tv, String title) {
        if (title == null || title.trim().isEmpty()) return;
        boolean on = MyListScreen.toggleProgram(tv, title);
        Toast.makeText(tv, on ? title + " added to My list" : title + " removed from My list", Toast.LENGTH_SHORT).show();
    }

    static void favourite(TvActivity tv, Channel c) {
        boolean on = Library.get(tv).toggleFavourite(c);
        Toast.makeText(tv, on ? ChannelEdits.name(c) + " added to Favourites" : ChannelEdits.name(c) + " removed from Favourites", Toast.LENGTH_SHORT).show();
    }

    /** Block channel: Settings → Parental control's lock (the PIN asked before it plays); unblocking asks for the PIN. */
    static void block(TvActivity tv, Channel c) {
        if (!Parental.hasPin(tv)) {
            Toast.makeText(tv, "Choose a PIN in Settings → Parental control to block channels", Toast.LENGTH_LONG).show();
            return;
        }
        if (Parental.lockedChannels(tv).contains(c.key)) {
            PinScreen.check(tv, "Unblock " + ChannelEdits.name(c), () -> {
                Parental.lockChannel(tv, c.key, false);
                Toast.makeText(tv, ChannelEdits.name(c) + " unblocked", Toast.LENGTH_SHORT).show();
            });
        } else {
            Parental.lockChannel(tv, c.key, true);
            Toast.makeText(tv, ChannelEdits.name(c) + " blocked: the PIN is asked before it plays", Toast.LENGTH_SHORT).show();
        }
    }

    /** Search, with the programme's title in it (what else there is like it). */
    static void search(TvActivity tv, String title) {
        tv.openSearch();
        Screen s = tv.top();
        if (s instanceof SearchScreen && title != null && !title.isEmpty()) ((SearchScreen) s).type(title);
    }

    /** The channel's archive by day (catch-up), the remote on the programme at time at. */
    static void archive(TvActivity tv, Channel c, long at) {
        if (c != null) tv.push(new ArchiveScreen(tv, c, at));
    }

    /** What a programme's description card says: {title, times, minutes left (on now) or ""}, and how far through (-1: not on). */
    static String[] about(Channel ch, Guide.Schedule s, int i, long now) {
        if (s == null || i < 0 || i >= s.start.length) return new String[]{ChannelEdits.name(ch), "", "", ""};
        String day = Ui.day(s.start[i], now), span = LiveText.span(s.start[i], s.stop[i]);
        if (!"Today".equals(day)) span = day + ", " + span;
        boolean on = s.start[i] <= now && now < s.stop[i];
        return new String[]{s.title[i], span, on ? GuideBand.minutes(s.stop[i] - now) : "", s.desc[i]};
    }
}
