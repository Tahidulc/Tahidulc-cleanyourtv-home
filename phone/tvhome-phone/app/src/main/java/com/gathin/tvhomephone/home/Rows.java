package com.gathin.tvhomephone.home;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.gathin.tvhomephone.glass.Glass;
import com.gathin.tvhomephone.glass.GlassDrawable;
import com.gathin.tvhomephone.glass.PosterCard;
import com.gathin.tvhomephone.glass.WatchCard;
import com.gathin.tvhomephone.live.BreakingNews.Story;
import com.gathin.tvhomephone.live.SourceQuality;
import com.gathin.tvhomephone.watch.Logos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Home's rows, in glass: posters as the family's reference picture draws them (PosterCard: the logo over the poster's own
 * colour, where it stands today, what the sources have of it, the title and year under it), Continue watching's landscape cards
 * (the picture, the title's logo, how far in and the time still to go) and the headlines' frosted cards (the picture
 * on top, then the source, how long ago and the headline). Every card has its size before anything loads, and shows
 * a shimmer while its row is still coming, so nothing moves when the pictures arrive. Pictures are read at the size
 * they are shown, from Glide's cache when it has them.
 */
final class Rows {
    private Rows() {}

    /** Shimmer cards a row shows while it loads. */
    static final int LOADING = 6;
    /** The cards are 12dp apart (a poster's width follows the screen: PosterCard.rowWidth); a poster's corners while it loads. */
    static final int POSTER_R = 12, GAP = 12;
    /** Continue watching's card (dp), and a headline card's corners. */
    static final int WATCH_W = 250, WATCH_H = 140, STORY_R = 18;

    interface OnPick { void on(Pick p); }

    interface OnCard { void on(Resume.Card c); }

    interface OnStory { void on(Story s); }

    static int newsDp(Context c) {
        return c.getResources().getConfiguration().screenWidthDp >= 600 ? 300 : 268;
    }

    /** A horizontal row for an adapter, its first card in line with Home's others, no scrollbar. */
    static RecyclerView row(Context c, RecyclerView.Adapter<?> adapter) {
        RecyclerView r = new RecyclerView(c);
        r.setLayoutManager(new LinearLayoutManager(c, RecyclerView.HORIZONTAL, false));
        int side = Glass.dp(c, 16);
        r.setPadding(side, 0, side - Glass.dp(c, GAP), 0);
        r.setClipToPadding(false);
        r.setNestedScrollingEnabled(false);
        r.setOverScrollMode(View.OVER_SCROLL_NEVER);
        r.setHorizontalScrollBarEnabled(false);
        r.setItemAnimator(null);
        r.setHasFixedSize(true);
        r.setAdapter(adapter);
        return r;
    }

    static void load(ImageView v, String url, int w, int h) {
        if (url == null || url.isEmpty()) {
            clear(v);
            return;
        }
        try {
            Glide.with(v).load(url).override(w, h).centerCrop().format(DecodeFormat.PREFER_RGB_565)
                    .transition(DrawableTransitionOptions.withCrossFade(180)).into(v);
        } catch (RuntimeException ignored) {
            // the screen went away
        }
    }

    static void clear(ImageView v) {
        try {
            Glide.with(v).clear(v);
        } catch (RuntimeException ignored) {
            // the screen went away
        }
        v.setImageDrawable(null);
    }

    static TextView text(Context c, float sp, int color, android.graphics.Typeface face, int lines) {
        TextView t = new TextView(c);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(face);
        t.setIncludeFontPadding(false);
        if (lines > 0) {
            t.setMaxLines(lines);
            t.setEllipsize(TextUtils.TruncateAt.END);
        }
        return t;
    }

    // ---- posters ------------------------------------------------------------------------------------------------

    /** One poster: PosterCard, its logo read after it shows when Home's picks didn't bring it. */
    static final class PosterHolder extends RecyclerView.ViewHolder {
        final PosterCard card;

        PosterHolder(Context c, int w, boolean wide) {
            super(new PosterCard(c, w, true, wide));
            card = (PosterCard) itemView;
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Glass.dp(c, GAP);
            card.setLayoutParams(lp);
        }

        void loading(Context c) {
            itemView.setOnClickListener(null);
            itemView.setClickable(false);
            itemView.setContentDescription(null);
            itemView.setTag(null);
            card.loading(new Art.Shimmer(Glass.dp(c, POSTER_R)));
        }
    }

    /** Pure: what a pick's poster shows. */
    static PosterCard.Info info(Pick p) {
        PosterCard.Info i = new PosterCard.Info();
        i.title = p.title;
        i.year = p.year;
        i.poster = p.poster;
        i.backdrop = PosterCard.cardSize(p.backdrop);
        String logo = p.logo != null ? p.logo : Logos.known(p.tv, p.id);
        i.logo = logo;
        i.logoKnown = logo != null || p.id <= 0;
        i.line = PosterCard.line(p.genre, p.vote);
        i.badge = p.badge == null || p.badge.isEmpty() ? null : p.badge;
        i.quality = p.quality;
        return i;
    }

    /** Pure: what a screen reader says for a pick's poster ("Dune, 2021, #2 Today, 4K"). */
    static String spoken(Pick p) {
        StringBuilder b = new StringBuilder(p.title);
        if (!p.year.isEmpty()) b.append(", ").append(p.year);
        if (p.badge != null && !p.badge.isEmpty()) b.append(", ").append(p.badge);
        String q = SourceQuality.spoken(p.quality);
        if (!q.isEmpty()) b.append(", ").append(q);
        return b.toString();
    }

    /** Films and TV from the playlists, or My list. Null until loaded: shimmer cards. */
    static final class Posters extends RecyclerView.Adapter<PosterHolder> {
        private List<Pick> items;
        private final OnPick tap;
        private final int w;
        /** Landscape cards (Settings, "Posters"), as the row was made. */
        private final boolean wide;

        Posters(Context c, OnPick tap) {
            this.tap = tap;
            wide = PosterCard.wide(c);
            w = PosterCard.rowWidth(c, wide);
        }

        void set(List<Pick> list) {
            items = list == null ? null : new ArrayList<>(list);
            notifyDataSetChanged();
        }

        @Override public int getItemCount() {
            return items == null ? LOADING : items.size();
        }

        @NonNull @Override public PosterHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new PosterHolder(parent.getContext(), w, wide);
        }

        @Override public void onBindViewHolder(@NonNull PosterHolder holder, int i) {
            Context c = holder.itemView.getContext();
            if (items == null) {
                holder.loading(c);
                return;
            }
            Pick p = items.get(i);
            PosterCard.Info info = info(p);
            holder.card.bind(info);
            String key = (p.tv ? "tv:" : "mv:") + p.id + ":" + p.title;
            holder.itemView.setTag(key);
            if (!info.logoKnown) {
                Logos.ask(c, p.tv, p.id, logo -> {
                    if (key.equals(holder.itemView.getTag())) holder.card.setLogo(logo);
                });
            }
            holder.itemView.setContentDescription(spoken(p));
            holder.itemView.setOnClickListener(v -> tap.on(p));
        }
    }

    // ---- Continue watching ----------------------------------------------------------------------------------------

    /** Continue watching's card, and a small chip on it for a title part-way on a TV. */
    static final class WatchHolder extends RecyclerView.ViewHolder {
        final WatchCard card;
        final TextView chip;

        WatchHolder(Context c) {
            super(new WatchCard(c));
            card = (WatchCard) itemView;
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(Glass.dp(c, WATCH_W), Glass.dp(c, WATCH_H));
            lp.rightMargin = Glass.dp(c, GAP);
            card.setLayoutParams(lp);
            chip = Glass.text(c, "ON TV", 10, Glass.TEXT, Glass.bodySemi(c));
            chip.setLetterSpacing(0.08f);
            chip.setPadding(Glass.dp(c, 7), Glass.dp(c, 3), Glass.dp(c, 7), Glass.dp(c, 3));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0x8C0E0F14);
            bg.setCornerRadius(Glass.dp(c, 7));
            bg.setStroke(Math.max(1, Glass.dp(c, 0.5f)), 0x33FFFFFF);
            chip.setBackground(bg);
            chip.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
            cp.setMargins(Glass.dp(c, 10), Glass.dp(c, 10), 0, 0);
            card.addView(chip, cp);
        }
    }

    /**
     * Continue watching: this phone's films and episodes part-way, then the TVs', each over its backdrop with its logo
     * (read after the cards show; the poster until then), how far in and its line ("S2 E4 · 32m left").
     */
    static final class Watching extends RecyclerView.Adapter<WatchHolder> {
        private final List<Resume.Card> items = new ArrayList<>();
        private final Map<String, Pick> art = new HashMap<>();
        private final OnCard tap, hold;

        Watching(OnCard tap, OnCard hold) {
            this.tap = tap;
            this.hold = hold;
        }

        /** The cards, with the backdrops and logos read so far (by each card's key). */
        void set(List<Resume.Card> cards, Map<String, Pick> arts) {
            items.clear();
            if (cards != null) items.addAll(cards);
            art.clear();
            if (arts != null) art.putAll(arts);
            notifyDataSetChanged();
        }

        /** One card's backdrop and logo, read after it showed. */
        void art(String key, Pick p) {
            art.put(key, p);
            for (int i = 0; i < items.size(); i++) if (items.get(i).key().equals(key)) notifyItemChanged(i);
        }

        /** A card's backdrop and logo, when read. */
        Pick art(Resume.Card k) {
            return k == null ? null : art.get(k.key());
        }

        @Override public int getItemCount() {
            return items.size();
        }

        @NonNull @Override public WatchHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new WatchHolder(parent.getContext());
        }

        @Override public void onBindViewHolder(@NonNull WatchHolder h, int i) {
            Resume.Card k = items.get(i);
            Pick a = art.get(k.key());
            String image = a != null && a.backdrop != null ? a.backdrop : k.poster;
            boolean onTv = k.tvTitle != null;
            String left = !k.line.isEmpty() ? k.line : onTv ? "On your TV" : "";
            h.card.set(k.title, image, a == null ? null : a.logo, Math.max(0f, k.progress), left);
            h.chip.setVisibility(onTv && !k.line.isEmpty() ? View.VISIBLE : View.GONE);
            h.card.setContentDescription("Resume " + k.title + (left.isEmpty() ? "" : ", " + left)
                    + (onTv && !k.line.isEmpty() ? ", on your TV" : ""));
            h.card.setOnClickListener(v -> tap.on(k));
            h.card.setOnLongClickListener(hold == null || k.entry == null ? null : v -> {
                hold.on(k);
                return true;
            });
        }
    }

    // ---- headlines ----------------------------------------------------------------------------------------------

    /**
     * A headline's frosted card: its picture on top with its source's mark on its corner (BBC News's or BBC Sport's), then
     * how long ago, then the headline.
     */
    static final class StoryHolder extends RecyclerView.ViewHolder {
        final ImageView picture, mark;
        final FrameLayout frame;
        final TextView kicker, headline;
        final GlassDrawable glass, rim;

        StoryHolder(Context c, int w) {
            super(new LinearLayout(c));
            LinearLayout card = (LinearLayout) itemView;
            card.setOrientation(LinearLayout.VERTICAL);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Glass.dp(c, GAP);
            card.setLayoutParams(lp);
            Glass.round(card, Glass.dp(c, STORY_R));
            glass = Glass.card(c, STORY_R);
            rim = Glass.rim(c, STORY_R);
            frame = new FrameLayout(c);
            picture = new ImageView(c);
            picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
            picture.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            frame.addView(picture, new FrameLayout.LayoutParams(-1, -1));
            mark = new ImageView(c);
            mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(-2, Glass.dp(c, 20), android.view.Gravity.TOP | android.view.Gravity.START);
            mp.leftMargin = mp.topMargin = Glass.dp(c, 10);
            mark.setAdjustViewBounds(true);
            frame.addView(mark, mp);
            card.addView(frame, new LinearLayout.LayoutParams(w, w * 9 / 16));
            LinearLayout words = new LinearLayout(c);
            words.setOrientation(LinearLayout.VERTICAL);
            int pad = Glass.dp(c, 14);
            words.setPadding(pad, Glass.dp(c, 12), pad, pad);
            kicker = text(c, 11, Art.NEWS, Type.interSemi(c), 1);
            kicker.setLetterSpacing(0.04f);
            words.addView(kicker);
            headline = text(c, 15, Glass.TEXT, Type.interSemi(c), 3);
            // Three lines kept whatever the headline's length: every card the same height.
            headline.setLines(3);
            headline.setLineSpacing(Glass.dp(c, 2), 1f);
            LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
            hl.topMargin = Glass.dp(c, 6);
            words.addView(headline, hl);
            card.addView(words, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    /** One feed's stories: BBC News's (accent amber) or BBC Sport's (green). Null until loaded: shimmer cards. */
    static final class Stories extends RecyclerView.Adapter<StoryHolder> {
        private List<Story> items;
        private final String source;
        private final int accent, w;
        private final OnStory tap;

        Stories(Context c, String source, int accent, OnStory tap) {
            this.source = source;
            this.accent = accent;
            this.tap = tap;
            w = Glass.dp(c, newsDp(c));
        }

        void set(List<Story> list) {
            items = list == null ? null : new ArrayList<>(list);
            notifyDataSetChanged();
        }

        @Override public int getItemCount() {
            return items == null ? 3 : items.size();
        }

        @NonNull @Override public StoryHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new StoryHolder(parent.getContext(), w);
        }

        @Override public void onBindViewHolder(@NonNull StoryHolder h, int i) {
            Context c = h.itemView.getContext();
            if (items == null) {
                h.itemView.setBackground(new Art.Shimmer(Glass.dp(c, STORY_R)));
                h.itemView.setForeground(null);
                h.frame.setBackground(null);
                clear(h.picture);
                h.mark.setImageDrawable(null);
                h.kicker.setText(" ");
                h.headline.setText("");
                h.itemView.setContentDescription(null);
                h.itemView.setOnClickListener(null);
                h.itemView.setClickable(false);
                return;
            }
            Story s = items.get(i);
            h.itemView.setBackground(h.glass);
            h.itemView.setForeground(h.rim);
            h.frame.setBackground(Glass.placeholder(s.title, 0));
            load(h.picture, s.picture.isEmpty() ? null : s.picture, w, w * 9 / 16);
            String ago = Headlines.ago(s.at, System.currentTimeMillis());
            // The source's own mark on the picture (BBC News's, BBC Sport's); the line under it says how long ago.
            com.gathin.tvhomephone.glass.BbcMark bbc = com.gathin.tvhomephone.glass.BbcMark.of(c, source, 20);
            h.mark.setImageDrawable(bbc);
            h.kicker.setTextColor(accent);
            String when = ago.isEmpty() ? "Latest" : ago;
            h.kicker.setText(bbc != null ? when.toUpperCase(java.util.Locale.UK)
                    : source.toUpperCase(java.util.Locale.UK) + (ago.isEmpty() ? "" : "  ·  " + ago));
            h.headline.setText(s.title);
            h.itemView.setContentDescription(s.title + ", " + source + (ago.isEmpty() ? "" : ", " + ago));
            h.itemView.setOnClickListener(v -> tap.on(s));
        }
    }
}
