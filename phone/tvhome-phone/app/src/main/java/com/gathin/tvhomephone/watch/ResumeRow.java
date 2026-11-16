package com.gathin.tvhomephone.watch;

import android.content.Context;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.glass.Glass;
import com.gathin.tvhomephone.glass.WatchCard;
import com.gathin.tvhomephone.live.VodHistory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Watch's Continue watching, as the reference has it: what was left part-way from the family's own playlist as
 * landscape glass cards, each with its backdrop and logo once TMDB has them (kept for the session), the line of how far
 * in and the time to go. Films on Movies, episodes on TV shows, both on Discover. Tap carries on; hold takes it off.
 */
final class ResumeRow {
    /** Each title's {backdrop, logo} as read: "tv|id" to the two URLs (either may be null). */
    private static final Map<String, String[]> ART = new HashMap<>();
    final LinearLayout view;
    private final LinearLayout row;
    private final Fragment host;
    /** The section shown, to draw again after a card is taken off. */
    private int shown;

    ResumeRow(Fragment host) {
        this.host = host;
        Context c = host.requireContext();
        view = new LinearLayout(c);
        view.setOrientation(LinearLayout.VERTICAL);
        view.addView(Glass.section(c, c.getString(R.string.f1_resume_title), null));
        HorizontalScrollView scroll = new HorizontalScrollView(c);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(Glass.dp(c, 16), 0, Glass.dp(c, 16), 0);
        scroll.addView(row);
        view.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        view.setVisibility(View.GONE);
    }

    /** The cards for this section (0 Discover, 1 Movies, 2 TV shows); none while a search shows. */
    void show(int section, boolean searching) {
        if (!host.isAdded()) return;
        shown = section;
        Context c = host.requireContext();
        row.removeAllViews();
        for (VodHistory.Entry e : VodHistory.list(c)) if (belongs(e, section)) row.addView(card(c, e));
        view.setVisibility(row.getChildCount() > 0 && !searching ? View.VISIBLE : View.GONE);
    }

    private View card(Context c, VodHistory.Entry e) {
        WatchCard card = new WatchCard(c);
        String name = e.name.isEmpty() ? e.title : e.name;
        String where = e.tv && e.episode > 0 ? (e.season > 0 ? "S" + e.season + " " : "") + "E" + e.episode : "";
        int left = e.minutesLeft();
        String rest = left > 0 ? c.getString(R.string.f1_resume_left, left) : c.getString(R.string.f1_resume_carry_on);
        String line = where.isEmpty() ? rest : where + " · " + rest;
        String key = (e.tv ? "tv|" : "film|") + e.tmdb;
        String[] art = ART.get(key);
        card.set(name, art != null && art[0] != null ? art[0] : e.poster, art == null ? null : art[1], e.progress(), line);
        if (art == null && e.tmdb > 0) read(c.getApplicationContext(), e, key, card, name, line);
        card.setOnClickListener(v -> host.startActivity(com.gathin.tvhomephone.live.VodPlayerActivity.intent(host.requireContext(), e)));
        card.setOnLongClickListener(v -> {
            VodHistory.remove(host.requireContext(), e.key);
            Toast.makeText(host.requireContext(), host.getString(R.string.f1_resume_removed, name), Toast.LENGTH_SHORT).show();
            show(shown, false);
            return true;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Glass.dp(c, 250), Glass.dp(c, 140));
        lp.rightMargin = Glass.dp(c, 12);
        card.setLayoutParams(lp);
        return card;
    }

    /** Its backdrop and logo from TMDB's details (kept on the phone), off the main thread; the card again with them. */
    private void read(Context app, VodHistory.Entry e, String key, WatchCard card, String name, String line) {
        App.io.execute(() -> {
            String back = null, logo = null;
            try {
                JSONObject d = Tmdb.detailsKept(app, e.tmdb, e.tv);
                if (d != null) {
                    back = Tmdb.image(d.optString("backdrop_path", null), "w780");
                    logo = logo(d);
                }
            } catch (Exception ignored) {
                // the poster, as it was
            }
            String[] got = {back, logo};
            App.main.post(() -> {
                ART.put(key, got);
                if (!host.isAdded() || card.getParent() == null) return;
                card.set(name, got[0] != null ? got[0] : e.poster, got[1], e.progress(), line);
            });
        });
    }

    /** Pure-ish: the title's English (or wordless) logo from TMDB's images, as the title page picks it. */
    static String logo(JSONObject d) {
        JSONObject im = d.optJSONObject("images");
        JSONArray logos = im == null ? null : im.optJSONArray("logos");
        if (logos == null || logos.length() == 0) return null;
        for (int i = 0; i < logos.length(); i++) {
            JSONObject l = logos.optJSONObject(i);
            if (l != null && ("en".equals(l.optString("iso_639_1")) || l.isNull("iso_639_1"))) {
                return Tmdb.image(l.optString("file_path", null), "w500");
            }
        }
        JSONObject first = logos.optJSONObject(0);
        return first == null ? null : Tmdb.image(first.optString("file_path", null), "w500");
    }

    /** Pure: whether a part-watched entry shows on a section (films on Movies, episodes on TV shows, both on Discover). */
    static boolean belongs(VodHistory.Entry e, int section) {
        return !(section == 1 && e.tv || section == 2 && !e.tv);
    }
}
