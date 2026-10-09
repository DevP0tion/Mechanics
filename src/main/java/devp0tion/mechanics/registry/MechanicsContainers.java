package devp0tion.mechanics.registry;

import devp0tion.mechanics.client.PumpContainerForm;
import devp0tion.mechanics.client.TankControllerContainerForm;
import devp0tion.mechanics.pipe.PumpContainer;
import devp0tion.mechanics.pipe.PumpObjectEntity;
import devp0tion.mechanics.tank.TankControllerContainer;
import devp0tion.mechanics.tank.TankControllerObjectEntity;
import necesse.engine.network.PacketReader;
import necesse.engine.registries.ContainerRegistry;

/**
 * Container (UI window) registration.
 */
public final class MechanicsContainers {

    /** The tank controller window (5-4). */
    public static int TANK_CONTROLLER;
    /** The log-fueled pumps' window: the fuel slot and the pump's state (N31-11, N36-44). */
    public static int PUMP;

    private MechanicsContainers() {
    }

    public static void load() {
        TANK_CONTROLLER = ContainerRegistry.registerOEContainer(
                (client, uniqueSeed, oe, content) -> new TankControllerContainerForm(client,
                        new TankControllerContainer(client.getClient(), uniqueSeed, (TankControllerObjectEntity) oe)),
                (client, uniqueSeed, oe, content, serverObject) ->
                        new TankControllerContainer(client, uniqueSeed, (TankControllerObjectEntity) oe));
        // As the vanilla object inventory window (settlement dependant), with the pump's own form.
        PUMP = ContainerRegistry.registerSettlementDependantOEContainer(
                (client, uniqueSeed, settlement, oe, content) -> new PumpContainerForm(client,
                        new PumpContainer(client.getClient(), uniqueSeed, settlement, (PumpObjectEntity) oe, new PacketReader(content))),
                (client, uniqueSeed, settlement, oe, content, serverObject) ->
                        new PumpContainer(client, uniqueSeed, settlement, (PumpObjectEntity) oe, new PacketReader(content)));
    }

}
