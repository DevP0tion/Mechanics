package devp0tion.mechanics.items;

import devp0tion.mechanics.core.Direction;
import devp0tion.mechanics.core.PipeGrid;
import devp0tion.mechanics.core.PipeLayer;
import devp0tion.mechanics.core.PipeNode;
import devp0tion.mechanics.pipe.PipeSystem;
import devp0tion.mechanics.wrench.WrenchMode;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.level.maps.Level;

/**
 * Client to server: the wrench's tooltip asks what only the server knows about a tile (N30-1,
 * N30-2): the fluids of its pipes (they stay on the server) and what a right click toward each side
 * and on the middle would return in the given mode. The server answers with
 * {@link PacketWrenchPreview}. Read only: nothing changes and no region is loaded; a tile that is
 * not loaded gets no answer.
 *
 * <p>The mode is the client's: the preview follows the client's switch at once. The clicks
 * themselves use the server's copy of the item (N30-5).
 */
public class PacketWrenchPreviewRequest extends Packet {

    public final int tileX;
    public final int tileY;
    public final WrenchMode mode;

    /** Received. */
    public PacketWrenchPreviewRequest(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        tileX = reader.getNextInt();
        tileY = reader.getNextInt();
        mode = WrenchMode.fromOrdinal(reader.getNextByteUnsigned());
    }

    public PacketWrenchPreviewRequest(int tileX, int tileY, WrenchMode mode) {
        this.tileX = tileX;
        this.tileY = tileY;
        this.mode = mode;
        PacketWriter writer = new PacketWriter(this);
        writer.putNextInt(tileX);
        writer.putNextInt(tileY);
        writer.putNextByteUnsigned(mode.ordinal());
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        if (!client.checkHasRequestedSelf() || client.playerMob == null) {
            return;
        }
        Level level = client.playerMob.getLevel();
        if (level == null || !level.isTileWithinBounds(tileX, tileY) || !level.regionManager.isTileLoaded(tileX, tileY)) {
            return;
        }
        PipeSystem system = PipeSystem.getIfExists(level);
        PipeGrid grid = system == null ? null : system.getGrid();
        PipeNode base = grid == null ? null : grid.getPipe(tileX, tileY, PipeLayer.BASE);
        PipeNode under = grid == null ? null : grid.getPipe(tileX, tileY, PipeLayer.UNDERGROUND);
        PipeGrid.Check[] checks = new PipeGrid.Check[PacketWrenchPreview.CHECKS];
        PipeGrid.Part part = MechanicsWrenchItem.partAt(level, tileX, tileY, mode);
        if (grid != null && part != null) {
            for (Direction d : Direction.values()) {
                checks[d.ordinal()] = checkSide(level, grid, part, d);
            }
            checks[PacketWrenchPreview.MIDDLE] = grid.checkToggleVertical(tileX, tileY);
        }
        server.network.sendPacket(new PacketWrenchPreview(level, tileX, tileY, mode, base != null,
                base == null ? null : base.getFluid(), under != null, under == null ? null : under.getFluid(), checks), client);
    }

    /**
     * What the side click would return ({@link PipeSystem#toggleSide}): when the neighbour's region
     * is not loaded the click loads it first, so only a region that was never generated can be
     * told (refused); otherwise the result cannot be known without loading it and none is given.
     */
    private PipeGrid.Check checkSide(Level level, PipeGrid grid, PipeGrid.Part part, Direction d) {
        int x = tileX + d.dx;
        int y = tileY + d.dy;
        if (level.isTileWithinBounds(x, y) && !level.regionManager.isTileLoaded(x, y)) {
            boolean generated = level.regionManager.isRegionGenerated(level.regionManager.getRegionCoordByTile(x),
                    level.regionManager.getRegionCoordByTile(y));
            return generated ? null : PipeGrid.Check.NOT_LOADED;
        }
        return grid.checkToggleSide(tileX, tileY, part, d);
    }

}
