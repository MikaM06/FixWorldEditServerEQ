package fr.earthquest.fixworldedit;

import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.regions.Region;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class ExtendedRegionListener implements Listener {
    private static final String SET = "set";
    private static final String WALLS = "walls";

    private final Plugin plugin;
    private final WorldEditPlugin worldEdit;
    private final ServerBlockRegistry registry;
    private final EditSessionSource editSessions;
    private final BlockWriter writer;
    private final ExtendedLog log;
    private final Map<UUID, Job> jobs = new HashMap<UUID, Job>();

    public ExtendedRegionListener(Plugin plugin, WorldEditPlugin worldEdit, BuildGuard guard,
                                  ServerBlockRegistry registry, EditSessionSource editSessions) {
        this.plugin = plugin;
        this.worldEdit = worldEdit;
        this.registry = registry;
        this.editSessions = editSessions;
        this.log = new ExtendedLog(plugin);
        this.writer = new BlockWriter(plugin, guard, new NativeBlocks(), log);
    }

    public ExtendedRegionListener(Plugin plugin, final WorldEditPlugin worldEdit, BuildGuard guard,
                                  ServerBlockRegistry registry) {
        this(plugin, worldEdit, guard, registry, new EditSessionSource() {
            @Override
            public EditSession create(com.sk89q.worldedit.world.World world, int blockLimit) {
                return worldEdit.getWorldEdit().getEditSessionFactory().getEditSession(world, blockLimit);
            }
        });
    }

    public ExtendedLog log() {
        return log;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        CommandLine command = CommandLine.of(event.getMessage());
        Job running = jobs.get(player.getUniqueId());

        if (command.is("cancel")) {
            if (running != null) {
                running.cancel();
                send(player, ChatColor.YELLOW, "Operation interrompue ; les blocs deja poses restent en place.");
                event.setCancelled(true);
            }
            return;
        }
        if (running != null && (command.is("undo") || command.is("redo"))) {
            send(player, ChatColor.RED, "Une operation etendue est en cours. Attendez la fin, ou //cancel.");
            event.setCancelled(true);
            return;
        }
        if (!command.is(SET) && !command.is(WALLS)) {
            return;
        }
        if (!command.hasArguments() || !command.mentionsExtendedId()) {
            return;
        }

        boolean walls = command.is(WALLS);
        String permission = walls ? "worldedit.region.walls" : "worldedit.region.set";
        if (!player.hasPermission(permission)) {
            send(player, ChatColor.RED, "Permission manquante : " + permission);
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        int[] block = command.parseIdAndData();
        if (block == null) {
            send(player, ChatColor.RED, "Un identifiant etendu s'emploie seul, sous la forme //"
                    + (walls ? WALLS : SET) + " <id>[:data] ; les melanges et pourcentages ne sont pas repris.");
            return;
        }
        int id = block[0];
        byte data = (byte) block[1];
        log.trace("Input", id, data);

        if (isDisallowed(player, id)) {
            send(player, ChatColor.RED, "Ce bloc figure parmi les blocs interdits par WorldEdit.");
            return;
        }
        LocalSession session = worldEdit.getSession(player);
        if (session.getMask() != null) {
            send(player, ChatColor.RED, "Un masque global est actif ; retirez-le avec //gmask avant d'utiliser un ID etendu.");
            return;
        }
        if (!registry.isBlock(id)) {
            send(player, ChatColor.RED, "L'ID " + id + " n'est pas un bloc de ce serveur.");
            return;
        }
        if (running != null) {
            send(player, ChatColor.RED, "Une operation etendue est deja en cours.");
            return;
        }

        World world = player.getWorld();
        com.sk89q.worldedit.world.World weWorld = new BukkitWorld(world);
        Region region;
        try {
            region = session.getSelection(weWorld);
        } catch (IncompleteRegionException incomplete) {
            send(player, ChatColor.RED, "Faites d'abord une selection complete.");
            return;
        }

        Iterator<BlockVector> positions = walls ? wallPositions(region) : region.iterator();
        int limit = session.getBlockChangeLimit();
        EditSession edit = editSessions.create(weWorld, limit);

        Job job = new Job(player, session, world, weWorld, positions, id, data, permission, limit, edit);
        jobs.put(player.getUniqueId(), job);
        job.task = plugin.getServer().getScheduler().runTaskTimer(plugin, job, 1L, 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Job job = jobs.get(event.getPlayer().getUniqueId());
        if (job != null) {
            job.cancel();
        }
    }

    public void close() {
        for (Job job : new HashMap<UUID, Job>(jobs).values()) {
            job.cancel();
        }
        jobs.clear();
    }

    private boolean isDisallowed(Player player, int id) {
        return worldEdit.getLocalConfiguration().disallowedBlocks.contains(id)
                && !player.hasPermission("worldedit.anyblock");
    }

    private static void send(Player player, ChatColor color, String message) {
        player.sendMessage(color + "[FixWorldEdit] " + message);
    }

    private static Iterator<BlockVector> wallPositions(Region region) {
        final Vector min = region.getMinimumPoint();
        final Vector max = region.getMaximumPoint();
        final int minX = min.getBlockX();
        final int maxX = max.getBlockX();
        final int minY = min.getBlockY();
        final int maxY = max.getBlockY();
        final int minZ = min.getBlockZ();
        final int maxZ = max.getBlockZ();

        return new Iterator<BlockVector>() {
            private int x = minX;
            private int y = minY;
            private int z = minZ - 1;
            private BlockVector next = advance();

            private BlockVector advance() {
                while (true) {
                    z++;
                    if (z > maxZ) {
                        z = minZ;
                        x++;
                    }
                    if (x > maxX) {
                        x = minX;
                        y++;
                    }
                    if (y > maxY) {
                        return null;
                    }
                    if (x == minX || x == maxX || z == minZ || z == maxZ) {
                        return new BlockVector(x, y, z);
                    }
                }
            }

            @Override
            public boolean hasNext() {
                return next != null;
            }

            @Override
            public BlockVector next() {
                if (next == null) {
                    throw new NoSuchElementException();
                }
                BlockVector current = next;
                next = advance();
                return current;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    private final class Job implements Runnable, TaskRegistry.Task {
        private final Player player;
        private final LocalSession session;
        private final World world;
        private final Iterator<BlockVector> positions;
        private final int id;
        private final byte data;
        private final String permission;
        private final int limit;
        private final EditSession edit;
        private final int blocksPerTick;

        private BukkitTask task;
        private int changed;
        private boolean done;

        Job(Player player, LocalSession session, World world, com.sk89q.worldedit.world.World weWorld,
            Iterator<BlockVector> positions, int id, byte data, String permission, int limit, EditSession edit) {
            this.player = player;
            this.session = session;
            this.world = world;
            this.positions = positions;
            this.id = id;
            this.data = data;
            this.permission = permission;
            this.limit = limit;
            this.edit = edit;
            this.blocksPerTick = Math.max(1, plugin.getConfig().getInt("extended-worldedit.blocks-per-tick", 1000));
        }

        @Override
        public void run() {
            if (done) {
                return;
            }
            if (!player.isOnline() || !player.hasPermission(permission)) {
                finish();
                return;
            }
            for (int budget = 0; budget < blocksPerTick; budget++) {
                if (limit >= 0 && changed >= limit) {
                    finish();
                    return;
                }
                if (!positions.hasNext()) {
                    finish();
                    return;
                }
                place(positions.next());
            }
            if (!positions.hasNext()) {
                finish();
            }
        }

        private void place(BlockVector position) {
            int x = position.getBlockX();
            int y = position.getBlockY();
            int z = position.getBlockZ();
            Block block = world.getBlockAt(x, y, z);
            if (!writer.canBuild(player, block)) {
                return;
            }
            int previousId = writer.typeId(block);
            byte previousData = block.getData();
            if (previousId == id && previousData == data) {
                return;
            }
            Object previousNbt = NativeTiles.captureNbt(world, x, y, z);
            if (!writer.place(block, id, data)) {
                return;
            }
            edit.getChangeSet().add(new BukkitBlockChange(writer, player, world, x, y, z,
                    previousId, previousData, previousNbt, id, data));
            changed++;
        }

        @Override
        public void cancel() {
            finish();
        }

        private void finish() {
            if (done) {
                return;
            }
            done = true;
            if (changed > 0) {
                session.remember(edit);
            }
            if (task != null) {
                task.cancel();
            }
            jobs.remove(player.getUniqueId());
            log.debug("Operation terminee : " + changed + " bloc(s) modifie(s) en ID " + id);
        }
    }
}
