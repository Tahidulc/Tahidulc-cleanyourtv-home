package com.gathin.tvhomephone.live;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.gathin.tvhomephone.MainActivity;
import com.gathin.tvhomephone.R;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Live TV: the family's own playlist. Groups as chips along the top (with Favourites and Recent), a search, and the
 * channels with their logos and the guide's now and next; the last channel watched first. A tap plays it; a long press
 * or the star makes it a favourite. Tablets get the list and the chosen channel's guide side by side.
 */
public class LiveTvFragment extends Fragment {
    private final Handler h = new Handler(Looper.getMainLooper());
    private Library lib;
    private FrameLayout body;
    private LinearLayout chips;
    private TextView sub;
    private EditText search;
    private RecyclerView list;
    private Adapter adapter;
    private LinearLayout guidePanel;
    private String group = Library.ALL;
    private String query = "";
    private String selected;
    private boolean wide;
    private int shownGroups = -1;
    private final Runnable changed = this::refreshAll;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (adapter != null) adapter.notifyItemRangeChanged(0, adapter.getItemCount());
            if (wide) showGuide();
            h.postDelayed(this, 60_000);
        }
    };
    private final Runnable filter = this::refilter;

    @Override public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle state) {
        Context c = requireContext();
        lib = Library.get(c);
        wide = getResources().getConfiguration().screenWidthDp >= 720;
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.GROUND);
        int pad = Ui.dp(c, 16);
        root.setPadding(pad, pad, pad, 0);

        // The title, the count, and a refresh.
        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(c);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(Ui.text(c, getString(R.string.live_title), 26, Ui.TEXT, true));
        sub = Ui.text(c, "", 13, Ui.DIM, false);
        titles.addView(sub);
        top.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageButton refresh = new ImageButton(c);
        refresh.setImageResource(android.R.drawable.ic_popup_sync);
        refresh.setContentDescription(getString(R.string.live_refresh));
        refresh.setBackground(Ui.touch(c, false, Ui.GLASS, 24));
        refresh.setOnClickListener(v -> lib.refresh(null));
        top.addView(refresh, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
        root.addView(top);

        search = new EditText(c);
        search.setHint(R.string.live_search);
        search.setHintTextColor(Ui.DIM);
        search.setTextColor(Ui.TEXT);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setBackground(Ui.round(c, Ui.GLASS, 22));
        search.setPadding(Ui.dp(c, 18), 0, Ui.dp(c, 18), 0);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable s) {
                query = s.toString();
                h.removeCallbacks(filter);
                h.postDelayed(filter, 200);
            }
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, Ui.dp(c, 46));
        sp.topMargin = Ui.dp(c, 12);
        root.addView(search, sp);

        HorizontalScrollView scroll = new HorizontalScrollView(c);
        scroll.setHorizontalScrollBarEnabled(false);
        chips = new LinearLayout(c);
        chips.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 8));
        scroll.addView(chips);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, -2));

        body = new FrameLayout(c);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        list = new RecyclerView(c);
        list.setLayoutManager(new LinearLayoutManager(c));
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, Ui.dp(c, 16));
        adapter = new Adapter();
        list.setAdapter(adapter);
        if (wide) {
            LinearLayout split = new LinearLayout(c);
            split.addView(list, new LinearLayout.LayoutParams(0, -1, 1f));
            ScrollView gs = new ScrollView(c);
            guidePanel = new LinearLayout(c);
            guidePanel.setOrientation(LinearLayout.VERTICAL);
            guidePanel.setPadding(Ui.dp(c, 20), 0, 0, Ui.dp(c, 16));
            gs.addView(guidePanel);
            split.addView(gs, new LinearLayout.LayoutParams(0, -1, 1f));
            body.addView(split, new FrameLayout.LayoutParams(-1, -1));
        } else {
            body.addView(list, new FrameLayout.LayoutParams(-1, -1));
        }
        return root;
    }

    @Override public void onStart() {
        super.onStart();
        lib.addListener(changed);
        lib.ensure();
        refreshAll();
        h.postDelayed(tick, 60_000);
    }

    @Override public void onStop() {
        lib.removeListener(changed);
        h.removeCallbacks(tick);
        h.removeCallbacks(filter);
        super.onStop();
    }

    /** The page as the library has it now: no source, loading, an error, or the channels. */
    private void refreshAll() {
        if (getView() == null) return;
        Context c = requireContext();
        boolean source = lib.hasSource(), ready = lib.ready();
        if (ready) {
            sub.setText(getString(R.string.live_count, lib.count()) + (lib.busy() ? " · " + getString(R.string.live_loading) : ""));
        } else sub.setText("");
        View state = body.findViewWithTag("state");
        if (state != null) body.removeView(state);
        if (!source || !ready) {
            list.setVisibility(View.GONE);
            if (guidePanel != null) ((View) guidePanel.getParent()).setVisibility(View.GONE);
            View s = !source ? empty(c) : lib.busy() || lib.status().isEmpty() ? loading(c) : failed(c);
            s.setTag("state");
            body.addView(s, new FrameLayout.LayoutParams(-1, -1));
            chips.removeAllViews();
            shownGroups = -1;
            search.setEnabled(source && ready);
            return;
        }
        search.setEnabled(true);
        list.setVisibility(View.VISIBLE);
        if (guidePanel != null) ((View) guidePanel.getParent()).setVisibility(View.VISIBLE);
        if (shownGroups != lib.groups().size()) buildChips();
        refilter();
    }

    private void buildChips() {
        Context c = requireContext();
        chips.removeAllViews();
        addChip(c, getString(R.string.live_all), Library.ALL);
        addChip(c, getString(R.string.live_favourites), Library.FAVOURITES);
        addChip(c, getString(R.string.live_recent), Library.RECENT);
        for (String g : lib.groups()) addChip(c, g, g);
        shownGroups = lib.groups().size();
    }

    private void addChip(Context c, String label, String g) {
        TextView t = Ui.chip(c, label, g.equals(group));
        t.setOnClickListener(v -> {
            group = g;
            for (int i = 0; i < chips.getChildCount(); i++) {
                TextView x = (TextView) chips.getChildAt(i);
                boolean on = g.equals(x.getTag());
                x.setTextColor(on ? Ui.NAVY_INK : Ui.TEXT);
                x.setBackground(Ui.touch(c, false, on ? Ui.TEXT : Ui.GLASS, 20));
            }
            refilter();
            list.scrollToPosition(0);
        });
        t.setTag(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Ui.dp(c, 8);
        chips.addView(t, lp);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void refilter() {
        if (adapter == null || !lib.ready()) return;
        List<Object> rows = new ArrayList<>();
        List<Channel> found = lib.list(group, query);
        // All channels with no search: the last one watched first.
        if (Library.ALL.equals(group) && query.trim().isEmpty()) {
            List<Channel> recent = lib.list(Library.RECENT, "");
            if (!recent.isEmpty()) {
                rows.add(getString(R.string.live_last_watched));
                rows.add(recent.get(0));
                rows.add(getString(R.string.live_all_channels));
            }
        }
        rows.addAll(found);
        if (found.isEmpty()) rows.add(getString(R.string.live_nothing_found));
        adapter.rows = rows;
        adapter.notifyDataSetChanged();
        if (wide) {
            if (selected == null || lib.byKey(selected) == null) selected = found.isEmpty() ? null : found.get(0).key;
            showGuide();
        }
    }

    /** Tablets: the chosen channel's guide beside the list, with Watch. */
    private void showGuide() {
        if (guidePanel == null) return;
        Context c = requireContext();
        guidePanel.removeAllViews();
        Channel ch = selected == null ? null : lib.byKey(selected);
        if (ch == null) return;
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(logo(c, ch, 64));
        TextView n = Ui.text(c, ch.name, 22, Ui.TEXT, true);
        n.setPadding(Ui.dp(c, 16), 0, 0, 0);
        head.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
        guidePanel.addView(head);
        MaterialButton watch = new MaterialButton(c);
        watch.setText(R.string.live_watch);
        watch.setTextColor(Ui.NAVY_INK);
        watch.setBackgroundTintList(ColorStateList.valueOf(Ui.TEXT));
        watch.setCornerRadius(Ui.dp(c, 24));
        watch.setOnClickListener(v -> PlayerActivity.start(requireContext(), ch.key, group));
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-2, Ui.dp(c, 52));
        wp.topMargin = Ui.dp(c, 14);
        wp.bottomMargin = Ui.dp(c, 10);
        guidePanel.addView(watch, wp);
        Guide.Schedule s = lib.schedule(ch.key);
        if (s == null || s.start.length == 0) {
            guidePanel.addView(Ui.text(c, getString(R.string.live_no_guide), 14, Ui.DIM, false));
            return;
        }
        long now = System.currentTimeMillis();
        for (int i = 0; i < s.start.length; i++) {
            if (s.stop[i] < now) continue;
            boolean on = s.start[i] <= now;
            LinearLayout r = new LinearLayout(c);
            r.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
            r.setBackground(on ? Ui.ring(c, 0x14FFFFFF, Ui.GREEN, 1.5f, 12) : null);
            TextView t = Ui.text(c, Ui.clock(s.start[i]), 14, on ? Ui.GREEN : Ui.SOFT, true);
            t.setMinWidth(Ui.dp(c, 64));
            r.addView(t);
            TextView title = Ui.text(c, s.title[i], 15, Ui.TEXT, on);
            r.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
            guidePanel.addView(r);
        }
    }

    // ---- states --------------------------------------------------------------------------------------------

    private View empty(Context c) {
        LinearLayout v = centred(c);
        ImageView icon = new ImageView(c);
        icon.setImageResource(android.R.drawable.ic_menu_slideshow);
        icon.setImageTintList(ColorStateList.valueOf(Ui.SOFT));
        v.addView(icon, new LinearLayout.LayoutParams(Ui.dp(c, 64), Ui.dp(c, 64)));
        TextView t = Ui.text(c, getString(R.string.live_empty_title), 20, Ui.TEXT, true);
        t.setPadding(0, Ui.dp(c, 16), 0, Ui.dp(c, 8));
        v.addView(t);
        TextView b = Ui.text(c, getString(R.string.live_empty_body), 15, Ui.SOFT, false);
        b.setGravity(Gravity.CENTER);
        v.addView(b);
        MaterialButton go = new MaterialButton(c);
        go.setText(R.string.live_empty_button);
        go.setCornerRadius(Ui.dp(c, 24));
        go.setOnClickListener(x -> {
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).open(R.id.tab_settings);
        });
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-2, Ui.dp(c, 52));
        gp.topMargin = Ui.dp(c, 20);
        v.addView(go, gp);
        return v;
    }

    private View loading(Context c) {
        LinearLayout v = centred(c);
        ProgressBar p = new ProgressBar(c);
        p.setIndeterminateTintList(ColorStateList.valueOf(Ui.TEXT));
        v.addView(p);
        TextView t = Ui.text(c, getString(R.string.live_loading), 15, Ui.SOFT, false);
        t.setPadding(0, Ui.dp(c, 14), 0, 0);
        v.addView(t);
        return v;
    }

    private View failed(Context c) {
        LinearLayout v = centred(c);
        TextView t = Ui.text(c, lib.status(), 16, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        v.addView(t);
        MaterialButton again = new MaterialButton(c);
        again.setText(R.string.live_try_again);
        again.setCornerRadius(Ui.dp(c, 24));
        again.setOnClickListener(x -> lib.refresh(null));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-2, Ui.dp(c, 52));
        gp.topMargin = Ui.dp(c, 16);
        v.addView(again, gp);
        return v;
    }

    private static LinearLayout centred(Context c) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER);
        v.setPadding(Ui.dp(c, 32), 0, Ui.dp(c, 32), Ui.dp(c, 48));
        return v;
    }

    /** A channel's logo on a rounded glass tile, its initials when it has none. */
    static FrameLayout logo(Context c, Channel ch, int sizeDp) {
        FrameLayout box = new FrameLayout(c);
        box.setBackground(Ui.round(c, Ui.GLASS, 12));
        box.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, sizeDp), Ui.dp(c, sizeDp)));
        TextView ini = Ui.text(c, Ui.initials(ch.name), sizeDp > 56 ? 18 : 14, Ui.SOFT, true);
        ini.setGravity(Gravity.CENTER);
        box.addView(ini, new FrameLayout.LayoutParams(-1, -1));
        ImageView img = new ImageView(c);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = Ui.dp(c, 6);
        img.setPadding(p, p, p, p);
        box.addView(img, new FrameLayout.LayoutParams(-1, -1));
        if (!ch.logo.isEmpty()) {
            ini.setVisibility(View.INVISIBLE);
            Glide.with(c).load(ch.logo).into(img);
        }
        return box;
    }

    // ---- the list ----------------------------------------------------------------------------------------------

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        List<Object> rows = new ArrayList<>();

        @Override public int getItemCount() { return rows.size(); }

        @Override public int getItemViewType(int position) { return rows.get(position) instanceof Channel ? 1 : 0; }

        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            Context c = parent.getContext();
            if (type == 0) {
                TextView t = Ui.text(c, "", 12, Ui.DIM, true);
                t.setLetterSpacing(0.12f);
                t.setPadding(Ui.dp(c, 4), Ui.dp(c, 14), 0, Ui.dp(c, 6));
                t.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
                return new RecyclerView.ViewHolder(t) {};
            }
            return new Row(c);
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object o = rows.get(position);
            if (holder instanceof Row) ((Row) holder).bind((Channel) o);
            else ((TextView) holder.itemView).setText((String) o);
        }
    }

    private final class Row extends RecyclerView.ViewHolder {
        final LinearLayout view;
        final TextView initials, name, nowTag, nowTitle, next;
        final ImageView logo;
        final ProgressBar progress;
        final ImageButton star;
        Channel ch;

        Row(Context c) {
            super(new LinearLayout(c));
            view = (LinearLayout) itemView;
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10));
            view.setMinimumHeight(Ui.dp(c, 76));
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, -2);
            lp.bottomMargin = Ui.dp(c, 6);
            view.setLayoutParams(lp);
            FrameLayout box = new FrameLayout(c);
            box.setBackground(Ui.round(c, Ui.GLASS, 12));
            initials = Ui.text(c, "", 14, Ui.SOFT, true);
            initials.setGravity(Gravity.CENTER);
            box.addView(initials, new FrameLayout.LayoutParams(-1, -1));
            logo = new ImageView(c);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int p = Ui.dp(c, 5);
            logo.setPadding(p, p, p, p);
            box.addView(logo, new FrameLayout.LayoutParams(-1, -1));
            view.addView(box, new LinearLayout.LayoutParams(Ui.dp(c, 52), Ui.dp(c, 52)));
            LinearLayout texts = new LinearLayout(c);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 6), 0);
            name = Ui.text(c, "", 16, Ui.TEXT, true);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(name);
            LinearLayout now = new LinearLayout(c);
            now.setGravity(Gravity.CENTER_VERTICAL);
            nowTag = Ui.text(c, getString(R.string.live_now), 11, Ui.GREEN, true);
            nowTag.setPadding(0, 0, Ui.dp(c, 6), 0);
            now.addView(nowTag);
            nowTitle = Ui.text(c, "", 14, Ui.SOFT, false);
            nowTitle.setSingleLine(true);
            nowTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
            now.addView(nowTitle, new LinearLayout.LayoutParams(0, -2, 1f));
            texts.addView(now);
            progress = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(1000);
            progress.setProgressTintList(ColorStateList.valueOf(Ui.GREEN));
            progress.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.LINE));
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, Ui.dp(c, 3));
            pp.topMargin = Ui.dp(c, 4);
            pp.bottomMargin = Ui.dp(c, 3);
            texts.addView(progress, pp);
            next = Ui.text(c, "", 12, Ui.DIM, false);
            next.setSingleLine(true);
            next.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(next);
            view.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
            star = new ImageButton(c);
            star.setBackground(null);
            star.setContentDescription(getString(R.string.live_favourite));
            view.addView(star, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
            view.setOnClickListener(v -> {
                if (ch == null) return;
                if (wide && !ch.key.equals(selected)) {
                    selected = ch.key;
                    adapter.notifyItemRangeChanged(0, adapter.getItemCount());
                    showGuide();
                } else {
                    PlayerActivity.start(requireContext(), ch.key, group);
                }
            });
            view.setOnLongClickListener(v -> {
                if (ch != null) favourite();
                return true;
            });
            star.setOnClickListener(v -> favourite());
        }

        private void favourite() {
            if (ch == null) return;
            Picks.toggle(requireContext(), ch.key);
            bind(ch);
        }

        void bind(Channel c) {
            ch = c;
            Context ctx = view.getContext();
            view.setBackground(Ui.touch(ctx, wide && c.key.equals(selected), 0x0FFFFFFF, 14));
            name.setText(c.number > 0 ? c.number + "  " + c.name : c.name);
            initials.setText(Ui.initials(c.name));
            if (c.logo.isEmpty()) {
                Glide.with(logo).clear(logo);
                initials.setVisibility(View.VISIBLE);
            } else {
                initials.setVisibility(View.INVISIBLE);
                Glide.with(logo).load(c.logo).into(logo);
            }
            star.setImageResource(Picks.isFavourite(ctx, c.key) ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);
            Guide.Schedule s = lib.schedule(c.key);
            long now = System.currentTimeMillis();
            int at = s == null ? -1 : s.at(now), nx = s == null ? -1 : s.after(now);
            if (at >= 0) {
                nowTag.setVisibility(View.VISIBLE);
                nowTitle.setText(getString(R.string.live_until, s.title[at], Ui.clock(s.stop[at])));
                progress.setVisibility(View.VISIBLE);
                progress.setProgress((int) (1000 * (now - s.start[at]) / Math.max(1, s.stop[at] - s.start[at])));
            } else {
                nowTag.setVisibility(View.GONE);
                nowTitle.setText(c.group);
                progress.setVisibility(View.GONE);
            }
            if (nx >= 0) {
                next.setVisibility(View.VISIBLE);
                next.setText(getString(R.string.live_next) + "  " + Ui.clock(s.start[nx]) + "  " + s.title[nx]);
            } else next.setVisibility(View.GONE);
        }
    }
}
