package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every kind of notification the launcher shows, in one list: the Control Centre's Notifications page is drawn from it
 * (NotifyPanel) and every pop-up is checked against it as it comes up (NotifyPrefs). A new kind is one line here, its
 * id set on the card (ScoreCard.Data.notify) or passed to NotifyPrefs.allowed where it shows. The football
 * competitions (COMPETITIONS) have a switch each too, which football's kinds pass as well (the game's slug on the
 * card: ScoreCard.Data.slug).
 *
 * <p>Pure Java (no Android), so the unit tests can read it.
 */
final class NotifyKinds {
    private NotifyKinds() { }

    /** The id for pop-ups that are not notifications but questions or codes (the sleep prompt, a pairing code). */
    static final String ALWAYS = "always";
    /** Film night's own reminder: it comes while its film night keeps the rest quiet. */
    static final String FILM_NIGHT = "filmnight";

    static final String FOOTBALL = "FOOTBALL", SPORT = "F1 AND OTHER SPORT", NEWS = "NEWS AND WEATHER", FILMS = "FILMS AND TV",
            HOME = "HOME AND FAMILY";
    static final List<String> GROUPS = Collections.unmodifiableList(Arrays.asList(FOOTBALL, SPORT, NEWS, FILMS, HOME));

    /** One kind: its id, the mark before its name, its name, one line on what it is, its group and its theme switch. */
    static final class Kind {
        final String id, icon, name, line, group;
        /**
         * The theme's switch that makes it come at all ("tennis_alerts"), and that switch's default when the theme does
         * not say; null when it always comes. A kind the theme leaves off is offered under Add rather than listed.
         */
        final String flag;
        final boolean flagDefault;
        /** Whether it shows only on Home (a birthday's greeting). */
        final boolean homeOnly;

        Kind(String id, String icon, String name, String line, String group, String flag, boolean flagDefault, boolean homeOnly) {
            this.id = id;
            this.icon = icon;
            this.name = name;
            this.line = line;
            this.group = group;
            this.flag = flag;
            this.flagDefault = flagDefault;
            this.homeOnly = homeOnly;
        }
    }

    private static Kind k(String id, String icon, String name, String line, String group) {
        return new Kind(id, icon, name, line, group, null, false, false);
    }

    private static Kind k(String id, String icon, String name, String line, String group, String flag, boolean flagDefault) {
        return new Kind(id, icon, name, line, group, flag, flagDefault, false);
    }

    /** Every kind, in the order the page lists them. */
    static final List<Kind> ALL = Collections.unmodifiableList(Arrays.asList(
            k("goal", "⚽", "Goals", "Every goal in the games you follow", FOOTBALL),
            k("red", "🟥", "Red cards", "A player sent off", FOOTBALL),
            k("ft", "🏁", "Full time", "The final score as a game ends", FOOTBALL),
            k("kickoff", "⏱\uFE0F", "Kick-off and line-ups", "Before kick-off, with both starting elevens", FOOTBALL, "kickoff_reminders", false),
            k("ht", "☕", "Half time", "The score, the scorers and the stats at the break", FOOTBALL),
            k("var", "📺", "VAR and penalties", "A check under way, its decision, a penalty missed", FOOTBALL, "incident_alerts", false),
            k("drama", "😱", "Late drama and comebacks", "A late equaliser or winner, a side back from two down", FOOTBALL),
            k("prediction", "🎯", "Prediction results", "Who called the score, at full time", FOOTBALL, "predictions", false),
            k("table", "📊", "Live table", "Where a goal moves a club in the table", FOOTBALL, "rich_alerts", true),
            k("goalrush", "⚡", "Goal Rush", "Every live game when the goals come quickly", FOOTBALL, "rich_alerts", true),
            k("milestone", "🏆", "Milestones", "Braces, hat-tricks and a player's landmark goals", FOOTBALL, "rich_alerts", true),
            k("f1live", "🏎\uFE0F", "F1 live", "Lights out, a new leader, every ten laps", SPORT, "f1_live_alerts", false),
            k("f1result", "🏆", "F1 results", "The podium and the full result", SPORT),
            k("f1grid", "🚦", "F1 starting grid", "The grid fifteen minutes before the start", SPORT, "f1_live_alerts", false),
            k("ufc", "🥊", "UFC", "A fight starting, and how it was won", SPORT, "ufc_alerts", false),
            k("boxing", "🥊", "Boxing", "Boxing's news from BBC Sport", SPORT, "boxing_alerts", false),
            k("tennis", "🎾", "Tennis", "A final under way, and its result", SPORT, "tennis_alerts", false),
            k("news", "📰", "Breaking news", "BBC News and BBC Sport", NEWS, "news_alerts", false),
            k("weather", "⛈\uFE0F", "Weather", "Warnings and rain on the way", NEWS, "weather_alerts", false),
            k("releases", "🎬", "New episodes and films", "Out today, from what you watch", FILMS, "release_alerts", true),
            k("creditscover", "🎬", "Credits cover", "A full page as a film's credits roll or an episode ends", FILMS, "credits_cover", true),
            k("howwasit", "⭐", "How was it?", "Rate a film as its credits roll, or a season as it ends", FILMS, "how_was_it", true),
            k("pausecard", "⏸\uFE0F", "Pause card", "When it ends if you carry on, as you pause", FILMS, "pause_card", true),
            k(FILM_NIGHT, "🍿", "Film night", "Ten minutes before a film night you planned", FILMS, "film_night", true),
            k("recap", "📈", "Your month on TV", "The month's viewing, on the 1st", HOME, "release_alerts", true),
            k("reminders", "⏰", "Reminders", "Shows and games you asked to be told about", HOME),
            k("messages", "💬", "Messages", "From the other TV or a phone", HOME),
            k("delivery", "🛵", "Deliveries", "An order on its way, from your phone", HOME),
            k("car", "🚗", "The car and the drive home", "The car's news and when you'll be home", HOME),
            k("camera", "📷", "Cameras", "Motion at a camera", HOME),
            k("doorbell", "🔔", "Doorbell", "Someone at the door", HOME),
            new Kind("birthday", "🎂", "Birthdays", "The birthday greeting on Home", HOME, null, false, true)));

    private static final Map<String, Kind> BY_ID = new HashMap<>();
    private static final Map<String, List<Kind>> BY_FLAG = new HashMap<>();

    static {
        for (Kind k : ALL) {
            BY_ID.put(k.id, k);
            if (k.flag != null) {
                List<Kind> same = BY_FLAG.get(k.flag);
                if (same == null) BY_FLAG.put(k.flag, same = new ArrayList<>());
                same.add(k);
            }
        }
    }

    // ---- the competitions: a switch each, under FOOTBALL ----------------------------------------------------------

    /** The heading of the competitions' switches, after FOOTBALL's on the page (not a kind's group). */
    static final String COMPETITION_GROUP = "COMPETITIONS";

    /**
     * One football competition the alerts can follow: its ESPN slug ("eng.1"), the mark before its name where it has
     * no logo, its name, one line on what it is, and whether its alerts come until it is switched.
     */
    static final class Competition {
        final String slug, icon, name, line;
        final boolean onByDefault;

        Competition(String slug, String icon, String name, String line, boolean onByDefault) {
            this.slug = slug;
            this.icon = icon;
            this.name = name;
            this.line = line;
            this.onByDefault = onByDefault;
        }
    }

    private static Competition comp(String slug, String icon, String name, String line) {
        return new Competition(slug, icon, name, line, true);
    }

    /**
     * Every competition the alerts' service can read (config.xml's goal_alert_leagues, score_leagues and
     * team_alert_leagues, and its own line-up competitions), in the order the page lists them; the page lists those
     * it reads now (GoalWatchService.alertSlugs). All on until switched off, but England's friendlies.
     */
    static final List<Competition> COMPETITIONS = Collections.unmodifiableList(Arrays.asList(
            comp("eng.1", "⚽", "Premier League", "England"),
            comp("eng.2", "⚽", "Championship", "England"),
            comp("eng.fa", "🏆", "FA Cup", "England"),
            comp("eng.league_cup", "🏆", "EFL Cup", "England"),
            comp("uefa.champions", "🏆", "Champions League", "Europe"),
            comp("uefa.europa", "🏆", "Europa League", "Europe"),
            comp("uefa.europa.conf", "🏆", "Conference League", "Europe"),
            comp("sco.1", "⚽", "Scottish Premiership", "Scotland"),
            comp("esp.1", "⚽", "LaLiga", "Spain"),
            comp("ita.1", "⚽", "Serie A", "Italy"),
            comp("ger.1", "⚽", "Bundesliga", "Germany"),
            comp("fra.1", "⚽", "Ligue 1", "France"),
            comp("esp.copa_del_rey", "🏆", "Copa del Rey", "Spain"),
            comp("esp.super_cup", "🏆", "Supercopa", "Spain"),
            comp("ita.coppa_italia", "🏆", "Coppa Italia", "Italy"),
            comp("ger.dfb_pokal", "🏆", "DFB-Pokal", "Germany"),
            comp("fra.coupe_de_france", "🏆", "Coupe de France", "France"),
            comp("fifa.world", "🏆", "World Cup", "England's games"),
            comp("uefa.euro", "🏆", "Euros", "England's games"),
            comp("fifa.worldq.uefa", "⚽", "World Cup qualifiers", "England's games"),
            comp("uefa.euroq", "⚽", "Euro qualifiers", "England's games"),
            comp("uefa.nations", "🏆", "Nations League", "England's games"),
            new Competition("fifa.friendly", "⚽", "Friendlies", "England's friendlies", false)));

    private static final Map<String, Competition> COMPETITION_BY_SLUG = new HashMap<>();

    static {
        for (Competition x : COMPETITIONS) COMPETITION_BY_SLUG.put(x.slug, x);
    }

    /** The competition with this slug; null for one not in the list. */
    static Competition competition(String slug) {
        return slug == null ? null : COMPETITION_BY_SLUG.get(slug);
    }

    /** Its name ("Premier League"); the slug itself for one not in the list. */
    static String competitionName(String slug) {
        Competition x = competition(slug);
        return x != null ? x.name : slug == null ? "" : slug;
    }

    /**
     * Whether a competition's alerts come: as switched (saved; null when never switched), else its default, on for one
     * not in the list. Null (no competition known) is always on.
     */
    static boolean competitionOn(String slug, Boolean saved) {
        if (slug == null) return true;
        if (saved != null) return saved;
        Competition x = competition(slug);
        return x == null || x.onByDefault;
    }

    /**
     * Whether a notification of kind id is held by its game's competition switch: football's kinds, and a card of no
     * listed kind that has a game (the older alert lines' VAR); never F1, the other sports, a reminder the user set
     * or ALWAYS.
     */
    static boolean byCompetition(String id) {
        if (ALWAYS.equals(id)) return false;
        Kind k = get(id);
        return k == null || FOOTBALL.equals(k.group);
    }

    /** The kind with this id; null for ALWAYS or an id not in the list. */
    static Kind get(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    /** The kinds a theme switch brings ("rich_alerts": the table, Goal Rush, milestones); null for any other switch. */
    static List<Kind> byFlag(String flag) {
        return flag == null ? null : BY_FLAG.get(flag);
    }

    /**
     * The kind of a score card: its own (ScoreCard.Data.notify) when set, else from what it is (Data.kind), an F1 card
     * with a podium being a result. Null for a card that is none of them (only the two main switches apply).
     */
    static String ofCard(String notify, String kind, boolean podium) {
        if (notify != null) return notify;
        if (kind == null) return null;
        switch (kind) {
            case "goal": return "goal";
            case "red": return "red";
            case "ft": return "ft";
            case "var":
            case "pen": return "var";
            case "kickoff": return "kickoff";
            case "reminder": return "reminders";
            case "f1": return podium ? "f1result" : "f1live";
            case "fight": return "ufc";
            case "ht": return "ht";
            case "table": return "table";
            case "milestone": return "milestone";
            case "prediction": return "prediction";
            case "release": return "releases";
            default: return null;
        }
    }

    /** The kind of an older-style alert line (GoalWatchService's own window) by its sound; null when unknown. */
    static String ofSound(String sound) {
        if (sound == null) return null;
        switch (sound) {
            case "goal": return "goal";
            case "red": return "red";
            case "fulltime": return "ft";
            case "f1": return "f1live";
            case "news": return "news";
            case "fight": return "ufc";   // the older lines share one sound for UFC and boxing
            case "tennis": return "tennis";
            case "weather": return "weather";
            case "message": return "messages";
            case "reminder": return "reminders";
            default: return null;
        }
    }
}
