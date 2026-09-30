package fr.earthquest.fixworldedit;

import java.lang.reflect.Method;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public interface BuildGuard {
    BuildGuard NONE = new BuildGuard() {
        @Override
        public boolean canBuild(Player player, Block block) {
            return true;
        }

        @Override
        public String describe() {
            return "aucune protection";
        }
    };

    boolean canBuild(Player player, Block block);

    String describe();

    static BuildGuard of(Plugin worldGuard) {
        if (worldGuard == null || !worldGuard.isEnabled()) {
            return NONE;
        }
        final Plugin target = worldGuard;
        final Method canBuild;
        try {
            canBuild = target.getClass().getMethod("canBuild", Player.class, Block.class);
        } catch (NoSuchMethodException missing) {
            return NONE;
        }
        return new BuildGuard() {
            @Override
            public boolean canBuild(Player player, Block block) {
                try {
                    return Boolean.TRUE.equals(canBuild.invoke(target, player, block));
                } catch (Exception failed) {
                    return false;
                }
            }

            @Override
            public String describe() {
                return "WorldGuard " + target.getDescription().getVersion();
            }
        };
    }
}
