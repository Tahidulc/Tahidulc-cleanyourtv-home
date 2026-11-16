package com.gathin.tvhomephone.watch;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.gathin.tvhomephone.glass.BlurBehind;
import com.gathin.tvhomephone.glass.Glass;
import com.gathin.tvhomephone.glass.GlassDrawable;
import com.gathin.tvhomephone.glass.Icons;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.function.Consumer;

/**
 * The cover page's rows that scroll sideways: the trailers (each video's picture from YouTube under the glass rim, a
 * small frosted play mark on it and its name under it), More like this (posters under the glass rim) and the cast
 * (round faces with a fine rim, their names in white and their parts dim).
 */
final class CoverRows {
    private CoverRows() {}

    /** A row that scrolls sideways, starting in line with the page's words. */
    private static LinearLayout row(Context c, int side, HorizontalScrollView[] holder) {
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(side, 0, side - Glass.dp(c, 12), 0);
        hs.addView(row);
        holder[0] = hs;
        return row;
    }

    /** The trailers, as {YouTube key, name}: a tap opens one (open). */
    static View trailers(Context c, List<String[]> videos, int side, Consumer<String> open) {
        HorizontalScrollView[] hs = new HorizontalScrollView[1];
        LinearLayout row = row(c, side, hs);
        float r = Glass.dp(c, 14);
        for (String[] v : videos) {
            String key = v[0], name = v[1];
            LinearLayout one = new LinearLayout(c);
            one.setOrientation(LinearLayout.VERTICAL);
            FrameLayout pic = new FrameLayout(c);
            pic.setBackground(Glass.placeholder(key, r));
            Glass.round(pic, r);
            pic.setForeground(Glass.rim(c, 14));
            ImageView thumb = new ImageView(c);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            pic.addView(thumb, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            BlurBehind play = new BlurBehind(c, 22, GlassDrawable.CARD);
            play.setTint(0x5912131A);
            play.setSources(thumb);
            ImageView mark = new ImageView(c);
            mark.setImageDrawable(new Icons(c, Icons.PLAY, Glass.TEXT));
            mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            play.addView(mark, new FrameLayout.LayoutParams(Glass.dp(c, 18), Glass.dp(c, 18), Gravity.CENTER));
            pic.addView(play, new FrameLayout.LayoutParams(Glass.dp(c, 44), Glass.dp(c, 44), Gravity.CENTER));
            one.addView(pic, new LinearLayout.LayoutParams(Glass.dp(c, 240), Glass.dp(c, 135)));
            TextView words = Glass.text(c, name, 13, Glass.MUTED, Glass.body(c));
            words.setMaxLines(2);
            words.setEllipsize(TextUtils.TruncateAt.END);
            words.setLineSpacing(0, 1.15f);
            LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(Glass.dp(c, 240), LinearLayout.LayoutParams.WRAP_CONTENT);
            wp.topMargin = Glass.dp(c, 8);
            one.addView(words, wp);
            try {
                // The video's own picture: the only thing read from YouTube.
                Glide.with(thumb).load("https://i.ytimg.com/vi/" + key + "/hqdefault.jpg").dontTransform().into(thumb);
            } catch (RuntimeException ignored) {
                // the page went away
            }
            one.setClickable(true);
            one.setFocusable(true);
            one.setContentDescription(name + ", video");
            one.setOnClickListener(x -> open.accept(key));
            CoverViews.press(one, 0.97f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Glass.dp(c, 12);
            row.addView(one, lp);
        }
        return hs[0];
    }

    /** More like this: the titles' posters, 112 by 168; a tap opens one (open). */
    static View similar(Context c, List<Title> titles, int side, Consumer<Title> open) {
        HorizontalScrollView[] hs = new HorizontalScrollView[1];
        LinearLayout row = row(c, side, hs);
        for (Title t : titles) {
            ImageView p = Glass.poster(c, t.title, 14);
            p.setContentDescription(t.title);
            try {
                if (t.poster != null) Glide.with(p).load(t.poster).centerCrop().into(p);
            } catch (RuntimeException ignored) {
                // the page went away
            }
            p.setClickable(true);
            p.setFocusable(true);
            p.setOnClickListener(x -> open.accept(t));
            CoverViews.press(p, 0.96f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Glass.dp(c, 112), Glass.dp(c, 168));
            lp.rightMargin = Glass.dp(c, 12);
            row.addView(p, lp);
        }
        return hs[0];
    }

    /** The cast, the first fifteen: each a round face under a fine rim, the name in white and the part dim; null for none. */
    static View cast(Context c, JSONObject details, int side) {
        JSONObject credits = details == null ? null : details.optJSONObject("credits");
        JSONArray cast = credits == null ? null : credits.optJSONArray("cast");
        if (cast == null || cast.length() == 0) return null;
        HorizontalScrollView[] hs = new HorizontalScrollView[1];
        LinearLayout row = row(c, side, hs);
        row.setPadding(side - Glass.dp(c, 4), 0, side - Glass.dp(c, 4), 0);
        int size = Glass.dp(c, 76);
        int added = 0;
        for (int i = 0; i < cast.length() && added < 15; i++) {
            JSONObject p = cast.optJSONObject(i);
            if (p == null) continue;
            String who = p.optString("name", "").trim(), part = p.optString("character", "").trim();
            if (who.isEmpty()) continue;
            LinearLayout one = new LinearLayout(c);
            one.setOrientation(LinearLayout.VERTICAL);
            one.setGravity(Gravity.CENTER_HORIZONTAL);
            ImageView face = new ImageView(c);
            face.setScaleType(ImageView.ScaleType.CENTER_CROP);
            face.setBackground(Glass.placeholder(who, size / 2f));
            Glass.round(face, size / 2f);
            face.setForeground(Glass.rim(c, 38));
            face.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            String photo = Tmdb.image(p.optString("profile_path", null), "w185");
            try {
                if (photo != null) Glide.with(face).load(photo).centerCrop().into(face);
            } catch (RuntimeException ignored) {
                // the page went away
            }
            one.addView(face, new LinearLayout.LayoutParams(size, size));
            TextView n = Glass.text(c, who, 12, Glass.TEXT, Glass.bodySemi(c));
            n.setGravity(Gravity.CENTER_HORIZONTAL);
            n.setMaxLines(2);
            n.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(Glass.dp(c, 92), LinearLayout.LayoutParams.WRAP_CONTENT);
            np.topMargin = Glass.dp(c, 8);
            one.addView(n, np);
            if (!part.isEmpty()) {
                TextView r = Glass.text(c, part, 11, Glass.MUTED, Glass.body(c));
                r.setGravity(Gravity.CENTER_HORIZONTAL);
                r.setMaxLines(2);
                r.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(Glass.dp(c, 92), LinearLayout.LayoutParams.WRAP_CONTENT);
                rp.topMargin = Glass.dp(c, 3);
                one.addView(r, rp);
            }
            one.setContentDescription(part.isEmpty() ? who : who + " as " + part);
            one.setFocusable(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = Glass.dp(c, 4);
            lp.rightMargin = Glass.dp(c, 4);
            row.addView(one, lp);
            added++;
        }
        return added == 0 ? null : hs[0];
    }
}
