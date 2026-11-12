package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The half-time widgets' fun facts and quiz (HalfTimeFun), as the phone's: Bangladesh first (two of its facts for each
 * of the rest, from Ambient mode's lists), the world's and football's taking turns in between; and quiz questions with
 * four answers each, played for points (10 a right answer, 5 more for each one in a row, at most 30). Each half-time
 * takes the next ones from a shuffled deck. Pure: the tests check it.
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

    /** {question, the right answer, three wrong ones}: short answers, for four buttons. */
    static final String[][] QUIZ = {
            {"Which country won the first World Cup, in 1930?", "Uruguay", "Brazil", "Italy", "Argentina"},
            {"Who has scored the most Premier League goals?", "Alan Shearer", "Wayne Rooney", "Harry Kane", "Thierry Henry"},
            {"Which club went the whole 2003–04 Premier League season unbeaten?", "Arsenal", "Chelsea", "Man United", "Liverpool"},
            {"How many times have Brazil won the World Cup?", "Five", "Four", "Three", "Six"},
            {"Who won the Premier League in 2015–16 at odds of 5000-1?", "Leicester City", "West Ham", "Southampton", "Everton"},
            {"What is the capital of Bangladesh?", "Dhaka", "Chittagong", "Sylhet", "Khulna"},
            {"What is the largest mangrove forest in the world?", "The Sundarbans", "The Amazon", "Borneo", "The Everglades"},
            {"How many points did Man City get in 2017–18, the Premier League record?", "100", "99", "98", "96"},
            {"In which year did the Premier League begin?", "1992", "1988", "1996", "2000"},
            {"What is Bangladesh's national animal?", "Bengal tiger", "Elephant", "Leopard", "Water buffalo"},
            {"Where was the first Formula One World Championship race, in 1950?", "Silverstone", "Monza", "Monaco", "Spa"},
            {"Which planet takes 165 years to go once round the Sun?", "Neptune", "Uranus", "Saturn", "Jupiter"},
            {"What is Bangladesh's cricket team nicknamed?", "The Tigers", "The Lions", "The Eagles", "The Falcons"},
            {"How many bones does an adult have?", "206", "186", "226", "306"},
            {"What is Bangladesh's national fish?", "Hilsa (ilish)", "Rui", "Pangas", "Koi"},
            {"Who scored 36 Premier League goals in 2022–23?", "Erling Haaland", "Harry Kane", "Mohamed Salah", "Ivan Toney"},
            {"What is the national sport of Bangladesh?", "Kabaddi", "Cricket", "Football", "Hockey"},
            {"How many hearts does an octopus have?", "Three", "One", "Two", "Eight"},
            {"When is Victory Day in Bangladesh?", "16 December", "26 March", "21 February", "15 August"},
            {"Which musical does You'll Never Walk Alone come from?", "Carousel", "Oklahoma!", "Cats", "Grease"},
            {"Which stadium is nicknamed the Theatre of Dreams?", "Old Trafford", "Anfield", "Wembley", "Goodison Park"},
            {"How many players does each team have on the pitch?", "Eleven", "Ten", "Twelve", "Nine"},
            {"What is a group of flamingos called?", "A flamboyance", "A parade", "A pride", "A gaggle"},
            {"Who wrote the national anthems of both Bangladesh and India?", "Rabindranath Tagore", "Kazi Nazrul Islam", "Jasimuddin", "Lalon"},
            {"How long is a football match, without added time?", "90 minutes", "80 minutes", "100 minutes", "120 minutes"},
            {"How many Formula One world titles has Lewis Hamilton won?", "Seven", "Six", "Five", "Eight"},
            {"Which country hosted the 2022 World Cup?", "Qatar", "Russia", "Brazil", "Saudi Arabia"},
            {"Who won the 2022 World Cup?", "Argentina", "France", "Croatia", "Brazil"},
            {"Which club is nicknamed the Toffees?", "Everton", "Burnley", "Fulham", "Brentford"},
            {"Which club plays at the Etihad Stadium?", "Man City", "Man United", "Arsenal", "Newcastle"},
            {"What colour card sends a player off?", "Red", "Yellow", "Blue", "Green"},
            {"Which is the largest ocean?", "The Pacific", "The Atlantic", "The Indian", "The Arctic"},
            {"How many continents are there?", "Seven", "Five", "Six", "Eight"},
            {"What do bees make from nectar?", "Honey", "Milk", "Silk", "Jam"},
            {"Which is the fastest land animal?", "The cheetah", "The lion", "The horse", "The gazelle"},
            {"What is the tallest mountain in the world?", "Everest", "K2", "Kilimanjaro", "Mont Blanc"},
            {"What is Bangladesh's national bird?", "Magpie robin", "Kingfisher", "Myna", "Peacock"},
            {"Which national team are called the Three Lions?", "England", "Wales", "Scotland", "Ireland"},
            {"Who won Euro 2024?", "Spain", "England", "France", "Germany"},
            {"Which Formula One team's cars are red?", "Ferrari", "McLaren", "Williams", "Alpine"},
            {"How many points is a win worth in the Premier League?", "Three", "Two", "One", "Four"},
            {"Which month of the Islamic calendar is Ramadan?", "The ninth", "The first", "The seventh", "The twelfth"},
            {"How many daily prayers are there in Islam?", "Five", "Three", "Four", "Seven"},
            {"Which is the biggest planet?", "Jupiter", "Saturn", "Neptune", "Earth"},
            {"Which animal is called the ship of the desert?", "The camel", "The horse", "The elephant", "The donkey"},
            {"Who is England men's record goalscorer?", "Harry Kane", "Wayne Rooney", "Bobby Charlton", "Gary Lineker"},
            {"Which club has won the European Cup the most times?", "Real Madrid", "AC Milan", "Liverpool", "Bayern Munich"},
            {"Which ground do Liverpool play at?", "Anfield", "Goodison Park", "Villa Park", "St James' Park"},
            {"Which river flows through London?", "The Thames", "The Severn", "The Mersey", "The Trent"},
            {"How many sides does a hexagon have?", "Six", "Five", "Seven", "Eight"},
            {"Which is the hottest planet?", "Venus", "Mercury", "Mars", "Jupiter"}};

    /** A question as asked: its four answers in a shuffled order, and which of them is right. */
    static final class Question {
        final String text;
        final String[] answers;
        final int right;

        Question(String text, String[] answers, int right) {
            this.text = text;
            this.answers = answers;
            this.right = right;
        }

        String rightAnswer() {
            return answers[right];
        }
    }

    /** A bank's row as asked, its answers shuffled. */
    static Question ask(String[] row, Random r) {
        List<String> a = new ArrayList<>();
        for (int i = 1; i < row.length; i++) a.add(row[i]);
        Collections.shuffle(a, r);
        return new Question(row[0], a.toArray(new String[0]), a.indexOf(row[1]));
    }

    /** Points for a right answer, given how many in a row it makes (1 for the first): 10, 15, 20 ... at most 30. */
    static int points(int inARow) {
        return Math.min(30, 10 + 5 * Math.max(0, inARow - 1));
    }

    /** The answer picked after an arrow on the four answers, two by two (0 1 / 2 3); it stays at an edge. */
    static int move(int at, int dx, int dy) {
        int col = Math.max(0, Math.min(1, at % 2 + dx)), row = Math.max(0, Math.min(1, at / 2 + dy));
        return row * 2 + col;
    }

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

    /** The next question from the shuffled bank, its answers shuffled too. */
    static synchronized Question question() {
        if (quiz == null || quizAt >= quiz.size()) {
            quiz = new ArrayList<>();
            Collections.addAll(quiz, QUIZ);
            Collections.shuffle(quiz, RANDOM);
            quizAt = 0;
        }
        return ask(quiz.get(quizAt++), RANDOM);
    }
}
