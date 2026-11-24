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

        /** A version picked in its Quality (one row per channel): kept already; the host plays it if the channel is on. */
        default void quality(Channel version) {}
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

    /**
     * A long press on a channel: its Quality (a channel in several, one row per channel), Add or Remove favourite, Hide
     * channel, its category's lines, then Homefield TV's Rename channel (Restore channel name once renamed), Change logo
     * and Move channel; Similar channels, Channel info.
     */
    static BottomSheetDialog channel(Context c, Library lib, Channel ch, ChannelMenu m) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(c, 20), Ui.dp(c, 16), Ui.dp(c, 20), Ui.dp(c, 12));
        box.addView(head(c, lib, ch));
        box.addView(new View(c), new LinearLayout.LayoutParams(-1, Ui.dp(c, 10)));
        BottomSheetDialog[] d = new BottomSheetDialog[1];
        quality(c, lib, ch, box, v -> {
            d[0].dismiss();
            m.quality(v);
        });
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
            String label = Groups.label(ch.group);
            action(c, box, favGroup ? R.drawable.live_ic_star : R.drawable.live_ic_star_outline,
                    favGroup ? R.string.groups_unfavourite_named : R.string.groups_favourite_named, label, () -> {
                        d[0].dismiss();
                        Groups.setFavourite(c, ch.group, !favGroup);
                    });
            action(c, box, R.drawable.live_ic_hide, R.string.groups_hide_named, label, () -> {
                d[0].dismiss();
                Groups.setHidden(c, ch.group, true);
                android.widget.Toast.makeText(c, c.getString(R.string.groups_hidden_toast, label), android.widget.Toast.LENGTH_LONG).show();
            });
        }
        // As Homefield TV's channel menu: a name of the family's own, its logo, its place in its category.
        action(c, box, R.drawable.live_ic_edit, R.string.ps_rename_channel, () -> {
            d[0].dismiss();
            EditSheets.renameChannel(c, lib, ch);
        });
        if (lib.renamed(ch)) {
            action(c, box, R.drawable.live_ic_replay, c.getString(R.string.ps_restore_name) + " · " + lib.defaultName(ch), () -> {
                d[0].dismiss();
                EditSheets.restoreName(c, lib, ch);
            });
        }
        action(c, box, R.drawable.live_ic_tv, R.string.ps_change_logo, () -> {
            d[0].dismiss();
            EditSheets.changeLogo(c, lib, ch);
        });
        if (!ch.group.isEmpty()) {
            action(c, box, R.drawable.live_ic_drag, R.string.ps_move_channel, () -> {
                d[0].dismiss();
                EditSheets.moveChannels(c, lib, ch);
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

    /**
     * Quality, for a channel shown as one row in several (Homefield TV's hold-OK card): Best, then each version by its
     * playlist's name, best first, the one shown lit; a tap keeps it for the channel (ChannelEdits) and hands it on.
     */
    static void quality(Context c, Library lib, Channel ch, LinearLayout box, Consumer<Channel> picked) {
        List<Channel> vs = lib.versions(ch);
        if (vs.size() < 2) return;
        List<String> keys = lib.familyKeys(ch.key);
        boolean auto = ChannelEdits.pick(c, keys) == null;
        TextView label = Ui.text(c, c.getString(R.string.ps_quality), 13, Ui.DIM, true);
        label.setPadding(Ui.dp(c, 4), 0, 0, Ui.dp(c, 6));
        box.addView(label);
        LinearLayout row = new LinearLayout(c);
        TextView best = LiveTvSettings.pill(c, c.getString(R.string.ps_quality_best), auto);
        best.setOnClickListener(v -> {
            ChannelEdits.setPick(c, keys, null);
            picked.accept(vs.get(0));
        });
        row.addView(best, LiveTvSettings.chipParams(c));
        for (Channel v : vs) {
            TextView t = LiveTvSettings.pill(c, v.playlistName, !auto && v.key.equals(ch.key));
            t.setOnClickListener(x -> {
                ChannelEdits.setPick(c, keys, v.key);
                android.widget.Toast.makeText(c, c.getString(R.string.ps_quality_kept, ch.name, v.playlistName), android.widget.Toast.LENGTH_SHORT).show();
                picked.accept(v);
            });
            row.addView(t, LiveTvSettings.chipParams(c));
        }
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(row);
        box.addView(hs);
        box.addView(new View(c), new LinearLayout.LayoutParams(-1, Ui.dp(c, 10)));
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
            TextView g = Ui.text(c, Groups.label(ch.group), 13, Ui.DIM, false);
            g.setSingleLine(true);
            g.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(g);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        return row;
    }

    private static FrameLayout logo(Context c, Channel ch, int w, int h) {
        FrameLayout box = new FrameLayout(c);
        // The logo on a tile in its own colour, with nothing behind it, or on black (LogoTiles); no logo, its initials on glass.
        box.setBackground(Ui.round(c, Ui.GLASS, 10));
        TextView ini = Ui.text(c, ch.logo.isEmpty() ? Ui.initials(ch.name) : "", 12, Ui.SOFT, true);
        ini.setGravity(Gravity.CENTER);
        box.addView(ini, new FrameLayout.LayoutParams(-1, -1));
        ImageView img = new ImageView(c);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = LogoTiles.pad(c, 4);
        img.setPadding(p, p, p, p);
        if (!ch.logo.isEmpty()) LogoTiles.show(box, 10, false, img, ch.logo, LogoTiles.ON_NAVY, () -> {
            box.setBackground(Ui.round(c, Ui.GLASS, 10));
            ini.setText(Ui.initials(ch.name));
        });
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
        // The ones still in the playlist (for their logos and numbers).
        Map<String, Channel> found = lib.hiddenChannels();
        // A channel shown as one row in several qualities, hidden whole, is one line (its versions hidden with it).
        java.util.Set<String> lines = new java.util.HashSet<>();
        for (Map.Entry<String, String> e : Hidden.all(c).entrySet()) {
            Channel ch = found.get(e.getKey());
            List<String> fam = ch == null ? null : lib.familyKeys(ch.key);
            String line = fam == null || fam.size() < 2 ? e.getKey() : java.util.Collections.min(fam);
            if (lines.add(line)) list.add(new String[]{e.getKey(), e.getValue()});
        }
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
                RecyclerView.LayoutParams rl = new RecyclerView.LayoutParams(-1, Ui.dp(c, 60));
                rl.bottomMargin = Ui.dp(c, 4);
                row.setLayoutParams(rl);
                row.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 4), 0);
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
                    Channel was = found.get(gone[0]);
                    if (was != null) Hidden.unhide(c, was);
                    else Hidden.unhide(c, gone[0]);
                    notifyItemRemoved(at);
                    if (list.isEmpty()) {
                        hint.setText(R.string.live_hidden_empty);
                        all.setVisibility(View.GONE);
                    }
                });
                row.setTag(new View[]{ini, logo, name, un, box});
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
                // The logo on a tile in its own colour and the row (its number and name) in it too, a touch lighter;
                // with nothing behind it, or on black, the plain row (LogoTiles); no logo, its initials on glass.
                int p = LogoTiles.pad(c, 4);
                logo.setPadding(p, p, p, p);
                h.itemView.setBackground(null);
                if (pic) {
                    LogoTiles.show(v[4], 10, false, logo, ch.logo, LogoTiles.ON_NAVY,
                            cols -> h.itemView.setBackground(LogoTiles.row(c, cols, false, Ui.TEXT, 12)), null);
                } else {
                    v[4].setBackground(Ui.round(c, Ui.GLASS, 10));
                    Glide.with(logo).clear(logo);
                }
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
        // Homefield TV's Manage categories: names, their own order and each one's Sort channels besides (EditSheets).
        EditSheets.categories(c, lib);
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
                RecyclerView.LayoutParams rl = new RecyclerView.LayoutParams(-1, Ui.dp(c, 60));
                rl.bottomMargin = Ui.dp(c, 4);
                row.setLayoutParams(rl);
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
                row.setTag(new View[]{logo, name, box});
                return vh;
            }

            @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
                Channel ch = list.get(position);
                View[] v = (View[]) h.itemView.getTag();
                ImageView logo = (ImageView) v[0];
                // The logo on a tile in its own colour and the row (its number and name) in it too, a touch lighter;
                // with nothing behind it, or on black, the plain row (LogoTiles); no logo, an empty glass box.
                int p = LogoTiles.pad(c, 4);
                logo.setPadding(p, p, p, p);
                h.itemView.setBackground(null);
                if (ch.logo.isEmpty()) {
                    v[2].setBackground(Ui.round(c, Ui.GLASS, 10));
                    Glide.with(logo).clear(logo);
                } else {
                    LogoTiles.show(v[2], 10, false, logo, ch.logo, LogoTiles.ON_NAVY,
                            cols -> h.itemView.setBackground(LogoTiles.row(c, cols, false, Ui.TEXT, 12)), null);
                }
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
                // back to its row: in its logo's colour (Logo's colour), else plain
                int at = h.getBindingAdapterPosition();
                Channel ch = at >= 0 && at < list.size() ? list.get(at) : null;
                h.itemView.setBackground(ch == null || ch.logo.isEmpty() ? null
                        : LogoTiles.row(c, LogoTiles.now(c, ch.logo), false, Ui.TEXT, 12));
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
