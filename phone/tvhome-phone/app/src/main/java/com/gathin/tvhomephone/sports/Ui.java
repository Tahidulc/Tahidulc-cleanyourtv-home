package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.gathin.tvhomephone.R;

/** Small view helpers in the launcher's look, for Home and Sports: text, cards, crests and the glass rows. */
public final class Ui {
    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** A tablet (600 dp wide or more): two columns. */
    public static boolean wide(Context c) {
        return c.getResources().getConfiguration().screenWidthDp >= 600;
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return t;
    }

    /** A section's heading: small capitals in the section's colour. */
    public static TextView heading(Context c, String s, int color) {
        TextView t = text(c, s.toUpperCase(java.util.Locale.ROOT), 13, color, true);
        t.setLetterSpacing(0.12f);
        t.setPadding(0, dp(c, 18), 0, dp(c, 8));
        return t;
    }

    /** The glass rows the launcher's panels use: a faint white wash with a hairline. */
    public static GradientDrawable glass(Context c, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(c.getColor(R.color.glass));
        d.setCornerRadius(dp(c, radiusDp));
        d.setStroke(dp(c, 1), c.getColor(R.color.surface_line));
        return d;
    }

    public static GradientDrawable fill(Context c, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static GradientDrawable vertical(int top, int bottom) {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    /** A crest or logo from a URL, fitted in a square. */
    public static ImageView image(Context c, String url, int sizeDp) {
        ImageView v = new ImageView(c);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        load(v, url);
        return v;
    }

    public static void load(ImageView v, String url) {
        if (url == null || url.isEmpty()) return;
        try {
            Glide.with(v).load(url).into(v);
        } catch (RuntimeException ignored) {
            // the screen went away
        }
    }

    /** A picture from the first of these addresses that loads (then the next), telling which way it went. Main thread. */
    public static void load(ImageView v, String[] urls, Runnable ready, Runnable failed) {
        try {
            RequestManager glide = Glide.with(v);
            RequestBuilder<Drawable> chain = null;
            for (int i = urls == null ? -1 : urls.length - 1; i >= 0; i--) {
                if (urls[i] == null || urls[i].isEmpty()) continue;
                RequestBuilder<Drawable> b = glide.load(urls[i]).listener(told(ready, chain == null ? failed : null));
                chain = chain == null ? b : b.error(chain);
            }
            if (chain == null) {
                glide.clear(v);
                v.setImageDrawable(null);
                if (failed != null) failed.run();
                return;
            }
            chain.into(v);
        } catch (RuntimeException ignored) {
            // the screen went away
        }
    }

    public static void load(ImageView v, String url, Runnable ready, Runnable failed) {
        load(v, new String[]{url}, ready, failed);
    }

    private static RequestListener<Drawable> told(Runnable ready, Runnable failed) {
        return new RequestListener<Drawable>() {
            @Override public boolean onLoadFailed(GlideException e, Object model, @NonNull Target<Drawable> target, boolean first) {
                if (failed != null) failed.run();
                return false;
            }

            @Override public boolean onResourceReady(@NonNull Drawable d, @NonNull Object model, Target<Drawable> target,
                                                     @NonNull DataSource source, boolean first) {
                if (ready != null) ready.run();
                return false;
            }
        };
    }

    /** No picture, and none still coming (a recycled row). */
    public static void clear(ImageView v) {
        try {
            Glide.with(v).clear(v);
        } catch (RuntimeException ignored) {
            // the screen went away
        }
        v.setImageDrawable(null);
    }

    /**
     * A competition's heading: its logo (for dark grounds), then its name in the section's small capitals. The logo
     * goes when none loads.
     */
    public static LinearLayout compHeading(Context c, String name, String logo, int color) {
        LinearLayout r = row(c);
        r.setPadding(0, dp(c, 16), 0, dp(c, 8));
        if (logo != null && !logo.isEmpty()) {
            ImageView v = new ImageView(c);
            v.setScaleType(ImageView.ScaleType.FIT_CENTER);
            v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, 22), dp(c, 22));
            lp.rightMargin = dp(c, 9);
            r.addView(v, lp);
            load(v, logo, null, () -> v.setVisibility(View.GONE));
        }
        TextView t = heading(c, name, color);
        t.setPadding(0, 0, 0, 0);
        r.addView(t, weight(1));
        return r;
    }

    public static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    public static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams margins(Context c, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = matchWrap();
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    /** A card: the glass row with padding, tappable when it has an action. */
    public static LinearLayout card(Context c, View.OnClickListener tap) {
        LinearLayout l = column(c);
        l.setBackground(glass(c, 14));
        int p = dp(c, 14);
        l.setPadding(p, p, p, p);
        if (tap != null) {
            l.setOnClickListener(tap);
            l.setClickable(true);
            l.setFocusable(true);
        }
        return l;
    }

    /**
     * "Live on" and the channel's logo (Sky's, the service's or the playlist's: ChannelLogos), its name written out when
     * no logo is known or it does not load; a link in the Sports green when the playlist has that channel.
     */
    public static View liveOn(Context c, String channel) {
        boolean link = channel != null && com.gathin.tvhomephone.live.LiveLinks.has(c, channel);
        int ink = link ? c.getColor(R.color.sports) : c.getColor(R.color.text_soft);
        LinearLayout r = row(c);
        r.setPadding(0, dp(c, 6), 0, dp(c, 6));
        r.addView(text(c, (link ? "▶  " : "") + "Live on", 14, ink, link));
        TextView name = text(c, " " + channel, 14, ink, link);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        r.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ImageView logo = new ImageView(c);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setMaxWidth(dp(c, 150));
        logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        logo.setVisibility(View.GONE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, 20));
        lp.leftMargin = dp(c, 8);
        r.addView(logo, lp);
        r.setContentDescription("Live on " + channel);
        if (link) r.setOnClickListener(v -> com.gathin.tvhomephone.live.LiveLinks.open(c, channel));
        String known = ChannelLogos.known(c, channel);
        if (known != null) {
            showLogo(logo, name, known);
        } else if (ChannelLogos.isService(channel)) {
            Context app = c.getApplicationContext();
            com.gathin.tvhomephone.App.io.execute(() -> {
                String found = ChannelLogos.find(app, channel);
                if (found != null) com.gathin.tvhomephone.App.main.post(() -> showLogo(logo, name, found));
            });
        }
        return r;
    }

    private static void showLogo(ImageView logo, TextView name, String url) {
        logo.setVisibility(View.VISIBLE);
        name.setVisibility(View.GONE);
        load(logo, url, null, () -> {
            logo.setVisibility(View.GONE);
            name.setVisibility(View.VISIBLE);
        });
    }

    public static void empty(LinearLayout into, String words) {
        Context c = into.getContext();
        TextView t = text(c, words, 15, c.getColor(R.color.text_dim), false);
        t.setPadding(0, dp(c, 12), 0, dp(c, 12));
        into.addView(t);
    }
}
