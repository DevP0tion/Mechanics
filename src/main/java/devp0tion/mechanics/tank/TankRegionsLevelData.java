package devp0tion.mechanics.tank;

import necesse.engine.registries.LevelDataRegistry;
import necesse.engine.save.LoadData;
import necesse.level.maps.levelData.LevelData;

/**
 * No longer used: tanks no longer keep the regions they span loaded together (N20-8, N15-6
 * replaced). Each controller saves its own judgment instead ({@link TankControllerObjectEntity}).
 *
 * <p>Kept registered so that levels saved with it (round 4a to N20) still load: the saved entries
 * are read and discarded, nothing is kept loaded, and it is not saved again (the way the game drops
 * its own deprecated level data). Its {@link #KEY} is still looked up elsewhere.
 */
@Deprecated
public class TankRegionsLevelData extends LevelData {

    /** Registry stringID and level data key of the saved data; must stay the same for saved worlds. */
    public static final String KEY = "mechanicstankregions";

    public TankRegionsLevelData() {
    }

    /** Registers the level data type (mod init), so saved levels holding it load. */
    public static void register() {
        LevelDataRegistry.registerLevelData(KEY, TankRegionsLevelData.class);
    }

    @Override
    public void applyLoadData(LoadData save) {
        super.applyLoadData(save);
        // The saved tank entries are discarded (N20-8).
    }

    @Override
    public boolean shouldSave() {
        return false;
    }

}
