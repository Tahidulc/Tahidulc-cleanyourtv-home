package com.gathin.tvhomephone.watch;

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
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;

import java.util.ArrayList;
import java.util.List;

/**
 * Posters in a grid (or a row): the picture with its rounded corners, the badge on its top corner, the foot line over
 * a dark fade, a progress bar when part-watched, Top 10's place, and the title under it. Tap opens the title's page;
 * hold does what the list asks (Coming up: the release pop-up off or on; elsewhere: My list).
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
        final ImageView image;
        final TextView badge, foot, name, rank;
        final View bar, barBack;
        final FrameLayout frame;

        Holder(View v, FrameLayout frame, ImageView image, TextView badge, TextView foot, TextView name, TextView rank, View bar, View barBack) {
            super(v);
            this.frame = frame;
            this.image = image;
            this.badge = badge;
            this.foot = foot;
            this.name = name;
            this.rank = rank;
            this.bar = bar;
            this.barBack = barBack;
        }
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        Context c = parent.getContext();
        LinearLayout cell = Ui.column(c);
        int pad = Ui.dp(c, 6);
        cell.setPadding(pad, pad, pad, pad);
        FrameLayout frame = new AspectFrame(c);
        frame.setBackground(Ui.round(0xFF1A1F57, 12, c));
        ImageView image = new ImageView(c);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frame.addView(image, new FrameLayout.LayoutParams(-1, -1));
        View fade = new View(c);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0x00000000, 0xD9000000});
        g.setCornerRadius(Ui.dp(c, 12));
        fade.setBackground(g);
        frame.addView(fade, new FrameLayout.LayoutParams(-1, -1));
        TextView badge = Ui.text(c, "", 11, 0xFFFFFFFF, true);
        badge.setLetterSpacing(0.06f);
        badge.setPadding(Ui.dp(c, 7), Ui.dp(c, 3), Ui.dp(c, 7), Ui.dp(c, 3));
        badge.setMaxLines(1);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        bp.setMargins(Ui.dp(c, 7), Ui.dp(c, 7), Ui.dp(c, 7), 0);
        frame.addView(badge, bp);
        TextView rank = Ui.text(c, "", 34, 0xFFFFFFFF, true);
        rank.setShadowLayer(8, 0, 2, 0xCC000000);
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        rp.setMargins(0, 0, Ui.dp(c, 8), 0);
        frame.addView(rank, rp);
        TextView foot = Ui.text(c, "", 12, 0xFFF0F1FA, true);
        foot.setMaxLines(2);
        foot.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        fp.setMargins(Ui.dp(c, 8), 0, Ui.dp(c, 8), Ui.dp(c, 9));
        frame.addView(foot, fp);
        View barBack = new View(c);
        barBack.setBackgroundColor(0x40FFFFFF);
        frame.addView(barBack, new FrameLayout.LayoutParams(-1, Ui.dp(c, 3), Gravity.BOTTOM));
        View bar = new View(c);
        bar.setBackgroundColor(0xFFFFC93C);
        frame.addView(bar, new FrameLayout.LayoutParams(0, Ui.dp(c, 3), Gravity.BOTTOM | Gravity.START));
        cell.addView(frame, new LinearLayout.LayoutParams(widthPx > 0 ? widthPx : -1, -2));
        TextView name = Ui.text(c, "", 13, Ui.SOFT, false);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPadding(Ui.dp(c, 2), Ui.dp(c, 6), Ui.dp(c, 2), 0);
        cell.addView(name, new LinearLayout.LayoutParams(widthPx > 0 ? widthPx : -1, -2));
        cell.setLayoutParams(new RecyclerView.LayoutParams(widthPx > 0 ? widthPx + 2 * pad : -1, -2));
        return new Holder(cell, frame, image, badge, foot, name, rank, bar, barBack);
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        Title t = items.get(position);
        Context c = h.itemView.getContext();
        Glide.with(h.image).load(t.poster).transform(new CenterCrop(), new RoundedCorners(Ui.dp(c, 12))).into(h.image);
        h.badge.setVisibility(t.badge == null ? View.GONE : View.VISIBLE);
        if (t.badge != null) {
            h.badge.setText(t.badge);
            h.badge.setBackground(Ui.round(t.badgeColor, 6, c));
        }
        h.foot.setVisibility(t.foot == null ? View.GONE : View.VISIBLE);
        h.foot.setText(t.foot == null ? "" : t.foot);
        h.rank.setVisibility(t.rank > 0 ? View.VISIBLE : View.GONE);
        h.rank.setText(String.valueOf(t.rank));
        h.name.setText(t.title);
        boolean bar = t.progress >= 0;
        h.bar.setVisibility(bar ? View.VISIBLE : View.GONE);
        h.barBack.setVisibility(bar ? View.VISIBLE : View.GONE);
        if (bar) {
            h.frame.post(() -> {
                ViewGroup.LayoutParams lp = h.bar.getLayoutParams();
                lp.width = Math.round(h.frame.getWidth() * Math.max(0f, Math.min(1f, t.progress)));
                h.bar.setLayoutParams(lp);
            });
        }
        h.itemView.setContentDescription(t.title + (t.badge == null ? "" : ", " + t.badge) + (t.foot == null ? "" : ", " + t.foot));
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

    /** A frame two parts wide to three tall, as posters are. */
    static final class AspectFrame extends FrameLayout {
        AspectFrame(Context c) {
            super(c);
        }

        @Override protected void onMeasure(int w, int h) {
            int width = MeasureSpec.getSize(w);
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(width * 3 / 2, MeasureSpec.EXACTLY));
        }
    }
}
