package com.gathin.tvhomephone.watch;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.SyncStore;
import com.gathin.tvhomephone.sync.Tvs;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A film's or series' page, as the TVs' title panel: its picture and logo, the line under it, which of the family's
 * playlists has it, what it's about, then Play (from those playlists only: nothing else is looked for and no other app
 * is opened), My list, How was it?, the trailer, Film night (films) or the Episode map (series), and the cast.
 */
public class TitleActivity extends AppCompatActivity {
    private Title t;
    private JSONObject details;
    /** The line saying which playlists have it; what was found there (null: not looked yet, or none). */
    private TextView where;
    private com.gathin.tvhomephone.live.Vod.Item found;
    private List<com.gathin.tvhomephone.live.Vod.Episode> foundEps;
    private boolean looked, looking;
    private LinearLayout col, buttons;
    private MaterialButton listButton, rateButton, nightButton;

    public static void open(Context c, Title t) {
        c.startActivity(intent(c, t));
    }

    /** Its page, and Play pressed on it at once (Home's hero): from the family's own playlists only, as Play is. */
    public static void play(Context c, Title t) {
        c.startActivity(intent(c, t).putExtra("autoplay", true));
    }

    private static Intent intent(Context c, Title t) {
        Intent i = new Intent(c, TitleActivity.class).putExtra("id", t.id).putExtra("tv", t.tv).putExtra("title", t.title)
                .putExtra("poster", t.poster).putExtra("backdrop", t.backdrop).putExtra("year", t.year);
        if (!(c instanceof android.app.Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent in = getIntent();
        t = new Title();
        t.id = in.getIntExtra("id", 0);
        t.tv = in.getBooleanExtra("tv", false);
        t.title = in.getStringExtra("title") == null ? "" : in.getStringExtra("title");
        t.poster = in.getStringExtra("poster");
        t.backdrop = in.getStringExtra("backdrop");
        t.year = in.getStringExtra("year") == null ? "" : in.getStringExtra("year");

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.ground(this));
        col = Ui.column(this);
        scroll.addView(col);
        setContentView(scroll);

        FrameLayout head = new FrameLayout(this) {
            @Override protected void onMeasure(int w, int h) {
                int width = MeasureSpec.getSize(w);
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(Math.min(width * 9 / 16, getResources().getDisplayMetrics().heightPixels / 2),
                        MeasureSpec.EXACTLY));
            }
        };
        ImageView back = new ImageView(this);
        back.setScaleType(ImageView.ScaleType.CENTER_CROP);
        head.addView(back, new FrameLayout.LayoutParams(-1, -1));
        View fade = new View(this);
        fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x330D0F2C, 0x000D0F2C, 0xFF0D0F2C}));
        head.addView(fade, new FrameLayout.LayoutParams(-1, -1));
        ImageView logo = new ImageView(this);
        logo.setScaleType(ImageView.ScaleType.FIT_START);
        logo.setAdjustViewBounds(true);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.dp(this, 240), Ui.dp(this, 72), Gravity.BOTTOM | Gravity.START);
        lp.setMargins(Ui.dp(this, 20), 0, 0, Ui.dp(this, 4));
        head.addView(logo, lp);
        TextView name = Ui.text(this, t.title, 26, 0xFFFFFFFF, true);
        FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM | Gravity.START);
        np.setMargins(Ui.dp(this, 20), 0, Ui.dp(this, 20), Ui.dp(this, 4));
        head.addView(name, np);
        col.addView(head, Ui.fill());
        Glide.with(this).load(t.backdrop != null ? t.backdrop : t.poster).centerCrop().into(back);

        LinearLayout info = Ui.column(this);
        int pad = Ui.dp(this, 20);
        info.setPadding(pad, Ui.dp(this, 8), pad, 0);
        info.addView(Ui.label(this, t.tv ? "TV SHOWS" : "MOVIES", t.tv ? Ui.SHOWS : Ui.MOVIES));
        TextView meta = Ui.text(this, "", 14, Ui.SOFT, false);
        meta.setPadding(0, Ui.dp(this, 6), 0, 0);
        info.addView(meta);
        where = Ui.text(this, "Looking in your playlists…", 14, 0xFFFFFFFF, true);
        where.setPadding(0, Ui.dp(this, 6), 0, 0);
        info.addView(where);
        lookInPlaylists(null);
        TextView overview = Ui.text(this, "", 15, 0xEEFFFFFF, false);
        overview.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 4));
        overview.setLineSpacing(0, 1.15f);
        info.addView(overview);
        col.addView(info, Ui.fill());

        HorizontalScrollView bs = new HorizontalScrollView(this);
        bs.setHorizontalScrollBarEnabled(false);
        buttons = Ui.row(this);
        buttons.setPadding(pad, Ui.dp(this, 12), pad, Ui.dp(this, 8));
        bs.addView(buttons);
        col.addView(bs, Ui.fill());
        addButtons();
        // Opened with Play (Home's hero): Play pressed once, as the page comes up.
        if (state == null && in.getBooleanExtra("autoplay", false)) col.post(this::play);

        LinearLayout more = Ui.column(this);
        col.addView(more, Ui.fill());

        App.io.execute(() -> {
            JSONObject d = null;
            try {
                d = Tmdb.details(this, t.id, t.tv);
            } catch (IOException ignored) {
                // the page as it came
            }
            JSONObject got = d;
            App.main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (got == null) {
                    meta.setText(Tmdb.ready(this) ? "Couldn't load the details right now." : "Pair with a TV in Settings to see the details.");
                    return;
                }
                details = got;
                Title full = Title.from(got, t.tv);
                if (full != null) {
                    if (t.poster == null) t.poster = full.poster;
                    if (t.backdrop == null && full.backdrop != null) {
                        t.backdrop = full.backdrop;
                        Glide.with(this).load(t.backdrop).centerCrop().into(back);
                    }
                    if (t.title.isEmpty()) t.title = full.title;
                }
                meta.setText(metaLine(got));
                // Its original title too (a foreign film's own name), when the first look found nothing.
                String original = got.optString(t.tv ? "original_name" : "original_title", "");
                if (looked && found == null && !original.isEmpty() && !original.equalsIgnoreCase(t.title)) lookInPlaylists(null);
                overview.setText(got.optString("overview"));
                String logoPath = logo(got);
                if (logoPath != null) {
                    name.setVisibility(View.GONE);
                    Glide.with(this).load(Tmdb.image(logoPath, "w500")).fitCenter().into(logo);
                }
                if (t.tv) more.addView(new EpisodeMap(this, t, got).view, Ui.fill());
                more.addView(cast(got), Ui.fill());
                View gap = new View(this);
                more.addView(gap, new LinearLayout.LayoutParams(-1, Ui.dp(this, 40)));
            });
        });
    }

    private void addButtons() {
        buttons.removeAllViews();
        buttons.addView(Ui.pill(this, "▶  Play", true, v -> play()), Ui.margins(Ui.wrap(), this, 0, 0, 8, 0));
        listButton = Ui.pill(this, "", false, v -> toggleList());
        buttons.addView(listButton, Ui.margins(Ui.wrap(), this, 0, 0, 8, 0));
        rateButton = Ui.pill(this, "", false, v -> rate());
        buttons.addView(rateButton, Ui.margins(Ui.wrap(), this, 0, 0, 8, 0));
        buttons.addView(Ui.pill(this, "Trailer", false, v -> trailer()), Ui.margins(Ui.wrap(), this, 0, 0, 8, 0));
        if (!t.tv) {
            nightButton = Ui.pill(this, "", false, v -> FilmNightSheet.show(this, t, details, this::paintButtons));
            buttons.addView(nightButton, Ui.margins(Ui.wrap(), this, 0, 0, 8, 0));
        }
        paintButtons();
    }

    private void paintButtons() {
        boolean in = SyncStore.get(this, "mylist", t.key()) != null;
        listButton.setText(in ? "✓  My list" : "+  My list");
        JSONObject r = SyncStore.get(this, "rating", t.key());
        int v = r == null ? 0 : r.optInt("value");
        rateButton.setText(v == 2 ? "♥  Loved it" : v == 1 ? "👍  Liked it" : v < 0 ? "Not for me" : "How was it?");
        if (nightButton != null) nightButton.setText(FilmNightSheet.words(this, t));
    }

    @Override protected void onResume() {
        super.onResume();
        if (listButton != null) paintButtons();
    }

    /** Play: from the family's own playlists only (a film at once, a series by season and episode); nothing else. */
    private void play() {
        if (looked && !looking) {
            playFound(null);
            return;
        }
        Toast.makeText(this, "Looking in your playlists…", Toast.LENGTH_SHORT).show();
        lookInPlaylists(() -> playFound(null));
    }

    /**
     * Looks for it in the family's own playlists (Vod: every playlist in use, every version), off the main thread; the
     * line under the title says which have it; then (when given) runs.
     */
    private void lookInPlaylists(Runnable then) {
        looking = true;
        String original = details == null ? "" : details.optString(t.tv ? "original_name" : "original_title", "");
        android.content.Context app = getApplicationContext();
        com.gathin.tvhomephone.App.io.execute(() -> {
            com.gathin.tvhomephone.live.Vod.Item it = com.gathin.tvhomephone.live.Vod.find(app, t.title, original, t.year, t.id, t.tv);
            List<com.gathin.tvhomephone.live.Vod.Episode> eps = it != null && "series".equals(it.kind)
                    ? com.gathin.tvhomephone.live.Vod.episodes(app, it) : null;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                boolean has = it != null && (!"series".equals(it.kind) || (eps != null && !eps.isEmpty()));
                found = has ? it : null;
                foundEps = has ? eps : null;
                looked = true;
                looking = false;
                if (where != null) where.setText(whereWords());
                if (then != null) then.run();
            });
        });
    }

    /** "In your playlists: Main · 4K, Backup · HD", the seasons for a series, or that none has it. */
    private String whereWords() {
        if (found == null) return "Not in your playlists";
        List<String> names = new ArrayList<>();
        for (String[] p : found.plays()) {
            String l = p.length > 3 && p[3] != null ? p[3] : "";
            if (!l.isEmpty() && !names.contains(l)) names.add(l);
        }
        String line = names.isEmpty() ? "In your playlist" : "In your playlists: " + String.join(", ", names);
        if (foundEps != null && !foundEps.isEmpty()) {
            java.util.Set<Integer> seasons = new java.util.TreeSet<>();
            for (com.gathin.tvhomephone.live.Vod.Episode e : foundEps) seasons.add(e.season);
            line += " · " + foundEps.size() + (foundEps.size() == 1 ? " episode" : " episodes")
                    + (seasons.size() > 1 ? " in " + seasons.size() + " seasons" : "");
        }
        return line;
    }

    /** What was found played: a film (its source chosen when there are several), a series' season and episode. */
    private void playFound(int[] episode) {
        if (found == null) {
            new MaterialAlertDialogBuilder(this).setTitle("Not in your playlists")
                    .setMessage("Homefield only plays films and series from your own playlists, and none of them has "
                            + t.title + ". Add a playlist that has it in Settings, Live TV.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        if (!"series".equals(found.kind)) {
            chooseSource(found.plays(), plays -> startActivity(com.gathin.tvhomephone.live.VodPlayerActivity.intent(this, plays, t.title,
                    com.gathin.tvhomephone.live.VodPlayerActivity.meta(t.title, t.id, false, t.poster, 0, 0, ""), "")));
            return;
        }
        if (episode != null) {
            for (com.gathin.tvhomephone.live.Vod.Episode e : foundEps) {
                if (e.season == episode[0] && e.number == episode[1]) {
                    playEpisode(e, foundEps);
                    return;
                }
            }
            Toast.makeText(this, "S" + episode[0] + " E" + episode[1] + " isn't in your playlists", Toast.LENGTH_LONG).show();
            return;
        }
        pickEpisode(foundEps);
    }

    /** One episode (from the Episode map), from the playlists only. */
    void playEpisode(int season, int number) {
        int[] ep = {season, number};
        if (looked && !looking) playFound(ep);
        else lookInPlaylists(() -> playFound(ep));
    }

    private void playEpisode(com.gathin.tvhomephone.live.Vod.Episode e, List<com.gathin.tvhomephone.live.Vod.Episode> eps) {
        int season = e.season;
        // With the whole series' episodes, for the next one to follow on.
        chooseSource(e.plays(), plays -> startActivity(com.gathin.tvhomephone.live.VodPlayerActivity.intent(this, plays,
                t.title + (season > 0 ? " · S" + season : "") + " E" + e.number,
                com.gathin.tvhomephone.live.VodPlayerActivity.meta(t.title, t.id, true, t.poster, e.season, e.number, e.title),
                com.gathin.tvhomephone.live.Vod.episodesJson(eps))));
    }

    /** A series from the playlist: its season, then its episode. */
    private void pickEpisode(List<com.gathin.tvhomephone.live.Vod.Episode> eps) {
        List<Integer> seasons = new ArrayList<>();
        for (com.gathin.tvhomephone.live.Vod.Episode e : eps) if (!seasons.contains(e.season)) seasons.add(e.season);
        if (seasons.size() == 1) {
            pickIn(eps, seasons.get(0));
            return;
        }
        String[] names = new String[seasons.size()];
        for (int i = 0; i < names.length; i++) names[i] = seasons.get(i) > 0 ? "Season " + seasons.get(i) : "Episodes";
        new MaterialAlertDialogBuilder(this).setTitle(t.title + " · from your playlist")
                .setItems(names, (d, w) -> pickIn(eps, seasons.get(w))).show();
    }

    private void pickIn(List<com.gathin.tvhomephone.live.Vod.Episode> eps, int season) {
        List<com.gathin.tvhomephone.live.Vod.Episode> in = new ArrayList<>();
        for (com.gathin.tvhomephone.live.Vod.Episode e : eps) if (e.season == season) in.add(e);
        String[] names = new String[in.size()];
        for (int i = 0; i < names.length; i++) {
            com.gathin.tvhomephone.live.Vod.Episode e = in.get(i);
            String own = e.title == null ? "" : e.title.trim();
            names[i] = "Episode " + e.number + (own.isEmpty() || own.equalsIgnoreCase(t.title) ? "" : " · " + own);
        }
        new MaterialAlertDialogBuilder(this).setTitle(season > 0 ? t.title + " · Season " + season : t.title)
                .setItems(names, (d, w) -> playEpisode(in.get(w), eps)).show();
    }

    private interface Chosen {
        void play(List<String[]> plays);
    }

    /**
     * More than one source (another playlist, another version: "Main · 4K · MKV", "Backup · HD"): which to play, the
     * best match first; the others stay its backups. One source plays straight away.
     */
    private void chooseSource(List<String[]> plays, Chosen chosen) {
        if (plays.size() < 2) {
            chosen.play(plays);
            return;
        }
        String[] names = new String[plays.size()];
        for (int i = 0; i < names.length; i++) {
            String[] p = plays.get(i);
            String l = p.length > 3 && p[3] != null && !p[3].isEmpty() ? p[3] : "Source " + (i + 1);
            names[i] = i == 0 ? l + "  ·  best match" : l;
        }
        new MaterialAlertDialogBuilder(this).setTitle("Choose a source")
                .setItems(names, (d, w) -> chosen.play(com.gathin.tvhomephone.live.VodPlayerActivity.chosenFirst(plays, w)))
                .show();
    }

    private void toggleList() {
        boolean in = SyncStore.get(this, "mylist", t.key()) != null;
        if (in) SyncStore.delete(this, "mylist", t.key());
        else SyncStore.put(this, "mylist", t.key(), t.listData());
        paintButtons();
    }

    /** How was it?: Not for me, Liked it or Loved it, the same on every device (a synced rating). */
    private void rate() {
        String[] words = {"♥  Loved it", "👍  Liked it", "Not for me", "Clear"};
        int[] values = {2, 1, -1, 0};
        new MaterialAlertDialogBuilder(this).setTitle("How was " + t.title + "?").setItems(words, (d, w) -> {
            if (values[w] == 0) SyncStore.delete(this, "rating", t.key());
            else {
                JSONObject r = new JSONObject();
                try {
                    r.put("value", values[w]).put("title", t.title).put("id", t.id).put("tv", t.tv).put("at", System.currentTimeMillis());
                } catch (JSONException ignored) {
                    // plain values
                }
                SyncStore.put(this, "rating", t.key(), r);
            }
            paintButtons();
        }).show();
    }

    /** The official trailer on YouTube (the app if it's on the phone, else the browser). */
    private void trailer() {
        String key = null;
        JSONObject v = details == null ? null : details.optJSONObject("videos");
        JSONArray r = v == null ? null : v.optJSONArray("results");
        for (String want : new String[]{"Trailer", "Teaser", "Clip"}) {
            for (int i = 0; r != null && i < r.length() && key == null; i++) {
                JSONObject x = r.optJSONObject(i);
                if (x != null && "YouTube".equals(x.optString("site")) && want.equals(x.optString("type"))) key = x.optString("key");
            }
            if (key != null) break;
        }
        Uri u = key != null ? Uri.parse("https://www.youtube.com/watch?v=" + key)
                : Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(t.title + " official trailer"));
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, u));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app here plays YouTube", Toast.LENGTH_SHORT).show();
        }
    }

    private String metaLine(JSONObject d) {
        StringBuilder b = new StringBuilder();
        String date = d.optString(t.tv ? "first_air_date" : "release_date", "");
        if (date.length() >= 4) b.append(date, 0, 4);
        if (t.tv) {
            int s = d.optInt("number_of_seasons"), e = d.optInt("number_of_episodes");
            if (s > 0) b.append(b.length() > 0 ? " · " : "").append(s).append(s == 1 ? " season" : " seasons");
            if (e > 0) b.append(" · ").append(e).append(" episodes");
        } else if (d.optInt("runtime") > 0) {
            b.append(b.length() > 0 ? " · " : "").append(Plan.length(d.optInt("runtime")));
        }
        JSONArray g = d.optJSONArray("genres");
        for (int i = 0; g != null && i < Math.min(3, g.length()); i++) b.append(" · ").append(g.optJSONObject(i).optString("name"));
        if (d.optDouble("vote_average", 0) > 0) b.append(String.format(Locale.UK, " · ★ %.1f", d.optDouble("vote_average")));
        return b.toString();
    }

    private static String logo(JSONObject d) {
        JSONObject im = d.optJSONObject("images");
        JSONArray logos = im == null ? null : im.optJSONArray("logos");
        for (int i = 0; logos != null && i < logos.length(); i++) {
            JSONObject l = logos.optJSONObject(i);
            if (l != null && ("en".equals(l.optString("iso_639_1")) || l.isNull("iso_639_1"))) return l.optString("file_path");
        }
        return logos != null && logos.length() > 0 ? logos.optJSONObject(0).optString("file_path") : null;
    }

    /** The cast in rings, as the TVs' panel has them: picture, name and part. */
    private View cast(JSONObject d) {
        LinearLayout box = Ui.column(this);
        JSONObject credits = d.optJSONObject("credits");
        JSONArray cast = credits == null ? null : credits.optJSONArray("cast");
        if (cast == null || cast.length() == 0) return box;
        TextView head = Ui.text(this, "Cast", 18, 0xFFFFFFFF, true);
        head.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), 0, Ui.dp(this, 8));
        box.addView(head);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.row(this);
        row.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        for (int i = 0; i < Math.min(15, cast.length()); i++) {
            JSONObject p = cast.optJSONObject(i);
            if (p == null) continue;
            LinearLayout one = Ui.column(this);
            one.setGravity(Gravity.CENTER_HORIZONTAL);
            ImageView face = new ImageView(this);
            face.setBackground(Ui.round(0x24FFFFFF, 40, this));
            String path = Tmdb.image(p.optString("profile_path", null), "w185");
            if (path != null) Glide.with(this).load(path).transform(new CircleCrop()).into(face);
            one.addView(face, new LinearLayout.LayoutParams(Ui.dp(this, 76), Ui.dp(this, 76)));
            TextView n = Ui.text(this, p.optString("name"), 12, 0xFFFFFFFF, true);
            n.setGravity(Gravity.CENTER);
            n.setMaxLines(2);
            one.addView(n, new LinearLayout.LayoutParams(Ui.dp(this, 92), -2));
            TextView part = Ui.text(this, p.optString("character"), 11, Ui.DIM, false);
            part.setGravity(Gravity.CENTER);
            part.setMaxLines(2);
            one.addView(part, new LinearLayout.LayoutParams(Ui.dp(this, 92), -2));
            row.addView(one, Ui.margins(Ui.wrap(), this, 4, 0, 4, 0));
        }
        hs.addView(row);
        box.addView(hs, Ui.fill());
        return box;
    }
}
