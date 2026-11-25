package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The helper's way through Debrid Stream to a title's page (AppSearchService's search for a Debrid Stream card), as pure
 * decisions on what is on its screen, so they can be tried against a made-up screen: the profile it asks for (the one it
 * highlights, with OK), its search (by name or by id), the title typed in (as written, then its other spellings while
 * nothing found matches), the keyboard put away, then the film or series of that name picked from the results (in the
 * row for its kind first, the same name before one that only begins the same).
 *
 * <p>Nothing here waits a set time: the service takes the next step the moment Debrid Stream's screen changes (its
 * accessibility events), with a look every SAFETY_MS besides as a safety net; the gaps below only keep the same thing
 * from being done twice while its screen is on its way. Brought to the front as it was left (it was running: its first
 * screen there within WARM_MS), a page with neither its search nor its profiles is left by starting it afresh (RESTART)
 * after WARM_GIVE_MS, as every search once began; never by Back, which could leave the app. Started afresh on purpose
 * (fresh: the helper was off when OK was pressed), its first screens are waited for as a cold start's.
 */
final class DebridSteps {
    /** A node of Debrid Stream's screen (AppSearchService wraps Android's; the tests make their own). */
    interface Node {
        CharSequence text();

        CharSequence desc();

        String id();

        boolean editable();

        boolean focused();

        int count();

        Node child(int i);
    }

    static final int WAIT = 0, PROFILE = 1, OPEN_SEARCH = 2, TYPE = 3, HIDE_KEYBOARD = 4, PICK = 5, RESTART = 6, PAGE = 7, NOT_FOUND = 8;

    /** One step: what to do, on which node (null: none, or the remote's OK for PROFILE), with what words (TYPE). */
    static final class Step {
        final int what;
        final Node node;
        final String text;

        Step(int what, Node node, String text) {
            this.what = what;
            this.node = node;
            this.text = text;
        }
    }

    static final Step WAITING = new Step(WAIT, null, null);

    /** The safety net's look, between Debrid Stream's own screen changes. */
    static final long SAFETY_MS = 600L;
    /** Its first screen this soon after it was asked to open: it was running, brought to the front as it was. */
    static final long WARM_MS = 700L;
    /**
     * Brought to the front on a page with neither its search nor its profiles: started afresh once it has been there this
     * long unchanged (a moment for the page to finish drawing; looked at again just then, not at the next safety look).
     */
    static final long WARM_GIVE_MS = 300L;
    /** A click on its search, or OK on a profile, not made again before its next screen has had this long to come. */
    static final long SEARCH_AGAIN_MS = 1_200L, PROFILE_AGAIN_MS = 1_000L;
    /** Nothing of that name this long after it was typed: its next spelling; none after NOT_FOUND_MS: the results stay. */
    static final long RESPELL_MS = 3_500L, NOT_FOUND_MS = 8_000L;

    final String query;
    final boolean wantTv;
    final List<String> spellings;
    final long launchedAt;
    /** Started afresh on purpose (the helper was off: it can't tell what it would have been brought back to). */
    final boolean fresh;
    int spelling;
    long typedAt, searchAt, profileAt, firstSeenAt;
    boolean opened, warm, restarted;

    DebridSteps(String query, boolean wantTv, long launchedAt, boolean fresh) {
        this.query = query == null ? "" : query;
        this.wantTv = wantTv;
        this.spellings = AppSearchService.spellings(this.query);
        this.launchedAt = launchedAt;
        this.fresh = fresh;
    }

    /**
     * How soon to look again if its screen doesn't change meanwhile: the safety net's SAFETY_MS, or just as a page it
     * was brought back to has had its WARM_GIVE_MS.
     */
    long lookAgainIn(long now) {
        if (warm && !restarted && searchAt == 0 && typedAt == 0 && profileAt == 0 && firstSeenAt > 0) {
            long due = firstSeenAt + WARM_GIVE_MS + 1 - now;
            if (due > 0) return Math.min(SAFETY_MS, due);
        }
        return SAFETY_MS;
    }

    /** The words typed now: the spelling being tried. */
    String typing() {
        return spelling < spellings.size() ? spellings.get(spelling) : query;
    }

    /** The service typed them (the field took the words). */
    void typed(long now) {
        typedAt = now;
    }

    /** Started afresh (RESTART done): its first screen as a cold start's from now. */
    void restarted(long now) {
        restarted = true;
        warm = false;
        firstSeenAt = now;
    }

    /**
     * The next step for Debrid Stream's screen now (root: its window; keyboard: the on-screen keyboard is up). PAGE once
     * the picked title's page is up (the search gone); NOT_FOUND when nothing of that name came up in time.
     */
    Step next(Node root, boolean keyboard, long now) {
        if (firstSeenAt == 0) {
            firstSeenAt = now;
            warm = !fresh && !restarted && now - launchedAt < WARM_MS;
        }
        Node box = find(root, EDIT, 0);
        if (opened) return box == null ? new Step(PAGE, null, null) : WAITING;
        if (box != null) {
            if (typedAt == 0) return new Step(TYPE, box, typing());
            // The app shows results as the words go in, but its field brings up the keyboard over them: Back takes the
            // keyboard away (only while it shows, so Back never leaves the search).
            if (keyboard) return new Step(HIDE_KEYBOARD, null, null);
            Node hit = result(root);
            if (hit != null) {
                opened = true;
                return new Step(PICK, hit, null);
            }
            long waited = now - typedAt;
            if (waited > RESPELL_MS && spelling + 1 < spellings.size()) {
                spelling++;
                typedAt = 0;
                return new Step(TYPE, box, typing());
            }
            return waited < NOT_FOUND_MS ? WAITING : new Step(NOT_FOUND, null, null);
        }
        Node search = find(root, SEARCH, 0);
        if (search != null) {
            if (searchAt != 0 && now - searchAt <= SEARCH_AGAIN_MS) return WAITING;
            searchAt = now;
            return new Step(OPEN_SEARCH, search, null);
        }
        if (find(root, PROFILE_PAGE, 0) != null) {
            // The profile picker (each time the app starts afresh): the profile it highlights is the one last used.
            if (profileAt != 0 && now - profileAt <= PROFILE_AGAIN_MS) return WAITING;
            profileAt = now;
            return new Step(PROFILE, find(root, FOCUSED, 0), null);
        }
        // Brought to the front on a page with neither (a title's page, its player): started afresh, as every search was.
        if (warm && !restarted && now - firstSeenAt > WARM_GIVE_MS) return new Step(RESTART, null, null);
        return WAITING;
    }

    // ---- reading the screen ------------------------------------------------------------------------------------------

    static final int EDIT = 0, SEARCH = 1, PROFILE_PAGE = 2, FOCUSED = 3;

    /** EDIT: a text field. SEARCH: what is named or labelled search. PROFILE_PAGE: a profile picker's title. FOCUSED. */
    static Node find(Node n, int kind, int depth) {
        if (n == null || depth > 40) return null;
        String text = AppSearchService.plain(n.text()), desc = AppSearchService.plain(n.desc());
        String id = n.id() == null ? "" : n.id().toLowerCase(Locale.ROOT);
        boolean hit = kind == EDIT ? n.editable() : kind == SEARCH ? AppSearchService.isSearch(text, desc, id)
                : kind == PROFILE_PAGE ? AppSearchService.isProfilePage(text) || AppSearchService.isProfilePage(desc) : n.focused();
        if (hit) return n;
        for (int i = 0; i < n.count(); i++) {
            Node h = find(n.child(i), kind, depth + 1);
            if (h != null) return h;
        }
        return null;
    }

    /**
     * Among the results (posters named by their description, under "Movies" and "TV Shows"), the one with the title
     * searched for: in the row for its kind first, the same name before one that only begins the same.
     */
    Node result(Node root) {
        List<Node> all = new ArrayList<>();
        collect(root, all, 0);
        String want = letters(query);
        if (want.isEmpty()) return null;
        boolean inTv = false;
        Node[] best = new Node[4];   // exact in kind, exact, close in kind, close
        for (Node n : all) {
            CharSequence text = n.text();
            if (text != null && !n.editable()) {
                String t = text.toString().trim().toLowerCase(Locale.ROOT);
                if (t.equals("tv shows") || t.equals("series") || t.equals("tv")) inTv = true;
                else if (t.equals("movies") || t.equals("films")) inTv = false;
            }
            CharSequence desc = n.desc();
            if (desc == null) continue;
            String d = letters(desc.toString());
            if (d.isEmpty()) continue;
            boolean kind = inTv == wantTv;
            int slot = d.equals(want) ? (kind ? 0 : 1) : (d.startsWith(want) || want.startsWith(d)) && Math.min(d.length(), want.length()) >= 4 ? (kind ? 2 : 3) : -1;
            if (slot >= 0 && best[slot] == null) best[slot] = n;
        }
        for (Node n : best) if (n != null) return n;
        return null;
    }

    private static void collect(Node n, List<Node> into, int depth) {
        if (n == null || depth > 40) return;
        into.add(n);
        for (int i = 0; i < n.count(); i++) collect(n.child(i), into, depth + 1);
    }

    /** A title to compare: lower case, letters and digits only. */
    static String letters(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
