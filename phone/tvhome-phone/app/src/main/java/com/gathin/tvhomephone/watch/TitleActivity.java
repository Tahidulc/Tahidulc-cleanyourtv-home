package com.gathin.tvhomephone.watch;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Layout;
import android.text.TextUtils;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.SyncStore;
import com.gathin.tvhomephone.glass.AmbientView;
import com.gathin.tvhomephone.glass.Glass;
import com.gathin.tvhomephone.glass.Icons;
import com.gathin.tvhomephone.live.SourceSheet;
import com.gathin.tvhomephone.live.TorBox;
import com.gathin.tvhomephone.live.Vod;
import com.gathin.tvhomephone.live.VodHistory;
import com.gathin.tvhomephone.live.VodPlayerActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * A film's or series' cover page, in the glass look the family chose after their reference picture: its backdrop across
 * the top of the screen, fading into the page's glow, with its logo and the line under it; Play (or Continue, when some
 * of it was left part-way) from the family's own playlists only, and their own files already in TorBox when its key is
 * saved (nothing else is searched for or added, and no other app is opened), which of them has it, frosted circles for
 * My list, How was it?, the trailer and Film night (films), and what it's about; for a series its seasons and episodes,
 * and the Episode map; then the trailers, More like this and the cast.
 * Back and "…" float over the picture.
 */
public class TitleActivity extends AppCompatActivity {
    private Title t;
    private JSONObject details;
    /** The line saying which playlists have it; what was found there (null: not looked yet, or none). */
    private TextView where;
    /** Under it, each source once: TorBox's mark or the playlist's, with its best copy's logos (SourceSheet). */
    private FrameLayout whereSources;
    private Vod.Item found;
    private List<Vod.Episode> foundEps;
    /** The episodes found there, as "season:number". */
    private final Set<String> have = new HashSet<>();
    private boolean looked, looking;
    /** A tablet: the words over the picture's foot at its left, as the reference's tablet has them. */
    private boolean wide;
    /** The page's side margin, and the width of the centred column the top's pieces sit in on a phone. */
    private int side, column;
    private FrameLayout root, page;
    private AmbientView ambient;
    private LinearLayout col, body, sections;
    private CoverHero hero;
    private CoverHero.Bar bar;
    private View topFade;
    private CoverViews.MainButton mainButton;
    private LinearLayout progressRow;
    private CoverViews.Line progressLine;
    private TextView progressWords, about, aboutMore;
    private boolean aboutOpen;
    private CoverViews.Circle listCircle, rateCircle, nightCircle;
    private CoverEpisodes episodes;
    /** The newest of it left part-way from the family's playlists (VodHistory), or null. */
    private VodHistory.Entry resume;
    private Insets bars = Insets.NONE;
    private CoverMenu menu;

    /** What the seasons and episodes ask of the page. */
    private final CoverEpisodes.Host host = new CoverEpisodes.Host() {
        @Override public Boolean has(int season, int number) {
            if (!looked) return null;
            return found != null && have.contains(season + ":" + number);
        }

        @Override public void play(int season, int number) {
            playEpisode(season, number);
        }

        @Override public void missing(int season, int number) {
            // The series in none of them: the full word on adding a playlist; one episode missing: a short one.
            if (found == null) playFound(null);
            else Toast.makeText(TitleActivity.this, "Not in " + TorBox.where(TitleActivity.this), Toast.LENGTH_SHORT).show();
        }
    };

    public static void open(Context c, Title t) {
        c.startActivity(intent(c, t));
    }

    /** A tablet's type a little larger, as the tabs' (Wide); a phone's as it is. */
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(com.gathin.tvhomephone.glass.Wide.wrap(base));
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
        wide = getResources().getConfiguration().smallestScreenWidthDp >= 600;
        side = Glass.dp(this, wide ? 40 : 20);
        column = Math.min(getResources().getDisplayMetrics().widthPixels - 2 * side, Glass.dp(this, 560));
        getWindow().setBackgroundDrawable(new ColorDrawable(Glass.GROUND));

        // Back to front: the glow; the page, in a frame of its own so the glass over it can draw it as it is scrolled; a
        // fade under the status bar once the picture has gone up; the floating buttons.
        root = new FrameLayout(this);
        root.setBackgroundColor(Glass.GROUND);
        ambient = new AmbientView(this);
        root.addView(ambient, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        page = new FrameLayout(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        page.addView(scroll, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(page, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(col, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        hero = new CoverHero(this, t.title, wide, side);
        col.addView(hero, fill());
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        col.addView(body, fill());
        topFade = new View(this);
        topFade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xF20E0F14, 0xB80E0F14, 0x000E0F14}));
        topFade.setAlpha(0f);
        topFade.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(topFade, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, Glass.dp(this, 88), Gravity.TOP));
        bar = new CoverHero.Bar(this, t.tv ? "Shows" : "Films", this::finish, this::openMenu);
        bar.setSources(ambient, page);
        root.addView(bar, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP));
        // TalkBack reaches Back and "…" first, then the page under them.
        bar.setId(View.generateViewId());
        page.setAccessibilityTraversalAfter(bar.getId());
        setContentView(root);
        edgeToEdge();

        buildTop();
        sections = new LinearLayout(this);
        sections.setOrientation(LinearLayout.VERTICAL);
        body.addView(sections, fill());

        String picture = bigger(t.backdrop != null ? t.backdrop : t.poster);
        hero.picture(picture);
        ambient.setImage(picture);
        hero.meta(t.year);
        lookInPlaylists(null);
        // Opened with Play (Home's hero): Play pressed once, as the page comes up.
        if (state == null && in.getBooleanExtra("autoplay", false)) root.post(this::play);
        scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> scrolled(y));
        loadDetails();
    }

    /**
     * Edge to edge: the picture under the status bar, the floating buttons just below it, the foot clear of the
     * navigation bar; light icons on both bars.
     */
    private void edgeToEdge() {
        Window w = getWindow();
        WindowCompat.setDecorFitsSystemWindows(w, false);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat ic = WindowCompat.getInsetsController(w, root);
        ic.setAppearanceLightStatusBars(false);
        ic.setAppearanceLightNavigationBars(false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, ins) -> {
            Insets s = ins.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            bars = s;
            bar.fit(s.top, s.left, s.right);
            hero.fit(s.top, s.left, s.right);
            body.setPadding(s.left, 0, s.right, s.bottom + Glass.dp(this, 40));
            ViewGroup.LayoutParams lp = topFade.getLayoutParams();
            lp.height = s.top + Glass.dp(this, 60);
            topFade.setLayoutParams(lp);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    /** The page scrolled: the picture's parallax, the glow drifting, the fade under the status bar, the back word. */
    private void scrolled(int y) {
        hero.scrolled(y);
        ambient.setShift(y);
        int h = hero.getHeight();
        if (h <= 0) return;
        topFade.setAlpha(Math.max(0f, Math.min(1f, (y - h * 0.5f) / (h * 0.3f))));
        bar.scrolled(y, h);
    }

    private static LinearLayout.LayoutParams fill() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    /** Where a piece of the top goes: on a phone, in the centred column; on a tablet, at the left over the picture's foot. */
    private LinearLayout.LayoutParams place(int width, int height, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height);
        lp.topMargin = Glass.dp(this, topDp);
        if (!wide) lp.gravity = Gravity.CENTER_HORIZONTAL;
        return lp;
    }

    /** Play (or Continue) and how far in, which playlists have it, the frosted circles, and what it's about. */
    private void buildTop() {
        int wrap = LinearLayout.LayoutParams.WRAP_CONTENT, match = LinearLayout.LayoutParams.MATCH_PARENT;
        int button = wide ? Glass.dp(this, 360) : column;
        LinearLayout to = wide ? hero.head : body;
        mainButton = new CoverViews.MainButton(this);
        mainButton.setOnClickListener(v -> mainPressed());
        to.addView(mainButton, place(button, Glass.dp(this, 52), wide ? 20 : 4));

        progressRow = new LinearLayout(this);
        progressRow.setOrientation(LinearLayout.HORIZONTAL);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        progressLine = new CoverViews.Line(this);
        progressRow.addView(progressLine, new LinearLayout.LayoutParams(0, Glass.dp(this, 3), 1f));
        progressWords = Glass.text(this, "", 12, Glass.MUTED, Glass.body(this));
        progressWords.setSingleLine(true);
        LinearLayout.LayoutParams pw = new LinearLayout.LayoutParams(wrap, wrap);
        pw.leftMargin = Glass.dp(this, 12);
        progressRow.addView(progressWords, pw);
        progressRow.setVisibility(View.GONE);
        to.addView(progressRow, place(button, wrap, 12));

        where = Glass.text(this, "Looking in " + TorBox.where(this) + "…", 12, Glass.FAINT, Glass.body(this));
        where.setGravity(wide ? Gravity.START : Gravity.CENTER_HORIZONTAL);
        where.setMaxLines(2);
        where.setEllipsize(TextUtils.TruncateAt.END);
        where.setLineSpacing(0, 1.15f);
        to.addView(where, place(wide ? match : column, wrap, 10));
        whereSources = new FrameLayout(this);
        to.addView(whereSources, place(wide ? match : column, wrap, 8));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(wide ? Gravity.START : Gravity.CENTER_HORIZONTAL);
        listCircle = circle(actions, v -> toggleList());
        rateCircle = circle(actions, v -> rate());
        circle(actions, v -> trailer()).set(new Icons(this, Icons.FILM, Glass.TEXT), "Trailer", false);
        if (!t.tv) nightCircle = circle(actions, v -> FilmNightSheet.show(this, t, details, this::paintButtons));
        LinearLayout.LayoutParams ap = place(wide ? wrap : column, wrap, wide ? 12 : 26);
        // A tablet's circles start in line with the words over the picture.
        if (wide) ap.leftMargin = side - Glass.dp(this, 21);
        body.addView(actions, ap);

        about = Glass.text(this, "", 15, Glass.SOFT, Glass.body(this));
        about.setMaxLines(4);
        about.setEllipsize(TextUtils.TruncateAt.END);
        about.setLineSpacing(0, 1.25f);
        about.setVisibility(View.GONE);
        about.setOnClickListener(v -> toggleAbout());
        about.addOnLayoutChangeListener((v, l, tp, r, b, ol, ot, or, ob) -> about.post(this::checkMore));
        aboutMore = Glass.text(this, "More", 14, Glass.TEXT, Glass.heading(this, 600));
        aboutMore.setPadding(0, Glass.dp(this, 8), Glass.dp(this, 16), Glass.dp(this, 8));
        aboutMore.setVisibility(View.GONE);
        aboutMore.setOnClickListener(v -> toggleAbout());
        LinearLayout aboutTo = wide ? hero.head : body;
        aboutTo.addView(about, place(wide ? match : column, wrap, wide ? 18 : 26));
        aboutTo.addView(aboutMore, place(wide ? wrap : column, wrap, 0));
    }

    private CoverViews.Circle circle(LinearLayout row, View.OnClickListener click) {
        CoverViews.Circle c = new CoverViews.Circle(this);
        c.setOnClickListener(click);
        row.addView(c, wide ? new LinearLayout.LayoutParams(Glass.dp(this, 96), LinearLayout.LayoutParams.WRAP_CONTENT)
                : new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return c;
    }

    /** What it's about (none for ""), up to four lines until More. */
    private void setAbout(String s) {
        String text = s == null ? "" : s.trim();
        about.setText(text);
        about.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        if (text.isEmpty()) aboutMore.setVisibility(View.GONE);
    }

    /** More shows while what it's about is cut short (and Less once it's open). */
    private void checkMore() {
        Layout l = about.getLayout();
        boolean cut = l != null && l.getLineCount() > 0 && l.getEllipsisCount(l.getLineCount() - 1) > 0;
        int show = about.getVisibility() == View.VISIBLE && (cut || aboutOpen) ? View.VISIBLE : View.GONE;
        if (aboutMore.getVisibility() != show) aboutMore.setVisibility(show);
    }

    private void toggleAbout() {
        if (aboutMore.getVisibility() != View.VISIBLE) return;
        aboutOpen = !aboutOpen;
        TransitionManager.beginDelayedTransition(col, new ChangeBounds().setDuration(260));
        about.setMaxLines(aboutOpen ? Integer.MAX_VALUE : 4);
        aboutMore.setText(aboutOpen ? "Less" : "More");
    }

    private void paintButtons() {
        boolean in = SyncStore.get(this, "mylist", t.key()) != null;
        listCircle.set(new Icons(this, in ? Icons.CHECK : Icons.PLUS, Glass.TEXT), "My list", in);
        listCircle.setContentDescription(in ? "On My list" : "Add to My list");
        int v = rating();
        rateCircle.set(new Icons(this, Icons.HEART, Glass.TEXT), rateWords(v), v != 0);
        if (nightCircle != null) nightCircle.set(new CoverViews.Moon(this, Glass.TEXT), nightWords(), nightOn());
    }

    private int rating() {
        JSONObject r = SyncStore.get(this, "rating", t.key());
        return r == null ? 0 : r.optInt("value");
    }

    private static String rateWords(int v) {
        return v == 2 ? "Loved it" : v == 1 ? "Liked it" : v < 0 ? "Not for me" : "How was it?";
    }

    /** Film night's words as its sheet gives them, without the marks: "Film night", or "Film night 20:00" when set for this. */
    private String nightWords() {
        return FilmNightSheet.words(this, t).replace("☾", "").replace("✓", "").trim();
    }

    private boolean nightOn() {
        JSONObject p = FilmNightSheet.plan(this);
        return p != null && p.optInt("id") == t.id;
    }

    /**
     * Whether some of it was left part-way (the newest such in VodHistory): the main button then says Continue (with the
     * season and episode for a series) and the line under it how far in and the time left; the episode up next marked.
     */
    private void paintResume() {
        resume = null;
        if (t.id > 0) {
            for (VodHistory.Entry e : VodHistory.list(this)) {
                if (e.tmdb == t.id && e.tv == t.tv) {
                    resume = e;
                    break;
                }
            }
        }
        boolean part = resume != null;
        String words = CoverText.button(part, t.tv, part ? resume.season : 0, part ? resume.episode : 0);
        String left = part ? CoverText.remaining(resume.minutesLeft()) : "";
        mainButton.set(words);
        mainButton.setContentDescription(left.isEmpty() ? words : words + ", " + left);
        progressRow.setVisibility(left.isEmpty() ? View.GONE : View.VISIBLE);
        if (part) {
            progressLine.set(resume.progress());
            progressWords.setText(left);
        }
        if (episodes != null) {
            int[] up = upNext();
            episodes.setUpNext(up[0], up[1], upProgress());
        }
    }

    /** The episode to watch next: the one left part-way, else the first. */
    private int[] upNext() {
        if (resume != null && t.tv && resume.episode > 0) return new int[]{resume.season, resume.episode};
        return new int[]{1, 1};
    }

    private float upProgress() {
        return resume != null && t.tv && resume.episode > 0 ? resume.progress() : 0f;
    }

    /** The main button: what was left part-way carried on, as Watch's Continue watching does; else Play. */
    private void mainPressed() {
        if (resume != null) {
            startActivity(VodPlayerActivity.intent(this, resume));
            return;
        }
        play();
    }

    @Override protected void onResume() {
        super.onResume();
        if (listCircle != null) paintButtons();
        if (mainButton != null) paintResume();
    }

    /** "…": the same as the circles (My list, How was it?, the trailer, Film night), in a frosted menu. */
    private void openMenu(View anchor) {
        boolean in = SyncStore.get(this, "mylist", t.key()) != null;
        int v = rating();
        List<CoverMenu.Entry> items = new ArrayList<>();
        items.add(new CoverMenu.Entry(in ? "Remove from My list" : "Add to My list", null,
                new Icons(this, in ? Icons.CHECK : Icons.PLUS, Glass.SOFT), in, this::toggleList));
        items.add(new CoverMenu.Entry("How was it?", v == 0 ? null : rateWords(v), new Icons(this, Icons.HEART, Glass.SOFT), v != 0,
                this::rate));
        items.add(new CoverMenu.Entry("Trailer", null, new Icons(this, Icons.FILM, Glass.SOFT), false, this::trailer));
        if (!t.tv) {
            boolean night = nightOn();
            items.add(new CoverMenu.Entry("Film night", night ? nightWords().replace("Film night", "").trim() : null,
                    new CoverViews.Moon(this, Glass.SOFT), night, () -> FilmNightSheet.show(this, t, details, this::paintButtons)));
        }
        showMenu(anchor, true, items);
    }

    private void showMenu(View anchor, boolean fromEnd, List<CoverMenu.Entry> items) {
        if (menu != null && menu.isOpen() || root.getWidth() == 0) return;
        menu = CoverMenu.show(this, root, anchor, fromEnd, bars, items, ambient, page);
    }

    /** A TMDB backdrop at the size the top of the page wants (w1280); anything else as it is. */
    static String bigger(String url) {
        if (url == null || !url.contains("image.tmdb.org")) return url;
        return url.replace("/w780/", "/w1280/").replace("/w300/", "/w1280/");
    }

    private void loadDetails() {
        Context app = getApplicationContext();
        App.io.execute(() -> {
            JSONObject d = null;
            try {
                d = Tmdb.details(app, t.id, t.tv);
            } catch (IOException ignored) {
                // the page as it came
            }
            JSONObject got = d;
            App.main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (got == null) {
                    hero.meta(Tmdb.ready(this) ? "Couldn't load the details right now." : "Pair with a TV in Settings to see the details.");
                    return;
                }
                showDetails(got);
            });
        });
    }

    /** The details in: the line under the title, the logo, what it's about, then the sections rising softly into place. */
    private void showDetails(JSONObject got) {
        details = got;
        Title full = Title.from(got, t.tv);
        if (full != null) {
            if (t.poster == null) t.poster = full.poster;
            if (t.backdrop == null && full.backdrop != null) {
                t.backdrop = full.backdrop;
                String big = Tmdb.image(got.optString("backdrop_path", null), "w1280");
                hero.picture(big != null ? big : full.backdrop);
                ambient.setImage(big != null ? big : full.backdrop);
            }
            if (t.title.isEmpty()) {
                t.title = full.title;
                hero.name(t.title);
            }
        }
        hero.meta(CoverText.meta(got, t.tv));
        // Its original title too (a foreign film's own name), when the first look found nothing.
        String original = got.optString(t.tv ? "original_name" : "original_title", "");
        if (looked && found == null && !original.isEmpty() && !original.equalsIgnoreCase(t.title)) lookInPlaylists(null);
        setAbout(got.optString("overview"));
        String logoPath = logo(got);
        if (logoPath != null) hero.logo(Tmdb.image(logoPath, "w500"));

        List<View> fresh = new ArrayList<>();
        if (about.getVisibility() == View.VISIBLE) fresh.add(about);
        if (t.tv) {
            int[] up = upNext();
            boolean across = getResources().getConfiguration().screenWidthDp >= 600;
            episodes = CoverEpisodes.make(this, t, got, host, across, side, up[0], up[1], upProgress(),
                    logoPath == null ? null : Tmdb.image(logoPath, "w500"));
            if (episodes != null) {
                sections.addView(episodes.view, fill());
                fresh.add(episodes.view);
            }
            EpisodeMap map = new EpisodeMap(this, t, got);
            styleMap(map.view);
            sections.addView(map.view, fill());
            fresh.add(map.view);
        }
        List<String[]> videos = CoverText.trailers(got, 12);
        if (!videos.isEmpty()) {
            View s = section("Trailers", CoverRows.trailers(this, videos, side, this::openVideo));
            sections.addView(s, fill());
            fresh.add(s);
        }
        // More like this comes in its own read; its place is kept here, between the trailers and the cast.
        LinearLayout similar = new LinearLayout(this);
        similar.setOrientation(LinearLayout.VERTICAL);
        sections.addView(similar, fill());
        View cast = CoverRows.cast(this, got, side);
        if (cast != null) {
            View s = section("Cast", cast);
            sections.addView(s, fill());
            fresh.add(s);
        }
        CoverViews.rise(fresh);
        loadSimilar(similar);
    }

    /** More like this: TMDB's recommendations for it, as posters; none, no row. */
    private void loadSimilar(LinearLayout box) {
        Context app = getApplicationContext();
        String path = (t.tv ? "tv/" : "movie/") + t.id + "/recommendations";
        App.io.execute(() -> {
            List<Title> got = null;
            try {
                got = Tmdb.list(app, path, t.tv, 1);
            } catch (IOException ignored) {
                // no row
            }
            List<Title> list = got;
            App.main.post(() -> {
                if (isFinishing() || isDestroyed() || list == null) return;
                List<Title> keep = new ArrayList<>();
                for (Title x : list) if (x.id != t.id && keep.size() < 20) keep.add(x);
                if (keep.isEmpty()) return;
                View s = section("More like this", CoverRows.similar(this, keep, side, x -> open(this, x)));
                box.addView(s, fill());
                CoverViews.rise(Collections.singletonList(s));
            });
        });
    }

    /** A section: its heading as the glass pages have them, then what it holds. */
    private LinearLayout section(String title, View content) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView head = Glass.section(this, title, null);
        head.setPadding(side, head.getPaddingTop(), side, head.getPaddingBottom());
        box.addView(head);
        box.addView(content, fill());
        return box;
    }

    /** The Episode map as it is, under its own heading in the page's type. */
    private void styleMap(LinearLayout map) {
        map.setPadding(side, Glass.dp(this, 10), side, 0);
        View head = map.getChildCount() > 0 ? map.getChildAt(0) : null;
        if (head instanceof LinearLayout && ((LinearLayout) head).getChildCount() > 0 && ((LinearLayout) head).getChildAt(0) instanceof TextView) {
            TextView h = (TextView) ((LinearLayout) head).getChildAt(0);
            h.setTypeface(Glass.heading(this, 500));
            h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
            h.setTextColor(Glass.SOFT);
            h.setPadding(0, Glass.dp(this, 14), 0, Glass.dp(this, 4));
        }
    }

    /**
     * Play: from the family's own playlists and TorBox files only (a film at once, a series by season and episode);
     * nothing else.
     */
    private void play() {
        if (looked && !looking) {
            playFound(null);
            return;
        }
        Toast.makeText(this, "Looking in " + TorBox.where(this) + "…", Toast.LENGTH_SHORT).show();
        lookInPlaylists(() -> playFound(null));
    }

    /**
     * Looks for it in the family's own playlists (Vod: every playlist in use, every version, and their TorBox files),
     * off the main thread; the line under Play says which have it, and the episodes they lack are dimmed; then (when
     * given) runs.
     */
    private void lookInPlaylists(Runnable then) {
        looking = true;
        String original = details == null ? "" : details.optString(t.tv ? "original_name" : "original_title", "");
        Context app = getApplicationContext();
        App.io.execute(() -> {
            Vod.Item it = Vod.find(app, t.title, original, t.year, t.id, t.tv);
            List<Vod.Episode> eps = it != null && "series".equals(it.kind) ? Vod.episodes(app, it) : null;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                boolean has = it != null && (!"series".equals(it.kind) || (eps != null && !eps.isEmpty()));
                found = has ? it : null;
                foundEps = has ? eps : null;
                have.clear();
                if (foundEps != null) for (Vod.Episode e : foundEps) have.add(e.season + ":" + e.number);
                looked = true;
                looking = false;
                if (where != null) where.setText(whereWords());
                showWhere();
                if (episodes != null) episodes.mark();
                if (then != null) then.run();
            });
        });
    }

    /**
     * The line under Play: "Ready to play · 3 sources", a series' "24 episodes in 2 seasons ready to play", or that none
     * has it; the sources themselves go under it (showWhere).
     */
    private String whereWords() {
        if (found == null) return "Not in " + TorBox.where(this);
        if (foundEps != null && !foundEps.isEmpty()) {
            Set<Integer> seasons = new TreeSet<>();
            for (Vod.Episode e : foundEps) seasons.add(e.season);
            return foundEps.size() + (foundEps.size() == 1 ? " episode" : " episodes")
                    + (seasons.size() > 1 ? " in " + seasons.size() + " seasons" : "") + " ready to play";
        }
        int n = found.plays().size();
        return n > 1 ? "Ready to play · " + n + " sources" : "Ready to play";
    }

    /** Each source once under the line: TorBox's (green, a cloud) or the playlist's (blue, a screen), with its logos. */
    private void showWhere() {
        if (whereSources == null) return;
        View lines = null;
        if (found != null) {
            lines = foundEps != null && !foundEps.isEmpty() ? SourceSheet.stripEpisodes(this, foundEps, !wide)
                    : SourceSheet.strip(this, found.plays(), !wide);
        }
        SourceSheet.fill(whereSources, lines);
    }

    /** Its original title (TMDB's), for the player to look for its other copies by too. */
    private String original() {
        return details == null ? "" : details.optString(t.tv ? "original_name" : "original_title", "");
    }

    /** What was found played: a film (its source chosen when there are several), a series' season and episode. */
    private void playFound(int[] episode) {
        if (found == null) {
            boolean torbox = TorBox.on(this);
            new MaterialAlertDialogBuilder(this).setTitle(torbox ? "Not in your playlists or TorBox" : "Not in your playlists")
                    .setMessage(torbox
                            ? "Homefield only plays films and series from your own playlists and the files already in your "
                                    + "TorBox, and none of them has " + t.title + ". Add a playlist that has it in Settings, "
                                    + "Live TV, or add it to your TorBox: it shows here within a couple of minutes."
                            : "Homefield only plays films and series from your own playlists (and your own TorBox files, "
                                    + "once its key is in Settings), and none of them has " + t.title
                                    + ". Add a playlist that has it in Settings, Live TV.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        if (!"series".equals(found.kind)) {
            chooseSource(found.plays(), plays -> startActivity(VodPlayerActivity.intent(this, plays, t.title,
                    VodPlayerActivity.meta(t.title, t.id, false, t.poster, 0, 0, "", t.year, original()), "")));
            return;
        }
        if (episode != null) {
            for (Vod.Episode e : foundEps) {
                if (e.season == episode[0] && e.number == episode[1]) {
                    playEpisode(e, foundEps);
                    return;
                }
            }
            Toast.makeText(this, "S" + episode[0] + " E" + episode[1] + " isn't in " + TorBox.where(this), Toast.LENGTH_LONG).show();
            return;
        }
        pickEpisode(foundEps);
    }

    /** One episode (from the episode cards or the Episode map), from the playlists (and TorBox) only. */
    void playEpisode(int season, int number) {
        int[] ep = {season, number};
        if (looked && !looking) playFound(ep);
        else lookInPlaylists(() -> playFound(ep));
    }

    private void playEpisode(Vod.Episode e, List<Vod.Episode> eps) {
        int season = e.season;
        // With the whole series' episodes, for the next one to follow on.
        chooseSource(e.plays(), plays -> startActivity(VodPlayerActivity.intent(this, plays,
                t.title + (season > 0 ? " · S" + season : "") + " E" + e.number,
                VodPlayerActivity.meta(t.title, t.id, true, t.poster, e.season, e.number, e.title, t.year, original()),
                Vod.episodesJson(eps))));
    }

    /** A series from the playlist (or TorBox): its season, then its episode. */
    private void pickEpisode(List<Vod.Episode> eps) {
        List<Integer> seasons = new ArrayList<>();
        for (Vod.Episode e : eps) if (!seasons.contains(e.season)) seasons.add(e.season);
        if (seasons.size() == 1) {
            pickIn(eps, seasons.get(0));
            return;
        }
        String[] names = new String[seasons.size()];
        for (int i = 0; i < names.length; i++) names[i] = seasons.get(i) > 0 ? "Season " + seasons.get(i) : "Episodes";
        new MaterialAlertDialogBuilder(this).setTitle(t.title + (TorBox.from(found) ? " · from TorBox" : " · from your playlist"))
                .setItems(names, (d, w) -> pickIn(eps, seasons.get(w))).show();
    }

    private void pickIn(List<Vod.Episode> eps, int season) {
        List<Vod.Episode> in = new ArrayList<>();
        for (Vod.Episode e : eps) if (e.season == season) in.add(e);
        String[] names = new String[in.size()];
        for (int i = 0; i < names.length; i++) {
            Vod.Episode e = in.get(i);
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
     * More than one source (TorBox's, the playlists', other versions): which to play, in a sheet that marks TorBox's and
     * the playlists' apart and shows each one's picture and sound logos, the best match first; the others stay its
     * backups (and can be changed to in the player). One source plays straight away.
     */
    private void chooseSource(List<String[]> plays, Chosen chosen) {
        if (plays.size() < 2) {
            chosen.play(plays);
            return;
        }
        SourceSheet.show(this, "Choose a source", "The others stay as backups: change between them while it plays.", plays, -1,
                w -> chosen.play(VodPlayerActivity.chosenFirst(plays, w)));
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
        openLink(u);
    }

    /** One of the trailers row's videos, opened as the trailer is. */
    private void openVideo(String key) {
        openLink(Uri.parse("https://www.youtube.com/watch?v=" + key));
    }

    private void openLink(Uri u) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, u));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app here plays YouTube", Toast.LENGTH_SHORT).show();
        }
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
}
