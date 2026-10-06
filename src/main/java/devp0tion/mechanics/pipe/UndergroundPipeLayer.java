package devp0tion.mechanics.pipe;

import necesse.engine.registries.ObjectLayerRegistry;
import necesse.level.maps.regionSystem.layers.objectLayer.ArrayObjectLayer;

/**
 * The underground pipes' own object layer (9-1, D2): a vanilla array layer registered by the mod.
 *
 * <p>Why a layer (and not only level data plus custom drawing and picking): the engine handles
 * every object layer generically, so the underground pipe objects are saved with the region files
 * ({@code <layer>Objects}), sent to clients with the region data, synced by the vanilla object
 * packets ({@code PacketPlaceObject}, {@code PacketChangeObject(s)}, which carry the layer), placed by
 * the vanilla object item (which places on the object's valid layers), drawn through
 * {@code GameObject.addLayerDrawables}, and they never block or are blocked by base layer objects
 * (the base layer only checks the layers of the object being placed). Only what the engine does
 * not keep per tile, the pipe state (fluid, link flags), lives in the level store
 * ({@link PipeSystem}, N14-4).
 */
public final class UndergroundPipeLayer {

    /** Layer stringID; also the region save name prefix. Must stay the same for saved worlds. */
    public static final String STRING_ID = "mechanicsunderground";

    /** The layer ID, valid after {@link #register()}. */
    public static int ID = -1;

    private UndergroundPipeLayer() {
    }

    /** Registers the layer (mod init, before any object). */
    public static void register() {
        ID = ObjectLayerRegistry.registerLayer(STRING_ID, ArrayObjectLayer.class);
    }

}
