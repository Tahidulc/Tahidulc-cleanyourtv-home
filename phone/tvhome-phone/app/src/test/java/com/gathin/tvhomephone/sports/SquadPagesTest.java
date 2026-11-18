package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The club page's squad carousel and the player's page: the pills' filter, the cards' arithmetic (place, size,
 * brightness, where a swipe or fling settles, what a tap and a key do), the page's colours, a name's two parts, and
 * the player's facts and figures (only what a source gave), read from invented ESPN and FotMob replies.
 */
public class SquadPagesTest {
    private static final float EPS = 1e-4f;

    // ---- the pills -----------------------------------------------------------------------------------------------

    @Test public void linePillsAddAndTakeOffAndAllClears() {
        assertEquals(SquadFilter.DEFENDERS, SquadFilter.tap(0, SquadFilter.DEFENDERS));
        assertEquals(SquadFilter.DEFENDERS | SquadFilter.FORWARDS, SquadFilter.tap(SquadFilter.DEFENDERS, SquadFilter.FORWARDS));
        assertEquals(0, SquadFilter.tap(SquadFilter.DEFENDERS, SquadFilter.DEFENDERS));
        assertEquals(0, SquadFilter.tap(SquadFilter.DEFENDERS | SquadFilter.FORWARDS, 0));
        // all four lines lit is All
        int three = SquadFilter.GOALKEEPERS | SquadFilter.DEFENDERS | SquadFilter.MIDFIELDERS;
        assertEquals(0, SquadFilter.tap(three, SquadFilter.FORWARDS));
        assertTrue(SquadFilter.lit(0, 0));
        assertFalse(SquadFilter.lit(SquadFilter.DEFENDERS, 0));
        assertTrue(SquadFilter.lit(SquadFilter.DEFENDERS, SquadFilter.DEFENDERS));
        assertFalse(SquadFilter.lit(SquadFilter.DEFENDERS, SquadFilter.FORWARDS));
    }

    private static TeamData.Player player(String name, String number, String line) {
        TeamData.Player p = new TeamData.Player();
        p.name = name;
        p.number = number;
        p.line = line;
        return p;
    }

    private static List<TeamData.Group> squad() {
        List<TeamData.Group> s = new ArrayList<>();
        String[][] people = {{"Manager", "Ola Brenn", ""}, {"Goalkeepers", "Ivo Kern", "1"}, {"Defenders", "Ari Lund", "2"},
                {"Defenders", "Bo Marsh", "5"}, {"Midfielders", "Cal Nye", "8"}, {"Forwards", "Dev Orr", "9"}, {"Forwards", "Eli Pask", "11"}};
        TeamData.Group g = null;
        for (String[] p : people) {
            if (g == null || !g.title.equals(p[0])) {
                g = new TeamData.Group(p[0]);
                s.add(g);
            }
            g.players.add(player(p[1], p[2], p[0]));
        }
        return s;
    }

    private static List<String> names(List<TeamData.Player> l) {
        List<String> out = new ArrayList<>();
        for (TeamData.Player p : l) out.add(p.name);
        return out;
    }

    @Test public void theCarouselShowsTheLinesLitInTheSquadsOrder() {
        List<TeamData.Group> s = squad();
        assertEquals(Arrays.asList("Ivo Kern", "Ari Lund", "Bo Marsh", "Cal Nye", "Dev Orr", "Eli Pask"), names(SquadFilter.shown(s, false, 0)));
        assertEquals(Arrays.asList("Ari Lund", "Bo Marsh", "Dev Orr", "Eli Pask"),
                names(SquadFilter.shown(s, false, SquadFilter.FORWARDS | SquadFilter.DEFENDERS)));
        assertEquals(Arrays.asList("Ola Brenn"), names(SquadFilter.shown(s, true, SquadFilter.FORWARDS)));
        assertTrue(SquadFilter.hasStaff(s));
        assertFalse(SquadFilter.hasStaff(s.subList(1, s.size())));
    }

    @Test public void theMiddleCardKeepsItsPlayerOrTheNextOneShown() {
        List<TeamData.Group> s = squad();
        List<TeamData.Player> all = SquadFilter.shown(s, false, 0);
        List<TeamData.Player> forwards = SquadFilter.shown(s, false, SquadFilter.FORWARDS);
        // Eli Pask stays in the middle
        assertEquals(1, SquadFilter.keep(all, 5, forwards));
        // Bo Marsh is not a forward: the next one after him that is (Dev Orr)
        assertEquals(0, SquadFilter.keep(all, 2, forwards));
        // nobody after him: the nearest before
        List<TeamData.Player> keepers = SquadFilter.shown(s, false, SquadFilter.GOALKEEPERS);
        assertEquals(0, SquadFilter.keep(all, 4, keepers));
        assertEquals(0, SquadFilter.keep(all, 3, new ArrayList<>()));
        // the same player read again (a new object) is found by name and number
        assertEquals(4, SquadFilter.indexOf(all, player("Dev Orr", "9", "Forwards")));
        assertEquals(4, SquadFilter.indexOf(all, SquadFilter.key(all.get(4))));
        assertEquals(-1, SquadFilter.indexOf(all, "Nobody#0"));
    }

    @Test public void aNameInTheCardsTwoParts() {
        assertArrayEquals(new String[]{"Virgil", "van Dijk"}, SquadFilter.parts("Virgil van Dijk", "", ""));
        assertArrayEquals(new String[]{"Kevin", "De Bruyne"}, SquadFilter.parts("Kevin De Bruyne", null, null));
        assertArrayEquals(new String[]{"", "Rodri"}, SquadFilter.parts("Rodri", "", ""));
        assertArrayEquals(new String[]{"", ""}, SquadFilter.parts("", "", ""));
        // ESPN's parts, in any order, when they make up the name
        assertArrayEquals(new String[]{"Min-jae", "Kim"}, SquadFilter.parts("Kim Min-jae", "Min-jae", "Kim"));
        assertArrayEquals(new String[]{"José", "Sá"}, SquadFilter.parts("Jose Sa", "José", "Sá"));
        // parts that are not the name shown are not used
        assertArrayEquals(new String[]{"Bruno", "Fernandes"}, SquadFilter.parts("Bruno Fernandes", "Bruno Miguel", "Borges Fernandes"));
    }

    @Test public void theCardsPhotosBestFirst() {
        TeamData.Player p = player("Ari Lund", "2", "Defenders");
        assertEquals(0, SquadFilter.photos(p).length);
        p.photo = "https://images.fotmob.com/image_resources/playerimages/1.png";
        p.cutout = "https://a.espncdn.com/i/headshots/soccer/players/full/1.png";
        assertArrayEquals(new String[]{p.cutout, p.photo}, SquadFilter.photos(p));
        assertEquals(SquadFilter.SHOULDERS, SquadFilter.kind(p.cutout));
        assertEquals(SquadFilter.HEAD, SquadFilter.kind(p.photo));
        assertEquals(SquadFilter.DRAWN, SquadFilter.kind(""));
    }

    // ---- the carousel's arithmetic ---------------------------------------------------------------------------------

    @Test public void cardsGetSmallerAndDimmerAwayFromTheMiddle() {
        assertEquals(1f, CarouselMath.scale(0), EPS);
        assertEquals(0.7f, CarouselMath.scale(1), EPS);
        assertEquals(0.7f, CarouselMath.scale(-1), EPS);
        assertEquals(0.55f, CarouselMath.scale(2), EPS);
        assertTrue(CarouselMath.scale(0.5f) < 1f && CarouselMath.scale(0.5f) > 0.7f);
        assertEquals(1f, CarouselMath.alpha(0), EPS);
        assertTrue(CarouselMath.alpha(1) < 1f);
        assertTrue(CarouselMath.alpha(2) < CarouselMath.alpha(1));
        assertTrue(CarouselMath.alpha(3) < CarouselMath.alpha(2));
        assertEquals(0f, CarouselMath.alpha(3.5f), EPS);
        assertEquals(1f, CarouselMath.focus(0), EPS);
        assertEquals(0f, CarouselMath.focus(1.2f), EPS);
        assertEquals(-CarouselMath.offset(1.5f, 100, 70, 50), CarouselMath.offset(-1.5f, 100, 70, 50), EPS);
        assertEquals(100f, CarouselMath.offset(1, 100, 70, 50), EPS);
        assertEquals(170f, CarouselMath.offset(2, 100, 70, 50), EPS);
        assertEquals(220f, CarouselMath.offset(3, 100, 70, 50), EPS);
    }

    @Test public void aSwipeSnapsAndAFlingCarriesOn() {
        // a slow let-go settles on the nearest card
        assertEquals(3, CarouselMath.flingTarget(3.3f, 0.2f, 3, 1f, 10));
        assertEquals(4, CarouselMath.flingTarget(3.6f, 0f, 3, 1f, 10));
        // a fling carries on at least a card from where the drag began, more when faster
        assertEquals(4, CarouselMath.flingTarget(3.1f, 2f, 3, 1f, 10));
        assertEquals(2, CarouselMath.flingTarget(2.9f, -2f, 3, 1f, 10));
        assertTrue(CarouselMath.flingTarget(3.1f, 20f, 3, 1f, 10) >= 6);
        // never past the ends
        assertEquals(9, CarouselMath.flingTarget(8.8f, 40f, 8, 1f, 10));
        assertEquals(0, CarouselMath.flingTarget(0.2f, -40f, 1, 1f, 10));
        assertEquals(0, CarouselMath.flingTarget(0f, 5f, 0, 1f, 0));
        // past the ends a drag gives only a little
        assertEquals(4f, CarouselMath.rubber(4f, 10), EPS);
        assertTrue(CarouselMath.rubber(-3f, 10) > -0.5f && CarouselMath.rubber(-3f, 10) < 0f);
        assertTrue(CarouselMath.rubber(12f, 10) < 9.5f && CarouselMath.rubber(12f, 10) > 9f);
        assertEquals(0, CarouselMath.nearest(-0.4f, 5));
        assertEquals(4, CarouselMath.nearest(7f, 5));
        assertTrue(CarouselMath.settleMs(1) > 0 && CarouselMath.settleMs(1) < CarouselMath.settleMs(4));
        assertTrue(CarouselMath.settleMs(30) <= 560);
        assertEquals(0, CarouselMath.settleMs(0));
    }

    @Test public void aTapOpensTheMiddleCardAndBringsAnyOtherToIt() {
        assertEquals(CarouselMath.OPEN, CarouselMath.tap(4, 4f, false, 10));
        assertEquals(CarouselMath.MOVE, CarouselMath.tap(5, 4f, false, 10));
        // the middle one while it is still moving only comes to rest
        assertEquals(CarouselMath.MOVE, CarouselMath.tap(4, 4f, true, 10));
        assertEquals(CarouselMath.MOVE, CarouselMath.tap(4, 3.6f, false, 10));
        assertEquals(CarouselMath.NONE, CarouselMath.tap(12, 4f, false, 10));
        assertEquals(CarouselMath.NONE, CarouselMath.tap(-1, 4f, false, 10));
    }

    @Test public void keysMoveTheCarouselAndEnterOpens() {
        assertEquals(3, CarouselMath.keyTarget(4, KeyEvent.KEYCODE_DPAD_LEFT, 10));
        assertEquals(5, CarouselMath.keyTarget(4, KeyEvent.KEYCODE_DPAD_RIGHT, 10));
        assertEquals(0, CarouselMath.keyTarget(0, KeyEvent.KEYCODE_DPAD_LEFT, 10));
        assertEquals(9, CarouselMath.keyTarget(9, KeyEvent.KEYCODE_DPAD_RIGHT, 10));
        assertEquals(0, CarouselMath.keyTarget(6, KeyEvent.KEYCODE_MOVE_HOME, 10));
        assertEquals(9, CarouselMath.keyTarget(6, KeyEvent.KEYCODE_MOVE_END, 10));
        assertEquals(-1, CarouselMath.keyTarget(6, KeyEvent.KEYCODE_DPAD_UP, 10));
        assertEquals(-1, CarouselMath.keyTarget(0, KeyEvent.KEYCODE_DPAD_RIGHT, 0));
        assertTrue(CarouselMath.opens(KeyEvent.KEYCODE_ENTER));
        assertTrue(CarouselMath.opens(KeyEvent.KEYCODE_DPAD_CENTER));
        assertFalse(CarouselMath.opens(KeyEvent.KEYCODE_DPAD_LEFT));
    }

    @Test public void aPageSwipeGoesOnePageAtMost() {
        // slow: the nearest page
        assertEquals(3, CarouselMath.pageTarget(3.3f, 0f, 3, 0.5f, 10));
        assertEquals(4, CarouselMath.pageTarget(3.6f, 0f, 3, 0.5f, 10));
        // a fling: on to the next page that way, even from a short drag, and never two
        assertEquals(4, CarouselMath.pageTarget(3.1f, 3f, 3, 0.5f, 10));
        assertEquals(2, CarouselMath.pageTarget(2.9f, -3f, 3, 0.5f, 10));
        assertEquals(4, CarouselMath.pageTarget(4f, 30f, 3, 0.5f, 10));
        // the ends
        assertEquals(0, CarouselMath.pageTarget(-0.2f, -3f, 0, 0.5f, 10));
        assertEquals(9, CarouselMath.pageTarget(9.2f, 3f, 9, 0.5f, 10));
        assertEquals(0, CarouselMath.pageTarget(0f, 0f, 0, 0.5f, 0));
    }

    @Test public void theWideLayoutOnItsSideAndOnTablets() {
        assertFalse(PageBits.wide(411, 891));
        assertTrue(PageBits.wide(891, 411));
        assertTrue(PageBits.wide(600, 960));
        assertTrue(PageBits.wide(800, 1280));
        assertTrue(PageBits.wide(1280, 800));
    }

    // ---- the colours -------------------------------------------------------------------------------------------------

    @Test public void thePageIsOnTheClubsColourDeepEnoughForWhiteWords() {
        int[] clubs = {0xFF034694, 0xFFC8102E, 0xFFFDB913, 0xFF6CABDD, 0xFFFFFFFF, 0xFF000000, 0xFF27F4D2, 0xFF7A263A, 0xFFA7A5A6};
        for (int c : clubs) {
            int g = ClubLook.ground(c);
            assertTrue(Integer.toHexString(c), ClubLook.contrast(0xFFFFFFFF, g) >= 7.0);
            int coin = ClubLook.coin(c);
            assertTrue(Integer.toHexString(c), ClubLook.contrast(ClubLook.coinInk(coin), coin) >= 3.0);
        }
        // a white club's page takes its other colour; none known is navy
        assertEquals(0xFF7A263A, ClubLook.pick(0xFFFFFFFF, 0xFF7A263A));
        assertEquals(0xFF034694, ClubLook.pick(0xFF034694, 0xFFFFFFFF));
        assertEquals(ClubLook.NAVY, ClubLook.pick(0, 0));
        assertEquals(0xFFC8102E, ClubLook.pick(0, 0xFFC8102E));
        // a blue club stays blue
        double[] hsl = ClubLook.hsl(ClubLook.ground(0xFF034694));
        assertTrue(hsl[0] > 200 && hsl[0] < 230);
    }

    // ---- the player's facts and figures -------------------------------------------------------------------------------

    @Test public void placesInWords() {
        assertArrayEquals(new String[]{"Right-back", "LB, LWB"}, PlayerFacts.positions("RB,LB,LWB"));
        assertArrayEquals(new String[]{"Striker", ""}, PlayerFacts.positions("ST"));
        assertArrayEquals(new String[]{"XYZ", ""}, PlayerFacts.positions("XYZ"));
        assertArrayEquals(new String[]{"", ""}, PlayerFacts.positions(""));
        assertEquals("DEFENDERS", PlayerFacts.lineLabel("Defenders"));
        assertEquals("SQUAD", PlayerFacts.lineLabel("Others"));
        assertEquals("Goalkeeper", PlayerFacts.lineOne("Goalkeepers"));
        assertEquals("", PlayerFacts.lineOne(null));
    }

    @Test public void ageFromTheBirthdayElseAsGiven() {
        LocalDate today = LocalDate.of(2026, 11, 18);
        assertEquals(27, PlayerFacts.age("1999-03-18", 0, today));
        assertEquals(26, PlayerFacts.age("1999-12-01", 31, today));
        assertEquals(25, PlayerFacts.age("", 25, today));
        assertEquals(25, PlayerFacts.age("not-a-day", 25, today));
        assertEquals(0, PlayerFacts.age("", 0, today));
        assertEquals("18 March 1999", PlayerFacts.birthday("1999-03-18"));
        assertEquals("", PlayerFacts.birthday("1999"));
    }

    private static List<String> figureNames(List<PlayerFacts.Figure> f) {
        List<String> out = new ArrayList<>();
        for (PlayerFacts.Figure x : f) out.add(x.name + "=" + x.value);
        return out;
    }

    @Test public void theSeasonsFiguresOnlyAsTheSourcesGaveThem() {
        TeamData.Player p = player("Dev Orr", "9", "Forwards");
        assertTrue(PlayerFacts.season(p).isEmpty());
        // ESPN's appearances with only some figures: those, and nothing filled in for the rest
        p.espnStats.put("appearances", 11);
        p.espnStats.put("totalGoals", 6);
        p.espnStats.put("yellowCards", 0);
        p.fotmobStats.put("rating", 7.236);
        assertEquals(Arrays.asList("Appearances=11", "Goals=6", "Average rating=7.24", "Yellow cards=0"), figureNames(PlayerFacts.season(p)));
        // a keeper's saves and goals let in, and no shots
        TeamData.Player k = player("Ivo Kern", "1", "Goalkeepers");
        k.espnStats.put("appearances", 12);
        k.espnStats.put("saves", 40);
        k.espnStats.put("goalsConceded", 9);
        k.espnStats.put("totalShots", 1);
        assertEquals(Arrays.asList("Appearances=12", "Saves=40", "Goals let in=9"), figureNames(PlayerFacts.season(k)));
        // FotMob's alone when ESPN has none for him
        TeamData.Player f = player("Cal Nye", "8", "Midfielders");
        f.fotmobStats.put("goals", 2.0);
        f.fotmobStats.put("assists", 4.0);
        assertEquals(Arrays.asList("Goals=2", "Assists=4"), figureNames(PlayerFacts.season(f)));
        assertEquals("Figures from FotMob", PlayerFacts.sources(PlayerFacts.season(f), "2026-27 Premier League"));
        assertEquals("Figures from ESPN · 2026-27 Premier League, FotMob", PlayerFacts.sources(PlayerFacts.season(p), "2026-27 Premier League"));
        // none for a manager
        TeamData.Player m = player("Ola Brenn", "", "Manager");
        m.espnStats.put("appearances", 12);
        assertTrue(PlayerFacts.season(m).isEmpty());
    }

    // ---- reading the squad ---------------------------------------------------------------------------------------------

    private static JSONObject athlete(String id, String first, String last, String jersey, String position, double inches) throws Exception {
        JSONObject stats = new JSONObject().put("splits", new JSONObject().put("categories", new JSONArray()
                .put(new JSONObject().put("name", "general").put("stats", new JSONArray()
                        .put(new JSONObject().put("name", "appearances").put("value", 10.0))
                        .put(new JSONObject().put("name", "subIns").put("value", JSONObject.NULL))))
                .put(new JSONObject().put("name", "offensive").put("stats", new JSONArray()
                        .put(new JSONObject().put("name", "totalGoals").put("value", 3.0))
                        .put(new JSONObject().put("name", "goalAssists"))))));
        return new JSONObject().put("id", id).put("firstName", first).put("lastName", last).put("displayName", first + " " + last)
                .put("jersey", jersey).put("position", new JSONObject().put("displayName", position)).put("height", inches)
                .put("dateOfBirth", "1998-05-02T07:00Z").put("headshot", new JSONObject().put("href", "https://a.espncdn.com/x/" + id + ".png"))
                .put("statistics", stats);
    }

    @Test public void espnsRosterWithCutOutsFiguresAndOnlyOneManager() throws Exception {
        JSONObject root = new JSONObject()
                .put("season", new JSONObject().put("displayName", "2026-27 Lakeside League"))
                .put("coach", new JSONArray().put(new JSONObject().put("firstName", "Old").put("lastName", "Boss"))
                        .put(new JSONObject().put("firstName", "Older").put("lastName", "Boss")))
                .put("athletes", new JSONArray().put(athlete("501", "Ari", "Lund", "2", "Defender", 72))
                        .put(athlete("502", "Dev", "Orr", "9", "Forward", 0)));
        List<TeamData.Group> s = TeamData.espnSquad(root);
        // several coaches are the club's past managers: no manager from ESPN
        assertEquals("Defenders", s.get(0).title);
        TeamData.Player a = s.get(0).players.get(0);
        assertEquals("Ari", a.first);
        assertEquals("Lund", a.last);
        assertEquals("501", a.espnId);
        assertEquals("https://a.espncdn.com/x/501.png", a.cutout);
        assertEquals(183, a.height);
        assertEquals("1998-05-02", a.born);
        assertEquals("Defenders", a.line);
        assertEquals(Integer.valueOf(10), a.espnStats.get("appearances"));
        assertEquals(Integer.valueOf(3), a.espnStats.get("totalGoals"));
        // a figure with no value is not one
        assertFalse(a.espnStats.containsKey("subIns"));
        assertFalse(a.espnStats.containsKey("goalAssists"));
        assertEquals(0, s.get(1).players.get(0).height);
        assertEquals("2026-27 Lakeside League", TeamData.rosterSeason(root));
        assertEquals("", TeamData.rosterSeason(new JSONObject()));

        root.put("coach", new JSONArray().put(new JSONObject().put("firstName", "Ola").put("lastName", "Brenn")));
        s = TeamData.espnSquad(root);
        assertEquals("Manager", s.get(0).title);
        assertEquals("Ola Brenn", s.get(0).players.get(0).name);
    }

    @Test public void fotmobsPlayersTakeOnEspnsCutOutsAndFigures() throws Exception {
        JSONObject lund = new JSONObject().put("id", 9001).put("name", "Ari Lund").put("shirtNumber", 2).put("ccode", "NOR").put("cname", "Norway")
                .put("positionIdsDesc", "CB,RB").put("height", 188).put("dateOfBirth", "1998-05-02").put("goals", 1).put("rating", JSONObject.NULL);
        JSONObject orr = new JSONObject().put("id", 9002).put("name", "D. Orr").put("shirtNumber", 9).put("role", new JSONObject().put("fallback", "Attacker"));
        JSONObject boss = new JSONObject().put("id", 9000).put("name", "Ola Brenn").put("goals", 4);
        JSONArray groups = new JSONArray()
                .put(new JSONObject().put("title", "coach").put("members", new JSONArray().put(boss)))
                .put(new JSONObject().put("title", "defenders").put("members", new JSONArray().put(lund)))
                .put(new JSONObject().put("title", "attackers").put("members", new JSONArray().put(orr)));
        List<TeamData.Group> s = TeamData.fotmobSquad(new JSONObject().put("squad", new JSONObject().put("squad", groups)));
        TeamData.Player manager = s.get(0).players.get(0), a = s.get(1).players.get(0), d = s.get(2).players.get(0);
        assertEquals("Manager", manager.position);
        assertTrue(manager.fotmobStats.isEmpty());
        assertEquals("Centre-back", a.position);
        assertEquals("RB", a.also);
        assertEquals(188, a.height);
        assertEquals("1998-05-02", a.born);
        assertEquals(9001, a.fotmobId);
        assertEquals(Double.valueOf(1), a.fotmobStats.get("goals"));
        assertFalse(a.fotmobStats.containsKey("rating"));
        assertEquals("Attacker", d.position);

        List<TeamData.Player> espn = new ArrayList<>();
        for (TeamData.Group g : TeamData.espnSquad(new JSONObject().put("athletes", new JSONArray()
                .put(athlete("501", "Ari", "Lund", "2", "Defender", 72)).put(athlete("502", "Devon", "Orr", "9", "Forward", 70))))) {
            espn.addAll(g.players);
        }
        TeamData.merge(s, espn);
        assertEquals("501", a.espnId);
        assertEquals("https://a.espncdn.com/x/501.png", a.cutout);
        assertEquals(188, a.height);
        assertEquals(Integer.valueOf(10), a.espnStats.get("appearances"));
        // "D. Orr" is ESPN's "Devon Orr": the same surname and initial
        assertEquals("502", d.espnId);
        assertEquals("Devon", d.first);
        assertEquals(178, d.height);
        assertEquals("", manager.espnId);
        // the same number and surname when the first names differ
        TeamData.Player x = player("Jon Orr", "9", "Forwards");
        assertEquals("502", TeamData.match(x, espn).espnId);
        assertEquals(null, TeamData.match(player("Jon Orr", "", "Forwards"), espn));
    }
}
