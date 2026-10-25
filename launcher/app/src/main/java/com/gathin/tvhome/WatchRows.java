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
        caption.setLetterSpacing(0.14f);
        caption.setAllCaps(true);
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
        panel.setY(y);
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

    void hide() {
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

    /** Card size in px, 16:9; fit() grows it to fill the space between the header and the dock. */
    private int cardH = -1;
    private int cardWidth() { return Math.round(cardHeight() * 16f / 9f); }
    private int cardHeight() { return cardH > 0 ? cardH : dp(135); }

    /**
     * Sizes the cards to fill from {@code top} (just under the header) to {@code bottom} (just above the dock),
     * and returns where the panel's top goes. Cards already on screen are rebuilt when the size changes.
     */
    float fit(float top, float bottom) {
        int chrome = height() - cardHeight();
        int h = Math.max(dp(110), Math.round(bottom - top) - chrome);
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
        strip.removeAllViews();
        if (items == null || items.isEmpty()) {
            // An app that publishes nothing (TiviMate, Debrid Stream): BBC News across the whole row instead.
            if (!showNews()) setShown(false);
            return;
        }
        for (int i = 0; i < items.size(); i++) strip.addView(card(items.get(i), i, items.size()));

        setCaption(items.get(0));
        setShown(true);
        panel.setAlpha(0f);
        panel.animate().alpha(1f).setDuration(220).start();
    }

    /** The news row: from the cache when fresh, else read and shown when it arrives. False when there is none yet. */
    private boolean showNews() {
        String feed = context.getString(R.string.news_panel_url);
        if (feed.isEmpty()) return false;
        if (System.currentTimeMillis() - newsAt >= NewsPanel.EVERY_MS) {
            newsAt = System.currentTimeMillis();
            final int gen = generation;
            reader.execute(() -> {
                List<String[]> fresh = NewsPanel.readAll(feed);
                main.post(() -> {
                    if (fresh.isEmpty()) { newsAt = 0; return; }
                    news = fresh;
                    if (gen == generation && strip.getChildCount() == 0) fillNews();
                });
            });
        }
        if (news.isEmpty()) return false;
        fillNews();
        return true;
    }

    private void fillNews() {
        strip.removeAllViews();
        for (int i = 0; i < news.size(); i++) strip.addView(newsCard(news.get(i), i, news.size()));
        setShown(true);
        panel.setAlpha(0f);
        panel.animate().alpha(1f).setDuration(220).start();
    }

    /** One story: its picture across the card, a BBC NEWS tag, the headline at the bottom. OK opens it. */
    private View newsCard(String[] story, int index, int count) {
        Item item = new Item();
        item.title = story[0];
        item.poster = story[1];
        item.intent = story[2];
        item.subtitle = "BBC NEWS";
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
        card.setFocusable(true);
        card.setClipToOutline(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1C1C22);
        bg.setCornerRadius(dp(10));
        card.setBackground(bg);
        GradientDrawable ring = new GradientDrawable();
        ring.setColor(Color.TRANSPARENT);
        ring.setCornerRadius(dp(10));
        ring.setStroke(dp(3), Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardWidth(), cardHeight());
        lp.rightMargin = dp(14);
        card.setLayoutParams(lp);

        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        View shade = new View(context);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x00000000, 0x66000000, 0xE6000000}));
        card.addView(shade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // Title, a quieter second line and the progress pill, stacked at the bottom-left.
        LinearLayout text = new LinearLayout(context);
        text.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        text.setPadding(p, 0, p, dp(14));
        boolean big = cardHeight() > dp(170);
        TextView title = new TextView(context);
        title.setText(item.art != null ? appLabel(item) : item.title == null ? "" : item.title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, big ? 19f : 14f);
        title.setTypeface(context.getResources().getFont(R.font.outfit_medium));
        title.setLetterSpacing(-0.01f);
        if ("BBC NEWS".equals(item.subtitle)) title.setMaxLines(2); else title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setShadowLayer(dp(6), 0, dp(1), 0x99000000);
        text.addView(title);
        String second = secondLine(item);
        if (second != null) {
            TextView sub = new TextView(context);
            sub.setText(second);
            boolean newsTag = "BBC NEWS".equals(second);
            sub.setTextColor(newsTag ? 0xFFFF4D4D : 0xBFFFFFFF);
            if (newsTag) { sub.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); sub.setLetterSpacing(0.14f); }
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, big ? 13f : 11f);
            sub.setTypeface(context.getResources().getFont(R.font.outfit_regular));
            sub.setSingleLine(true);
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            sub.setShadowLayer(dp(5), 0, dp(1), 0x99000000);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.topMargin = dp(2);
            text.addView(sub, slp);
        }
        if (item.progress >= 0) {
            // A slim rounded pill: a frosted track with a white fill that has round ends too.
            int trackW = cardWidth() - 2 * p;
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
            track.addView(fill, new FrameLayout.LayoutParams(Math.max(dp(6), Math.round(trackW * item.progress)),
                    ViewGroup.LayoutParams.MATCH_PARENT));
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(trackW, dp(5));
            tlp.topMargin = dp(10);
            text.addView(track, tlp);
        }
        card.addView(text, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        card.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? FOCUS_SCALE : 1f).scaleY(focused ? FOCUS_SCALE : 1f).setDuration(160).start();
            v.setForeground(focused ? ring : null);
            v.setElevation(focused ? dp(6) : 0);
        });
        card.setOnClickListener(v -> {
            if (item.art != null) host.onResumeApp();
            else open(item);
        });
        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) { host.onCardsUp(); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) { host.onCardsDown(); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return index == 0;
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return index == count - 1;
            return false;
        });
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
        return card;
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
        if (item.art != null || "BBC NEWS".equals(item.subtitle)) return item.subtitle;
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
        if (raw == null || raw.isEmpty()) return;
        String uri = sharper(raw);
        Bitmap hit = posters.get(uri);
        if (hit != null) { into.setImageBitmap(hit); return; }
        into.setTag(uri);
        int target = cardWidth();
        images.execute(() -> {
            Bitmap bmp = fetch(uri, target);
            if (bmp == null) return;
            main.post(() -> {
                posters.put(uri, bmp);
                if (uri.equals(into.getTag())) {
                    into.setImageBitmap(bmp);
                    into.setAlpha(0f);
                    into.animate().alpha(1f).setDuration(200).start();
                }
            });
        });
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
