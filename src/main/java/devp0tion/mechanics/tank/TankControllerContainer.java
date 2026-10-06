package devp0tion.mechanics.tank;

import necesse.engine.network.NetworkClient;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.container.Container;
import necesse.level.maps.Level;

/**
 * The tank controller window's container (5-4). It only shows the controller's state, which
 * clients already get through the object entity sync; there are no actions.
 */
public class TankControllerContainer extends Container {

    public final TankControllerObjectEntity controller;

    public TankControllerContainer(NetworkClient client, int uniqueSeed, TankControllerObjectEntity controller) {
        super(client, uniqueSeed);
        this.controller = controller;
    }

    @Override
    public boolean isValid(ServerClient client) {
        if (!super.isValid(client)) {
            return false;
        }
        Level level = client.getLevel();
        return !controller.removed() && level.getObject(controller.tileX, controller.tileY)
                .isInInteractRange(level, controller.tileX, controller.tileY, client.playerMob);
    }

}
