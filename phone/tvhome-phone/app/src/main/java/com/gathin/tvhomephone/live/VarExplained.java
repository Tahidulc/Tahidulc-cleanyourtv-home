package com.gathin.tvhomephone.live;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * VAR explained: a check out of the board as it starts ("Checking a penalty", what and who when it is known), then the
 * decision in its place ("No penalty", whether the referee's call stood or was changed) with a WHY under it: the law
 * behind it in a sentence or two, and how long the check took. What it says comes from ESPN's commentary around the
 * check and the decision ("Handball by …", "Offside, … is caught offside", "Penalty conceded by … after a foul in the
 * penalty area", "Goal confirmed following VAR Review", "GOAL OVERTURNED BY VAR: …") and FotMob's own VAR events ("Goal
 * ruled out" with "offside", "Penalty cancelled", "Yellow card cancelled"). It never says more than they do: with no
 * reason found, the WHY is the general one for that kind of decision. The parsing and words are pure, for the tests;
 * what was read is kept here between the alert service's reads and the board.
 */
final class VarExplained {
    private VarExplained() {
    }

    /** The mock-up's cyan, for the VAR chip and the WHY label (its ink is dark). */
    static final int CYAN = 0xFF3DD5F3, CYAN_INK = 0xFF0B1640, CYAN_ON_LIGHT = 0xFF0A7A96;
    /** A check stays out until its decision, this long at most; the decision and its WHY this long. */
    static final long CHECK_MAX_MS = 180_000L, WHY_MS = 12_000L;

    /** The reasons the commentary or FotMob give ("": none found). */
    static final String HANDBALL = "handball", OFFSIDE = "offside", FOUL = "foul", SIMULATION = "simulation",
            VIOLENT = "violent", SERIOUS = "serious", CHANCE = "chance", LANGUAGE = "language", OUT = "out",
            IDENTITY = "identity";

    /** The WHY's drawing: a hand on the ball, the offside line, a tackle, the ball over the line, two shirts, the VAR screen. */
    static final int ICON_HANDBALL = 1, ICON_OFFSIDE = 2, ICON_FOUL = 3, ICON_OUT = 4, ICON_OTHER = 5, ICON_SCREEN = 6;

    /** A decision as worked out from what was read. */
    static final class Call {
        /** penalty, no_penalty, goal, no_goal, red, card_cancelled, or other (FotMob's own words in outcome). */
        String kind = "";
        /** The reason found (HANDBALL, OFFSIDE ...), "" when none was. */
        String reason = "";
        /** Who the reason is about, as written ("Nikola Stulic"), "" when not told. */
        String player = "";
        /** Whether player is the one at fault (the reason names them: handled, offside, conceded, sent off), not just the scorer. */
        boolean atFault;
        /** Whose goal it was, as written, for a goal decision; "" when not told. */
        String scorer = "";
        /** Who was fouled, for a penalty decision ("Fisnik Asllani draws a foul in the penalty area"); "" when not told. */
        String victim = "";
        /** The on-field call: 1 it stood, -1 it was changed, 0 not known. */
        int onField;
        /** When, as written ("45'+2'"). */
        String minute = "";
        /** The side a penalty decision is for, as written ("Atalanta"), "" otherwise. */
        String team = "";
        /** FotMob's own words for a kind it has no name for here. */
        String words = "";

        @Override public String toString() {
            return kind + "/" + reason + "/" + player + "/" + onField + "/" + minute;
        }
    }

    /** What a check under way is about: {"penalty" | "goal" | "red" | "", reason, player}. */
    static String[] noCheck() {
        return new String[]{"", "", ""};
    }

    /** The WHY under a decision: its drawing, its sentences, its foot line. */
    static final class Why {
        int icon = ICON_SCREEN;
        String text = "", foot = "";
    }

    // ---- reading the commentary -------------------------------------------------------------------------------------

    private static final Pattern DECISION = Pattern.compile("VAR Decision:\\s*([^.]*)");
    private static final Pattern WHO_TEAM = Pattern.compile("^(.+?) \\(([^)]+)\\)");
    private static final Pattern HANDBALL_BY = Pattern.compile("Hand ?ball by (.+?) \\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONCEDED = Pattern.compile("Penalty conceded by (.+?) \\(([^)]+)\\)(.*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DRAWS = Pattern.compile("Penalty ([^.]+)\\. (.+?) draws a foul in the penalty area");
    private static final Pattern OFFSIDE_BY = Pattern.compile("Offside, ([^.]+)\\. (.+?) is caught offside");
    private static final Pattern SIMULATED = Pattern.compile("(.+?) \\(([^)]+)\\) has gone down, but the referee deems it simulation");
    private static final Pattern RED_CARD = Pattern.compile("(.+?) \\(([^)]+)\\) is shown the red card(?: for (.+?))?\\.?$");
    private static final Pattern FOUL_BY = Pattern.compile("^Foul by (.+?) \\(([^)]+)\\)");
    private static final Pattern OVERTURNED = Pattern.compile("OVERTURNED BY VAR:\\s*(.+?) \\(([^)]+)\\)");
    private static final Pattern GOAL_LINE = Pattern.compile("^(?:Goal!|Own Goal)[^.]*\\.\\s*(.+?) \\(([^)]+)\\)");
    private static final Pattern UPGRADED = Pattern.compile("Card upgraded (.+?) \\(([^)]+)\\)");
    /** A goal decision's scorer at its end ("VAR Decision: Goal Everton - Newcastle United 0-1 Everton (Thierno Barry)."). */
    private static final Pattern SCORER_AT_END = Pattern.compile("\\(([^()]+)\\)\\.?\\s*$");

    /** Pure: the minute a commentary time stands for ("45'+2'": 45, "90'+6'": 90); -1 when there is none. */
    static int base(String minute) {
        if (minute == null) return -1;
        Matcher m = Pattern.compile("(\\d+)").matcher(minute);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** Pure: the time a commentary time stands for in added minutes too ("45'+2'": 47), for how far apart two are. */
    static int at(String minute) {
        if (minute == null) return -1;
        Matcher m = Pattern.compile("(\\d+)'?\\s*\\+\\s*(\\d+)").matcher(minute);
        if (m.find()) return Integer.parseInt(m.group(1)) + Integer.parseInt(m.group(2));
        return base(minute);
    }

    /** Pure: the decision's kind from ESPN's words ("VAR Decision: No Penalty Lyon."): penalty, no_penalty, goal, no_goal, red; null else. */
    static String kindOf(String words) {
        if (words == null) return null;
        String w = words.replaceFirst("^.*?VAR Decision:\\s*", "").trim().toLowerCase(Locale.ROOT);
        if (w.startsWith("no penalty")) return "no_penalty";
        if (w.startsWith("penalty")) return "penalty";
        if (w.startsWith("no goal")) return "no_goal";
        if (w.startsWith("goal")) return "goal";
        if (w.startsWith("card upgraded") || w.startsWith("red card")) return "red";
        return null;
    }

    /** Pure: the side a penalty decision names ("VAR Decision: Penalty Atalanta." → Atalanta); "" for any other. */
    static String teamOf(String words) {
        if (words == null) return "";
        Matcher m = DECISION.matcher(words);
        String w = m.find() ? m.group(1).trim() : words.trim();
        String lower = w.toLowerCase(Locale.ROOT);
        String rest = lower.startsWith("no penalty") ? w.substring(10) : lower.startsWith("penalty") ? w.substring(7) : "";
        return rest.replaceAll("[.\\s]+$", "").trim();
    }

    /** Where a reason came from: a penalty's own line, or a plain "Foul by" (weak: fouls happen all game). */
    static final String FROM_PENALTY = "penalty", FROM_FOUL_BY = "foul_by", FROM_LINE = "line";

    /**
     * Pure: a reason one line of commentary gives, {reason, player, team, where from} ("Handball by Nikola Stulic
     * (Lecce)." → {handball, Nikola Stulic, Lecce, line}); null when it gives none.
     */
    static String[] reasonOf(String text) {
        if (text == null || text.isEmpty()) return null;
        Matcher m = CONCEDED.matcher(text);
        if (m.find()) {
            String how = m.group(3).toLowerCase(Locale.ROOT);
            return new String[]{how.contains("hand") ? HANDBALL : FOUL, m.group(1).trim(), m.group(2).trim(), FROM_PENALTY};
        }
        m = HANDBALL_BY.matcher(text);
        if (m.find()) return new String[]{HANDBALL, m.group(1).trim(), m.group(2).trim(), FROM_LINE};
        m = OFFSIDE_BY.matcher(text);
        if (m.find()) return new String[]{OFFSIDE, m.group(2).trim(), m.group(1).trim(), FROM_LINE};
        m = SIMULATED.matcher(text);
        if (m.find()) return new String[]{SIMULATION, m.group(1).trim(), m.group(2).trim(), FROM_LINE};
        // The player who drew the foul was fouled, not at fault: no name with it as the one at fault, kept as the fouled.
        m = DRAWS.matcher(text);
        if (m.find()) return new String[]{FOUL, "", m.group(1).trim(), FROM_PENALTY, m.group(2).trim()};
        m = RED_CARD.matcher(text.trim());
        if (m.find()) {
            String why = redReason(m.group(3));
            return why.isEmpty() ? null : new String[]{why, m.group(1).trim(), m.group(2).trim(), FROM_LINE};
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("mistaken identity")) return new String[]{IDENTITY, "", "", FROM_LINE};
        if (lower.contains("out of play")) return new String[]{OUT, "", "", FROM_LINE};
        m = FOUL_BY.matcher(text);
        if (m.find()) return new String[]{FOUL, m.group(1).trim(), m.group(2).trim(), FROM_FOUL_BY};
        return null;
    }

    /** Pure: a red card's reason in ESPN's or FotMob's words ("violent conduct", "Serious foul play"); "" none known. */
    static String redReason(String words) {
        if (words == null) return "";
        String w = words.toLowerCase(Locale.ROOT);
        if (w.contains("violent")) return VIOLENT;
        if (w.contains("serious") || w.contains("bad foul")) return SERIOUS;
        if (w.contains("professional") || w.contains("denying") || w.contains("goal-scoring") || w.contains("goal scoring")) return CHANCE;
        if (w.contains("language") || w.contains("offensive") || w.contains("insult") || w.contains("abusive")) return LANGUAGE;
        return "";
    }

    /** How strongly a reason says why (a plain foul nearby says least). */
    private static int strength(String reason) {
        return FOUL.equals(reason) ? 1 : reason == null || reason.isEmpty() ? 0 : 2;
    }

    /**
     * Pure: the decision at line at (lines {text, minute}, oldest first, its words with "VAR Decision:") worked out from the
     * lines round it: the reason the nearest of them gives for it, who, and whether the on-field call stood. Null when it
     * is not a decision this knows (a card decision other than an upgrade, "Other Decision Cancelled").
     */
    static Call decision(List<String[]> lines, int at) {
        if (lines == null || at < 0 || at >= lines.size()) return null;
        String text = lines.get(at)[0];
        Matcher dm = DECISION.matcher(text == null ? "" : text);
        if (!dm.find()) return null;
        String kind = kindOf(text);
        if (kind == null) return null;
        Call c = new Call();
        c.kind = kind;
        c.minute = lines.get(at)[1] == null ? "" : lines.get(at)[1];
        c.team = "penalty".equals(kind) || "no_penalty".equals(kind) ? teamOf(text) : "";
        int when = at(c.minute);
        // Whose goal (or card) it was: the line that names them.
        String scorer = "", scorerTeam = "", victim = "";
        boolean overturnedGoal = false, confirmed = false, awarded = false, penaltyBefore = false, penaltyEarlier = false;
        Matcher up = UPGRADED.matcher(text);
        if (up.find()) {
            scorer = up.group(1).trim();
            scorerTeam = up.group(2).trim();
        }
        int from = Math.max(0, at - 6), to = Math.min(lines.size() - 1, at + 2);
        for (int j = from; j <= to; j++) {
            if (j == at) continue;
            String t = lines.get(j)[0] == null ? "" : lines.get(j)[0];
            int w = at(lines.get(j)[1]);
            if (when >= 0 && w >= 0 && (w > when + 1 || w < when - 3)) continue;
            Matcher o = OVERTURNED.matcher(t);
            if (o.find()) {
                overturnedGoal = true;
                scorer = o.group(1).trim();
                scorerTeam = o.group(2).trim();
            }
            Matcher g = GOAL_LINE.matcher(t);
            if (g.find() && ("goal".equals(kind) || "no_goal".equals(kind))) {
                if (t.contains("Goal confirmed following VAR")) confirmed = true;
                if (t.contains("Goal awarded following VAR")) awarded = true;
                if (scorer.isEmpty()) {
                    scorer = g.group(1).trim();
                    scorerTeam = g.group(2).trim();
                }
            }
            if (j < at && (CONCEDED.matcher(t).find() || DRAWS.matcher(t).find())) {
                penaltyBefore = true;
                if (when >= 0 && w >= 0 && w < when) penaltyEarlier = true;
            }
            Matcher dr = DRAWS.matcher(t);
            if (victim.isEmpty() && dr.find() && ("penalty".equals(kind) || "no_penalty".equals(kind))) victim = dr.group(2).trim();
        }
        // The reason: the strongest the lines round it give that is about this decision, the nearest of those.
        String[] best = null;
        int bestAt = Integer.MAX_VALUE;
        for (int j = from; j <= to; j++) {
            if (j == at) continue;
            String t = lines.get(j)[0] == null ? "" : lines.get(j)[0];
            int w = at(lines.get(j)[1]);
            if (when >= 0 && w >= 0 && (w > when + 1 || w < when - 3)) continue;
            String[] r = reasonOf(t);
            if (r == null || !about(kind, r, scorer, scorerTeam, c.team)) continue;
            int s = strength(r[0]), bs = best == null ? -1 : strength(best[0]);
            int dist = Math.abs(j - at);
            if (s > bs || s == bs && dist < bestAt) {
                best = r;
                bestAt = dist;
            }
        }
        if (best != null) {
            c.reason = best[0];
            c.player = best[1];
            c.atFault = !best[1].isEmpty();
        } else if (!scorer.isEmpty()) {
            c.player = scorer;
            // A card upgraded: the player named is the one sent off; a goal's scorer is no one at fault.
            c.atFault = "red".equals(kind);
        }
        c.victim = victim;
        if ("goal".equals(kind) || "no_goal".equals(kind)) {
            if (scorer.isEmpty()) {
                Matcher sm = SCORER_AT_END.matcher(text == null ? "" : text);
                if (sm.find()) scorer = sm.group(1).trim();
            }
            c.scorer = scorer;
        }
        switch (kind) {
            case "goal": c.onField = confirmed ? 1 : awarded ? -1 : 0; break;
            case "no_goal": c.onField = overturnedGoal ? -1 : 0; break;
            case "penalty": c.onField = penaltyEarlier ? 1 : 0; break;
            case "no_penalty": c.onField = penaltyBefore ? -1 : 0; break;
            case "red": c.onField = -1; break;
            default: c.onField = 0;
        }
        return c;
    }

    /**
     * Whether a reason line is about this decision: for a goal, the scoring side's offside, handball, foul or the ball out
     * of play; for a penalty, its own lines (conceded, drawn) or a handball; a dive or an offside by the side the penalty
     * was for; for a red card, the sending-off's reason for that player.
     */
    private static boolean about(String kind, String[] r, String scorer, String scorerTeam, String team) {
        String reason = r[0], who = r[1], side = r[2], from = r.length > 3 ? r[3] : FROM_LINE;
        boolean sideKnown = !scorerTeam.isEmpty() && !side.isEmpty();
        boolean scorers = sideKnown && same(side, scorerTeam) || !scorer.isEmpty() && same(who, scorer);
        switch (kind) {
            case "no_goal":
            case "goal":
                if (!(OFFSIDE.equals(reason) || HANDBALL.equals(reason) || FOUL.equals(reason) || OUT.equals(reason))) return false;
                // A plain foul only when it is the scoring side's own (its build-up), and that side is known.
                if (FROM_FOUL_BY.equals(from)) return sideKnown && same(side, scorerTeam);
                return OUT.equals(reason) || !sideKnown || scorers;
            case "penalty":
                if (HANDBALL.equals(reason)) return true;
                return FOUL.equals(reason) && FROM_PENALTY.equals(from);
            case "no_penalty":
                if (OFFSIDE.equals(reason) || SIMULATION.equals(reason)) return !team.isEmpty() && same(side, team);
                if (HANDBALL.equals(reason)) return true;
                return FOUL.equals(reason) && FROM_PENALTY.equals(from);
            case "red":
                return (VIOLENT.equals(reason) || SERIOUS.equals(reason) || CHANCE.equals(reason) || LANGUAGE.equals(reason))
                        && (scorer.isEmpty() || same(who, scorer));
            default:
                return false;
        }
    }

    private static boolean same(String a, String b) {
        return a != null && b != null && !a.isEmpty() && a.trim().equalsIgnoreCase(b.trim());
    }

    /**
     * Pure: what a check (line at, "VAR checking") is likely about, from the lines just before it: {"goal" | "penalty" |
     * "red" | "", reason, player}.
     */
    static String[] checkAbout(List<String[]> lines, int at) {
        String[] out = noCheck();
        if (lines == null || at < 0 || at >= lines.size()) return out;
        for (int j = at - 1; j >= Math.max(0, at - 4); j--) {
            String t = lines.get(j)[0] == null ? "" : lines.get(j)[0];
            String lower = t.toLowerCase(Locale.ROOT);
            String[] r = reasonOf(t);
            if (out[1].isEmpty() && r != null && !FROM_FOUL_BY.equals(r[3]) && strength(r[0]) >= 1) {
                out[1] = r[0];
                out[2] = r[1];
            }
            if (out[0].isEmpty()) {
                if (t.startsWith("Goal!") || t.startsWith("Own Goal")) out[0] = "goal";
                else if (lower.contains("penalty")) out[0] = "penalty";
                else if (lower.contains("red card")) out[0] = "red";
            }
        }
        if (out[0].isEmpty() && (VIOLENT.equals(out[1]) || SERIOUS.equals(out[1]) || CHANCE.equals(out[1]))) out[0] = "red";
        return out;
    }

    /** Pure: what a check is about from the alert's own words ("Possible penalty", "Checking goal", "Possible red card"). */
    static String aboutOf(String words) {
        String w = words == null ? "" : words.toLowerCase(Locale.ROOT);
        if (w.contains("penalty")) return "penalty";
        if (w.contains("red")) return "red";
        if (w.contains("goal")) return "goal";
        return "";
    }

    /** One thing the commentary says VAR did: a check under way (about) or a decision (call), keyed by its line's sequence. */
    static final class Seen {
        String key = "";
        String[] about;
        Call call;
    }

    /**
     * Pure: what VAR did as a game's commentary has it (lines {text, minute, sequence}, oldest first): each decision it
     * knows, and the latest check still waiting for its decision (none once one came after it).
     */
    static List<Seen> scan(List<String[]> lines) {
        List<Seen> out = new ArrayList<>();
        if (lines == null) return out;
        int lastDecision = -1, lastCheck = -1;
        for (int i = 0; i < lines.size(); i++) {
            String t = lines.get(i)[0] == null ? "" : lines.get(i)[0];
            if (t.contains("VAR Decision:")) lastDecision = i;
            if (t.contains("VAR checking")) lastCheck = i;
        }
        for (int i = 0; i < lines.size(); i++) {
            String t = lines.get(i)[0] == null ? "" : lines.get(i)[0];
            if (!t.contains("VAR Decision:")) continue;
            Call c = decision(lines, i);
            if (c == null) continue;
            Seen s = new Seen();
            s.key = keyOf(lines.get(i), i);
            s.call = c;
            out.add(s);
        }
        if (lastCheck > lastDecision) {
            Seen s = new Seen();
            s.key = keyOf(lines.get(lastCheck), lastCheck);
            s.about = checkAbout(lines, lastCheck);
            out.add(s);
        }
        return out;
    }

    private static String keyOf(String[] line, int i) {
        return line.length > 2 && line[2] != null && !line[2].isEmpty() ? line[2] : "i" + i;
    }

    /** What VAR did in a game already shown (or there before the board came up), by "game|key". */
    private static final Set<String> scanned = new HashSet<>(), scannedGames = new HashSet<>();

    /** The commentary's VAR moments not seen before for a game (none on its first read: they were before the board). */
    static synchronized List<Seen> fresh(String game, List<Seen> all) {
        List<Seen> out = new ArrayList<>();
        if (game == null || all == null) return out;
        boolean first = scannedGames.add(game);
        for (Seen s : all) if (scanned.add(game + "|" + s.key + (s.call != null ? "|d" : "|c")) && !first) out.add(s);
        if (scanned.size() > 400) scanned.clear();
        return out;
    }

    // ---- the words ---------------------------------------------------------------------------------------------------

    /** Pure: a check's title: "Checking a penalty", "Checking a goal", "Checking a red card", "Checking an incident". */
    static String checkTitle(String about) {
        switch (about == null ? "" : about) {
            case "penalty": return "Checking a penalty";
            case "goal": return "Checking a goal";
            case "red": return "Checking a red card";
            default: return "Checking an incident";
        }
    }

    /** Pure: a check's small line, what and who when known ("possible handball · Okafor"); "" when neither is. */
    static String checkLine(String reason, String player) {
        String what = reasonWords(reason);
        String who = surname(player);
        if (what.isEmpty()) return who;
        return who.isEmpty() ? "possible " + what : "possible " + what + " · " + who;
    }

    /** Pure: a reason in a few words ("handball", "offside", "a foul"); "" for none. */
    static String reasonWords(String reason) {
        switch (reason == null ? "" : reason) {
            case HANDBALL: return "handball";
            case OFFSIDE: return "offside";
            case FOUL: return "foul";
            case SIMULATION: return "dive";
            case VIOLENT: return "violent conduct";
            case SERIOUS: return "serious foul play";
            case CHANCE: return "denied goal chance";
            case LANGUAGE: return "offensive language";
            case OUT: return "ball out of play";
            case IDENTITY: return "wrong player";
            default: return "";
        }
    }

    /** Pure: a name's surname as the board shows names ("Nikola Stulic" → "Stulic"); "" for none. */
    static String surname(String name) {
        if (name == null || name.trim().isEmpty()) return "";
        String n = name.trim();
        int sp = n.lastIndexOf(' ');
        return sp < 0 ? n : n.substring(sp + 1);
    }

    /** Pure: the decision's words, big: "Penalty given", "No penalty", "Goal stands", "No goal", "Red card" ... */
    static String outcome(Call c) {
        if (c == null) return "VAR decision";
        switch (c.kind) {
            case "penalty": return "Penalty given";
            case "no_penalty": return "No penalty";
            case "goal": return c.onField < 0 ? "Goal given" : "Goal stands";
            case "no_goal": return "No goal";
            case "red": return "Red card";
            case "card_cancelled": return "Card taken back";
            default: return c.words == null || c.words.isEmpty() ? "VAR decision" : c.words;
        }
    }

    /** Pure: the small line under it: the referee's call stood, was changed, or (not known) only that VAR looked. */
    static String callLine(int onField) {
        return onField > 0 ? "the referee's call stands" : onField < 0 ? "overturned · the referee changed it" : "after a VAR review";
    }

    /** Pure: how long a check took, as said ("42 s", "1 min 25 s", "2 min"); "" when not known. */
    static String length(long ms) {
        if (ms < 1_000L) return "";
        long s = Math.round(ms / 1000.0);
        if (s < 60) return s + " s";
        long m = s / 60, r = s % 60;
        return r == 0 ? m + " min" : m + " min " + r + " s";
    }

    /** Pure: the WHY's foot: "Checked for 1 min 25 s · on-field decision stood" (each part only when known). */
    static String foot(long checkMs, int onField) {
        String len = length(checkMs);
        String call = onField > 0 ? "on-field decision stood" : onField < 0 ? "on-field decision overturned" : "";
        if (len.isEmpty()) return call.isEmpty() ? "VAR review" : call.substring(0, 1).toUpperCase(Locale.ROOT) + call.substring(1);
        return "Checked for " + len + (call.isEmpty() ? "" : " · " + call);
    }

    /** Pure: the WHY's drawing for a call. */
    static int icon(Call c) {
        if (c == null) return ICON_SCREEN;
        switch (c.reason) {
            case HANDBALL: return ICON_HANDBALL;
            case OFFSIDE: return ICON_OFFSIDE;
            case FOUL: case SIMULATION: case VIOLENT: case SERIOUS: case CHANCE: return ICON_FOUL;
            case OUT: return ICON_OUT;
            case IDENTITY: case LANGUAGE: return ICON_OTHER;
            default: return "card_cancelled".equals(c.kind) ? ICON_OTHER : ICON_SCREEN;
        }
    }

    /**
     * Pure: the WHY's sentences: the law behind the decision for the reason found, plainly, with the players and the side
     * the commentary names for it (whose hand, who was offside, who fouled whom, whose goal, whose penalty); with no
     * reason, what VAR looks for in that kind of decision. Never more than the reason and the decision say: a player is
     * named only for the part the commentary gives them, and without names the sentences are the general ones.
     */
    static String why(Call c) {
        if (c == null) return GENERAL;
        String k = c.kind, r = c.reason;
        String p = c.atFault ? nz(c.player) : "", v = nz(c.victim), s = nz(c.scorer), t = nz(c.team);
        switch (r) {
            case OFFSIDE:
                if ("no_goal".equals(k)) {
                    if (!p.isEmpty() && !s.isEmpty() && !same(p, s)) {
                        return p + " was offside and involved in the move, so " + poss(s) + " goal can't count under the offside law.";
                    }
                    if (!p.isEmpty()) return p + " was offside when the ball was played. Under the offside law, that stops the goal counting.";
                    return "An attacker was offside and involved in the move. Under the offside law, that stops the goal counting.";
                }
                if ("goal".equals(k)) {
                    return "VAR checked the offside lines and found " + (s.isEmpty() ? "the scorer" : s) + " level or behind, so the goal counts.";
                }
                if ("no_penalty".equals(k)) {
                    return (p.isEmpty() ? "An attacker" : p) + " was offside earlier in the move, so play should have stopped there: no penalty"
                            + (t.isEmpty() ? "." : " for " + t + ".");
                }
                break;
            case HANDBALL: {
                String arm = p.isEmpty() ? null : poss(p) + " hand or arm";
                if ("no_goal".equals(k)) return "The ball touched " + (arm == null ? "an attacker's hand or arm" : arm)
                        + " just before the goal. Under the handball law, a goal can't come straight from that, even by accident.";
                if ("penalty".equals(k)) return "The ball hit " + (arm == null ? "a defender's hand or arm" : arm)
                        + " in the penalty area. It's a penalty when the arm makes the body bigger or moves towards the ball.";
                if ("no_penalty".equals(k)) return "The ball touched " + (arm == null ? "a hand or arm" : arm)
                        + ", but not in a way the handball law punishes, so there's no penalty.";
                break;
            }
            case FOUL:
                if ("no_goal".equals(k)) {
                    return (p.isEmpty() ? "There was a foul by the attacking side" : p + " fouled an opponent") + " in the build-up, so "
                            + (s.isEmpty() || same(p, s) ? "the goal" : poss(s) + " goal") + " can't count.";
                }
                if ("penalty".equals(k)) {
                    if (!p.isEmpty() && !v.isEmpty()) return p + " fouled " + v + " inside the penalty area, and that's a penalty kick.";
                    if (!p.isEmpty()) return p + " fouled an opponent inside the penalty area, and that's a penalty kick.";
                    if (!v.isEmpty()) return v + " was fouled inside the penalty area, and that's a penalty kick.";
                    return "A defender fouled an opponent inside the penalty area, and that's a penalty kick.";
                }
                if ("no_penalty".equals(k)) {
                    if (!p.isEmpty()) return "VAR found no foul by " + p + (v.isEmpty() ? "" : " on " + v) + " worth a penalty, so play goes on without one.";
                    if (!v.isEmpty()) return "VAR found no foul on " + v + " worth a penalty, so play goes on without one.";
                    return "VAR found no foul worth a penalty in the area, so play goes on without one.";
                }
                break;
            case SIMULATION:
                return (p.isEmpty() ? "The player" : p) + " went down without being fouled. Trying to trick the referee into a penalty is called"
                        + " simulation, and it can be a yellow card.";
            case VIOLENT:
                return "Violent conduct" + (p.isEmpty() ? "" : " by " + p) + ": using too much force against someone when not challenging for"
                        + " the ball. That's a straight red card.";
            case SERIOUS:
                return "Serious foul play" + (p.isEmpty() ? "" : " by " + p) + ": a challenge with excessive force that puts an opponent's safety"
                        + " at risk. That's a straight red card.";
            case CHANCE:
                return p.isEmpty() ? "Stopping a clear goal-scoring chance with a foul is a sending-off offence."
                        : p + " stopped a clear goal-scoring chance with a foul, and that's a sending-off offence.";
            case LANGUAGE:
                return p.isEmpty() ? "Offensive, insulting or abusive words or gestures are a straight red card."
                        : p + " used offensive, insulting or abusive words or gestures, and that's a straight red card.";
            case OUT:
                return "The ball went out of play earlier in the move, so " + (s.isEmpty() ? "what came after it doesn't count." : poss(s) + " goal doesn't count.");
            case IDENTITY:
                return (p.isEmpty() ? "The card went to the wrong player." : "The card went to " + p + " by mistake.")
                        + " VAR can step in to put a case of mistaken identity right.";
            default:
                break;
        }
        switch (k) {
            case "goal": {
                String whose = s.isEmpty() ? "this one" : poss(s) + " goal";
                return c.onField < 0
                        ? "The on-field call was wrong: VAR found nothing to stop " + (s.isEmpty() ? "the goal" : whose) + ", so it counts."
                        : "VAR checks every goal. It found nothing clear and obvious to rule " + (s.isEmpty() ? "this one out" : "out " + whose) + ", so it stands.";
            }
            case "no_goal": return "VAR found something in the build-up that stops " + (s.isEmpty() ? "the goal" : poss(s) + " goal")
                    + " counting, such as an offside, a foul or a handball.";
            case "penalty": return c.onField > 0
                    ? "VAR looked again at the referee's penalty" + (t.isEmpty() ? "" : " for " + t) + " and found no clear and obvious error, so it stands."
                    : "VAR found a foul or handball in the penalty area that the referee needed to see, so it's a penalty" + (t.isEmpty() ? "." : " to " + t + ".");
            case "no_penalty": return c.onField < 0
                    ? "VAR showed the referee there was no penalty offence after all, so " + (t.isEmpty() ? "the penalty is" : poss(t) + " penalty is") + " taken away."
                    : "VAR only changes a decision for a clear and obvious error. After the review, there's no penalty" + (t.isEmpty() ? "." : " for " + t + ".");
            case "red": return p.isEmpty() ? "VAR can step in for a straight red card the referee missed or got wrong."
                    : p + " is sent off: VAR can step in for a straight red card the referee missed or got wrong.";
            case "card_cancelled": return p.isEmpty() ? "VAR can take back a card when it was shown to the wrong player."
                    : poss(p) + " card is taken back: VAR can take back a card shown to the wrong player.";
            default: return GENERAL;
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    /** Pure: a name's possessive ("Thierno Barry's", "Scotland's"; "Jesus'"). */
    static String poss(String name) {
        String n = nz(name);
        if (n.isEmpty()) return n;
        return n.endsWith("s") ? n + "'" : n + "'s";
    }

    private static final String GENERAL = "VAR only steps in for a clear and obvious error, or a serious incident the referee missed.";

    /** Pure: the whole WHY for a call and how long its check took (0: not known). */
    static Why whyOf(Call c, long checkMs) {
        Why w = new Why();
        w.icon = icon(c);
        w.text = why(c);
        w.foot = foot(checkMs, c == null ? 0 : c.onField);
        return w;
    }

    // ---- FotMob's VAR events -------------------------------------------------------------------------------------------

    /**
     * Pure: FotMob's VAR event ({minute, player, key, value, reason key or ""}) as a call; reds ({minute, player, reason
     * key}) say whether a card taken back was a yellow upgraded to red.
     */
    static Call fromFotMob(String[] e, List<String[]> reds) {
        if (e == null || e.length < 4) return null;
        Call c = new Call();
        String key = e[2] == null ? "" : e[2].toLowerCase(Locale.ROOT), more = e.length > 4 && e[4] != null ? e[4].toLowerCase(Locale.ROOT) : "";
        c.minute = e[0] == null ? "" : e[0] + "'";
        c.player = e[1] == null ? "" : e[1];
        c.onField = -1;
        // FotMob names the scorer for a goal, the player for a card: only a card's is the one at fault.
        if (key.contains("goal")) {
            c.kind = key.contains("cancel") || key.contains("disallow") || key.contains("ruled") ? "no_goal" : "goal";
        } else if (key.contains("penalty")) {
            c.kind = key.contains("cancel") || key.contains("removed") ? "no_penalty" : "penalty";
        } else if (key.contains("red")) {
            c.kind = key.contains("cancel") || key.contains("removed") ? "card_cancelled" : "red";
        } else if (key.contains("yellow") || key.contains("card")) {
            c.kind = "card_cancelled";
            // A yellow taken back and a red for the same player just after: the card upgraded.
            for (String[] r : reds == null ? new ArrayList<String[]>() : reds) {
                if (r.length < 3 || !same(r[1], c.player)) continue;
                int d = base(r[0]) - base(e[0]);
                if (d >= 0 && d <= 4) {
                    c.kind = "red";
                    c.reason = redReason(r[2].replace('_', ' '));
                    c.minute = r[0] + "'";
                }
            }
        } else {
            c.kind = "other";
            c.words = e[3] == null ? "" : e[3];
        }
        if (c.reason.isEmpty()) c.reason = fotmobReason(more);
        if ("goal".equals(c.kind) || "no_goal".equals(c.kind)) c.scorer = c.player;
        c.atFault = "red".equals(c.kind) || "card_cancelled".equals(c.kind);
        return c;
    }

    /** Pure: FotMob's second key as a reason ("offside", "foul", "handball"); "" for none. */
    static String fotmobReason(String key) {
        String k = key == null ? "" : key.toLowerCase(Locale.ROOT);
        if (k.contains("offside")) return OFFSIDE;
        if (k.contains("hand")) return HANDBALL;
        if (k.contains("foul")) return FOUL;
        if (k.contains("identity")) return IDENTITY;
        if (k.contains("out")) return OUT;
        return "";
    }

    /** Pure: ESPN's call with FotMob's word where ESPN has none: the same kind of decision, within two minutes. */
    static void enrich(Call c, List<String[]> fotmob) {
        if (c == null || fotmob == null) return;
        int when = at(c.minute);
        for (String[] e : fotmob) {
            Call f = fromFotMob(e, null);
            if (f == null || when >= 0 && Math.abs(base(e[0]) - when) > 2) continue;
            boolean match = f.kind.equals(c.kind) || "red".equals(c.kind) && "card_cancelled".equals(f.kind);
            if (!match) continue;
            if (c.onField == 0) c.onField = f.onField;
            if (c.reason.isEmpty()) c.reason = f.reason;
            if (c.player.isEmpty()) {
                c.player = f.player;
                c.atFault = f.atFault;
            }
            if (c.scorer.isEmpty()) c.scorer = f.scorer;
            return;
        }
    }

    // ---- FotMob's live commentary ---------------------------------------------------------------------------------------

    /** Pure: whether w (lower case, words between single spaces, a space at each end) has any of these phrases. */
    private static boolean has(String w, String... phrases) {
        for (String p : phrases) if (w.contains(" " + p + " ")) return true;
        return false;
    }

    /** Pure: a commentary line as has() reads it: lower case, only letters, digits and apostrophes, a space at each end. */
    private static String plain(String text) {
        return " " + (text == null ? "" : text).toLowerCase(Locale.ROOT).replace('’', '\'').replaceAll("[^a-z0-9']+", " ").trim() + " ";
    }

    /**
     * Pure: the decision a line of FotMob's live commentary says, as a person writes it while the game goes on ("PENALTY
     * STANDS!", "No penalty: VAR overturns it", "the goal is ruled out"): penalty, no_penalty, goal or no_goal; "other"
     * for a check it says is over without saying how; null for a check still going on (or words it can't read).
     */
    static String tickerKind(String text) {
        String w = plain(text);
        if (has(w, "ruled out", "disallowed", "chalked off", "no goal", "goal is overturned", "goal overturned", "won't count",
                "will not count", "doesn't count", "does not count", "not going to count", "goal won't stand", "goal will not stand")) {
            return "no_goal";
        }
        if (has(w, "goal stands", "goal will stand", "goal is given", "goal is confirmed", "goal confirmed", "goal counts",
                "goal is good", "goal is awarded", "goal awarded", "it's a goal", "it is a goal")) {
            return "goal";
        }
        if (has(w, "no penalty", "penalty is overturned", "penalty overturned", "overturns the penalty", "penalty cancelled",
                "penalty is cancelled", "penalty rescinded", "penalty is rescinded", "penalty is taken away", "not a penalty",
                "no spot kick", "spot kick is overturned")) {
            return "no_penalty";
        }
        if (has(w, "says penalty", "penalty stands", "penalty will stand", "penalty is confirmed", "penalty confirmed",
                "penalty is given", "penalty given", "penalty awarded", "penalty is awarded", "awards a penalty", "points to the spot",
                "pointing to the spot", "it's a penalty", "it is a penalty", "spot kick stands")) {
            return "penalty";
        }
        boolean aboutPenalty = has(w, "penalty", "spot kick", "spot");
        if (aboutPenalty && has(w, "play goes on", "play continues", "waves play on", "nothing doing", "nothing given", "no foul")) {
            return "no_penalty";
        }
        if (has(w, "check is complete", "check complete", "check is over", "checks are complete", "check has been completed")) return "other";
        return null;
    }

    /** Pure: what a commentary line says of the referee's call: 1 it stood, -1 VAR changed it, 0 it doesn't say. */
    static int tickerOnField(String text) {
        String w = plain(text);
        if (has(w, "overturn", "overturns", "overturned", "reverses", "reversed", "rescinds", "rescinded", "changes his mind",
                "changes her mind", "changed his mind", "changed her mind", "changes his decision", "changes her decision",
                "taken away", "cancelled")) {
            return -1;
        }
        // Never a bare "stands": the crowd is in the stands.
        if (has(w, "decision stands", "call stands", "penalty stands", "goal stands", "it stands", "will stand", "sticks with",
                "stays with", "confirmed", "says penalty", "isn't having it", "is not having it", "check is complete", "check complete",
                "right call", "correct call", "original decision")) {
            return 1;
        }
        return 0;
    }

    /** Pure: a commentary minute as ESPN writes one (44, -1: "44'"; 45, 2: "45'+2'"). */
    static String tickerMinute(int elapsed, int plus) {
        if (elapsed < 0) return "";
        return plus > 0 ? elapsed + "'+" + plus + "'" : elapsed + "'";
    }

    /**
     * Pure: what VAR did as FotMob's live commentary has it (its VAR lines {key, minute as ESPN writes it, words}, oldest
     * first), with ESPN's commentary ({text, minute}) for who and why: each line that says a decision as that decision,
     * worked out as if ESPN had written "VAR Decision:" at that minute; and the latest that doesn't as the check still
     * going on (none once a decision came after it). A quick check that leaves the referee's call as it was has a line
     * here and nowhere else.
     */
    static List<Seen> ticker(List<String[]> vars, List<String[]> espn) {
        List<Seen> out = new ArrayList<>();
        if (vars == null) return out;
        int lastDecision = -1, lastCheck = -1;
        for (int i = 0; i < vars.size(); i++) {
            if (vars.get(i) == null || vars.get(i).length < 3) continue;
            if (tickerKind(vars.get(i)[2]) != null) lastDecision = i;
            else lastCheck = i;
        }
        for (int i = 0; i < vars.size(); i++) {
            String[] v = vars.get(i);
            if (v == null || v.length < 3) continue;
            String kind = tickerKind(v[2]);
            if (kind == null) continue;
            Seen s = new Seen();
            s.key = "ltc:" + v[0];
            s.call = tickerCall(v, kind, espn);
            out.add(s);
        }
        if (lastCheck > lastDecision) {
            String[] v = vars.get(lastCheck);
            Seen s = new Seen();
            s.key = "ltc:" + v[0];
            s.about = tickerAbout(v, espn);
            out.add(s);
        }
        return out;
    }

    /**
     * Pure: the live commentary's VAR moments less the decisions ESPN's commentary has too (the same kind within four
     * minutes), so one written up in both comes out once, however late the second is.
     */
    static List<Seen> notInEspn(List<Seen> ltc, List<Seen> espn) {
        List<Seen> out = new ArrayList<>();
        if (ltc == null) return out;
        for (Seen s : ltc) {
            boolean both = false;
            for (Seen e : espn == null ? new ArrayList<Seen>() : espn) {
                if (s.call == null || e.call == null || !s.call.kind.equals(e.call.kind)) continue;
                int a = at(s.call.minute), b = at(e.call.minute);
                if (a < 0 || b < 0 || Math.abs(a - b) <= 4) both = true;
            }
            if (!both) out.add(s);
        }
        return out;
    }

    /** Pure: the place in ESPN's lines (oldest first) for a line at this minute: after the last at or before it. */
    private static int placeFor(List<String[]> lines, String minute) {
        int when = at(minute), i = lines.size();
        while (i > 0 && when >= 0 && at(lines.get(i - 1)[1]) > when) i--;
        return i;
    }

    /** Pure: a decision from FotMob's line {key, minute, words}, its kind as tickerKind read it, its who and why from ESPN's. */
    static Call tickerCall(String[] v, String kind, List<String[]> espn) {
        String minute = v[1] == null ? "" : v[1];
        Call c = null;
        if (!"other".equals(kind)) {
            List<String[]> lines = espn == null ? new ArrayList<>() : new ArrayList<>(espn);
            int at = placeFor(lines, minute);
            String words = "penalty".equals(kind) ? "Penalty" : "no_penalty".equals(kind) ? "No Penalty" : "goal".equals(kind) ? "Goal" : "No Goal";
            lines.add(at, new String[]{"VAR Decision: " + words + ".", minute, "ltc"});
            c = decision(lines, at);
        }
        if (c == null) {
            c = new Call();
            c.kind = kind;
            c.minute = minute;
            if ("other".equals(kind)) c.words = "Check complete";
        }
        int said = tickerOnField(v[2]);
        if (said != 0) c.onField = said;
        if (c.team.isEmpty() && ("penalty".equals(c.kind) || "no_penalty".equals(c.kind))) c.team = penaltyTeam(espn, minute);
        return c;
    }

    /** Pure: the side ESPN's lines give a penalty to near this minute ("Penalty Whitebrook. … draws a foul"); "" when none. */
    static String penaltyTeam(List<String[]> espn, String minute) {
        if (espn == null) return "";
        int when = at(minute);
        String team = "";
        for (String[] l : espn) {
            int w = at(l[1]);
            if (when >= 0 && w >= 0 && (w > when + 1 || w < when - 4)) continue;
            Matcher m = DRAWS.matcher(l[0] == null ? "" : l[0]);
            if (m.find()) team = m.group(1).trim();
        }
        return team;
    }

    /** Pure: a check under way from FotMob's line: what it is about ({about, reason, player}), from its words and ESPN's. */
    static String[] tickerAbout(String[] v, List<String[]> espn) {
        List<String[]> lines = espn == null ? new ArrayList<>() : new ArrayList<>(espn);
        int at = placeFor(lines, v[1]);
        lines.add(at, new String[]{"VAR checking.", v[1], "ltc"});
        String[] about = checkAbout(lines, at);
        String said = aboutOf(v[2]);
        if (!said.isEmpty()) about[0] = said;
        return about;
    }

    // ---- what was read, kept between the alert service and the board (any thread) --------------------------------------

    private static final Map<String, Call> calls = new LinkedHashMap<String, Call>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Call> e) {
            return size() > 60;
        }
    };
    private static final Map<String, String[]> checks = new LinkedHashMap<String, String[]>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String[]> e) {
            return size() > 60;
        }
    };
    private static final Map<String, List<String[]>> fotmobVars = new HashMap<>(), fotmobReds = new HashMap<>();
    private static final Map<String, Long> checkStarted = new HashMap<>(), decidedAt = new HashMap<>();
    private static final Set<String> fotmobSeen = new HashSet<>(), fotmobGames = new HashSet<>();

    /** A decision as worked out from the commentary, by its incident's key (and its game's, for callNear). */
    static synchronized void remember(String game, String key, Call c) {
        if (key == null || c == null) return;
        calls.put(key, c);
        if (game != null) gameOf.put(key, game);
        gameOf.keySet().retainAll(calls.keySet());
    }

    static synchronized Call callFor(String key) {
        return key == null ? null : calls.get(key);
    }

    /**
     * The game's decision of this kind worked out from the commentary within two minutes of minute (a goal ruled out
     * that came as a key event rather than a commentary line); null when there is none.
     */
    static synchronized Call callNear(String game, String kind, String minute) {
        if (game == null || kind == null) return null;
        int when = at(minute);
        Call best = null;
        for (Map.Entry<String, Call> e : calls.entrySet()) {
            Call c = e.getValue();
            if (!game.equals(gameOf.get(e.getKey())) || !kind.equals(c.kind)) continue;
            if (when >= 0 && Math.abs(at(c.minute) - when) > 2) continue;
            best = c;
        }
        return best;
    }

    private static final Map<String, String> gameOf = new HashMap<>();

    /** A check under way as worked out from the commentary ({about, reason, player}), by its incident's key. */
    static synchronized void rememberCheck(String key, String[] about) {
        if (key != null && about != null) checks.put(key, about);
    }

    static synchronized String[] checkFor(String key) {
        return key == null ? null : checks.get(key);
    }

    /** FotMob's latest read for a game: its VAR events and its red cards with their reasons. */
    static synchronized void fotmob(String game, List<String[]> vars, List<String[]> reds) {
        if (game == null) return;
        fotmobVars.put(game, vars == null ? new ArrayList<>() : new ArrayList<>(vars));
        fotmobReds.put(game, reds == null ? new ArrayList<>() : new ArrayList<>(reds));
    }

    static synchronized List<String[]> fotmobVars(String game) {
        List<String[]> v = game == null ? null : fotmobVars.get(game);
        return v == null ? new ArrayList<>() : new ArrayList<>(v);
    }

    static synchronized List<String[]> fotmobReds(String game) {
        List<String[]> v = game == null ? null : fotmobReds.get(game);
        return v == null ? new ArrayList<>() : new ArrayList<>(v);
    }

    /** FotMob's VAR events not seen before for a game (none on its first read: they were before the board came up). */
    static synchronized List<String[]> freshFotmob(String game, List<String[]> vars) {
        List<String[]> out = new ArrayList<>();
        if (game == null || vars == null) return out;
        boolean first = fotmobGames.add(game);
        for (String[] e : vars) {
            String k = game + "|" + e[0] + "|" + e[1] + "|" + e[2];
            if (fotmobSeen.add(k) && !first) out.add(e);
        }
        if (fotmobSeen.size() > 300) fotmobSeen.clear();
        return out;
    }

    /** A check began for a game now (kept from its first sight), for how long it took. */
    static synchronized void checkStarted(String game, long now) {
        if (game == null) return;
        Long was = checkStarted.get(game);
        if (was == null || now - was > CHECK_MAX_MS) checkStarted.put(game, now);
    }

    /** The game's check as begun at this time (tests). */
    static synchronized void checkBegan(String game, long at) {
        if (game != null) checkStarted.put(game, at);
    }

    /** How long the game's check took, to now; 0 when none began within the last few minutes. */
    static synchronized long checkLength(String game, long now) {
        Long was = game == null ? null : checkStarted.remove(game);
        return was == null || now - was > CHECK_MAX_MS + 60_000L ? 0 : now - was;
    }

    /** Whether a check is known to be under way for the game (begun, not yet decided). */
    static synchronized boolean checking(String game, long now) {
        Long was = game == null ? null : checkStarted.get(game);
        return was != null && now - was <= CHECK_MAX_MS;
    }

    /** Whether a decision was shown for the game within the last three minutes (one from each source is one). */
    static synchronized boolean decidedLately(String game, long now) {
        Long was = game == null ? null : decidedAt.get(game);
        return was != null && now - was < 180_000L;
    }

    static synchronized void decided(String game, long now) {
        if (game != null) decidedAt.put(game, now);
    }

    /** Pure: a check as an alert's card has it ({about, reason, player}) from its words. */
    static String[] checkOf(String headline) {
        String[] o = noCheck();
        o[0] = aboutOf(headline);
        return o;
    }

    /** Pure: a decision as an alert's card has it (its label, "NO PENALTY", "GOAL" "STANDS"...), when nothing more was read. */
    static Call fromCard(String label, String small, String minute) {
        String l = ((label == null ? "" : label) + " " + (small == null ? "" : small)).trim().toUpperCase(Locale.ROOT);
        Call c = new Call();
        c.minute = minute == null ? "" : minute;
        if (l.startsWith("NO PENALTY")) c.kind = "no_penalty";
        else if (l.startsWith("PENALTY")) c.kind = "penalty";
        else if (l.startsWith("NO GOAL")) c.kind = "no_goal";
        else if (l.startsWith("GOAL")) c.kind = "goal";
        else if (l.startsWith("RED")) c.kind = "red";
        else {
            c.kind = "other";
            c.words = label == null ? "" : label;
        }
        return c;
    }
}
