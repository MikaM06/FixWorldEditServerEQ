package fr.earthquest.fixworldedit;

import com.sk89q.jnbt.CompoundTag;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.regions.Region;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

public class ExtendedClipboardListener implements Listener {
    private static final int DEFAULT_MAX_HISTORY = 2000000;
    private static final int DEFAULT_MAX_CLIPBOARD = 100000000;

    private final Plugin plugin;
    private final WorldEditPlugin worldEdit;
    private final ServerBlockRegistry registry;
    private final NativeTiles tiles;
    private final TaskRegistry tasks;
    private final EditSessionSource editSessions;
    private final BlockWriter writer;
    private final ExtendedLog log;
    private final Map<UUID, ExtendedClipboard> clipboards = new HashMap<UUID, ExtendedClipboard>();

    public ExtendedClipboardListener(Plugin plugin, WorldEditPlugin worldEdit, BuildGuard guard,
                                     ServerBlockRegistry registry, NativeTiles tiles, NativeBlocks natives,
                                     TaskRegistry tasks, EditSessionSource editSessions) {
        this.plugin = plugin;
        this.worldEdit = worldEdit;
        this.registry = registry;
        this.tiles = tiles;
        this.tasks = tasks;
        this.editSessions = editSessions;
        this.log = new ExtendedLog(plugin);
        this.writer = new BlockWriter(plugin, guard, natives, log);
    }

    public ExtendedClipboardListener(Plugin plugin, final WorldEditPlugin worldEdit, BuildGuard guard,
                                     ServerBlockRegistry registry, NativeTiles tiles, NativeBlocks natives,
                                     TaskRegistry tasks) {
        this(plugin, worldEdit, guard, registry, tiles, natives, tasks, new EditSessionSource() {
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

        if (command.is("qtundo")) {
            event.setCancelled(true);
            replayJournal(player);
            return;
        }
        if (tasks.isBusy(player.getUniqueId())) {
            if (command.is("cancel")) {
                tasks.get(player.getUniqueId()).cancel();
                send(player, ChatColor.YELLOW, "Operation interrompue ; les blocs deja poses restent en place.");
                event.setCancelled(true);
            }
            return;
        }

        if (command.is("copy") || command.is("cut")) {
            copy(event, player, command.is("cut"));
            return;
        }
        if (command.is("paste")) {
            paste(event, player, command.arguments());
            return;
        }
        if (command.is("rotate") || command.is("flip")) {
            if (clipboards.containsKey(player.getUniqueId())) {
                send(player, ChatColor.RED, "Le presse-papiers etendu ne sait pas tourner ni retourner ;"
                        + " recopiez la zone dans l'orientation voulue.");
                event.setCancelled(true);
            }
            return;
        }
        if (command.is("schem") || command.is("schematic")) {
            schematic(event, player, command.arguments());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (tasks.isBusy(id)) {
            tasks.get(id).cancel();
        }
        clipboards.remove(id);
    }

    public void close() {
        tasks.cancelAll();
        clipboards.clear();
    }

    private void copy(PlayerCommandPreprocessEvent event, Player player, boolean cut) {
        World world = player.getWorld();
        com.sk89q.worldedit.world.World weWorld = new BukkitWorld(world);
        LocalSession session = worldEdit.getSession(player);
        Region region;
        try {
            region = session.getSelection(weWorld);
        } catch (IncompleteRegionException incomplete) {
            return;
        }
        Vector min = region.getMinimumPoint();
        Vector max = region.getMaximumPoint();
        if (!containsExtendedId(world, min, max)) {
            clipboards.remove(player.getUniqueId());
            return;
        }
        if (!event.getMessage().trim().toLowerCase().endsWith(cut ? "cut" : "copy")) {
            send(player, ChatColor.RED, "Les options de //" + (cut ? "cut" : "copy")
                    + " ne sont pas reprises sur une selection etendue.");
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);

        long volume = (long) (max.getBlockX() - min.getBlockX() + 1)
                * (max.getBlockY() - min.getBlockY() + 1)
                * (max.getBlockZ() - min.getBlockZ() + 1);
        long maxVolume = plugin.getConfig().getLong("extended-worldedit.max-clipboard-blocks", DEFAULT_MAX_CLIPBOARD);
        if (volume > maxVolume) {
            send(player, ChatColor.RED, "Selection trop grande : " + volume + " blocs pour un maximum de " + maxVolume + ".");
            return;
        }

        Vector placement = placement(player, session, min);
        CopyJob job = new CopyJob(player, world, session, min, max, placement, cut);
        tasks.start(player.getUniqueId(), job);
        job.task = plugin.getServer().getScheduler().runTaskTimer(plugin, job, 1L, 1L);
    }

    private boolean containsExtendedId(World world, Vector min, Vector max) {
        for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
            for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    if (writer.typeIdAt(world, x, y, z) > ServerBlockRegistry.VANILLA_MAX_ID) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private Vector placement(Player player, LocalSession session, Vector fallback) {
        try {
            return session.getPlacementPosition(worldEdit.wrapPlayer(player));
        } catch (IncompleteRegionException incomplete) {
            return fallback;
        } catch (RuntimeException unavailable) {
            log.warn("Position de reference indisponible, repli sur " + fallback, unavailable);
            return fallback;
        }
    }

    private void paste(PlayerCommandPreprocessEvent event, Player player, String arguments) {
        ExtendedClipboard clipboard = clipboards.get(player.getUniqueId());
        if (clipboard == null) {
            return;
        }
        event.setCancelled(true);

        boolean skipAir = false;
        boolean atOrigin = false;
        for (String argument : arguments.split("\\s+")) {
            if (argument.isEmpty()) {
                continue;
            }
            if (argument.equals("-a")) {
                skipAir = true;
            } else if (argument.equals("-o")) {
                atOrigin = true;
            } else {
                send(player, ChatColor.RED, "Option " + argument + " non reprise pour un collage etendu ;"
                        + " seules -a et -o le sont.");
                return;
            }
        }

        LocalSession session = worldEdit.getSession(player);
        Vector target;
        if (atOrigin) {
            target = new Vector(clipboard.offsetX, clipboard.offsetY, clipboard.offsetZ);
        } else {
            Vector placement = placement(player, session, new Vector(0, 0, 0));
            target = placement.add(clipboard.offsetX, clipboard.offsetY, clipboard.offsetZ);
        }

        PasteJob job = new PasteJob(player, player.getWorld(), session, clipboard, target, skipAir);
        tasks.start(player.getUniqueId(), job);
        job.task = plugin.getServer().getScheduler().runTaskTimer(plugin, job, 1L, 1L);
    }

    private void schematic(PlayerCommandPreprocessEvent event, Player player, String arguments) {
        String[] parts = arguments.split("\\s+");
        if (parts.length < 2) {
            return;
        }
        String action = parts[0].toLowerCase();

        String name = null;
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty() && !parts[i].startsWith("-")) {
                name = parts[i];
            }
        }
        if (name == null) {
            return;
        }
        if (action.equals("load") || action.equals("l")) {
            load(event, player, name);
        } else if (action.equals("save") || action.equals("s")) {
            save(event, player, name);
        }
    }

    private void load(PlayerCommandPreprocessEvent event, Player player, String name) {
        File file;
        try {
            file = resolve(player, name, true);
        } catch (Exception refused) {
            return;
        }
        if (file == null || !file.isFile()) {
            return;
        }
        ExtendedSchematic.Header header;
        try {
            header = ExtendedSchematic.peek(file);
        } catch (IOException unreadable) {
            return;
        }
        if (!header.isExtended()) {
            return;
        }
        event.setCancelled(true);

        ExtendedClipboard clipboard = readClipboard(player, name);
        if (clipboard == null) {
            return;
        }
        clipboards.put(player.getUniqueId(), clipboard);
        log.trace("Clipboard", clipboard.idAt(0));
        send(player, ChatColor.GREEN, "Schematic etendu charge : " + clipboard.width + "x"
                + clipboard.height + "x" + clipboard.length + " blocs.");

        log.info("Schematic " + file.getName() + " charge : " + clipboard.width + "x"
                + clipboard.height + "x" + clipboard.length + " (" + clipboard.size() + " cases)");
        log.info("  decalage WEOffset : " + clipboard.offsetX + "," + clipboard.offsetY + ","
                + clipboard.offsetZ + ", tile entities : " + clipboard.tiles.size()
                + ", noms de blocs : " + clipboard.mapping.size());
        log.info("  contenu : " + census(clipboard));
    }

    private ExtendedClipboard readClipboard(Player player, String name) {
        File file;
        try {
            file = resolve(player, name, true);
        } catch (Exception refused) {
            return null;
        }
        if (file == null || !file.isFile()) {
            send(player, ChatColor.RED, "Fichier introuvable : " + name);
            return null;
        }
        ExtendedClipboard clipboard;
        try {
            clipboard = ExtendedSchematic.read(file);
        } catch (IOException unreadable) {
            send(player, ChatColor.RED, "Fichier illisible : " + unreadable.getMessage());
            log.warn("Lecture de " + file.getName() + " impossible", unreadable);
            return null;
        }

        clipboard = remap(clipboard);
        for (int index = 0; index < clipboard.size(); index++) {
            int id = clipboard.idAt(index);
            if (id > ServerBlockRegistry.VANILLA_MAX_ID && !registry.isBlock(id)) {
                send(player, ChatColor.RED, "L'ID " + id + " n'existe pas sur ce serveur ; fichier refuse.");
                return null;
            }
        }
        return clipboard;
    }

    private ExtendedClipboard remap(ExtendedClipboard clipboard) {
        Map<Integer, Integer> replacements = new HashMap<Integer, Integer>();
        for (Map.Entry<String, Integer> entry : clipboard.mapping.entrySet()) {
            int local = registry.idForName(entry.getKey());
            if (local > 0 && local != entry.getValue()) {
                log.debug("Remappage " + entry.getKey() + " : " + entry.getValue() + " -> " + local);
                replacements.put(entry.getValue(), local);
            }
        }
        return replacements.isEmpty() ? clipboard : clipboard.remap(replacements);
    }

    private void save(PlayerCommandPreprocessEvent event, Player player, String name) {
        final ExtendedClipboard clipboard = clipboards.get(player.getUniqueId());
        if (clipboard == null) {
            return;
        }
        event.setCancelled(true);

        final File file;
        try {
            file = resolve(player, name, false);
        } catch (Exception refused) {
            send(player, ChatColor.RED, "Nom de fichier refuse.");
            return;
        }
        if (file == null) {
            return;
        }

        Map<String, Integer> mapping = new HashMap<String, Integer>();
        for (int index = 0; index < clipboard.size(); index++) {
            int id = clipboard.idAt(index);
            if (id > ServerBlockRegistry.VANILLA_MAX_ID) {
                String blockName = registry.nameForId(id);
                if (blockName != null) {
                    mapping.put(blockName, id);
                }
            }
        }
        final ExtendedClipboard saved = new ExtendedClipboard(clipboard.width, clipboard.height, clipboard.length,
                clipboard.ids, clipboard.data, clipboard.offsetX, clipboard.offsetY, clipboard.offsetZ,
                clipboard.tiles, mapping);
        final Player owner = player;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    ExtendedSchematic.write(file, saved);
                    send(owner, ChatColor.GREEN, "Schematic ecrit : " + file.getName()
                            + (saved.hasExtendedIds() ? " (profil " + ExtendedSchematic.EXTENDED + ")" : ""));
                } catch (IOException failed) {
                    send(owner, ChatColor.RED, "Ecriture impossible : " + failed.getMessage());
                    log.warn("Ecriture de " + file.getName() + " impossible", failed);
                }
            }
        });
    }

    private File resolve(Player player, String name, boolean open) throws Exception {
        File directory = worldEdit.getWorldEdit()
                .getWorkingDirectoryFile(worldEdit.getLocalConfiguration().saveDir);
        com.sk89q.worldedit.entity.Player actor = worldEdit.wrapPlayer(player);
        return open
                ? worldEdit.getWorldEdit().getSafeOpenFile(actor, directory, name, "schematic", "schematic")
                : worldEdit.getWorldEdit().getSafeSaveFile(actor, directory, name, "schematic", "schematic");
    }

    private void replayJournal(Player player) {
        if (!player.hasPermission("fixworldedit.qtundo")) {
            send(player, ChatColor.RED, "Permission manquante : fixworldedit.qtundo");
            return;
        }
        UndoJournal journal = journalOf(player);
        if (!journal.exists()) {
            send(player, ChatColor.YELLOW, "Aucune operation etendue a annuler.");
            return;
        }
        UndoJournal.Reader reader;
        try {
            reader = journal.read();
        } catch (IOException unreadable) {
            send(player, ChatColor.RED, "Journal d'annulation illisible ; il est supprime.");
            journal.delete();
            return;
        }
        World world = plugin.getServer().getWorld(reader.worldName());
        if (world == null) {
            reader.close();
            send(player, ChatColor.RED, "Le monde " + reader.worldName() + " n'est plus charge.");
            return;
        }
        int restored = 0;
        try {
            while (reader.next()) {
                Block block = world.getBlockAt(reader.x, reader.y, reader.z);
                if (writer.canBuild(player, block) && writer.place(block, reader.id, (byte) reader.data)) {
                    restored++;
                }
            }
        } catch (IOException truncated) {
            log.warn("Journal interrompu apres " + restored + " blocs", truncated);
        } finally {
            reader.close();
        }
        journal.delete();
        send(player, ChatColor.GREEN, restored + " bloc(s) restaure(s).");
    }

    private UndoJournal journalOf(Player player) {
        return new UndoJournal(new File(new File(plugin.getDataFolder(), "undo"),
                player.getUniqueId() + ".qtundo"));
    }

    private static void send(Player player, ChatColor color, String message) {
        player.sendMessage(color + "[FixWorldEdit] " + message);
    }

    private int blocksPerTick() {
        return Math.max(1, plugin.getConfig().getInt("extended-worldedit.blocks-per-tick", 1000));
    }

    private long tickBudgetNanos() {
        long millis = plugin.getConfig().getLong("extended-worldedit.millis-per-tick", 30L);
        return Math.max(1L, Math.min(millis, 45L)) * 1000000L;
    }

    private static boolean memoryCritical() {
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        if (max == Long.MAX_VALUE) {
            return false;
        }
        long used = runtime.totalMemory() - runtime.freeMemory();
        return used > max / 100L * 85L;
    }

    private final class CopyJob implements Runnable, TaskRegistry.Task {
        private final Player player;
        private final World world;
        private final LocalSession session;
        private final Vector min;
        private final Vector max;
        private final boolean cut;
        private final int width;
        private final int height;
        private final int length;
        private final short[] ids;
        private final byte[] data;
        private final List<CompoundTag> tileTags = new ArrayList<CompoundTag>();
        private final Vector placement;

        private BulkBlockChange history;
        private EditSession edit;
        private UndoJournal journal;
        private BukkitTask task;
        private int cursor;
        private int removed;
        private boolean done;

        CopyJob(Player player, World world, LocalSession session, Vector min, Vector max, Vector placement, boolean cut) {
            this.player = player;
            this.world = world;
            this.session = session;
            this.min = min;
            this.max = max;
            this.placement = placement;
            this.cut = cut;
            this.width = max.getBlockX() - min.getBlockX() + 1;
            this.height = max.getBlockY() - min.getBlockY() + 1;
            this.length = max.getBlockZ() - min.getBlockZ() + 1;
            this.ids = new short[width * height * length];
            this.data = new byte[width * height * length];
            if (cut) {
                this.edit = editSessions.create(new BukkitWorld(world), session.getBlockChangeLimit());
                this.history = new BulkBlockChange(writer, player, world);
                this.journal = journalOf(player);
                try {
                    journal.open(world.getName());
                } catch (IOException failed) {
                    log.warn("Journal d'annulation indisponible", failed);
                    journal = null;
                }
            }
        }

        @Override
        public void run() {
            if (done) {
                return;
            }
            int budget = blocksPerTick();
            int volume = ids.length;
            long deadline = System.nanoTime() + tickBudgetNanos();

            while (budget-- > 0 && cursor < volume) {
                step(cursor++);
                if ((cursor & 255) == 0 && System.nanoTime() > deadline) {
                    break;
                }
            }
            if (cursor >= volume) {
                complete();
            }
        }

        private void step(int index) {
            int x = min.getBlockX() + index % width;
            int y = min.getBlockY() + index / (width * length);
            int z = min.getBlockZ() + index / width % length;

            Block block = world.getBlockAt(x, y, z);
            int id = writer.typeId(block);
            byte meta = block.getData();
            int target = (y - min.getBlockY()) * length * width + (z - min.getBlockZ()) * width + (x - min.getBlockX());
            ids[target] = (short) id;
            data[target] = meta;

            if (tiles.isAvailable()) {
                CompoundTag tag = tiles.toCompoundTag(tiles.capture(world, x, y, z));
                if (tag != null) {
                    tileTags.add(relocate(tag, x - min.getBlockX(), y - min.getBlockY(), z - min.getBlockZ()));
                }
            }
            if (!cut || id == 0) {
                return;
            }
            if (!writer.canBuild(player, block)) {
                return;
            }
            Object previousNbt = NativeTiles.captureNbt(world, x, y, z);
            if (!writer.place(block, 0, (byte) 0)) {
                return;
            }
            removed++;
            recordUndo(player, world, edit, history, journal, x, y, z, id, meta, previousNbt, 0, (byte) 0);
        }

        private void complete() {
            if (done) {
                return;
            }
            done = true;
            if (task != null) {
                task.cancel();
            }
            tasks.finish(player.getUniqueId());
            if (journal != null) {
                journal.close();
            }
            if (cut && history != null && !history.isEmpty()) {
                edit.getChangeSet().add(history);
                session.remember(edit);
            }
            Vector offset = min.subtract(placement);
            ExtendedClipboard clipboard = new ExtendedClipboard(width, height, length, ids, data,
                    offset.getBlockX(), offset.getBlockY(), offset.getBlockZ(),
                    tileTags, new HashMap<String, Integer>());
            clipboards.put(player.getUniqueId(), clipboard);
            log.trace("Clipboard", clipboard.idAt(0));
            send(player, ChatColor.GREEN, (cut ? "Zone coupee : " + removed + " bloc(s) retire(s), " : "Zone copiee : ")
                    + ids.length + " case(s) dans le presse-papiers etendu.");
        }

        @Override
        public void cancel() {
            done = true;
            if (task != null) {
                task.cancel();
            }
            if (journal != null) {
                journal.close();
            }
            if (cut && history != null && !history.isEmpty()) {
                edit.getChangeSet().add(history);
                session.remember(edit);
            }
            tasks.finish(player.getUniqueId());
        }
    }

    private final class PasteJob implements Runnable, TaskRegistry.Task {
        private final Player player;
        private final World world;
        private final LocalSession session;
        private final ExtendedClipboard clipboard;
        private final Vector target;
        private final boolean skipAir;
        private final int limit;
        private final long maxHistory;

        private BulkBlockChange history;
        private EditSession edit;
        private UndoJournal journal;
        private BukkitTask task;
        private int cursor;
        private int changed;
        private int skippedOutsideWorld;
        private int skippedProtected;
        private int skippedIdentical;
        private int skippedRefused;
        private int lastRefusedId;
        private int samples;
        private boolean done;

        private final RetryQueue retry = new RetryQueue();
        private boolean retrying;
        private int retryCursor;
        private int recovered;
        private final long startedAt = System.currentTimeMillis();
        private long lastReport = System.currentTimeMillis();

        PasteJob(Player player, World world, LocalSession session, ExtendedClipboard clipboard,
                 Vector target, boolean skipAir) {
            this.player = player;
            this.world = world;
            this.session = session;
            this.clipboard = clipboard;
            this.target = target;
            this.skipAir = skipAir;
            this.limit = session.getBlockChangeLimit();
            this.maxHistory = plugin.getConfig().getLong("extended-worldedit.max-history-blocks", DEFAULT_MAX_HISTORY);
            if (maxHistory > 0) {
                this.edit = editSessions.create(new BukkitWorld(world), limit);
                this.history = new BulkBlockChange(writer, player, world);
            }
            this.journal = journalOf(player);
            try {
                journal.open(world.getName());
            } catch (IOException failed) {
                log.warn("Journal d'annulation indisponible", failed);
                journal = null;
            }

            log.info("Collage demande par " + player.getName() + " dans " + world.getName()
                    + " : " + clipboard.width + "x" + clipboard.height + "x" + clipboard.length
                    + " (" + clipboard.size() + " cases)");
            log.info("  decalage du presse-papiers : " + clipboard.offsetX + "," + clipboard.offsetY
                    + "," + clipboard.offsetZ + " -> pose a partir de " + target.getBlockX() + ","
                    + target.getBlockY() + "," + target.getBlockZ()
                    + ", hauteur du monde " + world.getMaxHeight());
            log.info("  ignorer l'air : " + skipAir + ", limite de changements : " + limit
                    + ", historique max : " + maxHistory);
            log.info("  pose native : " + writer.describeNative(world));
        }

        @Override
        public void run() {
            if (done) {
                return;
            }
            int budget = blocksPerTick();
            int volume = clipboard.size();
            long deadline = System.nanoTime() + tickBudgetNanos();

            if (retrying) {
                retryPass(budget, deadline);
                return;
            }

            while (budget-- > 0 && cursor < volume) {
                if (limit >= 0 && changed >= limit) {
                    finish();
                    return;
                }
                step(cursor++);

                if ((cursor & 255) == 0) {
                    if (System.nanoTime() > deadline) {
                        break;
                    }
                    guardMemory();
                }
            }
            report(volume);
            if (cursor >= volume) {
                finish();
            }
        }

        private void step(int index) {
            int id = clipboard.idAt(index);
            if (skipAir && id == 0) {
                return;
            }
            int x = target.getBlockX() + index % clipboard.width;
            int y = target.getBlockY() + index / (clipboard.width * clipboard.length);
            int z = target.getBlockZ() + index / clipboard.width % clipboard.length;

            if (y < 0 || y >= world.getMaxHeight()) {
                skippedOutsideWorld++;
                return;
            }
            Block block = world.getBlockAt(x, y, z);
            if (!writer.canBuild(player, block)) {
                skippedProtected++;
                return;
            }
            int previousId = writer.typeId(block);
            byte previousData = block.getData();
            byte meta = clipboard.dataAt(index);
            if (previousId == id && previousData == meta) {
                skippedIdentical++;
                return;
            }
            Object previousNbt = NativeTiles.captureNbt(world, x, y, z);
            boolean placed = writer.place(block, id, meta);
            sample(index, x, y, z, previousId, previousData, id, meta, placed);
            if (!placed) {
                if (!retrying) {
                    retry.add(x, y, z, id, meta);
                }
                skippedRefused++;
                lastRefusedId = id;
                return;
            }
            changed++;
            recordUndo(player, world, edit, history, journal, x, y, z,
                    previousId, previousData, previousNbt, id, meta);

            if (history != null && history.size() > maxHistory) {
                history = null;
                edit = null;
                send(player, ChatColor.YELLOW, "Chantier trop grand pour //undo ; utilisez /qtundo.");
                log.warn("Historique memoire abandonne au-dela de " + maxHistory + " blocs pour " + player.getName());
            }
        }

        private void finish() {
            if (retrying || retry.isEmpty()) {
                complete();
                return;
            }
            retrying = true;
            retryCursor = 0;
            send(player, ChatColor.GRAY, "Second passage sur " + retry.size()
                    + " bloc(s) refuse(s) au premier.");
            log.info("Second passage : " + retry.size() + " bloc(s) a reprendre"
                    + (retry.hasOverflowed() ? " (liste plafonnee)" : ""));
        }

        private void retryPass(int budget, long deadline) {
            while (budget-- > 0 && retryCursor < retry.size()) {
                int index = retryCursor++;
                int x = retry.x(index);
                int y = retry.y(index);
                int z = retry.z(index);
                Block block = world.getBlockAt(x, y, z);
                if (writer.canBuild(player, block)) {
                    int previousId = writer.typeId(block);
                    byte previousData = block.getData();
                    if (writer.place(block, retry.id(index), retry.data(index))) {
                        recovered++;
                        skippedRefused--;
                        changed++;
                        recordUndo(player, world, edit, history, journal, x, y, z,
                                previousId, previousData, null, retry.id(index), retry.data(index));
                    }
                }
                if ((retryCursor & 255) == 0 && System.nanoTime() > deadline) {
                    return;
                }
            }
            if (retryCursor >= retry.size()) {
                complete();
            }
        }

        private void guardMemory() {
            if (history == null || !memoryCritical()) {
                return;
            }
            long dropped = history.memoryBytes() >> 20;
            history = null;
            edit = null;
            send(player, ChatColor.YELLOW, "Memoire serveur tendue : //undo abandonne pour ce"
                    + " chantier, /qtundo prend le relais.");
            log.warn("Historique memoire abandonne (" + dropped + " Mo) pour " + player.getName()
                    + " : tas a plus de 85 %.");
        }

        private void report(int volume) {
            long now = System.currentTimeMillis();
            if (now - lastReport < 5000L || cursor >= volume) {
                return;
            }
            lastReport = now;
            int percent = (int) ((long) cursor * 100L / volume);
            long elapsed = now - startedAt;
            long remaining = cursor == 0 ? 0 : elapsed * (volume - cursor) / cursor;
            String progress = "Collage " + percent + "% (" + cursor + "/" + volume
                    + "), " + changed + " pose(s), fin dans ~" + (remaining / 1000L)
                    + "s. Ignores : " + skipSummary();
            send(player, ChatColor.GRAY, progress + ". //cancel pour arreter.");
            log.info(progress);
        }

        private void sample(int index, int x, int y, int z, int previousId, byte previousData,
                            int id, byte meta, boolean placed) {
            if (samples >= 20) {
                return;
            }
            samples++;
            log.info("  bloc #" + index + " en " + x + "," + y + "," + z
                    + " : " + previousId + ":" + previousData + " -> " + id + ":" + meta
                    + (placed ? " OK, relu " + writer.typeIdAt(world, x, y, z)
                              : " REFUSE, ancien bloc remis"));
        }

        private String skipSummary() {
            StringBuilder skipped = new StringBuilder();
            append(skipped, skippedIdentical, "deja conforme(s)");
            append(skipped, skippedOutsideWorld, "hors du monde");
            append(skipped, skippedProtected, "refuse(s) par WorldGuard");
            append(skipped, skippedRefused, "refuse(s) par le serveur (dernier ID : " + lastRefusedId + ")");
            return skipped.length() == 0 ? "aucun" : skipped.toString();
        }

        private void summarise() {
            send(player, ChatColor.GREEN, changed + " bloc(s) pose(s)"
                    + (recovered > 0 ? ", dont " + recovered + " au second passage" : "")
                    + ". Ignores : " + skipSummary() + ".");
            log.info("Collage termine pour " + player.getName() + " : " + changed + " pose(s) sur "
                    + cursor + " case(s) traitee(s), " + recovered + " rattrape(s) au second"
                    + " passage. Ignores : " + skipSummary());
            if (changed == 0 && skippedRefused > 0) {
                send(player, ChatColor.RED, "Aucun bloc pose : le serveur n'a garde aucun ID."
                        + " Pose native : " + writer.describeNative(world));
            }
            if (changed == 0 && skippedIdentical > 0 && skippedRefused == 0) {
                send(player, ChatColor.YELLOW, "Rien a changer : la zone visee est deja identique"
                        + " au presse-papiers. Position de collage : " + target.getBlockX() + ","
                        + target.getBlockY() + "," + target.getBlockZ() + ".");
            }
        }

        private void complete() {
            if (done) {
                return;
            }
            done = true;
            if (task != null) {
                task.cancel();
            }
            tasks.finish(player.getUniqueId());
            if (journal != null) {
                journal.close();
            }
            applyTiles();
            if (history != null && !history.isEmpty()) {
                edit.getChangeSet().add(history);
                session.remember(edit);
            }
            summarise();
        }

        private void applyTiles() {
            if (!tiles.isAvailable()) {
                return;
            }
            for (CompoundTag tag : clipboard.tiles) {
                Object nbt = tiles.fromCompoundTag(tag);
                if (nbt == null) {
                    continue;
                }
                tiles.apply(world, target.getBlockX() + tag.getInt("x"),
                        target.getBlockY() + tag.getInt("y"),
                        target.getBlockZ() + tag.getInt("z"), nbt);
            }
        }

        @Override
        public void cancel() {
            done = true;
            if (task != null) {
                task.cancel();
            }
            if (journal != null) {
                journal.close();
            }
            if (history != null && !history.isEmpty()) {
                edit.getChangeSet().add(history);
                session.remember(edit);
            }
            tasks.finish(player.getUniqueId());
            summarise();
        }
    }

    private void recordUndo(Player player, World world, EditSession edit, BulkBlockChange history,
                            UndoJournal journal, int x, int y, int z,
                            int previousId, byte previousData, Object previousNbt, int currentId, byte currentData) {
        if (history != null) {
            if (previousNbt != null) {
                edit.getChangeSet().add(new BukkitBlockChange(writer, player, world,
                        x, y, z, previousId, previousData, previousNbt, currentId, currentData));
            } else {
                history.add(x, y, z, previousId, previousData, currentId, currentData);
            }
        }
        if (journal != null) {
            try {
                journal.record(x, y, z, previousId, previousData);
            } catch (IOException failed) {
                log.warn("Journal d'annulation interrompu", failed);
            }
        }
    }

    private static String census(ExtendedClipboard clipboard) {
        int air = 0;
        int vanilla = 0;
        int extended = 0;
        int firstExtended = -1;
        for (int index = 0; index < clipboard.size(); index++) {
            int id = clipboard.idAt(index);
            if (id == 0) {
                air++;
            } else if (id <= ServerBlockRegistry.VANILLA_MAX_ID) {
                vanilla++;
            } else {
                extended++;
                if (firstExtended < 0) {
                    firstExtended = id;
                }
            }
        }
        return air + " d'air, " + vanilla + " ordinaire(s), " + extended + " etendu(s)"
                + (firstExtended < 0 ? "" : " (premier : " + firstExtended + ")");
    }

    private static void append(StringBuilder text, int count, String label) {
        if (count <= 0) {
            return;
        }
        if (text.length() > 0) {
            text.append(", ");
        }
        text.append(count).append(' ').append(label);
    }

    private static CompoundTag relocate(CompoundTag tag, int x, int y, int z) {
        Map<String, com.sk89q.jnbt.Tag> values = new HashMap<String, com.sk89q.jnbt.Tag>(tag.getValue());
        values.put("x", new com.sk89q.jnbt.IntTag(x));
        values.put("y", new com.sk89q.jnbt.IntTag(y));
        values.put("z", new com.sk89q.jnbt.IntTag(z));
        return new CompoundTag(values);
    }
}
