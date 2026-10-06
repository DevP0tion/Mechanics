package devp0tion.mechanics.registry;

import devp0tion.mechanics.client.TankControllerContainerForm;
import devp0tion.mechanics.tank.TankControllerContainer;
import devp0tion.mechanics.tank.TankControllerObjectEntity;
import necesse.engine.registries.ContainerRegistry;

/**
 * Container (UI window) registration.
 */
public final class MechanicsContainers {

    /** The tank controller window (5-4). */
    public static int TANK_CONTROLLER;

    private MechanicsContainers() {
    }

    public static void load() {
        TANK_CONTROLLER = ContainerRegistry.registerOEContainer(
                (client, uniqueSeed, oe, content) -> new TankControllerContainerForm(client,
                        new TankControllerContainer(client.getClient(), uniqueSeed, (TankControllerObjectEntity) oe)),
                (client, uniqueSeed, oe, content, serverObject) ->
                        new TankControllerContainer(client, uniqueSeed, (TankControllerObjectEntity) oe));
    }

}
