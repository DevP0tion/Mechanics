package devp0tion.mechanics.pipe;

import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.save.levelData.ObjectEntitySave;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.level.maps.Level;
import necesse.level.maps.regionSystem.Region;
import net.bytebuddy.asm.Advice;
import necesse.engine.modLoader.annotations.ModMethodPatch;

/**
 * The two engine patches that save the underground pipe holders ({@link UndergroundPipeHolder}) in
 * the engine's object entity section of the region files (N24-1).
 *
 * <ul>
 *     <li>{@link RegionSave}: {@code Region.addSaveData} writes the region's object entities into an
 *     {@code OBJECTENTITIES} section; the holders of the region are added to it as entries with a
 *     {@value UndergroundPipeHolder#LAYER_FIELD} field.</li>
 *     <li>{@link EntryLoad}: {@code ObjectEntitySave.loadSave} reads one entry of that section; an
 *     entry with a layer field goes to the mod's holder list ({@link PipeSystem#loadHolderEntry}) and
 *     the engine gets nothing for it. Without the mod, the engine reads such an entry as an object
 *     entity of the base object on its tile, finds no matching one and drops the entry with an
 *     error in the log.</li>
 * </ul>
 */
public final class HolderSavePatches {

    private HolderSavePatches() {
    }

    /** {@code Region.addSaveData(save)}: after the region wrote its sections. */
    @ModMethodPatch(target = Region.class, name = "addSaveData", arguments = {SaveData.class})
    public static class RegionSave {

        @Advice.OnMethodExit
        static void onExit(@Advice.This Region region, @Advice.Argument(0) SaveData save) {
            PipeSystem.addHolderEntries(region, save);
        }

    }

    /** {@code ObjectEntitySave.loadSave(save, level)}: one object entity entry of a region. */
    @ModMethodPatch(target = ObjectEntitySave.class, name = "loadSave", arguments = {LoadData.class, Level.class})
    public static class EntryLoad {

        /** True for a holder entry: the engine's method is skipped and returns {@code null}. */
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        static boolean onEnter(@Advice.Argument(0) LoadData save, @Advice.Argument(1) Level level) {
            return PipeSystem.loadHolderEntry(save, level);
        }

        @Advice.OnMethodExit
        static void onExit(@Advice.Enter boolean holder, @Advice.Return(readOnly = false) ObjectEntity result) {
            if (holder) {
                result = null;
            }
        }

    }

}
