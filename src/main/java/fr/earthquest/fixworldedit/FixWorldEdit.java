package fr.earthquest.fixworldedit;

import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class FixWorldEdit extends JavaPlugin {
    private ExtendedRegionListener extendedRegionListener;
    private ExtendedClipboardListener extendedClipboardListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Plugin plugin = Bukkit.getPluginManager().getPlugin("WorldEdit");
        if (!(plugin instanceof WorldEditPlugin) || !plugin.isEnabled()) {
            getLogger().severe("WorldEdit est requis mais introuvable !");
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

    @Override
    public void onDisable() {
        if (this.extendedRegionListener != null) {
            this.extendedRegionListener.close();
        }
        if (this.extendedClipboardListener != null) {
            this.extendedClipboardListener.close();
        }
        HandlerList.unregisterAll(this);
    }
}
