package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.gathin.tvhomephone.home.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * A club's own page, opened from a table's row or a game's crest, as the family's picture has it: on the club's own
 * colour (deep enough for white words, with fine grain and a vignette), Players and Management at the top left and the
 * lines (All, Goalkeepers, Defenders, Midfielders, Forwards) at the top right; the squad as a carousel, the player in
 * the middle large with his photo rising above his card, his number on a coin, his first name small and his surname
 * huge, his line up the side, smaller and dimmer cards either side. Swipe, tap a neighbour, tap the middle card for the
 * player's page. Under it, the club's form and games, its season, FotMob's figures and its leading players (5.18's).
 */
public class TeamActivity extends AppCompatActivity {
    private static final String SLUG = "slug", ID = "id", NAME = "name", LOGO = "logo";
    private static final String KEPT_KEY = "focusKey", KEPT_LINES = "lines", KEPT_STAFF = "staff";

    private String slug, id, nameGiven, logoGiven;
    private TeamData data;
    private boolean fotmobRead;
    private boolean management;
    private int lines;
    /** The player in the middle (name and number), kept through a new reading and a turn of the phone. */
    private String focusKey;
    private List<TeamData.Player> shown = new ArrayList<>();
    private int colour = ClubLook.NAVY;

    private FrameLayout root;
    private ScrollView scroll;
    private LinearLayout col;
    private HeaderRows head;
    private ImageView crest;
    private View crestDisc;
    private TextView titleName, titleLine, playersPill, staffPill;
    private final TextView[] linePills = new TextView[SquadFilter.PILLS.length];
    private FrameLayout stage;
    private SquadCarousel carousel;
    private TextView note, more;
    private LinearLayout sections;
    private ClubSections sectionMaker;
    private int barTop, barBottom, barLeft, barRight;
    private ActivityResultLauncher<Intent> playerPage;

    public static Intent intent(Context c, String slug, String id, String name, String logo) {
        return new Intent(c, TeamActivity.class).putExtra(SLUG, slug).putExtra(ID, id).putExtra(NAME, name).putExtra(LOGO, logo);
    }

    /** A tablet's type a little larger, as the tabs' (Wide); a phone's as it is. */
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(com.gathin.tvhomephone.glass.Wide.wrap(base));
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent in = getIntent();
        slug = in.getStringExtra(SLUG);
        id = in.getStringExtra(ID);
        if (slug == null || id == null || id.isEmpty()) {
            finish();
            return;
        }
        nameGiven = in.getStringExtra(NAME);
        logoGiven = in.getStringExtra(LOGO);
        if (state != null) {
            focusKey = state.getString(KEPT_KEY);
            lines = state.getInt(KEPT_LINES);
            management = state.getBoolean(KEPT_STAFF);
        }
        playerPage = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
            Intent back = r.getData();
            String key = back == null ? null : back.getStringExtra(PlayerPageActivity.SHOWN_KEY);
            if (key == null || carousel == null) return;
            focusKey = key;
            int at = SquadFilter.indexOf(shown, key);
            if (at >= 0) carousel.jump(at);
        });
        build();
        edgeToEdge();

        TeamData kept = TeamData.kept(slug, id);
        if (kept == null) {
            TeamData last = TeamData.lastShown(slug, id);
            if (last != null && last.done) kept = last;
        }
        if (kept != null) {
            data = kept;
            fotmobRead = true;
            paint();
            return;
        }
        data = new TeamData();
        data.slug = slug;
        data.id = id;
        data.name = nameGiven == null ? "" : nameGiven;
        data.shortName = data.name;
        data.logo = logoGiven == null ? "" : logoGiven;
        paint();
        TeamData.read(this, slug, id, nameGiven, logoGiven, (t, done) -> {
            if (isFinishing() || isDestroyed()) return;
            if (!done && data.fotmob) return;
            if (t != null) data = t;
            if (done) {
                fotmobRead = true;
                data.done = true;
            }
            paint();
        });
    }

    @Override protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(KEPT_KEY, focusKey);
        out.putInt(KEPT_LINES, lines);
        out.putBoolean(KEPT_STAFF, management);
    }

    // ---- the page's parts ----------------------------------------------------------------------------------------

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void build() {
        root = new FrameLayout(this);
        root.setBackground(new PageArt.Ground(colour, getResources().getDisplayMetrics().density, 0.5f, 0.42f));
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        head = new HeaderRows(this, title());
        playersPill = HeaderRows.pill(this, "Players", ClubLook.ground(colour));
        staffPill = HeaderRows.pill(this, "Management", ClubLook.ground(colour));
        playersPill.setOnClickListener(v -> pickStaff(false));
        staffPill.setOnClickListener(v -> pickStaff(true));
        head.left.addView(playersPill, HeaderRows.spaced(this, true));
        head.left.addView(staffPill, HeaderRows.spaced(this, false));
        for (int i = 0; i < SquadFilter.PILLS.length; i++) {
            TextView p = HeaderRows.pill(this, SquadFilter.PILL_NAMES[i], ClubLook.ground(colour));
            int pill = SquadFilter.PILLS[i];
            p.setOnClickListener(v -> pickLine(pill));
            linePills[i] = p;
            head.rightRow.addView(p, HeaderRows.spaced(this, i == 0));
        }
        col.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        stage = new FrameLayout(this);
        stage.setClipChildren(false);
        carousel = new SquadCarousel(this);
        carousel.setListener(new SquadCarousel.Listener() {
            @Override public void open(int index) {
                openPlayer(index);
            }

            @Override public void settled(int index) {
                if (index >= 0 && index < shown.size()) focusKey = SquadFilter.key(shown.get(index));
            }
        });
        stage.addView(carousel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        note = text("", 15, 0xB3FFFFFF, Type.inter(this));
        note.setGravity(Gravity.CENTER);
        stage.addView(note, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        col.addView(stage, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(420)));

        more = text("THE SEASON  ⌄", 11, 0xCCFFFFFF, Type.outfit(this, 600));
        more.setLetterSpacing(0.16f);
        more.setGravity(Gravity.CENTER);
        more.setMinHeight(dp(34));
        more.setOnClickListener(v -> scroll.smoothScrollTo(0, sections.getTop() - dp(12)));
        more.setContentDescription("The club's season, further down");
        col.addView(more, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        sections = new LinearLayout(this);
        sections.setOrientation(LinearLayout.VERTICAL);
        sectionMaker = new ClubSections(this);
        col.addView(sections, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);

        // The stage fills what the screen leaves under the head (no more than its cards need), whatever the size.
        scroll.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (b - t != ob - ot || r - l != or - ol) v.post(this::sizeStage);
        });
        head.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (b - t != ob - ot) v.post(this::sizeStage);
        });
    }

    /** The title row: Back, the crest on a white disc, the club's name and its standing. */
    private View title() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 32, 0xFFFFFFFF, Type.outfit(this, 500));
        back.setGravity(Gravity.CENTER);
        back.setIncludeFontPadding(false);
        back.setOnClickListener(v -> finish());
        back.setContentDescription("Back");
        r.addView(back, new LinearLayout.LayoutParams(dp(40), dp(48)));
        FrameLayout disc = new FrameLayout(this);
        crestDisc = disc;
        disc.setBackground(Ui.fill(this, 0xFFFFFFFF, 18));
        crest = new ImageView(this);
        crest.setScaleType(ImageView.ScaleType.FIT_CENTER);
        crest.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        disc.addView(crest, new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));
        r.addView(disc, new LinearLayout.LayoutParams(dp(36), dp(36)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        titleName = text("", 18, 0xFFFFFFFF, Type.outfit(this, 700));
        titleName.setSingleLine(true);
        titleName.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(titleName);
        titleLine = text("", 12, 0xB8FFFFFF, Type.inter(this));
        titleLine.setSingleLine(true);
        titleLine.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(titleLine);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        wp.leftMargin = dp(10);
        r.addView(words, wp);
        return r;
    }

    private TextView text(String s, float sp, int colour, android.graphics.Typeface face) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(colour);
        t.setTypeface(face);
        return t;
    }

    /** Edge to edge: the club's colour under the status bar, everything else clear of the bars and any cut-out. */
    private void edgeToEdge() {
        Window w = getWindow();
        WindowCompat.setDecorFitsSystemWindows(w, false);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat ic = WindowCompat.getInsetsController(w, root);
        ic.setAppearanceLightStatusBars(false);
        ic.setAppearanceLightNavigationBars(false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, ins) -> {
            Insets s = ins.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            barTop = s.top;
            barBottom = s.bottom;
            barLeft = s.left;
            barRight = s.right;
            pad();
            return WindowInsetsCompat.CONSUMED;
        });
        pad();
    }

    /** The page's margins: the bars, and on a wide screen room either side of the sections. */
    private void pad() {
        int side = dp(16);
        head.setPadding(barLeft + dp(6), barTop + dp(6), barRight + side, dp(4));
        int widthPx = getResources().getDisplayMetrics().widthPixels;
        int max = dp(1100);
        int extra = Math.max(0, (widthPx - barLeft - barRight - 2 * side - max) / 2);
        sections.setPadding(barLeft + side + extra, dp(6), barRight + side + extra, barBottom + dp(28));
        stage.setPadding(barLeft, 0, barRight, 0);
        sizeStage();
    }

    /** The stage's height: what the screen leaves under the head and the season's tab, never more than its cards need. */
    private void sizeStage() {
        if (scroll == null || scroll.getHeight() == 0 || head.getHeight() == 0) return;
        float density = getResources().getDisplayMetrics().density;
        int avail = scroll.getHeight() - head.getHeight() - more.getHeight() - barBottom;
        int width = scroll.getWidth() - barLeft - barRight;
        int want;
        if (shown.isEmpty()) {
            want = dp(220);
        } else {
            float wDp = width / density;
            float share = wDp < 600 ? 0.56f : wDp < 1000 ? 0.30f : 0.25f;
            float p = Math.min(avail / 2.12f, width * share);
            want = Math.round(Math.max(dp(250), Math.min(avail, p * 2.12f + dp(14))));
        }
        ViewGroup.LayoutParams lp = stage.getLayoutParams();
        if (lp.height != want) {
            lp.height = want;
            stage.setLayoutParams(lp);
        }
    }

    // ---- painting --------------------------------------------------------------------------------------------------

    private void paint() {
        int c = ClubLook.pick(TeamData.colour(data.color, 0), TeamData.colour(data.alternate, 0));
        if (c != colour) recolour(c);
        titleName.setText(data.name);
        titleLine.setText(standing());
        crestDisc.setVisibility(data.logo.isEmpty() ? View.GONE : View.VISIBLE);
        if (!data.logo.isEmpty()) Ui.load(crest, data.logo, null, () -> crestDisc.setVisibility(View.GONE));
        boolean staff = SquadFilter.hasStaff(data.squad);
        if (!staff && management) management = false;
        staffPill.setVisibility(staff ? View.VISIBLE : View.GONE);
        lightPills();
        // the cards' club: its league's own pictures of its players, and its crest for one there is no picture of
        carousel.setClub(data.slug, data.name, data.shortName, data.logo);
        showSquad(false);
        sectionMaker.fill(sections, data, fotmobRead, getResources().getConfiguration().screenWidthDp >= 720);
        more.setVisibility(sections.getChildCount() > 0 ? View.VISIBLE : View.INVISIBLE);
        TeamData.shown(data);
        sizeStage();
    }

    /** The club's colour on the ground (faded across from the last), the pills' ink and the cards. */
    private void recolour(int c) {
        int old = colour;
        colour = c;
        int g = ClubLook.ground(c);
        float density = getResources().getDisplayMetrics().density;
        Drawable now = new PageArt.Ground(c, density, 0.5f, 0.42f);
        if (old == ClubLook.NAVY && data.color.isEmpty()) {
            root.setBackground(now);
        } else {
            TransitionDrawable fade = new TransitionDrawable(new Drawable[]{root.getBackground(), now});
            fade.setCrossFadeEnabled(true);
            root.setBackground(fade);
            fade.startTransition(320);
        }
        for (TextView p : allPills()) p.setTag(g);
        lightPills();
        carousel.setColours(c, g);
    }

    private List<TextView> allPills() {
        List<TextView> out = new ArrayList<>();
        out.add(playersPill);
        out.add(staffPill);
        for (TextView t : linePills) out.add(t);
        return out;
    }

    private String standing() {
        if (!data.standing.isEmpty()) return data.standing;
        if (data.rank > 0) return TeamData.ordinal(data.rank) + " in the " + Espn.name(data.slug);
        return Espn.name(data.slug);
    }

    private void lightPills() {
        HeaderRows.light(playersPill, !management);
        HeaderRows.light(staffPill, management);
        for (int i = 0; i < linePills.length; i++) HeaderRows.light(linePills[i], SquadFilter.lit(lines, SquadFilter.PILLS[i]));
        head.right.setVisibility(management ? View.INVISIBLE : View.VISIBLE);
    }

    /** The carousel for the pills now, the same player kept in the middle when he is still shown (fade: cross-fade). */
    private void showSquad(boolean fade) {
        List<TeamData.Player> next = SquadFilter.shown(data.squad, management, lines);
        int at = focusKey == null ? -1 : SquadFilter.indexOf(next, focusKey);
        if (at < 0 && !shown.isEmpty() && focusKey != null) {
            int was = SquadFilter.indexOf(shown, focusKey);
            at = SquadFilter.keep(shown, was, next);
        }
        if (at < 0) at = 0;
        boolean same = sameList(shown, next);
        if (data.squad.isEmpty()) {
            note.setText(fotmobRead ? "No squad listed for this club" : "Reading the squad…");
            note.setVisibility(View.VISIBLE);
        } else {
            note.setVisibility(next.isEmpty() ? View.VISIBLE : View.GONE);
            note.setText(management ? "No manager listed" : "No players in these lines");
        }
        if (same) return;
        List<TeamData.Player> list = next;
        int middle = at;
        Runnable swap = () -> {
            shown = list;
            carousel.setPlayers(list, middle);
            if (!list.isEmpty()) focusKey = SquadFilter.key(list.get(middle));
            sizeStage();
        };
        if (fade && carousel.count() > 0) {
            carousel.animate().cancel();
            carousel.animate().alpha(0f).setDuration(120).withEndAction(() -> {
                swap.run();
                carousel.animate().alpha(1f).setDuration(180).start();
            }).start();
        } else {
            swap.run();
        }
    }

    private static boolean sameList(List<TeamData.Player> a, List<TeamData.Player> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (a.get(i) != b.get(i)) return false;
        return true;
    }

    private void pickStaff(boolean staff) {
        if (staff == management) return;
        management = staff;
        lightPills();
        showSquad(true);
    }

    private void pickLine(int pill) {
        int next = SquadFilter.tap(lines, pill);
        if (next == lines && !management) return;
        lines = next;
        management = false;
        lightPills();
        showSquad(true);
    }

    private void openPlayer(int index) {
        if (index < 0 || index >= shown.size()) return;
        focusKey = SquadFilter.key(shown.get(index));
        TeamData.shown(data);
        playerPage.launch(PlayerPageActivity.intent(this, slug, id, data.name, data.logo, management, lines, focusKey));
    }
}
