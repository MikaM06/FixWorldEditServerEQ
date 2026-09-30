package fr.earthquest.fixworldedit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;

public class ServerBlockRegistry {
    public static final int VANILLA_MAX_ID = 4095;

    private static final int EXTENDED_MAX_ID = 32767;

    private static final String[] BLOCK_CLASSES = {
            "net.minecraft.block.Block",
            "net.minecraft.server.v1_7_R4.Block",
    };

    private static final String[] REGISTRY_FIELDS = {"blockRegistry", "field_149771_c", "REGISTRY"};
    private static final String[] NAME_OF = {"getNameForObject", "func_148750_c", "c"};
    private static final String[] OBJECT_OF = {"getObject", "func_82594_a", "get", "a"};

    private final Class<?> blockClass;
    private final Method getBlockById;
    private final Method getIdFromBlock;
    private final Object blockRegistry;
    private final Method getNameForObject;
    private final Method getObjectByName;
    private final Object air;
    private final int maxBlockId;

    public ServerBlockRegistry() {
        Class<?> foundClass = blockClass();
        Method byId = null;
        Method toId = null;
        Object registry = null;
        Method nameOf = null;
        Method objectOf = null;
        Object airBlock = null;

        if (foundClass != null) {
            byId = Reflect.findStatic(foundClass, foundClass, Integer.TYPE);
            toId = Reflect.findStatic(foundClass, Integer.TYPE, foundClass);
            if (byId != null) {
                try {
                    airBlock = byId.invoke(null, 0);
                } catch (Exception unreadable) {
                    byId = null;
                }
            }
            registry = registryOf(foundClass);
            if (registry != null) {
                nameOf = Reflect.named(registry.getClass(), NAME_OF, 1);
                objectOf = Reflect.named(registry.getClass(), OBJECT_OF, 1);
            }
        }

        this.blockClass = foundClass;
        this.getBlockById = byId;
        this.getIdFromBlock = toId;
        this.blockRegistry = registry;
        this.getNameForObject = nameOf;
        this.getObjectByName = objectOf;
        this.air = airBlock;
        this.maxBlockId = byId == null ? VANILLA_MAX_ID : EXTENDED_MAX_ID;
    }

    public boolean isNative() {
        return getBlockById != null;
    }

    public String describe() {
        if (blockClass == null) {
            return "aucune classe Block trouvee parmi " + BLOCK_CLASSES.length + " candidates";
        }
        if (getBlockById == null) {
            return blockClass.getName() + " trouvee, mais pas son accesseur (int) -> Block";
        }
        return blockClass.getName() + "." + getBlockById.getName() + "(int)"
                + (blockRegistry == null ? ", sans registre nomme" : ", registre nomme disponible");
    }

    public int maxBlockId() {
        return maxBlockId;
    }

    public boolean isBlock(int id) {
        if (id < 0 || id > maxBlockId) {
            return false;
        }
        if (id == 0) {
            return true;
        }
        if (getBlockById == null) {
            Material material = Material.getMaterial(id);
            return material != null && material.isBlock();
        }
        try {
            Object block = getBlockById.invoke(null, id);
            return block != null && block != air;
        } catch (Exception unreadable) {
            return false;
        }
    }

    public String nameForId(int id) {
        if (getBlockById == null || getNameForObject == null || !isBlock(id)) {
            return null;
        }
        try {
            Object block = getBlockById.invoke(null, id);
            Object name = getNameForObject.invoke(blockRegistry, block);
            return name == null ? null : name.toString();
        } catch (Exception unreadable) {
            return null;
        }
    }

    public int idForName(String name) {
        if (name == null || getObjectByName == null || getIdFromBlock == null) {
            return -1;
        }
        try {
            Object block = getObjectByName.invoke(blockRegistry, name);
            if (block == null || block == air || !blockClass.isInstance(block)) {
                return -1;
            }
            return (Integer) getIdFromBlock.invoke(null, block);
        } catch (Exception unreadable) {
            return -1;
        }
    }

    private static Object registryOf(Class<?> blockClass) {
        for (String name : REGISTRY_FIELDS) {
            try {
                Field field = blockClass.getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(null);
                if (value != null) {
                    return value;
                }
            } catch (Exception absent) {
            }
        }
        return null;
    }

    private static Class<?> blockClass() {
        List<ClassLoader> loaders = new ArrayList<ClassLoader>();
        loaders.add(ServerBlockRegistry.class.getClassLoader());
        loaders.add(Thread.currentThread().getContextClassLoader());
        try {
            if (Bukkit.getServer() != null) {
                loaders.add(Bukkit.getServer().getClass().getClassLoader());
            }
        } catch (Throwable withoutServer) {
        }
        for (String name : BLOCK_CLASSES) {
            for (ClassLoader loader : loaders) {
                if (loader == null) {
                    continue;
                }
                try {
                    return Class.forName(name, false, loader);
                } catch (Throwable absent) {
                }
            }
        }
        return null;
    }
}
