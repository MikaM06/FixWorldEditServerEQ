package fr.earthquest.fixworldedit;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;

public final class ExtendedLog {
    public static final String PREFIX = "[WorldEditExtendedFix] ";

    private final Logger logger;
    private boolean debug;

    public ExtendedLog(Plugin plugin) {
        this.logger = plugin.getLogger();
        reload(plugin);
    }

    public void reload(Plugin plugin) {
        this.debug = plugin.getConfig().getBoolean("extended-worldedit.debug", false);
    }

    public boolean isDebug() {
        return debug;
    }

    public void trace(String stage, int id) {
        if (debug) {
            logger.info(PREFIX + stage + " block ID: " + id);
        }
    }

    public void trace(String stage, int id, int data) {
        if (debug) {
            logger.info(PREFIX + stage + " block ID: " + id + ", metadata: " + data);
        }
    }

    public void debug(String message) {
        if (debug) {
            logger.info(PREFIX + message);
        }
    }

    public void info(String message) {
        logger.info(PREFIX + message);
    }

    public void warn(String message) {
        logger.warning(PREFIX + message);
    }

    public void warn(String message, Throwable cause) {
        logger.log(Level.WARNING, PREFIX + message, cause);
    }
}
