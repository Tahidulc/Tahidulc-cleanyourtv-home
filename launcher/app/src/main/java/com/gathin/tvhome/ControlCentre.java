package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The Control Centre (launcher v2): the one button at the top right of Home opens it, a panel just under it in the
 * launcher's navy glass (after Apple TV's): the cameras (the latest alarm's picture, OK opening the smart home panel's
 * live view), then the controls two across, each an icon, a name and what it is now (the Wi-Fi network, the VPN on or
 * off). OK on one does what its own button did and closes the panel; Back closes it.
 */
final class ControlCentre {
    /** One control: its name, icon, what it is now (may be null), what OK does, and whether it is lit (VPN on). */
    static final class Item {
        final String name;
        final int icon;
        final Supplier<String> state;
        final Supplier<Boolean> lit;
        final Runnable ok;

        Item(String name, int icon, Supplier<String> state, Supplier<Boolean> lit, Runnable ok) {
            this.name = name;
            this.icon = icon;
            this.state = state;
            this.lit = lit;
            this.ok = ok;
        }
    }

    private static final int NAVY = 0xFF10157E;
    private final HomeV2 h;
    private final Context c;
    private final List<Item> items;
    /** The camera tile: {name, open}; null without cameras. */
    private final String camera;
    private final Runnable openCamera;
    /** The doorbell's app opened; its tile shows beside the camera's while the app (CloudEdge) is on the TV. */
    private final Runnable openDoorbell;
    private final Typeface heavy, semi, regular;
    private FrameLayout overlay;
    private boolean open;
    private final List<View> tiles = new ArrayList<>();
    private final List<Runnable> acts = new ArrayList<>();
    /** Each tile's place: {row, column, columns it spans}. */
    private final List<int[]> at = new ArrayList<>();
    private int focus;

    ControlCentre(HomeV2 home, List<Item> items, String camera, Runnable openCamera, Runnable openDoorbell) {
        h = home;
        c = home.a;
        this.items = items;
        this.camera = camera;
        this.openCamera = openCamera;
        this.openDoorbell = openDoorbell;
        heavy = c.getResources().getFont(R.font.outfit_extrabold);
        semi = c.getResources().getFont(R.font.inter_semibold);
        regular = c.getResources().getFont(R.font.inter_regular);
    }

    boolean isOpen() {
        return open;
    }

    void show() {
        if (open) return;
        open = true;
        tiles.clear();
        acts.clear();
        at.clear();
        overlay = new FrameLayout(c);
        overlay.setClickable(true);
        // Home dimmed a little behind it, as on Apple TV.
        overlay.setBackgroundColor(0x59000014);
        LinearLayout panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(px(30), px(26), px(30), px(30));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xF51A2296, 0xF5121980, 0xF50B0F5A});
        g.setCornerRadius(px(40));
        g.setStroke(Math.max(1, px(1.5f)), 0x40FFFFFF);
        panel.setBackground(g);
        panel.setClipToOutline(true);
        panel.setOutlineProvider(round(40));
        // A focused tile grows a little: into the panel's margin, not cut off at it.
        panel.setClipToPadding(false);
        panel.setClipChildren(false);

        TextView title = text(36, heavy, Color.WHITE);
        title.setText("Control Centre");
        panel.addView(title);
        int row = 0;
        boolean cam = camera != null && openCamera != null;
        boolean bell = openDoorbell != null && DoorbellAlert.installed(c);
        int camTiles = 0;
        if (cam || bell) {
            // The camera and the doorbell side by side (either alone across the panel).
            panel.addView(heading("CAMERAS"), headParams());
            LinearLayout camRow = new LinearLayout(c);
            camRow.setClipChildren(false);
            if (cam) {
                camRow.addView(cameraTile(), new LinearLayout.LayoutParams(0, -1, 1f));
                acts.add(() -> { hide(); openCamera.run(); });
                at.add(new int[]{row, 0, bell ? 1 : 2});
                camTiles++;
            }
            if (bell) {
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, -1, 1f);
                if (cam) bp.leftMargin = px(14);
                camRow.addView(doorbellTile(), bp);
                acts.add(() -> { hide(); openDoorbell.run(); });
                at.add(new int[]{row, cam ? 1 : 0, cam ? 1 : 2});
                camTiles++;
            }
            panel.addView(camRow, new LinearLayout.LayoutParams(-1, px(cam && bell ? 190 : 220)));
            row++;
        }
        panel.addView(heading("CONTROLS"), headParams());
        LinearLayout line = null;
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (i % 2 == 0) {
                line = new LinearLayout(c);
                line.setClipChildren(false);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, px(86));
                if (i > 0) lp.topMargin = px(14);
                panel.addView(line, lp);
            }
            View t = tile(it);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -1, 1f);
            if (i % 2 == 1) tp.leftMargin = px(14);
            line.addView(t, tp);
            tiles.add(t);
            acts.add(() -> { hide(); it.ok.run(); });
            at.add(new int[]{row + i / 2, i % 2, 1});
        }
        if (items.size() % 2 == 1 && line != null) line.addView(new View(c), new LinearLayout.LayoutParams(0, -1, 1f));
        prayerSection(panel);

        FrameLayout box = new FrameLayout(c);
        box.addView(panel, new FrameLayout.LayoutParams(-1, -2));
        box.setClipChildren(true);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(px(640), -2, Gravity.TOP | Gravity.END);
        bl.topMargin = h.px(172);
        bl.rightMargin = h.px(96);
        overlay.addView(box, bl);
        h.root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        // Taller than the room under the top bar (many controls, the cameras and prayer times): up just enough.
        box.post(() -> {
            int over = box.getBottom() - (overlay.getHeight() - h.px(24));
            if (over <= 0) return;
            bl.topMargin = Math.max(h.px(24), bl.topMargin - over);
            box.setLayoutParams(bl);
        });
        box.setAlpha(0f);
        box.setTranslationY(-px(16));
        box.setScaleX(0.97f);
        box.setScaleY(0.97f);
        box.setPivotX(px(640));
        box.setPivotY(0);
        box.animate().alpha(1f).translationY(0).scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(HomeV2.SKY).start();
        focus = camTiles;
        paint();
    }

    void hide() {
        if (!open) return;
        open = false;
        View o = overlay;
        overlay = null;
        o.animate().alpha(0f).setDuration(160).withEndAction(() -> h.root.removeView(o)).start();
        h.paint();
    }

    /**
     * Prayer times (Prayer, when the theme asks): today's five in a row, the next one lit, and in the heading how long
     * until it (in Ramadan, iftar or sehri). Words only: the remote passes over it.
     */
    private void prayerSection(LinearLayout panel) {
        String head = Prayer.heading(c);
        if (head == null) return;
        panel.addView(heading(head), headParams());
        Object[] next = Prayer.next(c);
        LinearLayout row = new LinearLayout(c);
        boolean first = true;
        for (String[] p : Prayer.today(c)) {
            if ("Sunrise".equals(p[0])) continue;
            boolean lit = next != null && p[0].equals(next[0]) && p[1].equals(next[1]);
            LinearLayout box = new LinearLayout(c);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER);
            box.setBackground(fill(18, lit ? 0xFFFFFFFF : 0x1FFFFFFF));
            TextView name = text(15, semi, lit ? 0xCC10157E : 0xB3FFFFFF);
            name.setText(p[0]);
            box.addView(name);
            TextView time = text(24, heavy, lit ? 0xFF10157E : Color.WHITE);
            time.setText(p[1]);
            box.addView(time);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            if (!first) lp.leftMargin = px(10);
            first = false;
            row.addView(box, lp);
        }
        panel.addView(row, new LinearLayout.LayoutParams(-1, px(80)));
    }

    private TextView heading(String s) {
        TextView t = text(15, semi, 0xB3FFFFFF);
        t.setText(s);
        t.setLetterSpacing(0.16f);
        return t;
    }

    private LinearLayout.LayoutParams headParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = px(22);
        lp.bottomMargin = px(12);
        return lp;
    }

    /** The cameras: the latest alarm's picture when there has been one this session, the camera's name over it. */
    private View cameraTile() {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(fill(24, 0xFF0A0E46));
        f.setClipToOutline(true);
        f.setOutlineProvider(round(24));
        Object[] shot = CameraAlert.lastShot(camera);
        Bitmap pic = shot != null && shot[0] instanceof Bitmap ? (Bitmap) shot[0] : null;
        if (pic != null) {
            ImageView i = new ImageView(c);
            i.setImageBitmap(pic);
            i.setScaleType(ImageView.ScaleType.CENTER_CROP);
            f.addView(i, new FrameLayout.LayoutParams(-1, -1));
        } else {
            ImageView i = new ImageView(c);
            i.setImageResource(R.drawable.ic_camera);
            i.setColorFilter(0x66FFFFFF);
            f.addView(i, new FrameLayout.LayoutParams(px(96), px(96), Gravity.CENTER));
        }
        View shade = new View(c);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0x00000000, 0xB3000000}));
        f.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout name = new LinearLayout(c);
        name.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(c);
        icon.setImageResource(R.drawable.ic_camera);
        icon.setColorFilter(Color.WHITE);
        name.addView(icon, new LinearLayout.LayoutParams(px(28), px(28)));
        TextView t = text(21, semi, Color.WHITE);
        t.setText(camera);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.leftMargin = px(10);
        name.addView(t, tl);
        if (shot == null) {
            TextView s = text(16, regular, 0xBFFFFFFF);
            s.setText("  ·  live view");
            name.addView(s);
        }
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        nl.leftMargin = px(18);
        nl.bottomMargin = px(14);
        f.addView(name, nl);
        tiles.add(f);
        return f;
    }

    /** The doorbell: the last ring's picture this session (else a bell), "Doorbell" and when it last rang over it. */
    private View doorbellTile() {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(fill(24, 0xFF0A0E46));
        f.setClipToOutline(true);
        f.setOutlineProvider(round(24));
        Object[] ring = DoorbellAlert.lastRing();
        Bitmap pic = ring != null && ring[0] instanceof Bitmap ? (Bitmap) ring[0] : null;
        if (pic != null) {
            ImageView i = new ImageView(c);
            i.setImageBitmap(pic);
            i.setScaleType(ImageView.ScaleType.CENTER_CROP);
            f.addView(i, new FrameLayout.LayoutParams(-1, -1));
        } else {
            f.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF3A2A0E, 0xFF14102A, 0xFF0A0E46}));
            TextView b = text(54, regular, Color.WHITE);
            b.setText("🔔");
            FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
            bl.bottomMargin = px(20);
            f.addView(b, bl);
        }
        View shade = new View(c);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0x00000000, 0xB3000000}));
        f.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout name = new LinearLayout(c);
        name.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(21, semi, Color.WHITE);
        t.setText("Doorbell");
        name.addView(t);
        TextView s = text(15, regular, 0xBFFFFFFF);
        s.setText(ring == null ? "CloudEdge" : "Rang at " + new java.text.SimpleDateFormat("HH:mm", java.util.Locale.UK)
                .format(new java.util.Date((Long) ring[1])));
        name.addView(s);
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        nl.leftMargin = px(18);
        nl.bottomMargin = px(14);
        f.addView(name, nl);
        tiles.add(f);
        return f;
    }

    private View tile(Item it) {
        LinearLayout t = new LinearLayout(c);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(px(18), 0, px(14), 0);
        ImageView i = new ImageView(c);
        i.setImageResource(it.icon);
        i.setTag("icon");
        t.addView(i, new LinearLayout.LayoutParams(px(32), px(32)));
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView n = text(19, semi, Color.WHITE);
        n.setText(it.name);
        n.setSingleLine(true);
        n.setTag("name");
        words.addView(n);
        String st = it.state == null ? null : it.state.get();
        if (st != null && !st.isEmpty()) {
            TextView s = text(14, regular, 0xB3FFFFFF);
            s.setText(st);
            s.setSingleLine(true);
            s.setTag("state");
            words.addView(s);
        }
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, -2, 1f);
        wl.leftMargin = px(14);
        t.addView(words, wl);
        t.setTag(it);
        return t;
    }

    /** The panel a size down from Home's own measures, so it ends well clear of the foot of the screen. */
    private static final float K = 0.8f;

    private int px(float v) {
        return h.px(v * K);
    }

    private TextView text(float size, android.graphics.Typeface face, int colour) {
        return h.text(size * K, face, colour);
    }

    private GradientDrawable fill(float radius, int colour) {
        return h.fill(radius * K, colour);
    }

    private android.view.ViewOutlineProvider round(float radius) {
        return h.round(radius * K);
    }

    boolean key(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (e.getAction() == KeyEvent.ACTION_UP) hide();
            return true;
        }
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (ok) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled() && focus < acts.size()) acts.get(focus).run();
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        int[] here = at.get(focus);
        int to = -1;
        if (code == KeyEvent.KEYCODE_DPAD_LEFT) to = find(here[0], here[1] - 1);
        else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) to = find(here[0], here[1] + here[2]);
        else if (code == KeyEvent.KEYCODE_DPAD_UP) to = find(here[0] - 1, here[1]);
        else if (code == KeyEvent.KEYCODE_DPAD_DOWN) to = find(here[0] + 1, here[1]);
        if (to >= 0) { focus = to; paint(); }
        return true;
    }

    /** The tile covering {row, column} (a wide one covers both columns), or -1. */
    private int find(int row, int col) {
        if (col < 0 || col > 1) return -1;
        for (int i = 0; i < at.size(); i++) {
            int[] p = at.get(i);
            if (p[0] == row && col >= p[1] && col < p[1] + p[2]) return i;
        }
        // Down onto a row with one tile: that one.
        for (int i = 0; i < at.size(); i++) if (at.get(i)[0] == row) return i;
        return -1;
    }

    private void paint() {
        for (int i = 0; i < tiles.size(); i++) {
            View t = tiles.get(i);
            boolean on = i == focus;
            Item it = t.getTag() instanceof Item ? (Item) t.getTag() : null;
            boolean lit = it != null && it.lit != null && Boolean.TRUE.equals(it.lit.get());
            if (it == null) {
                // The camera: a white ring when focused, along the tile's own rounded edge (the stroke sits half its
                // width inside the edge, so its corners are rounded by that much less to follow it).
                int w = px(4);
                GradientDrawable ring = new GradientDrawable();
                ring.setCornerRadius(Math.max(0, px(24) - w / 2f));
                if (on) ring.setStroke(w, Color.WHITE);
                ((FrameLayout) t).setForeground(ring);
                t.animate().scaleX(on ? 1.02f : 1f).scaleY(on ? 1.02f : 1f).setDuration(140).start();
                continue;
            }
            GradientDrawable bg = fill(22, on ? Color.WHITE : lit ? 0x669B5CFF : 0x33050838);
            if (!on) bg.setStroke(Math.max(1, px(1)), lit ? 0xB3C4A8FF : 0x26FFFFFF);
            t.setBackground(bg);
            int ink = on ? NAVY : Color.WHITE;
            ((ImageView) t.findViewWithTag("icon")).setColorFilter(on ? NAVY : lit ? 0xFFD9C4FF : Color.WHITE);
            ((TextView) t.findViewWithTag("name")).setTextColor(ink);
            View s = t.findViewWithTag("state");
            if (s != null) ((TextView) s).setTextColor(on ? 0xB310157E : lit ? 0xFFD9C4FF : 0xB3FFFFFF);
            t.animate().scaleX(on ? 1.04f : 1f).scaleY(on ? 1.04f : 1f).setDuration(140).start();
        }
    }
}
