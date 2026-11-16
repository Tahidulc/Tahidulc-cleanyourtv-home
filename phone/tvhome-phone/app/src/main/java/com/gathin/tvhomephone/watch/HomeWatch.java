package com.gathin.tvhomephone.watch;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.MainActivity;
import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.sync.Tvs;

import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Home's watching cards (Home itself is phone-sports'): tonight's Film night when one is planned, Continue watching
 * from the TVs, and what's new today from Coming up. Each part shows only when it has something.
 */
public final class HomeWatch {
    private HomeWatch() {}

    public static View build(Fragment host, ViewGroup parent) {
        return build(host, parent, true);
    }

    /**
     * The cards; withResume false leaves out the TVs' Continue watching (Home's own row has it, with this phone's
     * films and episodes).
     */
    public static View build(Fragment host, ViewGroup parent, boolean withResume) {
        Context c = host.requireContext();
        LinearLayout box = Ui.column(c);
        if (!Tvs.paired(c)) {
            box.addView(Ui.pairCard(c, () -> {
                if (host.getActivity() instanceof MainActivity) ((MainActivity) host.getActivity()).open(R.id.tab_settings);
            }), Ui.margins(Ui.fill(), c, 16, 12, 16, 12));
            return box;
        }
        LinearLayout night = Ui.column(c);
        box.addView(night, Ui.fill());
        filmNight(host, night);

        PosterAdapter resume = new PosterAdapter((t, i) -> TitleActivity.open(c, t), null, com.gathin.tvhomephone.glass.PosterCard.rowWidth(c));
        LinearLayout resumeBox = rowSection(c, "Continue watching", resume);
        box.addView(resumeBox, Ui.fill());
        List<Title> cached = withResume ? Lists.resume(c) : new ArrayList<>();
        resume.set(cached);
        resumeBox.setVisibility(cached.isEmpty() ? View.GONE : View.VISIBLE);
        if (withResume) Tvs.watching(c, (w, e) -> {
            if (!host.isAdded()) return;
            List<Title> now = Lists.resume(c);
            resume.set(now);
            resumeBox.setVisibility(now.isEmpty() ? View.GONE : View.VISIBLE);
        });

        PosterAdapter fresh = new PosterAdapter((t, i) -> TitleActivity.open(c, t), null, com.gathin.tvhomephone.glass.PosterCard.rowWidth(c));
        LinearLayout freshBox = rowSection(c, "New today", fresh);
        freshBox.setVisibility(View.GONE);
        box.addView(freshBox, Ui.fill());
        if (Tmdb.ready(c)) App.io.execute(() -> {
            List<Title> today = new ArrayList<>();
            try {
                long end = LocalDate.now().plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                for (Title t : ComingUp.load(c, 0)) if (t.when < end) today.add(t);
            } catch (IOException | RuntimeException ignored) {
                // nothing new shown
            }
            App.main.post(() -> {
                if (!host.isAdded()) return;
                fresh.set(today);
                freshBox.setVisibility(today.isEmpty() ? View.GONE : View.VISIBLE);
            });
        });
        return box;
    }

    private static LinearLayout rowSection(Context c, String name, PosterAdapter adapter) {
        LinearLayout box = Ui.column(c);
        box.addView(com.gathin.tvhomephone.glass.Glass.sectionRow(c, name, null));
        RecyclerView row = new RecyclerView(c);
        row.setLayoutManager(new LinearLayoutManager(c, LinearLayoutManager.HORIZONTAL, false));
        row.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 10), 0);
        row.setClipToPadding(false);
        row.setAdapter(adapter);
        box.addView(row, Ui.fill());
        return box;
    }

    /** Tonight's Film night, when one is planned for today: the poster, the time and when it ends. */
    private static void filmNight(Fragment host, LinearLayout into) {
        Context c = host.requireContext();
        JSONObject p = FilmNightSheet.plan(c);
        if (p == null) return;
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(p.optLong("start"));
        if (start.get(Calendar.DAY_OF_YEAR) != Calendar.getInstance().get(Calendar.DAY_OF_YEAR)) return;
        LinearLayout card = Ui.row(c);
        int pad = Ui.dp(c, 14);
        card.setPadding(pad, pad, pad, pad);
        card.setBackground(Ui.glass(c));
        ImageView poster = new ImageView(c);
        String url = p.optString("poster", "");
        if (!url.isEmpty()) Glide.with(poster).load(url).transform(new CenterCrop(), new RoundedCorners(Ui.dp(c, 8))).into(poster);
        card.addView(poster, new LinearLayout.LayoutParams(Ui.dp(c, 60), Ui.dp(c, 90)));
        LinearLayout words = Ui.column(c);
        words.setPadding(Ui.dp(c, 14), 0, 0, 0);
        words.addView(Ui.label(c, "FILM NIGHT · TONIGHT", Ui.MOVIES));
        words.addView(Ui.text(c, p.optString("title"), 18, 0xFFFFFFFF, true));
        int startMin = start.get(Calendar.HOUR_OF_DAY) * 60 + start.get(Calendar.MINUTE);
        int end = startMin + p.optInt("runtime", 120);
        int spare = Plan.spare(end, WatchPrefs.bedtime(c));
        words.addView(Ui.text(c, "Starts " + Plan.hm(startMin) + " · ends " + Plan.hm(end)
                + (spare >= 0 ? ", " + spare + " min before bed" : ""), 14, Ui.SOFT, false));
        card.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        Title t = new Title();
        t.id = p.optInt("id");
        t.tv = false;
        t.title = p.optString("title");
        t.poster = url.isEmpty() ? null : url;
        card.setOnClickListener(v -> TitleActivity.open(c, t));
        into.addView(card, Ui.margins(Ui.fill(), c, 16, 12, 16, 4));
    }
}
