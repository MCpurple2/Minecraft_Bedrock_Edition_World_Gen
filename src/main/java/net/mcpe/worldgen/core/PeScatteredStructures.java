package net.mcpe.worldgen.core;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** RandomScatteredLargeFeature and local drawing commands captured from the release ARM routines. */
final class PeScatteredStructures {
    private record Command(String kind, int[] values) {}
    private record Loot(int id, int weight, int min, int max) {}
    // WeighedTreasureItem vectors passed to the native createChest/createDispenser.
    private static final List<Loot> EARLY_LOOT = List.of(new Loot(264,3,1,3), new Loot(265,10,1,5),
        new Loot(266,15,2,7), new Loot(388,2,1,3), new Loot(352,20,4,6), new Loot(367,16,3,7));
    private static List<Loot> laterLoot(int bookWeight) {
        var result = new java.util.ArrayList<>(EARLY_LOOT);
        result.addAll(List.of(new Loot(329,3,1,1), new Loot(417,1,1,1), new Loot(418,1,1,1),
            new Loot(419,1,1,1), new Loot(403,bookWeight,1,1)));
        return List.copyOf(result);
    }
    private static final List<Loot> DESERT_LOOT = laterLoot(20), JUNGLE_LOOT = laterLoot(1);
    private static final List<Loot> ARROWS = List.of(new Loot(262,30,2,7));
    private static final Map<String, List<Command>> TEMPLATES = new ConcurrentHashMap<>();
    private final int worldSeed, xScale, zScale;
    private final PeRelease release;
    private final java.util.Set<PeContent> flags;

    PeScatteredStructures(long seed, PeRelease release) {
        this(seed,release,PeContent.defaults(release,false));
    }
    PeScatteredStructures(long seed, PeRelease release, java.util.Set<PeContent> flags) {
        this.flags=flags;
        worldSeed = (int)seed;
        PeRandom random = new PeRandom(seed);
        xScale = (random.nextRawInt() >>> 1) | 1;
        zScale = (random.nextRawInt() >>> 1) | 1;
        this.release = release;
    }

    boolean candidate(int cx, int cz) {
        int dx = Math.floorMod(cx, 32), dz = Math.floorMod(cz, 32);
        if (dx >= 24 || dz >= 24) return false;
        PeRandom random = new PeRandom(worldSeed ^ (cx * xScale + cz * zScale));
        random.nextRawInt(); // StructureFeature::addFeature
        return dx == random.nextInt(24) && dz == random.nextInt(24);
    }

    void populate(PePopulationWorld world, PeRandom random, int cx, int cz) {
        if (!flags.contains(PeContent.DESERT_PYRAMIDS) && !flags.contains(PeContent.HUTS) && !flags.contains(PeContent.JUNGLE_PYRAMIDS)) return;
        // All native pieces face direction zero. The widest is the 21-block pyramid.
        for (int x = cx - 1; x <= cx; x++) for (int z = cz - 1; z <= cz; z++) {
            if (!candidate(x, z)) continue;
            int bx = x << 4, bz = z << 4;
            int biome = world.biomeAt(bx + 8, bz + 8);
            boolean later = release.compareTo(PeRelease.V0_15_10) >= 0;
            String template;
            int floor;
            List<Loot> loot = EARLY_LOOT;
            if ((biome == 2 || biome == 17) && flags.contains(PeContent.DESERT_PYRAMIDS)) {
                template = later ? "desert_015" : "desert_013";
                floor = 64;
                if (later) loot = DESERT_LOOT;
            } else if ((biome == 6 || biome == 134) && flags.contains(PeContent.HUTS)) {
                if (cx != x || cz != z) continue;
                template = later ? "hut_015" : "hut_014";
                floor = ground(world, bx, bz, 7, 9) + 1;
            } else if ((biome == 21 || biome == 22) && flags.contains(PeContent.JUNGLE_PYRAMIDS)) {
                if (cx != x || cz != z) continue;
                template = "jungle_015";
                floor = ground(world, bx, bz, 12, 15);
                loot = JUNGLE_LOOT;
            } else continue;
            for (Command command : TEMPLATES.computeIfAbsent(template, PeScatteredStructures::load)) {
                draw(world, random, command, bx, floor, bz, cx, cz, loot);
            }
            if (biome == 6 || biome == 134) world.spawnHutWitch(bx + 2, floor + 2, bz + 5);
        }
    }

    private static int ground(PePopulationWorld world, int bx, int bz, int width, int length) {
        int total = 0;
        for (int x = 0; x < width; x++) for (int z = 0; z < length; z++)
            total += Math.max(64, world.topSolidY(bx + x, bz + z, true));
        return total / (width * length);
    }

    private static List<Command> load(String key) {
        String path = "/data/pe_worldgen/legacy/scattered/" + key + ".json";
        var input = PeScatteredStructures.class.getResourceAsStream(path);
        if (input == null) throw new IllegalStateException("Missing scattered structure " + key);
        try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonArray().asList().stream().map(element -> {
                var row = element.getAsJsonArray();
                int[] values = new int[row.size() - 1];
                for (int i = 0; i < values.length; i++) values[i] = row.get(i + 1).getAsInt();
                return new Command(row.get(0).getAsString(), values);
            }).toList();
        } catch (IOException e) { throw new IllegalStateException(key, e); }
    }

    private static void draw(PePopulationWorld w, PeRandom random, Command op, int bx, int by, int bz, int cx, int cz, List<Loot> loot) {
        int[] v = op.values;
        int rolls = op.kind.equals("chest") ? 2 + random.nextInt(5) : 0;
        boolean volume = v.length >= 7;
        int endX = volume ? v[3] : v[0], endY = volume ? v[4] : v[1], endZ = volume ? v[5] : v[2];
        for (int y = v[1]; y <= endY; y++) for (int x = v[0]; x <= endX; x++) for (int z = v[2]; z <= endZ; z++) {
            int wx = bx + x, wy = by + y, wz = bz + z;
            if (wx >> 4 != cx || wz >> 4 != cz || !w.isInside(wx, wy, wz)) continue;
            switch (op.kind) {
                case "box" -> {
                    if (v[10] != 0 && w.getBlock(wx, wy, wz) == 0) continue;
                    int offset = x == v[0] || x == endX || y == v[1] || y == endY || z == v[2] || z == endZ ? 6 : 8;
                    w.setBlock(wx, wy, wz, v[offset], v[offset + 1]);
                }
                case "mossbox" -> {
                    if (v[6] == 0 || w.getBlock(wx, wy, wz) != 0)
                        w.setBlock(wx, wy, wz, random.nextFloat() < 0.4f ? 4 : 48, 0);
                }
                case "down", "foundation" -> {
                    int i = volume ? 6 : 3;
                    while (wy > 1) {
                        int block = w.getBlock(wx, wy, wz);
                        if (block != 0 && (block < 8 || block > 11)) break;
                        w.setBlock(wx, wy--, wz, v[i], v[i + 1]);
                    }
                }
                case "set" -> {
                    w.setBlock(wx, wy, wz, v[3], v[4]);
                    if (v[3] == 23) fillContainer(w, random, wx, wy, wz, 9, 2, ARROWS);
                }
                case "chest" -> {
                    w.setBlock(wx, wy, wz, 54, new int[]{3,4,2,5}[v[4] & 3]);
                    fillContainer(w, random, wx, wy, wz, 27, rolls, loot);
                }
                default -> throw new IllegalStateException("Unknown PE piece command " + op.kind);
            }
        }
    }
    private static void fillContainer(PePopulationWorld world, PeRandom random, int x, int y, int z,
                                      int slots, int rolls, List<Loot> loot) {
        int weight = 0;
        for (Loot item : loot) weight += item.weight;
        for (int i = 0; i < rolls; i++) {
            int ticket = random.nextInt(weight);
            for (Loot item : loot) {
                ticket -= item.weight;
                if (ticket >= 0) continue;
                int count = item.min == item.max ? item.min : item.min + random.nextInt(item.max - item.min + 1);
                world.setChestSlot(x, y, z, random.nextInt(slots), item.id, count);
                break;
            }
        }
    }
}
