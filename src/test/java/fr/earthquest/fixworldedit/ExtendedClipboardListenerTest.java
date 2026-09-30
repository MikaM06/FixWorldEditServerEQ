package fr.earthquest.fixworldedit;

import com.sk89q.jnbt.CompoundTag;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitConfiguration;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.function.operation.ChangeSetExecutor;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.history.UndoContext;
import com.sk89q.worldedit.history.changeset.BlockOptimizedHistory;
import com.sk89q.worldedit.regions.CuboidRegion;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentMatchers;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ExtendedClipboardListenerTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Plugin plugin;
    private WorldEditPlugin worldEdit;
    private WorldEdit worldEditCore;
    private BuildGuard guard;
    private ServerBlockRegistry registry;
    private NativeTiles tiles;
    private BukkitScheduler scheduler;
    private World world;
    private Player player;
    private LocalSession session;
    private EditSession history;
    private BlockOptimizedHistory changes;
    private ExtendedClipboardListener listener;
    private final List<Runnable> ticks = new ArrayList<>();
    private final Map<String, int[]> blocks = new HashMap<>();
    private File schematics;
    private int refusedId = -1;

    @Before
    public void setup() throws Exception {
        plugin = mock(Plugin.class);
        worldEdit = mock(WorldEditPlugin.class);
        worldEditCore = mock(WorldEdit.class);
        guard = mock(BuildGuard.class);
        registry = mock(ServerBlockRegistry.class);
        tiles = mock(NativeTiles.class);
        scheduler = mock(BukkitScheduler.class);
        world = mock(World.class);
        player = mock(Player.class);
        session = mock(LocalSession.class);
        history = mock(EditSession.class);
        changes = new BlockOptimizedHistory();
        schematics = folder.newFolder("schematics");

        Server server = mock(Server.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("extended-worldedit.blocks-per-tick", 1000);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.getDataFolder()).thenReturn(folder.newFolder("data"));
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.isPrimaryThread()).thenReturn(true);
        when(server.getWorld("world")).thenReturn(world);
        BukkitTask task = mock(BukkitTask.class);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L))).thenAnswer(invocation -> {
            ticks.add(invocation.getArgument(1));
            return task;
        });
        when(scheduler.runTaskAsynchronously(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return task;
        });
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return task;
        });

        when(world.getMaxHeight()).thenReturn(256);
        when(world.getName()).thenReturn("world");
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
                block(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
        when(world.getBlockTypeIdAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
                idAt(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
        when(player.getWorld()).thenReturn(world);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("builder");
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission(anyString())).thenReturn(true);
        when(guard.canBuild(eq(player), any(Block.class))).thenReturn(true);

        when(worldEdit.getSession(player)).thenReturn(session);
        when(worldEdit.getWorldEdit()).thenReturn(worldEditCore);
        BukkitConfiguration weConfig = mock(BukkitConfiguration.class);
        weConfig.saveDir = "schematics";
        when(worldEdit.getLocalConfiguration()).thenReturn(weConfig);
        when(worldEditCore.getWorkingDirectoryFile("schematics")).thenReturn(schematics);
        when(worldEditCore.getSafeOpenFile(any(), eq(schematics), anyString(), anyString(), any(String[].class)))
                .thenAnswer(invocation -> new File(schematics, withExtension(invocation.getArgument(2))));
        when(worldEditCore.getSafeSaveFile(any(), eq(schematics), anyString(), anyString(), any(String[].class)))
                .thenAnswer(invocation -> new File(schematics, withExtension(invocation.getArgument(2))));
        when(session.getBlockChangeLimit()).thenReturn(-1);
        when(session.getPlacementPosition(any())).thenReturn(new Vector(10, 64, 10));
        when(history.getChangeSet()).thenReturn(changes);
        when(registry.isBlock(anyInt())).thenReturn(true);
        when(tiles.isAvailable()).thenReturn(false);

        listener = new ExtendedClipboardListener(plugin, worldEdit, guard, registry, tiles, new NativeBlocks(), new TaskRegistry(),
                (w, limit) -> history);
    }

    private static String withExtension(String filename) {
        return filename.lastIndexOf('.') == -1 ? filename + ".schematic" : filename;
    }

    private Block block(int x, int y, int z) {
        String key = x + ":" + y + ":" + z;
        int[] value = blocks.computeIfAbsent(key, ignored -> new int[] {0, 0});
        Block block = mock(Block.class);
        when(block.getTypeId()).thenAnswer(invocation -> value[0]);
        when(block.getData()).thenAnswer(invocation -> (byte) value[1]);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(z);
        when(block.setTypeIdAndData(anyInt(), anyByte(), eq(false))).thenAnswer(invocation -> {
            int id = invocation.getArgument(0);
            if (id == refusedId) {
                return true;
            }
            value[0] = id;
            value[1] = (Byte) invocation.getArgument(1);
            return true;
        });
        when(block.getState()).thenAnswer(invocation -> {
            int oldId = value[0];
            int oldData = value[1];
            BlockState state = mock(BlockState.class);
            when(state.getTypeId()).thenReturn(oldId);
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

    private void setBlock(int x, int y, int z, int id, int data) {
        blocks.put(x + ":" + y + ":" + z, new int[] {id, data});
    }

    private int idAt(int x, int y, int z) {
        int[] value = blocks.get(x + ":" + y + ":" + z);
        return value == null ? 0 : value[0];
    }

    private PlayerCommandPreprocessEvent run(String command) {
        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, command, java.util.Collections.emptySet());
        listener.onCommand(event);
        for (Runnable tick : new ArrayList<>(ticks)) {
            for (int i = 0; i < 64 && ticks.contains(tick); i++) {
                tick.run();
            }
        }
        ticks.clear();
        return event;
    }

    private void select(int x1, int y1, int z1, int x2, int y2, int z2) throws Exception {
        when(session.getSelection(any(com.sk89q.worldedit.world.World.class)))
                .thenReturn(new CuboidRegion(new Vector(x1, y1, z1), new Vector(x2, y2, z2)));
    }

    private File writeSchematic(String name, int[] ids, int width) throws Exception {
        short[] values = new short[ids.length];
        for (int i = 0; i < ids.length; i++) {
            values[i] = (short) ids[i];
        }
        File file = new File(schematics, name + ".schematic");
        ExtendedSchematic.write(file, new ExtendedClipboard(width, 1, ids.length / width, values,
                new byte[ids.length], 0, 0, 0, new ArrayList<CompoundTag>(), new HashMap<String, Integer>()));
        return file;
    }

    @Test
    public void leavesWorldEditAloneWithoutAnExtendedClipboard() {
        assertFalse(run("//paste").isCancelled());
        assertFalse(run("//schem save maison").isCancelled());
        assertFalse(run("//flip").isCancelled());
    }

    @Test
    public void leavesAlphaSchematicsToWorldEdit() throws Exception {
        writeSchematic("vanilla", new int[] {1, 2}, 2);
        assertFalse(run("//schem load vanilla").isCancelled());
    }

    @Test
    public void acceptsAFileWithAnOrdinaryBlockThisServerLacks() throws Exception {
        writeSchematic("etranger", new int[] {5016, 250}, 2);
        when(registry.isBlock(250)).thenReturn(false);
        assertTrue(run("//schem load etranger").isCancelled());
        assertTrue(run("//paste").isCancelled());
        assertEquals(5016, idAt(10, 64, 10));
    }

    @Test
    public void loadsAnExtendedSchematicAndPastesIt() throws Exception {
        writeSchematic("quest", new int[] {5016, 0, 5490, 1}, 4);
        assertTrue(run("//schem load quest").isCancelled());

        assertTrue(run("//paste").isCancelled());
        assertEquals(5016, idAt(10, 64, 10));
        assertEquals(0, idAt(11, 64, 10));
        assertEquals(5490, idAt(12, 64, 10));
        assertEquals(1, idAt(13, 64, 10));
    }

    @Test
    public void findsTheFileNameAmongFormatAndFlags() throws Exception {
        writeSchematic("quest", new int[] {5016, 0}, 2);
        assertTrue(run("//schem load mcedit quest").isCancelled());
        assertTrue(run("//schem load quest -a").isCancelled());
        assertTrue(run("//schem l quest").isCancelled());
        assertTrue(run("//paste").isCancelled());
        assertEquals(5016, idAt(10, 64, 10));
    }

    @Test
    public void loadsAFileNamedWithItsExtension() throws Exception {
        writeSchematic("spawn_earthquest_1.7.10", new int[] {5016, 0}, 2);
        assertTrue(run("//schem load spawn_earthquest_1.7.10.schematic").isCancelled());
        assertTrue(run("//paste").isCancelled());
        assertEquals(5016, idAt(10, 64, 10));
    }

    @Test
    public void ignoresBlocksAboveTheWorldHeight() throws Exception {
        File file = new File(schematics, "tall.schematic");
        ExtendedSchematic.write(file, new ExtendedClipboard(1, 2, 1,
                new short[] {(short) 5016, (short) 5016}, new byte[2], 0, 0, 0,
                new ArrayList<CompoundTag>(), new HashMap<String, Integer>()));
        run("//schem load tall");
        when(session.getPlacementPosition(any())).thenReturn(new Vector(10, 255, 10));

        run("//paste");

        assertEquals(5016, idAt(10, 255, 10));
        assertEquals(0, idAt(10, 256, 10));
    }

    @Test
    public void refusesAnExtendedSchematicWhoseBlocksAreMissing() throws Exception {
        writeSchematic("quest", new int[] {5016, 1}, 2);
        when(registry.isBlock(5016)).thenReturn(false);
        assertTrue(run("//schem load quest").isCancelled());
        assertTrue(run("//paste").isCancelled() == false);
    }

    @Test
    public void leavesVanillaSelectionsToWorldEditOnCopy() throws Exception {
        select(0, 64, 0, 1, 64, 0);
        setBlock(0, 64, 0, 1, 0);
        setBlock(1, 64, 0, 35, 2);
        assertFalse(run("//copy").isCancelled());
        assertFalse(run("//paste").isCancelled());
    }

    @Test
    public void copiesExtendedBlocksAndPastesThemElsewhere() throws Exception {
        select(0, 64, 0, 1, 64, 0);
        setBlock(0, 64, 0, 5016, 3);
        setBlock(1, 64, 0, 1, 0);
        when(session.getPlacementPosition(any())).thenReturn(new Vector(0, 64, 0));
        assertTrue(run("//copy").isCancelled());

        when(session.getPlacementPosition(any())).thenReturn(new Vector(20, 70, 5));
        assertTrue(run("//paste").isCancelled());
        assertEquals(5016, idAt(20, 70, 5));
        assertEquals(1, idAt(21, 70, 5));
    }

    @Test
    public void cutRemovesTheBlocksAndRemembersTheHistory() throws Exception {
        select(0, 64, 0, 1, 64, 0);
        setBlock(0, 64, 0, 5016, 0);
        setBlock(1, 64, 0, 5016, 0);
        assertTrue(run("//cut").isCancelled());
        assertEquals(0, idAt(0, 64, 0));
        assertEquals(0, idAt(1, 64, 0));
        verify(session).remember(history);

        Operations.completeBlindly(ChangeSetExecutor.createUndo(changes, new UndoContext()));
        assertEquals(5016, idAt(0, 64, 0));
        assertEquals(5016, idAt(1, 64, 0));
        Operations.completeBlindly(ChangeSetExecutor.createRedo(changes, new UndoContext()));
        assertEquals(0, idAt(0, 64, 0));
        assertEquals(0, idAt(1, 64, 0));
    }

    @Test
    public void savesTheExtendedProfileFromTheClipboard() throws Exception {
        select(0, 64, 0, 1, 64, 0);
        setBlock(0, 64, 0, 5016, 0);
        when(registry.nameForId(5016)).thenReturn("questblock:pierre");
        run("//copy");
        assertTrue(run("//schem save quest").isCancelled());

        File file = new File(schematics, "quest.schematic");
        assertTrue(file.isFile());
        assertEquals(ExtendedSchematic.EXTENDED, ExtendedSchematic.peek(file).materials);
        ExtendedClipboard written = ExtendedSchematic.read(file);
        assertEquals(5016, written.idAt(0));
        assertEquals(Integer.valueOf(5016), written.mapping.get("questblock:pierre"));
    }

    @Test
    public void skipsAirWithTheDashAFlag() throws Exception {
        writeSchematic("quest", new int[] {5016, 0}, 2);
        run("//schem load quest");
        setBlock(11, 64, 10, 35, 0);
        run("//paste -a");
        assertEquals(5016, idAt(10, 64, 10));
        assertEquals(35, idAt(11, 64, 10));
    }

    @Test
    public void refusesUnsupportedPasteFlagsAndTransforms() throws Exception {
        writeSchematic("quest", new int[] {5016, 1}, 2);
        run("//schem load quest");
        assertTrue(run("//paste -s").isCancelled());
        assertEquals(0, idAt(10, 64, 10));
        assertTrue(run("//rotate 90").isCancelled());
    }

    @Test
    public void qtundoRestoresThePasteEvenWithoutWorldEditHistory() throws Exception {
        YamlConfiguration config = (YamlConfiguration) plugin.getConfig();
        config.set("extended-worldedit.max-history-blocks", 0);
        writeSchematic("quest", new int[] {5016, 5490}, 2);
        run("//schem load quest");
        setBlock(10, 64, 10, 1, 0);
        setBlock(11, 64, 10, 3, 0);

        run("//paste");
        assertEquals(5016, idAt(10, 64, 10));
        assertEquals(5490, idAt(11, 64, 10));
        verify(session, never()).remember(any(EditSession.class));

        assertTrue(run("/qtundo").isCancelled());
        assertEquals(1, idAt(10, 64, 10));
        assertEquals(3, idAt(11, 64, 10));

        assertTrue(run("/qtundo").isCancelled());
        assertEquals(1, idAt(10, 64, 10));
    }

    @Test
    public void refusedIdKeepsTheOldBlockAndTheRestIsStillPasted() throws Exception {
        writeSchematic("quest", new int[] {5247, 5016, 5247, 1}, 4);
        run("//schem load quest");
        setBlock(10, 64, 10, 3, 0);
        setBlock(12, 64, 10, 3, 0);
        refusedId = 5247;

        run("//paste");

        assertEquals(3, idAt(10, 64, 10));
        assertEquals(5016, idAt(11, 64, 10));
        assertEquals(3, idAt(12, 64, 10));
        assertEquals(1, idAt(13, 64, 10));
    }

    @Test
    public void respectsWorldGuardOnPaste() throws Exception {
        writeSchematic("quest", new int[] {5016, 5016}, 2);
        run("//schem load quest");
        when(guard.canBuild(eq(player), ArgumentMatchers.<Block>argThat(block -> block.getX() == 11)))
                .thenReturn(false);
        run("//paste");
        assertEquals(5016, idAt(10, 64, 10));
        assertEquals(0, idAt(11, 64, 10));
    }
}
