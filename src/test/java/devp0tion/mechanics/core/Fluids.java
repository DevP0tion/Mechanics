package devp0tion.mechanics.core;

import java.util.Collections;

/** Test helpers for the fluid and pipe tests. */
final class Fluids {

    /**
     * Test pipe transport amounts (the real values are undecided, numbers.md table 2):
     * COPPER 10, IRON 20, GOLD 30, ... (tier order x 10).
     */
    static final PipeTierRules TIERS = tier -> (tier.ordinal() + 1) * 10;

    private Fluids() {
    }

    /** Every tier carries the same amount per cell. */
    static PipeTierRules uniform(final int amount) {
        return tier -> amount;
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
        for (int x = x0; x <= x1; x++) {
            grid.placePipe(x, y, PipeLayer.BASE, tier);
        }
    }

    /** Fills every pipe of a row segment to capacity with {@code fluid}. */
    static void fill(PipeGrid grid, int x0, int x1, int y, PipeLayer layer, FluidType fluid) {
        for (int x = x0; x <= x1; x++) {
            PipeNode node = grid.getPipe(x, y, layer);
            node.setContents(fluid, node.getCapacity());
        }
    }

    /** A pump placed at the tile, standing on an infinite liquid tile of {@code fluid}. */
    static Pump pump(PipeGrid grid, int x, int y, PumpTier tier, FluidType fluid) {
        Pump pump = new Pump(tier);
        pump.setSource(new LiquidTileSource(fluid));
        grid.placePump(x, y, pump);
        return pump;
    }

    /** Ticks a log-fueled pump until something other than WAITING happens. */
    static PumpResult cycle(Pump pump) {
        for (int i = 0; i < 1000; i++) {
            PumpResult result = pump.tick();
            if (result.getStatus() != PumpResult.Status.WAITING) {
                return result;
            }
        }
        throw new AssertionError("no cycle within 1000 ticks");
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

    /** A log-fueled pump with plenty of logs. */
    static Pump fueledPump(PipeGrid grid, int x, int y, PumpTier tier, FluidType fluid) {
        Pump pump = pump(grid, x, y, tier, fluid);
        pump.setFuelSupply(new Logs(1000));
        return pump;
    }

}
