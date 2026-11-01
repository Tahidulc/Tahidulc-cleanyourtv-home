package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Launcher v2's smart home panel, opened with OK on the top bar's light bulb: a navy card under the bar, in Home's
 * glass, with the cameras (OK: the live view in Tuya Smart; the last alarm's picture and time when there was one),
 * every light as a tile (OK switches it on or off: yellow when on, a dimmer gold when low, glass when off), the
 * brightness of the light last picked (Left and Right, 10% a step, sent once it stands still), and the car's charge and
 * a delivery on its way when the phone has passed them on. The lights' state comes from Tuya's cloud when the TV's
 * config.json has a Tuya project (TuyaCloud); without one each change goes to Google Assistant as a typed request, as
 * the lights button always did, and the panel shows what it last set. Back closes it.
 */
final class SmartPanel {
    private static final int NAVY = 0xF70E1450, YELLOW = 0xFFFFD66B, GOLD = 0xFF8A6A1F, GREEN = 0xFF3DDC84, RED = 0xFFE3061B;
    private static final long SETTLE_MS = 900L;

    /** The car's and a delivery's latest news from the phone (GoalWatchService), with when it came. */
    static volatile JSONObject car, delivery;
    static volatile long carAt, deliveryAt;

    /** One light as the panel shows it: from Tuya (t set), or a room for Google Assistant (state as last set). */
    private static final class Light {
        String name, room;
        TuyaCloud.Light t;
        boolean on, known, online = true;
        int level = -1;
        FrameLayout tile;
        TextView state;
        ImageView bulb;
        Switch toggle;
    }

    private final HomeV2 h;
    private FrameLayout overlay, card;
    private View dim;
    private LinearLayout lightBox, body;
    private TextView source, levelName, levelValue;
    private Slider slider;
    private boolean open;
    private final List<Light> lights = new ArrayList<>();
    private static List<TuyaCloud.Light> lastTuya;
    /** The rows the remote moves through: cameras, the lights three to a row, the brightness. */
    private final List<List<View>> rows = new ArrayList<>();
    private int row, col;
    private Light picked;

    SmartPanel(HomeV2 h) {
        this.h = h;
    }

    boolean isOpen() {
        return open;
    }

    // ---- open and close -------------------------------------------------------------------------------------------

    void show() {
        if (open) return;
        open = true;
        overlay = new FrameLayout(h.a);
        dim = new View(h.a);
        dim.setBackgroundColor(0x8C050816);
        dim.setAlpha(0f);
        overlay.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        card = new FrameLayout(h.a);
        GradientDrawable bg = h.fill(34, NAVY);
        bg.setStroke(Math.max(1, h.px(1.5f)), 0x2EFFFFFF);
        card.setBackground(bg);
        card.setClipToOutline(true);
        card.setOutlineProvider(h.round(34));
        card.setElevation(h.px(24));
        body = new LinearLayout(h.a);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(h.px(32), h.px(28), h.px(32), h.px(28));
        card.addView(body, new FrameLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(h.px(660), -2, Gravity.TOP | Gravity.END);
        cl.rightMargin = h.px(78);
        cl.topMargin = h.px(196);
        overlay.addView(card, cl);
        h.root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        build();
        dim.animate().alpha(1f).setDuration(260).start();
        card.setAlpha(0f);
        card.setScaleX(0.96f);
        card.setScaleY(0.96f);
        card.setTranslationY(-h.px(18));
        card.setPivotX(h.px(560));
        card.setPivotY(0);
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0).setDuration(320).setInterpolator(HomeV2.SKY).start();
        readTuya();
    }

    void hide() {
        if (!open) return;
        open = false;
        if (slider != null) slider.flush();
        FrameLayout o = overlay;
        overlay = null;
        dim.animate().alpha(0f).setDuration(220).start();
        card.animate().alpha(0f).scaleX(0.96f).scaleY(0.96f).translationY(-h.px(14)).setDuration(220)
                .setInterpolator(HomeV2.SKY).withEndAction(() -> h.root.removeView(o)).start();
    }

    // ---- the card -------------------------------------------------------------------------------------------------

    private void build() {
        rows.clear();
        // Home, and how to close it.
        LinearLayout head = new LinearLayout(h.a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = h.text(40, h.semi, Color.WHITE);
        title.setText("Home");
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView close = h.text(18, h.regular, 0xFFAEB5E8);
        close.setText("Back closes");
        head.addView(close);
        body.addView(head);

        cameras();

        // The lights.
        LinearLayout lh = new LinearLayout(h.a);
        lh.setGravity(Gravity.CENTER_VERTICAL);
        lh.addView(kicker("LIGHTS"), new LinearLayout.LayoutParams(0, -2, 1f));
        source = h.text(15, h.regular, 0x99FFFFFF);
        lh.addView(source);
        LinearLayout.LayoutParams lhl = new LinearLayout.LayoutParams(-1, -2);
        lhl.topMargin = h.px(26);
        body.addView(lh, lhl);
        lightBox = new LinearLayout(h.a);
        lightBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lbl = new LinearLayout.LayoutParams(-1, -2);
        lbl.topMargin = h.px(12);
        body.addView(lightBox, lbl);
        loadLights();

        // The brightness of the light picked.
        LinearLayout bh = new LinearLayout(h.a);
        bh.setGravity(Gravity.CENTER_VERTICAL);
        levelName = h.text(18, h.semi, Color.WHITE);
        bh.addView(levelName, new LinearLayout.LayoutParams(0, -2, 1f));
        levelValue = h.text(20, h.semi, YELLOW);
        bh.addView(levelValue);
        LinearLayout.LayoutParams bhl = new LinearLayout.LayoutParams(-1, -2);
        bhl.topMargin = h.px(22);
        body.addView(bh, bhl);
        slider = new Slider(h.a);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, h.px(44));
        sl.topMargin = h.px(6);
        body.addView(slider, sl);

        info();

        TextView hint = h.text(15, h.regular, 0x8CFFFFFF);
        hint.setText("OK switches a light on or off  ·  ◀ ▶ on the bar dims it");
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-2, -2);
        hl.topMargin = h.px(20);
        body.addView(hint, hl);

        fillLights();
        row = rows.size() > 1 && rows.get(0).size() > 0 && lightsRow() >= 0 ? lightsRow() : 0;
        col = 0;
        paint();
    }

    private TextView kicker(String s) {
        TextView k = h.text(15, h.semi, 0xA6FFFFFF);
        k.setLetterSpacing(0.2f);
        k.setText(s);
        return k;
    }

    /** The theme's cameras (Tuya Smart's), two to a row: the last alarm's picture under a red tag, else LIVE. */
    private void cameras() {
        if (!installed(CameraAlert.TUYA)) return;
        List<String> names = CameraAlert.cameras(h.a);
        if (names.isEmpty()) names = java.util.Collections.singletonList("Camera");
        LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(-2, -2);
        kl.topMargin = h.px(22);
        body.addView(kicker("CAMERAS"), kl);
        LinearLayout r = new LinearLayout(h.a);
        List<View> focus = new ArrayList<>();
        for (int i = 0; i < Math.min(2, names.size()); i++) {
            String name = names.get(i);
            LinearLayout cell = new LinearLayout(h.a);
            cell.setOrientation(LinearLayout.VERTICAL);
            FrameLayout tile = new FrameLayout(h.a);
            tile.setBackground(h.fill(20, 0xFF262B35));
            tile.setClipToOutline(true);
            tile.setOutlineProvider(h.round(20));
            Object[] shot = CameraAlert.lastShot(name);
            boolean recent = shot != null && System.currentTimeMillis() - (Long) shot[1] < 6 * 3_600_000L;
            if (recent && shot[0] != null) {
                ImageView pic = new ImageView(h.a);
                pic.setScaleType(ImageView.ScaleType.CENTER_CROP);
                pic.setImageBitmap((Bitmap) shot[0]);
                tile.addView(pic, new FrameLayout.LayoutParams(-1, -1));
            } else {
                ImageView cam = new ImageView(h.a);
                cam.setImageResource(R.drawable.ic_camera);
                cam.setColorFilter(0x59FFFFFF);
                tile.addView(cam, new FrameLayout.LayoutParams(h.px(54), h.px(54), Gravity.CENTER));
            }
            String tagText = recent ? "MOTION  " + new java.text.SimpleDateFormat("HH:mm", Locale.UK).format(new java.util.Date((Long) shot[1])) : "LIVE";
            TextView tag = h.text(13, h.semi, Color.WHITE);
            tag.setText(tagText);
            tag.setLetterSpacing(0.1f);
            tag.setPadding(h.px(9), h.px(4), h.px(9), h.px(4));
            tag.setBackground(h.fill(7, recent ? RED : 0x40FFFFFF));
            FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
            tl.leftMargin = h.px(10);
            tl.topMargin = h.px(10);
            tile.addView(tag, tl);
            tile.setTag(new Runnable() {
                @Override public void run() {
                    hide();
                    CameraAlert.live(h.a, "Camera".equals(name) ? null : name, null);
                }
            });
            cell.addView(tile, new LinearLayout.LayoutParams(-1, h.px(166)));
            TextView label = h.text(19, h.semi, Color.WHITE);
            label.setText(name.toLowerCase(Locale.ROOT).contains("camera") ? name : name + " camera");
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, -2);
            ll.topMargin = h.px(8);
            cell.addView(label, ll);
            LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) cellLp.leftMargin = h.px(14);
            r.addView(cell, cellLp);
            focus.add(tile);
        }
        if (names.size() == 1) r.addView(new View(h.a), new LinearLayout.LayoutParams(0, 1, 1f));
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
        rl.topMargin = h.px(12);
        body.addView(r, rl);
        rows.add(focus);
    }

    /** The car's charge, and a delivery on its way, when the phone has passed them on lately. */
    private void info() {
        JSONObject c = car;
        if (c != null && System.currentTimeMillis() - carAt < 12 * 3_600_000L) {
            int battery = c.optInt("battery", -1);
            String title = c.optString("title", "Tesla").trim(), status = c.optString("status", "").trim();
            LinearLayout box = infoBox();
            LinearLayout top = new LinearLayout(h.a);
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView t = h.text(19, h.semi, Color.WHITE);
            t.setText((title.isEmpty() ? "Tesla" : title) + (status.isEmpty() ? "" : " · " + status));
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            top.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            if (battery >= 0) {
                TextView b = h.text(22, h.semi, Color.WHITE);
                b.setText(battery + "%");
                top.addView(b);
            }
            box.addView(top);
            if (battery >= 0) {
                FrameLayout bar = new FrameLayout(h.a);
                bar.setBackground(h.fill(5, 0x24FFFFFF));
                View fillBar = new View(h.a);
                fillBar.setBackground(h.fill(5, GREEN));
                bar.addView(fillBar, new FrameLayout.LayoutParams(Math.round(h.px(560) * battery / 100f), -1));
                LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, h.px(10));
                bl.topMargin = h.px(10);
                box.addView(bar, bl);
            }
        }
        JSONObject d = delivery;
        if (d != null && System.currentTimeMillis() - deliveryAt < 3 * 3_600_000L && d.optInt("stage", 0) < 4) {
            LinearLayout box = infoBox();
            TextView t = h.text(19, h.semi, Color.WHITE);
            String from = d.optString("from", "").trim();
            t.setText("Delivery" + (from.isEmpty() ? "" : " · " + from));
            box.addView(t);
            String status = d.optString("status", "").trim(), eta = d.optString("eta", "").trim();
            TextView s = h.text(16, h.regular, 0xFFC9CEF2);
            s.setText(status + (eta.isEmpty() ? "" : (status.isEmpty() ? "" : " · ") + "arriving " + eta));
            s.setSingleLine(true);
            s.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
            sl.topMargin = h.px(4);
            box.addView(s, sl);
        }
    }

    private LinearLayout infoBox() {
        LinearLayout box = new LinearLayout(h.a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(h.px(18), h.px(14), h.px(18), h.px(14));
        box.setBackground(h.fill(18, 0x12FFFFFF));
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
        l.topMargin = h.px(14);
        body.addView(box, l);
        return box;
    }

    // ---- the lights -----------------------------------------------------------------------------------------------

    /** Tuya's lights as last read (shown at once), else the theme's rooms for Google Assistant with what was last set. */
    private void loadLights() {
        lights.clear();
        List<TuyaCloud.Light> t = lastTuya;
        if (t != null && TuyaCloud.configured(h.a)) {
            for (TuyaCloud.Light x : t) lights.add(fromTuya(x));
            source.setText("Tuya");
            return;
        }
        SharedPreferences p = prefs();
        List<String> rooms = Config.themeStrings(h.a, "lights_rooms", java.util.Arrays.asList(h.a.getResources().getStringArray(R.array.lights_rooms)));
        for (String room : rooms) {
            Light l = new Light();
            l.room = room;
            l.name = capitalise(room);
            l.known = p.contains(room + ".on");
            l.on = p.getBoolean(room + ".on", false);
            l.level = p.getInt(room + ".level", 50);
            lights.add(l);
        }
        source.setText(TuyaCloud.configured(h.a) ? "Reading Tuya…" : "Google Assistant");
    }

    private Light fromTuya(TuyaCloud.Light x) {
        Light l = new Light();
        l.t = x;
        l.name = x.name;
        l.on = x.on;
        l.known = true;
        l.online = x.online;
        l.level = x.level;
        return l;
    }

    /** Tuya's lights afresh, in the background; the tiles redrawn when they come (the focus kept). */
    private void readTuya() {
        if (!TuyaCloud.configured(h.a)) return;
        h.io.execute(() -> {
            List<TuyaCloud.Light> got;
            try {
                got = TuyaCloud.lights(h.a);
            } catch (Exception e) {
                got = null;
            }
            List<TuyaCloud.Light> read = got;
            h.main.post(() -> {
                if (!open) return;
                if (read == null) {
                    source.setText(lastTuya == null ? "Tuya not reachable" : "Tuya · not refreshed");
                    return;
                }
                lastTuya = read;
                String pickedName = picked == null ? null : picked.name;
                lights.clear();
                for (TuyaCloud.Light x : read) lights.add(fromTuya(x));
                source.setText("Tuya · " + read.size() + (read.size() == 1 ? " light" : " lights"));
                int lr = lightsRow();
                int at = row >= lr && lr >= 0 ? (row - lr) * 3 + col : -1;
                fillLights();
                for (Light l : lights) if (l.name.equals(pickedName)) picked = l;
                if (at >= 0) {
                    int n = Math.min(at, lights.size() - 1);
                    row = lightsRow() + Math.max(0, n) / 3;
                    col = Math.max(0, n) % 3;
                }
                paint();
            });
        });
    }

    /** The light tiles, three to a row, and the remote's rows for them. */
    private void fillLights() {
        int lr = lightsRow();
        // Drop the old light rows and the slider row; the cameras' row stays.
        while (rows.size() > (lr < 0 ? rowsBeforeLights() : lr)) rows.remove(rows.size() - 1);
        lightBox.removeAllViews();
        if (lights.isEmpty()) {
            TextView none = h.text(18, h.regular, 0xB3FFFFFF);
            none.setText("No lights found");
            lightBox.addView(none);
        }
        LinearLayout r = null;
        List<View> focus = null;
        for (int i = 0; i < lights.size(); i++) {
            if (i % 3 == 0) {
                r = new LinearLayout(h.a);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, h.px(128));
                if (i > 0) rl.topMargin = h.px(12);
                lightBox.addView(r, rl);
                focus = new ArrayList<>();
                rows.add(focus);
            }
            Light l = lights.get(i);
            l.tile = tile(l);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -1, 1f);
            if (i % 3 > 0) tl.leftMargin = h.px(12);
            r.addView(l.tile, tl);
            focus.add(l.tile);
        }
        // Fill out the last row so the tiles keep their width.
        if (r != null) for (int i = lights.size() % 3; i > 0 && i < 3; i++) {
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -1, 1f);
            tl.leftMargin = h.px(12);
            r.addView(new View(h.a), tl);
        }
        List<View> sliderRow = new ArrayList<>();
        sliderRow.add(slider);
        rows.add(sliderRow);
        if (picked == null || !lights.contains(picked)) picked = lights.isEmpty() ? null : lights.get(0);
        showLevel();
    }

    private int camerasRows() {
        return installed(CameraAlert.TUYA) ? 1 : 0;
    }

    private int rowsBeforeLights() {
        return camerasRows();
    }

    /** The first row of light tiles; -1 when there are none yet. */
    private int lightsRow() {
        return lights.isEmpty() ? -1 : camerasRows();
    }

    private FrameLayout tile(Light l) {
        FrameLayout t = new FrameLayout(h.a);
        t.setClipToOutline(true);
        t.setOutlineProvider(h.round(20));
        LinearLayout col = new LinearLayout(h.a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(h.px(16), h.px(14), h.px(12), h.px(12));
        // The bulb, and the switch across from it: plain to see that OK turns it on or off.
        LinearLayout top = new LinearLayout(h.a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        l.bulb = new ImageView(h.a);
        l.bulb.setImageResource(R.drawable.ic_bulb);
        top.addView(l.bulb, new LinearLayout.LayoutParams(h.px(28), h.px(28)));
        top.addView(new View(h.a), new LinearLayout.LayoutParams(0, 1, 1f));
        l.toggle = new Switch(h.a);
        top.addView(l.toggle, new LinearLayout.LayoutParams(h.px(50), h.px(28)));
        col.addView(top, new LinearLayout.LayoutParams(-1, -2));
        TextView name = h.text(19, h.semi, Color.WHITE);
        name.setText(l.name);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
        nl.topMargin = h.px(8);
        col.addView(name, nl);
        l.state = h.text(16, h.regular, Color.WHITE);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
        sl.topMargin = h.px(3);
        col.addView(l.state, sl);
        t.addView(col, new FrameLayout.LayoutParams(-1, -1));
        t.setTag(l);
        l.tile = t;
        look(l);
        return t;
    }

    /** A tile's colours and words for its light: yellow on, a dimmer gold when low, glass off, faded offline. */
    private void look(Light l) {
        if (l.tile == null) return;
        boolean low = l.on && l.level > 0 && l.level < 35;
        int ink = l.on && !low ? 0xFF2A1F00 : Color.WHITE;
        GradientDrawable bg = h.fill(20, !l.online ? 0x0AFFFFFF : l.on ? (low ? GOLD : YELLOW) : 0x1AFFFFFF);
        if (!l.on) bg.setStroke(Math.max(1, h.px(1.5f)), 0x47FFFFFF);
        l.tile.setBackground(bg);
        LinearLayout col = (LinearLayout) l.tile.getChildAt(0);
        ((TextView) col.getChildAt(1)).setTextColor(ink);
        l.state.setTextColor(l.on && !low ? 0xCC2A1F00 : 0xFFC9CEF2);
        l.bulb.setColorFilter(l.on ? (low ? YELLOW : 0xFF2A1F00) : 0x80FFFFFF);
        String s;
        if (!l.online) s = "Offline";
        else if (!l.known) s = "Press OK";
        else if (!l.on) s = "Off";
        else s = l.level > 0 ? "On · " + l.level + "%" : "On";
        l.state.setText(s);
        l.toggle.set(l.on && l.online, low);
        l.tile.setAlpha(l.online ? 1f : 0.55f);
    }

    private void showLevel() {
        if (picked == null) {
            levelName.setText("Brightness");
            levelValue.setText("");
            slider.set(50, false);
            return;
        }
        boolean dims = picked.t == null || picked.t.brightCode != null;
        levelName.setText(picked.name + " · Brightness");
        int lv = picked.level > 0 ? picked.level : 50;
        levelValue.setText(!dims ? "On / off only" : picked.known && !picked.on ? "Off" : lv + "%");
        slider.set(lv, dims);
    }

    /** OK on a tile: the light switched the other way, shown at once, then sent (and Tuya's answer shown). */
    private void toggle(Light l) {
        if (!l.online) return;
        boolean to = !(l.known && l.on);
        l.on = to;
        l.known = true;
        look(l);
        picked = l;
        showLevel();
        if (l.t != null) {
            h.io.execute(() -> {
                boolean ok = TuyaCloud.power(h.a, l.t, to);
                if (ok) settle(l);
                else h.main.post(() -> { l.on = !to; look(l); showLevel(); source.setText("Tuya didn't take that"); });
            });
        } else {
            remember(l);
            LightsPanel.send(h.a, null, l.tile, (to ? "turn on the " : "turn off the ") + l.room + " lights");
        }
    }

    /** The brightness, once the slider stands still: to Tuya, or asked of Google Assistant. */
    private void setLevel(int level) {
        Light l = picked;
        if (l == null || !l.online) return;
        l.level = level;
        l.on = true;
        l.known = true;
        look(l);
        showLevel();
        if (l.t != null) {
            h.io.execute(() -> {
                boolean ok = TuyaCloud.level(h.a, l.t, level);
                if (ok) settle(l);
                else h.main.post(() -> source.setText("Tuya didn't take that"));
            });
        } else {
            remember(l);
            LightsPanel.send(h.a, null, slider, String.format(Locale.ROOT, "set the %s lights to %d%%", l.room, level));
        }
    }

    /** Tuya's own reading of the light a moment after a change, so the tile shows what the light really did. */
    private void settle(Light l) {
        try { Thread.sleep(1200); } catch (InterruptedException e) { return; }
        try { TuyaCloud.refresh(h.a, l.t); } catch (Exception ignored) { return; }
        h.main.post(() -> {
            l.on = l.t.on;
            l.level = l.t.level;
            l.online = l.t.online;
            look(l);
            if (picked == l) showLevel();
        });
    }

    private void remember(Light l) {
        prefs().edit().putBoolean(l.room + ".on", l.on).putInt(l.room + ".level", l.level > 0 ? l.level : 50).apply();
    }

    private SharedPreferences prefs() {
        return h.a.getSharedPreferences("smart_lights", Context.MODE_PRIVATE);
    }

    // ---- the remote -----------------------------------------------------------------------------------------------

    /** Back closes; the arrows move through the tiles (on the bar Left and Right dim); OK presses. */
    boolean key(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (e.getAction() == KeyEvent.ACTION_UP) hide();
            return true;
        }
        if (code == KeyEvent.KEYCODE_HOME) return false;
        if (rows.isEmpty()) return true;
        View on = current();
        boolean onSlider = on == slider;
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
            if (e.getAction() != KeyEvent.ACTION_UP) return true;
            if (onSlider) slider.flush();
            else if (on != null && on.getTag() instanceof Light) toggle((Light) on.getTag());
            else if (on != null && on.getTag() instanceof Runnable) ((Runnable) on.getTag()).run();
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (row > 0) { row--; col = Math.min(col, rows.get(row).size() - 1); }
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (row < rows.size() - 1) { row++; col = Math.min(col, rows.get(row).size() - 1); }
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (onSlider) slider.step(-10);
                else if (col > 0) col--;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (onSlider) slider.step(10);
                else if (col < rows.get(row).size() - 1) col++;
                break;
            default:
                return true;
        }
        View now = current();
        if (now != null && now.getTag() instanceof Light) { picked = (Light) now.getTag(); showLevel(); }
        paint();
        return true;
    }

    private View current() {
        if (row < 0 || row >= rows.size()) return null;
        List<View> r = rows.get(row);
        if (r.isEmpty()) return null;
        col = Math.max(0, Math.min(col, r.size() - 1));
        return r.get(col);
    }

    /** The focus: a white ring on the tile, a little larger; the bar draws its own. */
    private void paint() {
        View on = current();
        for (List<View> r : rows) {
            for (View v : r) {
                boolean f = v == on;
                if (v == slider) { slider.focus(f); continue; }
                ((FrameLayout) v).setForeground(f ? h.ring(20) : null);
                v.animate().scaleX(f ? 1.05f : 1f).scaleY(f ? 1.05f : 1f).setDuration(150).setInterpolator(HomeV2.SKY).start();
                v.setTranslationZ(0);
            }
        }
    }

    private boolean installed(String pkg) {
        try {
            h.a.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String capitalise(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** A tile's on/off switch: a dark track with the knob to the right when on, faint glass with it to the left off. */
    private final class Switch extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean on, low;

        Switch(Context c) {
            super(c);
        }

        void set(boolean on, boolean low) {
            this.on = on;
            this.low = low;
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), ht = getHeight(), r = ht / 2f, knob = r - h.px(4);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(on ? (low ? 0x66000000 : 0xFF2A1F00) : 0x33FFFFFF);
            c.drawRoundRect(new RectF(0, 0, w, ht), r, r, paint);
            paint.setColor(on ? (low ? YELLOW : 0xFFFFD66B) : Color.WHITE);
            c.drawCircle(on ? w - r : r, r, knob, paint);
        }
    }

    /**
     * The brightness bar: the level in the lights' yellow along a glass track, a white knob; Left and Right move it 10%
     * at a time (10% to 100%), the value showing as it moves, and the change sent once it has stood still a moment.
     */
    private final class Slider extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int level = 50;
        private boolean focused, enabled = true, moved;
        private final Runnable send = this::flush;

        Slider(Context c) {
            super(c);
        }

        void set(int l, boolean dims) {
            level = Math.max(10, Math.min(100, l));
            enabled = dims;
            moved = false;
            invalidate();
        }

        void focus(boolean f) {
            focused = f;
            invalidate();
        }

        void step(int by) {
            if (!enabled || picked == null) return;
            int next = Math.max(10, Math.min(100, level + by));
            if (next == level) return;
            level = next;
            moved = true;
            levelValue.setText(level + "%");
            invalidate();
            h.main.removeCallbacks(send);
            h.main.postDelayed(send, SETTLE_MS);
        }

        /** The level now, if it moved since it was last sent. */
        void flush() {
            h.main.removeCallbacks(send);
            if (!moved) return;
            moved = false;
            setLevel(level);
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), ht = getHeight();
            float knob = h.px(focused ? 15 : 12);
            float track = h.px(10), left = knob + h.px(2), right = w - knob - h.px(2), cy = ht / 2f;
            float x = left + (right - left) * (level - 10) / 90f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x24FFFFFF);
            c.drawRoundRect(new RectF(left, cy - track / 2, right, cy + track / 2), track / 2, track / 2, paint);
            paint.setColor(enabled ? YELLOW : 0x40FFFFFF);
            c.drawRoundRect(new RectF(left, cy - track / 2, x, cy + track / 2), track / 2, track / 2, paint);
            paint.setColor(0x66000000);
            c.drawCircle(x, cy + h.px(2), knob, paint);
            paint.setColor(Color.WHITE);
            c.drawCircle(x, cy, knob, paint);
            if (focused) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(h.px(3));
                paint.setColor(YELLOW);
                c.drawCircle(x, cy, knob + h.px(4), paint);
            }
        }
    }
}
