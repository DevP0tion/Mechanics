package devp0tion.mechanics.objects;

import devp0tion.mechanics.core.TankStructure;
import devp0tion.mechanics.tank.LevelTankCellLookup;
import devp0tion.mechanics.tank.TankValveObjectEntity;
import necesse.engine.localization.Localization;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.inventory.InventoryItem;
import necesse.level.maps.Level;

import java.awt.Color;

/**
 * Tank valve (탱크 밸브): the inlet/outlet that pipes connect to (2-1, 2-2, 2-3).
 *
 * <ul>
 *     <li>Any number per tank, none needed, never on a corner (4-4, 4-5); takes the place of a
 *     mineral wall (5-13).</li>
 *     <li>Accepts incoming fluid automatically into its tank (N7-3, {@link TankValveObjectEntity});
 *     off while it receives a wire signal, on otherwise (N11-3).</li>
 *     <li>Placement is rejected where the valve would be part of two tanks at once, i.e. in a wall
 *     shared by two tanks (N11-1); the item description says so, and the wire rule (N11-3, N11-6).</li>
 * </ul>
 */
public class TankValveObject extends TankBorderBlockObject {

    /** canPlace error when the valve would sit in a wall shared by two tanks (N11-1). */
    public static final String SHARED_WALL_ERROR = "tanksharedwall";

    public TankValveObject(String textureName) {
        // Minimap color: the base shade of the texture palette (art choice, not a design value).
        super(textureName, new Color(158, 170, 184));
        showsWire = true;
    }

    @Override
    public String canPlace(Level level, int layerID, int x, int y, int rotation, boolean byPlayer, boolean ignoreOtherLayers) {
        String error = super.canPlace(level, layerID, x, y, rotation, byPlayer, ignoreOtherLayers);
        if (error != null) {
            return error;
        }
        if (!TankStructure.canPlaceValve(x, y, new LevelTankCellLookup(level))) {
            return SHARED_WALL_ERROR;
        }
        return null;
    }

    @Override
    public boolean canReplace(Level level, int layerID, int tileX, int tileY, int rotation) {
        // Replacing a wall with a valve that would then be rejected would only destroy the wall.
        return TankStructure.canPlaceValve(tileX, tileY, new LevelTankCellLookup(level))
                && super.canReplace(level, layerID, tileX, tileY, rotation);
    }

    @Override
    public ObjectEntity getNewObjectEntity(Level level, int x, int y) {
        return new TankValveObjectEntity(level, x, y);
    }

    @Override
    public void onWireUpdate(Level level, int layerID, int tileX, int tileY, int wireID, boolean active) {
        TankValveObjectEntity valve = getCurrentObjectEntity(level, tileX, tileY, TankValveObjectEntity.class);
        if (valve != null) {
            valve.updateWireSignal();
        }
    }

    @Override
    public ListGameTooltips getItemTooltips(InventoryItem item, PlayerMob perspective) {
        ListGameTooltips tooltips = super.getItemTooltips(item, perspective);
        // Placement rejection rules and the wire rule (N11-6): not in a wall shared by two tanks
        // (N11-1); off while receiving a wire signal (N11-3).
        tooltips.add(Localization.translate("itemtooltip", "tankvalvetip"), 400);
        return tooltips;
    }

}
