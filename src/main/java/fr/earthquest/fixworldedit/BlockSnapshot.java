package fr.earthquest.fixworldedit;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

public final class BlockSnapshot {
    private final BlockState state;
    private final Object tileNbt;

    public BlockSnapshot(Block block) {
        this.state = block.getState();
        int actual = block.getTypeId();
        if (state.getTypeId() != actual) {
            throw new IllegalStateException("Bukkit ne represente pas l'ID " + actual
                    + " (BlockState annonce " + state.getTypeId() + ") : sauvegarde impossible");
        }
        this.tileNbt = NativeTiles.captureNbt(block.getWorld(), state.getX(), state.getY(), state.getZ());
    }

    public int getTypeId() {
        return state.getTypeId();
    }

    public boolean restore() {
        boolean updated = state.update(true, false);
        if (tileNbt != null) {
            NativeTiles.applyNbt(state.getBlock().getWorld(), state.getX(), state.getY(), state.getZ(), tileNbt);
        }
        return updated;
    }
}
