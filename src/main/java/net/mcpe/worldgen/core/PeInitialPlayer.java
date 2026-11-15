package net.mcpe.worldgen.core;

import java.util.function.IntPredicate;
import java.util.function.IntSupplier;

/** First join of a new, living player in PE 0.11.1. Coordinates are Java feet coordinates. */
public final class PeInitialPlayer {
    public static final float YAW = 0.0f;
    public static final float PITCH = 0.0f;

    private PeInitialPlayer() {}

    /**
     * topSolidY supplies the highest solid non-leaf block or water in the PE column
     * (Y=2..127, -1 if absent). collidesAtY tests the player's 0.6 by 1.8 box with
     * its lower corner at the integer tile position used by PE's fixSpawnPosition.
     */
    public static int feetY(int sharedY, IntSupplier topSolidY, IntPredicate collidesAtY) {
        // Player::Player calls moveTo(sharedY + 1); moveTo adds the 1.62 eye offset.
        // recheckSpawnPosition starts at the floor of that eye coordinate.
        int initial = sharedY + 2;
        if (initial > 127) {
            // getTopSolidBlock(true) itself returns the first Y above the material.
            initial = topSolidY.getAsInt() + 2;
        }
        for (int y = initial; y <= 128; y++) {
            if (!collidesAtY.test(y)) return y;
        }
        return initial;
    }
}
