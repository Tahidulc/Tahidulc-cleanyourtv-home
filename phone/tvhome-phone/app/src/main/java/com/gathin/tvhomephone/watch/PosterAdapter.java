package com.gathin.tvhomephone.watch;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gathin.tvhomephone.glass.PosterCard;
import com.gathin.tvhomephone.live.SourceQuality;

import java.util.ArrayList;
import java.util.List;

/**
 * Posters in a grid (or a row), drawn as the family's reference picture draws them (PosterCard): the picture fading into
 * its own colour at the foot with the title's logo, its genre and rating; where it stands on the top left (Top 10's
 * place, "#1 Today", "Trending", "New", or the list's own word: "THU 5 · TOMORROW", "3 LEFT"); what the family's own
 * sources have of it on the top right; a line along the foot when part-watched; its title and year under it. A list's
 * own foot line ("Ends 22:41 · 25 min spare", "S2 E4 watched") takes the genre's place. Tap opens the title's page; hold
 * does what the list asks (Coming up: the release pop-up off or on; elsewhere: My list).
 */
public final class PosterAdapter extends RecyclerView.Adapter<PosterAdapter.Holder> {
    public interface OnTitle {
        void on(Title t, int position);
    }

    private final List<Title> items = new ArrayList<>();
    private final OnTitle tap, hold;
    private final int widthPx;

    /** widthPx: each poster's width for a row; 0 for a grid's (the column's). */
    public PosterAdapter(OnTitle tap, OnTitle hold, int widthPx) {
        this.tap = tap;
        this.hold = hold;
        this.widthPx = widthPx;
    }

    public void set(List<Title> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    public Title get(int i) {
        return items.get(i);
    }

    @Override public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final PosterCard card;

        Holder(View v, PosterCard card) {
            super(v);
            this.card = card;
        }
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        Context c = parent.getContext();
        LinearLayout cell = Ui.column(c);
        int pad = Ui.dp(c, 6);
        cell.setPadding(pad, pad, pad, pad);
        int w = widthPx > 0 ? widthPx : columnWidth(parent, c) - 2 * pad;
        PosterCard card = new PosterCard(c, w, widthPx > 0, PosterCard.wide(c));
        cell.addView(card, new LinearLayout.LayoutParams(widthPx > 0 ? widthPx : -1, -2));
        cell.setLayoutParams(new RecyclerView.LayoutParams(widthPx > 0 ? widthPx + 2 * pad : -1, -2));
        return new Holder(cell, card);
    }

    /** A grid's column (its span count across the list's width less its padding); a third of the screen when not laid out yet. */
    private static int columnWidth(ViewGroup parent, Context c) {
        int cols = 3;
        if (parent instanceof RecyclerView && ((RecyclerView) parent).getLayoutManager() instanceof GridLayoutManager) {
            cols = ((GridLayoutManager) ((RecyclerView) parent).getLayoutManager()).getSpanCount();
        }
        int width = parent.getWidth() > 0 ? parent.getWidth() : c.getResources().getDisplayMetrics().widthPixels;
        return Math.max(1, (width - parent.getPaddingLeft() - parent.getPaddingRight()) / Math.max(1, cols));
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        Title t = items.get(position);
        Context c = h.itemView.getContext();
        int said = com.gathin.tvhomephone.live.StreamFacts.known(c, t.tv, t.id, t.title, t.year);
        if (said > 0) t.quality = SourceQuality.best(t.quality, said);
        PosterCard.Info info = info(t);
        h.card.bind(info);
        String key = t.key() + ":" + t.title;
        h.itemView.setTag(key);
        if (said < 0) {
            // What the provider itself says of the stream (its picture, HDR, Atmos), once a title.
            com.gathin.tvhomephone.live.StreamFacts.ask(c, t.tv, t.id, t.title, t.year, bits -> {
                t.quality = SourceQuality.best(t.quality, bits);
                if (key.equals(h.itemView.getTag())) h.card.setQuality(t.quality);
            });
        }
        if (!info.logoKnown) {
            Logos.ask(c, t.tv, t.id, logo -> {
                t.logo = logo;
                if (key.equals(h.itemView.getTag())) h.card.setLogo(logo);
            });
        }
        h.itemView.setContentDescription(spoken(t));
        h.itemView.setOnClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p >= 0 && tap != null) tap.on(items.get(p), p);
        });
        h.itemView.setOnLongClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p < 0 || hold == null) return false;
            hold.on(items.get(p), p);
            return true;
        });
    }

    /** Pure: what a title's poster shows. */
    static PosterCard.Info info(Title t) {
        PosterCard.Info i = new PosterCard.Info();
        i.title = t.title;
        i.year = t.year;
        i.poster = t.poster;
        i.backdrop = PosterCard.cardSize(t.backdrop);
        String logo = t.logo != null ? t.logo : Logos.known(t.tv, t.id);
        i.logo = logo;
        i.logoKnown = logo != null || t.id <= 0;
        String genre = t.genres != null && t.genres.length > 0 ? Tmdb.genre(t.genres[0]) : null;
        i.line = t.foot != null ? t.foot : PosterCard.line(genre, t.vote);
        i.badge = t.badge;
        i.badgeFill = t.badgeColor;
        i.quality = t.quality;
        i.progress = t.progress;
        return i;
    }

    /** Pure: what a screen reader says for a poster ("Dune, 2021, #2 Today, Drama • ★ 7.8, 4K"). */
    static String spoken(Title t) {
        StringBuilder b = new StringBuilder(t.title);
        if (!t.year.isEmpty()) b.append(", ").append(t.year);
        if (t.badge != null) b.append(", ").append(t.badge);
        String genre = t.genres != null && t.genres.length > 0 ? Tmdb.genre(t.genres[0]) : null;
        String line = t.foot != null ? t.foot : PosterCard.line(genre, t.vote);
        if (!line.isEmpty()) b.append(", ").append(line);
        String q = SourceQuality.spoken(t.quality);
        if (!q.isEmpty()) b.append(", ").append(q);
        return b.toString();
    }
}
