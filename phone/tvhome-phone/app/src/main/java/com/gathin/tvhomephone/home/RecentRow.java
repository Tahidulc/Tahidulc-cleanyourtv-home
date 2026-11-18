package com.gathin.tvhomephone.home;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.glass.Glass;
import com.gathin.tvhomephone.live.PlayerActivity;
import com.gathin.tvhomephone.live.Watched;
import com.gathin.tvhomephone.sports.Competitions;
import com.gathin.tvhomephone.sports.Game;
import com.gathin.tvhomephone.sports.GameActivity;
import com.gathin.tvhomephone.sports.LiveClock;
import com.gathin.tvhomephone.sports.Ui;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Home's Recently watched, in glass, under the alerts: the films, episodes, games and programmes watched on this
 * phone, all together and newest first (Watched). Each card is a rounded tile under the glass rim, with its own kind's
 * look. A film or episode shows its poster and how far in. A game shows both crests and its score, as it is now or as
 * it ended. A programme shows its channel's logo and title on frosted glass. The time it was watched is underneath.
 * Tap plays it again: a film or episode from where it was left, a series watched to the end of an episode at the next
 * one, a programme's channel, a game still on its channel, else its match page. Hold takes it off. The row is hidden
 * while there is nothing.
 */
final class RecentRow {
    /** A card 112dp wide (its height its own), its corners rounded 14: smaller than the poster rows above it. */
    static final int CARD_DP = 112, RADIUS_DP = 14;
    private static final int LIVE = 0xFFE5333B;
    /** The warm dark a game's two clubs' colours are deepened towards. */
    private static final int DUSK = 0xFF1C1A20;
    /** Home's sides. */
    private static final int SIDE_DP = 16;

    private final Fragment host;
    private final LinearLayout box;
    private final Cards cards = new Cards();
    /** What the cards show, so a read that changes nothing leaves them as they are. */
    private String shown = "";

    RecentRow(Fragment host) {
        this.host = host;
        Context c = host.requireContext();
        // The heading as Home's other sections have it, its cards under it, the first in line with Home's others.
        box = Ui.column(c);
        box.addView(Glass.sectionRow(c, "Recently watched", null));
        RecyclerView row = new RecyclerView(c);
        row.setLayoutManager(new LinearLayoutManager(c, LinearLayoutManager.HORIZONTAL, false));
        int half = Glass.dp(c, Rows.GAP / 2f);
        row.setPadding(Glass.dp(c, SIDE_DP) - half, 0, Glass.dp(c, SIDE_DP) - half, 0);
        row.setClipToPadding(false);
        row.setOverScrollMode(View.OVER_SCROLL_NEVER);
        row.setHorizontalScrollBarEnabled(false);
        row.setAdapter(cards);
        row.setNestedScrollingEnabled(false);
        box.addView(row, Ui.matchWrap());
        box.setVisibility(View.GONE);
    }

    View view() {
        return box;
    }

    /** Read again (this phone's own list: quick), after watching something or a game's score moving on. */
    void refresh() {
        if (!host.isAdded()) return;
        List<Watched.Entry> list = Watched.list(host.requireContext());
        StringBuilder sig = new StringBuilder();
        for (Watched.Entry e : list) {
            sig.append(e.kind).append('|').append(e.at).append('|').append(e.name()).append('|').append(Watched.when(e.at));
            if (e.isVod()) sig.append('|').append(e.finished).append('|').append(e.vod == null ? 0 : e.vod.position);
            if (e.game != null) sig.append('|').append(e.game.homeScore).append('-').append(e.game.awayScore).append('|').append(state(e.game));
            sig.append('\n');
        }
        if (sig.toString().equals(shown)) return;
        shown = sig.toString();
        cards.set(list);
        box.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ---- what a tap does ------------------------------------------------------------------------------------------

    private void open(Context c, Watched.Entry e) {
        if (e.isVod()) {
            if (e.kind == Watched.FILM && e.finished) {
                new MaterialAlertDialogBuilder(c).setTitle(e.name()).setMessage("Watch it again from the start?")
                        .setPositiveButton("Play", (d, w) -> start(c, e)).setNegativeButton("Not now", null).show();
            } else {
                start(c, e);
            }
            return;
        }
        Game g = e.kind == Watched.SPORT ? e.game : null;
        if (g != null && !Watched.onNow(g, System.currentTimeMillis())) {
            c.startActivity(GameActivity.intent(c, g));
            return;
        }
        // Its channel again, while the playlists still have it (read first if they haven't been yet).
        App.io.execute(() -> {
            boolean has = Watched.hasChannel(c, e);
            App.main.post(() -> {
                if (!host.isAdded()) return;
                if (has) c.startActivity(PlayerActivity.intent(c, e.channelKey));
                else if (g != null) c.startActivity(GameActivity.intent(c, g));
                else Toast.makeText(c, e.channelName + " isn't in your playlists any more", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private static void start(Context c, Watched.Entry e) {
        android.content.Intent i = Watched.play(c, e);
        if (i != null) c.startActivity(i);
    }

    private void forget(Context c, Watched.Entry e) {
        Watched.remove(c, e);
        shown = "";
        Toast.makeText(c, e.name() + " taken off Recently watched", Toast.LENGTH_SHORT).show();
        refresh();
    }

    // ---- the cards --------------------------------------------------------------------------------------------------

    private final class Cards extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<Watched.Entry> items = new ArrayList<>();

        void set(List<Watched.Entry> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @Override public int getItemCount() {
            return items.size();
        }

        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            Context c = parent.getContext();
            LinearLayout cell = Ui.column(c);
            // Half the gap each side of a card: the cards Home's 12dp apart.
            int half = Glass.dp(c, Rows.GAP / 2f);
            cell.setPadding(half, 0, half, half);
            cell.setLayoutParams(new RecyclerView.LayoutParams(Glass.dp(c, CARD_DP) + 2 * half, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerView.ViewHolder(cell) {};
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
            Watched.Entry e = items.get(position);
            LinearLayout cell = (LinearLayout) h.itemView;
            cell.removeAllViews();
            Context c = cell.getContext();
            FrameLayout frame = new Poster(c);
            if (e.isVod()) vod(c, frame, e);
            else if (e.kind == Watched.SPORT && e.game != null) game(c, frame, e.game, e.channelName);
            else programme(c, frame, e);
            cell.addView(frame, new LinearLayout.LayoutParams(Glass.dp(c, CARD_DP), ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView name = Glass.text(c, under(e), 13, Glass.SOFT, Glass.bodySemi(c));
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setPadding(Glass.dp(c, 2), Glass.dp(c, 8), Glass.dp(c, 2), 0);
            cell.addView(name, new LinearLayout.LayoutParams(Glass.dp(c, CARD_DP), ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView when = Glass.text(c, Watched.when(e.at), 11, Glass.MUTED, Glass.body(c));
            when.setSingleLine(true);
            when.setPadding(Glass.dp(c, 2), Glass.dp(c, 4), Glass.dp(c, 2), 0);
            cell.addView(when);
            cell.setContentDescription(describe(e));
            cell.setOnClickListener(v -> open(v.getContext(), e));
            cell.setOnLongClickListener(v -> {
                forget(v.getContext(), e);
                return true;
            });
        }
    }

    /** The line under the card: the film's or series' name, the game's clubs, or the programme's channel. */
    private static String under(Watched.Entry e) {
        if (e.isVod()) return e.name();
        if (e.kind == Watched.SPORT && e.game != null) return e.game.title();
        return e.channelName;
    }

    private static String describe(Watched.Entry e) {
        String kind = e.kind == Watched.FILM ? "Film" : e.kind == Watched.EPISODE ? "TV show" : e.kind == Watched.SPORT ? "Sport" : e.tag;
        String line = e.isVod() ? Watched.vodLine(e) : e.kind == Watched.SPORT && e.game != null ? scoreWords(e.game) : e.title;
        return kind + ", " + e.name() + (line.isEmpty() ? "" : ", " + line) + (e.isVod() ? "" : ", on " + e.channelName)
                + ", watched " + Watched.when(e.at);
    }

    // A film or an episode: its poster (its own colours without one), how far in, and what's next.
    private void vod(Context c, FrameLayout frame, Watched.Entry e) {
        frame.setBackground(Glass.placeholder(e.name(), Glass.dp(c, RADIUS_DP)));
        String poster = e.vod == null ? "" : e.vod.poster;
        if (poster != null && !poster.isEmpty()) {
            ImageView image = new ImageView(c);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            frame.addView(image, new FrameLayout.LayoutParams(-1, -1));
            try {
                Glide.with(image).load(poster).centerCrop().into(image);
            } catch (RuntimeException ignored) {
                // Home went away
            }
        } else {
            frame.addView(middle(c, e.name()), new FrameLayout.LayoutParams(-1, -1));
        }
        fade(c, frame);
        badge(c, frame, e.kind == Watched.FILM ? "FILM" : "TV SHOW", false);
        foot(c, frame, Watched.vodLine(e), e.finished || e.vod == null ? -1 : e.vod.progress(), 2);
        frame.setForeground(Glass.rim(c, RADIUS_DP));
    }

    // A game: both crests side by side, the score and where it is (or how it ended), the competition in the corner.
    private void game(Context c, FrameLayout frame, Game g, String channel) {
        frame.setBackground(clubs(g));
        boolean live = Watched.onNow(g, System.currentTimeMillis());
        String comp = Competitions.logo(g);
        if (comp != null) {
            ImageView logo = new ImageView(c);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20), Gravity.TOP | Gravity.END);
            lp.setMargins(0, Ui.dp(c, 8), Ui.dp(c, 8), 0);
            frame.addView(logo, lp);
            Ui.load(logo, comp, null, () -> logo.setVisibility(View.GONE));
        }
        LinearLayout col = Ui.column(c);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout crests = Ui.row(c);
        crests.setGravity(Gravity.CENTER);
        crests.addView(crest(c, g.homeLogo), new LinearLayout.LayoutParams(Ui.dp(c, 38), Ui.dp(c, 38)));
        crests.addView(new View(c), new LinearLayout.LayoutParams(Ui.dp(c, 10), 1));
        crests.addView(crest(c, g.awayLogo), new LinearLayout.LayoutParams(Ui.dp(c, 38), Ui.dp(c, 38)));
        col.addView(crests, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        String score = g.state == Game.PRE ? "v" : g.homeScore + " – " + g.awayScore;
        TextView s = Glass.text(c, score, 26, Glass.TEXT, Glass.heading(c, 700));
        s.setGravity(Gravity.CENTER);
        s.setShadowLayer(6, 0, 1, 0x99000000);
        col.addView(s, Ui.margins(c, 0, 4, 0, 0));
        String state = state(g);
        if (!state.isEmpty()) {
            TextView st = Glass.text(c, state, 12, live ? 0xFFFF8A8F : Glass.SOFT, Glass.bodySemi(c));
            st.setGravity(Gravity.CENTER);
            st.setLetterSpacing(0.04f);
            col.addView(st, Ui.margins(c, 0, 4, 0, 0));
        }
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        cp.topMargin = Ui.dp(c, 36);
        frame.addView(col, cp);
        fade(c, frame);
        badge(c, frame, live ? "LIVE" : "SPORT", live);
        foot(c, frame, channel, -1, 1);
        frame.setForeground(Glass.rim(c, RADIUS_DP));
    }

    // A programme: its channel's logo and its title, on frosted glass.
    private void programme(Context c, FrameLayout frame, Watched.Entry e) {
        frame.setBackground(Glass.card(c, RADIUS_DP));
        TextView name = middle(c, e.channelName);
        name.setVisibility(View.GONE);
        frame.addView(name, new FrameLayout.LayoutParams(-1, Ui.dp(c, 120), Gravity.TOP));
        if (!e.channelLogo.isEmpty()) {
            ImageView logo = new ImageView(c);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            // On a tile in its own colour (or black), a little wider for its room; or as it is on the glass (LogoTiles).
            boolean boxed = com.gathin.tvhomephone.live.LogoTiles.boxed(c);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.dp(c, boxed ? 92 : 78), Ui.dp(c, 54), Gravity.CENTER_HORIZONTAL | Gravity.TOP);
            lp.topMargin = Ui.dp(c, 44);
            frame.addView(logo, lp);
            int p = boxed ? com.gathin.tvhomephone.live.LogoTiles.pad(c, 7) : 0;
            logo.setPadding(p, p, p, p);
            com.gathin.tvhomephone.live.LogoTiles.show(logo, 10, false, logo, e.channelLogo, com.gathin.tvhomephone.live.LogoTiles.ON_NAVY, () -> {
                logo.setVisibility(View.GONE);
                name.setVisibility(View.VISIBLE);
            });
        } else {
            name.setVisibility(View.VISIBLE);
        }
        fade(c, frame);
        badge(c, frame, e.tag.isEmpty() ? "TV" : e.tag.toUpperCase(Locale.UK), false);
        foot(c, frame, e.title.isEmpty() ? e.channelName : e.title, -1, 2);
    }

    /** Where the game is: "FT", "HT", "67'", or its kick-off ("KO 19:45"); "" for one last seen on that isn't now. */
    static String state(Game g) {
        if (g.state == Game.POST) return g.detail == null || g.detail.isEmpty() ? "FT" : g.detail;
        if (g.state == Game.LIVE) {
            long now = System.currentTimeMillis();
            if (!Watched.onNow(g, now)) return "";
            return g.halftime ? "HT" : LiveClock.words(g, now);
        }
        if (g.kickoff <= 0) return "";
        return "KO " + java.time.Instant.ofEpochMilli(g.kickoff).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm", Locale.UK));
    }

    private static String scoreWords(Game g) {
        String st = state(g);
        if (g.state == Game.PRE) return st;
        return g.homeScore + " " + g.awayScore + (st.isEmpty() ? "" : ", " + st);
    }

    // ---- the parts --------------------------------------------------------------------------------------------------

    private static ImageView crest(Context c, String url) {
        ImageView v = new ImageView(c);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        Ui.load(v, url);
        return v;
    }

    /** The two clubs' colours, deepened towards the warm dark, from one corner to the other. */
    private static GradientDrawable clubs(Game g) {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{deep(g.homeColor, DUSK), DUSK, deep(g.awayColor, DUSK)});
    }

    /** An ESPN colour ("c8102e") halfway to the ground given, a little darker; the ground itself when there is none. */
    static int deep(String hex, int ground) {
        if (hex == null || !hex.matches("[0-9a-fA-F]{6}")) return ground;
        int rgb = Integer.parseInt(hex, 16);
        int r = ((rgb >> 16 & 0xFF) + Color.red(ground)) / 2, g = ((rgb >> 8 & 0xFF) + Color.green(ground)) / 2,
                b = ((rgb & 0xFF) + Color.blue(ground)) / 2;
        return Color.rgb(r * 3 / 4, g * 3 / 4, b * 3 / 4);
    }

    private static TextView middle(Context c, String words) {
        TextView t = Glass.text(c, words, 16, Glass.TEXT, Glass.heading(c, 600));
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(3);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 10), 0);
        return t;
    }

    /** Darker towards the foot, so the words along it read over any picture. */
    private static void fade(Context c, FrameLayout frame) {
        View fade = new View(c);
        fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x00000000, 0xD9000000}));
        frame.addView(fade, new FrameLayout.LayoutParams(-1, -1));
    }

    /** The kind's small chip in the top corner: dark glass with white words, red while a game is on. */
    private static void badge(Context c, FrameLayout frame, String words, boolean live) {
        TextView b = Glass.text(c, words, 10, Glass.TEXT, Glass.bodySemi(c));
        b.setLetterSpacing(0.08f);
        b.setPadding(Ui.dp(c, 7), Ui.dp(c, 3), Ui.dp(c, 7), Ui.dp(c, 3));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(live ? LIVE : 0x8C0E0F14);
        bg.setCornerRadius(Ui.dp(c, 7));
        if (!live) bg.setStroke(Math.max(1, Glass.dp(c, 0.5f)), 0x33FFFFFF);
        b.setBackground(bg);
        b.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        lp.setMargins(Ui.dp(c, 8), Ui.dp(c, 8), 0, 0);
        frame.addView(b, lp);
    }

    /** The words along the card's foot, over the fade, with a fine line for how far in (progress below 0: none). */
    private static void foot(Context c, FrameLayout frame, String words, float progress, int lines) {
        int side = Ui.dp(c, 8);
        if (words != null && !words.isEmpty()) {
            TextView t = Glass.text(c, words, 12, Glass.TEXT, Glass.bodySemi(c));
            t.setMaxLines(lines);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
            lp.setMargins(side, 0, side, Ui.dp(c, progress >= 0 ? 17 : 9));
            frame.addView(t, lp);
        }
        if (progress < 0) return;
        // A faint track with the part watched in white, as Continue watching's cards draw it.
        int h = Ui.dp(c, 3);
        View back = new View(c);
        back.setBackground(Art.round(0x4DFFFFFF, h / 2f));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, h, Gravity.BOTTOM);
        bp.setMargins(side, 0, side, side);
        frame.addView(back, bp);
        View bar = new View(c);
        bar.setBackground(Art.round(0xFFFFFFFF, h / 2f));
        float p = Math.max(0.03f, Math.min(1f, progress));
        int width = Math.max(h, Math.round((Ui.dp(c, CARD_DP) - 2 * side) * p));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, h, Gravity.BOTTOM | Gravity.START);
        lp.setMargins(side, 0, 0, side);
        frame.addView(bar, lp);
    }

    /** A card two parts wide to three tall, as posters are, its corners rounded as Home's tiles are. */
    private static final class Poster extends FrameLayout {
        Poster(Context c) {
            super(c);
            Glass.round(this, Glass.dp(c, RADIUS_DP));
        }

        @Override protected void onMeasure(int w, int h) {
            int width = MeasureSpec.getSize(w);
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(width * 3 / 2, MeasureSpec.EXACTLY));
        }
    }
}
