package devp0tion.mechanics.pipe;

import necesse.engine.network.NetworkClient;
import necesse.engine.network.PacketReader;
import necesse.inventory.container.object.OEInventoryContainer;
import necesse.inventory.container.settlement.events.SettlementDataEvent;

/**
 * The window of the log-fueled pumps (N31-11, N36-44): the vanilla object inventory container with
 * the one fuel slot (11-7, 11-8); its form also shows the pump's output direction, form and state,
 * which clients already get through the object entity sync. The manual pump has no window: its
 * state is in the hover tooltip only (N36-57).
 */
public class PumpContainer extends OEInventoryContainer {

    public final PumpObjectEntity pump;

    public PumpContainer(NetworkClient client, int uniqueSeed, SettlementDataEvent settlement, PumpObjectEntity pump,
                         PacketReader reader) {
        super(client, uniqueSeed, settlement, pump, reader);
        this.pump = pump;
    }

}
