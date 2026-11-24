package com.gathin.broadcast;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure: the scoreboard editor's working copy, the same on the phone (touch) and the TV (the remote): the spec being
 * edited, the colour slot picked, and every change the editor makes, each one leaving a board that can be drawn: a base
 * picked (its own colours, or the colours kept), a colour from the swatches, the clubs' own, a hex or the hue and
 * brightness sliders, the finish, typeface and crests stepped, the corners and border, the strips and the accent line,
 * and the contrast mended in one go. Changes can be undone.
 */
public final class BoardEditing {
    /** The curated swatches every slot offers (deep grounds, light ones, accents), broadcast colours. */
    public static final int[] SWATCHES = {
            0xFF0B1640, 0xFF0A0F24, 0xFF14161C, 0xFF050608, 0xFF2A2F3A, 0xFF9AA5BA, 0xFFE3E7EE, 0xFFF5F6FA,
            0xFF2443B4, 0xFF2EC5FF, 0xFF0F8B8D, 0xFF1E7A3C, 0xFF7DDA58, 0xFFF5C518, 0xFFF28C28, 0xFFD7192F,
            0xFF7A1F3D, 0xFF4B1E78, 0xFFE0407B, 0xFFF1E7D0};

    private BoardSpec spec;
    private final List<String> undo = new ArrayList<>();
    /** The two clubs' colours (0: not known), offered with the swatches. */
    public int home, away;
    /** The colour slot picked (BoardSpec.EDITABLE). */
    public String slot = "panel";

    public BoardEditing(BoardSpec start) {
        spec = (start == null ? new BoardSpec() : start).copy();
    }

    /** The board as it stands (a copy, safe to keep). */
    public BoardSpec spec() {
        return spec.copy();
    }

    /** The board as it stands, not copied (for drawing it now). */
    public BoardSpec live() {
        return spec;
    }

    private void keep() {
        undo.add(spec.toJson());
        if (undo.size() > 40) undo.remove(0);
    }

    private void done() {
        spec.validated();
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public void undo() {
        if (undo.isEmpty()) return;
        spec = BoardSpec.parse(undo.remove(undo.size() - 1));
    }

    /** A whole board taken in (a suggestion, Claude's design), its name kept unless it has one. */
    public void load(BoardSpec s) {
        keep();
        spec = s.copy();
        done();
    }

    public void name(String n) {
        spec.name = n;
        done();
    }

    /** A base picked: with its own colours (fresh) or the colours as they are on the new layout. */
    public void base(BoardSpec.Base b, boolean fresh) {
        keep();
        if (fresh) {
            String n = spec.name;
            spec = BoardSpec.of(b);
            spec.name = n;
        } else {
            spec.base = b;
        }
        done();
    }

    public void stepBase(int step) {
        BoardSpec.Base[] all = BoardSpec.Base.values();
        base(all[Math.floorMod(spec.base.ordinal() + step, all.length)], false);
    }

    /** The slot's colour set (opaque from the swatches; a see-through cell keeps its see-through). */
    public void colour(int c) {
        keep();
        int now = spec.colour(slot);
        int a = BoardColours.alpha(now);
        boolean cell = "score".equals(slot) || "clock".equals(slot) || "border".equals(slot);
        spec.colour(slot, cell && a < 0xFF && a > 0 ? BoardColours.withAlpha(c, a) : BoardColours.opaque(c));
        done();
    }

    /** The slot's colour from "#RRGGBB" (false, unchanged, when it can't be read). */
    public boolean hex(String h) {
        if (!BoardColours.valid(h)) return false;
        keep();
        spec.colour(slot, BoardColours.parse(h, spec.colour(slot)));
        done();
        return true;
    }

    /** {hue 0-360, saturation 0-1, brightness 0-1} of the slot's colour, for the sliders. */
    public float[] hsv() {
        return BoardColours.hsv(spec.colour(slot));
    }

    /** The slot's colour from the sliders (its see-through kept). */
    public void hsv(float h, float s, float v) {
        keep();
        int now = spec.colour(slot);
        spec.colour(slot, BoardColours.withAlpha(BoardColours.fromHsv(h, s, v), Math.max(1, BoardColours.alpha(now))));
        done();
    }

    /** The remote's sliders: the hue on by degrees, the brightness by steps of 0.05 (the saturation when sat). */
    public void nudge(float hueDeg, float value, float sat) {
        float[] v = hsv();
        float s = v[1];
        // A grey given a hue gets some colour to show it.
        if (hueDeg != 0 && s < 0.05f) s = 0.5f;
        hsv(v[0] + hueDeg, clamp(s + sat), clamp(v[2] + value));
    }

    private static float clamp(float f) {
        return Math.max(0f, Math.min(1f, f));
    }

    public void stepFinish(int step) {
        keep();
        BoardSpec.Finish[] all = BoardSpec.Finish.values();
        spec.finish = all[Math.floorMod(spec.finish.ordinal() + step, all.length)];
        done();
    }

    public void finish(BoardSpec.Finish f) {
        keep();
        spec.finish = f;
        done();
    }

    public void stepFont(int step) {
        keep();
        BoardSpec.Font[] all = BoardSpec.Font.values();
        spec.font = all[Math.floorMod(spec.font.ordinal() + step, all.length)];
        done();
    }

    public void font(BoardSpec.Font f) {
        keep();
        spec.font = f;
        done();
    }

    public void stepCrest(int step) {
        keep();
        BoardSpec.Crest[] all = BoardSpec.Crest.values();
        spec.crest = all[Math.floorMod(spec.crest.ordinal() + step, all.length)];
        done();
    }

    public void crest(BoardSpec.Crest c) {
        keep();
        spec.crest = c;
        done();
    }

    /** The corners: 0 (square) to 0.5 (a pill). */
    public void radius(float r) {
        keep();
        spec.radius = r;
        done();
    }

    public void stepRadius(int step) {
        radius(Math.round((spec.radius + step * 0.04f) * 100) / 100f);
    }

    /** The border's width: 0 (none) to 0.1 of the height. */
    public void border(float w) {
        keep();
        spec.borderWidth = w;
        done();
    }

    public void stepBorder(int step) {
        border(Math.round((spec.borderWidth + step * 0.01f) * 1000) / 1000f);
    }

    public void strips(boolean on) {
        keep();
        spec.strips = on;
        done();
    }

    public void accentLine(boolean on) {
        keep();
        spec.accentLine = on;
        done();
    }

    /** Every word made readable at the generator's mark (4.5:1), the score cell and strips seen. */
    public void mendContrast(BoardDesigner.Brief b) {
        keep();
        spec = BoardDesigner.rules(spec, b);
    }

    /** What reads badly now ("" when all reads well), for the editor's line. */
    public String warning(BoardDesigner.Brief b) {
        List<String> p = BoardDesigner.problems(spec, b);
        if (p.isEmpty()) return "";
        return "Hard to read on a TV: " + String.join(", ", p) + ".";
    }

    /** The swatches for the slot: the two clubs' colours first (when known), then the curated ones. */
    public int[] swatches() {
        List<Integer> l = new ArrayList<>();
        if (home != 0) l.add(BoardColours.opaque(home));
        if (away != 0 && away != home) l.add(BoardColours.opaque(away));
        for (int s : SWATCHES) l.add(s);
        int[] out = new int[l.size()];
        for (int i = 0; i < out.length; i++) out[i] = l.get(i);
        return out;
    }

    /** The slot after (or before) this one, along BoardSpec.EDITABLE. */
    public void stepSlot(int step) {
        String[] all = BoardSpec.EDITABLE;
        int i = 0;
        for (int k = 0; k < all.length; k++) if (all[k].equals(slot)) i = k;
        slot = all[Math.floorMod(i + step, all.length)];
    }
}
