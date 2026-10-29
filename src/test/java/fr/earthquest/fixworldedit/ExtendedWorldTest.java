package fr.earthquest.fixworldedit;

import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.function.mask.BlockMask;
import com.sk89q.worldedit.function.mask.ExistingBlockMask;
import com.sk89q.worldedit.function.mask.FuzzyBlockMask;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.mask.Masks;
import java.util.Collections;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ExtendedWorldTest {
    private static final Vector POSITION = new Vector(10, 64, -3);

    private World world;
    private NativeBlocks natives;
    private ServerBlockRegistry registry;
    private ExtendedWorld extendedWorld;

    @Before
    public void setUp() {
        world = mock(World.class);
        Block block = mock(Block.class);
        when(world.getBlockAt(10, 64, -3)).thenReturn(block);
        when(block.getTypeId()).thenReturn(5342);
        when(block.getData()).thenReturn((byte) 3);
        natives = mock(NativeBlocks.class);
        registry = mock(ServerBlockRegistry.class);
        extendedWorld = new ExtendedWorld(world, natives, registry);
    }

    @Test
    public void readsBlocksAboveTheWorldEditLimit() {
        BaseBlock block = extendedWorld.getBlock(POSITION);
        BaseBlock lazy = extendedWorld.getLazyBlock(POSITION);

        assertEquals(5342, block.getId());
        assertEquals(5342, block.getType());
        assertEquals(3, block.getData());
        assertFalse(block.isAir());
        assertEquals(block, lazy);
        assertEquals(block.hashCode(), lazy.hashCode());
    }

    @Test
    public void writesExtendedBlocksThroughTheNativeWorld() throws Exception {
        when(natives.setBlock(world, 1, 2, 3, 5342, 3)).thenReturn(true);
        BaseBlock block = extendedWorld.getBlock(POSITION);

        assertTrue(extendedWorld.setBlock(new Vector(1, 2, 3), block, true));
        verify(natives).setBlock(world, 1, 2, 3, 5342, 3);
    }

    @Test
    public void validatesExtendedIdsAgainstTheServerRegistry() {
        when(registry.isBlock(5342)).thenReturn(true);

        assertTrue(extendedWorld.isValidBlockType(5342));
        assertFalse(extendedWorld.isValidBlockType(6000));
    }

    @Test
    public void wrapsPlainBukkitWorldsOnce() {
        Extent wrapped = ExtendedWorld.wrap(new BukkitWorld(world), natives, registry);

        assertTrue(wrapped instanceof ExtendedWorld);
        assertSame(wrapped, ExtendedWorld.wrap(wrapped, natives, registry));
    }

    @Test
    public void blockMasksSeeExtendedBlocksAsTheStandIn() {
        Extent extent = new ExtendedMaskParser.StandInExtent(extendedWorld, 4000);
        Mask stone = new BlockMask(extent, new BaseBlock(1));
        Mask fuzzyStone = new FuzzyBlockMask(extent, Collections.singleton(new BaseBlock(1, -1)));

        assertFalse(stone.test(POSITION));
        assertFalse(fuzzyStone.test(POSITION));
        assertTrue(Masks.negate(stone).test(POSITION));
        assertTrue(new ExistingBlockMask(extent).test(POSITION));
        assertTrue(new BlockMask(extent, new BaseBlock(4000, 3)).test(POSITION));
    }
}
