package com.gathin.tvhomephone.live;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The ad-break screen's half-time widgets' fun facts and quiz (HalfTimeFun), as the TV's: Bangladesh first (two of its
 * facts for each of the rest), the world's and football's in between; and quiz questions with four answers each,
 * played for points (10 a right answer, 5 more for each one in a row, at most 30). Each half-time takes the next ones
 * from a shuffled deck, so the breaks of one evening don't repeat. Pure: the tests check it.
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
