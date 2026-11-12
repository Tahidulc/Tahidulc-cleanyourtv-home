package com.gathin.tvhomephone.home;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.gathin.tvhomephone.live.BreakingNews.Story;

import java.util.ArrayList;
import java.util.List;

/**
 * Home's rows: posters (films and TV picked from the playlists), Continue watching (posters with how far in, the
 * episode and a play button) and the headlines (a picture, the source, how long ago, the headline). Every card has
 * its size before anything loads, and shows a shimmer while its row is still coming, so nothing moves when the
 * pictures arrive. Pictures are read at the size they are shown, from Glide's cache when it has them.
 */
final class Rows {
    private Rows() {}

    /** Shimmer cards a row shows while it loads. */
    static final int LOADING = 6;

    interface OnPick { void on(Pick p); }

    interface OnCard { void on(Resume.Card c); }

    interface OnStory { void on(Story s); }

    /** A poster's width (dp): a little larger on a wide screen or sideways. */
    static int posterDp(Context c) {
        return c.getResources().getConfiguration().screenWidthDp >= 600 ? 128 : 112;
    }

    static int newsDp(Context c) {
        return c.getResources().getConfiguration().screenWidthDp >= 600 ? 300 : 268;
    }

    /** A horizontal row for an adapter, its first card in line with Home's headings. */
    static RecyclerView row(Context c, RecyclerView.Adapter<?> adapter) {
        RecyclerView r = new RecyclerView(c);
        r.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(c, RecyclerView.HORIZONTAL, false));
        int side = Art.dp(c, 16);
        r.setPadding(side, 0, side - Art.dp(c, 10), 0);
        r.setClipToPadding(false);
        r.setNestedScrollingEnabled(false);
        r.setOverScrollMode(View.OVER_SCROLL_NEVER);
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

    /** One poster card: the picture (or the title's own gradient with its name), its title and a line under it. */
    static final class PosterHolder extends RecyclerView.ViewHolder {
        final FrameLayout card;
        final ImageView picture;
        final TextView name, title, sub, badge;
        final View track, bar, play;

        PosterHolder(Context c, int w, int h) {
            super(new LinearLayout(c));
            LinearLayout col = (LinearLayout) itemView;
            col.setOrientation(LinearLayout.VERTICAL);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Art.dp(c, 10);
            col.setLayoutParams(lp);
            card = new FrameLayout(c);
            float r = Art.dp(c, 12);
            Art.clip(card, r);
            card.setElevation(Art.dp(c, 4));
            picture = new ImageView(c);
            picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
            picture.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            card.addView(picture, new FrameLayout.LayoutParams(-1, -1));
            name = text(c, 16, Art.TEXT, Type.outfit(c, 700), 3);
            int pad = Art.dp(c, 10);
            name.setPadding(pad, pad, pad, pad);
            name.setGravity(Gravity.BOTTOM | Gravity.START);
            card.addView(name, new FrameLayout.LayoutParams(-1, -1));
            // How far in (Continue watching): a bar along the foot.
            track = new View(c);
            track.setBackgroundColor(0x55FFFFFF);
            FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-1, Art.dp(c, 4), Gravity.BOTTOM);
            card.addView(track, tp);
            bar = new View(c);
            card.addView(bar, new FrameLayout.LayoutParams(0, Art.dp(c, 4), Gravity.BOTTOM | Gravity.START));
            play = new View(c);
            play.setBackground(Art.round(0x66080A1E, Art.dp(c, 21)));
            play.setForeground(new android.graphics.drawable.InsetDrawable(new Art.Glyph(Art.Glyph.PLAY, Art.TEXT, Art.dp(c, 16)),
                    Art.dp(c, 13)));
            card.addView(play, new FrameLayout.LayoutParams(Art.dp(c, 42), Art.dp(c, 42), Gravity.CENTER));
            badge = text(c, 10, Art.TEXT, Type.interSemi(c), 1);
            badge.setLetterSpacing(0.06f);
            badge.setBackground(Art.round(0xD9080A1E, Art.dp(c, 6)));
            badge.setPadding(Art.dp(c, 6), Art.dp(c, 3), Art.dp(c, 6), Art.dp(c, 3));
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
            bp.setMargins(Art.dp(c, 6), Art.dp(c, 6), 0, 0);
            card.addView(badge, bp);
            col.addView(card, new LinearLayout.LayoutParams(w, h));
            title = text(c, 13, Art.SOFT, Type.interSemi(c), 1);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
            tl.topMargin = Art.dp(c, 8);
            col.addView(title, tl);
            sub = text(c, 11, Art.DIM, Type.inter(c), 1);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
            sl.topMargin = Art.dp(c, 3);
            col.addView(sub, sl);
            // Room for both lines, filled or not, so every card is the same height.
            title.setText(" ");
            sub.setText(" ");
        }

        void loading(Context c) {
            itemView.setOnClickListener(null);
            itemView.setOnLongClickListener(null);
            itemView.setClickable(false);
            Rows.clear(picture);
            card.setBackground(new Art.Shimmer(Art.dp(c, 12)));
            name.setText("");
            title.setText(" ");
            sub.setText(" ");
            track.setVisibility(View.GONE);
            bar.setVisibility(View.GONE);
            play.setVisibility(View.GONE);
            badge.setVisibility(View.GONE);
        }

        /** The picture, else the title's own gradient with its name on it. */
        void show(Context c, String url, String words, int w, int h) {
            card.setBackground(Art.placeholder(words, Art.dp(c, 12)));
            boolean has = url != null && !url.isEmpty();
            name.setText(has ? "" : words);
            if (has) Rows.load(picture, url, w, h);
            else Rows.clear(picture);
        }

        void progress(float p, int color, int w) {
            boolean on = p > 0;
            track.setVisibility(on ? View.VISIBLE : View.GONE);
            bar.setVisibility(on ? View.VISIBLE : View.GONE);
            if (!on) return;
            bar.setBackgroundColor(color);
            ViewGroup.LayoutParams lp = bar.getLayoutParams();
            lp.width = Math.max(Art.dp(itemView.getContext(), 4), Math.round(w * Math.min(1, p)));
            bar.setLayoutParams(lp);
        }
    }

    /** Films and TV from the playlists. Null until loaded: shimmer cards. */
    static final class Posters extends RecyclerView.Adapter<PosterHolder> {
        private List<Pick> items;
        private final OnPick tap;
        private final int w, h;

        Posters(Context c, OnPick tap) {
            this.tap = tap;
            w = Art.dp(c, posterDp(c));
            h = Math.round(w * 1.5f);
        }

        void set(List<Pick> list) {
            items = list == null ? null : new ArrayList<>(list);
            notifyDataSetChanged();
        }

        boolean empty() {
            return items != null && items.isEmpty();
        }

        @Override public int getItemCount() {
            return items == null ? LOADING : items.size();
        }

        @NonNull @Override public PosterHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new PosterHolder(parent.getContext(), w, h);
        }

        @Override public void onBindViewHolder(@NonNull PosterHolder holder, int i) {
            Context c = holder.itemView.getContext();
            if (items == null) {
                holder.loading(c);
                return;
            }
            Pick p = items.get(i);
            holder.show(c, p.poster, p.title, w, h);
            holder.title.setText(p.title);
            String sub = p.year;
            if (!p.genre.isEmpty()) sub = sub.isEmpty() ? p.genre : sub + " · " + p.genre;
            holder.sub.setText(sub.isEmpty() ? (p.tv ? "TV show" : "Film") : sub);
            holder.progress(-1, 0, w);
            holder.play.setVisibility(View.GONE);
            holder.badge.setVisibility(View.GONE);
            holder.itemView.setContentDescription(p.title + (p.year.isEmpty() ? "" : ", " + p.year));
            holder.itemView.setOnClickListener(v -> tap.on(p));
            holder.itemView.setOnLongClickListener(null);
        }
    }

    /** Continue watching: this phone's films and episodes part-way, then the TVs'. */
    static final class Resumes extends RecyclerView.Adapter<PosterHolder> {
        private List<Resume.Card> items;
        private final OnCard tap, hold;
        private final int w, h;

        Resumes(Context c, OnCard tap, OnCard hold) {
            this.tap = tap;
            this.hold = hold;
            w = Art.dp(c, posterDp(c));
            h = Math.round(w * 1.5f);
        }

        void set(List<Resume.Card> list) {
            items = list == null ? null : new ArrayList<>(list);
            notifyDataSetChanged();
        }

        @Override public int getItemCount() {
            return items == null ? LOADING : items.size();
        }

        @NonNull @Override public PosterHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new PosterHolder(parent.getContext(), w, h);
        }

        @Override public void onBindViewHolder(@NonNull PosterHolder holder, int i) {
            Context c = holder.itemView.getContext();
            if (items == null) {
                holder.loading(c);
                return;
            }
            Resume.Card k = items.get(i);
            holder.show(c, k.poster, k.title, w, h);
            holder.title.setText(k.title);
            holder.sub.setText(k.line.isEmpty() ? (k.tvTitle != null ? "On your TV" : " ") : k.line);
            holder.progress(k.progress, k.tv ? Art.SHOW : Art.FILM, w);
            holder.play.setVisibility(View.VISIBLE);
            boolean badge = k.badge != null && !k.badge.isEmpty();
            holder.badge.setVisibility(badge ? View.VISIBLE : View.GONE);
            if (badge) holder.badge.setText(k.badge);
            holder.itemView.setContentDescription("Resume " + k.title + (k.line.isEmpty() ? "" : ", " + k.line));
            holder.itemView.setOnClickListener(v -> tap.on(k));
            holder.itemView.setOnLongClickListener(hold == null || k.entry == null ? null : v -> {
                hold.on(k);
                return true;
            });
        }
    }

    // ---- headlines ----------------------------------------------------------------------------------------------

    static final class StoryHolder extends RecyclerView.ViewHolder {
        final ImageView picture;
        final FrameLayout frame;
        final TextView kicker, headline;

        StoryHolder(Context c, int w) {
            super(new LinearLayout(c));
            LinearLayout card = (LinearLayout) itemView;
            card.setOrientation(LinearLayout.VERTICAL);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Art.dp(c, 10);
            card.setLayoutParams(lp);
            Art.clip(card, Art.dp(c, 16));
            frame = new FrameLayout(c);
            picture = new ImageView(c);
            picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
            picture.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            frame.addView(picture, new FrameLayout.LayoutParams(-1, -1));
            card.addView(frame, new LinearLayout.LayoutParams(w, w * 9 / 16));
            LinearLayout words = new LinearLayout(c);
            words.setOrientation(LinearLayout.VERTICAL);
            int pad = Art.dp(c, 12);
            words.setPadding(pad, Art.dp(c, 10), pad, pad);
            kicker = text(c, 11, Art.NEWS, Type.interSemi(c), 1);
            kicker.setLetterSpacing(0.04f);
            words.addView(kicker);
            headline = text(c, 15, Art.TEXT, Type.interSemi(c), 3);
            // Three lines kept whatever the headline's length: every card the same height.
            headline.setLines(3);
            headline.setLineSpacing(Art.dp(c, 2), 1f);
            LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
            hl.topMargin = Art.dp(c, 6);
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
            w = Art.dp(c, newsDp(c));
        }

        void set(List<Story> list) {
            items = list == null ? null : new ArrayList<>(list);
            notifyDataSetChanged();
        }

        boolean empty() {
            return items != null && items.isEmpty();
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
                h.itemView.setBackground(new Art.Shimmer(Art.dp(c, 16)));
                h.frame.setBackground(null);
                Rows.clear(h.picture);
                h.kicker.setText(" ");
                h.headline.setText("");
                h.itemView.setOnClickListener(null);
                h.itemView.setClickable(false);
                return;
            }
            Story s = items.get(i);
            h.itemView.setBackground(Art.glass(c, 16));
            h.frame.setBackground(Art.placeholder(s.title, 0));
            Rows.load(h.picture, s.picture.isEmpty() ? null : s.picture, w, w * 9 / 16);
            String ago = Headlines.ago(s.at, System.currentTimeMillis());
            h.kicker.setTextColor(accent);
            h.kicker.setText(source.toUpperCase(java.util.Locale.UK) + (ago.isEmpty() ? "" : "  ·  " + ago));
            h.headline.setText(s.title);
            h.itemView.setContentDescription(s.title + ", " + source + (ago.isEmpty() ? "" : ", " + ago));
            h.itemView.setOnClickListener(v -> tap.on(s));
        }
    }
}
