package com.gathin.tvhomephone.home;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.MainActivity;
import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.SyncStore;
import com.gathin.tvhomephone.glass.Glass;
import com.gathin.tvhomephone.live.BreakingNews.Story;
import com.gathin.tvhomephone.live.FanFeed;
import com.gathin.tvhomephone.live.Watched;
import com.gathin.tvhomephone.sports.AlertLog;
import com.gathin.tvhomephone.sports.Channels;
import com.gathin.tvhomephone.sports.Clubs;
import com.gathin.tvhomephone.sports.Competitions;
import com.gathin.tvhomephone.sports.Espn;
import com.gathin.tvhomephone.sports.Game;
import com.gathin.tvhomephone.sports.GameActivity;
import com.gathin.tvhomephone.sports.GameRow;
import com.gathin.tvhomephone.sports.ScoreBug;
import com.gathin.tvhomephone.sports.Ui;
import com.gathin.tvhomephone.sports.Weather;
import com.gathin.tvhomephone.sync.Tvs;
import com.gathin.tvhomephone.watch.HomeWatch;
import com.gathin.tvhomephone.watch.Lists;
import com.gathin.tvhomephone.watch.Title;
import com.gathin.tvhomephone.watch.TitleActivity;
import com.gathin.tvhomephone.watch.WatchFragment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Home, in glass (HomeScreen lays it out): the family's greeting with the date and Manchester's weather; the pill to
 * Watch's films and TV, its search, and Live TV; a frosted card for each section below; Continue watching (what you're
 * part-way through, each over its own backdrop with its logo); Favourites from My list; the scores box (today's games,
 * your clubs and live ones first, moving on by itself every 8 seconds as the TV's does) and your clubs' games today;
 * the films and TV picked for you from your own playlists; BBC News's and BBC Sport's headlines; what fans of your
 * clubs are posting (read again every two minutes while Home shows, new posts dropping in); the latest alerts;
 * Recently watched; then the TVs' cards. Every row reads at the same time, off the main thread, and shows what it had
 * last time until then.
 */
public class HomeFragment extends Fragment implements HomeScreen.Host {
    private HomeScreen screen;
    private TextView count, clubs;
    private RecyclerView bugs;
    /** The games of the clubs the family follows, marked Your club on the scores box. */
    private final Set<String> mineIds = new HashSet<>();
    private final List<Game> shown = new ArrayList<>();
    private LinearLayout today, todayBox;
    /** Recently watched, under the alerts. */
    private RecentRow recent;
    private boolean visible, touching;
    private int gen, picksGen, newsGen, listGen, socialGen;
    private Pager pager;
    /**
     * Each Continue watching title's backdrop and logo (by its card's key), kept while it is in Continue watching, so
     * Home shows them at once when it comes back; and the ones being read now.
     */
    private static final Map<String, Pick> ARTS = new HashMap<>();
    private final Set<String> reading = new HashSet<>();
    /** My list as it was last read, shown at once when Home comes back. */
    private static List<Pick> listKept;
    /** The scores box moves on to its next game every AUTO_MS, as the TV's does. */
    private static final long AUTO_MS = 8_000L;
    private static final int LOOPS = 1000;
    private final Runnable auto = this::advance;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!visible) return;
            stamp();
            App.main.postDelayed(this, 15_000L);
        }
    };
    private final Runnable reread = new Runnable() {
        @Override public void run() {
            if (!visible) return;
            load();
            App.main.postDelayed(this, 30_000L);
        }
    };
    /** The fans' posts read again every two minutes while Home shows (the first at once). */
    private final Runnable socialAgain = new Runnable() {
        @Override public void run() {
            if (!visible) return;
            social(false);
            App.main.postDelayed(this, 120_000L);
        }
    };
    /** Something kept changed (My list here, or synced from a TV): Favourites read again. */
    private final Runnable listChanged = () -> App.main.post(this::favourites);

    @Override public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle state) {
        Context c = requireContext();
        screen = new HomeScreen(c, this);
        screen.swipe.setOnRefreshListener(() -> {
            load();
            picks(true);
            headlines(true);
            favourites();
            social(true);
        });

        // The scores, in their frosted box: the box to swipe through, its count, then the clubs' games today.
        LinearLayout box = screen.sportsBox;
        bugs = new RecyclerView(c);
        bugs.setLayoutManager(new LinearLayoutManager(c, LinearLayoutManager.HORIZONTAL, false));
        bugs.setNestedScrollingEnabled(false);
        bugs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        pager = new Pager();
        pager.attachToRecyclerView(bugs);
        bugs.setAdapter(new Bugs());
        bugs.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrollStateChanged(@NonNull RecyclerView r, int s) {
                if (s == RecyclerView.SCROLL_STATE_IDLE) {
                    counter();
                    autoLater();
                } else if (s == RecyclerView.SCROLL_STATE_DRAGGING) {
                    App.main.removeCallbacks(auto);
                }
            }
        });
        // A finger on the box holds it; it moves on again AUTO_MS after it is let go.
        bugs.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override public boolean onInterceptTouchEvent(@NonNull RecyclerView r, @NonNull MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) {
                    touching = true;
                    App.main.removeCallbacks(auto);
                } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                    touching = false;
                    autoLater();
                }
                return false;
            }
        });
        box.addView(bugs, Ui.matchWrap());
        count = Glass.text(c, "", 12, Glass.MUTED, Glass.body(c));
        count.setGravity(Gravity.CENTER);
        count.setPadding(0, Glass.dp(c, 10), 0, Glass.dp(c, 2));
        count.setVisibility(View.GONE);
        box.addView(count, Ui.matchWrap());
        todayBox = Ui.column(c);
        clubs = Glass.text(c, "YOUR CLUBS TODAY", 12, Glass.MUTED, Glass.bodySemi(c));
        clubs.setLetterSpacing(0.12f);
        clubs.setPadding(Glass.dp(c, 2), Glass.dp(c, 16), 0, Glass.dp(c, 10));
        todayBox.addView(clubs);
        today = Ui.column(c);
        todayBox.addView(today, Ui.matchWrap());
        todayBox.setVisibility(View.GONE);
        box.addView(todayBox, Ui.matchWrap());

        recent = new RecentRow(this);
        screen.recent.addView(recent.view(), Ui.matchWrap());
        recent.refresh();
        // The TVs' cards are Watch's own; Home only places them, at its foot.
        screen.tvs.addView(HomeWatch.build(this, screen.tvs, false), Ui.matchWrap());

        // What it had last time, at once; then everything read again at the same time.
        screen.feed(Feed.kept(c));
        screen.news(Headlines.kept(c));
        if (FanFeed.on(c)) screen.social(FanFeed.kept(), FanFeed.keptClubs());
        if (listKept != null) screen.favourites(listKept);
        resume();
        if (Tvs.paired(c)) Tvs.watching(c, (w, e) -> {
            if (isAdded()) resume();
        });
        picks(false);
        headlines(false);
        stamp();
        return screen.view();
    }

    @Override public void onResume() {
        super.onResume();
        visible = true;
        touching = false;
        // The alerts at once, and back from watching something it is first in Continue and Recently watched.
        alerts(requireContext());
        if (recent != null) recent.refresh();
        resume();
        favourites();
        SyncStore.addListener(listChanged);
        screen.visible(true);
        App.main.post(tick);
        App.main.post(reread);
        App.main.post(socialAgain);
        autoLater();
    }

    @Override public void onPause() {
        visible = false;
        screen.visible(false);
        SyncStore.removeListener(listChanged);
        App.main.removeCallbacks(tick);
        App.main.removeCallbacks(reread);
        App.main.removeCallbacks(socialAgain);
        App.main.removeCallbacks(auto);
        super.onPause();
    }

    // ---- films, TV and the headlines ----------------------------------------------------------------------------

    /**
     * Continue watching from this phone (and the TVs), at once; each title's backdrop and logo read after, one read
     * each, and kept while it stays in Continue watching (a read that failed is tried again next time).
     */
    private void resume() {
        if (screen == null || !isAdded()) return;
        Context app = requireContext().getApplicationContext();
        List<Resume.Card> cards = Resume.pick(Watched.list(app), Lists.resume(app), 12);
        Set<String> keys = new HashSet<>();
        for (Resume.Card k : cards) keys.add(k.key());
        ARTS.keySet().retainAll(keys);
        screen.resume(cards, ARTS);
        for (Resume.Card k : cards) {
            String key = k.key();
            if (k.tmdb <= 0 || ARTS.containsKey(key) || !reading.add(key)) continue;
            int tmdb = k.tmdb;
            boolean tv = k.tv;
            App.io.execute(() -> {
                Pick art = Feed.art(app, tmdb, tv);
                App.main.post(() -> {
                    reading.remove(key);
                    if (art == null) return;
                    ARTS.put(key, art);
                    if (isAdded() && screen != null) screen.art(key, art);
                });
            });
        }
    }

    /** Favourites: My list, read off the main thread (what it was last time shows until then). */
    private void favourites() {
        if (screen == null || !isAdded()) return;
        Context app = requireContext().getApplicationContext();
        int my = ++listGen;
        App.io.execute(() -> {
            List<Pick> list = new ArrayList<>();
            try {
                for (Title t : Lists.myList(app)) {
                    Pick p = Pick.of(t);
                    if (p != null) list.add(p);
                }
            } catch (RuntimeException e) {
                // as it was
                return;
            }
            App.main.post(() -> {
                listKept = list;
                if (my != listGen || !isAdded() || screen == null) return;
                screen.favourites(list);
            });
        });
    }

    /** The films and TV picked from the playlists (made again when stale, or forced). */
    private void picks(boolean force) {
        Context app = requireContext().getApplicationContext();
        int my = ++picksGen;
        App.io.execute(() -> {
            Feed.Result r = Feed.load(app, force);
            App.main.post(() -> {
                if (my != picksGen || !isAdded()) return;
                screen.feed(r == null ? emptyFeed() : r);
            });
        });
    }

    private static Feed.Result emptyFeed() {
        return new Feed.Result();
    }

    private void headlines(boolean force) {
        Context app = requireContext().getApplicationContext();
        int my = ++newsGen;
        App.io.execute(() -> {
            Headlines.Read r = Headlines.load(app, force);
            App.main.post(() -> {
                if (my != newsGen || !isAdded()) return;
                screen.news(r == null ? new Headlines.Read() : r);
            });
        });
    }

    /** The fans' posts about the starred clubs (off in Settings, or no clubs: the section hidden). */
    private void social(boolean force) {
        Context app = requireContext().getApplicationContext();
        int my = ++socialGen;
        App.io.execute(() -> {
            List<FanFeed.Item> items = FanFeed.load(app, SocialCard.MAX + 3, force);
            List<String> clubs = FanFeed.keptClubs();
            App.main.post(() -> {
                if (my != socialGen || !isAdded() || screen == null) return;
                screen.social(items, clubs);
            });
        });
    }

    // ---- the page's taps ------------------------------------------------------------------------------------------

    @Override public void open(Pick p) {
        TitleActivity.open(requireContext(), p.title());
    }

    @Override public void resume(Resume.Card k) {
        Context c = requireContext();
        if (k.entry != null) {
            Intent i = Watched.play(c, k.entry);
            if (i != null) startActivity(i);
        } else if (k.tvTitle != null) {
            TitleActivity.open(c, k.tvTitle);
        }
    }

    @Override public void forget(Resume.Card k) {
        if (k.entry == null) return;
        Context c = requireContext();
        Watched.remove(c, k.entry);
        Toast.makeText(c, k.title + " taken off Continue watching", Toast.LENGTH_SHORT).show();
        resume();
        if (recent != null) recent.refresh();
    }

    @Override public void story(Story s) {
        if (s.link == null || !s.link.startsWith("https://")) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(s.link)));
        } catch (RuntimeException e) {
            Toast.makeText(requireContext(), "No browser to open it in", Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void post(FanFeed.Item p) {
        if (p.link.isEmpty()) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(p.link)));
        } catch (RuntimeException e) {
            Toast.makeText(requireContext(), "No browser to open it in", Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void settings() {
        if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).open(R.id.tab_settings);
    }

    /** Watch, asked to open on a section (and its search), then shown. */
    @Override public void watch(int section, boolean search) {
        WatchFragment.ask(section, search);
        if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).open(R.id.tab_watch);
    }

    @Override public void live() {
        if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).open(R.id.tab_live);
    }

    // ---- the scores box moving on by itself -----------------------------------------------------------------------

    /** The next game in AUTO_MS, while Home shows, nobody holds the box and it has more than one game. */
    private void autoLater() {
        App.main.removeCallbacks(auto);
        if (visible && !touching && shown.size() > 1) App.main.postDelayed(auto, AUTO_MS);
    }

    /** Slides the next game in from the right, as a swipe does (after the last, the first again). */
    private void advance() {
        if (!visible || touching || shown.size() < 2 || bugs == null || bugs.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) {
            autoLater();
            return;
        }
        LinearLayoutManager lm = (LinearLayoutManager) bugs.getLayoutManager();
        if (lm == null) return;
        int at = at(lm);
        RecyclerView.Adapter<?> a = bugs.getAdapter();
        if (a == null || at + 1 >= a.getItemCount()) {
            // The end of the long loop (some days in): back to the middle, then on.
            bugs.scrollToPosition(middle(at % shown.size()));
            autoLater();
            return;
        }
        RecyclerView.SmoothScroller slide = pager.slide(lm);
        if (slide == null) {
            bugs.smoothScrollToPosition(at + 1);
        } else {
            slide.setTargetPosition(at + 1);
            lm.startSmoothScroll(slide);
        }
        // The slide's end (idle) sets the next one; this one only if it never started.
        autoLater();
    }

    /** The page the box is on (an adapter position: the game is it modulo shown.size()). */
    private static int at(LinearLayoutManager lm) {
        int at = lm.findFirstCompletelyVisibleItemPosition();
        if (at < 0) at = lm.findFirstVisibleItemPosition();
        return Math.max(0, at);
    }

    /** The position of game i in the middle round of the loop, so the box can go either way from it. */
    private int middle(int i) {
        return shown.size() < 2 ? Math.max(0, i) : (LOOPS / 2) * shown.size() + i;
    }

    /** PagerSnapHelper's own settling slide, as a fling ends with, for the box's moves by itself. */
    private static final class Pager extends PagerSnapHelper {
        RecyclerView.SmoothScroller slide(RecyclerView.LayoutManager lm) {
            return createScroller(lm);
        }
    }

    /** The greeting and the date as they are now, on the clock's tick. */
    private void stamp() {
        if (screen != null) screen.stamp();
    }

    private void load() {
        Context app = requireContext().getApplicationContext();
        int my = ++gen;
        App.io.execute(() -> {
            Set<String> teams = Clubs.starred(app);
            List<Game> all = new ArrayList<>(), mine = new ArrayList<>();
            for (String[] l : Espn.LEAGUES) {
                for (Game g : Espn.scoreboard(l[0], LocalDate.now(), 30_000L)) {
                    all.add(g);
                    if (g.involves(teams)) mine.add(g);
                }
            }
            List<Game> bugList = new ArrayList<>(mine);
            all.sort((a, b) -> a.state != b.state ? Integer.compare(a.state, b.state) : Long.compare(a.kickoff, b.kickoff));
            for (Game g : all) {
                if (bugList.size() >= 12) break;
                boolean big = "eng.1".equals(g.slug) || "uefa.champions".equals(g.slug);
                if (!bugList.contains(g) && big) bugList.add(g);
            }
            List<String> channels = new ArrayList<>();
            for (Game g : mine) channels.add(g.state == Game.POST ? null : Channels.best(app, g));
            Object[] w = Weather.now();
            // The games in Recently watched, their scores as they are now (or as they ended).
            try {
                Watched.refreshGames(app, all);
            } catch (RuntimeException ignored) {
                // as they were
            }
            App.main.post(() -> {
                if (my != gen || !isAdded()) return;
                screen.swipe.setRefreshing(false);
                show(bugList, mine, channels, w);
                recent.refresh();
            });
        });
    }

    private void show(List<Game> bugList, List<Game> mine, List<String> channels, Object[] w) {
        Context c = requireContext();
        if (w != null) screen.weather(w[0] + "° · Manchester");
        int was = shown.size();
        LinearLayoutManager lm = (LinearLayoutManager) bugs.getLayoutManager();
        int on = was == 0 || lm == null ? 0 : at(lm) % was;
        shown.clear();
        shown.addAll(bugList);
        mineIds.clear();
        for (Game g : mine) if (g.id != null) mineIds.add(g.id);
        RecyclerView.Adapter<?> a = bugs.getAdapter();
        if (a != null) a.notifyDataSetChanged();
        // A new number of games: the same place in the list (or the first), in the loop's middle round.
        if (shown.size() != was) bugs.scrollToPosition(middle(Math.min(on, Math.max(0, shown.size() - 1))));
        screen.sports.setVisibility(shown.isEmpty() && mine.isEmpty() ? View.GONE : View.VISIBLE);
        bugs.setVisibility(shown.isEmpty() ? View.GONE : View.VISIBLE);
        counter();
        autoLater();
        today.removeAllViews();
        todayBox.setVisibility(mine.isEmpty() ? View.GONE : View.VISIBLE);
        // With no scores box above them, the clubs' games start at the top of the box.
        clubs.setPadding(clubs.getPaddingLeft(), Glass.dp(c, shown.isEmpty() ? 2 : 16), 0, clubs.getPaddingBottom());
        List<View> cards = new ArrayList<>();
        for (int i = 0; i < mine.size(); i++) {
            Game g = mine.get(i);
            // Each with its competition's logo and name: the clubs' games today may be in several.
            LinearLayout card = GameRow.build(c, g, true, true);
            String ch = channels.get(i);
            if (ch != null) card.addView(Ui.liveOn(c, ch));
            cards.add(card);
        }
        GameRow.addAll(today, cards);
        alerts(c);
    }

    /**
     * The latest alerts: from this phone's own log, so straight away (before the scores are read) and again with every
     * read. Each on its competition's silver band, as the TV pops them up, over frosted glass.
     */
    private void alerts(Context c) {
        if (screen == null) return;
        LinearLayout latest = screen.alertList;
        latest.removeAllViews();
        JSONArray log = AlertLog.list(c);
        screen.alerts.setVisibility(log.length() == 0 ? View.GONE : View.VISIBLE);
        for (int i = 0; i < log.length() && i < 5; i++) {
            JSONObject a = log.optJSONObject(i);
            if (a == null) continue;
            LinearLayout card = Ui.column(c);
            card.setBackground(Glass.card(c, 16));
            LinearLayout band = Ui.row(c);
            band.setGravity(Gravity.CENTER_VERTICAL);
            // The competition's logo (its version for light grounds, on the silver band), F1's for an F1 alert.
            String kind = a.optString("kind");
            // A breaking story: its source's mark (BBC News's, BBC Sport's), by its source or its link.
            String section = "news".equals(kind) ? com.gathin.tvhomephone.glass.BbcMark.section(a.optString("channel", "")) : null;
            if ("news".equals(kind) && section == null) section = com.gathin.tvhomephone.glass.BbcMark.sectionOfLink(a.optString("slug", ""));
            String logo = section != null ? null : kind.startsWith("f1") ? Competitions.F1_LOGO : Competitions.lightLogo(a.optString("slug", null));
            if (section != null) {
                android.widget.ImageView v = new android.widget.ImageView(c);
                v.setImageDrawable(com.gathin.tvhomephone.glass.BbcMark.chip(c, section, 20));
                v.setAdjustViewBounds(true);
                v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Ui.dp(c, 20));
                lp.rightMargin = Ui.dp(c, 10);
                band.addView(v, lp);
            }
            if (logo != null) {
                android.widget.ImageView v = new android.widget.ImageView(c);
                v.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20));
                lp.rightMargin = Ui.dp(c, 8);
                band.addView(v, lp);
                Ui.load(v, logo, null, () -> v.setVisibility(View.GONE));
            }
            TextView title = Rows.text(c, 15, c.getColor(R.color.band_ink), Type.outfit(c, 700), 1);
            title.setText(a.optString("title"));
            band.addView(title, Ui.weight(1));
            TextView at = Rows.text(c, 12, 0xFF33386A, Type.interSemi(c), 1);
            at.setText(java.time.Instant.ofEpochMilli(a.optLong("at")).atZone(java.time.ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm")));
            band.addView(at);
            band.setBackground(Ui.vertical(c.getColor(R.color.band_silver_top), c.getColor(R.color.band_silver_bottom)));
            band.setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10));
            card.addView(band, Ui.matchWrap());
            TextView t = Rows.text(c, 14, Glass.SOFT, Type.inter(c), 3);
            t.setText(a.optString("text"));
            t.setLineSpacing(Ui.dp(c, 2), 1f);
            t.setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 12));
            card.addView(t);
            Glass.round(card, Glass.dp(c, 16));
            latest.addView(card, Ui.margins(c, 0, 0, 0, 10));
        }
    }

    private void counter() {
        if (shown.size() < 2) {
            count.setText("");
            count.setVisibility(View.GONE);
            return;
        }
        LinearLayoutManager lm = (LinearLayoutManager) bugs.getLayoutManager();
        int at = lm == null ? 0 : at(lm) % shown.size();
        count.setText((at + 1) + " of " + shown.size() + "  ·  swipe for more");
        count.setVisibility(View.VISIBLE);
    }

    /**
     * The scores box, one game a page, round and round: with two games or more it holds LOOPS rounds of them (page p
     * is game p modulo their number), so moving on from the last slides the first in from the right.
     */
    private final class Bugs extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            Context c = parent.getContext();
            android.widget.FrameLayout box = new android.widget.FrameLayout(c);
            box.setLayoutParams(new RecyclerView.LayoutParams(parent.getWidth() > 0 ? parent.getWidth() : ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            ScoreBug b = new ScoreBug(c);
            box.addView(b, new android.widget.FrameLayout.LayoutParams(-1, -2));
            // A family's club's game: a small Your club tag on its top right corner.
            TextView tag = Ui.text(c, getString(R.string.f1_your_club).toUpperCase(java.util.Locale.UK), 9, 0xFF0A0D33, true);
            tag.setLetterSpacing(0.06f);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(0xFFFFC93C);
            bg.setCornerRadii(new float[]{0, 0, Ui.dp(c, 6), Ui.dp(c, 6), 0, 0, Ui.dp(c, 6), Ui.dp(c, 6)});
            tag.setBackground(bg);
            tag.setPadding(Ui.dp(c, 6), Ui.dp(c, 1), Ui.dp(c, 6), Ui.dp(c, 2));
            tag.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            box.addView(tag, new android.widget.FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END));
            return new RecyclerView.ViewHolder(box) {};
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
            Game g = shown.get(pos % shown.size());
            android.widget.FrameLayout box = (android.widget.FrameLayout) h.itemView;
            ScoreBug b = (ScoreBug) box.getChildAt(0);
            b.show(g);
            boolean mine = g.id != null && mineIds.contains(g.id);
            box.getChildAt(1).setVisibility(mine ? View.VISIBLE : View.GONE);
            if (mine) b.setContentDescription(b.getContentDescription() + ", " + getString(R.string.f1_your_club));
            h.itemView.setOnClickListener(v -> v.getContext().startActivity(GameActivity.intent(v.getContext(), g)));
        }

        @Override public int getItemCount() { return shown.size() < 2 ? shown.size() : shown.size() * LOOPS; }
    }
}
