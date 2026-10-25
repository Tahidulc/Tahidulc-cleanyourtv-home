package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.util.Xml;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Headlines scrolling along the top edge of Home, from an RSS feed (config.xml news_feed_url, BBC News by default).
 * Read when Home comes up and every 15 minutes while it is visible; the marquee only runs while Home is in front.
 */
final class NewsTicker {
    private static final long EVERY_MS = 15 * 60_000L;
    private static final long RETRY_MS = 5 * 60_000L;
    private static final int MAX_HEADLINES = 12;

    private final TextView view;
    private final String feedUrl, source;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private long fetchedAt;
    private boolean running;
    private final Runnable tick = this::fetch;

    NewsTicker(Context context, FrameLayout root, float density, String feedUrl, String source) {
        this.feedUrl = feedUrl;
        this.source = source;
        view = new TextView(context);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        view.setMarqueeRepeatLimit(-1);
        view.setHorizontallyScrolling(true);
        view.setFocusable(false);
        view.setTextColor(0xCCFFFFFF);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setVisibility(View.GONE);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.round(24 * density), Gravity.TOP);
        lp.leftMargin = Math.round(62 * density);
        lp.rightMargin = Math.round(56 * density);
        lp.topMargin = Math.round(4 * density);
        root.addView(view, lp);
    }

    void start() {
        if (feedUrl.isEmpty()) return;
        running = true;
        view.setSelected(true);
        main.removeCallbacks(tick);
        long age = System.currentTimeMillis() - fetchedAt;
        if (age >= EVERY_MS) fetch();
        else main.postDelayed(tick, EVERY_MS - age);
    }

    void stop() {
        running = false;
        view.setSelected(false);   // stops the marquee, so nothing redraws behind an app
        main.removeCallbacks(tick);
    }

    void close() {
        stop();
        io.shutdownNow();
    }

    private void fetch() {
        io.execute(() -> {
            List<String> headlines = read();
            main.post(() -> {
                if (!headlines.isEmpty()) {
                    fetchedAt = System.currentTimeMillis();
                    show(headlines);
                }
                if (running) main.postDelayed(tick, headlines.isEmpty() ? RETRY_MS : EVERY_MS);
            });
        });
    }

    private void show(List<String> headlines) {
        SpannableStringBuilder line = new SpannableStringBuilder();
        if (!source.isEmpty()) {
            line.append(source.toUpperCase());
            line.setSpan(new StyleSpan(Typeface.BOLD), 0, line.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            line.setSpan(new ForegroundColorSpan(0xFFFF4D4D), 0, line.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            line.append("     ");
        }
        for (int i = 0; i < headlines.size(); i++) {
            if (i > 0) {
                int at = line.length();
                line.append("     •     ");
                line.setSpan(new ForegroundColorSpan(0x80FFFFFF), at, line.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            line.append(headlines.get(i));
        }
        view.setText(line);
        view.setVisibility(View.VISIBLE);
        view.setSelected(running);
    }

    /** The first MAX_HEADLINES item titles of the feed; empty on any failure. */
    private List<String> read() {
        List<String> out = new ArrayList<>();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(feedUrl).openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(8000);
            try (InputStream in = conn.getInputStream()) {
                XmlPullParser p = Xml.newPullParser();
                p.setInput(in, null);
                boolean inItem = false;
                for (int e = p.getEventType(); e != XmlPullParser.END_DOCUMENT && out.size() < MAX_HEADLINES; e = p.next()) {
                    if (e == XmlPullParser.START_TAG) {
                        if ("item".equals(p.getName()) || "entry".equals(p.getName())) inItem = true;
                        else if (inItem && "title".equals(p.getName())) {
                            String t = p.nextText().trim();
                            if (!t.isEmpty()) out.add(t);
                        }
                    } else if (e == XmlPullParser.END_TAG && ("item".equals(p.getName()) || "entry".equals(p.getName()))) {
                        inItem = false;
                    }
                }
            }
        } catch (Exception ignored) {
            out.clear();
        } finally {
            if (conn != null) conn.disconnect();
        }
        return out;
    }
}
