package fr.earthquest.fixworldedit;

import com.sk89q.worldedit.history.UndoContext;
import com.sk89q.worldedit.history.change.Change;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class BulkBlockChange implements Change {
    private static final int SEGMENT = 1 << 16;

    private final BlockWriter writer;
    private final Player player;
    private final World world;

    private final List<int[]> positions = new ArrayList<int[]>();
    private final List<short[]> previousIds = new ArrayList<short[]>();
    private final List<byte[]> previousData = new ArrayList<byte[]>();
    private final List<short[]> currentIds = new ArrayList<short[]>();
    private final List<byte[]> currentData = new ArrayList<byte[]>();
    private int size;

    public BulkBlockChange(BlockWriter writer, Player player, World world) {
        this.writer = writer;
        this.player = player;
        this.world = world;
    }

    public void add(int x, int y, int z, int previousId, byte previousDataValue,
                    int currentId, byte currentDataValue) {
        int segment = size >>> 16;
        int offset = size & (SEGMENT - 1);
        if (offset == 0) {
            positions.add(new int[SEGMENT * 3]);
            previousIds.add(new short[SEGMENT]);
            previousData.add(new byte[SEGMENT]);
            currentIds.add(new short[SEGMENT]);
            currentData.add(new byte[SEGMENT]);
        }
        int[] slot = positions.get(segment);
        slot[offset * 3] = x;
        slot[offset * 3 + 1] = y;
        slot[offset * 3 + 2] = z;
        previousIds.get(segment)[offset] = (short) previousId;
        previousData.get(segment)[offset] = previousDataValue;
        currentIds.get(segment)[offset] = (short) currentId;
        currentData.get(segment)[offset] = currentDataValue;
        size++;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public long memoryBytes() {
        return (long) positions.size() * SEGMENT * 16L;
    }

    @Override
    public void undo(UndoContext context) {
        replay(previousIds, previousData, true);
    }

    @Override
    public void redo(UndoContext context) {
        replay(currentIds, currentData, false);
    }

    private void replay(final List<short[]> ids, final List<byte[]> data, final boolean backwards) {
        writer.runSync(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                for (int step = 0; step < size; step++) {
                    int index = backwards ? size - 1 - step : step;
                    int segment = index >>> 16;
                    int offset = index & (SEGMENT - 1);
                    int[] slot = positions.get(segment);
                    Block block = world.getBlockAt(slot[offset * 3], slot[offset * 3 + 1],
                            slot[offset * 3 + 2]);
                    if (writer.canBuild(player, block)) {
                        writer.place(block, ids.get(segment)[offset] & 0xFFFF,
                                data.get(segment)[offset]);
                    }
                }
                return true;
            }
        });
    }
}
