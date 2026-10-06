package devp0tion.mechanics.core;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A pipe network (N13-1, N18-2): the pipes the fluid has actually reached, linked to each other and
 * holding the same fluid, together with the pumps pushing that fluid into them. {@link PipeGrid}
 * builds, merges and splits networks.
 *
 * <ul>
 *     <li>An empty pipe belongs to no network (N13-1).</li>
 *     <li>A newly placed pump starts a network of its own; when its fluid reaches a pipe of another
 *     network of the same fluid, the two become one (N18-2).</li>
 *     <li>One fluid per network (12-7): two pipes holding different fluids are never linked, the
 *     face between them is a dead end (N13-2).</li>
 *     <li>The lowest tier of its pipes is the one whose conditions are judged when the fluid reaches
 *     a new pipe (N12-4, N14-1).</li>
 *     <li>Route caching and its invalidation are per network (N18-2): the routes of each of its pumps
 *     are kept here until something changes on the tiles they cross.</li>
 * </ul>
 * The fluid is stored in the pipes themselves, so splitting or merging networks never has to divide
 * an amount.
 */
public final class PipeNetwork {

    final Set<PipeNode> nodes = new LinkedHashSet<>();
    final Set<Pump> pumps = new LinkedHashSet<>();
    FluidType fluid;
    MineralTier lowestTier;
    final Map<Pump, PipeGrid.PumpRoutes> routeCache = new IdentityHashMap<>();

    PipeNetwork() {
    }

    public Set<PipeNode> getNodes() {
        return Collections.unmodifiableSet(nodes);
    }

    public Set<Pump> getPumps() {
        return Collections.unmodifiableSet(pumps);
    }

    /** Number of pipes. */
    public int size() {
        return nodes.size();
    }

    /** The fluid of its pipes, or of its pump while no pipe is reached yet; {@code null} for a new pump. */
    public FluidType getFluid() {
        return fluid;
    }

    /** The lowest tier of its pipes, or {@code null} while it has none (N12-4). */
    public MineralTier getLowestTier() {
        return lowestTier;
    }

    /** Total fluid held by its pipes. */
    public long getTotalAmount() {
        long total = 0;
        for (PipeNode node : nodes) {
            total += node.getAmount();
        }
        return total;
    }

    void addNode(PipeNode node) {
        nodes.add(node);
        node.network = this;
        fluid = node.getFluid();
        lowestTier = lowestTier == null ? node.getTier() : MineralTier.lowest(lowestTier, node.getTier());
    }

    void addPump(Pump pump) {
        pumps.add(pump);
        pump.network = this;
        if (fluid == null) {
            fluid = pump.getLastPushedFluid();
        }
    }

    @Override
    public String toString() {
        return "PipeNetwork[" + nodes.size() + " pipes, " + pumps.size() + " pumps, " + lowestTier + ", "
                + (fluid == null ? "no fluid" : fluid + " " + getTotalAmount()) + "]";
    }

}
