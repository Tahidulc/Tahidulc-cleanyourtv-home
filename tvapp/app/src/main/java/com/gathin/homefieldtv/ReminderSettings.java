package com.gathin.homefieldtv;

import com.gathin.homefieldtv.engine.Reminders;

import java.util.List;

/**
 * Settings → Other → Reminders: whether a reminder's card may pop up over other apps (OK opens Android's page for it),
 * what the card does, and the reminders set in the guide, soonest first (OK on one cancels it; My list → My reminders
 * lists them too).
 */
final class ReminderSettings {
    private ReminderSettings() {}

    static void rows(TvActivity tv, List<RowList.Row> out) {
        boolean over = ReminderCard.overApps(tv);
        out.add(RowList.Row.item("over", "Pop up over other apps", over ? "Allowed" : "Not allowed yet  ·  OK opens Android's page for it"));
        out.add(RowList.Row.note("A minute before the start its card pops up, over other apps too: OK watches it, Back puts it away. "
                + "A TV that's off isn't woken for it."));
        long now = System.currentTimeMillis();
        List<Reminders.Reminder> list = Reminders.list(tv, now);
        out.add(RowList.Row.header("Reminders set"));
        if (list.isEmpty()) out.add(RowList.Row.note("None yet. In the TV guide, hold OK on a programme to come, then Remind me."));
        for (Reminders.Reminder r : list) {
            out.add(RowList.Row.item("rem:" + r.id(), r.title, Ui.day(r.start, now) + " " + LiveText.hm(r.start) + "  ·  " + r.channel
                    + "  ·  OK to cancel it"));
        }
    }

    /** A row chosen: changed (true), or not one of the page's (false). */
    static boolean chosen(TvActivity tv, String tag) {
        if (tag.startsWith("rem:")) return cancel(tv, tag.substring(4));
        if (tag.equals("over")) {
            if (!ReminderCard.overApps(tv) && !ReminderCard.openSettings(tv)) {
                android.widget.Toast.makeText(tv, "This TV has no such page: reminders come as a notification instead",
                        android.widget.Toast.LENGTH_LONG).show();
            }
            return true;
        }
        return false;
    }

    /** A reminder cancelled by its id ("key@start"), its alarm made again: true when it went. */
    static boolean cancel(TvActivity tv, String id) {
        int at = id.lastIndexOf('@');
        if (at <= 0) return false;
        try {
            Reminders.remove(tv, id.substring(0, at), Long.parseLong(id.substring(at + 1)));
        } catch (NumberFormatException e) {
            return false;
        }
        ReminderAlarm.schedule(tv);
        return true;
    }
}
