package com.gathin.tvhomephone.live;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.gathin.tvhomephone.R;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The sheets for what Homefield TV lets the family edit in its channels and categories, the phone's way: a name of
 * their own for a channel (Rename channel, and Settings' Rename channels to find any), its logo (Change logo), its
 * place in its category (Move channel: drag it), and Manage categories (each one's star, hide, name, Sort channels, and
 * their own order). What they change is ChannelEdits' and Groups'; the lists follow.
 */
final class EditSheets {
    private EditSheets() {}

    // ---- a name of the family's own ---------------------------------------------------------------------------------

    /** A line to type a name in, with its playlist's name again when it has one of the family's. */
    static void ask(Context c, String title, String now, String playlistName, boolean renamed, Consumer<String> done) {
        EditText field = new EditText(c);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        field.setText(now);
        field.setSelectAllOnFocus(true);
        field.setHint(playlistName);
        field.setTextColor(Ui.TEXT);
        field.setHintTextColor(Ui.DIM);
        field.setBackground(Ui.round(c, Ui.GLASS, 14));
        field.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), 0);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(c, 22), Ui.dp(c, 8), Ui.dp(c, 22), 0);
        box.addView(field, new LinearLayout.LayoutParams(-1, Ui.dp(c, 50)));
        TextView was = Ui.text(c, c.getString(R.string.ps_rename_hint, playlistName), 13, Ui.DIM, false);
        was.setPadding(Ui.dp(c, 4), Ui.dp(c, 8), 0, 0);
        box.addView(was);
        AlertDialog.Builder b = new AlertDialog.Builder(c)
                .setTitle(title)
                .setView(box)
                .setPositiveButton(R.string.ps_save, (d, w) -> done.accept(field.getText().toString()))
                .setNegativeButton(R.string.ps_cancel, null);
        if (renamed) b.setNeutralButton(R.string.ps_playlist_name, (d, w) -> done.accept(""));
        AlertDialog d = b.create();
        d.setOnShowListener(x -> {
            field.requestFocus();
            if (d.getWindow() != null) d.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        });
        d.show();
    }

    /** Rename channel: every version of it; nothing typed (or its own name) is its playlist's name again. */
    static void renameChannel(Context c, Library lib, Channel ch) {
        String was = lib.defaultName(ch);
        boolean renamed = lib.renamed(ch);
        ask(c, c.getString(R.string.ps_rename_title, ch.name), ch.name, was, renamed, text -> {
            ChannelEdits.rename(c, lib.familyKeys(ch.key), text, was);
            String now = ChannelEdits.clean(text);
            boolean own = !now.isEmpty() && !now.equals(was);
            Toast.makeText(c, own ? c.getString(R.string.ps_renamed_toast, was, now) : c.getString(R.string.ps_unnamed_toast, was),
                    Toast.LENGTH_SHORT).show();
        });
    }

    /** Restore channel name: the playlist's again, on every version. */
    static void restoreName(Context c, Library lib, Channel ch) {
        String was = lib.defaultName(ch);
        ChannelEdits.rename(c, lib.familyKeys(ch.key), "", was);
        Toast.makeText(c, c.getString(R.string.ps_unnamed_toast, was), Toast.LENGTH_SHORT).show();
    }

    /**
     * Settings' Rename channels: the channels renamed, then every channel shown (a search finds one by its name or its
     * playlist's); a tap types a new name.
     */
    @SuppressLint("NotifyDataSetChanged")
    static void renameChannels(Context c, Library lib) {
        LinearLayout root = sheetRoot(c);
        root.addView(head(c, c.getString(R.string.ps_rename), c.getString(R.string.ps_rename_sheet_hint), null));
        EditText search = new EditText(c);
        search.setHint(R.string.ps_rename_search);
        search.setHintTextColor(Ui.DIM);
        search.setTextColor(Ui.TEXT);
        search.setSingleLine(true);
        search.setBackground(Ui.round(c, Ui.GLASS, 22));
        search.setPadding(Ui.dp(c, 18), 0, Ui.dp(c, 18), 0);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, Ui.dp(c, 46));
        sp.topMargin = Ui.dp(c, 12);
        root.addView(search, sp);

        List<Object> items = new ArrayList<>();
        String[] query = {""};
        Runnable fill = () -> {
            items.clear();
            List<Channel> renamed = lib.renamedChannels();
            String q = Names.plain(query[0]).toLowerCase(java.util.Locale.ROOT).trim();
            List<Channel> shown = new ArrayList<>();
            for (Channel ch : renamed) {
                String n = Names.plain(ch.name + "\n" + ch.playlistName).toLowerCase(java.util.Locale.ROOT);
                if (q.isEmpty() || n.contains(q)) shown.add(ch);
            }
            if (!shown.isEmpty()) {
                items.add(c.getString(R.string.ps_renamed_header));
                items.addAll(shown);
            }
            List<Channel> all = lib.list(Library.ALL, query[0]);
            if (!all.isEmpty()) items.add(c.getString(R.string.ps_all_channels_header));
            items.addAll(all);
        };
        fill.run();

        RecyclerView rv = list(c, root);
        RecyclerView.Adapter<RecyclerView.ViewHolder> adapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override public int getItemCount() { return items.size(); }

            @Override public int getItemViewType(int position) { return items.get(position) instanceof String ? 0 : 1; }

            @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
                if (type == 0) {
                    TextView t = Ui.text(c, "", 12, Ui.DIM, true);
                    t.setLetterSpacing(0.08f);
                    t.setPadding(Ui.dp(c, 4), Ui.dp(c, 14), 0, Ui.dp(c, 6));
                    t.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
                    return new RecyclerView.ViewHolder(t) {};
                }
                ChannelRow row = new ChannelRow(c);
                RecyclerView.ViewHolder vh = new RecyclerView.ViewHolder(row.view) {};
                row.view.setOnClickListener(v -> {
                    int at = vh.getBindingAdapterPosition();
                    if (at >= 0 && at < items.size() && items.get(at) instanceof Channel) renameChannel(c, lib, (Channel) items.get(at));
                });
                row.view.setTag(row);
                return vh;
            }

            @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
                Object it = items.get(position);
                if (it instanceof String) {
                    ((TextView) h.itemView).setText(((String) it).toUpperCase(java.util.Locale.UK));
                    return;
                }
                Channel ch = (Channel) it;
                boolean own = !ch.name.equals(lib.defaultName(ch)) && lib.renamed(ch);
                ((ChannelRow) h.itemView.getTag()).bind(lib, ch,
                        own ? c.getString(R.string.ps_playlist_name_line, lib.defaultName(ch)) : Groups.label(ch.group));
            }
        };
        rv.setAdapter(adapter);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}

            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}

            @Override public void afterTextChanged(Editable s) {
                query[0] = s.toString();
                fill.run();
                adapter.notifyDataSetChanged();
            }
        });
        // A name kept: the lists made again off the main thread (Library.editsChanged), then this list.
        Runnable again = () -> {
            fill.run();
            adapter.notifyDataSetChanged();
        };
        lib.addListener(again);
        BottomSheetDialog d = Sheets.open(c, root, false);
        d.setOnDismissListener(x -> lib.removeListener(again));
    }

    // ---- Change logo --------------------------------------------------------------------------------------------------

    /**
     * The logos a channel can show {label, address}: the playlist's own, each of its other versions' and its copies' in
     * the family's other playlists, and Sky's for its name; each address once.
     */
    static List<String[]> logoChoices(Context c, Library lib, Channel ch) {
        Map<String, String> found = new LinkedHashMap<>();
        if (!ch.playlistLogo.isEmpty()) found.put(ch.playlistLogo, c.getString(R.string.ps_logo_playlist));
        List<Channel> all = new ArrayList<>(lib.versions(ch));
        for (Channel v : new ArrayList<>(all)) all.addAll(v.alternates);
        for (Channel v : all) {
            if (!v.playlistLogo.isEmpty() && !found.containsKey(v.playlistLogo)) {
                found.put(v.playlistLogo, c.getString(R.string.ps_logo_of, v.playlistName));
            }
        }
        for (String n : new String[]{ch.playlistName, lib.defaultName(ch)}) {
            String sky = com.gathin.tvhomephone.sports.ChannelLogos.sky(n);
            if (sky != null && !found.containsKey(sky)) found.put(sky, c.getString(R.string.ps_logo_sky));
        }
        List<String[]> out = new ArrayList<>();
        for (Map.Entry<String, String> e : found.entrySet()) out.add(new String[]{e.getValue(), e.getKey()});
        return out;
    }

    /** Change logo: its own (as the playlist gives it), or one of the others found, the one showing ticked. */
    static void changeLogo(Context c, Library lib, Channel ch) {
        LinearLayout root = sheetRoot(c);
        root.addView(head(c, c.getString(R.string.ps_logo_title), c.getString(R.string.ps_logo_hint, ch.name), null));
        List<String[]> choices = logoChoices(c, lib, ch);
        String chosen = ChannelEdits.logo(c, lib.familyKeys(ch.key));
        BottomSheetDialog[] d = new BottomSheetDialog[1];
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, Ui.dp(c, 10), 0, 0);
        // Its own first: the playlist's logo (or Sky's by its name), as before any was chosen.
        box.addView(logoRow(c, ch, c.getString(R.string.ps_logo_own), Ui.logo(ch.dressed(ch.name, ch.playlistLogo)), chosen == null, () -> {
            ChannelEdits.setLogo(c, lib.familyKeys(ch.key), null);
            d[0].dismiss();
        }));
        // One chosen before that isn't among them now (a version gone from the playlist): still there to keep.
        boolean listed = chosen == null;
        for (String[] l : choices) listed |= l[1].equals(chosen);
        if (!listed) choices.add(0, new String[]{c.getString(R.string.ps_logo_chosen), chosen});
        for (String[] l : choices) {
            if (l[1].equals(ch.playlistLogo)) continue;
            box.addView(logoRow(c, ch, l[0], l[1], l[1].equals(chosen), () -> {
                ChannelEdits.setLogo(c, lib.familyKeys(ch.key), l[1]);
                d[0].dismiss();
            }));
        }
        if (box.getChildCount() < 2) {
            TextView none = Ui.text(c, c.getString(R.string.ps_logo_only), 13, Ui.DIM, false);
            none.setPadding(Ui.dp(c, 4), Ui.dp(c, 10), 0, Ui.dp(c, 6));
            box.addView(none);
        }
        root.addView(box);
        NestedScrollView scroll = new NestedScrollView(c);
        scroll.addView(root, new FrameLayout.LayoutParams(-1, -2));
        d[0] = Sheets.open(c, scroll, true);
    }

    private static View logoRow(Context c, Channel ch, String label, String url, boolean on, Runnable pick) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6));
        row.setBackground(Ui.touch(c, on, 0, 14));
        FrameLayout tile = new FrameLayout(c);
        tile.setBackground(Ui.round(c, Ui.GLASS, 12));
        TextView ini = Ui.text(c, url == null || url.isEmpty() ? Ui.initials(ch.name) : "", 14, Ui.SOFT, true);
        ini.setGravity(Gravity.CENTER);
        tile.addView(ini, new FrameLayout.LayoutParams(-1, -1));
        ImageView img = new ImageView(c);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = LogoTiles.pad(c, 6);
        img.setPadding(p, p, p, p);
        tile.addView(img, new FrameLayout.LayoutParams(-1, -1));
        if (url != null && !url.isEmpty()) LogoTiles.show(tile, 12, false, img, url, LogoTiles.ON_NAVY, () -> ini.setText(Ui.initials(ch.name)));
        row.addView(tile, new LinearLayout.LayoutParams(Ui.dp(c, 84), Ui.dp(c, 56)));
        TextView t = Ui.text(c, label, 15, Ui.TEXT, on);
        t.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 8), 0);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        if (on) {
            TextView tick = Ui.text(c, "✓", 20, Ui.TEXT, true);
            tick.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(tick);
        }
        row.setContentDescription(label);
        row.setSelected(on);
        row.setOnClickListener(v -> pick.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.dp(c, 6);
        row.setLayoutParams(lp);
        return row;
    }

    // ---- Move channel ---------------------------------------------------------------------------------------------

    /**
     * Move channel: the channel's category as it shows (one row a channel), each draggable by its handle, the channel
     * held lit; Done keeps that order and makes the category's Sort channels Manual.
     */
    static void moveChannels(Context c, Library lib, Channel focus) {
        String group = focus.group;
        List<Channel> list = new ArrayList<>(lib.list(group, ""));
        List<String[]> items = new ArrayList<>();
        int at = -1;
        for (Channel ch : list) {
            if (ch.key.equals(focus.key) || lib.familyKeys(ch.key).contains(focus.key)) at = items.size();
            items.add(new String[]{ch.key, ch.name, ch.logo});
        }
        drag(c, c.getString(R.string.ps_move_title), c.getString(R.string.ps_move_hint, Groups.label(group)), items, at, keys -> {
            ChannelEdits.setOrder(c, group, keys);
            Groups.setSort(c, group, ChannelEdits.MANUAL);
        });
    }

    // ---- Manage categories ------------------------------------------------------------------------------------------

    /**
     * Manage categories: every category in the family's order, a star to put one first, Hide or Show, and its menu
     * (Rename category, its playlist's name again, Sort channels); Move to drag them into an order of their own; Show
     * all when any is hidden.
     */
    @SuppressLint("NotifyDataSetChanged")
    static void categories(Context c, Library lib) {
        List<String> all = new ArrayList<>(Groups.arranged(lib.groups()));
        LinearLayout root = sheetRoot(c);
        MaterialButton showAll = pillButton(c, R.string.groups_show_all, true);
        MaterialButton move = pillButton(c, R.string.ps_cat_move, false);
        LinearLayout buttons = new LinearLayout(c);
        buttons.addView(move, new LinearLayout.LayoutParams(-2, Ui.dp(c, 48)));
        LinearLayout.LayoutParams sap = new LinearLayout.LayoutParams(-2, Ui.dp(c, 48));
        sap.leftMargin = Ui.dp(c, 8);
        buttons.addView(showAll, sap);
        root.addView(head(c, c.getString(R.string.groups_title), c.getString(all.isEmpty() ? R.string.groups_empty : R.string.groups_hint),
                buttons));
        showAll.setVisibility(Groups.hidden(c).isEmpty() ? View.GONE : View.VISIBLE);
        move.setVisibility(all.size() > 1 ? View.VISIBLE : View.GONE);

        RecyclerView rv = list(c, root);
        RecyclerView.Adapter<RecyclerView.ViewHolder> adapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override public int getItemCount() { return all.size(); }

            @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
                LinearLayout row = new LinearLayout(c);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setLayoutParams(new RecyclerView.LayoutParams(-1, Ui.dp(c, 64)));
                ImageButton star = new ImageButton(c);
                star.setBackground(Ui.touch(c, false, 0, 24));
                row.addView(star, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
                LinearLayout words = new LinearLayout(c);
                words.setOrientation(LinearLayout.VERTICAL);
                words.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 4), 0);
                TextView name = Ui.text(c, "", 15, Ui.TEXT, true);
                name.setSingleLine(true);
                name.setEllipsize(TextUtils.TruncateAt.END);
                words.addView(name);
                TextView count = Ui.text(c, "", 12, Ui.DIM, false);
                count.setMaxLines(2);
                count.setEllipsize(TextUtils.TruncateAt.END);
                words.addView(count);
                row.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
                ImageButton more = Ui.icon(c, R.drawable.live_ic_more_vert, R.string.ps_cat_rename);
                row.addView(more, new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 48)));
                MaterialButton hide = pillButton(c, R.string.groups_hide_short, false);
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
                more.setOnClickListener(v -> {
                    int at = vh.getBindingAdapterPosition();
                    if (at < 0 || at >= all.size()) return;
                    categoryMenu(c, v, all.get(at), () -> notifyItemChanged(at), null, null);
                });
                row.setTag(new View[]{star, name, count, hide, more});
                return vh;
            }

            @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
                String g = all.get(position);
                View[] v = (View[]) h.itemView.getTag();
                boolean fav = Groups.isFavourite(c, g), gone = Groups.hidden(c).contains(g);
                ImageButton star = (ImageButton) v[0];
                star.setImageResource(fav ? R.drawable.live_ic_star : R.drawable.live_ic_star_outline);
                star.setImageTintList(ColorStateList.valueOf(fav ? Ui.STAR : Ui.DIM));
                star.setContentDescription(c.getString(fav ? R.string.groups_unfavourite : R.string.groups_favourite) + ": " + Groups.label(g));
                ((TextView) v[1]).setText(Groups.label(g));
                ((TextView) v[1]).setTextColor(gone ? Ui.DIM : Ui.TEXT);
                int n = lib.groupSize(g);
                StringBuilder line = new StringBuilder(c.getResources().getQuantityString(R.plurals.groups_channels, n, n));
                if (gone) line.append(" · ").append(c.getString(R.string.groups_hidden_word));
                else if (fav) line.append(" · ").append(c.getString(R.string.groups_favourite_word));
                int own = Groups.sortOf(g);
                if (own >= 0) line.append(" · ").append(c.getString(ChannelEdits.SORT_NAMES[own]));
                if (Groups.renamed(g)) line.append(" · ").append(c.getString(R.string.ps_cat_playlist_calls, g));
                ((TextView) v[2]).setText(line);
                ((MaterialButton) v[3]).setText(gone ? R.string.groups_show : R.string.groups_hide_short);
                v[3].setContentDescription(c.getString(gone ? R.string.groups_show : R.string.groups_hide_short) + ": " + Groups.label(g));
                v[4].setContentDescription(c.getString(R.string.ps_cat_options, Groups.label(g)));
            }
        };
        rv.setAdapter(adapter);
        BottomSheetDialog sheet = Sheets.open(c, root, false);
        showAll.setOnClickListener(v -> {
            Groups.showAll(c);
            sheet.dismiss();
        });
        move.setOnClickListener(v -> moveCategories(c, lib, () -> {
            all.clear();
            all.addAll(Groups.arranged(lib.groups()));
            adapter.notifyDataSetChanged();
        }));
    }

    /**
     * A category's menu (its options in Manage categories, and a category held in Live TV): Rename category, its
     * playlist's name again, Sort channels (as Channels sorting, or its own); before them, from Live TV, the phone's own
     * lines (more: added by the caller, ids under 100, each handed to onMore). changed: after any of these.
     */
    static PopupMenu categoryMenu(Context c, View anchor, String g, Runnable changed, Consumer<Menu> more,
                                  java.util.function.IntConsumer onMore) {
        PopupMenu m = new PopupMenu(c, anchor);
        Menu menu = m.getMenu();
        if (more != null) more.accept(menu);
        menu.add(1, 101, 50, R.string.ps_cat_rename);
        if (Groups.renamed(g)) menu.add(1, 102, 51, R.string.ps_cat_restore);
        Menu sort = menu.addSubMenu(1, 103, 52, R.string.ps_cat_sort);
        int own = Groups.sortOf(g);
        sort.add(2, 200, 0, R.string.ps_cat_sort_default).setCheckable(true).setChecked(own < 0);
        for (int i = 0; i < ChannelEdits.SORT_NAMES.length; i++) {
            sort.add(2, 201 + i, i + 1, ChannelEdits.SORT_NAMES[i]).setCheckable(true).setChecked(own == i);
        }
        sort.setGroupCheckable(2, true, true);
        m.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id < 100) {
                if (onMore != null) onMore.accept(id);
                return true;
            }
            if (id == 103) return false;
            if (id == 101) {
                ask(c, c.getString(R.string.ps_rename_title, Groups.label(g)), Groups.label(g), g, Groups.renamed(g), text -> {
                    Groups.rename(c, g, text);
                    if (changed != null) changed.run();
                });
            } else if (id == 102) {
                Groups.rename(c, g, "");
            } else if (id >= 200 && id <= 204) {
                Groups.setSort(c, g, id - 201);
            } else {
                return false;
            }
            if (changed != null) changed.run();
            return true;
        });
        m.show();
        return m;
    }

    /** Move categories: every category in the order shown, draggable; Done keeps it as their own order. */
    static void moveCategories(Context c, Library lib, Runnable after) {
        List<String[]> items = new ArrayList<>();
        for (String g : Groups.arranged(lib.groups())) items.add(new String[]{g, Groups.label(g), null});
        drag(c, c.getString(R.string.ps_move_cats_title), c.getString(R.string.ps_move_cats_hint), items, -1, keys -> {
            Groups.setOwnOrder(c, keys);
            if (after != null) after.run();
        });
    }

    // ---- the parts ------------------------------------------------------------------------------------------------

    /**
     * A sheet of rows {key, words, logo or null} to drag into order by their handles (the row at lit, scrolled to);
     * Done (or the sheet put away) hands over the keys in their new order when any moved.
     */
    @SuppressLint("ClickableViewAccessibility")
    static void drag(Context c, String title, String hint, List<String[]> items, int lit, Consumer<List<String>> done) {
        LinearLayout root = sheetRoot(c);
        MaterialButton ok = pillButton(c, R.string.live_done, true);
        root.addView(head(c, title, hint, ok));
        RecyclerView rv = list(c, root);
        String litKey = lit >= 0 && lit < items.size() ? items.get(lit)[0] : null;
        boolean[] moved = {false};
        ItemTouchHelper[] helper = new ItemTouchHelper[1];
        RecyclerView.Adapter<RecyclerView.ViewHolder> adapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override public int getItemCount() { return items.size(); }

            @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
                LinearLayout row = new LinearLayout(c);
                row.setGravity(Gravity.CENTER_VERTICAL);
                RecyclerView.LayoutParams rl = new RecyclerView.LayoutParams(-1, Ui.dp(c, 58));
                rl.bottomMargin = Ui.dp(c, 4);
                row.setLayoutParams(rl);
                ImageView handle = new ImageView(c);
                handle.setImageResource(R.drawable.live_ic_drag);
                handle.setImageTintList(ColorStateList.valueOf(Ui.SOFT));
                handle.setScaleType(ImageView.ScaleType.CENTER);
                handle.setContentDescription(c.getString(R.string.live_drag));
                row.addView(handle, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
                FrameLayout box = new FrameLayout(c);
                ImageView logo = new ImageView(c);
                logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
                int p = LogoTiles.pad(c, 4);
                logo.setPadding(p, p, p, p);
                box.addView(logo, new FrameLayout.LayoutParams(-1, -1));
                row.addView(box, new LinearLayout.LayoutParams(Ui.dp(c, 56), Ui.dp(c, 40)));
                TextView name = Ui.text(c, "", 15, Ui.TEXT, true);
                name.setSingleLine(true);
                name.setEllipsize(TextUtils.TruncateAt.END);
                name.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 8), 0);
                row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
                RecyclerView.ViewHolder vh = new RecyclerView.ViewHolder(row) {};
                handle.setOnTouchListener((v, e) -> {
                    if (e.getActionMasked() == MotionEvent.ACTION_DOWN && helper[0] != null) helper[0].startDrag(vh);
                    return false;
                });
                row.setTag(new View[]{box, logo, name});
                return vh;
            }

            @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
                String[] it = items.get(position);
                View[] v = (View[]) h.itemView.getTag();
                ((TextView) v[2]).setText(it[1]);
                boolean on = it[0].equals(litKey);
                h.itemView.setBackground(on ? Ui.ring(c, 0x26FFFFFF, Ui.TEXT, 2, 12) : null);
                ImageView logo = (ImageView) v[1];
                if (it[2] == null) {
                    v[0].setVisibility(View.GONE);
                } else if (it[2].isEmpty()) {
                    v[0].setVisibility(View.VISIBLE);
                    v[0].setBackground(Ui.round(c, Ui.GLASS, 10));
                    Glide.with(logo).clear(logo);
                } else {
                    v[0].setVisibility(View.VISIBLE);
                    LogoTiles.show(v[0], 10, false, logo, it[2], LogoTiles.ON_NAVY, null);
                }
            }
        };
        rv.setAdapter(adapter);
        helper[0] = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override public boolean onMove(@NonNull RecyclerView r, @NonNull RecyclerView.ViewHolder a, @NonNull RecyclerView.ViewHolder b) {
                int from = a.getBindingAdapterPosition(), to = b.getBindingAdapterPosition();
                if (from < 0 || to < 0) return false;
                Collections.swap(items, from, to);
                adapter.notifyItemMoved(from, to);
                moved[0] = true;
                return true;
            }

            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder h, int dir) {}

            @Override public void onSelectedChanged(RecyclerView.ViewHolder h, int state) {
                super.onSelectedChanged(h, state);
                if (h != null && state == ItemTouchHelper.ACTION_STATE_DRAG) h.itemView.setBackground(Ui.round(c, 0x33FFFFFF, 12));
            }

            @Override public void clearView(@NonNull RecyclerView r, @NonNull RecyclerView.ViewHolder h) {
                super.clearView(r, h);
                int at = h.getBindingAdapterPosition();
                boolean on = at >= 0 && at < items.size() && items.get(at)[0].equals(litKey);
                h.itemView.setBackground(on ? Ui.ring(c, 0x26FFFFFF, Ui.TEXT, 2, 12) : null);
            }
        });
        helper[0].attachToRecyclerView(rv);
        if (lit > 3) rv.scrollToPosition(Math.max(0, lit - 2));
        BottomSheetDialog sheet = Sheets.open(c, root, false);
        ok.setOnClickListener(v -> sheet.dismiss());
        sheet.setOnDismissListener(x -> {
            if (!moved[0]) return;
            List<String> keys = new ArrayList<>(items.size());
            for (String[] it : items) keys.add(it[0]);
            done.accept(keys);
        });
    }

    /** A channel's row in a sheet: its logo on its tile, its name, a line under it. */
    static final class ChannelRow {
        final LinearLayout view;
        private final FrameLayout box;
        private final ImageView logo;
        private final TextView ini, name, line;

        ChannelRow(Context c) {
            view = new LinearLayout(c);
            view.setGravity(Gravity.CENTER_VERTICAL);
            RecyclerView.LayoutParams rl = new RecyclerView.LayoutParams(-1, Ui.dp(c, 62));
            rl.bottomMargin = Ui.dp(c, 4);
            view.setLayoutParams(rl);
            view.setPadding(Ui.dp(c, 6), 0, Ui.dp(c, 6), 0);
            view.setBackground(Ui.touch(c, false, 0, 12));
            box = new FrameLayout(c);
            ini = Ui.text(c, "", 12, Ui.SOFT, true);
            ini.setGravity(Gravity.CENTER);
            box.addView(ini, new FrameLayout.LayoutParams(-1, -1));
            logo = new ImageView(c);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int p = LogoTiles.pad(c, 4);
            logo.setPadding(p, p, p, p);
            box.addView(logo, new FrameLayout.LayoutParams(-1, -1));
            view.addView(box, new LinearLayout.LayoutParams(Ui.dp(c, 56), Ui.dp(c, 40)));
            LinearLayout words = new LinearLayout(c);
            words.setOrientation(LinearLayout.VERTICAL);
            words.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 4), 0);
            name = Ui.text(c, "", 15, Ui.TEXT, true);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            words.addView(name);
            line = Ui.text(c, "", 12, Ui.DIM, false);
            line.setSingleLine(true);
            line.setEllipsize(TextUtils.TruncateAt.END);
            words.addView(line);
            view.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
            ImageView pen = new ImageView(c);
            pen.setImageResource(R.drawable.live_ic_edit);
            pen.setImageTintList(ColorStateList.valueOf(Ui.DIM));
            view.addView(pen, new LinearLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22)));
        }

        void bind(Library lib, Channel ch, String under) {
            Context c = view.getContext();
            int n = lib.number(ch);
            name.setText(n > 0 ? n + "  " + ch.name : ch.name);
            line.setText(under);
            line.setVisibility(under == null || under.isEmpty() ? View.GONE : View.VISIBLE);
            String url = Ui.logo(ch);
            ini.setText(url == null ? Ui.initials(ch.name) : "");
            box.setBackground(Ui.round(c, Ui.GLASS, 10));
            if (url == null) Glide.with(logo).clear(logo);
            else LogoTiles.show(box, 10, false, logo, url, LogoTiles.ON_NAVY, () -> ini.setText(Ui.initials(ch.name)));
            view.setContentDescription(c.getString(R.string.ps_rename_title, ch.name));
        }
    }

    private static LinearLayout sheetRoot(Context c) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 8));
        return root;
    }

    /** A sheet's head: its title and a line under it, and what goes at its right (a button or two). */
    private static View head(Context c, String title, String hint, View right) {
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(c);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(Ui.text(c, title, 20, Ui.TEXT, true));
        if (hint != null) titles.addView(Ui.text(c, hint, 13, Ui.DIM, false));
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        if (right != null) {
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-2, -2);
            rp.leftMargin = Ui.dp(c, 8);
            head.addView(right, rp);
        }
        return head;
    }

    private static RecyclerView list(Context c, LinearLayout root) {
        RecyclerView rv = new RecyclerView(c);
        rv.setLayoutManager(new LinearLayoutManager(c));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = Ui.dp(c, 12);
        root.addView(rv, rp);
        return rv;
    }

    private static MaterialButton pillButton(Context c, int label, boolean primary) {
        MaterialButton b = new MaterialButton(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setCornerRadius(Ui.dp(c, 24));
        b.setTextColor(primary ? Ui.G_INK : Ui.TEXT);
        b.setBackgroundTintList(ColorStateList.valueOf(primary ? Ui.G_SELECT : 0x29FFFFFF));
        return b;
    }
}
