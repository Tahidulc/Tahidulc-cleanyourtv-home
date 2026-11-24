package com.gathin.broadcast;

import org.json.JSONObject;

/**
 * What Ask AI says to Claude, and what it makes of the answer: the system prompt for a scoreboard (the spec, the base
 * looks, the rules a board on a TV across the room needs, broadcast style), the family's own words with the match's
 * context (the two clubs' names and colours, the competition; nothing about the family), and the design back as a
 * BoardSpec held to the same rules as the offline generator. Pure, so the tests see exactly what is sent.
 */
public final class AskAi {
    private AskAi() {
    }

    /** At most this much of the family's words is sent. */
    public static final int WORDS_MAX = 600;

    /** The scoreboard system prompt (fixed, so it is the same every time). */
    public static String boardSystem() {
        StringBuilder b = new StringBuilder();
        b.append("You design live football scoreboards (the score bug in the top corner of a TV broadcast) for a family's ")
                .append("TV and phone apps. Answer with one design as JSON in the schema given; the app draws it.\n\n")
                .append("The board shows: the competition's logo in its own block, each club's colour strip, crest and three-letter ")
                .append("code, the score in its own cell, and the match clock in its own cell. Added time appears as a red tab ")
                .append("beside the board. A goal sweeps the scorers' colour across it.\n\n")
                .append("base is the layout:\n")
                .append("- CLASSIC: one row, the competition block, clubs, a score cell in the middle, the clock cell at the end.\n")
                .append("- GLASS: a pill with see-through score and clock cells over the panel (use #AARRGGBB cells, e.g. #40FFFFFF).\n")
                .append("- LIGHT: a light panel with dark words; the competition block stays dark.\n")
                .append("- CLUB: each club's half filled in its own colour (strips are ignored).\n")
                .append("- MINIMAL: words only on one quiet panel, no cells.\n")
                .append("- CRESTS: the two crests either side of the score, the clock in its own box, no names.\n")
                .append("- STACKED: the clubs one above the other, each with its goals in a box.\n")
                .append("- CARD: a light card, each crest on it, the score over the clock in a coloured middle block.\n")
                .append("- FROSTED: silvery frost with see-through cells, crests, score and clock.\n\n")
                .append("colours (all \"#RRGGBB\"; cells may be \"#AARRGGBB\"): panel is the board's solid ground; panelFoot is ")
                .append("its colour at the foot for a gentle vertical gradient, or \"\" for flat; comp is the competition block; ")
                .append("score and scoreFoot the score cell top to foot; clock the clock cell; text the club codes on the panel; ")
                .append("scoreText the score's figures on its cell; clockText the clock's figures on its cell; dim quieter words; ")
                .append("accent a highlight (the ring round crests, a fine line along the foot); stoppage the red for added ")
                .append("time (keep #D7192F unless asked otherwise); added the added-time tab (normally the same red).\n")
                .append("radius: corner radius as a share of the height, 0 square to 0.5 a pill. border: width 0 to 0.1 of the ")
                .append("height and its colour. finish: GLOSS (a sheen across the top), FLAT, or FROSTED. font: OUTFIT (geometric, ")
                .append("modern), KHAND (condensed, sporty), BARLOW (condensed), BARLOW_HEAVY (heavy condensed, retro), ")
                .append("BARLOW_SEMI (semi-condensed), MONTSERRAT (wide, bold), INTER (neutral, minimal), RUBIK (rounded), SYSTEM. ")
                .append("crest: PLAIN, DISC (on a light disc, for dark crests on dark panels), RING (in a ring of the accent). ")
                .append("strips: each club's colour strip beside its code. accentLine: a fine accent line along the foot.\n\n")
                .append("Design rules (it is watched on a TV about three metres away, over a moving picture):\n")
                .append("- Every word at least 4.5:1 contrast on what it sits on: text on panel, scoreText on the score cell, ")
                .append("clockText on the clock cell, white on stoppage and added.\n")
                .append("- The panel is always solid (it covers the channel's own scoreboard). The score cell must stand off ")
                .append("the panel.\n")
                .append("- Broadcast style: restrained, premium, two or three colours and one accent, no rainbow, no neon ")
                .append("on neon. Deep, rich grounds read best; pure white panels glare all game, prefer soft light greys.\n")
                .append("- Use the clubs' colours where asked, but keep them where they read: on dark clubs' colours use ")
                .append("crest DISC and turn strips off if a strip would vanish into the panel.\n")
                .append("- No logos, brand names, sponsors or text in the design; the name is a short title of your own for it ")
                .append("(at most 40 characters, no club or competition trademarks).\n")
                .append("- Set v to 1.");
        return b.toString();
    }

    /** The family's words and the match's context, as the user's message. */
    public static String boardUser(String words, BoardDesigner.Brief match) {
        StringBuilder b = new StringBuilder();
        String w = clean(words);
        b.append("What we'd like: ").append(w.isEmpty() ? "Surprise us: something stylish and professional." : w).append("\n\n");
        if (match != null) {
            b.append("The match on now (for its colours):\n");
            if (!match.homeName.isEmpty() || match.home != 0) {
                b.append("- Home: ").append(match.homeName.isEmpty() ? "home club" : clean(match.homeName))
                        .append(match.home != 0 ? ", colour " + BoardColours.hex(BoardColours.opaque(match.home)) : "").append('\n');
            }
            if (!match.awayName.isEmpty() || match.away != 0) {
                b.append("- Away: ").append(match.awayName.isEmpty() ? "away club" : clean(match.awayName))
                        .append(match.away != 0 ? ", colour " + BoardColours.hex(BoardColours.opaque(match.away)) : "").append('\n');
            }
            if (!match.comp.isEmpty()) b.append("- Competition: ").append(clean(match.comp)).append('\n');
        }
        return b.toString().trim();
    }

    /** Words made plain and short (no control characters; at most WORDS_MAX). */
    static String clean(String s) {
        String t = s == null ? "" : s.replaceAll("[\\x00-\\x09\\x0B-\\x1F\\x7F]", " ").trim().replaceAll("[ \\t]+", " ");
        return t.length() > WORDS_MAX ? t.substring(0, WORDS_MAX) : t;
    }

    /** Claude's answer as a board, held to the generator's rules (a word that wouldn't read is made to). */
    public static BoardSpec board(JSONObject answer, BoardDesigner.Brief match) {
        BoardSpec s = BoardSpec.from(answer);
        return BoardDesigner.rules(s, match);
    }

    /** A scoreboard designed by Claude: blocking (never the main thread). */
    public static BoardSpec designBoard(Claude claude, String key, String model, String words, BoardDesigner.Brief match)
            throws Claude.Failure {
        JSONObject a = claude.ask(key, model, boardSystem(), boardUser(words, match), BoardSpec.schema());
        return board(a, match);
    }

    /** A key as Settings shows it: its start and its last four, the rest hidden. */
    public static String masked(String key) {
        String k = key == null ? "" : key.trim();
        if (k.isEmpty()) return "";
        if (k.length() <= 12) return "••••";
        String start = k.startsWith("sk-ant-") ? "sk-ant-" : k.substring(0, 3);
        return start + "••••••••" + k.substring(k.length() - 4);
    }

    /** Whether a key looks like an Anthropic API key (it is still sent as typed; this is for a hint only). */
    public static boolean looksLikeKey(String key) {
        String k = key == null ? "" : key.trim();
        return k.startsWith("sk-ant-") && k.length() >= 30 && !k.contains(" ");
    }
}
