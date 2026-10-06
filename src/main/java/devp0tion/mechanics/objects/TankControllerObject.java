package devp0tion.mechanics.objects;

import devp0tion.mechanics.core.TankStructure;
import devp0tion.mechanics.registry.MechanicsContainers;
import devp0tion.mechanics.tank.LevelTankCellLookup;
import devp0tion.mechanics.tank.TankControllerObjectEntity;
import devp0tion.mechanics.tank.TankInteriorPlacement;
import necesse.engine.localization.Localization;
import necesse.engine.network.packet.PacketOpenContainer;
import necesse.engine.registries.ContainerRegistry;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.inventory.InventoryItem;
import necesse.level.maps.Level;

import java.awt.Color;

/**
 * Tank controller (탱크 컨트롤러): recognizes the multiblock tank and shows its state (2-1, 2-2).
 *
 * <ul>
 *     <li>Exactly one per tank, anywhere in the border including the corners (4-3, 4-5); takes
 *     the place of a mineral wall (5-13).</li>
 *     <li>Holds the tank's fluid in its object entity ({@link TankControllerObjectEntity}, 5-9);
 *     breaking it loses the fluid (5-10).</li>
 *     <li>Interacting opens the controller window (5-4).</li>
 *     <li>Placement is rejected where the controller would be part of two tanks at once, i.e. in
 *     a wall shared by two tanks (N8-1), judged against the tanks their controllers hold now (N13-3),
 *     and inside a recognized tank (N16-2); the item description says so (N11-6).</li>
 *     <li>It keeps the tank it recognized first (N13-3, {@link TankControllerObjectEntity}).</li>
 * </ul>
 */
public class TankControllerObject extends TankBorderBlockObject {

    /** canPlace error when the controller would sit in a wall shared by two tanks (N8-1). */
    public static final String SHARED_WALL_ERROR = "tanksharedwall";

    public TankControllerObject(String textureName) {
        // Minimap color: the base shade of the texture palette (art choice, not a design value).
        super(textureName, new Color(112, 122, 140));
    }

    @Override
    public String canPlace(Level level, int layerID, int x, int y, int rotation, boolean byPlayer, boolean ignoreOtherLayers) {
        String error = super.canPlace(level, layerID, x, y, rotation, byPlayer, ignoreOtherLayers);
        if (error != null) {
            return error;
        }
        if (!TankStructure.canPlaceController(x, y, new LevelTankCellLookup(level))) {
            return SHARED_WALL_ERROR;
        }
        return null;
    }

    @Override
    public boolean canReplace(Level level, int layerID, int tileX, int tileY, int rotation) {
        // Replacing a wall with a controller that would then be rejected would only destroy the wall.
        return TankStructure.canPlaceController(tileX, tileY, new LevelTankCellLookup(level))
                && super.canReplace(level, layerID, tileX, tileY, rotation);
    }

    @Override
    public ObjectEntity getNewObjectEntity(Level level, int x, int y) {
        return new TankControllerObjectEntity(level, x, y);
    }

    @Override
    public boolean canInteract(Level level, int x, int y, PlayerMob player) {
        return true;
    }

    @Override
    public String getInteractTip(Level level, int x, int y, PlayerMob perspective, boolean debug) {
        return Localization.translate("controls", "usetip");
    }

    @Override
    public void interact(Level level, int x, int y, PlayerMob player) {
        super.interact(level, x, y, player);
        if (level.isServer()) {
            TankControllerObjectEntity controller = getCurrentObjectEntity(level, x, y, TankControllerObjectEntity.class);
            if (controller != null) {
                PacketOpenContainer packet = PacketOpenContainer.ObjectEntity(MechanicsContainers.TANK_CONTROLLER, controller);
                ContainerRegistry.openAndSendContainer(player.getServerClient(), packet);
            }
        }
    }

    @Override
    public ListGameTooltips getItemTooltips(InventoryItem item, PlayerMob perspective) {
        ListGameTooltips tooltips = super.getItemTooltips(item, perspective);
        // Placement rejection rules (N11-6): not in a wall shared by two tanks (N8-1); not inside a
        // recognized tank (N16-2).
        tooltips.add(Localization.translate("itemtooltip", "tankcontrollertip"), 400);
        tooltips.add(TankInteriorPlacement.rejectedTooltip(), 400);
        return tooltips;
    }

}
