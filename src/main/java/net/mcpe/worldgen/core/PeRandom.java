package net.mcpe.worldgen.core;

/**
 * MT19937 primitive recovered from PE 0.11.1 ARMv7 Random::genrand_int32
 * (0x00183130). Standard MT state update, with explicit unsigned conversion.
 * No Minecraft or java.util.Random dependency.
 */
public final class PeRandom {
    private final int[] state = new int[624];
    private int index;

    public PeRandom(long seed) {
        setSeed(seed);
    }

    public void setSeed(long seed) {
        state[0] = (int) seed;
        for (int i = 1; i < 624; i++) {
            state[i] = 1812433253 * (state[i - 1] ^ (state[i - 1] >>> 30)) + i;
        }
        index = 624;
    }

    public int nextRawInt() {
        if (index >= 624) {
            for (int i = 0; i < 624; i++) {
                int y = (state[i] & 0x80000000) | (state[(i + 1) % 624] & 0x7fffffff);
                state[i] = state[(i + 397) % 624] ^ (y >>> 1) ^ ((y & 1) != 0 ? 0x9908b0df : 0);
            }
            index = 0;
        }
        int value = state[index++];
        value ^= value >>> 11;
        value ^= (value << 7) & 0x9d2c5680;
        value ^= (value << 15) & 0xefc60000;
        return value ^ (value >>> 18);
    }

    public int nextInt() {
        return nextRawInt() >>> 1;
    }

    /** C++ Random-by-value semantics used by native stronghold piece selection. */
    public PeRandom copy() {
        PeRandom copy=new PeRandom(0);
        System.arraycopy(state,0,copy.state,0,state.length);
        copy.index=index;
        return copy;
    }

    public int nextInt(int bound) {
        if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
        return (int) (Integer.toUnsignedLong(nextRawInt()) % bound);
    }

    /** Preserve ARM's uint32 -> float rounding before its double scaling. */
    public float nextFloat() {
        float unsignedAsFloat = (float) Integer.toUnsignedLong(nextRawInt());
        return (float) ((double) unsignedAsFloat * 0x1.0p-32);
    }
}
