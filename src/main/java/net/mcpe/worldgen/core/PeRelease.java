package net.mcpe.worldgen.core;

import java.util.Arrays;
import java.util.Optional;

/** Pocket Edition release selected when the world is created. IDs persist in worldgen settings. */
public enum PeRelease {
    V0_1_3("0.1.3"), V0_2_2("0.2.2"), V0_3_3("0.3.3"), V0_4_0("0.4.0"), V0_5_0("0.5.0"), V0_6_1("0.6.1"), V0_7_6("0.7.6"), V0_8_1("0.8.1"),
    V0_9_5("0.9.5"), V0_10_2("0.10.2"), V0_11_1("0.11.1"), V0_12_3("0.12.3"), V0_13_2("0.13.2"),
    V0_14_3("0.14.3"), V0_15_10("0.15.10"), V0_16_2("0.16.2"), V0_16_5("0.16.5");

    private final String id;
    PeRelease(String id) { this.id = id; }
    public boolean hasWorldTypes() { return Integer.parseInt(id.split("\\.")[1]) >= 9; }
    // ARM builds from 0.15 draw Z first in BlockPos constructors with randomized arguments.
    boolean drawsZFirst() { return compareTo(V0_15_10) >= 0; }
    /** IDs 39/40 used the ordinary Bush class until Mushroom was introduced in 0.5.0. */
    public boolean hasBushMushrooms() { return compareTo(V0_4_0) <= 0; }
    public String id() { return id; }
    public boolean supports(boolean finite) { return finite || hasWorldTypes() && this != V0_16_5; }
    public String presetPath(boolean finite) {
        if (!supports(finite)) throw new IllegalArgumentException("Unsupported PE world type for release: " + id);
        return "pe_" + id.replace(".", "") + (finite ? "_old" : "_infinite");
    }
    public static Optional<PeRelease> byId(String id) {
        return Arrays.stream(values()).filter(release -> release.id.equals(id)).findFirst();
    }
}
