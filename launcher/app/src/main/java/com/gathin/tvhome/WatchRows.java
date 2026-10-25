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
    }

    private static final int MAX_CONTINUE = 8;
    private static final int MAX_RECOMMENDED = 12;
    private static final long CACHE_MS = 60_000L;

    static final class Item {
        String title, poster, intent;
        boolean continueWatching;
        float progress = -1f;
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
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        caption.setLetterSpacing(0.14f);
        caption.setAllCaps(true);
        caption.setSingleLine(true);
        panel.addView(caption, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        scroller = new HorizontalScrollView(context);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        scroller.setFillViewport(false);
        int pad = dp(8);
        scroller.setPadding(0, pad, pad * 2, pad);
        strip = new LinearLayout(context);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setClipChildren(false);
        strip.setClipToPadding(false);
        scroller.addView(strip);
        panel.addView(scroller, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        lp.leftMargin = dp(72);
        lp.rightMargin = dp(40);
        root.addView(panel, lp);
    }

    /** Height of the whole panel (caption, cards and their focus room), for placing it above the shelf. */
    int height() {
        return dp(12 + 4) + dp(8) * 2 + cardHeight();
    }

    boolean hasCards() {
        return panel.getVisibility() == View.VISIBLE && strip.getChildCount() > 0;
    }

    boolean hasFocus() {
        return panel.hasFocus();
    }

    /** Shows the rows for this dock app at y (root coordinates of the panel's top), reading them if needed. */
    void show(String packageName, String appLabel, float y) {
        panel.setY(y);
        if (packageName == null) { hide(); return; }
        if (packageName.equals(shownPackage)) {
            if (strip.getChildCount() > 0) panel.setVisibility(View.VISIBLE);
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
        panel.setVisibility(View.GONE);
        reader.execute(() -> {
            List<Item> items = read(packageName);
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
        panel.setVisibility(View.GONE);
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
                    if (ep != null && !ep.isEmpty() && !ep.equals(it.title)) it.title = it.title + " · " + ep;
                    it.poster = first(str(c, iThumb), str(c, iPoster));
                    it.intent = str(c, iIntent);
                    it.continueWatching = true;
                    long pos = iPos >= 0 ? c.getLong(iPos) : 0, dur = iDur >= 0 ? c.getLong(iDur) : 0;
                    if (pos > 0 && dur > 0) it.progress = Math.min(1f, pos / (float) dur);
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
                    it.poster = first(str(c, iThumb), str(c, iPoster));
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

    private int cardWidth() { return dp(150); }
    private int cardHeight() { return dp(84); }

    private void fill(List<Item> items) {
        strip.removeAllViews();
        if (items == null || items.isEmpty()) {
            panel.setVisibility(View.GONE);
            return;
        }
        for (int i = 0; i < items.size(); i++) strip.addView(card(items.get(i), i, items.size()));
        setCaption(items.get(0));
        panel.setVisibility(View.VISIBLE);
        panel.setAlpha(0f);
        panel.animate().alpha(1f).setDuration(220).start();
    }

    private void setCaption(Item item) {
        String section = context.getString(item.continueWatching ? R.string.continue_watching : R.string.recommended);
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
        ring.setStroke(dp(2), Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardWidth(), cardHeight());
        lp.rightMargin = dp(14);
        card.setLayoutParams(lp);

        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        View shade = new View(context);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x00000000, 0xD9000000}));
        card.addView(shade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(context);
        title.setText(item.title == null ? "" : item.title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        int p = dp(8);
        title.setPadding(p, 0, p, item.progress >= 0 ? dp(9) : dp(6));
        card.addView(title, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        if (item.progress >= 0) {
            FrameLayout track = new FrameLayout(context);
            track.setBackgroundColor(0x59FFFFFF);
            View bar = new View(context);
            bar.setBackgroundColor(Color.WHITE);
            track.addView(bar, new FrameLayout.LayoutParams(Math.max(dp(4),
                    Math.round((cardWidth() - 2 * p) * item.progress)), ViewGroup.LayoutParams.MATCH_PARENT));
            FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(cardWidth() - 2 * p, dp(3),
                    Gravity.BOTTOM | Gravity.START);
            tlp.leftMargin = p;
            tlp.bottomMargin = dp(5);
            card.addView(track, tlp);
        }

        card.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.08f : 1f).scaleY(focused ? 1.08f : 1f).setDuration(160).start();
            v.setForeground(focused ? ring : null);
            v.setElevation(focused ? dp(6) : 0);
            if (focused) setCaption(item);
        });
        card.setOnClickListener(v -> open(item));
        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) { host.onCardsUp(); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) { host.onCardsDown(); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return index == 0;
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return index == count - 1;
            return false;
        });
        loadPoster(item.poster, image);
        return card;
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

    private void loadPoster(String uri, ImageView into) {
        if (uri == null || uri.isEmpty()) return;
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
