package fr.earthquest.fixworldedit;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class FixWorldEdit extends JavaPlugin {
    private ExtendedRegionListener extendedRegionListener;
    private ExtendedClipboardListener extendedClipboardListener;
    private ExtendedEditSessionFactory extendedSessions;
    private ExtendedMaskParser extendedMasks;
    private WorldEdit worldEditCore;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Plugin plugin = Bukkit.getPluginManager().getPlugin("WorldEdit");
        if (!(plugin instanceof WorldEditPlugin) || !plugin.isEnabled()) {
            getLogger().severe("WorldEdit est requis mais introuvable !");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        try {
            Class.forName("com.sk89q.worldedit.world.World", false, plugin.getClass().getClassLoader());
        } catch (ClassNotFoundException | LinkageError missing) {
            getLogger().severe("WorldEdit 6 est requis, version trouvee : "
                    + plugin.getDescription().getVersion() + ". Installez worldedit-bukkit 6.1.9.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        if (!getConfig().getBoolean("extended-worldedit.enabled", true)) {
            getLogger().info("Correctif WorldEdit : desactive par la configuration (extended-worldedit.enabled).");
            return;
        }
        WorldEditPlugin worldEdit = (WorldEditPlugin) plugin;
        BuildGuard guard = BuildGuard.of(Bukkit.getPluginManager().getPlugin("WorldGuard"));
        ServerBlockRegistry registry = new ServerBlockRegistry();
        hookWorldEdit(worldEdit.getWorldEdit(), registry);
        this.extendedRegionListener = new ExtendedRegionListener(this, worldEdit, guard, registry);
        this.extendedClipboardListener = new ExtendedClipboardListener(this, worldEdit, guard, registry,
                new NativeTiles(), new NativeBlocks(), new TaskRegistry());
        Bukkit.getPluginManager().registerEvents(this.extendedRegionListener, this);
        Bukkit.getPluginManager().registerEvents(this.extendedClipboardListener, this);

        if (registry.isNative()) {
            getLogger().info("Correctif WorldEdit : actif, IDs jusqu'a " + registry.maxBlockId()
                    + " via " + registry.describe() + ", protections : " + guard.describe() + ".");
        } else {
            getLogger().warning("Correctif WorldEdit : registre de blocs natif introuvable ("
                    + registry.describe() + ") : les IDs > 4095 seront refuses.");
        }
    }

    private void hookWorldEdit(WorldEdit core, ServerBlockRegistry registry) {
        this.worldEditCore = core;
        NativeBlocks natives = new NativeBlocks();
        try {
            this.extendedSessions = ExtendedEditSessionFactory.install(core, natives, registry);
        } catch (ReflectiveOperationException | RuntimeException failed) {
            getLogger().log(Level.WARNING, "Lecture des IDs etendus par WorldEdit impossible : les commandes"
                    + " WorldEdit echoueront encore sur les blocs > 4095.", failed);
        }
        try {
            this.extendedMasks = ExtendedMaskParser.install(core, natives, registry);
            getLogger().info("Masques WorldEdit : les blocs > 4095 y valent l'ID " + extendedMasks.standIn() + ".");
        } catch (ReflectiveOperationException | RuntimeException failed) {
            getLogger().log(Level.WARNING, "Masques WorldEdit non corriges : //replace <bloc> echouera encore"
                    + " sur les blocs > 4095.", failed);
        }
    }

    @Override
    public void onDisable() {
        if (this.extendedMasks != null) {
            this.extendedMasks.uninstall();
            this.extendedMasks = null;
        }
        if (this.extendedSessions != null) {
            try {
                this.extendedSessions.uninstall(this.worldEditCore);
            } catch (ReflectiveOperationException failed) {
                getLogger().log(Level.WARNING, "Restauration de la fabrique d'EditSession impossible", failed);
            }
            this.extendedSessions = null;
        }
        if (this.extendedRegionListener != null) {
            this.extendedRegionListener.close();
        }
        if (this.extendedClipboardListener != null) {
            this.extendedClipboardListener.close();
        }
        HandlerList.unregisterAll(this);
    }
}
