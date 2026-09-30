package fr.earthquest.fixworldedit;

import java.lang.reflect.Method;
import org.bukkit.World;

public class NativeBlocks {
    private static final int FLAG_NO_PHYSICS = 2;

    private Class<?> handleClass;
    private Method getHandle;
    private Method nativeGetBlock;
    private Method nativeSetBlock;
    private Method getIdFromBlock;
    private Method getBlockById;
    private boolean resolved;

    public boolean isAvailable(World world) {
        resolve(world);
        return nativeSetBlock != null && nativeGetBlock != null;
    }

    public String describe(World world) {
        resolve(world);
        if (getHandle == null) {
            return "indisponible (pas de getHandle sur " + world.getClass().getName() + ")";
        }
        if (nativeGetBlock == null) {
            return "indisponible (pas de lecture (int,int,int) -> Block sur "
                    + (handleClass == null ? "?" : handleClass.getName()) + ")";
        }
        if (nativeSetBlock == null) {
            return "indisponible (pas de pose (int,int,int,Block,int,int) sur " + handleClass.getName() + ")";
        }
        return handleClass.getName() + "." + nativeSetBlock.getName() + "(...)";
    }

    public int getId(World world, int x, int y, int z) {
        if (!isAvailable(world)) {
            return -1;
        }
        try {
            Object block = nativeGetBlock.invoke(handle(world), x, y, z);
            return block == null ? -1 : (Integer) getIdFromBlock.invoke(null, block);
        } catch (Exception unreadable) {
            return -1;
        }
    }

    public boolean setBlock(World world, int x, int y, int z, int id, int data) {
        if (!isAvailable(world)) {
            return false;
        }
        try {
            Object block = getBlockById.invoke(null, id);
            if (block == null) {
                return false;
            }
            Object placed = nativeSetBlock.invoke(handle(world), x, y, z, block, data, FLAG_NO_PHYSICS);
            return placed instanceof Boolean ? (Boolean) placed : true;
        } catch (Exception refused) {
            return false;
        }
    }

    private Object handle(World world) throws Exception {
        return getHandle.invoke(world);
    }

    private void resolve(World world) {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            getHandle = findMethod(world.getClass(), "getHandle", 0);
            if (getHandle == null) {
                return;
            }
            Object handle = getHandle.invoke(world);
            if (handle == null) {
                return;
            }
            handleClass = handle.getClass();

            for (Method method : handleClass.getMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length == 3 && allInts(parameters, 3)
                        && !method.getReturnType().isPrimitive()
                        && method.getReturnType().getSimpleName().equals("Block")) {
                    nativeGetBlock = method;
                    break;
                }
            }
            if (nativeGetBlock == null) {
                return;
            }
            Class<?> blockClass = nativeGetBlock.getReturnType();
            getIdFromBlock = Reflect.findStatic(blockClass, Integer.TYPE, blockClass);
            getBlockById = Reflect.findStatic(blockClass, blockClass, Integer.TYPE);
            if (getIdFromBlock == null || getBlockById == null) {
                nativeGetBlock = null;
                return;
            }

            nativeSetBlock = Reflect.find(handleClass, Boolean.TYPE,
                    Integer.TYPE, Integer.TYPE, Integer.TYPE, blockClass, Integer.TYPE, Integer.TYPE);
        } catch (Exception withoutNative) {
            nativeGetBlock = null;
            nativeSetBlock = null;
        }
    }

    private static boolean allInts(Class<?>[] parameters, int count) {
        for (int i = 0; i < count; i++) {
            if (!parameters[i].equals(Integer.TYPE)) {
                return false;
            }
        }
        return true;
    }

    private static Method findMethod(Class<?> owner, String name, int parameters) {
        for (Method method : owner.getMethods()) {
            if (method.getName().equals(name) && method.getParameterTypes().length == parameters) {
                return method;
            }
        }
        return null;
    }
}
