package com.gathin.homefieldtv;

import com.gathin.homefieldtv.engine.Reminders;

import java.util.List;

/**
 * Settings' Reminders section: the reminders set in the guide, soonest first (OK on one cancels it), whether their card
 * may pop up over other apps (OK opens Android's page for it), and what the card does.
 */
final class ReminderSettings {
    private ReminderSettings() {}

    static void fill(TvActivity tv, List<ListPanel.Item> out) {
        long now = System.currentTimeMillis();
        List<Reminders.Reminder> list = Reminders.list(tv, now);
        out.add(new ListPanel.Item(null, ListPanel.HEADING, "Reminders", ""));
        if (list.isEmpty()) {
            out.add(new ListPanel.Item(null, ListPanel.NOTE, "None yet. In the guide, hold OK on a programme to come, then Remind me.", ""));
        }
        for (Reminders.Reminder r : list) {
            out.add(new ListPanel.Item("rem:" + r.id(), ListPanel.TEXT, r.title,
                    Ui.day(r.start, now) + " " + LiveText.hm(r.start) + "  ·  " + r.channel + "  ·  OK to cancel it"));
        }
        boolean over = ReminderCard.overApps(tv);
        out.add(new ListPanel.Item("over", ListPanel.TEXT, "Pop up over other apps",
                over ? "Allowed" : "Not allowed yet  ·  OK opens Android's page for it"));
        out.add(new ListPanel.Item(null, ListPanel.NOTE, "A minute before the start its card pops up, over other apps too: OK watches it,", ""));
        out.add(new ListPanel.Item(null, ListPanel.NOTE, "Back puts it away. A TV that's off isn't woken for it.", ""));
    }

    /** A line chosen: changed (true), or not one of the section's (false). */
    static boolean chosen(TvActivity tv, String tag) {
        if (tag.startsWith("rem:")) {
            String id = tag.substring(4);
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
        if (tag.equals("over")) {
            if (!ReminderCard.overApps(tv) && !ReminderCard.openSettings(tv)) {
                android.widget.Toast.makeText(tv, "This TV has no such page: reminders come as a notification instead",
                        android.widget.Toast.LENGTH_LONG).show();
            }
            return true;
        }
        return false;
    }
}
