package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.util.Xml;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * BBC News beside a lone resume card: the top stories from the feed in config.xml news_panel_url, each with its
 * picture (asked for at 480 px instead of the feed's 240) and a two-line headline. Read off the main thread.
 */
final class NewsPanel {
    static final long EVERY_MS = 15 * 60_000L;
    static final int STORIES = 12;

    /**
     * Several feeds separated by spaces, taken in turn (the first, local news, leads): each story once, each with
     * its tag as a fourth field: BBC MANCHESTER, BBC SPORT or BBC NEWS.
     */
    static List<String[]> readAll(String feeds) {
        String[] urls = feeds.trim().split("\\s+");
        List<List<String[]>> lists = new ArrayList<>();
        for (String u : urls) lists.add(read(u));
        List<String[]> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; out.size() < STORIES; i++) {
            boolean any = false;
            for (int f = 0; f < urls.length && out.size() < STORIES; f++) {
                if (i >= lists.get(f).size()) continue;
                any = true;
                String[] st = lists.get(f).get(i);
                if (!seen.add(st[0])) continue;
                out.add(new String[]{st[0], st[1], st[2], tag(urls[f])});
            }
            if (!any) break;
        }
        return out;
    }

    static String tag(String feed) {
        return feed.contains("/sport/") ? "BBC SPORT" : feed.contains("/manchester/") ? "BBC MANCHESTER" : "BBC NEWS";
    }

    /** A news card's tag, whichever BBC section it is. */
    static boolean isTag(String s) {
        return s != null && s.startsWith("BBC ");
    }

    /** {headline, picture url, article link, published (RFC 822, or null)} */
    static List<String[]> read(String feed) {
        List<String[]> out = new ArrayList<>();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(feed).openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(8000);
            try (InputStream in = conn.getInputStream()) {
                XmlPullParser p = Xml.newPullParser();
                p.setInput(in, null);
                String title = null, image = null, link = null, date = null;
                boolean inItem = false;
                for (int e = p.getEventType(); e != XmlPullParser.END_DOCUMENT && out.size() < STORIES; e = p.next()) {
                    if (e == XmlPullParser.START_TAG) {
                        String n = p.getName();
                        if ("item".equals(n)) { inItem = true; title = null; image = null; link = null; date = null; }
                        else if (inItem && "pubDate".equals(n)) date = p.nextText().trim();
                        else if (inItem && "title".equals(n)) title = p.nextText().trim();
                        else if (inItem && "link".equals(n)) link = p.nextText().trim();
                        else if (inItem && "thumbnail".equals(n)) image = p.getAttributeValue(null, "url");
                    } else if (e == XmlPullParser.END_TAG && "item".equals(p.getName())) {
                        inItem = false;
                        // The feed's pictures are 240 px wide; the same server has an 800 px one for a full card.
                        if (title != null && image != null) out.add(new String[]{title,
                                image.replace("/standard/240/", "/standard/800/"), link, date});
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

    interface PosterLoader { void load(String url, ImageView into); }

    /** The panel: a red BBC NEWS line, then one row per story. */
    static View build(Context ctx, float density, List<String[]> stories, int width, int height, PosterLoader posters) {
        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.glass_panel);
        int pad = Math.round(16 * density);
        panel.setPadding(pad, Math.round(12 * density), pad, Math.round(12 * density));
        panel.setFocusable(false);
        // BBC News on brand: its blocks on BBC red.
        android.widget.ImageView head = new android.widget.ImageView(ctx);
        head.setImageDrawable(Badges.bbcChip(density, "NEWS"));
        head.setScaleType(android.widget.ImageView.ScaleType.FIT_START);
        head.setAdjustViewBounds(true);
        panel.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Math.round(22 * density)));
        int headH = Math.round(24 * density), gap = Math.round(10 * density);
        int rowH = Math.max(Math.round(40 * density), (height - 2 * Math.round(12 * density) - headH - gap * (STORIES - 1)) / STORIES);
        for (int i = 0; i < stories.size(); i++) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            ImageView pic = new ImageView(ctx);
            pic.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFF1C1C22);
            bg.setCornerRadius(8 * density);
            pic.setBackground(bg);
            pic.setClipToOutline(true);
            row.addView(pic, new LinearLayout.LayoutParams(Math.round(rowH * 16f / 9f), rowH));
            TextView title = new TextView(ctx);
            title.setText(stories.get(i)[0]);
            title.setTextColor(Color.WHITE);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            title.setTypeface(ctx.getResources().getFont(R.font.outfit_regular));
            title.setMaxLines(rowH > 70 * density ? 3 : 2);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tlp.leftMargin = Math.round(14 * density);
            row.addView(title, tlp);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowH);
            rlp.topMargin = i == 0 ? Math.round(8 * density) : gap;
            panel.addView(row, rlp);
            posters.load(stories.get(i)[1], pic);
        }
        panel.setLayoutParams(new LinearLayout.LayoutParams(width, height));
        return panel;
    }
}
