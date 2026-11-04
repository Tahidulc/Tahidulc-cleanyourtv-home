package com.gathin.tvhomephone.watch;

import android.content.Context;

import com.gathin.tvhomephone.SyncStore;
import com.gathin.tvhomephone.sync.Tvs;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Coming up, as the TVs' tab has it: the next episodes of the series watched on the TVs (and series on My list), and
 * films on My list on the day they reach digital in the UK, within the days asked for, soonest first. A title whose
 * release pop-up is off (the synced "mute") says so; holding a poster turns it back on or off for every device.
 */
public final class ComingUp {
    private ComingUp() {}

    public static List<Title> load(Context c, int days) throws IOException {
        LocalDate today = LocalDate.now(), last = today.plusDays(days);
        Set<String> series = new HashSet<>();
        List<Title> out = new ArrayList<>();
        JSONArray shows = Tvs.watchingCached(c).optJSONArray("shows");
        for (int i = 0; shows != null && i < shows.length(); i++) {
            JSONObject s = shows.optJSONObject(i);
            if (s != null && s.optInt("id") > 0) series.add("tv:" + s.optInt("id"));
        }
        List<JSONObject> films = new ArrayList<>();
        for (JSONObject d : SyncStore.list(c, "mylist")) {
            if (d.optBoolean("tv")) series.add("tv:" + d.optInt("id"));
            else films.add(d);
        }
        IOException failed = null;
        for (String k : series) {
            int id = Integer.parseInt(k.substring(3));
            try {
                JSONObject d = Tmdb.get(c, "tv/" + id);
                JSONObject next = d.optJSONObject("next_episode_to_air");
                if (next == null) continue;
                LocalDate air = parse(next.optString("air_date"));
                if (air == null || air.isBefore(today) || air.isAfter(last)) continue;
                Title t = Title.from(d, true);
                if (t == null) continue;
                t.when = air.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                t.badge = Plan.day(air, today);
                t.badgeColor = 0xFF2F64D8;
                t.foot = "S" + next.optInt("season_number") + " E" + next.optInt("episode_number") + network(d);
                out.add(t);
            } catch (IOException e) {
                failed = e;
            }
        }
        for (JSONObject f : films) {
            try {
                JSONObject rd = Tmdb.get(c, "movie/" + f.optInt("id") + "/release_dates");
                LocalDate digital = digitalGb(rd);
                if (digital == null || digital.isBefore(today) || digital.isAfter(last)) continue;
                Title t = Title.fromItem(f);
                if (t == null) continue;
                t.tv = false;
                t.when = digital.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                t.badge = Plan.day(digital, today);
                t.badgeColor = 0xFFFF4FA3;
                t.foot = "Film · out on digital";
                out.add(t);
            } catch (IOException e) {
                failed = e;
            }
        }
        if (out.isEmpty() && failed != null) throw failed;
        out.sort((a, b) -> Long.compare(a.when, b.when));
        for (Title t : out) if (muted(c, t)) t.badge = "POP-UP OFF";
        return out;
    }

    /** The series' network or service, " · Netflix", for the foot; empty when TMDB names none. */
    private static String network(JSONObject d) {
        JSONArray n = d.optJSONArray("networks");
        JSONObject first = n == null || n.length() == 0 ? null : n.optJSONObject(0);
        return first == null ? "" : " · " + first.optString("name");
    }

    /** The UK's digital release (type 4) date, or null. */
    static LocalDate digitalGb(JSONObject releaseDates) {
        JSONArray r = releaseDates == null ? null : releaseDates.optJSONArray("results");
        for (int i = 0; r != null && i < r.length(); i++) {
            JSONObject c = r.optJSONObject(i);
            if (c == null || !"GB".equals(c.optString("iso_3166_1"))) continue;
            JSONArray dates = c.optJSONArray("release_dates");
            for (int j = 0; dates != null && j < dates.length(); j++) {
                JSONObject d = dates.optJSONObject(j);
                if (d != null && d.optInt("type") == 4) return parse(d.optString("release_date"));
            }
        }
        return null;
    }

    static LocalDate parse(String s) {
        try {
            return s == null || s.length() < 10 ? null : LocalDate.parse(s.substring(0, 10));
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static boolean muted(Context c, Title t) {
        return SyncStore.get(c, "mute", t.key()) != null;
    }

    /** Hold on a Coming up poster: its release pop-up off (or on again), on every device. */
    public static boolean toggleMute(Context c, Title t) {
        if (muted(c, t)) {
            SyncStore.delete(c, "mute", t.key());
            return false;
        }
        JSONObject d = new JSONObject();
        try {
            d.put("title", t.title);
        } catch (JSONException ignored) {
            // plain value
        }
        SyncStore.put(c, "mute", t.key(), d);
        return true;
    }
}
