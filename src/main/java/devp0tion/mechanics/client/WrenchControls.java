package devp0tion.mechanics.client;

import devp0tion.mechanics.items.MechanicsWrenchItem;
import devp0tion.mechanics.items.PacketWrenchMode;
import devp0tion.mechanics.wrench.WrenchMode;
import necesse.engine.input.Control;
import necesse.engine.input.InputEvent;
import necesse.engine.localization.Localization;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.client.Client;
import necesse.engine.state.MainGame;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.gameFont.FontOptions;
import necesse.inventory.InventoryItem;
import necesse.inventory.PlayerInventorySlot;
import necesse.level.maps.Level;
import necesse.level.maps.hudManager.floatText.UniqueFloatText;
import net.bytebuddy.asm.Advice;

/**
 * The engineering wrench's mode key (N30-5): a mod key binding ({@code Control.addModControl}),
 * rebindable in the control settings, by default the middle mouse button. Pressed while the wrench
 * is the selected item, it switches the wrench's mode:
 *
 * <ol>
 *     <li>the client's copy of the item changes at once (its tooltip and next clicks' preview);</li>
 *     <li>{@link PacketWrenchMode} sets the server's copy, which the server's click handling uses;</li>
 *     <li>a brief notice over the player names the new mode, only at the moment of switching (no
 *     permanent indicator), as the vanilla smart mining toggle does.</li>
 * </ol>
 * While the wrench is the selected item the vanilla pipette (also the middle mouse button by
 * default) does nothing, whatever the keys are bound to: it would swap the held item.
 *
 * <p>Two patches, both acting on the local player only: {@link TickControlsPatch} reads the key
 * once per frame with the player's other controls, {@link PipettePatch} skips the vanilla pipette
 * ({@code PlayerMob.pipetteAt}, called only by the pipette control) while the wrench is selected.
 *
 * <p>TODO(game): not verified on a client (the dedicated server has no input): the key binding in
 * the control settings, the switch, the notice and the pipette suppression.
 */
public final class WrenchControls {

    /** The middle mouse button's input ID (the vanilla pipette's default key). */
    public static final int MIDDLE_MOUSE = -98;

    /** The control's ID; its name and tip are in the {@code [controls]} locale section. */
    public static final String CONTROL_ID = "mechanicswrenchmode";

    /**
     * Its own overlap group, so the control settings do not mark it as clashing with the pipette on
     * the same default key (the pipette does nothing while the wrench is held).
     */
    private static final String OVERLAP_GROUP = "mechanicswrench";

    private static Control mode;

    private WrenchControls() {
    }

    /** Adds the key binding; from the mod's init (mod controls can only be added while mods load). */
    public static void register() {
        mode = Control.addModControl(new Control(MIDDLE_MOUSE, CONTROL_ID, OVERLAP_GROUP)
                .setTooltip(new LocalMessage("controls", CONTROL_ID + "tip")));
    }

    /** Whether the player's selected item is the wrench. */
    public static boolean holdsWrench(PlayerMob player) {
        InventoryItem held = player == null ? null : player.getSelectedItem();
        return held != null && held.item instanceof MechanicsWrenchItem;
    }

    // ------------------------------------------------------------------ the mode key

    /** {@code PlayerMob.tickControls(mainGame, isGameTick, camera)}: the local player's controls, once per frame. */
    @ModMethodPatch(target = PlayerMob.class, name = "tickControls",
            arguments = {MainGame.class, boolean.class, GameCamera.class})
    public static class TickControlsPatch {

        @Advice.OnMethodEnter
        static void onEnter(@Advice.This PlayerMob player) {
            WrenchControls.tickControls(player);
        }

    }

    public static void tickControls(PlayerMob player) {
        if (mode == null || !mode.isPressed() || !holdsWrench(player)) {
            return;
        }
        // The key press is the switch's: no other control acts on it (the pipette on the same key).
        InputEvent event = mode.getEvent();
        if (event != null) {
            event.use();
        }
        switchMode(player);
    }

    /** Switches the selected wrench's mode on the client and the server, and shows the notice (N30-5). */
    static void switchMode(PlayerMob player) {
        Level level = player.getLevel();
        Client client = level == null ? null : level.getClient();
        if (client == null) {
            return;
        }
        PlayerInventorySlot slot = player.getSelectedItemSlot();
        InventoryItem item = slot.getItem(player.getInv());
        if (item == null || !(item.item instanceof MechanicsWrenchItem)) {
            return;
        }
        WrenchMode next = MechanicsWrenchItem.getMode(item).next();
        MechanicsWrenchItem.setMode(item, next);
        client.network.sendPacket(new PacketWrenchMode(slot, next));
        showNotice(player, level, next);
    }

    /** The brief notice over the player, like the vanilla smart mining toggle's (N30-5). */
    private static void showNotice(final PlayerMob player, Level level, WrenchMode mode) {
        String key = mode == WrenchMode.UNDERGROUND ? "mechanicswrenchundergroundmode" : "mechanicswrenchbasicmode";
        UniqueFloatText text = new UniqueFloatText(player.getX(), player.getY() - 20, Localization.translate("misc", key),
                new FontOptions(16).outline(), "mechanicswrenchmode") {

            @Override
            public int getAnchorX() {
                return player.getX();
            }

            @Override
            public int getAnchorY() {
                return player.getY() - 20;
            }
        };
        text.riseTime = 500;
        text.fadeOutTime = 500;
        text.expandTime = 50;
        level.hudManager.addElement(text);
    }

    // ------------------------------------------------------------------ pipette suppression

    /** {@code PlayerMob.pipetteAt(x, y)}: skipped while the wrench is the selected item (N30-5). */
    @ModMethodPatch(target = PlayerMob.class, name = "pipetteAt", arguments = {int.class, int.class})
    public static class PipettePatch {

        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        static boolean onEnter(@Advice.This PlayerMob player) {
            return WrenchControls.holdsWrench(player);
        }

    }

}
