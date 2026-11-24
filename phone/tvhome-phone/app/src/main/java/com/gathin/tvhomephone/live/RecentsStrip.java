package com.gathin.tvhomephone.live;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.gathin.tvhomephone.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Recently watched, as Live TV shows it (and the player's guide panel): "Recently watched" with "Back to <the channel
 * before>" beside it, then a row of frosted cards, newest first, each the channel's logo on its own colour, what's on it
 * now and the channel, and a white line for how far in that is. Tap a card to watch it, hold it for the channel's menu.
 * The list is Recents' (each merged channel once, the one playing left out); it hides itself when there is none.
 */
@SuppressLint("ViewConstructor")
final class RecentsStrip extends LinearLayout {
    interface Listener {
        void play(Channel ch);

        void menu(Channel ch);
    }

    private final Library lib;
    private final TextView back;
    private final RecyclerView row;
    private final Cards cards = new Cards();
    private final boolean slim;
    private Listener listener;
    private Channel last;

    /** slim: the small cards (a pane beside the guide); otherwise the full ones. */
    RecentsStrip(Context c, Library lib, boolean slim) {
        super(c);
        this.lib = lib;
        this.slim = slim;
        setOrientation(VERTICAL);
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = TvLook.text(c, c.getString(R.string.tv07_recent_title), slim ? 14 : 15, TvLook.L1, true);
        title.setSingleLine(true);
        head.addView(title, new LayoutParams(-2, -2));
        // Back to the channel before: one tap.
        back = TvLook.text(c, "", 12.5f, TvLook.L2, false);
        back.setSingleLine(true);
        back.setEllipsize(TextUtils.TruncateAt.END);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setMinHeight(Ui.dp(c, 32));
        back.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 6), 0);
        back.setBackground(TvLook.touch(c, Ui.round(c, 0, 14), 14));
        back.setOnClickListener(v -> {
            if (last != null && listener != null) listener.play(last);
        });
        LayoutParams bp = new LayoutParams(0, -2, 1f);
        bp.leftMargin = Ui.dp(c, 4);
        head.addView(back, bp);
        head.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 8), 0);
        addView(head, new LayoutParams(-1, -2));

        row = new RecyclerView(c);
        row.setLayoutManager(new LinearLayoutManager(c, LinearLayoutManager.HORIZONTAL, false));
        row.setAdapter(cards);
        row.setItemAnimator(null);
        row.setClipToPadding(false);
        row.setHorizontalScrollBarEnabled(false);
        row.setPadding(Ui.dp(c, 12), Ui.dp(c, 2), Ui.dp(c, 12), Ui.dp(c, 8));
        row.setContentDescription(c.getString(R.string.tv07_recent_title));
        addView(row, new LayoutParams(-1, Ui.dp(c, (slim ? 56 : 60) + 10)));
        setVisibility(GONE);
    }

    void setListener(Listener l) {
        listener = l;
    }

    /** The recent channels as they are now (newest first), the one to go back to first among them; none hides it. */
    @SuppressLint("NotifyDataSetChanged")
    void set(List<Channel> recent) {
        cards.items = recent == null ? new ArrayList<>() : new ArrayList<>(recent);
        cards.notifyDataSetChanged();
        last = cards.items.isEmpty() ? null : cards.items.get(0);
        if (last != null) {
            back.setText(getContext().getString(R.string.tv07_back_to, last.name));
            back.setContentDescription(getContext().getString(R.string.tv07_back_to, last.name));
        }
        back.setVisibility(last == null ? INVISIBLE : VISIBLE);
        setVisibility(cards.items.isEmpty() ? GONE : VISIBLE);
    }

    /** The minute moved on: each card's line again. */
    void tick() {
        cards.notifyItemRangeChanged(0, cards.getItemCount());
    }

    boolean hasAny() {
        return !cards.items.isEmpty();
    }

    private final class Cards extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        List<Channel> items = new ArrayList<>();

        @Override public int getItemCount() {
            return items.size();
        }

        @SuppressLint("ClickableViewAccessibility")
        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            Context c = parent.getContext();
            int h = Ui.dp(c, slim ? 56 : 60), tile = h - Ui.dp(c, 12);
            FrameLayout card = new FrameLayout(c);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(Ui.dp(c, slim ? 150 : 172), h);
            lp.rightMargin = Ui.dp(c, 8);
            card.setLayoutParams(lp);
            card.setBackground(TvLook.glass(c, 16));
            card.setElevation(Ui.dp(c, 2));
            LinearLayout in = new LinearLayout(c);
            in.setGravity(Gravity.CENTER_VERTICAL);
            in.setPadding(Ui.dp(c, 6), 0, Ui.dp(c, 10), 0);
            FrameLayout box = new FrameLayout(c);
            box.setElevation(Ui.dp(c, 1));
            TextView ini = TvLook.text(c, "", 12, TvLook.L1, true);
            ini.setGravity(Gravity.CENTER);
            box.addView(ini, new FrameLayout.LayoutParams(-1, -1));
            ImageView img = new ImageView(c);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            box.addView(img, new FrameLayout.LayoutParams(-1, -1));
            in.addView(box, new LayoutParams(tile, tile));
            LinearLayout words = new LinearLayout(c);
            words.setOrientation(VERTICAL);
            words.setPadding(Ui.dp(c, 9), 0, 0, Ui.dp(c, 6));
            TextView now = TvLook.text(c, "", slim ? 12.5f : 13.5f, TvLook.L1, true);
            now.setSingleLine(true);
            now.setEllipsize(TextUtils.TruncateAt.END);
            words.addView(now);
            TextView name = TvLook.text(c, "", 11, TvLook.L3, false);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setPadding(0, Ui.dp(c, 3), 0, 0);
            words.addView(name);
            in.addView(words, new LayoutParams(0, -2, 1f));
            card.addView(in, new FrameLayout.LayoutParams(-1, -1));
            ProgressBar line = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
            line.setMax(1000);
            TvLook.line(line);
            FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-1, Math.max(2, Ui.dp(c, 3)), Gravity.BOTTOM);
            pp.leftMargin = Ui.dp(c, 6) + tile + Ui.dp(c, 9);
            pp.rightMargin = Ui.dp(c, 10);
            pp.bottomMargin = Ui.dp(c, 8);
            card.addView(line, pp);
            card.setTag(new View[]{box, ini, img, now, name, line});
            RecyclerView.ViewHolder vh = new RecyclerView.ViewHolder(card) {};
            card.setOnClickListener(v -> {
                int at = vh.getBindingAdapterPosition();
                if (at >= 0 && at < items.size() && listener != null) listener.play(items.get(at));
            });
            card.setOnLongClickListener(v -> {
                int at = vh.getBindingAdapterPosition();
                if (at < 0 || at >= items.size() || listener == null) return false;
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                listener.menu(items.get(at));
                return true;
            });
            // 07's press: the card lifts toward the finger.
            card.setOnTouchListener((v, e) -> {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) lift(v, true);
                else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) lift(v, false);
                return false;
            });
            return vh;
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Channel ch = items.get(position);
            Context c = holder.itemView.getContext();
            View[] v = (View[]) holder.itemView.getTag();
            View box = v[0];
            TextView ini = (TextView) v[1], now = (TextView) v[3], name = (TextView) v[4];
            ImageView img = (ImageView) v[2];
            ProgressBar line = (ProgressBar) v[5];
            String logo = Ui.logo(ch);
            int p = LogoTiles.pad(c, 5);
            img.setPadding(p, p, p, p);
            if (logo == null) {
                box.setBackground(TvLook.touch(c, LogoTiles.ground(c, GuideLook.nameColour(ch.name), GuideLook.nameColour(ch.name), 12), 12));
                Glide.with(img).clear(img);
                ini.setText(Ui.initials(ch.name));
            } else {
                ini.setText("");
                box.setBackground(TvLook.glass(c, 12));
                LogoTiles.show(box, 12, false, img, logo, TvLook.GROUND, () -> ini.setText(Ui.initials(ch.name)));
            }
            int n = lib.number(ch);
            name.setText(n > 0 ? n + "  " + ch.name : ch.name);
            Guide.Schedule s = lib.schedule(ch.key);
            long t = System.currentTimeMillis();
            int at = s == null ? -1 : s.at(t);
            if (at >= 0 && !s.title[at].isEmpty()) {
                now.setText(s.title[at]);
                line.setVisibility(Theme.progress ? VISIBLE : INVISIBLE);
                line.setProgress(Math.round(1000 * TvLook.progress(s.start[at], s.stop[at], t)));
            } else {
                now.setText(ch.name);
                name.setText(n > 0 ? String.valueOf(n) : ch.group);
                line.setVisibility(INVISIBLE);
            }
            holder.itemView.setContentDescription(c.getString(R.string.live_watch_named, ch.name)
                    + (at >= 0 ? ", " + s.title[at] : ""));
        }
    }

    /** A card lifted toward the finger (pressed) or back down, with 07's ease. */
    static void lift(View v, boolean up) {
        float s = up ? TvLook.PRESS_SCALE : 1f;
        v.animate().scaleX(s).scaleY(s).translationZ(up ? Ui.dp(v.getContext(), 6) : 0)
                .setDuration(up ? 160 : TvLook.LIFT_MS / 2).setInterpolator(TvLook.ease()).start();
    }
}
