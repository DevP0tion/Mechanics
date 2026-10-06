package devp0tion.mechanics.core;

import java.util.Collections;
import java.util.List;

/**
 * A connected group of linked pipes ({@link PipeGrid} builds and rebuilds them).
 *
 * <ul>
 *     <li>One fluid type per network (12-7): {@link #getFluid()} is the fluid of its pipes, or
 *     {@code null} while they are all empty.</li>
 *     <li>The whole network uses the conditions of its lowest pipe tier (9-10): every pipe cell
 *     holds up to that tier's transport amount (N7-3, {@link #getCellCapacity()}).</li>
 * </ul>
 * The fluid is stored in the pipes themselves, not in the network, so splitting or merging
 * networks never has to divide an amount.
 */
public final class PipeNetwork {

    private final List<PipeNode> nodes;
    private final MineralTier lowestTier;
    private final int cellCapacity;
    private FluidType fluid;

    PipeNetwork(List<PipeNode> nodes, MineralTier lowestTier, int cellCapacity) {
        this.nodes = Collections.unmodifiableList(nodes);
        this.lowestTier = lowestTier;
        this.cellCapacity = cellCapacity;
        this.fluid = findFluid();
    }

    public List<PipeNode> getNodes() {
        return nodes;
    }

    public int size() {
        return nodes.size();
    }

    /** The lowest pipe tier in the network, whose conditions the network uses (9-10). */
    public MineralTier getLowestTier() {
        return lowestTier;
    }

    /** Fluid units each pipe cell holds: the lowest tier's transport amount (N7-3, 9-10). */
    public int getCellCapacity() {
        return cellCapacity;
    }

    /** The network's fluid, or {@code null} while every pipe is empty (12-7). */
    public FluidType getFluid() {
        return fluid;
    }

    /** Whether fluid of {@code type} may enter: the network is empty or already holds it (12-7). */
    public boolean canCarry(FluidType type) {
        return type != null && (fluid == null || fluid == type);
    }

    /** Total fluid held by the network's pipes. */
    public long getTotalAmount() {
        long total = 0;
        for (PipeNode node : nodes) {
            total += node.getAmount();
        }
        return total;
    }

    void onNodeContentsChanged(PipeNode node) {
        if (node.getFluid() != null) {
            fluid = node.getFluid();
        } else if (fluid != null) {
            fluid = findFluid();
        }
    }

    private FluidType findFluid() {
        for (PipeNode node : nodes) {
            if (node.getFluid() != null) {
                return node.getFluid();
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "PipeNetwork[" + nodes.size() + " pipes, " + lowestTier + ", cell " + cellCapacity + ", "
                + (fluid == null ? "empty" : fluid + " " + getTotalAmount()) + "]";
    }

}
