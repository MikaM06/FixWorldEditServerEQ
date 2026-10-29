package fr.earthquest.fixworldedit;

import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.extent.Extent;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Monde WorldEdit qui lit et pose les IDs superieurs a 4095 au lieu de lever
 * "Can't have a block ID above 4095" des qu'une commande touche un bloc etendu.
 */
final class ExtendedWorld extends BukkitWorld {
    private final NativeBlocks natives;
    private final ServerBlockRegistry registry;

    ExtendedWorld(World world, NativeBlocks natives, ServerBlockRegistry registry) {
        super(world);
        this.natives = natives;
        this.registry = registry;
    }

    static Extent wrap(Extent extent, NativeBlocks natives, ServerBlockRegistry registry) {
        if (extent instanceof BukkitWorld && !(extent instanceof ExtendedWorld)) {
            return new ExtendedWorld(((BukkitWorld) extent).getWorld(), natives, registry);
        }
        return extent;
    }

    @Override
    public BaseBlock getBlock(Vector position) {
        BaseBlock extended = readExtended(position, true);
        return extended != null ? extended : super.getBlock(position);
    }

    @Override
    public BaseBlock getLazyBlock(Vector position) {
        BaseBlock extended = readExtended(position, false);
        return extended != null ? extended : super.getLazyBlock(position);
    }

    @Override
    public boolean setBlock(Vector position, BaseBlock block, boolean notifyAndLight) throws WorldEditException {
        if (block.getId() <= ServerBlockRegistry.VANILLA_MAX_ID) {
            return super.setBlock(position, block, notifyAndLight);
        }
        World world = getWorld();
        int x = position.getBlockX();
        int y = position.getBlockY();
        int z = position.getBlockZ();
        if (!natives.setBlock(world, x, y, z, block.getId(), block.getData())) {
            return false;
        }
        if (block instanceof ExtendedBlock) {
            NativeTiles.applyNbt(world, x, y, z, ((ExtendedBlock) block).tileAt(x, y, z));
        }
        return true;
    }

    @Override
    public boolean isValidBlockType(int type) {
        return type > ServerBlockRegistry.VANILLA_MAX_ID ? registry.isBlock(type) : super.isValidBlockType(type);
    }

    private ExtendedBlock readExtended(Vector position, boolean withTile) {
        World world = getWorld();
        int x = position.getBlockX();
        int y = position.getBlockY();
        int z = position.getBlockZ();
        Block block = world.getBlockAt(x, y, z);
        int id = block.getTypeId();
        if (id <= ServerBlockRegistry.VANILLA_MAX_ID) {
            return null;
        }
        Object tile = withTile ? NativeTiles.captureNbt(world, x, y, z) : null;
        return new ExtendedBlock(id, block.getData(), tile, x, y, z);
    }
}
