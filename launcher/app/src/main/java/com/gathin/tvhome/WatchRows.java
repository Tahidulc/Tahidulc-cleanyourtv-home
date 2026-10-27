package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Continue watching and Recommended for the focused dock app: the rows apps publish to the system's TV provider
 * (TvContract.WatchNextPrograms and PreviewPrograms), read with READ_TV_LISTINGS, as a strip of cards above the
 * shelf's name. Posters come over HTTPS, so this is the one part of Home that needs INTERNET.
 * Up from the dock enters the strip, Up again goes to the top bar, Down returns to the dock.
 */
final class WatchRows {
    interface Host {
        void onCardsUp();
        void onCardsDown();
        /** The strip appeared or went: Home hides the shelf's big app name while the cards take its place. */
        void onCardsShown(boolean shown);
        /** OK on the resume card of an app that publishes no rows: open it the way its dock tile does. */
        void onResumeApp();
    }

    private static final int MAX_CONTINUE = 8;
    private static final int MAX_RECOMMENDED = 12;
    private static final long CACHE_MS = 60_000L;
    /** The focused card grows this much; EDGE dp of padding around the strip leaves it room on every side. */
    private static final float FOCUS_SCALE = 1.04f;
    private static final int EDGE = 14;
    /** The most a resume card enlarges an app's banner; past this it shows pixels. */
    private static final float MAX_ART_SCALE = 1.6f;

    static final class Item {
        String title, poster, intent;
        boolean continueWatching;
        float progress = -1f;
        /** Episode name, and minutes left in it; for Continue watching only. */
        String episode;
        int minutesLeft = -1;
        /** The resume card: the app's own artwork and when it was last opened, for apps that publish nothing. */
        android.graphics.drawable.Drawable art;
        String subtitle;
        /** For the resume card: a bundled scene shown full size behind the app's logo (resume_backdrops). */
        int backdrop;
        /** A matching picture from another app's published rows (resume_backdrop_queries), shown instead when found. */
        String liveBackdrop;
    }

    private final Context context;
    private final Host host;
    private final float density;
    private final LinearLayout panel;
    private final TextView caption;
    private final HorizontalScrollView scroller;
    private final LinearLayout strip;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService reader = Executors.newSingleThreadExecutor();
    private final ExecutorService images = Executors.newFixedThreadPool(2);
    private final LruCache<String, Bitmap> posters = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    private final Map<String, List<Item>> cache = new HashMap<>();
    private final Map<String, Long> cachedAt = new HashMap<>();
    private String shownPackage;
    private String label = "";
    private Item resume;
    private final Map<String, String> backdrops = new HashMap<>();
    /** BBC News for the space beside a lone card, and when it was read. */
    private List<String[]> news = new ArrayList<>();
    private long newsAt;
    private int generation;

    WatchRows(Context context, FrameLayout root, float density, Host host) {
        this.context = context;
        this.host = host;
        this.density = density;
        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        panel.setVisibility(View.GONE);
        caption = new TextView(context);
        caption.setTextColor(0xB3FFFFFF);
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        caption.setTypeface(context.getResources().getFont(R.font.outfit_medium));
        caption.setLetterSpacing(0.02f);
        caption.setSingleLine(true);
        caption.setPadding(dp(EDGE), 0, 0, 0);
        // The section line ("Continue watching · Disney+") is gone: the cards speak for themselves.
        caption.setVisibility(View.GONE);
        scroller = new HorizontalScrollView(context);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        scroller.setFillViewport(false);
        // Room for the focused card to grow (FOCUS_SCALE) without the scroller clipping its edges.
        int pad = dp(EDGE);
        scroller.setPadding(pad, pad, pad * 2, pad);
        strip = new LinearLayout(context);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setClipChildren(false);
        strip.setClipToPadding(false);
        scroller.addView(strip);
        // The trending rows' names (setTabs); hidden for every other row.
        caption.setIncludeFontPadding(false);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(TABS_DP));
        clp.bottomMargin = -dp(EDGE) + dp(4);
        caption.setGravity(Gravity.BOTTOM);
        panel.addView(caption, clp);
        panel.addView(scroller, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        lp.leftMargin = dp(72 - EDGE);
        lp.rightMargin = dp(40);
        root.addView(panel, lp);
    }

    /** Height of the whole panel (caption, cards and their focus room), for placing it above the shelf. */
    int height() {
        return dp(EDGE) * 2 + cardHeight();
    }

    boolean hasCards() {
        return panel.getVisibility() == View.VISIBLE && strip.getChildCount() > 0;
    }

    boolean hasFocus() {
        return panel.hasFocus();
    }

    /** Shows the rows for this dock app at y (root coordinates of the panel's top), reading them if needed. */
    void show(String packageName, String appLabel, float y, android.graphics.drawable.Drawable art, String lastOpened) {
        baseY = y;
        panel.setY(y - (caption.getVisibility() == View.VISIBLE ? dp(TABS_DP) - dp(EDGE) + dp(4) : 0));
        Item r = new Item();
        r.art = art;
        r.title = appLabel;
        r.subtitle = lastOpened;
        r.backdrop = backdropFor(packageName);
        r.liveBackdrop = backdrops.get(packageName);
        resume = r;
        if (packageName == null) { hide(); return; }
        if (packageName.equals(shownPackage)) {
            if (strip.getChildCount() > 0) setShown(true);
            return;
        }
        shownPackage = packageName;
        label = appLabel;
        // Another app's row starts from its first card.
        scroller.scrollTo(0, 0);
        int gen = ++generation;
        Long at = cachedAt.get(packageName);
        if (at != null && System.currentTimeMillis() - at < CACHE_MS) {
            fill(cache.get(packageName));
            return;
        }
        strip.removeAllViews();
        setShown(false);
        reader.execute(() -> {
            List<Item> items = read(packageName);
            String live = items.isEmpty() ? findBackdrop(packageName) : null;
            main.post(() -> {
                if (live != null) backdrops.put(packageName, live);
                if (resume != null && live != null && packageName.equals(shownPackage)) resume.liveBackdrop = live;
            });
            main.post(() -> {
                cache.put(packageName, items);
                cachedAt.put(packageName, System.currentTimeMillis());
                if (gen == generation) fill(items);
            });
        });
    }

    /** Home goes to the background: no trailer plays behind the app it opened. */
    void pause() {
        stopTrailer();
    }

    void hide() {
        stopTrailer();
        if (panel.hasFocus()) return;
        shownPackage = null;
        generation++;
        setShown(false);
    }

    /** Focus the first card; false when there is none. */
    boolean focus() {
        if (!hasCards()) return false;
        scroller.scrollTo(0, 0);
        return strip.getChildAt(0).requestFocus();
    }

    void close() {
        stopTrailer();
        reader.shutdownNow();
        images.shutdownNow();
        posters.evictAll();
    }

    void trim() {
        posters.evictAll();
        cache.clear();
        cachedAt.clear();
    }

    // ---- reading ---------------------------------------------------------------------------------

    private List<Item> read(String pkg) {
        List<Item> out = new ArrayList<>();
        ContentResolver cr = context.getContentResolver();
        // A caller without ACCESS_ALL_EPG_DATA may not pass a selection: read the table and filter here.
        try (Cursor c = cr.query(Uri.parse("content://android.media.tv/watch_next_program"), null, null, null,
                null)) {
            if (c != null) {
                List<long[]> order = new ArrayList<>();
                List<Item> found = new ArrayList<>();
                int iPkg = c.getColumnIndex("package_name"), iTitle = c.getColumnIndex("title"),
                        iPoster = c.getColumnIndex("poster_art_uri"), iThumb = c.getColumnIndex("thumbnail_uri"),
                        iIntent = c.getColumnIndex("intent_uri"), iPos = c.getColumnIndex("last_playback_position_millis"),
                        iDur = c.getColumnIndex("duration_millis"), iWhen = c.getColumnIndex("last_engagement_time_utc_millis"),
                        iEp = c.getColumnIndex("episode_title");
                while (c.moveToNext()) {
                    if (!pkg.equals(str(c, iPkg))) continue;
                    Item it = new Item();
                    it.title = str(c, iTitle);
                    String ep = str(c, iEp);
                    if (ep != null && !ep.isEmpty() && !ep.equals(it.title)) it.episode = ep;
                    it.poster = first(str(c, iPoster), str(c, iThumb));
                    it.intent = str(c, iIntent);
                    it.continueWatching = true;
                    long pos = iPos >= 0 ? c.getLong(iPos) : 0, dur = iDur >= 0 ? c.getLong(iDur) : 0;
                    if (pos > 0 && dur > 0) {
                        it.progress = Math.min(1f, pos / (float) dur);
                        it.minutesLeft = (int) Math.max(1, Math.round((dur - pos) / 60_000.0));
                    }
                    order.add(new long[]{iWhen >= 0 ? c.getLong(iWhen) : 0, found.size()});
                    found.add(it);
                }
                order.sort((a, b) -> Long.compare(b[0], a[0]));
                for (long[] o : order) {
                    if (out.size() >= MAX_CONTINUE) break;
                    out.add(found.get((int) o[1]));
                }
            }
        } catch (Exception ignored) {
        }
        int recommended = 0;
        try (Cursor c = cr.query(Uri.parse("content://android.media.tv/preview_program"), null, null, null,
                null)) {
            if (c != null) {
                int iPkg = c.getColumnIndex("package_name"), iTitle = c.getColumnIndex("title"),
                        iPoster = c.getColumnIndex("poster_art_uri"), iThumb = c.getColumnIndex("thumbnail_uri"),
                        iIntent = c.getColumnIndex("intent_uri"), iBrowsable = c.getColumnIndex("browsable");
                while (c.moveToNext() && recommended < MAX_RECOMMENDED) {
                    if (!pkg.equals(str(c, iPkg))) continue;
                    if (iBrowsable >= 0 && c.getInt(iBrowsable) == 0) continue;
                    Item it = new Item();
                    it.title = str(c, iTitle);
                    it.poster = first(str(c, iPoster), str(c, iThumb));
                    it.intent = str(c, iIntent);
                    out.add(it);
                    recommended++;
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static String str(Cursor c, int i) {
        return i >= 0 && !c.isNull(i) ? c.getString(i) : null;
    }

    private static String first(String a, String b) {
        return a != null && !a.isEmpty() ? a : b;
    }

    // ---- cards -----------------------------------------------------------------------------------

    /**
     * Card size in px. A card stands as a 2:3 poster and widens to 16:9 while it has focus, the way Netflix's rows do;
     * fit() sizes the height to the space between the header and the dock, up to MAX_CARD_DP.
     */
    private int cardH = -1;
    private static final int MAX_CARD_DP = 240;
    private static final long WIDEN_MS = 220L;
    /** The picture's height: the card's, less the name and line under it. */
    private int imageHeight() { return cardHeight() - dp(LABEL_DP); }
    private int cardWidth() { return Math.round(imageHeight() * 16f / 9f); }
    private int posterWidth() { return Math.round(imageHeight() * 2f / 3f); }
    private int cardHeight() { return cardH > 0 ? cardH : dp(135); }
    /** What a card changes between its poster and its wide look, beyond its width (wide true or false). */
    private final java.util.WeakHashMap<View, java.util.function.Consumer<Boolean>> wideLooks = new java.util.WeakHashMap<>();

    /** Focus: the card grows a little and widens from poster to 16:9; it narrows again when focus moves on. */
    /** A card's own {poster, wide} widths, and the view its focus ring goes round, when not the card's defaults. */
    private final java.util.WeakHashMap<View, int[]> cardWidths = new java.util.WeakHashMap<>();
    private final java.util.WeakHashMap<View, View> ringOn = new java.util.WeakHashMap<>();

    /**
     * A tile: the picture's frame (rounded, the focus ring round it) and under it the name and a quieter line, as the
     * trending posters have. The tile takes focus, widens and narrows; the frame only draws.
     */
    private LinearLayout wrap(FrameLayout frame, CharSequence name, CharSequence line, int lineColor, int index, int count,
            GradientDrawable ring) {
        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setFocusable(true);
        tile.setClipChildren(false);
        tile.setClipToPadding(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(posterWidth(), cardHeight());
        lp.rightMargin = dp(14);
        tile.setLayoutParams(lp);
        frame.setFocusable(false);
        tile.addView(frame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, imageHeight()));
        ringOn.put(tile, frame);
        TextView n = new TextView(context);
        n.setText(name == null ? "" : name);
        n.setTextColor(Color.WHITE);
        n.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        n.setTypeface(context.getResources().getFont(R.font.outfit_medium));
        n.setSingleLine(true);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = dp(5);
        nlp.leftMargin = dp(2);
        tile.addView(n, nlp);
        if (line != null && line.length() > 0) {
            TextView l = new TextView(context);
            l.setText(line);
            l.setTextColor(lineColor);
            l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            l.setTypeface(context.getResources().getFont(R.font.outfit_regular));
            l.setSingleLine(true);
            l.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            llp.leftMargin = dp(2);
            tile.addView(l, llp);
        }
        tile.setOnFocusChangeListener((v, focused) -> focusLook(v, focused, ring));
        tile.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) { host.onCardsUp(); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) { host.onCardsDown(); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return index == 0;
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return index == count - 1;
            return false;
        });
        return tile;
    }

    private static FrameLayout frameOf(View tile) {
        return (FrameLayout) ((ViewGroup) tile).getChildAt(0);
    }

    private void focusLook(View v, boolean focused, GradientDrawable ring) {
        v.animate().scaleX(focused ? FOCUS_SCALE : 1f).scaleY(focused ? FOCUS_SCALE : 1f).setDuration(160).start();
        View framed = ringOn.containsKey(v) ? ringOn.get(v) : v;
        framed.setForeground(focused ? ring : null);
        v.setElevation(focused ? dp(6) : 0);
        int[] sizes = cardWidths.get(v);
        java.util.function.Consumer<Boolean> look = wideLooks.get(v);
        if (look != null && focused) look.accept(true);
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        int to = sizes != null ? sizes[focused ? 1 : 0] : focused ? cardWidth() : posterWidth();
        android.animation.ValueAnimator widen = android.animation.ValueAnimator.ofInt(lp.width, to);
        widen.setDuration(WIDEN_MS);
        widen.setInterpolator(new android.view.animation.DecelerateInterpolator(1.4f));
        widen.addUpdateListener(a -> { lp.width = (int) a.getAnimatedValue(); v.requestLayout(); });
        widen.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                if (look != null && !focused) look.accept(false);
                // Wider now: keep all of it on screen.
                if (focused) {
                    int right = v.getRight() + dp(EDGE) * 2 - scroller.getScrollX();
                    if (right > scroller.getWidth()) scroller.smoothScrollBy(right - scroller.getWidth(), 0);
                }
            }
        });
        widen.start();
    }

    /**
     * Sizes the cards to fill from {@code top} (just under the header) to {@code bottom} (just above the dock),
     * and returns where the panel's top goes. Cards already on screen are rebuilt when the size changes.
     */
    float fit(float top, float bottom) {
        int chrome = height() - cardHeight();
        int h = Math.max(dp(110), Math.min(dp(MAX_CARD_DP), Math.round(bottom - top) - chrome));
        if (h != cardH) {
            cardH = h;
            if (shownPackage != null && cache.containsKey(shownPackage)) fill(cache.get(shownPackage));
        }
        return bottom - height();
    }

    private void setShown(boolean shown) {
        boolean was = panel.getVisibility() == View.VISIBLE;
        panel.setVisibility(shown ? View.VISIBLE : View.GONE);
        if (was != shown) host.onCardsShown(shown);
    }

    private void fill(List<Item> items) {
        stopTrailer();
        setTabs(false);
        strip.removeAllViews();
        if (items == null || items.isEmpty()) {
            // An app that publishes nothing (TiviMate, Debrid Stream): BBC News across the whole row instead.
            if (!showWhatsOn() && !showTrending() && !showNews()) setShown(false);
            return;
        }
        for (int i = 0; i < items.size(); i++) strip.addView(card(items.get(i), i, items.size()));

        setCaption(items.get(0));
        setShown(true);
        panel.setAlpha(0f);
        panel.animate().alpha(1f).setDuration(220).start();
    }

    // ---- What's on (a theme's "whats_on"): today's games and the next F1 session, on the TiviMate row ------------

    private List<ScoresTicker.Game> whatsOn = new ArrayList<>();
    /** What is on British TV now (UkTv), shown before the fixtures. */
    private List<UkTv.Now> tvNow = new ArrayList<>();
    /** The British channel each fixture is live on (TvGuide), by game id. */
    private Map<String, TvGuide.Channel> liveOn = new HashMap<>();
    private long whatsOnAt;
    private Boolean whatsOnOn;

    private boolean showWhatsOn() {
        if (whatsOnOn == null) whatsOnOn = Config.themeFlag(context, "whats_on", false);
        String pkg = context.getString(R.string.scores_open_package);
        if (!whatsOnOn || !pkg.equals(shownPackage)) return false;
        if (System.currentTimeMillis() - whatsOnAt >= 2 * 60_000L) {
            whatsOnAt = System.currentTimeMillis();
            final int gen = generation;
            String[] leagues = context.getResources().getStringArray(R.array.score_leagues);
            String[] channels = context.getResources().getStringArray(R.array.uk_tv_channels);
            reader.execute(() -> {
                List<UkTv.Now> tv = UkTv.now(channels);
                List<ScoresTicker.Game> games = new ArrayList<>();
                for (ScoresTicker.Game g : ScoresTicker.load(leagues)) if (g.state != 2) games.add(g);
                ScoresTicker.Game f1 = ScoresTicker.f1Game(F1Results.weekend(null));
                if (f1 != null && f1.state != 2) games.add(f1);
                games.sort((a, b) -> a.state != b.state ? Integer.compare(a.state, b.state) : Long.compare(a.kickoff, b.kickoff));
                List<ScoresTicker.Game> top = new ArrayList<>(games.subList(0, Math.min(12, games.size())));
                String[] football = context.getResources().getStringArray(R.array.football_tv_channels);
                Map<String, TvGuide.Channel> on = new HashMap<>();
                for (ScoresTicker.Game g : top) {
                    TvGuide.Channel c = TvGuide.find(football, g.homeName, g.awayName, g.kickoff);
                    if (c != null && g.id != null) on.put(g.id, c);
                }
                main.post(() -> {
                    if (top.isEmpty() && tv.isEmpty()) { whatsOnAt = 0; }
                    whatsOn = top;
                    tvNow = tv;
                    liveOn = on;
                    if (gen == generation && pkg.equals(shownPackage) && (!top.isEmpty() || !tv.isEmpty())) fillWhatsOn();
                });
            });
        }
        if (whatsOn.isEmpty() && tvNow.isEmpty()) return false;
        fillWhatsOn();
        return true;
    }

    private void fillWhatsOn() {
        setTabs(false);
        strip.removeAllViews();
        int count = tvNow.size() + whatsOn.size();
        for (int i = 0; i < tvNow.size(); i++) strip.addView(tvCard(tvNow.get(i), i, count));
        for (int i = 0; i < whatsOn.size(); i++) strip.addView(whatsOnCard(whatsOn.get(i), tvNow.size() + i, count));
        setShown(true);
        panel.setAlpha(0f);
        panel.animate().alpha(1f).setDuration(220).start();
    }

    /** One fixture over a dimmed stadium: the competition, both crests, the time or the live score. OK opens TiviMate. */
    private View whatsOnCard(ScoresTicker.Game g, int index, int count) {
        FrameLayout card = new FrameLayout(context);
        card.setClipToOutline(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF15171C);
        bg.setCornerRadius(dp(10));
        card.setBackground(bg);
        GradientDrawable ring = new GradientDrawable();
        ring.setColor(Color.TRANSPARENT);
        ring.setCornerRadius(dp(10));
        ring.setStroke(dp(3), Color.WHITE);
        boolean f1 = g.drivers != null || g.text != null;
        ImageView scene = new ImageView(context);
        scene.setScaleType(ImageView.ScaleType.CENTER_CROP);
        scene.setImageResource(R.drawable.backdrop_stadium);
        scene.setAlpha(f1 ? 0.25f : 0.45f);
        card.addView(scene, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View shade = new View(context);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x99000000, 0x33000000, 0xCC000000}));
        card.addView(shade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        int crest = Math.round(imageHeight() * 0.28f);
        // Top-left: the competition.
        LinearLayout head = new LinearLayout(context);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView leagueLogo = new ImageView(context);
        leagueLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (g.leagueLogo != null) loadPoster(g.leagueLogo, leagueLogo);
        head.addView(leagueLogo, new LinearLayout.LayoutParams(dp(f1 ? 44 : 26), dp(26)));
        TextView tag = new TextView(context);
        tag.setText(f1 ? "FORMULA 1" : g.league);
        tag.setTextColor(0xE6FFFFFF);
        tag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        tag.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tag.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = dp(10);
        head.addView(tag, tlp);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        hlp.leftMargin = hlp.topMargin = dp(16);
        card.addView(head, hlp);
        // Middle: crest, score or time, crest; F1 shows its session and the Grand Prix instead.
        LinearLayout mid = new LinearLayout(context);
        mid.setGravity(Gravity.CENTER);
        String centre = g.state == 1 ? "v" : g.homeScore + " – " + g.awayScore;
        if (f1) {
            centre = g.home + (g.text != null ? "\n" + g.text : "");
        } else {
            ImageView h = new ImageView(context);
            h.setScaleType(ImageView.ScaleType.FIT_CENTER);
            loadPoster(g.homeLogo, h);
            mid.addView(h, new LinearLayout.LayoutParams(crest, crest));
        }
        TextView c = new TextView(context);
        c.setText(centre);
        c.setTextColor(Color.WHITE);
        c.setGravity(Gravity.CENTER);
        c.setTextSize(TypedValue.COMPLEX_UNIT_SP, f1 ? 22f : g.state == 1 ? 22f : 34f);
        c.setTypeface(context.getResources().getFont(R.font.outfit_medium));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = clp.rightMargin = dp(f1 ? 0 : 24);
        mid.addView(c, clp);
        if (!f1) {
            ImageView a = new ImageView(context);
            a.setScaleType(ImageView.ScaleType.FIT_CENTER);
            loadPoster(g.awayLogo, a);
            mid.addView(a, new LinearLayout.LayoutParams(crest, crest));
        }
        card.addView(mid, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        mid.setOrientation(LinearLayout.VERTICAL);
        clp.leftMargin = clp.rightMargin = 0;
        clp.topMargin = clp.bottomMargin = dp(f1 ? 0 : 6);
        // Under the picture: the teams (the Grand Prix for F1) and when, green while live.
        String when = g.state == 0 ? "●  LIVE  " + g.detail : whenText(g);
        String teams = f1 ? (g.text != null ? g.text : "Formula 1") : g.home + "  v  " + g.away;
        TvGuide.Channel tv = g.id == null ? null : liveOn.get(g.id);
        if (tv != null) {
            // Bottom-right: "LIVE ON" and the channel's logo.
            LinearLayout on = new LinearLayout(context);
            on.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = new TextView(context);
            label.setText("LIVE ON");
            label.setTextColor(0xCCFFFFFF);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            label.setLetterSpacing(0.1f);
            on.addView(label);
            ImageView logo = new ImageView(context);
            logo.setScaleType(ImageView.ScaleType.FIT_END);
            loadPoster(tv.logo, logo);
            LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(dp(150), dp(20));
            olp.leftMargin = dp(8);
            on.addView(logo, olp);
            FrameLayout.LayoutParams onlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.END);
            onlp.rightMargin = dp(14);
            onlp.bottomMargin = dp(12);
            card.addView(on, onlp);
            on.setAlpha(0f);
        }
        final View channel = tv != null ? card.getChildAt(card.getChildCount() - 1) : null;
        final boolean f1Card = f1;
        LinearLayout tile = wrap(card, teams, when, g.state == 0 ? 0xFF4ADE80 : 0x99FFFFFF, index, count, ring);
        // As a poster: the crests stacked over the score. Wide: side by side, and the channel too.
        wideLooks.put(tile, wide -> {
            mid.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
            clp.leftMargin = clp.rightMargin = wide && !f1Card ? dp(24) : 0;
            clp.topMargin = clp.bottomMargin = wide || f1Card ? 0 : dp(6);
            c.setLayoutParams(clp);
            if (channel != null) channel.animate().alpha(wide ? 1f : 0f).setDuration(WIDEN_MS).start();
        });
        tile.setOnClickListener(v -> host.onResumeApp());
        return tile;
    }

    /**
     * A programme on now: its picture across the card with the channel's logo in the corner, the title, how long is
     * left and what follows, and how far through it is. OK opens TiviMate.
     */
    private View tvCard(UkTv.Now n, int index, int count) {
        Item item = new Item();
        item.title = n.title;
        item.poster = n.image;
        long now = System.currentTimeMillis();
        item.progress = Math.max(0f, Math.min(1f, (now - n.start) / (float) Math.max(1, n.end - n.start)));
        item.minutesLeft = (int) Math.max(1, (n.end - now) / 60_000L);
        String next = n.next == null ? "" : "  ·  Next " + java.time.Instant.ofEpochMilli(n.nextStart)
                .atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) + " " + n.next;
        item.episode = n.channel + next;
        Intent open = context.getPackageManager().getLeanbackLaunchIntentForPackage(context.getString(R.string.scores_open_package));
        item.intent = open == null ? null : open.toUri(Intent.URI_INTENT_SCHEME);
        View tile = card(item, index, count);
        FrameLayout card = frameOf(tile);
        // The channel's logo, top-left on a soft shade so a light picture never swallows it.
        View topShade = new View(context);
        topShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x99000000, 0x00000000}));
        card.addView(topShade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(90), Gravity.TOP));
        ImageView logo = new ImageView(context);
        logo.setScaleType(ImageView.ScaleType.FIT_START);
        loadPoster(n.logo, logo);
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(dp(96), dp(34), Gravity.TOP | Gravity.START);
        llp.leftMargin = llp.topMargin = dp(10);
        card.addView(logo, llp);
        return tile;
    }

    /** "Today 20:00", "Tomorrow 15:00" or "Sat 15:00". */
    private static String whenText(ScoresTicker.Game g) {
        java.time.ZonedDateTime at = java.time.Instant.ofEpochMilli(g.kickoff).atZone(java.time.ZoneId.systemDefault());
        java.time.LocalDate today = java.time.LocalDate.now();
        String time = at.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        if (at.toLocalDate().equals(today)) return "Today " + time;
        if (at.toLocalDate().equals(today.plusDays(1))) return "Tomorrow " + time;
        return at.format(java.time.format.DateTimeFormatter.ofPattern("EEE HH:mm", java.util.Locale.getDefault()));
    }

    // ---- Trending (a theme's "trending"): films and TV from Apple's charts, on Debrid Stream's row ----------------

    private List<Trending.Title> trending = new ArrayList<>();
    private long trendingAt;
    private Boolean trendingOn;
    private static final long TRENDING_MS = 30 * 60_000L;

    /** The focused app is the one config.xml trending_package names, and the theme wants trending there. */
    private boolean trendingRow() {
        if (trendingOn == null) trendingOn = Config.themeFlag(context, "trending", false);
        return trendingOn && context.getString(R.string.trending_package).equals(shownPackage);
    }

    /** Trending films and TV, then the news after them; read when stale and shown when it arrives. */
    private boolean showTrending() {
        if (!trendingRow()) return false;
        if (System.currentTimeMillis() - trendingAt >= TRENDING_MS) {
            trendingAt = System.currentTimeMillis();
            final int gen = generation;
            String country = context.getString(R.string.trending_country);
            reader.execute(() -> {
                List<Trending.Title> fresh = Trending.read(country, 10);
                main.post(() -> {
                    if (fresh.isEmpty()) { trendingAt = 0; return; }
                    trending = fresh;
                    if (gen == generation && trendingRow()) fillTrending();
                });
                // The badges come from a page per title: the row shows first and takes them in once they are all read,
                // unless a card has focus then (it gets them the next time the row is drawn).
                Trending.badges(context, fresh);
                main.post(() -> {
                    if (trending == fresh && trendingRow() && !panel.hasFocus()) fillTrending();
                });
            });
        }
        // The news is read alongside, for the cards after the trending ones.
        refreshNews();
        if (trending.isEmpty()) return false;
        fillTrending();
        return true;
    }

    /** The trending row on show: films (with the news after them) or, Up from there, TV. */
    private boolean tvTab;
    private float baseY;
    private static final int TABS_DP = 26;

    private void fillTrending() {
        stopTrailer();
        strip.removeAllViews();
        List<Trending.Title> row = new ArrayList<>();
        for (Trending.Title t : trending) if (t.tv == tvTab) row.add(t);
        List<String[]> after = tvTab ? new ArrayList<>() : news;
        int count = row.size() + after.size();
        for (int i = 0; i < row.size(); i++) strip.addView(trendCard(row.get(i), i, count));
        for (int i = 0; i < after.size(); i++) strip.addView(newsCard(after.get(i), row.size() + i, count));
        setTabs(true);
        setShown(true);
        panel.setAlpha(0f);
        panel.animate().alpha(1f).setDuration(220).start();
    }

    /**
     * The two trending rows' names over the cards, the one on show bright: "Trending Films   Trending TV". They sit in
     * the room above the cards, which are no taller than MAX_CARD_DP.
     */
    private void setTabs(boolean on) {
        caption.setVisibility(on ? View.VISIBLE : View.GONE);
        panel.setY(baseY - (on ? dp(TABS_DP) - dp(EDGE) + dp(4) : 0));
        if (!on) return;
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
        String[] names = {"Trending Films", "Trending TV"};
        for (int i = 0; i < 2; i++) {
            int at = sb.length();
            sb.append(names[i]);
            boolean active = (i == 1) == tvTab;
            sb.setSpan(new android.text.style.ForegroundColorSpan(active ? Color.WHITE : 0x66FFFFFF), at, sb.length(), 0);
            if (active) sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), at, sb.length(), 0);
            if (i == 0) sb.append(tvTab ? "      ▾ " : "      ▴ ");
        }
        caption.setText(sb);
    }

    /** Up from the films row shows the TV row, Down from the TV row the films again; focus keeps its place. */
    private void switchRow(boolean tv, int index) {
        tvTab = tv;
        fillTrending();
        strip.setTranslationY(dp(tv ? -24 : 24));
        strip.animate().translationY(0f).setDuration(220).start();
        strip.post(() -> {
            int i = Math.min(index, strip.getChildCount() - 1);
            if (i >= 0) strip.getChildAt(i).requestFocus();
        });
    }

    /** Room under a trending poster for its name and year. */
    private static final int LABEL_DP = 40;

    /**
     * One title, BetterPosters style: the poster with "#1 Today" top-left, the Dolby mark and 4K or 1080p top-right,
     * and its genre at the foot over a fade in the poster's own colour; its name and year underneath.
     * Wide, the poster widens to 16:9: a film shows its whole poster on the right over a dimmed crop of itself, a series
     * its picture, both with the synopsis on the left. OK opens Debrid Stream on its search for the title.
     */
    private View trendCard(Trending.Title t, int index, int count) {
        int h = cardHeight(), ph = h - dp(LABEL_DP);
        int narrow = Math.round(ph * 2f / 3f), wideW = Math.round(ph * 16f / 9f);
        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setFocusable(true);
        tile.setClipChildren(false);
        tile.setClipToPadding(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(narrow, h);
        lp.rightMargin = dp(14);
        tile.setLayoutParams(lp);
        cardWidths.put(tile, new int[]{narrow, wideW});

        FrameLayout card = new FrameLayout(context);
        card.setClipToOutline(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1C1C22);
        bg.setCornerRadius(dp(12));
        card.setBackground(bg);
        tile.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ph));
        ringOn.put(tile, card);
        GradientDrawable ring = new GradientDrawable();
        ring.setColor(Color.TRANSPARENT);
        ring.setCornerRadius(dp(12));
        ring.setStroke(dp(3), Color.WHITE);

        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // The foot fades into the poster's own colour, darkened, under the genre and rating.
        View foot = new View(context);
        foot.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0xCC000000, 0xF2000000}));
        card.addView(foot, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(ph * 0.42f), Gravity.BOTTOM));
        java.util.function.Consumer<Bitmap> tint = b -> {
            int c = footColour(b);
            foot.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{c & 0x00FFFFFF, (c & 0x00FFFFFF) | 0xCC000000, (c & 0x00FFFFFF) | 0xF5000000}));
        };
        if (t.tv) loadPoster(t.crop(wideW, ph), image, tint);
        else loadPoster(t.poster(narrow, ph), image, tint);

        List<java.util.function.Consumer<Boolean>> looks = new ArrayList<>();
        // Wide: a shade over the left for the synopsis, and a film's whole poster on the right.
        View side = new View(context);
        side.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                t.tv ? new int[]{0xF2000000, 0xB3000000, 0x00000000} : new int[]{0xF7000000, 0xCC000000, 0x80000000}));
        card.addView(side, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        List<View> wideOnly = new ArrayList<>();
        wideOnly.add(side);
        int pw = Math.round((ph - dp(24)) * 2f / 3f);
        if (!t.tv) {
            // In a box of its own: the image's fade-in as it loads must not show it while the card is a poster.
            FrameLayout poster = new FrameLayout(context);
            poster.setClipToOutline(true);
            GradientDrawable round = new GradientDrawable();
            round.setColor(0xFF1C1C22);
            round.setCornerRadius(dp(8));
            poster.setBackground(round);
            ImageView posterArt = new ImageView(context);
            posterArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
            loadPoster(t.poster(narrow, ph), posterArt);
            poster.addView(posterArt, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(pw, ph - dp(24), Gravity.END | Gravity.CENTER_VERTICAL);
            plp.rightMargin = dp(12);
            card.addView(poster, plp);
            wideOnly.add(poster);
        }
        if (!t.summary.isEmpty()) {
            TextView about = synopsis(t.summary, ph);
            FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(t.tv ? Math.round(wideW * 0.55f) : wideW - pw - dp(48),
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
            alp.leftMargin = dp(14);
            alp.topMargin = dp(44);
            card.addView(about, alp);
            wideOnly.add(about);
        }
        for (View x : wideOnly) x.setAlpha(0f);
        looks.add(wide -> { for (View x : wideOnly) x.animate().alpha(wide ? 1f : 0f).setDuration(WIDEN_MS).start(); });
        // Netflix-style: once it has been wide a moment, the trailer plays across the card, over the artwork and
        // under the marks; it stops when the card narrows.
        looks.add(wide -> {
            main.removeCallbacks(trailerStart);
            if (!wide) { if (playingTile == tile) stopTrailer(); return; }
            trailerStart = () -> { if (tile.hasFocus()) playTrailer(tile, card, t, wideOnly); };
            main.postDelayed(trailerStart, TRAILER_DELAY_MS);
        });

        // Foot of the poster: just the genre, centred; wide, it moves left under the synopsis. The first of a double
        // genre ("Action & Adventure") keeps it short.
        TextView line = score("", 0xD9FFFFFF);
        String foot2 = t.genre.split(" & | / ")[0];
        line.setText(foot2);
        line.setGravity(Gravity.CENTER);
        line.setSingleLine(true);
        line.setEllipsize(android.text.TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        llp.leftMargin = llp.rightMargin = dp(8);
        llp.bottomMargin = dp(12);
        card.addView(line, llp);
        looks.add(wide -> line.setGravity(wide ? Gravity.START | Gravity.CENTER_VERTICAL : Gravity.CENTER));
        // A soft shade along the top, so the rank and the marks read on a light poster.
        View top = new View(context);
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x8C000000, 0x00000000}));
        card.addView(top, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), Gravity.TOP));
        looks.add(wide -> { llp.leftMargin = dp(wide ? 14 : 8); line.setLayoutParams(llp); });

        // Top: "#1 Today" on the left, the Dolby mark and the picture's quality on the right.
        TextView rank = new TextView(context);
        rank.setText("#" + t.rank + " Today");
        rank.setTextColor(Color.WHITE);
        rank.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
        rank.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0xCC1E2019);
        pill.setCornerRadius(dp(6));
        rank.setBackground(pill);
        rank.setPadding(dp(5), dp(2), dp(5), dp(2));
        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        rlp.leftMargin = rlp.topMargin = dp(7);
        card.addView(rank, rlp);
        View marks = marks(t);
        if (marks != null) {
            FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(17), Gravity.TOP | Gravity.END);
            mlp.rightMargin = dp(5);
            mlp.topMargin = dp(7);
            card.addView(marks, mlp);
        }

        // Under the poster: the name, and the year (a series' season too).
        TextView name = new TextView(context);
        name.setText(t.title);
        name.setTextColor(Color.WHITE);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        name.setTypeface(context.getResources().getFont(R.font.outfit_medium));
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = dp(5);
        nlp.leftMargin = dp(2);
        tile.addView(name, nlp);
        TextView year = new TextView(context);
        year.setText(t.year + (t.season != null ? "  ·  " + t.season : ""));
        year.setTextColor(0x99FFFFFF);
        year.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        year.setTypeface(context.getResources().getFont(R.font.outfit_regular));
        year.setSingleLine(true);
        LinearLayout.LayoutParams ylp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ylp.leftMargin = dp(2);
        tile.addView(year, ylp);

        wideLooks.put(tile, wide -> { for (java.util.function.Consumer<Boolean> l : looks) l.accept(wide); });
        tile.setOnFocusChangeListener((v, focused) -> focusLook(v, focused, ring));
        tile.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) { if (tvTab) host.onCardsUp(); else switchRow(true, index); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) { if (tvTab) switchRow(false, index); else host.onCardsDown(); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return index == 0;
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return index == count - 1;
            return false;
        });
        // OK: the app opens on its search for this title (AppSearchService), or just opens without that helper.
        tile.setOnClickListener(v -> {
            stopTrailer();
            if (!AppSearchService.search(context, context.getString(R.string.trending_package), t.title)) host.onResumeApp();
        });
        return tile;
    }

    // ---- trailers ----------------------------------------------------------------------------------

    private static final long TRAILER_DELAY_MS = 1_200L;
    private Runnable trailerStart = () -> { };
    private android.media.MediaPlayer player;
    private android.view.TextureView playerView;
    private View playingTile;
    private List<View> playerCovers = new ArrayList<>();

    /**
     * The title's trailer in the card: the HD one from Apple's TV page, or the store's preview clip when that will
     * not play here. A TextureView, so the rounded card clips it and it widens with the card; the picture is cropped
     * to fill it (the preview's letterbox goes with that). The synopsis and the side poster make way while it plays.
     */
    private void playTrailer(View tile, FrameLayout card, Trending.Title t, List<View> covers) {
        String url = t.trailer != null ? t.trailer : t.preview;
        if (url == null) return;
        stopTrailer();
        playingTile = tile;
        playerCovers = covers;
        android.view.TextureView view = new android.view.TextureView(context);
        view.setAlpha(0f);
        card.addView(view, 1, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        playerView = view;
        view.setSurfaceTextureListener(new android.view.TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture st, int w, int h) {
                if (playerView != view) return;
                start(view, new android.view.Surface(st), url, t.trailer != null && t.preview != null ? t.preview : null);
            }
            @Override public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture st, int w, int h) { }
            @Override public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture st) { return true; }
            @Override public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture st) { }
        });
    }

    private void start(android.view.TextureView view, android.view.Surface surface, String url, String fallback) {
        android.media.MediaPlayer mp = new android.media.MediaPlayer();
        player = mp;
        try {
            mp.setSurface(surface);
            mp.setAudioAttributes(new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE).build());
            mp.setDataSource(context, Uri.parse(url));
            mp.setOnVideoSizeChangedListener((m, vw, vh) -> crop(view, vw, vh));
            mp.setOnPreparedListener(m -> {
                if (player != m) return;
                m.setVolume(0.8f, 0.8f);
                m.start();
                view.animate().alpha(1f).setDuration(500).start();
                for (View x : playerCovers) x.animate().alpha(0f).setDuration(400).start();
            });
            mp.setOnErrorListener((m, what, extra) -> {
                if (player != m) return true;
                m.release();
                player = null;
                // The HD trailer will not play on this TV: the preview clip instead.
                if (fallback != null && playerView == view) start(view, surface, fallback, null);
                else stopTrailer();
                return true;
            });
            mp.setOnCompletionListener(m -> stopTrailer());
            mp.prepareAsync();
        } catch (Exception e) {
            mp.release();
            player = null;
            if (fallback != null) start(view, surface, fallback, null);
        }
    }

    /** Scales the TextureView's picture so the video fills the card, cropped, at its own proportions. */
    private static void crop(android.view.TextureView view, int vw, int vh) {
        int w = view.getWidth(), h = view.getHeight();
        if (vw <= 0 || vh <= 0 || w <= 0 || h <= 0) return;
        float video = vw / (float) vh, card = w / (float) h;
        android.graphics.Matrix m = new android.graphics.Matrix();
        if (video > card) m.setScale(video / card, 1f, w / 2f, h / 2f);
        else m.setScale(1f, card / video, w / 2f, h / 2f);
        view.setTransform(m);
    }

    private void stopTrailer() {
        main.removeCallbacks(trailerStart);
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            player.release();
            player = null;
        }
        if (playerView != null) {
            android.view.TextureView view = playerView;
            playerView = null;
            if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
        }
        // What made way comes back if the card is still wide.
        if (playingTile != null && playingTile.hasFocus()) for (View x : playerCovers) x.animate().alpha(1f).setDuration(300).start();
        playingTile = null;
        playerCovers = new ArrayList<>();
    }

    /** The poster's foot colour: the average of its bottom fifth, darkened to a deep shade of it. */
    private static int footColour(Bitmap b) {
        long r = 0, g = 0, bl = 0, n = 0;
        int w = b.getWidth(), hh = b.getHeight();
        for (int y = hh * 4 / 5; y < hh; y += Math.max(1, hh / 60)) {
            for (int x = 0; x < w; x += Math.max(1, w / 24)) {
                int c = b.getPixel(x, y);
                r += (c >> 16) & 0xFF; g += (c >> 8) & 0xFF; bl += c & 0xFF; n++;
            }
        }
        if (n == 0) return 0xFF000000;
        float[] hsv = new float[3];
        Color.RGBToHSV((int) (r / n), (int) (g / n), (int) (bl / n), hsv);
        hsv[2] = Math.min(hsv[2], 0.22f);
        hsv[1] = Math.min(hsv[1], 0.75f);
        return Color.HSVToColor(hsv);
    }

    /** Top-right of a poster: the Dolby mark (Atmos and Vision together when it has both), HDR, and 4K or 1080p. */
    private View marks(Trending.Title t) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        if (t.dolbyVision && t.atmos) row.addView(logo(R.drawable.badge_dolby_atmos_vision, 11.5f, 4));
        else if (t.dolbyVision) row.addView(logo(R.drawable.badge_dolby_vision, 11.5f, 4));
        else if (t.atmos) row.addView(logo(R.drawable.badge_dolby_atmos, 11.5f, 4));
        if (!t.dolbyVision && t.hdr10plus) row.addView(logo(R.drawable.badge_hdr10plus, 10, 4));
        else if (!t.dolbyVision && t.hdr) row.addView(logo(R.drawable.badge_hdr, 10, 4));
        if ("4K".equals(t.quality)) row.addView(logo(R.drawable.badge_4k, 11, 0));
        else if ("HD".equals(t.quality)) row.addView(resolution("1080", "p"));
        return row.getChildCount() == 0 ? null : row;
    }

    /** "1080p" on a light chip, the p small, as the quality marks are drawn. */
    private TextView resolution(String number, String unit) {
        TextView v = new TextView(context);
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(number + unit);
        sb.setSpan(new android.text.style.RelativeSizeSpan(0.75f), number.length(), sb.length(), 0);
        v.setText(sb);
        v.setTextColor(0xFF1C1C1E);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f);
        v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        v.setIncludeFontPadding(false);
        GradientDrawable d = new GradientDrawable();
        d.setColor(0xFFEBEBF5);
        d.setCornerRadius(dp(3));
        v.setBackground(d);
        v.setPadding(dp(4), dp(2), dp(4), dp(2));
        return v;
    }

    private TextView synopsis(String text, int h) {
        TextView about = new TextView(context);
        about.setText(text);
        about.setTextColor(0xB3FFFFFF);
        about.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        about.setTypeface(context.getResources().getFont(R.font.outfit_regular));
        about.setLineSpacing(0, 1.1f);
        about.setMaxLines(h > dp(200) ? 4 : 3);
        about.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return about;
    }

    /** An official mark, heightDp tall at its own proportions, with a soft shadow so it reads on a light poster. */
    private View logo(int res, float heightDp, int gapDp) {
        ImageView v = new ImageView(context);
        android.graphics.drawable.Drawable d = context.getDrawable(res);
        v.setImageDrawable(d);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int hh = dp(heightDp);
        int ww = d == null || d.getIntrinsicHeight() <= 0 ? hh : Math.round(hh * d.getIntrinsicWidth() / (float) d.getIntrinsicHeight());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ww, hh);
        lp.rightMargin = dp(gapDp);
        v.setLayoutParams(lp);
        v.setElevation(dp(2));
        return v;
    }

    private TextView score(String text, int color) {
        TextView v = new TextView(context);
        v.setText(text);
        v.setTextColor(color);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        v.setTypeface(context.getResources().getFont(R.font.outfit_medium));
        v.setShadowLayer(dp(4), 0, dp(1), 0xCC000000);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(4);
        v.setLayoutParams(lp);
        return v;
    }



    /** The news row: from the cache when fresh, else read and shown when it arrives. False when there is none yet. */
    private boolean showNews() {
        if (context.getString(R.string.news_panel_url).isEmpty()) return false;
        refreshNews();
        if (news.isEmpty()) return false;
        fillNews();
        return true;
    }

    /** Reads the news when it is stale; it fills the row when it comes, if the row is still waiting for it. */
    private void refreshNews() {
        String feed = context.getString(R.string.news_panel_url);
        if (feed.isEmpty()) return;
        if (System.currentTimeMillis() - newsAt >= NewsPanel.EVERY_MS) {
            newsAt = System.currentTimeMillis();
            final int gen = generation;
            reader.execute(() -> {
                List<String[]> fresh = NewsPanel.readAll(feed);
                main.post(() -> {
                    if (fresh.isEmpty()) { newsAt = 0; return; }
                    news = fresh;
                    if (gen != generation) return;
                    if (trendingRow()) { if (!trending.isEmpty()) fillTrending(); }
                    else if (strip.getChildCount() == 0) fillNews();
                });
            });
        }
    }

    private void fillNews() {
        setTabs(false);
        strip.removeAllViews();
        for (int i = 0; i < news.size(); i++) strip.addView(newsCard(news.get(i), i, news.size()));
        setShown(true);
        panel.setAlpha(0f);
        panel.animate().alpha(1f).setDuration(220).start();
    }

    /** One story: its picture across the card, a BBC NEWS tag, the headline at the bottom. OK opens it. */
    private View newsCard(String[] story, int index, int count) {
        View tile = newsTile(story, index, count);
        if (trendingRow() && !tvTab) {
            tile.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) { switchRow(true, index); return true; }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) { host.onCardsDown(); return true; }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return index == 0;
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return index == count - 1;
                return false;
            });
        }
        return tile;
    }

    private View newsTile(String[] story, int index, int count) {
        Item item = new Item();
        item.title = story[0];
        item.poster = story[1];
        item.intent = story[2];
        item.subtitle = story.length > 3 ? story[3] : "BBC NEWS";
        View card = card(item, index, count);
        card.setOnClickListener(v -> {
            if (story[2] == null) return;
            try {
                context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(story[2]))
                        .addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
            }
        });
        return card;
    }

    private void setCaption(Item item) {
        String section = context.getString(item.art != null ? R.string.resume
                : item.continueWatching ? R.string.continue_watching : R.string.recommended);
        caption.setText(section + "  ·  " + label);
    }

    private View card(Item item, int index, int count) {
        FrameLayout card = new FrameLayout(context);
        card.setClipToOutline(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1C1C22);
        bg.setCornerRadius(dp(10));
        card.setBackground(bg);
        GradientDrawable ring = new GradientDrawable();
        ring.setColor(Color.TRANSPARENT);
        ring.setCornerRadius(dp(10));
        ring.setStroke(dp(3), Color.WHITE);

        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // The name and its line go under the picture; only the progress pill stays on it, over a light shade.
        if (item.progress >= 0) {
            View shade = new View(context);
            shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0x99000000}));
            card.addView(shade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36), Gravity.BOTTOM));
            // A slim rounded pill: a frosted track with a white fill that has round ends too.
            FrameLayout track = new FrameLayout(context);
            GradientDrawable trackBg = new GradientDrawable();
            trackBg.setColor(0x40FFFFFF);
            trackBg.setCornerRadius(dp(3));
            track.setBackground(trackBg);
            View fill = new View(context);
            GradientDrawable fillBg = new GradientDrawable();
            fillBg.setColor(Color.WHITE);
            fillBg.setCornerRadius(dp(3));
            fill.setBackground(fillBg);
            // The fill is a share of the track, whatever width the card has.
            LinearLayout bar = new LinearLayout(context);
            bar.addView(fill, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, Math.max(0.02f, item.progress)));
            bar.addView(new View(context), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, Math.max(0f, 1f - item.progress)));
            track.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4), Gravity.BOTTOM);
            tlp.leftMargin = tlp.rightMargin = dp(10);
            tlp.bottomMargin = dp(9);
            card.addView(track, tlp);
        }
        if (item.art != null && item.backdrop != 0) {
            // The scene fills the card; the app's own logo sits small in the top-left corner.
            image.setImageResource(item.backdrop);
            if (item.liveBackdrop != null) loadPoster(item.liveBackdrop, image);
            // The logo floats over the picture with no box: its field knocked out, a soft shadow under it.
            ImageView badge = new ImageView(context);
            badge.setImageDrawable(BannerArt.knockout(context, item.art));
            badge.setScaleType(ImageView.ScaleType.FIT_START);
            View topShade = new View(context);
            topShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0x8C000000, 0x00000000}));
            card.addView(topShade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(110), Gravity.TOP));
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(dp(150), dp(56), Gravity.TOP | Gravity.START);
            blp.leftMargin = dp(16);
            blp.topMargin = dp(16);
            card.addView(badge, blp);
        } else if (item.art != null) {
            // Never stretch a banner past MAX_ART_SCALE: a small one sits centred on its own edge colour
            // instead of turning to pixels across the whole card.
            int w = item.art.getIntrinsicWidth(), h = item.art.getIntrinsicHeight();
            if (w > 0 && h > 0) {
                float scale = Math.min(cardWidth() / (float) w, cardHeight() / (float) h);
                if (scale > MAX_ART_SCALE) {
                    int padX = Math.max(0, Math.round((cardWidth() - w * MAX_ART_SCALE) / 2f));
                    int padY = Math.max(0, Math.round((cardHeight() - h * MAX_ART_SCALE) / 2f));
                    image.setPadding(padX, padY, padX, padY);
                    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                }
                bg.setColor(BannerArt.edgeColor(item.art));
            }
            image.setImageDrawable(item.art);
        } else {
            loadPoster(item.poster, image);
        }
        String name = item.art != null ? appLabel(item) : item.title;
        String second = secondLine(item);
        boolean newsTag = NewsPanel.isTag(second);
        // BBC Sport's yellow for sport, BBC News red for the rest.
        int colour = !newsTag ? 0x99FFFFFF : "BBC SPORT".equals(second) ? 0xFFFFD230 : 0xFFFF4D4D;
        LinearLayout tile = wrap(card, name, second, colour, index, count, ring);
        tile.setOnClickListener(v -> {
            if (item.art != null) host.onResumeApp();
            else open(item);
        });
        return tile;
    }

    /**
     * A poster from YouTube's own published rows whose title matches this app's query (resume_backdrop_queries,
     * "package=words|more words"), e.g. a Premier League highlights thumbnail for TiviMate. Null when none.
     */
    private String findBackdrop(String pkg) {
        String[] words = null;
        for (String entry : context.getResources().getStringArray(R.array.resume_backdrop_queries)) {
            String[] kv = entry.split("=", 2);
            if (kv.length == 2 && kv[0].trim().equals(pkg)) words = kv[1].toLowerCase(java.util.Locale.ROOT).split("\\|");
        }
        if (words == null) return null;
        try (Cursor c = context.getContentResolver().query(Uri.parse("content://android.media.tv/preview_program"),
                null, null, null, null)) {
            if (c == null) return null;
            int iTitle = c.getColumnIndex("title"), iPoster = c.getColumnIndex("poster_art_uri");
            while (c.moveToNext()) {
                String title = str(c, iTitle), poster = str(c, iPoster);
                if (title == null || poster == null) continue;
                String t = title.toLowerCase(java.util.Locale.ROOT);
                for (String w : words) if (!w.trim().isEmpty() && t.contains(w.trim())) return poster;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** The bundled scene for this app's resume card, from config.xml resume_backdrops ("package=drawable"). */
    private int backdropFor(String pkg) {
        for (String entry : context.getResources().getStringArray(R.array.resume_backdrops)) {
            String[] kv = entry.split("=", 2);
            if (kv.length == 2 && kv[0].trim().equals(pkg)) {
                return context.getResources().getIdentifier(kv[1].trim(), "drawable", context.getPackageName());
            }
        }
        return 0;
    }

    private String appLabel(Item item) {
        return label;
    }

    /** "Episode name · 23 min left", "23 min left", "Last opened 30 minutes ago", or nothing. */
    private static String secondLine(Item item) {
        if (item.art != null || NewsPanel.isTag(item.subtitle)) return item.subtitle;
        String left = item.minutesLeft > 0 ? item.minutesLeft + " min left" : null;
        if (item.episode != null && left != null) return item.episode + "  ·  " + left;
        return item.episode != null ? item.episode : left;
    }

    private void open(Item item) {
        if (item.intent == null || item.intent.isEmpty()) return;
        try {
            Intent intent = item.intent.startsWith("intent:")
                    ? Intent.parseUri(item.intent, Intent.URI_INTENT_SCHEME)
                    : new Intent(Intent.ACTION_VIEW, Uri.parse(item.intent));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    // ---- posters ---------------------------------------------------------------------------------

    /** Disney+ publishes 544x306 images from a server that scales on request: ask for the card's size. */
    private String sharper(String uri) {
        if (uri.contains("bamgrid.com/") && uri.contains("/scale?")) {
            return uri.replaceAll("([?&])width=\\d+", "$1width=" + cardWidth())
                    .replaceAll("([?&])height=\\d+", "$1height=" + cardHeight());
        }
        return uri;
    }

    private void loadPoster(String raw, ImageView into) {
        loadPoster(raw, into, null);
    }

    /** As loadPoster, and then: the picture, once it is on the view. */
    private void loadPoster(String raw, ImageView into, java.util.function.Consumer<Bitmap> then) {
        if (raw == null || raw.isEmpty()) return;
        String uri = sharper(raw);
        Bitmap hit = posters.get(uri);
        if (hit != null) { into.setImageBitmap(hit); if (then != null) then.accept(hit); return; }
        into.setTag(uri);
        int target = cardWidth();
        images.execute(() -> {
            // YouTube's usual thumbnail is 4:3 with the video letterboxed in it: its widescreen one first.
            Bitmap bmp = null;
            java.util.regex.Matcher yt = java.util.regex.Pattern.compile("(https://i\\d?\\.ytimg\\.com/vi(?:_webp)?/[^/]+/)[a-z0-9_]+\\.(?:jpg|webp)").matcher(uri);
            if (yt.lookingAt()) bmp = fetch(yt.group(1) + "maxresdefault.jpg", target);
            if (bmp == null) bmp = fetch(uri, target);
            bmp = unletterbox(bmp);
            final Bitmap shown = bmp;
            if (shown == null) return;
            main.post(() -> {
                posters.put(uri, shown);
                if (uri.equals(into.getTag())) {
                    into.setImageBitmap(shown);
                    if (then != null) then.accept(shown);
                    into.setAlpha(0f);
                    into.animate().alpha(1f).setDuration(200).start();
                }
            });
        });
    }

    /**
     * A picture with black bars baked in above and below (a 4:3 thumbnail of a widescreen video) cut to what is
     * between them, so it fills the card; any other picture as it is.
     */
    private static Bitmap unletterbox(Bitmap b) {
        if (b == null || b.getHeight() < 40 || b.getWidth() < b.getHeight()) return b;
        int w = b.getWidth(), h = b.getHeight();
        int top = 0, bottom = h - 1;
        while (top < h / 4 && dark(b, top, w)) top++;
        while (bottom > h * 3 / 4 && dark(b, bottom, w)) bottom--;
        int cut = bottom - top + 1;
        // Bars at both ends, as a letterbox has; a dark sky or floor on its own stays.
        if (top < h / 20 || h - 1 - bottom < h / 20 || cut < h / 2) return b;
        return Bitmap.createBitmap(b, 0, top, w, cut);
    }

    /** A row that is all but black: a few points along it, none brighter than a dim grey. */
    private static boolean dark(Bitmap b, int y, int w) {
        for (int i = 1; i < 16; i++) {
            int c = b.getPixel(w * i / 16, y);
            if (((c >> 16) & 0xFF) + ((c >> 8) & 0xFF) + (c & 0xFF) > 60) return false;
        }
        return true;
    }

    private Bitmap fetch(String uri, int targetWidth) {
        byte[] data;
        try {
            if (uri.startsWith("http")) {
                HttpURLConnection conn = (HttpURLConnection) new URL(uri).openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(8000);
                conn.setInstanceFollowRedirects(true);
                try (InputStream in = conn.getInputStream()) { data = readAll(in); }
                finally { conn.disconnect(); }
            } else {
                try (InputStream in = context.getContentResolver().openInputStream(Uri.parse(uri))) {
                    if (in == null) return null;
                    data = readAll(in);
                }
            }
        } catch (Exception e) {
            return null;
        }
        if (data == null) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        int sample = 1;
        while (o.outWidth / (sample * 2) >= targetWidth) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        d.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeByteArray(data, 0, data.length, d);
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n, total = 0;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > 3 * 1024 * 1024) return null;   // not a poster
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
