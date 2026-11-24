package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * The dock's order as the family set it on this TV with Edit dock (launcher v2's dock layout, and pop-up mode's dock):
 * its apps in order, All apps among them, then its sections in order. Kept in the launcher's own prefs (dock_order),
 * on this TV only (the house's sync carries no Home layout). With nothing saved the dock is the theme's dock_apps, All
 * apps after them, then the sections in their usual order; Reset to default goes back to that, and the saved order is
 * kept even when a theme later changes dock_apps.
 *
 * <p>An app that is no longer installed is left out of the dock but keeps its place in the saved order, so it comes
 * back where it was if it is installed again; an app removed with Edit dock leaves the saved order. TV Guide and Apps
 * stay in the dock (the way into the channels, and the way to every app on the TV, an app taken off the dock among
 * them), as does All apps; they can be moved. The other sections can be taken off and put back. Pure but for the prefs.
 */
final class DockOrder {
    /** All apps' place among the dock's apps (a package name never starts with '*'). */
    static final String ALL = "*all";
    /** The sections (HomeV2's CAT_NAMES), and their usual order along the dock: Kids between Sports and Apps. */
    static final int GUIDE = 0, APPS = 5, KIDS = 6, SECTION_COUNT = 7;
    static final int[] DEFAULT_SECTIONS = {0, 1, 2, 3, 4, KIDS, APPS};
    /** Most apps the dock holds (All apps included), whatever room there is. */
    static final int MOST_APPS = 16;

    private static final String MAGIC = "tvhome-dock 1", APPS_LINE = "apps ", SECTIONS_LINE = "sections ";
    private static final String PREFS = "dock_order", KEY = "order";

    /** Packages and ALL, in order, no repeats. */
    final List<String> apps;
    /** Section numbers in order, no repeats; a section not here is off the dock. */
    final List<Integer> sections;

    DockOrder(List<String> apps, List<Integer> sections) {
        this.apps = new ArrayList<>(apps);
        this.sections = new ArrayList<>(sections);
    }

    DockOrder copy() {
        return new DockOrder(apps, sections);
    }

    /** The dock as it comes: dock_apps (blanks and repeats left out), All apps, the sections in their usual order. */
    static DockOrder defaults(List<String> dockApps) {
        List<String> a = new ArrayList<>();
        for (String p : dockApps == null ? Collections.<String>emptyList() : dockApps) {
            String t = p == null ? "" : p.trim();
            if (!t.isEmpty() && !t.startsWith("*") && !t.contains(",") && !a.contains(t)) a.add(t);
        }
        a.add(ALL);
        List<Integer> s = new ArrayList<>();
        for (int i : DEFAULT_SECTIONS) s.add(i);
        return new DockOrder(a, s);
    }

    /** The packages alone, in order (All apps left out). */
    List<String> packages() {
        List<String> out = new ArrayList<>();
        for (String p : apps) if (!ALL.equals(p)) out.add(p);
        return out;
    }

    int[] sectionArray() {
        int[] out = new int[sections.size()];
        for (int i = 0; i < out.length; i++) out[i] = sections.get(i);
        return out;
    }

    /** What the dock shows: the apps installed on this TV, and All apps, in order. */
    List<String> shown(Predicate<String> installed) {
        List<String> out = new ArrayList<>();
        for (String p : apps) if (ALL.equals(p) || installed.test(p)) out.add(p);
        return out;
    }

    /** Whether it is the dock as it comes for these dock_apps. */
    boolean isDefault(List<String> dockApps) {
        return equals(defaults(dockApps));
    }

    @Override public boolean equals(Object o) {
        return o instanceof DockOrder && ((DockOrder) o).apps.equals(apps) && ((DockOrder) o).sections.equals(sections);
    }

    @Override public int hashCode() {
        return apps.hashCode() * 31 + sections.hashCode();
    }

    // ---- editing (Edit dock works on what the dock shows) ----------------------------------------------------------

    /** The app at place `at` moved one place (dir -1 left, 1 right) among the apps; its place now. */
    int moveApp(int at, int dir) {
        return move(apps, at, dir);
    }

    /** The section at place `at` moved one place among the sections; its place now. */
    int moveSection(int at, int dir) {
        return move(sections, at, dir);
    }

    private static <T> int move(List<T> list, int at, int dir) {
        if (at < 0 || at >= list.size()) return at;
        int to = Math.max(0, Math.min(list.size() - 1, at + Integer.signum(dir)));
        if (to != at) Collections.swap(list, at, to);
        return to;
    }

    /** Whether the app at `at` may be taken off the dock: any but All apps. */
    boolean appRemovable(int at) {
        return at >= 0 && at < apps.size() && !ALL.equals(apps.get(at));
    }

    /** Takes the app at `at` off the dock; false for All apps. */
    boolean removeApp(int at) {
        if (!appRemovable(at)) return false;
        apps.remove(at);
        return true;
    }

    /** Whether section `which` may be taken off the dock: any but TV Guide and Apps, which navigation needs. */
    static boolean sectionRemovable(int which) {
        return which != GUIDE && which != APPS && which >= 0 && which < SECTION_COUNT;
    }

    boolean removeSection(int at) {
        if (at < 0 || at >= sections.size() || !sectionRemovable(sections.get(at))) return false;
        sections.remove(at);
        return true;
    }

    /** Whether one more app fits along the dock (the screen's width, with these sections). */
    boolean roomForApp() {
        return apps.size() < maxApps(sections.size());
    }

    /**
     * Puts pkg on the dock after the app at place `after` (-1: first; past the end: before All apps when that is last);
     * its place, or -1 when it is already on the dock (no repeats), blank, or the dock is full.
     */
    int addApp(String pkg, int after) {
        if (pkg == null || pkg.trim().isEmpty() || pkg.startsWith("*") || pkg.contains(",") || apps.contains(pkg) || !roomForApp()) return -1;
        int at;
        if (after >= apps.size() || after < -1) {
            int all = apps.indexOf(ALL);
            at = all == apps.size() - 1 ? all : apps.size();
        } else {
            at = after + 1;
        }
        apps.add(at, pkg);
        return at;
    }

    /** The sections off the dock, in their usual order. */
    List<Integer> offSections() {
        List<Integer> out = new ArrayList<>();
        for (int i : DEFAULT_SECTIONS) if (!sections.contains(i)) out.add(i);
        return out;
    }

    /** Puts a section back on the dock, at its usual place among those there; its place, or -1 if it is there already. */
    int addSection(int which) {
        if (which < 0 || which >= SECTION_COUNT || sections.contains(which)) return -1;
        int rank = rank(which), at = sections.size();
        for (int i = 0; i < sections.size(); i++) {
            if (rank(sections.get(i)) > rank) { at = i; break; }
        }
        sections.add(at, which);
        return at;
    }

    private static int rank(int which) {
        for (int i = 0; i < DEFAULT_SECTIONS.length; i++) if (DEFAULT_SECTIONS[i] == which) return i;
        return DEFAULT_SECTIONS.length;
    }

    /**
     * The order to keep after an edit: the dock as edited (what it showed), with the apps that were in the order before
     * but are not installed now put back after the one they followed (or first), so a reinstalled app returns to its
     * place. Apps the edit removed are not among those: they were installed, and shown.
     */
    static DockOrder keepMissing(DockOrder edited, DockOrder before, Predicate<String> installed) {
        List<String> out = new ArrayList<>(edited.apps);
        if (before != null) {
            for (int i = 0; i < before.apps.size(); i++) {
                String p = before.apps.get(i);
                if (ALL.equals(p) || installed.test(p) || out.contains(p)) continue;
                int at = 0;
                for (int j = i - 1; j >= 0; j--) {
                    int k = out.indexOf(before.apps.get(j));
                    if (k >= 0) { at = k + 1; break; }
                }
                out.add(at, p);
            }
        }
        return new DockOrder(out, edited.sections);
    }

    // ---- the dock's measures (HomeV2.layoutDock's, design px) -------------------------------------------------------

    static final int EDGE = 28, APP = 64, SEC = 112, GAP = 14, RULE = 2, END = 20;
    /** The room kept either side of the dock on a 1920-wide screen. */
    static final int MARGIN = 40;

    /** The dock's width with n apps (All apps included) and this many sections. */
    static int width(int n, int sections) {
        return EDGE + n * APP + Math.max(0, n - 1) * GAP + EDGE + RULE + GAP + sections * SEC + Math.max(0, sections - 1) * GAP + END;
    }

    /** Most apps (All apps included) that fit along the screen beside this many sections, at most MOST_APPS. */
    static int maxApps(int sections) {
        int n = 1;
        while (n < MOST_APPS && width(n + 1, sections) <= 1920 - 2 * MARGIN) n++;
        return n;
    }

    // ---- kept as text ---------------------------------------------------------------------------------------------

    static String encode(DockOrder o) {
        StringBuilder s = new StringBuilder(MAGIC).append('\n').append(APPS_LINE).append(String.join(",", o.apps)).append('\n')
                .append(SECTIONS_LINE);
        for (int i = 0; i < o.sections.size(); i++) s.append(i > 0 ? "," : "").append(o.sections.get(i));
        return s.append('\n').toString();
    }

    /**
     * The order in this text, or null when it is not one. Repeats are dropped, All apps added at the end when missing,
     * unknown sections dropped, and TV Guide and Apps put back if missing (so a damaged file never strands the dock).
     */
    static DockOrder decode(String text) {
        if (text == null) return null;
        String[] lines = text.split("\n");
        if (lines.length < 3 || !MAGIC.equals(lines[0]) || !lines[1].startsWith(APPS_LINE) || !lines[2].startsWith(SECTIONS_LINE)) return null;
        List<String> apps = new ArrayList<>();
        for (String p : lines[1].substring(APPS_LINE.length()).split(",")) {
            String t = p.trim();
            if (t.isEmpty() || apps.contains(t) || (t.startsWith("*") && !ALL.equals(t))) continue;
            apps.add(t);
        }
        if (!apps.contains(ALL)) apps.add(ALL);
        List<Integer> secs = new ArrayList<>();
        for (String p : lines[2].substring(SECTIONS_LINE.length()).split(",")) {
            try {
                int v = Integer.parseInt(p.trim());
                if (v >= 0 && v < SECTION_COUNT && !secs.contains(v)) secs.add(v);
            } catch (NumberFormatException ignored) {
                // not a section
            }
        }
        DockOrder o = new DockOrder(apps, secs);
        for (int must : Arrays.asList(GUIDE, APPS)) o.addSection(must);
        return o;
    }

    // ---- this TV's prefs ------------------------------------------------------------------------------------------

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The order saved on this TV, or null. */
    static DockOrder saved(Context c) {
        return decode(prefs(c).getString(KEY, null));
    }

    /** The dock now: the saved order, else the theme's dock_apps as they come. */
    static DockOrder current(Context c, List<String> dockApps) {
        DockOrder o = saved(c);
        return o != null ? o : defaults(dockApps);
    }

    /** Kept on this TV; the dock as it comes is kept as nothing saved, so it follows the theme's dock_apps again. */
    static void save(Context c, DockOrder o, List<String> dockApps) {
        if (o == null || o.isDefault(dockApps)) prefs(c).edit().remove(KEY).apply();
        else prefs(c).edit().putString(KEY, encode(o)).apply();
    }
}
