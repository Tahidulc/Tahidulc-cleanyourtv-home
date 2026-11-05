package com.gathin.tvhomephone.live;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Broadcast mode's fun facts and quiz, as the TVs' Ambient mode has them: Bangladesh first (two of its facts for each
 * of the rest), the world's and football's in between; and quiz questions, the answer shown a few seconds after. Each
 * break takes the next ones from a shuffled deck, so the breaks of one evening don't repeat. Pure: the tests check it.
 */
final class Facts {
    private Facts() {}

    static final String BANGLADESH = "BANGLADESH", WORLD = "DID YOU KNOW?", FOOTBALL = "FOOTBALL";

    static final String[] BD = {
            "The Sundarbans, shared with India, is the largest mangrove forest in the world.",
            "The Royal Bengal tiger is Bangladesh's national animal, and it swims between the Sundarbans' islands.",
            "Cox's Bazar has one of the longest natural sea beaches in the world, about 120 km of sand.",
            "Bangla is one of the ten most spoken languages in the world.",
            "International Mother Language Day, 21 February, honours the 1952 Language Movement in Dhaka.",
            "The Shaheed Minar in Dhaka remembers those who died for the Bangla language in 1952.",
            "Bangladesh's national flower is the water lily, the shapla.",
            "The jackfruit is Bangladesh's national fruit, and the biggest tree fruit in the world.",
            "The hilsa, or ilish, is Bangladesh's national fish.",
            "The national bird is the oriental magpie-robin, the doyel.",
            "Bangladesh has around 700 rivers.",
            "Bangladesh sits on the Ganges–Brahmaputra delta, the largest river delta in the world.",
            "The Padma Bridge, opened in 2022, is over 6 km long.",
            "Dhaka is known as the City of Mosques.",
            "The Star Mosque in Dhaka is covered in mosaic stars made from pieces of china.",
            "Despite its name, the Sixty Dome Mosque in Bagerhat has 77 domes on its roof.",
            "The Sixty Dome Mosque and the Buddhist monastery at Paharpur are UNESCO World Heritage Sites.",
            "Bangladesh won its independence in 1971; Victory Day is 16 December.",
            "Independence Day in Bangladesh is 26 March.",
            "Rabindranath Tagore wrote the national anthems of both Bangladesh and India.",
            "The national anthem, Amar Shonar Bangla, means My Golden Bengal.",
            "Muhammad Yunus and Grameen Bank won the Nobel Peace Prize in 2006 for small loans that change lives.",
            "Bangladesh is one of the world's biggest makers of clothes.",
            "Dhaka has been called the rickshaw capital of the world.",
            "The red circle on Bangladesh's flag sits a little towards the pole, so it looks centred as the flag flies.",
            "Srimangal in Sylhet is called the tea capital of Bangladesh.",
            "Pohela Boishakh, the Bengali New Year, is celebrated on 14 April with colourful parades.",
            "The Bengali calendar has six seasons: summer, monsoon, autumn, late autumn, winter and spring.",
            "Kabaddi is the national sport of Bangladesh.",
            "Bangladesh's cricket team is nicknamed the Tigers.",
            "Saint Martin's Island is Bangladesh's only coral island.",
            "Ratargul in Sylhet is a freshwater swamp forest you can explore by boat.",
            "Lalbagh Fort in Dhaka was built by the Mughals in the 1600s.",
            "Nakshi kantha are Bengal's beautiful hand-embroidered quilts.",
            "Jamdani weaving from Bangladesh is on UNESCO's list of the world's living heritage.",
            "Baul songs, Bengal's mystic folk music, are on UNESCO's list of the world's living heritage.",
            "Rajshahi is famous for its mangoes.",
            "Kacchi biryani is one of Dhaka's best-loved dishes.",
            "Fuchka, crispy shells filled with spicy tamarind water, is a favourite street snack in Bangladesh.",
            "Bangladesh is one of the most densely populated countries in the world."};

    static final String[] EVERYWHERE = {
            "Octopuses have three hearts.",
            "Honey never spoils: pots of it have been found, still edible, in ancient Egyptian tombs.",
            "A day on Venus is longer than its year.",
            "Bananas are berries, but strawberries aren't.",
            "Sharks have been around longer than trees.",
            "Wombat poo is cube-shaped.",
            "The Eiffel Tower can grow about 15 cm taller in summer as its iron warms up.",
            "Sea otters hold hands while they sleep so they don't drift apart.",
            "A group of flamingos is called a flamboyance.",
            "Butterflies taste with their feet.",
            "Lightning is about five times hotter than the surface of the Sun.",
            "Koalas can sleep up to 22 hours a day.",
            "The Moon is drifting away from Earth by about 3.8 cm every year.",
            "Hummingbirds can fly backwards.",
            "The shortest war in history, between Britain and Zanzibar in 1896, lasted under an hour.",
            "An ostrich's eye is bigger than its brain.",
            "Starfish have no brain.",
            "A fluffy cumulus cloud can weigh around 500 tonnes.",
            "Elephants can't jump.",
            "Neptune takes 165 Earth years to go once round the Sun.",
            "Giraffes have seven bones in their necks, the same as people.",
            "Babies are born with about 300 bones; adults have 206.",
            "Cats can't taste sweet things.",
            "Rainbows are really full circles; from the ground we only see part of one.",
            "Polar bears have black skin under their fur.",
            "Dolphins sleep with one half of their brain at a time.",
            "A teaspoon of a neutron star would weigh about a billion tonnes.",
            "There are more trees on Earth than stars in the Milky Way.",
            "Penguins have knees, hidden inside their bodies.",
            "The Great Wall of China can't be seen from space with the naked eye."};

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
        Collections.addAll(bd, BD);
        Collections.addAll(world, EVERYWHERE);
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
    private static List<String[]> facts;
    private static List<String[]> quiz;
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
