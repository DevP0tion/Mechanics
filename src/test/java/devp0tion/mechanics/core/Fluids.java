package devp0tion.mechanics.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Test helpers for the fluid and pipe tests. */
final class Fluids {

    /**
     * Test pipe transport amounts: COPPER 10, IRON 20, GOLD 30, ... (tier order x 10). Every tier
     * carries every fluid.
     */
    static final PipeTierRules TIERS = carryingAll(tier -> (tier.ordinal() + 1) * 10);

    private Fluids() {
    }

    /**
     * Every tier carries the same amount per cell (and per cycle), and every fluid: the tests of
     * other rules do not depend on the game's transportable kinds (N32-5), which
     * {@link PipeTierRules#TABLE} and {@link #breaking} test.
     */
    static PipeTierRules uniform(final int amount) {
        return carryingAll(tier -> amount);
    }

    /** The given transport amounts; every tier carries every fluid. */
    static PipeTierRules carryingAll(final java.util.function.ToIntFunction<MineralTier> amounts) {
        return new PipeTierRules() {
            @Override
            public int getTransportAmount(MineralTier tier) {
                return amounts.applyAsInt(tier);
            }

            @Override
            public boolean canCarry(MineralTier lowestTier, FluidType type) {
                return type != null;
            }
        };
    }

    /** Uniform amounts; tiers below {@code minTier} cannot carry {@code fluid} (N12-4). */
    static PipeTierRules breaking(final int amount, final FluidType fluid, final MineralTier minTier) {
        return new PipeTierRules() {
            @Override
            public int getTransportAmount(MineralTier tier) {
                return amount;
            }

            @Override
            public boolean canCarry(MineralTier lowestTier, FluidType type) {
                return type != fluid || lowestTier.isAtLeast(minTier);
            }
        };
    }

    /** An active tank storage with the given capacity. */
    static TankStorage tank(int capacity) {
        TankStorage tank = new TankStorage();
        tank.applyStructure(validTank(capacity));
        return tank;
    }

    static TankValidation validTank(int capacity) {
        return TankValidation.valid(new TankBounds(0, 0, 3, 3), TankValidation.InteriorCondition.ALL_EMPTY,
                MineralTier.COPPER, capacity, new GridPos(0, 0), Collections.singletonList(new GridPos(1, 0)));
    }

    /** A valve of a new tank with the given capacity. */
    static TankValve valve(int tankCapacity) {
        return valveOf(tank(tankCapacity));
    }

    static TankValve valveOf(TankStorage tank) {
        TankValve valve = new TankValve();
        valve.setTank(tank);
        return valve;
    }

    /** Places basic pipes from x0 to x1 (inclusive) on row y. */
    static void baseLine(PipeGrid grid, int x0, int x1, int y, MineralTier tier) {
        line(grid, x0, x1, y, PipeLayer.BASE, tier);
    }

    static void line(PipeGrid grid, int x0, int x1, int y, PipeLayer layer, MineralTier tier) {
        for (int x = x0; x <= x1; x++) {
            grid.placePipe(x, y, layer, tier);
        }
    }

    /**
     * Fills pipes of a row segment to capacity with {@code fluid} as saved state (the grid builds
     * their networks as for loaded pipes).
     */
    static void fill(PipeGrid grid, int x0, int x1, int y, PipeLayer layer, FluidType fluid) {
        for (int x = x0; x <= x1; x++) {
            PipeNode node = grid.getPipe(x, y, layer);
            grid.loadPipe(x, y, layer, node.getTier(), node.getLinks(), fluid, node.getCapacity(), node.isLoaded());
        }
    }

    /** Puts {@code amount} of {@code fluid} into one pipe as saved state. */
    static PipeNode set(PipeGrid grid, int x, int y, PipeLayer layer, FluidType fluid, int amount) {
        PipeNode node = grid.getPipe(x, y, layer);
        return grid.loadPipe(x, y, layer, node.getTier(), node.getLinks(), fluid, amount, node.isLoaded());
    }

    /**
     * A ground pump placed at the tile facing east (its output, N36-1: most layouts have the pipe at
     * x + 1), standing on an infinite liquid tile of {@code fluid}.
     */
    static Pump pump(PipeGrid grid, int x, int y, PumpTier tier, FluidType fluid) {
        return pump(grid, x, y, tier, fluid, Direction.EAST);
    }

    /** A ground pump placed at the tile facing {@code facing}, on an infinite liquid tile of {@code fluid}. */
    static Pump pump(PipeGrid grid, int x, int y, PumpTier tier, FluidType fluid, Direction facing) {
        Pump pump = new Pump(tier);
        pump.setDirection(facing);
        pump.setForm(PumpForm.GROUND);
        pump.setTileSource(LiquidTileSource.infinite(fluid));
        grid.placePump(x, y, pump);
        return pump;
    }

    /** A valve pump (N36-3) placed at the tile facing {@code facing}: it pulls from the valve behind it. */
    static Pump valvePump(PipeGrid grid, int x, int y, PumpTier tier, Direction facing) {
        Pump pump = new Pump(tier);
        pump.setDirection(facing);
        pump.setForm(PumpForm.VALVE);
        grid.placePump(x, y, pump);
        return pump;
    }

    /**
     * Runs the engine's systems ({@link PipeGrid#runTick}) until the pump ran a cycle (the global push
     * tick, N28-16); returns its result.
     */
    static PumpResult cycle(Pump pump) {
        PipeGrid grid = (PipeGrid) pump.host;
        long key = PipeGrid.key(pump.getTileX(), pump.getTileY());
        for (int i = 0; i < 1000; i++) {
            PumpResult result = grid.runTick().get(key);
            if (result != null) {
                return result;
            }
        }
        throw new AssertionError("no cycle within 1000 ticks");
    }

    /**
     * Runs the engine's systems up to the next tick in which one of the given pumps ran a cycle (the
     * global push tick, N28-16); returns their results ({@code WAITING} for one that did not run).
     */
    static PumpResult[] push(PipeGrid grid, Pump... pumps) {
        for (int i = 0; i < 1000; i++) {
            PumpResult[] results = tickAll(grid, pumps);
            for (PumpResult result : results) {
                if (result.getStatus() != PumpResult.Status.WAITING) {
                    return results;
                }
            }
        }
        throw new AssertionError("no push within 1000 ticks");
    }

    /** One tick of the engine's systems; returns the given pumps' results ({@code WAITING} when one did not run). */
    static PumpResult[] tickAll(PipeGrid grid, Pump... pumps) {
        Map<Long, PumpResult> ran = grid.runTick();
        PumpResult[] results = new PumpResult[pumps.length];
        for (int i = 0; i < pumps.length; i++) {
            PumpResult result = ran.get(PipeGrid.key(pumps[i].getTileX(), pumps[i].getTileY()));
            results[i] = result != null ? result : PumpResult.WAITING;
        }
        return results;
    }

    /** Fuel supply with a fixed number of logs. */
    static final class Logs implements Pump.FuelSupply {
        int logs;
        int consumed;

        Logs(int logs) {
            this.logs = logs;
        }

        @Override
        public boolean consumeLog() {
            if (logs == 0) {
                return false;
            }
            logs--;
            consumed++;
            return true;
        }
    }

    /** A log-fueled ground pump facing east with plenty of logs. */
    static Pump fueledPump(PipeGrid grid, int x, int y, PumpTier tier, FluidType fluid) {
        return fueledPump(grid, x, y, tier, fluid, Direction.EAST);
    }

    /** A log-fueled ground pump facing {@code facing} with plenty of logs. */
    static Pump fueledPump(PipeGrid grid, int x, int y, PumpTier tier, FluidType fluid, Direction facing) {
        Pump pump = pump(grid, x, y, tier, fluid, facing);
        pump.setFuelSupply(new Logs(1000));
        return pump;
    }

    /**
     * A {@link LiquidTileLookup} built from ASCII rows (row 0 is tile y = 0); tiles outside are land.
     * <pre>
     * s  seawater      f  freshwater     l  lava      o  crude oil (deep seawater)
     * .  land          u  not loaded
     * </pre>
     * {@link #consume} turns a tile into land and records it. {@link #unload} and {@link #load}
     * unload a tile and load it again with what it held (or what {@link #set} put there meanwhile).
     */
    static final class Liquids implements LiquidTileLookup {
        private final Map<Long, Character> tiles = new HashMap<>();
        private final Set<Long> unloaded = new HashSet<>();
        final Set<Long> consumed = new HashSet<>();
        final java.util.List<long[]> order = new java.util.ArrayList<>();

        Liquids(String... rows) {
            for (int y = 0; y < rows.length; y++) {
                for (int x = 0; x < rows[y].length(); x++) {
                    tiles.put(PipeGrid.key(x, y), rows[y].charAt(x));
                }
            }
        }

        private char at(int x, int y) {
            Character c = tiles.get(PipeGrid.key(x, y));
            return c == null ? '.' : c;
        }

        /** Changes a tile (loaded or not). */
        void set(int x, int y, char c) {
            tiles.put(PipeGrid.key(x, y), c);
        }

        /** Unloads the tiles of a rectangle (inclusive). */
        void unload(int x0, int y0, int x1, int y1) {
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    unloaded.add(PipeGrid.key(x, y));
                }
            }
        }

        /** Loads the tiles of a rectangle (inclusive) again. */
        void load(int x0, int y0, int x1, int y1) {
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    unloaded.remove(PipeGrid.key(x, y));
                }
            }
        }

        @Override
        public boolean isLoaded(int tileX, int tileY) {
            return at(tileX, tileY) != 'u' && !unloaded.contains(PipeGrid.key(tileX, tileY));
        }

        @Override
        public FluidType getFluid(int tileX, int tileY) {
            if (!isLoaded(tileX, tileY)) {
                return null;
            }
            switch (at(tileX, tileY)) {
                case 's':
                    return FluidType.SEAWATER;
                case 'f':
                    return FluidType.FRESHWATER;
                case 'l':
                    return FluidType.LAVA;
                case 'o':
                    return FluidType.CRUDE_OIL;
                default:
                    return null;
            }
        }

        @Override
        public void consume(int tileX, int tileY) {
            tiles.put(PipeGrid.key(tileX, tileY), '.');
            consumed.add(PipeGrid.key(tileX, tileY));
            order.add(new long[]{tileX, tileY});
        }
    }

}
