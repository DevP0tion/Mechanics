package devp0tion.mechanics.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The parallel comparison of the two engines (N22-6, N26-1): the same scenario runs on the engine
 * before the ECS restructure and on the new engine in its compatibility mode, and after every tick
 * the cells (fluid, amount, links, blocked faces), the tanks, the pumps and every pump result must
 * be exactly equal.
 *
 * <p>The old engine is driven as the game drove it (every pump ticked in connection order); the new
 * one through its systems ({@link PipeGrid#runTick}). The scenarios have no two paths of equal
 * length to one valve: the old engine broke such ties by its search from each pump, the new one by
 * its search from each valve (both are "shortest path"; the tie is not a design rule). Unloading
 * happens only where the new engine's summary is current (a normal cycle after the region was last
 * filled, no structure change while unloaded): the intended differences of the summary (N23-2) are
 * tested in {@link NewEngineTest}.
 */
final class ComparisonTest {

    private ComparisonTest() {
    }

    /** One copy of a scenario on one engine, with the handles the comparison reads by index. */
    static final class World {
        final EngineApi grid;
        final PipeGrid newGrid;
        final List<TankValve> valves = new ArrayList<>();
        final List<int[]> valveTiles = new ArrayList<>();
        final List<Boolean> valvePlaced = new ArrayList<>();
        final List<Pump> pumps = new ArrayList<>();
        /** Unloaded pipes of the new engine (their holders' state), to load them again. */
        final Map<String, PipeNode> held = new HashMap<>();

        World(EngineApi grid) {
            this.grid = grid;
            this.newGrid = Engines.newGrid(grid);
        }

        TankValve valve(int capacity, int x, int y) {
            TankValve valve = Fluids.valve(capacity);
            grid.placeValve(x, y, valve);
            valves.add(valve);
            valveTiles.add(new int[]{x, y});
            valvePlaced.add(true);
            return valve;
        }

        Pump pump(int x, int y, PumpTier tier, FluidType fluid) {
            Pump pump = Fluids.fueledPump(grid, x, y, tier, fluid);
            pumps.add(pump);
            return pump;
        }

        void unload(int x, int y, PipeLayer layer) {
            PipeNode node = grid.unloadPipe(x, y, layer);
            if (newGrid != null && node != null) {
                held.put(x + "," + y + "," + layer, node);
            }
        }

        void load(int x, int y, PipeLayer layer) {
            if (newGrid != null) {
                PipeNode node = held.remove(x + "," + y + "," + layer);
                if (node != null) {
                    newGrid.loadPipe(x, y, layer, node.getTier(), node.getLinks(), node.getFluid(), node.getAmount(), true,
                            node.getHintDestinations(), node.getHintCodes());
                }
            } else {
                PipeNode mirror = grid.getPipe(x, y, layer);
                if (mirror != null) {
                    grid.loadPipe(x, y, layer, mirror.getTier(), mirror.getLinks(), mirror.getFluid(), mirror.getAmount(), true);
                }
            }
        }
    }

    /** A scenario: what is built, and what happens before each tick. */
    interface Script {
        void build(World world);

        default void beforeTick(World world, int tick) {
        }
    }

    /** Runs {@code script} on both engines for {@code ticks} ticks and compares every tick. */
    static void compare(String name, PipeTierRules rules, Script script, int ticks) {
        compare(name, rules, () -> script, ticks, true);
    }

    /** As above, with one script instance per engine (scripts that keep state). */
    static void compare(String name, PipeTierRules rules, java.util.function.Supplier<Script> scripts, int ticks,
                        boolean mustMoveFluid) {
        World legacy = new World(Engines.create(Engines.Kind.LEGACY, rules));
        World compat = new World(Engines.create(Engines.Kind.NEW_COMPAT, rules));
        Script a0 = scripts.get();
        Script b0 = scripts.get();
        a0.build(legacy);
        b0.build(compat);
        Check.equal(state(legacy), state(compat), name + ": built");
        int pumped = 0;
        int other = 0;
        for (int tick = 0; tick < ticks; tick++) {
            a0.beforeTick(legacy, tick);
            b0.beforeTick(compat, tick);
            List<String> a = tickLegacy(legacy);
            List<String> b = tickNew(compat);
            Check.equal(a, b, name + " tick " + tick + " results");
            Check.equal(state(legacy), state(compat), name + " tick " + tick + " state");
            for (String result : a) {
                if (result.contains(" PUMPED ")) {
                    pumped++;
                } else {
                    other++;
                }
            }
        }
        long stored = 0;
        for (TankValve valve : compat.valves) {
            stored += valve.getTank() == null ? 0 : valve.getTank().getAmount();
        }
        Check.isTrue(!mustMoveFluid || pumped > 0 && stored > 0, name + ": the scenario moved fluid");
        System.out.println("  compared " + name + ": " + ticks + " ticks, " + pumped + " pumped cycles, " + other
                + " other results, " + stored + " units in tanks, " + compat.held.size() + " pipes unloaded at the end");
    }

    private static List<String> tickLegacy(World world) {
        world.grid.tick();
        List<String> results = new ArrayList<>();
        for (Pump pump : world.pumps) {
            PumpResult result = pump.tick();
            if (result.getStatus() != PumpResult.Status.WAITING && result.getStatus() != PumpResult.Status.DISABLED) {
                results.add(describe(world, pump, result));
            }
        }
        return results;
    }

    private static List<String> tickNew(World world) {
        Map<Long, PumpResult> ran = world.newGrid.runTick();
        List<String> results = new ArrayList<>();
        for (Map.Entry<Long, PumpResult> entry : ran.entrySet()) {
            Pump pump = world.newGrid.getPump(PipeGrid.keyX(entry.getKey()), PipeGrid.keyY(entry.getKey()));
            results.add(describe(world, pump, entry.getValue()));
        }
        return results;
    }

    static String describe(World world, Pump pump, PumpResult result) {
        StringBuilder text = new StringBuilder("pump" + world.pumps.indexOf(pump) + " " + result.getStatus());
        if (result.isPumped()) {
            text.append(' ').append(result.getFluid()).append(" moved ").append(result.getMoved()).append(" fill ")
                    .append(result.getPipeFill()).append(" updated ").append(result.getPipesUpdated()).append(" lost ")
                    .append(result.getLost()).append(" delivered [");
            for (Map.Entry<TankValve, Integer> entry : result.getDelivered().entrySet()) {
                text.append(" v").append(indexOf(world.valves, entry.getKey())).append('=').append(entry.getValue());
            }
            text.append(" ] broken [");
            for (PipeNode node : result.getBroken()) {
                text.append(' ').append(node.getLayer()).append(node.getTileX()).append(',').append(node.getTileY());
            }
            text.append(" ]");
        }
        return text.toString();
    }

    private static int indexOf(List<TankValve> valves, TankValve valve) {
        for (int i = 0; i < valves.size(); i++) {
            if (valves.get(i) == valve) {
                return i;
            }
        }
        return -1;
    }

    /** Everything the comparison checks after a tick, as text. */
    static String state(World world) {
        List<String> cells = new ArrayList<>();
        for (PipeNode node : world.grid.getPipes()) {
            if (!node.isLoaded()) {
                continue;
            }
            cells.add(node.getLayer() + " " + node.getTileX() + "," + node.getTileY() + " " + node.getTier() + " l"
                    + node.getLinks() + " " + node.getFluid() + " " + node.getAmount() + " b"
                    + world.grid.getFluidBlockedSides(node.getTileX(), node.getTileY(), node.getLayer()));
        }
        Collections.sort(cells);
        StringBuilder text = new StringBuilder(String.join("\n", cells));
        for (int i = 0; i < world.valves.size(); i++) {
            TankValve valve = world.valves.get(i);
            text.append("\nvalve").append(i).append(" l").append(valve.getLinks()).append(' ').append(valve.getTank());
        }
        for (int i = 0; i < world.pumps.size(); i++) {
            Pump pump = world.pumps.get(i);
            text.append("\npump").append(i).append(' ').append(pump.getFluid()).append(' ').append(pump.getAmount())
                    .append(" burn ").append(pump.getBurnTicksLeft()).append(" pushed ").append(pump.getLastPushedFluid())
                    .append(" links ").append(pump.getLinks()).append(" sources ").append(pump.getSourceSlots());
        }
        return text.toString();
    }

    // ---------------------------------------------------------------- fixed scenarios

    public static void testCombFillsTheSame() {
        // A comb: trunk y=0 x=1..40, branches down at x=8,16,24,32 (10 long), valves at the branch ends
        // and the trunk end, four fire pumps along the top, empty at the start (N7-1, N7-2, N14-2, N18-1).
        compare("comb", Fluids.uniform(80), new Script() {
            @Override
            public void build(World w) {
                Fluids.baseLine(w.grid, 1, 40, 0, MineralTier.IRON);
                for (int b = 1; b <= 4; b++) {
                    for (int y = 1; y <= 10; y++) {
                        w.grid.placePipe(b * 8, y, PipeLayer.BASE, MineralTier.IRON);
                    }
                    w.valve(2000 + b * 300, b * 8, 11);
                }
                w.valve(5000, 41, 0);
                for (int i = 0; i < 4; i++) {
                    w.pump(3 + i * 9, -1, i == 2 ? PumpTier.ADVANCED_FIRE : PumpTier.FIRE, FluidType.FRESHWATER);
                }
            }
        }, 3000);
    }

    public static void testMixedTiersCapsAndSaturationTheSame() {
        // Transport amounts 10..60 by tier (Fluids.TIERS): caps along the paths (N14-2), small tanks
        // that fill up and pass their shares on (N7-2), a second fluid next to the first (N13-2).
        compare("tiers", Fluids.TIERS, new Script() {
            @Override
            public void build(World w) {
                MineralTier[] tiers = {MineralTier.GOLD, MineralTier.IRON, MineralTier.COPPER, MineralTier.DEMONIC};
                for (int x = 1; x <= 20; x++) {
                    w.grid.placePipe(x, 0, PipeLayer.BASE, tiers[x % tiers.length]);
                }
                for (int y = 1; y <= 6; y++) {
                    w.grid.placePipe(5, y, PipeLayer.BASE, MineralTier.IVY);
                    w.grid.placePipe(13, -y, PipeLayer.BASE, MineralTier.COPPER);
                }
                w.valve(150, 5, 7);
                w.valve(90, 13, -7);
                w.valve(4000, 21, 0);
                w.pump(0, 0, PumpTier.ADVANCED_FIRE, FluidType.FRESHWATER);
                w.pump(10, 1, PumpTier.FIRE, FluidType.FRESHWATER);
                // Lava next door: its own line, touching the water line at (17, 1)-(17, 0).
                for (int y = 1; y <= 4; y++) {
                    w.grid.placePipe(17, y, PipeLayer.BASE, MineralTier.TUNGSTEN);
                }
                w.valve(500, 18, 4);
                w.pump(16, 4, PumpTier.ADVANCED_FIRE, FluidType.LAVA);
            }
        }, 2500);
    }

    public static void testBreakingTheSame() {
        // Copper cannot carry lava here (N12-4): newly reached copper pipes break, only their share is
        // lost (N14-1), and the pump goes on through the others.
        compare("breaking", Fluids.breaking(20, FluidType.LAVA, MineralTier.IRON), new Script() {
            @Override
            public void build(World w) {
                Fluids.baseLine(w.grid, 1, 6, 0, MineralTier.IRON);
                for (int y = 1; y <= 3; y++) {
                    w.grid.placePipe(3, y, PipeLayer.BASE, y == 2 ? MineralTier.COPPER : MineralTier.IRON);
                    w.grid.placePipe(5, -y, PipeLayer.BASE, MineralTier.GOLD);
                }
                w.valve(1000, 3, 4);
                w.valve(1000, 5, -4);
                w.valve(1000, 7, 0);
                w.pump(0, 0, PumpTier.ADVANCED_FIRE, FluidType.LAVA);
            }

            @Override
            public void beforeTick(World w, int tick) {
                if (tick == 400) {
                    // A new copper pipe on a full path: it breaks when the lava reaches it.
                    w.grid.removePipe(5, -2, PipeLayer.BASE);
                    w.grid.placePipe(5, -2, PipeLayer.BASE, MineralTier.COPPER);
                }
            }
        }, 900);
    }

    public static void testUndergroundAndWrenchTheSame() {
        // Basic -> underground under a wall -> basic, linked by the wrench in the middle of the tiles
        // (N16-4), a valve over an underground pipe (9-9, 13-5), and cuts and links during the run.
        compare("underground", Fluids.uniform(30), new Script() {
            @Override
            public void build(World w) {
                Fluids.baseLine(w.grid, 1, 3, 0, MineralTier.IRON);
                Fluids.line(w.grid, 3, 12, 0, PipeLayer.UNDERGROUND, MineralTier.IRON);
                w.grid.toggleVertical(3, 0);
                Fluids.baseLine(w.grid, 12, 14, 0, MineralTier.IRON);
                w.grid.toggleVertical(12, 0);
                w.valve(3000, 15, 0);
                Fluids.line(w.grid, 7, 7, 1, PipeLayer.UNDERGROUND, MineralTier.IRON);
                Fluids.line(w.grid, 7, 7, 2, PipeLayer.UNDERGROUND, MineralTier.IRON);
                w.valve(800, 7, 2);
                w.pump(0, 0, PumpTier.ADVANCED_FIRE, FluidType.FRESHWATER);
            }

            @Override
            public void beforeTick(World w, int tick) {
                if (tick == 300) {
                    w.grid.toggleVertical(7, 2);
                }
                if (tick == 500) {
                    w.grid.toggleSide(7, 0, PipeGrid.Part.UNDERGROUND_PIPE, Direction.EAST);
                }
                if (tick == 700) {
                    w.grid.toggleSide(7, 0, PipeGrid.Part.UNDERGROUND_PIPE, Direction.EAST);
                    w.grid.toggleVertical(7, 2);
                }
                if (tick == 900) {
                    w.valves.get(0).setEnabled(false);
                }
                if (tick == 1100) {
                    w.valves.get(0).setEnabled(true);
                }
            }
        }, 1400);
    }

    public static void testUnloadedFullRegionTheSame() {
        // A line across three regions (x 0..47): the middle region unloads while its pipes are full
        // and the summary is current (N14-3, N15-1, N23-2), and loads again later.
        compare("unloaded", Fluids.uniform(20), new Script() {
            @Override
            public void build(World w) {
                Fluids.baseLine(w.grid, 1, 46, 0, MineralTier.IRON);
                for (int y = 1; y <= 4; y++) {
                    w.grid.placePipe(40, y, PipeLayer.BASE, MineralTier.IRON);
                }
                w.valve(100000, 47, 0);
                w.valve(300, 40, 5);
                w.pump(0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
            }

            @Override
            public void beforeTick(World w, int tick) {
                if (tick == 1200 || tick == 2000) {
                    for (int x = 16; x < 32; x++) {
                        w.unload(x, 0, PipeLayer.BASE);
                    }
                }
                if (tick == 1600 || tick == 2400) {
                    for (int x = 16; x < 32; x++) {
                        w.load(x, 0, PipeLayer.BASE);
                    }
                }
            }
        }, 2800);
    }

    // ---------------------------------------------------------------- random trees

    public static void testRandomTreesTheSame() {
        for (long seed = 1; seed <= 16; seed++) {
            final long s = seed;
            compare("random " + seed, Fluids.TIERS, () -> new RandomTree(s), 2400, false);
        }
    }

    /**
     * A random tree of pipes on a lattice (nodes at even tiles, edges on the odd tile between them),
     * so no two pipes are next to each other unless they are linked by a tree edge: every valve has
     * one path from each pump. Valves and pumps sit on unused edge tiles next to exactly one pipe.
     * Random wrench cuts and links, pipe and valve removals and placements, wire switching, and
     * region unloads (only of regions whose pipes have all been full for a whole cycle).
     */
    static final class RandomTree implements Script {
        private final long seed;
        private Random random;
        private final List<int[]> edgeTiles = new ArrayList<>();
        private final Map<Long, MineralTier> tiers = new HashMap<>();
        private final List<int[]> removed = new ArrayList<>();
        private final Map<Long, Integer> fullSince = new HashMap<>();
        private long unloadedRegion = Long.MIN_VALUE;
        private int unloadedAt;

        RandomTree(long seed) {
            this.seed = seed;
        }

        @Override
        public void build(World w) {
            random = new Random(seed);
            edgeTiles.clear();
            tiers.clear();
            removed.clear();
            fullSince.clear();
            unloadedRegion = Long.MIN_VALUE;
            Set<Long> nodes = new HashSet<>();
            List<int[]> frontier = new ArrayList<>();
            int count = 25 + random.nextInt(25);
            nodes.add(PipeGrid.key(0, 0));
            frontier.add(new int[]{0, 0});
            placePipe(w, 0, 0);
            while (nodes.size() < count && !frontier.isEmpty()) {
                int[] from = frontier.get(random.nextInt(frontier.size()));
                Direction d = Direction.values()[random.nextInt(4)];
                int nx = from[0] + 2 * d.dx;
                int ny = from[1] + 2 * d.dy;
                if (nodes.contains(PipeGrid.key(nx, ny)) || Math.abs(nx) > 24 || Math.abs(ny) > 24) {
                    continue;
                }
                nodes.add(PipeGrid.key(nx, ny));
                frontier.add(new int[]{nx, ny});
                placePipe(w, from[0] + d.dx, from[1] + d.dy);
                edgeTiles.add(new int[]{from[0] + d.dx, from[1] + d.dy});
                placePipe(w, nx, ny);
            }
            // Most pipes start full of water, as if saved so (so that regions fill up and unload).
            List<Long> keys = new ArrayList<>(tiers.keySet());
            Collections.sort(keys);
            for (long key : keys) {
                if (random.nextInt(10) < 7) {
                    Fluids.set(w.grid, PipeGrid.keyX(key), PipeGrid.keyY(key), PipeLayer.BASE, FluidType.FRESHWATER,
                            Fluids.TIERS.getTransportAmount(tiers.get(key)));
                }
            }
            // Valves and pumps on free edge tiles whose far node is not in the tree.
            List<int[]> spots = new ArrayList<>();
            for (int[] node : frontier) {
                for (Direction d : Direction.values()) {
                    int far = 0;
                    if (!nodes.contains(PipeGrid.key(node[0] + 2 * d.dx, node[1] + 2 * d.dy))) {
                        spots.add(new int[]{node[0] + d.dx, node[1] + d.dy, far});
                    }
                }
            }
            Collections.shuffle(spots, random);
            int valves = 3 + random.nextInt(5);
            int pumps = 1 + random.nextInt(4);
            Set<Long> used = new HashSet<>();
            int index = 0;
            for (int[] spot : spots) {
                if (!used.add(PipeGrid.key(spot[0], spot[1]))) {
                    continue;
                }
                if (index < valves) {
                    w.valve(random.nextInt(4) == 0 ? 60 + random.nextInt(200) : 5000 + random.nextInt(5000), spot[0], spot[1]);
                } else if (index < valves + pumps) {
                    w.pump(spot[0], spot[1], random.nextInt(3) == 0 ? PumpTier.ADVANCED_FIRE : PumpTier.FIRE,
                            FluidType.FRESHWATER);
                } else {
                    break;
                }
                index++;
            }
        }

        private void placePipe(World w, int x, int y) {
            MineralTier tier = MineralTier.values()[random.nextInt(4)];
            tiers.put(PipeGrid.key(x, y), tier);
            w.grid.placePipe(x, y, PipeLayer.BASE, tier);
        }

        @Override
        public void beforeTick(World w, int tick) {
            // The same random draws on both copies: the stream depends only on the tick and the seed.
            Random r = new Random(seed * 1_000_003L + tick);
            if (unloadedRegion != Long.MIN_VALUE) {
                if (tick - unloadedAt >= 200) {
                    forEachPipeIn(w, unloadedRegion, true, (x, y) -> w.load(x, y, PipeLayer.BASE));
                    unloadedRegion = Long.MIN_VALUE;
                }
                return;
            }
            trackFull(w, tick);
            if (tick % 37 != 5) {
                return;
            }
            int op = r.nextInt(7);
            if (op == 0 && !edgeTiles.isEmpty()) {
                int[] edge = edgeTiles.get(r.nextInt(edgeTiles.size()));
                if (w.grid.getPipe(edge[0], edge[1], PipeLayer.BASE) != null) {
                    Direction d = Direction.values()[r.nextInt(4)];
                    w.grid.toggleSide(edge[0], edge[1], PipeGrid.Part.BASIC_PIPE, d);
                }
            } else if (op == 1 && !edgeTiles.isEmpty()) {
                int[] edge = edgeTiles.get(r.nextInt(edgeTiles.size()));
                if (w.grid.removePipe(edge[0], edge[1], PipeLayer.BASE) != null) {
                    removed.add(edge);
                }
            } else if (op == 2 && !removed.isEmpty()) {
                int[] edge = removed.remove(r.nextInt(removed.size()));
                w.grid.placePipe(edge[0], edge[1], PipeLayer.BASE, tiers.get(PipeGrid.key(edge[0], edge[1])));
            } else if (op == 3 && !w.valves.isEmpty()) {
                int i = r.nextInt(w.valves.size());
                int[] tile = w.valveTiles.get(i);
                if (w.valvePlaced.get(i)) {
                    w.grid.removeValve(tile[0], tile[1]);
                    w.valvePlaced.set(i, false);
                } else {
                    w.grid.placeValve(tile[0], tile[1], w.valves.get(i));
                    w.valvePlaced.set(i, true);
                }
            } else if (op == 4 && !w.valves.isEmpty()) {
                TankValve valve = w.valves.get(r.nextInt(w.valves.size()));
                valve.setEnabled(!valve.isEnabled());
            } else if (op == 5) {
                // Unload a region whose pipes have all been full for a whole cycle (the summary is current).
                List<Long> candidates = new ArrayList<>();
                for (Map.Entry<Long, Integer> entry : fullSince.entrySet()) {
                    if (tick - entry.getValue() > PipeGrid.CYCLE_TICKS + 1 && !hasPumpOrValve(w, entry.getKey())) {
                        candidates.add(entry.getKey());
                    }
                }
                Collections.sort(candidates);
                if (!candidates.isEmpty()) {
                    unloadedRegion = candidates.get(r.nextInt(candidates.size()));
                    unloadedAt = tick;
                    if (w.newGrid != null) {
                        System.out.println("    seed " + seed + " tick " + tick + ": region " + PipeGrid.keyX(unloadedRegion)
                                + "," + PipeGrid.keyY(unloadedRegion) + " unloaded");
                    }
                    forEachPipeIn(w, unloadedRegion, false, (x, y) -> w.unload(x, y, PipeLayer.BASE));
                }
            }
        }

        /** Regions whose loaded pipes are all full, since when. */
        private void trackFull(World w, int tick) {
            Map<Long, Boolean> full = new HashMap<>();
            for (PipeNode node : w.grid.getPipes()) {
                if (!node.isLoaded()) {
                    continue;
                }
                long region = TileBuckets.bucketOf(node.getTileX(), node.getTileY());
                boolean before = full.containsKey(region) ? full.get(region) : true;
                full.put(region, before && node.isFull());
            }
            for (Map.Entry<Long, Boolean> entry : full.entrySet()) {
                if (!entry.getValue()) {
                    fullSince.remove(entry.getKey());
                } else if (!fullSince.containsKey(entry.getKey())) {
                    fullSince.put(entry.getKey(), tick);
                }
            }
            fullSince.keySet().retainAll(full.keySet());
        }

        private boolean hasPumpOrValve(World w, long region) {
            for (int[] tile : w.valveTiles) {
                if (TileBuckets.bucketOf(tile[0], tile[1]) == region) {
                    return true;
                }
            }
            for (Pump pump : w.pumps) {
                if (TileBuckets.bucketOf(pump.getTileX(), pump.getTileY()) == region) {
                    return true;
                }
            }
            return false;
        }

        private interface TileAction {
            void apply(int x, int y);
        }

        private void forEachPipeIn(World w, long region, boolean unloaded, TileAction action) {
            List<int[]> tiles = new ArrayList<>();
            for (Map.Entry<Long, MineralTier> entry : tiers.entrySet()) {
                int x = PipeGrid.keyX(entry.getKey());
                int y = PipeGrid.keyY(entry.getKey());
                if (TileBuckets.bucketOf(x, y) == region) {
                    tiles.add(new int[]{x, y});
                }
            }
            tiles.sort((a, b) -> a[1] != b[1] ? Integer.compare(a[1], b[1]) : Integer.compare(a[0], b[0]));
            for (int[] tile : tiles) {
                action.apply(tile[0], tile[1]);
            }
        }
    }

}
