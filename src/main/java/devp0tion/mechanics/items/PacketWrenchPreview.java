package devp0tion.mechanics.items;

import devp0tion.mechanics.client.WrenchTooltip;
import devp0tion.mechanics.core.FluidType;
import devp0tion.mechanics.core.PipeGrid;
import devp0tion.mechanics.wrench.WrenchMode;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.level.maps.Level;

/**
 * Server to client: the answer to {@link PacketWrenchPreviewRequest} for one tile (N30-1, N30-2):
 * whether it has a basic and an underground pipe in the engine and their fluids, and what a right
 * click toward each side ({@link devp0tion.mechanics.core.Direction} order) and on the middle would
 * return in the requested mode ({@code null}: not known, or no part to act on).
 */
public class PacketWrenchPreview extends Packet {

    /** Number of click results: four sides, then the middle. */
    public static final int CHECKS = 5;
    /** Index of the middle click's result. */
    public static final int MIDDLE = 4;

    /** Fluid byte: no pipe there. */
    private static final int NO_PIPE = 0;
    /** Fluid byte: an empty pipe; a fluid is {@code FIRST_FLUID + ordinal}. */
    private static final int EMPTY = 1;
    private static final int FIRST_FLUID = 2;
    /** Check byte: not known. */
    private static final int NO_CHECK = 255;

    public final int levelIdentifierHashCode;
    public final int tileX;
    public final int tileY;
    public final WrenchMode mode;
    public final boolean basePipe;
    public final FluidType baseFluid;
    public final boolean undergroundPipe;
    public final FluidType undergroundFluid;
    public final PipeGrid.Check[] checks;

    /** Received. */
    public PacketWrenchPreview(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        levelIdentifierHashCode = reader.getNextInt();
        tileX = reader.getNextInt();
        tileY = reader.getNextInt();
        mode = WrenchMode.fromOrdinal(reader.getNextByteUnsigned());
        int base = reader.getNextByteUnsigned();
        basePipe = base != NO_PIPE;
        baseFluid = fluidOf(base);
        int under = reader.getNextByteUnsigned();
        undergroundPipe = under != NO_PIPE;
        undergroundFluid = fluidOf(under);
        checks = new PipeGrid.Check[CHECKS];
        PipeGrid.Check[] values = PipeGrid.Check.values();
        for (int i = 0; i < CHECKS; i++) {
            int code = reader.getNextByteUnsigned();
            checks[i] = code < values.length ? values[code] : null;
        }
    }

    public PacketWrenchPreview(Level level, int tileX, int tileY, WrenchMode mode, boolean basePipe, FluidType baseFluid,
                               boolean undergroundPipe, FluidType undergroundFluid, PipeGrid.Check[] checks) {
        this.levelIdentifierHashCode = level.getIdentifierHashCode();
        this.tileX = tileX;
        this.tileY = tileY;
        this.mode = mode;
        this.basePipe = basePipe;
        this.baseFluid = baseFluid;
        this.undergroundPipe = undergroundPipe;
        this.undergroundFluid = undergroundFluid;
        this.checks = checks;
        PacketWriter writer = new PacketWriter(this);
        writer.putNextInt(levelIdentifierHashCode);
        writer.putNextInt(tileX);
        writer.putNextInt(tileY);
        writer.putNextByteUnsigned(mode.ordinal());
        writer.putNextByteUnsigned(fluidCode(basePipe, baseFluid));
        writer.putNextByteUnsigned(fluidCode(undergroundPipe, undergroundFluid));
        for (int i = 0; i < CHECKS; i++) {
            PipeGrid.Check check = i < checks.length ? checks[i] : null;
            writer.putNextByteUnsigned(check == null ? NO_CHECK : check.ordinal());
        }
    }

    private static int fluidCode(boolean pipe, FluidType fluid) {
        if (!pipe) {
            return NO_PIPE;
        }
        return fluid == null ? EMPTY : FIRST_FLUID + fluid.ordinal();
    }

    private static FluidType fluidOf(int code) {
        FluidType[] values = FluidType.values();
        int ordinal = code - FIRST_FLUID;
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    /** The result of the click toward a side, or on the middle for {@code null}. */
    public PipeGrid.Check checkOf(devp0tion.mechanics.core.Direction side) {
        return checks[side == null ? MIDDLE : side.ordinal()];
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
        Level level = client.getLevel();
        if (level == null || level.getIdentifierHashCode() != levelIdentifierHashCode) {
            return;
        }
        WrenchTooltip.onPreview(level, this);
    }

}
