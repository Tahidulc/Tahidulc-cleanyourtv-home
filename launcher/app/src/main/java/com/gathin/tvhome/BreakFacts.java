package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The half-time break's fun facts and quiz (HalfTimeBreak), as the phone's: Bangladesh first (two of its facts for each
 * of the rest, from Ambient mode's lists), the world's and football's taking turns in between; and quiz questions, the
 * answer shown a few seconds after. Each break takes the next ones from a shuffled deck. Pure: the tests check it.
 */
final class BreakFacts {
    private BreakFacts() { }

    static final String BANGLADESH = "BANGLADESH", WORLD = "DID YOU KNOW?", FOOTBALL = "FOOTBALL";

    static final String[] SPORT = {
            "The Premier League began in 1992, when the old First Division's clubs broke away to form it.",
            "Arsenal went the whole 2003–04 Premier League season unbeaten: 26 wins and 12 draws.",
            "Manchester City reached 100 points in 2017–18, the most in a Premier League season.",
            "Alan Shearer has scored the most Premier League goals: 260.",
            "Erling Haaland scored 36 Premier League goals in 2022–23, a record for a 38-game season.",
            "The first World Cup was played in Uruguay in 1930, and Uruguay won it.",
            "Brazil have won the World Cup five times, more than any other country.",
            "Leicester City won the Premier League in 2015–16, after bookmakers had them at 5000-1.",
            "The quickest Premier League goal came after 7.69 seconds, by Shane Long in 2019.",
            "Ryan Giggs won 13 Premier League titles, all with Manchester United.",
            "Penalty kicks were brought into football in 1891.",
            "Liverpool's anthem, You'll Never Walk Alone, comes from the 1945 musical Carousel.",
            "Old Trafford is nicknamed the Theatre of Dreams.",
            "The World Cup trophy is made of 18-carat gold and weighs just over 6 kg.",
            "Silverstone hosted the very first Formula One World Championship race, in 1950.",
            "Lewis Hamilton and Michael Schumacher share the record of seven Formula One world titles.",
            "A Premier League pitch is 105 metres long and 68 metres wide.",
            "Hamza Choudhury, an FA Cup winner with Leicester in 2021, first played for Bangladesh in 2025."};

    /** {question, answer}. */
    static final String[][] QUIZ = {
            {"Which country won the first World Cup, in 1930?", "Uruguay"},
            {"Who has scored the most Premier League goals?", "Alan Shearer, with 260"},
            {"Which club went the whole 2003–04 Premier League season unbeaten?", "Arsenal, the Invincibles"},
            {"How many times have Brazil won the World Cup?", "Five"},
            {"Which team won the Premier League in 2015–16 at odds of 5000-1?", "Leicester City"},
            {"What is the capital of Bangladesh?", "Dhaka"},
            {"What is the largest mangrove forest in the world?", "The Sundarbans"},
            {"How many points did Manchester City get in 2017–18, the Premier League record?", "100"},
            {"In which year did the Premier League begin?", "1992"},
            {"What is Bangladesh's national animal?", "The Royal Bengal tiger"},
            {"Where was the first Formula One World Championship race held?", "Silverstone, in 1950"},
            {"Which planet takes 165 years to go once round the Sun?", "Neptune"},
            {"What is Bangladesh's cricket team nicknamed?", "The Tigers"},
            {"How many bones does an adult have?", "206"},
            {"What is Bangladesh's national fish?", "The hilsa, or ilish"},
            {"Who scored 36 Premier League goals in 2022–23?", "Erling Haaland"},
            {"What is the national sport of Bangladesh?", "Kabaddi"},
            {"How many hearts does an octopus have?", "Three"},
            {"When is Victory Day in Bangladesh?", "16 December"},
            {"Which musical does You'll Never Walk Alone come from?", "Carousel"},
            {"Which stadium is nicknamed the Theatre of Dreams?", "Old Trafford"},
            {"How many players does each team have on the pitch?", "Eleven"},
            {"What is a group of flamingos called?", "A flamboyance"},
            {"Who wrote the national anthems of both Bangladesh and India?", "Rabindranath Tagore"}};

    /** {kind, fact} in order: two of Bangladesh's, then one of the world's or football's (taking turns). */
    static List<String[]> deck(Random r) {
        List<String> bd = new ArrayList<>(), world = new ArrayList<>(), sport = new ArrayList<>();
        Collections.addAll(bd, AmbientFacts.BANGLADESH);
        Collections.addAll(world, AmbientFacts.WORLD);
        Collections.addAll(sport, SPORT);
        Collections.shuffle(bd, r);
        Collections.shuffle(world, r);
        Collections.shuffle(sport, r);
        List<String[]> out = new ArrayList<>();
        int b = 0, w = 0, s = 0, turn = 0;
        while (b < bd.size() || w < world.size() || s < sport.size()) {
            for (int i = 0; i < 2 && b < bd.size(); i++) out.add(new String[]{BANGLADESH, bd.get(b++)});
            boolean football = (turn++ % 2 == 1 && s < sport.size()) || w >= world.size();
            if (football && s < sport.size()) out.add(new String[]{FOOTBALL, sport.get(s++)});
            else if (w < world.size()) out.add(new String[]{WORLD, world.get(w++)});
        }
        return out;
    }

    private static final Random RANDOM = new Random();
    private static List<String[]> facts, quiz;
    private static int factAt, quizAt;

    /** The next n facts, {kind, fact}. */
    static synchronized List<String[]> next(int n) {
        List<String[]> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (facts == null || factAt >= facts.size()) {
                facts = deck(RANDOM);
                factAt = 0;
            }
            out.add(facts.get(factAt++));
        }
        return out;
    }

    /** The next n quiz questions, {question, answer}. */
    static synchronized List<String[]> questions(int n) {
        List<String[]> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (quiz == null || quizAt >= quiz.size()) {
                quiz = new ArrayList<>();
                Collections.addAll(quiz, QUIZ);
                Collections.shuffle(quiz, RANDOM);
                quizAt = 0;
            }
            out.add(quiz.get(quizAt++));
        }
        return out;
    }
}
