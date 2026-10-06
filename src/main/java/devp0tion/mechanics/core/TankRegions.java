package devp0tion.mechanics.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Keeping the regions a tank spans loaded together (N15-6, D5).
 *
 * <p>The game unloads regions away from players one by one. A recognized tank that spans several
 * regions keeps them loaded together instead: while any of its regions is kept loaded by something
 * else (a player nearby, a settlement), all its regions stay loaded. Tanks that share a region are
 * kept together the same way, so a whole group of tanks linked through shared regions loads and
 * unloads as one. Regions kept only by this rule do not keep anything loaded by themselves, so the
 * group unloads once nothing else keeps any of it.
 */
public final class TankRegions {

    private TankRegions() {
    }

    /**
     * The regions the rectangle {@code bounds} covers, row by row.
     *
     * @param regionSize the side of a region in tiles (16 in the game)
     */
    public static List<GridPos> regionsOf(TankBounds bounds, int regionSize) {
        if (regionSize <= 0) {
            throw new IllegalArgumentException("regionSize must be positive: " + regionSize);
        }
        List<GridPos> regions = new ArrayList<>();
        int minRegionX = Math.floorDiv(bounds.x, regionSize);
        int maxRegionX = Math.floorDiv(bounds.getMaxX(), regionSize);
        int minRegionY = Math.floorDiv(bounds.y, regionSize);
        int maxRegionY = Math.floorDiv(bounds.getMaxY(), regionSize);
        for (int regionY = minRegionY; regionY <= maxRegionY; regionY++) {
            for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
                regions.add(new GridPos(regionX, regionY));
            }
        }
        return regions;
    }

    /**
     * The groups of regions to keep loaded now: for every group of tanks linked through shared
     * regions where at least one region {@code isKeptByOthers}, all the regions of that group. Each
     * group is returned separately (the game keeps a group's save timing together too).
     *
     * @param tanks          the regions of each recognized tank ({@link #regionsOf})
     * @param isKeptByOthers whether a region is loaded and kept loaded by something other than this
     *                       rule (a player nearby, a settlement)
     */
    public static List<Set<GridPos>> groupsToKeepLoaded(Collection<? extends Collection<GridPos>> tanks,
                                                         Predicate<GridPos> isKeptByOthers) {
        List<Set<GridPos>> groups = new ArrayList<>();
        List<Collection<GridPos>> remaining = new ArrayList<Collection<GridPos>>(tanks);
        while (!remaining.isEmpty()) {
            // Grow one group of tanks linked through shared regions.
            Set<GridPos> group = new LinkedHashSet<>(remaining.remove(remaining.size() - 1));
            boolean grown = true;
            while (grown) {
                grown = false;
                for (int i = remaining.size() - 1; i >= 0; i--) {
                    if (sharesAny(group, remaining.get(i))) {
                        group.addAll(remaining.remove(i));
                        grown = true;
                    }
                }
            }
            for (GridPos region : group) {
                if (isKeptByOthers.test(region)) {
                    groups.add(group);
                    break;
                }
            }
        }
        return groups;
    }

    private static boolean sharesAny(Set<GridPos> group, Collection<GridPos> regions) {
        for (GridPos region : regions) {
            if (group.contains(region)) {
                return true;
            }
        }
        return false;
    }

}
