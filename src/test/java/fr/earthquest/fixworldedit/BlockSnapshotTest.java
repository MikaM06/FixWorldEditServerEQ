package fr.earthquest.fixworldedit;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class BlockSnapshotTest {
    public interface NativeWorldHandle {
        Object getHandle();
    }

    public static class NBTTagCompound {
        int inventory;

        public NBTTagCompound copy() {
            NBTTagCompound copy = new NBTTagCompound();
            copy.inventory = inventory;
            return copy;
        }
    }

    public static class TileEntity {
        int inventory = 42;
        boolean dirty;

        public void writeToNBT(NBTTagCompound nbt) {
            nbt.inventory = inventory;
        }

        public void readFromNBT(NBTTagCompound nbt) {
            inventory = nbt.inventory;
            nbt.inventory = -1;
        }

        public void markDirty() {
            dirty = true;
        }
    }

    public static class NativeWorld {
        final TileEntity tile = new TileEntity();

        public TileEntity getTileEntity(int x, int y, int z) {
            return tile;
        }
    }

    @Test
    public void preservesModdedTileEntityNbtAcrossRepeatedRestores() throws Exception {
        NativeWorld nativeWorld = new NativeWorld();
        World world = mock(World.class, withSettings().extraInterfaces(NativeWorldHandle.class));
        when(((NativeWorldHandle) world).getHandle()).thenReturn(nativeWorld);
        Block block = mock(Block.class);
        BlockState state = mock(BlockState.class);
        when(block.getWorld()).thenReturn(world);
        when(block.getTypeId()).thenReturn(5016);
        when(block.getData()).thenReturn((byte) 3);
        when(block.getState()).thenReturn(state);
        when(state.getTypeId()).thenReturn(5016);
        when(state.getBlock()).thenReturn(block);
        when(state.update(true, false)).thenReturn(true);
        BlockSnapshot snapshot = new BlockSnapshot(block);
        nativeWorld.tile.inventory = 0;
        snapshot.restore();
        assertEquals(42, nativeWorld.tile.inventory);
        assertTrue(nativeWorld.tile.dirty);
        nativeWorld.tile.inventory = 10;
        snapshot.restore();
        assertEquals(42, nativeWorld.tile.inventory);
    }

    @Test
    public void refusesSnapshotWhenBukkitCannotRepresentId() {
        Block block = mock(Block.class);
        BlockState state = mock(BlockState.class);
        when(block.getState()).thenReturn(state);
        when(block.getTypeId()).thenReturn(5016);
        when(state.getTypeId()).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> new BlockSnapshot(block));
        verify(state, never()).update(true, false);
    }
}
