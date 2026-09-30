package fr.earthquest.fixworldedit;

import com.sk89q.worldedit.history.UndoContext;
import com.sk89q.worldedit.history.change.Change;
import java.util.concurrent.Callable;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class BukkitBlockChange implements Change {
    private final BlockWriter writer;
    private final Player player;
    private final World world;
    private final int x;
    private final int y;
    private final int z;

    private final int previousId;
    private final byte previousData;
    private final Object previousNbt;

    private final int currentId;
    private final byte currentData;

    public BukkitBlockChange(BlockWriter writer, Player player, World world, int x, int y, int z,
                             int previousId, byte previousData, Object previousNbt,
                             int currentId, byte currentData) {
        this.writer = writer;
        this.player = player;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.previousId = previousId;
        this.previousData = previousData;
        this.previousNbt = previousNbt;
        this.currentId = currentId;
        this.currentData = currentData;
    }

    @Override
    public void undo(UndoContext context) {
        apply(previousId, previousData, previousNbt);
    }

    @Override
    public void redo(UndoContext context) {
        apply(currentId, currentData, null);
    }

    private void apply(final int id, final byte data, final Object nbt) {
        writer.runSync(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                Block block = world.getBlockAt(x, y, z);
                if (!writer.canBuild(player, block)) {
                    writer.log().debug("Rejeu refuse par WorldGuard en " + x + "," + y + "," + z);
                    return false;
                }
                if (!writer.place(block, id, data)) {
                    return false;
                }
                if (nbt != null) {
                    NativeTiles.applyNbt(world, x, y, z, nbt);
                }
                return true;
            }
        });
    }
}
