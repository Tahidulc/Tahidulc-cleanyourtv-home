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

/** Live TV's sheets from the foot: a programme's details (with Remind me), and Manage favourites (drag to reorder). */
final class Sheets {
    private Sheets() {}

    static BottomSheetDialog open(Context c, View content, boolean draggable) {
        BottomSheetDialog d = new BottomSheetDialog(c);
        d.setContentView(content);
        BottomSheetBehavior<FrameLayout> b = d.getBehavior();
        b.setSkipCollapsed(true);
        b.setState(BottomSheetBehavior.STATE_EXPANDED);
        b.setDraggable(draggable);
        d.show();
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
