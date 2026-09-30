package fr.earthquest.fixworldedit;

import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.bukkit.BukkitConfiguration;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.operation.ChangeSetExecutor;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.history.UndoContext;
import com.sk89q.worldedit.history.changeset.BlockOptimizedHistory;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ExtendedRegionListenerTest {
    private Plugin plugin;
    private WorldEditPlugin worldEdit;
    private BuildGuard guard;
    private ServerBlockRegistry registry;
    private Server server;
    private BukkitScheduler scheduler;
    private BukkitTask task;
    private World world;
    private Player player;
    private LocalSession session;
    private EditSession history;
    private BlockOptimizedHistory changes;
    private ExtendedRegionListener listener;
    private int[][] values;
    private Block[] blocks;

    @Before
    public void setup() throws Exception {
        plugin = mock(Plugin.class);
        worldEdit = mock(WorldEditPlugin.class);
        guard = mock(BuildGuard.class);
        registry = mock(ServerBlockRegistry.class);
        server = mock(Server.class);
        scheduler = mock(BukkitScheduler.class);
        task = mock(BukkitTask.class);
        world = mock(World.class);
        player = mock(Player.class);
        session = mock(LocalSession.class);
        history = mock(EditSession.class);
        changes = new BlockOptimizedHistory();
        YamlConfiguration config = new YamlConfiguration();
        config.set("extended-worldedit.blocks-per-tick", 1);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.isPrimaryThread()).thenReturn(true);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L))).thenReturn(task);
        when(world.getName()).thenReturn("world");
        when(world.getMaxHeight()).thenReturn(256);
        when(player.getWorld()).thenReturn(world);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("builder");
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("worldedit.region.set")).thenReturn(true);
        when(guard.canBuild(eq(player), any(Block.class))).thenReturn(true);
        when(registry.isBlock(5016)).thenReturn(true);
        when(registry.isBlock(5490)).thenReturn(true);
        when(worldEdit.getSession(player)).thenReturn(session);
        BukkitConfiguration weConfig = mock(BukkitConfiguration.class);
        weConfig.disallowedBlocks = Collections.emptySet();
        when(worldEdit.getLocalConfiguration()).thenReturn(weConfig);
        when(history.getChangeSet()).thenReturn(changes);
        when(history.size()).thenAnswer(invocation -> changes.size());
        when(session.getBlockChangeLimit()).thenReturn(-1);
        Region region = new CuboidRegion(new Vector(0, 64, 0), new Vector(1, 64, 0));
        when(session.getSelection(any(com.sk89q.worldedit.world.World.class))).thenReturn(region);
        values = new int[][] {{1, 0}, {5, 2}};
        blocks = new Block[] {block(0), block(1)};
        when(world.getBlockAt(0, 64, 0)).thenReturn(blocks[0]);
        when(world.getBlockAt(1, 64, 0)).thenReturn(blocks[1]);
        listener = new ExtendedRegionListener(plugin, worldEdit, guard, registry, (w, limit) -> history);
    }

    private Block block(int x) {
        return block(x, 64, 0, values[x]);
    }

    private Block block(int x, int y, int z, int[] value) {
        Block block = mock(Block.class);
        when(block.getTypeId()).thenAnswer(invocation -> value[0]);
        when(block.getData()).thenAnswer(invocation -> (byte) value[1]);
        when(block.getWorld()).thenReturn(world);
        when(block.setTypeIdAndData(anyInt(), anyByte(), eq(false))).thenAnswer(invocation -> {
            value[0] = invocation.getArgument(0);
            value[1] = (Byte) invocation.getArgument(1);
            return true;
        });
        when(block.getState()).thenAnswer(invocation -> {
            int oldId = value[0];
            int oldData = value[1];
            BlockState state = mock(BlockState.class);
            when(state.getTypeId()).thenReturn(oldId);
            when(state.getRawData()).thenReturn((byte) oldData);
            when(state.getX()).thenReturn(x);
            when(state.getY()).thenReturn(y);
            when(state.getZ()).thenReturn(z);
            when(state.getBlock()).thenReturn(block);
            when(state.update(true, false)).thenAnswer(update -> {
                value[0] = oldId;
                value[1] = oldData;
                return true;
            });
            return state;
        });
        return block;
    }

    private PlayerCommandPreprocessEvent command(String text) {
        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, text, Collections.emptySet());
        listener.onCommand(event);
        return event;
    }

    private Runnable start(String text) {
        assertTrue(command(text).isCancelled());
        ArgumentCaptor<Runnable> runnable = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskTimer(eq(plugin), runnable.capture(), eq(1L), eq(1L));
        return runnable.getValue();
    }

    @Test
    public void cancelStopsTheRunningOperationAndKeepsWhatIsAlreadyPlaced() throws Exception {
        Runnable run = start("//set 5016:3");
        run.run();
        assertArrayEquals(new int[] {5016, 3}, values[0]);

        assertTrue(command("//cancel").isCancelled());
        verify(task).cancel();
        verify(session).remember(history);
        assertArrayEquals(new int[] {5016, 3}, values[0]);
        assertArrayEquals(new int[] {5, 2}, values[1]);

        assertFalse(command("//copy").isCancelled());
    }

    @Test
    public void setPreservesFullIdAndMetadataAcrossTicksAndWorldEditUndoRedo() throws Exception {
        Runnable run = start("//set 5016:3");
        run.run();
        assertArrayEquals(new int[] {5016, 3}, values[0]);
        assertArrayEquals(new int[] {5, 2}, values[1]);
        verify(session, never()).remember(any(EditSession.class));
        run.run();
        assertArrayEquals(new int[] {5016, 3}, values[1]);
        assertEquals(2, changes.size());
        verify(session).remember(history);
        verify(task).cancel();

        Operations.completeBlindly(ChangeSetExecutor.createUndo(changes, new UndoContext()));
        assertArrayEquals(new int[] {1, 0}, values[0]);
        assertArrayEquals(new int[] {5, 2}, values[1]);
        Operations.completeBlindly(ChangeSetExecutor.createRedo(changes, new UndoContext()));
        assertArrayEquals(new int[] {5016, 3}, values[0]);
        assertArrayEquals(new int[] {5016, 3}, values[1]);
    }

    @Test
    public void worldGuardDenialSkipsProtectedBlocks() {
        when(guard.canBuild(player, blocks[0])).thenReturn(false);
        Runnable run = start("//set 5016");
        run.run();
        run.run();
        assertArrayEquals(new int[] {1, 0}, values[0]);
        assertArrayEquals(new int[] {5016, 0}, values[1]);
        assertEquals(1, changes.size());
    }

    @Test
    public void changeLimitKeepsPartialHistory() {
        when(session.getBlockChangeLimit()).thenReturn(1);
        Runnable run = start("//set 5016");
        run.run();
        run.run();
        assertEquals(5016, values[0][0]);
        assertEquals(5, values[1][0]);
        verify(session).remember(history);
        assertEquals(1, changes.size());
    }

    @Test
    public void unchangedBlocksDoNotConsumeLimitOrHistory() {
        values[0][0] = 5016;
        when(session.getBlockChangeLimit()).thenReturn(1);
        Runnable run = start("//set 5016");
        run.run();
        run.run();
        verify(blocks[0], never()).setTypeIdAndData(anyInt(), anyByte(), anyBoolean());
        assertEquals(5016, values[1][0]);
        assertEquals(1, changes.size());
    }

    @Test
    public void zeroLimitMakesNoChanges() {
        when(session.getBlockChangeLimit()).thenReturn(0);
        start("//set 5016").run();
        assertEquals(1, values[0][0]);
        assertEquals(0, changes.size());
        verify(session, never()).remember(any(EditSession.class));
    }

    @Test
    public void nonCuboidSelectionDoesNotFillBoundingBox() throws Exception {
        Region region = mock(Region.class);
        when(region.clone()).thenReturn(region);
        when(region.iterator()).thenReturn(Collections.singletonList(new com.sk89q.worldedit.BlockVector(1, 64, 0)).iterator());
        when(session.getSelection(any(com.sk89q.worldedit.world.World.class))).thenReturn(region);
        start("//set 5016").run();
        assertEquals(1, values[0][0]);
        assertEquals(5016, values[1][0]);
    }

    @Test
    public void closeAndDisconnectKeepCompletedChangesUndoable() {
        Runnable run = start("//set 5016");
        run.run();
        listener.onQuit(new PlayerQuitEvent(player, "bye"));
        listener.close();
        run.run();
        assertEquals(5016, values[0][0]);
        assertEquals(5, values[1][0]);
        verify(session, times(1)).remember(history);
        verify(task, times(1)).cancel();
    }

    @Test
    public void blocksUndoUntilJobFinishes() {
        Runnable run = start("//set 5016");
        assertTrue(command("//undo").isCancelled());
        run.run();
        run.run();
        assertFalse(command("//undo").isCancelled());
    }

    @Test
    public void nativeCommandsRemainWithWorldEdit() {
        assertFalse(command("//set 1:3").isCancelled());
        assertFalse(command("//set stone").isCancelled());
        assertFalse(command("//set 50%stone,50%dirt").isCancelled());
        assertFalse(command("//replace 1 5016").isCancelled());
        assertFalse(command("//setter 5016").isCancelled());
        assertFalse(command("//walls stone").isCancelled());
        assertFalse(command("//walls 1:3").isCancelled());
        assertFalse(command("//wallspaper 5490").isCancelled());
        verifyNoInteractions(scheduler);
    }

    @Test
    public void acceptsNamespacedCommand() {
        start("/worldedit:/set 5016").run();
        assertEquals(5016, values[0][0]);
    }

    @Test
    public void permissionDeniedBeforeScheduling() {
        when(player.hasPermission("worldedit.region.set")).thenReturn(false);
        assertTrue(command("//set 5016").isCancelled());
        verifyNoInteractions(scheduler, registry);
    }

    @Test
    public void rejectsUnknownIdAndItemId() {
        when(registry.isBlock(5016)).thenReturn(false);
        assertTrue(command("//set 5016").isCancelled());
        verifyNoInteractions(scheduler);
    }

    @Test
    public void doesNotBypassDisallowedBlocks() {
        worldEdit.getLocalConfiguration().disallowedBlocks = Collections.singleton(5016);
        assertTrue(command("//set 5016").isCancelled());
        verifyNoInteractions(scheduler);
    }

    @Test
    public void doesNotSilentlyBypassGlobalMask() {
        when(session.getMask()).thenReturn(mock(Mask.class));
        assertTrue(command("//set 5016").isCancelled());
        verifyNoInteractions(scheduler);
    }

    @Test
    public void rejectsUnsupportedPatternsInsteadOfCrashingWorldEdit() {
        assertTrue(command("//set 50%5016,50%1").isCancelled());
        assertTrue(command("//set 5016:16").isCancelled());
        assertTrue(command("//set 5016 extra").isCancelled());
        assertTrue(command("//set 99999999999999999999999999999999999").isCancelled());
        verifyNoInteractions(scheduler);
    }

    @Test
    public void serverTruncationRestoresFailedBlockAndPreservesEarlierHistory() {
        doAnswer(invocation -> {
            values[1][0] = 5016 & 4095;
            return true;
        }).when(blocks[1]).setTypeIdAndData(eq(5016), eq((byte) 0), eq(false));
        Runnable run = start("//set 5016");
        run.run();
        run.run();
        assertEquals(5016, values[0][0]);
        assertArrayEquals(new int[] {5, 2}, values[1]);
        assertEquals(1, changes.size());
        verify(session).remember(history);
    }

    @Test
    public void replayFromAsyncWorldEditIsMarshalledToMainThread() throws Exception {
        Runnable run = start("//set 5016");
        run.run();
        run.run();
        when(server.isPrimaryThread()).thenReturn(false);
        when(scheduler.callSyncMethod(eq(plugin), any(Callable.class))).thenAnswer(invocation -> {
            Callable<?> callable = invocation.getArgument(1);
            return CompletableFuture.completedFuture(callable.call());
        });
        changes.backwardIterator().next().undo(new UndoContext());
        assertEquals(5, values[1][0]);
        verify(scheduler).callSyncMethod(eq(plugin), any(Callable.class));
    }

    @Test
    public void undoRechecksWorldGuardProtection() throws Exception {
        Runnable run = start("//set 5016");
        run.run();
        run.run();
        when(guard.canBuild(player, blocks[1])).thenReturn(false);
        changes.backwardIterator().next().undo(new UndoContext());
        assertEquals(5016, values[1][0]);
    }

    private Map<BlockVector, int[]> wallSelection(Vector first, Vector second) throws Exception {
        Region region = new CuboidRegion(first, second);
        when(session.getSelection(any(com.sk89q.worldedit.world.World.class))).thenReturn(region);
        when(player.hasPermission("worldedit.region.set")).thenReturn(false);
        when(player.hasPermission("worldedit.region.walls")).thenReturn(true);
        Map<BlockVector, int[]> grid = new HashMap<>();
        for (BlockVector position : region) {
            int[] value = {1, 2};
            grid.put(position, value);
            int x = position.getBlockX();
            int y = position.getBlockY();
            int z = position.getBlockZ();
            Block selectedBlock = block(x, y, z, value);
            when(world.getBlockAt(x, y, z)).thenReturn(selectedBlock);
        }
        return grid;
    }

    private void finishJob(Runnable run) {
        for (int tick = 0; tick < 100; tick++) {
            run.run();
        }
        verify(task, times(1)).cancel();
    }

    private void assertWalls(Map<BlockVector, int[]> grid) {
        for (Map.Entry<BlockVector, int[]> entry : grid.entrySet()) {
            BlockVector position = entry.getKey();
            boolean wall = Math.abs(position.getBlockX()) == 1 || Math.abs(position.getBlockZ()) == 1;
            assertArrayEquals(position.toString(), wall ? new int[] {5490, 3} : new int[] {1, 2}, entry.getValue());
        }
    }

    @Test
    public void wallsLeaveInteriorFloorAndCeilingIntactAndSupportUndoRedo() throws Exception {
        Map<BlockVector, int[]> grid = wallSelection(new Vector(1, 66, 1), new Vector(-1, 64, -1));
        Runnable run = start("//walls 5490:3");
        run.run();
        assertEquals(1, changes.size());
        verify(task, never()).cancel();
        finishJob(run);
        assertWalls(grid);
        assertEquals(24, changes.size());
        verify(session, times(1)).remember(history);
        for (int y = 64; y <= 66; y++) {
            verify(world, never()).getBlockAt(0, y, 0);
        }
        Operations.completeBlindly(ChangeSetExecutor.createUndo(changes, new UndoContext()));
        for (int[] value : grid.values()) {
            assertArrayEquals(new int[] {1, 2}, value);
        }
        Operations.completeBlindly(ChangeSetExecutor.createRedo(changes, new UndoContext()));
        assertWalls(grid);
    }

    @Test
    public void wallsUseTheirOwnPermission() {
        assertTrue(command("//walls 5490").isCancelled());
        verifyNoInteractions(scheduler, registry);
        verify(player).sendMessage(contains("worldedit.region.walls"));
    }

    @Test
    public void wallsRecheckPermissionBetweenBatches() throws Exception {
        wallSelection(new Vector(-1, 64, -1), new Vector(1, 66, 1));
        Runnable run = start("//walls 5490");
        run.run();
        when(player.hasPermission("worldedit.region.walls")).thenReturn(false);
        run.run();
        assertEquals(1, changes.size());
        verify(session).remember(history);
        verify(task).cancel();
    }

    @Test
    public void namespacedWallsRespectWorldGuardAndChangeLimit() throws Exception {
        Map<BlockVector, int[]> grid = wallSelection(new Vector(-1, 64, -1), new Vector(1, 66, 1));
        Block protectedBlock = world.getBlockAt(-1, 64, -1);
        when(guard.canBuild(player, protectedBlock)).thenReturn(false);
        when(session.getBlockChangeLimit()).thenReturn(2);
        finishJob(start("/worldedit:/walls 5490"));
        assertArrayEquals(new int[] {1, 2}, grid.get(new BlockVector(-1, 64, -1)));
        assertEquals(2, changes.size());
        verify(protectedBlock, never()).setTypeIdAndData(anyInt(), anyByte(), anyBoolean());
        verify(session).remember(history);
    }

    @Test
    public void wallsHandleOneBlockWideSelectionWithoutDuplicateChanges() throws Exception {
        Map<BlockVector, int[]> grid = wallSelection(new Vector(0, 64, -1), new Vector(0, 66, 1));
        finishJob(start("/worldedit:walls 5490:3"));
        assertEquals(9, changes.size());
        for (int[] value : grid.values()) {
            assertArrayEquals(new int[] {5490, 3}, value);
        }
    }

    @Test
    public void wallsHandleSingleBlockSelection() throws Exception {
        Map<BlockVector, int[]> grid = wallSelection(new Vector(0, 64, 0), new Vector(0, 64, 0));
        finishJob(start("//WALLS 5490"));
        assertEquals(1, changes.size());
        assertArrayEquals(new int[] {5490, 0}, grid.get(new BlockVector(0, 64, 0)));
    }

    @Test
    public void wallsUseBoundingBoxForNonCuboidSelectionLikeWorldEditSix() throws Exception {
        Vector min = new Vector(-1, 64, -1);
        Vector max = new Vector(1, 66, 1);
        Map<BlockVector, int[]> grid = wallSelection(min, max);
        Region region = mock(Region.class);
        when(region.clone()).thenReturn(region);
        when(region.getMinimumPoint()).thenReturn(min);
        when(region.getMaximumPoint()).thenReturn(max);
        when(session.getSelection(any(com.sk89q.worldedit.world.World.class))).thenReturn(region);
        finishJob(start("//walls 5490:3"));
        assertWalls(grid);
        assertEquals(24, changes.size());
    }

    @Test
    public void wallsRejectInvalidMetadataAndUnsupportedPatternsBeforeScheduling() {
        when(player.hasPermission("worldedit.region.walls")).thenReturn(true);
        assertTrue(command("//walls 5490:16").isCancelled());
        assertTrue(command("//walls 50%5490,50%1").isCancelled());
        verifyNoInteractions(scheduler);
    }
}
