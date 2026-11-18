package com.gathin.tvhomephone.live;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.gathin.tvhomephone.R;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Live TV's sheets from the foot: a programme's details (with Remind me), Manage favourites (drag to reorder), a
 * channel's menu (its long press), Similar channels, and the hidden channels (to unhide).
 */
final class Sheets {
    private Sheets() {}

    /** What a channel's menu does. */
    interface ChannelMenu {
        void favourite(Channel ch);

        void hide(Channel ch);

        void similar(Channel ch);

        void info(Channel ch);
    }

    static BottomSheetDialog open(Context c, View content, boolean draggable) {
        BottomSheetDialog d = new BottomSheetDialog(c);
        d.setContentView(content);
        BottomSheetBehavior<FrameLayout> b = d.getBehavior();
        b.setSkipCollapsed(true);
        b.setState(BottomSheetBehavior.STATE_EXPANDED);
        b.setDraggable(draggable);
        d.show();
        // A tablet: a card in the middle of the screen rather than a sheet across its foot.
        com.gathin.tvhomephone.glass.Wide.card(d);
        return d;
    }

    /** Everything the guide has on one programme, with Watch, Remind me and the star. */
    static BottomSheetDialog programme(Context c, Library lib, Channel ch, int index, Details.Actions actions) {
        NestedScrollView scroll = new NestedScrollView(c);
        Details d = new Details(c, lib, false, 40);
        scroll.addView(d, new FrameLayout.LayoutParams(-1, -2));
        BottomSheetDialog sheet = open(c, scroll, true);
        d.setActions(new Details.Actions() {
            @Override public void watch(Channel x) {
                sheet.dismiss();
                actions.watch(x);
            }

            @Override public void reminded(Channel x, long start, boolean on) { actions.reminded(x, start, on); }

            @Override public void favourite(Channel x) {
                actions.favourite(x);
                d.refresh();
            }

            @Override public void more(Channel x, int i) {}
        });
        d.bind(ch, index);
        return sheet;
    }

    // ---- a channel's menu ------------------------------------------------------------------------------------------

    /** A long press on a channel: Add or Remove favourite, Hide channel, Similar channels, Channel info. */
    static BottomSheetDialog channel(Context c, Library lib, Channel ch, ChannelMenu m) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(c, 20), Ui.dp(c, 16), Ui.dp(c, 20), Ui.dp(c, 12));
        box.addView(head(c, lib, ch));
        box.addView(new View(c), new LinearLayout.LayoutParams(-1, Ui.dp(c, 10)));
        BottomSheetDialog[] d = new BottomSheetDialog[1];
        boolean fav = Picks.isFavourite(c, ch.key);
        action(c, box, fav ? R.drawable.live_ic_star : R.drawable.live_ic_star_outline,
                fav ? R.string.live_fav_remove : R.string.live_fav_add, () -> {
                    d[0].dismiss();
                    m.favourite(ch);
                });
        action(c, box, R.drawable.live_ic_hide, R.string.live_hide, () -> {
            d[0].dismiss();
            m.hide(ch);
        });
        // Its whole category: hidden, or a favourite (first among the chips).
        if (!ch.group.isEmpty()) {
            boolean favGroup = Groups.isFavourite(c, ch.group);
            action(c, box, favGroup ? R.drawable.live_ic_star : R.drawable.live_ic_star_outline,
                    favGroup ? R.string.groups_unfavourite_named : R.string.groups_favourite_named, ch.group, () -> {
                        d[0].dismiss();
                        Groups.setFavourite(c, ch.group, !favGroup);
                    });
            action(c, box, R.drawable.live_ic_hide, R.string.groups_hide_named, ch.group, () -> {
                d[0].dismiss();
                Groups.setHidden(c, ch.group, true);
                android.widget.Toast.makeText(c, c.getString(R.string.groups_hidden_toast, ch.group), android.widget.Toast.LENGTH_LONG).show();
            });
        }
        action(c, box, R.drawable.live_ic_similar, R.string.live_similar, () -> {
            d[0].dismiss();
            m.similar(ch);
        });
        action(c, box, R.drawable.live_ic_info, R.string.live_channel_info, () -> {
            d[0].dismiss();
            m.info(ch);
        });
        d[0] = open(c, box, true);
        return d[0];
    }

    /** A channel's logo, number and name, and its group. */
    private static View head(Context c, Library lib, Channel ch) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(logo(c, ch, 56, 40));
        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Ui.dp(c, 14), 0, 0, 0);
        int n = lib.number(ch);
        TextView name = Ui.text(c, n > 0 ? n + "  " + ch.name : ch.name, 18, Ui.TEXT, true);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(name);
        if (!ch.group.isEmpty()) {
            TextView g = Ui.text(c, ch.group, 13, Ui.DIM, false);
            g.setSingleLine(true);
            g.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(g);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        return row;
    }

    private static FrameLayout logo(Context c, Channel ch, int w, int h) {
        FrameLayout box = new FrameLayout(c);
        box.setBackground(Ui.round(c, Ui.GLASS, 10));
        TextView ini = Ui.text(c, ch.logo.isEmpty() ? Ui.initials(ch.name) : "", 12, Ui.SOFT, true);
        ini.setGravity(Gravity.CENTER);
        box.addView(ini, new FrameLayout.LayoutParams(-1, -1));
        ImageView img = new ImageView(c);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = Ui.dp(c, 4);
        img.setPadding(p, p, p, p);
        if (!ch.logo.isEmpty()) Glide.with(img).load(ch.logo).into(img);
        box.addView(img, new FrameLayout.LayoutParams(-1, -1));
        box.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, w), Ui.dp(c, h)));
        return box;
    }

    /** One of a menu's lines: an icon and what it does. */
    /** As action, the label a formatted string with one name in it ("Hide UK | Sports"). */
    static void action(Context c, LinearLayout box, int icon, int label, String name, Runnable r) {
        action(c, box, icon, c.getString(label, name), r);
    }

    static void action(Context c, LinearLayout box, int icon, int label, Runnable r) {
        action(c, box, icon, c.getString(label), r);
    }

    static void action(Context c, LinearLayout box, int icon, String label, Runnable r) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0);
        row.setBackground(Ui.touch(c, false, 0, 12));
        ImageView i = new ImageView(c);
        i.setImageResource(icon);
        i.setImageTintList(ColorStateList.valueOf(Ui.G_SELECT));
        row.addView(i, new LinearLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24)));
        TextView t = Ui.text(c, label, 16, Ui.TEXT, false);
        t.setPadding(Ui.dp(c, 16), 0, 0, 0);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        row.setOnClickListener(v -> r.run());
        box.addView(row, new LinearLayout.LayoutParams(-1, Ui.dp(c, 52)));
    }

    /** Channels like this one (its family, then its group): tap one to watch it. */
    static BottomSheetDialog similar(Context c, Library lib, Channel ch, Consumer<Channel> watch) {
        List<Channel> list = lib.similar(ch, 24);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 12));
        box.addView(Ui.text(c, c.getString(R.string.live_similar), 20, Ui.TEXT, true));
        TextView sub = Ui.text(c, list.isEmpty() ? c.getString(R.string.live_similar_none) : ch.name, 13, Ui.DIM, false);
        sub.setPadding(0, Ui.dp(c, 2), 0, Ui.dp(c, 8));
        box.addView(sub);
        BottomSheetDialog[] d = new BottomSheetDialog[1];
        long now = System.currentTimeMillis();
        for (Channel x : list) {
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(c, 4), Ui.dp(c, 6), Ui.dp(c, 4), Ui.dp(c, 6));
            row.setBackground(Ui.touch(c, false, 0, 12));
            row.addView(logo(c, x, 52, 36));
            LinearLayout texts = new LinearLayout(c);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(Ui.dp(c, 12), 0, 0, 0);
            int n = lib.number(x);
            TextView name = Ui.text(c, n > 0 ? n + "  " + x.name : x.name, 15, Ui.TEXT, true);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(name);
            Guide.Schedule s = lib.schedule(x.key);
            int at = s == null ? -1 : s.at(now);
            TextView on = Ui.text(c, at >= 0 && !s.title[at].isEmpty() ? s.title[at] : c.getString(R.string.live_no_guide_info), 13,
                    Ui.SOFT, false);
            on.setSingleLine(true);
            on.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(on);
            row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
            row.setContentDescription(c.getString(R.string.live_watch_named, x.name));
            row.setOnClickListener(v -> {
                d[0].dismiss();
                watch.accept(x);
            });
            box.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        NestedScrollView scroll = new NestedScrollView(c);
        scroll.addView(box, new FrameLayout.LayoutParams(-1, -2));
        d[0] = open(c, scroll, true);
        return d[0];
    }

    // ---- the hidden channels ----------------------------------------------------------------------------------

    /** The channels hidden on this phone, each with Unhide, and Unhide all. */
    static void hidden(Context c, Library lib) {
        List<String[]> list = new ArrayList<>();
        for (Map.Entry<String, String> e : Hidden.all(c).entrySet()) list.add(new String[]{e.getKey(), e.getValue()});
        // The ones still in the playlist (for their logos and numbers).
        Map<String, Channel> found = lib.hiddenChannels();
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 8));
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(c);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(Ui.text(c, c.getString(R.string.live_hidden_title), 20, Ui.TEXT, true));
        TextView hint = Ui.text(c, c.getString(list.isEmpty() ? R.string.live_hidden_empty : R.string.live_hidden_hint), 13, Ui.DIM, false);
        titles.addView(hint);
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        MaterialButton all = new MaterialButton(c);
        all.setText(R.string.live_unhide_all);
        all.setAllCaps(false);
        all.setCornerRadius(Ui.dp(c, 24));
        all.setTextColor(Ui.G_INK);
        all.setBackgroundTintList(ColorStateList.valueOf(Ui.G_SELECT));
        all.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
        head.addView(all, new LinearLayout.LayoutParams(-2, Ui.dp(c, 48)));
        root.addView(head);

        RecyclerView rv = new RecyclerView(c);
        rv.setLayoutManager(new LinearLayoutManager(c));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = Ui.dp(c, 12);
        root.addView(rv, rp);
        RecyclerView.Adapter<RecyclerView.ViewHolder> adapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override public int getItemCount() { return list.size(); }

            @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
                LinearLayout row = new LinearLayout(c);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setLayoutParams(new RecyclerView.LayoutParams(-1, Ui.dp(c, 64)));
                FrameLayout box = new FrameLayout(c);
                box.setBackground(Ui.round(c, Ui.GLASS, 10));
                TextView ini = Ui.text(c, "", 12, Ui.SOFT, true);
                ini.setGravity(Gravity.CENTER);
                box.addView(ini, new FrameLayout.LayoutParams(-1, -1));
                ImageView logo = new ImageView(c);
                logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
                int p = Ui.dp(c, 4);
                logo.setPadding(p, p, p, p);
                box.addView(logo, new FrameLayout.LayoutParams(-1, -1));
                row.addView(box, new LinearLayout.LayoutParams(Ui.dp(c, 56), Ui.dp(c, 40)));
                TextView name = Ui.text(c, "", 15, Ui.TEXT, true);
                name.setSingleLine(true);
                name.setEllipsize(TextUtils.TruncateAt.END);
                name.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 8), 0);
                row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
                MaterialButton un = new MaterialButton(c);
                un.setText(R.string.live_unhide);
                un.setAllCaps(false);
                un.setCornerRadius(Ui.dp(c, 24));
                un.setTextColor(Ui.TEXT);
                un.setBackgroundTintList(ColorStateList.valueOf(0x29FFFFFF));
                row.addView(un, new LinearLayout.LayoutParams(-2, Ui.dp(c, 48)));
                RecyclerView.ViewHolder vh = new RecyclerView.ViewHolder(row) {};
                un.setOnClickListener(v -> {
                    int at = vh.getBindingAdapterPosition();
                    if (at < 0 || at >= list.size()) return;
                    String[] gone = list.remove(at);
                    Hidden.unhide(c, gone[0]);
                    notifyItemRemoved(at);
                    if (list.isEmpty()) {
                        hint.setText(R.string.live_hidden_empty);
                        all.setVisibility(View.GONE);
                    }
                });
                row.setTag(new View[]{ini, logo, name, un});
                return vh;
            }

            @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
                String[] it = list.get(position);
                View[] v = (View[]) h.itemView.getTag();
                Channel ch = found.get(it[0]);
                String label = ch != null ? ch.name : it[1];
                ImageView logo = (ImageView) v[1];
                boolean pic = ch != null && !ch.logo.isEmpty();
                ((TextView) v[0]).setText(pic ? "" : Ui.initials(label));
                if (pic) Glide.with(logo).load(ch.logo).into(logo);
                else Glide.with(logo).clear(logo);
                int n = ch == null ? 0 : lib.number(ch);
                ((TextView) v[2]).setText(n > 0 ? n + "  " + label : label);
                v[3].setContentDescription(c.getString(R.string.live_unhide_named, label));
            }
        };
        rv.setAdapter(adapter);
        BottomSheetDialog sheet = open(c, root, false);
        all.setOnClickListener(v -> {
            Hidden.unhideAll(c);
            sheet.dismiss();
        });
    }

    // ---- Categories ----------------------------------------------------------------------------------------------

    /**
     * The playlists' categories: a star puts one first among the chips (favourites in the order starred), Hide takes one
     * out of Live TV (its channels out of the guide, the lists and search). Hidden ones stay listed here, dimmed, to show
     * again; Show all brings every one back.
     */
    static void categories(Context c, Library lib) {
        List<String> all = new ArrayList<>(lib.groups());
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 8));
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(c);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(Ui.text(c, c.getString(R.string.groups_title), 20, Ui.TEXT, true));
        titles.addView(Ui.text(c, c.getString(all.isEmpty() ? R.string.groups_empty : R.string.groups_hint), 13, Ui.DIM, false));
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        MaterialButton showAll = new MaterialButton(c);
        showAll.setText(R.string.groups_show_all);
        showAll.setAllCaps(false);
        showAll.setCornerRadius(Ui.dp(c, 24));
        showAll.setTextColor(Ui.G_INK);
        showAll.setBackgroundTintList(ColorStateList.valueOf(Ui.G_SELECT));
        showAll.setVisibility(Groups.hidden(c).isEmpty() ? View.GONE : View.VISIBLE);
        head.addView(showAll, new LinearLayout.LayoutParams(-2, Ui.dp(c, 48)));
        root.addView(head);

        RecyclerView rv = new RecyclerView(c);
        rv.setLayoutManager(new LinearLayoutManager(c));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = Ui.dp(c, 12);
        root.addView(rv, rp);
        RecyclerView.Adapter<RecyclerView.ViewHolder> adapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override public int getItemCount() { return all.size(); }

            @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
                LinearLayout row = new LinearLayout(c);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setLayoutParams(new RecyclerView.LayoutParams(-1, Ui.dp(c, 60)));
                ImageButton star = new ImageButton(c);
                star.setBackground(Ui.touch(c, false, 0, 24));
                row.addView(star, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
                LinearLayout words = new LinearLayout(c);
                words.setOrientation(LinearLayout.VERTICAL);
                words.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 8), 0);
                TextView name = Ui.text(c, "", 15, Ui.TEXT, true);
                name.setSingleLine(true);
                name.setEllipsize(TextUtils.TruncateAt.END);
                words.addView(name);
                TextView count = Ui.text(c, "", 12, Ui.DIM, false);
                words.addView(count);
                row.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
                MaterialButton hide = new MaterialButton(c);
                hide.setAllCaps(false);
                hide.setCornerRadius(Ui.dp(c, 24));
                hide.setTextColor(Ui.TEXT);
                hide.setBackgroundTintList(ColorStateList.valueOf(0x29FFFFFF));
                row.addView(hide, new LinearLayout.LayoutParams(-2, Ui.dp(c, 48)));
                RecyclerView.ViewHolder vh = new RecyclerView.ViewHolder(row) {};
                star.setOnClickListener(v -> {
                    int at = vh.getBindingAdapterPosition();
                    if (at < 0 || at >= all.size()) return;
                    String g = all.get(at);
                    boolean fav = !Groups.isFavourite(c, g);
                    if (fav && Groups.hidden(c).contains(g)) Groups.setHidden(c, g, false);
                    Groups.setFavourite(c, g, fav);
                    notifyItemChanged(at);
                });
                hide.setOnClickListener(v -> {
                    int at = vh.getBindingAdapterPosition();
                    if (at < 0 || at >= all.size()) return;
                    String g = all.get(at);
                    Groups.setHidden(c, g, !Groups.hidden(c).contains(g));
                    showAll.setVisibility(Groups.hidden(c).isEmpty() ? View.GONE : View.VISIBLE);
                    notifyItemChanged(at);
                });
                row.setTag(new View[]{star, name, count, hide});
                return vh;
            }

            @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
                String g = all.get(position);
                View[] v = (View[]) h.itemView.getTag();
                boolean fav = Groups.isFavourite(c, g), gone = Groups.hidden(c).contains(g);
                ImageButton star = (ImageButton) v[0];
                star.setImageResource(fav ? R.drawable.live_ic_star : R.drawable.live_ic_star_outline);
                star.setImageTintList(ColorStateList.valueOf(fav ? Ui.STAR : Ui.DIM));
                star.setContentDescription(c.getString(fav ? R.string.groups_unfavourite : R.string.groups_favourite) + ": " + g);
                ((TextView) v[1]).setText(g);
                ((TextView) v[1]).setTextColor(gone ? Ui.DIM : Ui.TEXT);
                int n = lib.groupSize(g);
                ((TextView) v[2]).setText(c.getResources().getQuantityString(R.plurals.groups_channels, n, n)
                        + (gone ? " · " + c.getString(R.string.groups_hidden_word) : fav ? " · " + c.getString(R.string.groups_favourite_word) : ""));
                ((MaterialButton) v[3]).setText(gone ? R.string.groups_show : R.string.groups_hide_short);
                v[3].setContentDescription(c.getString(gone ? R.string.groups_show : R.string.groups_hide_short) + ": " + g);
            }
        };
        rv.setAdapter(adapter);
        BottomSheetDialog sheet = open(c, root, false);
        showAll.setOnClickListener(v -> {
            Groups.showAll(c);
            sheet.dismiss();
        });
    }

    // ---- Manage favourites ------------------------------------------------------------------------------------------

    /** Your channels in order: drag (by the handle, or a long press) to reorder, ✕ to take one out. Saved on closing. */
    @SuppressLint("ClickableViewAccessibility")
    static void favourites(Context c, Library lib) {
        List<Channel> list = new ArrayList<>(lib.list(Library.FAVOURITES, ""));
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 8));
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(c);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(Ui.text(c, c.getString(R.string.live_manage_title), 20, Ui.TEXT, true));
        TextView hint = Ui.text(c, c.getString(list.isEmpty() ? R.string.live_fav_empty : R.string.live_manage_hint), 13, Ui.DIM, false);
        titles.addView(hint);
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        MaterialButton done = new MaterialButton(c);
        done.setText(R.string.live_done);
        done.setAllCaps(false);
        done.setCornerRadius(Ui.dp(c, 24));
        done.setTextColor(Ui.G_INK);
        done.setBackgroundTintList(ColorStateList.valueOf(Ui.G_SELECT));
        head.addView(done, new LinearLayout.LayoutParams(-2, Ui.dp(c, 48)));
        root.addView(head);

        RecyclerView rv = new RecyclerView(c);
        rv.setLayoutManager(new LinearLayoutManager(c));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = Ui.dp(c, 12);
        root.addView(rv, rp);

        boolean[] changed = {false};
        ItemTouchHelper[] drag = new ItemTouchHelper[1];
        RecyclerView.Adapter<RecyclerView.ViewHolder> adapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override public int getItemCount() { return list.size(); }

            @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
                LinearLayout row = new LinearLayout(c);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setLayoutParams(new RecyclerView.LayoutParams(-1, Ui.dp(c, 64)));
                ImageView handle = new ImageView(c);
                handle.setImageResource(R.drawable.live_ic_drag);
                handle.setImageTintList(ColorStateList.valueOf(Ui.SOFT));
                handle.setScaleType(ImageView.ScaleType.CENTER);
                handle.setContentDescription(c.getString(R.string.live_drag));
                row.addView(handle, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
                FrameLayout box = new FrameLayout(c);
                box.setBackground(Ui.round(c, Ui.GLASS, 10));
                ImageView logo = new ImageView(c);
                logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
                int p = Ui.dp(c, 4);
                logo.setPadding(p, p, p, p);
                box.addView(logo, new FrameLayout.LayoutParams(-1, -1));
                row.addView(box, new LinearLayout.LayoutParams(Ui.dp(c, 56), Ui.dp(c, 40)));
                TextView name = Ui.text(c, "", 15, Ui.TEXT, true);
                name.setSingleLine(true);
                name.setEllipsize(TextUtils.TruncateAt.END);
                name.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 8), 0);
                row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
                ImageButton remove = Ui.icon(c, R.drawable.live_ic_close, R.string.live_fav_remove);
                row.addView(remove, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
                RecyclerView.ViewHolder vh = new RecyclerView.ViewHolder(row) {};
                handle.setOnTouchListener((v, e) -> {
                    if (e.getActionMasked() == MotionEvent.ACTION_DOWN && drag[0] != null) drag[0].startDrag(vh);
                    return false;
                });
                remove.setOnClickListener(v -> {
                    int at = vh.getBindingAdapterPosition();
                    if (at < 0 || at >= list.size()) return;
                    Channel gone = list.remove(at);
                    Picks.toggle(c, gone.key);
                    notifyItemRemoved(at);
                    if (list.isEmpty()) hint.setText(R.string.live_fav_empty);
                });
                row.setTag(new View[]{logo, name});
                return vh;
            }

            @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
                Channel ch = list.get(position);
                View[] v = (View[]) h.itemView.getTag();
                ImageView logo = (ImageView) v[0];
                if (ch.logo.isEmpty()) Glide.with(logo).clear(logo);
                else Glide.with(logo).load(ch.logo).into(logo);
                int n = lib.number(ch);
                ((TextView) v[1]).setText(n > 0 ? n + "  " + ch.name : ch.name);
            }
        };
        rv.setAdapter(adapter);
        drag[0] = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override public boolean onMove(@NonNull RecyclerView r, @NonNull RecyclerView.ViewHolder a, @NonNull RecyclerView.ViewHolder b) {
                int from = a.getBindingAdapterPosition(), to = b.getBindingAdapterPosition();
                if (from < 0 || to < 0) return false;
                Collections.swap(list, from, to);
                adapter.notifyItemMoved(from, to);
                changed[0] = true;
                return true;
            }

            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder h, int dir) {}

            @Override public void onSelectedChanged(RecyclerView.ViewHolder h, int state) {
                super.onSelectedChanged(h, state);
                if (h != null && state == ItemTouchHelper.ACTION_STATE_DRAG) h.itemView.setBackground(Ui.round(c, 0x33FFFFFF, 12));
            }

            @Override public void clearView(@NonNull RecyclerView r, @NonNull RecyclerView.ViewHolder h) {
                super.clearView(r, h);
                h.itemView.setBackground(null);
            }
        });
        drag[0].attachToRecyclerView(rv);

        // Not draggable itself, so a drag in the list moves a channel rather than the sheet; the list scrolls inside it.
        BottomSheetDialog sheet = open(c, root, false);
        done.setOnClickListener(v -> sheet.dismiss());
        sheet.setOnDismissListener(x -> {
            if (!changed[0]) return;
            List<String> keys = new ArrayList<>();
            for (Channel ch : list) keys.add(ch.key);
            Picks.setOrder(c, keys);
        });
    }
}
