package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ambient mode's facts for the bar along the foot: Bangladesh first (two of its facts for every one of the rest), and
 * interesting facts from everywhere else. Each fits one line of the bar.
 */
final class AmbientFacts {
    private AmbientFacts() { }

    static final String[] BANGLADESH = {
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

    static final String[] WORLD = {
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

    /** A fresh order for the bar: Bangladesh first and twice as often, the rest in between. */
    static List<String[]> order() {
        List<String> bd = new ArrayList<>(), world = new ArrayList<>();
        Collections.addAll(bd, BANGLADESH);
        Collections.addAll(world, WORLD);
        Collections.shuffle(bd);
        Collections.shuffle(world);
        List<String[]> out = new ArrayList<>();
        int b = 0, w = 0;
        while (b < bd.size() || w < world.size()) {
            for (int i = 0; i < 2 && b < bd.size(); i++) out.add(new String[]{"BANGLADESH", bd.get(b++)});
            if (w < world.size()) out.add(new String[]{"DID YOU KNOW?", world.get(w++)});
        }
        return out;
    }
}
