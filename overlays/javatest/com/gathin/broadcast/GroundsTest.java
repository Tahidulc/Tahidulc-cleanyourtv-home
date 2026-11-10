package com.gathin.broadcast;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GroundsTest {
    @Test
    public void aViewFromTheStandsBeatsTheOutsideAndOnlyFreePhotosCount() {
        int stands = Grounds.score("File:-2007-05-05 Aston Villa v Sheffield United, Villa Park from the Holt End (11).JPG".replace(".JPG", ".jpg"),
                2560, 1440, "image/jpeg", "CC BY-SA 3.0", "Villa Park", true);
        int facade = Grounds.score("File:Facade of North Stand, Villa Park.jpg", 3264, 2448, "image/jpeg", "CC BY-SA 3.0", "Villa Park", true);
        assertTrue(stands > facade);
        assertTrue(Grounds.score("File:Old Trafford (interior), Manchester United.jpg", 4544, 2452, "image/jpeg", "CC BY 2.0", "Old Trafford", false) > 0);
        // not free, too small, upright, not a JPEG, or (outside the ground's category) not naming it
        assertTrue(Grounds.score("File:Villa Park pitch.jpg", 3000, 1700, "image/jpeg", "Fair use", "Villa Park", true) < 0);
        assertTrue(Grounds.score("File:Villa Park pitch.jpg", 1000, 600, "image/jpeg", "CC BY 2.0", "Villa Park", true) < 0);
        assertTrue(Grounds.score("File:Villa Park pitch.jpg", 2000, 3000, "image/jpeg", "CC BY 2.0", "Villa Park", true) < 0);
        assertTrue(Grounds.score("File:Villa Park pitch.png", 3000, 1700, "image/png", "CC BY 2.0", "Villa Park", true) < 0);
        assertTrue(Grounds.score("File:Interior of Park-Villa Rieter 2.jpg", 11768, 4410, "image/jpeg", "CC BY-SA 4.0", "Villa Park", false) < 0);
        // "design" is not a sign
        assertTrue(Grounds.score("File:Villa Park design of the pitch.jpg", 3000, 1700, "image/jpeg", "CC0", "Villa Park", true) > 0);
    }
}
