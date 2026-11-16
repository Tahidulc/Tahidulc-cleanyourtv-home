package com.gathin.tvhomephone.watch;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.glass.FanCard;
import com.gathin.tvhomephone.glass.Glass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A section's lists as glass cards along the top of Watch, as the reference's genre cards: each list's name with three
 * of its posters fanned in its card, the one showing below brighter. A card's posters are read as it comes into view
 * (TMDB's own cache keeps the lists) and kept for the session, so scrolling back is instant.
 */
final class ListCards extends RecyclerView.Adapter<ListCards.Holder> {
    interface OnList {
        void picked(int index);
    }

    /** The first three posters of each list read this session: "section|name" to their URLs. */
    private static final Map<String, List<String>> POSTERS = new HashMap<>();
    private final Context app;
    private final OnList on;
    private final Set<String> asked = new HashSet<>();
    private List<Lists.Spec> specs = new ArrayList<>();
    private int section, chosen = -1;

    ListCards(Context c, OnList on) {
        this.app = c.getApplicationContext();
        this.on = on;
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final FanCard card;

        Holder(FanCard card) {
            super(card);
            this.card = card;
        }
    }

    /** The section's lists, and which shows (-1 none, as while searching). */
    void set(int section, List<Lists.Spec> specs, int chosen) {
        this.section = section;
        this.specs = specs == null ? new ArrayList<>() : specs;
        this.chosen = chosen;
        notifyDataSetChanged();
    }

    void choose(int i) {
        int was = chosen;
        chosen = i;
        if (was >= 0 && was < specs.size()) notifyItemChanged(was);
        if (i >= 0 && i < specs.size()) notifyItemChanged(i);
    }

    @Override public int getItemCount() {
        return specs.size();
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int type) {
        Context c = parent.getContext();
        FanCard card = new FanCard(c);
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(Glass.dp(c, 228), Glass.dp(c, 100));
        lp.rightMargin = Glass.dp(c, 12);
        card.setLayoutParams(lp);
        return new Holder(card);
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        Lists.Spec s = specs.get(position);
        String key = section + "|" + s.name;
        h.card.set(s.name, POSTERS.get(key));
        h.card.setPicked(position == chosen);
        h.card.setOnClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p >= 0) on.picked(p);
        });
        if (!POSTERS.containsKey(key) && asked.add(key)) fetch(key, s);
    }

    /** The list's first three posters, off the main thread; its card again once they're in. */
    private void fetch(String key, Lists.Spec s) {
        App.io.execute(() -> {
            List<String> got = new ArrayList<>();
            try {
                for (Title t : Lists.load(app, s)) {
                    if (t.poster != null && !t.poster.isEmpty()) got.add(t.poster);
                    if (got.size() == 3) break;
                }
            } catch (Exception ignored) {
                // its own colours, as a list that can't be read now
            }
            App.main.post(() -> {
                POSTERS.put(key, got);
                for (int i = 0; i < specs.size(); i++) {
                    if ((section + "|" + specs.get(i).name).equals(key)) notifyItemChanged(i);
                }
            });
        });
    }
}
