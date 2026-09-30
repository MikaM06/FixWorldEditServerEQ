package fr.earthquest.fixworldedit;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class BlockWriter {
    private final Plugin plugin;
    private final BuildGuard guard;
    private final NativeBlocks natives;
    private final ExtendedLog log;
    private boolean warnedFallback;

    public BlockWriter(Plugin plugin, BuildGuard guard, NativeBlocks natives, ExtendedLog log) {
        this.plugin = plugin;
        this.guard = guard;
        this.natives = natives;
        this.log = log;
    }

    public ExtendedLog log() {
        return log;
    }

    public String describeNative(World world) {
        return natives.describe(world);
    }

    public int typeId(Block block) {
        int nativeId = natives.getId(block.getWorld(), block.getX(), block.getY(), block.getZ());
        return nativeId >= 0 ? nativeId : block.getTypeId();
    }

    public int typeIdAt(World world, int x, int y, int z) {
        int nativeId = natives.getId(world, x, y, z);
        return nativeId >= 0 ? nativeId : world.getBlockTypeIdAt(x, y, z);
    }

    public boolean canBuild(Player player, Block block) {
        return guard.canBuild(player, block);
    }

    public boolean place(Block block, int id, int data) {
        int previousId = typeId(block);
        byte previousData = block.getData();

        if (!write(block, id, data)) {
            return false;
        }
        int written = typeId(block);
        if (written != id) {
            log.debug("ID " + id + " refuse par le serveur (relu : " + written + "), ancien bloc conserve");
            write(block, previousId, previousData);
            return false;
        }
        log.trace("Placement", id, data);
        return true;
    }

    private boolean write(Block block, int id, int data) {
        World world = block.getWorld();
        if (natives.isAvailable(world)) {
            return natives.setBlock(world, block.getX(), block.getY(), block.getZ(), id, data);
        }
        if (id > ServerBlockRegistry.VANILLA_MAX_ID && !warnedFallback) {
            warnedFallback = true;
            log.warn("Pose native indisponible, l'API Bukkit tronquera l'ID " + id + " : "
                    + natives.describe(world));
        }
        return block.setTypeIdAndData(id, (byte) data, false);
    }

    public boolean runSync(Callable<Boolean> action) {
        if (plugin.getServer().isPrimaryThread()) {
            try {
                return Boolean.TRUE.equals(action.call());
            } catch (Exception failed) {
                log.warn("Pose interrompue", failed);
                return false;
            }
        }
        try {
            Future<Boolean> future = plugin.getServer().getScheduler().callSyncMethod(plugin, action);
            return Boolean.TRUE.equals(future.get());
        } catch (Exception failed) {
            log.warn("Pose differee interrompue", failed);
            return false;
        }
    }
}
