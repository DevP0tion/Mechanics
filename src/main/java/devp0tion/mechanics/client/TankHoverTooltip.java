package devp0tion.mechanics.client;

import devp0tion.mechanics.tank.TankControllerObjectEntity;
import devp0tion.mechanics.tank.TankRegistry;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.util.GameMath;
import necesse.engine.util.GameUtils;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.gameTooltips.GameTooltipManager;
import necesse.gfx.gameTooltips.StringTooltips;
import necesse.gfx.gameTooltips.TooltipLocation;
import necesse.level.maps.Level;
import necesse.level.maps.LevelObject;
import net.bytebuddy.asm.Advice;

/**
 * The tooltip shown while the cursor is over a recognized tank's interior (5-4): the tank state as
 * {@code <fluid name> <current>/<max>} (6-2, 6-9), or "비어 있음" / "Empty" for an empty tank
 * (N31-7). With mouse input the game draws hover tooltips just right of the cursor (6-2).
 *
 * <p>An empty interior tile has no object of its own to hover, so the HUD's object hover call
 * ({@code LevelObject.onMouseHover}, which the HUD makes for the object under the cursor, the air
 * object included) is patched. The HUD may call it twice per frame (interactable object and hit
 * object); the tooltip is only added for the hit object, so it appears once.
 *
 * <p>Only recognized tanks show the tooltip: an inactive tank (wall broken, fluid kept, 5-9) shows
 * none (N31-6).
 */
public final class TankHoverTooltip {

    private TankHoverTooltip() {
    }

    /** {@code LevelObject.onMouseHover(camera, perspective, debug)}. */
    @ModMethodPatch(target = LevelObject.class, name = "onMouseHover",
            arguments = {GameCamera.class, PlayerMob.class, boolean.class})
    public static class HoverPatch {

        @Advice.OnMethodExit
        static void onExit(@Advice.This LevelObject hovered, @Advice.Argument(0) GameCamera camera) {
            TankHoverTooltip.onObjectHover(hovered, camera);
        }

    }

    public static void onObjectHover(LevelObject hovered, GameCamera camera) {
        Level level = hovered.level;
        if (level == null || !level.isClient() || camera == null) {
            return;
        }
        int mouseX = camera.getMouseLevelPosX();
        int mouseY = camera.getMouseLevelPosY();
        LevelObject hit = GameUtils.getInteractObjectHit(level, mouseX, mouseY, 0, null);
        if (hit == null || hit.tileX != hovered.tileX || hit.tileY != hovered.tileY || hit.layerID != hovered.layerID) {
            return;
        }
        TankControllerObjectEntity tank = TankRegistry.findTankWithInterior(level,
                GameMath.getTileCoordinate(mouseX), GameMath.getTileCoordinate(mouseY));
        if (tank != null) {
            GameTooltipManager.addTooltip(new StringTooltips(tank.getStatusText()), TooltipLocation.INTERACT_FOCUS);
        }
    }

}
