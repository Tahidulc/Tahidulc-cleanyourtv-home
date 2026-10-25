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
     * Several feeds separated by spaces, the first preferred: up to half the stories from it (local news), the rest
     * from the others in turn, each story once.
     */
    static List<String[]> readAll(String feeds) {
        String[] urls = feeds.trim().split("\\s+");
        if (urls.length == 1) return read(urls[0]);
        List<String[]> out = new ArrayList<>();
        java.util.Set<String> links = new java.util.HashSet<>();
        for (int f = 0; f < urls.length; f++) {
            int cap = f == 0 ? STORIES / 2 : STORIES;
            int taken = 0;
            for (String[] st : read(urls[f])) {
                if (out.size() >= STORIES || taken >= cap) break;
                if (links.add(st[2] == null ? st[0] : st[2]) && links.add(st[0])) { out.add(st); taken++; }
            }
        }
        return out;
    }

    /** {headline, picture url, article link} */
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
                String title = null, image = null, link = null;
                boolean inItem = false;
                for (int e = p.getEventType(); e != XmlPullParser.END_DOCUMENT && out.size() < STORIES; e = p.next()) {
                    if (e == XmlPullParser.START_TAG) {
                        String n = p.getName();
                        if ("item".equals(n)) { inItem = true; title = null; image = null; link = null; }
                        else if (inItem && "title".equals(n)) title = p.nextText().trim();
                        else if (inItem && "link".equals(n)) link = p.nextText().trim();
                        else if (inItem && "thumbnail".equals(n)) image = p.getAttributeValue(null, "url");
                    } else if (e == XmlPullParser.END_TAG && "item".equals(p.getName())) {
                        inItem = false;
                        // The feed's pictures are 240 px wide; the same server has an 800 px one for a full card.
                        if (title != null && image != null) out.add(new String[]{title,
                                image.replace("/standard/240/", "/standard/800/"), link});
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
        TextView head = new TextView(ctx);
        head.setText("BBC NEWS");
        head.setTextColor(0xFFFF4D4D);
        head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setLetterSpacing(0.16f);
        panel.addView(head);
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
