package net.mcpe.worldgen.core;

import java.util.Arrays;
import java.util.Optional;

/** Pocket Edition release selected when the world is created. IDs persist in worldgen settings. */
public enum PeRelease {
    V0_11_1("0.11.1"), V0_12_3("0.12.3"), V0_13_2("0.13.2"),
    V0_14_3("0.14.3"), V0_15_10("0.15.10"), V0_16_2("0.16.2");

    private final String id;
    PeRelease(String id) { this.id = id; }
    public String id() { return id; }
    public boolean supports(boolean finite) { return !finite || this == V0_11_1; }
    public String presetPath(boolean finite) {
        if (!supports(finite)) throw new IllegalArgumentException("Unsupported finite PE release: " + id);
        return "pe_" + id.replace(".", "") + (finite ? "_old" : "_infinite");
    }
    public static Optional<PeRelease> byId(String id) {
        return Arrays.stream(values()).filter(release -> release.id.equals(id)).findFirst();
    }
}
