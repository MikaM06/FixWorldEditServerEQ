package fr.earthquest.fixworldedit;

import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.NBTInputStream;
import com.sk89q.jnbt.NBTOutputStream;
import com.sk89q.jnbt.NamedTag;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.lang.reflect.Method;
import org.bukkit.World;

public class NativeTiles {
    private static final String[] GET_TILE = {"getTileEntity", "func_147438_o"};
    private static final String[] WRITE_NBT = {"writeToNBT", "func_145841_b", "b"};
    private static final String[] READ_NBT = {"readFromNBT", "func_145839_a", "a"};
    private static final String[] MARK_DIRTY = {"markDirty", "func_70296_d", "update"};
    private static final String[] COPY = {"copy", "func_74737_b", "clone"};

    private static final String[] STREAM_TOOLS = {
            "net.minecraft.nbt.CompressedStreamTools",
            "net.minecraft.server.v1_7_R4.NBTCompressedStreamTools",
    };

    public boolean isAvailable() {
        return streamToolsClass() != null;
    }

    public Object capture(World world, int x, int y, int z) {
        return captureNbt(world, x, y, z);
    }

    public void apply(World world, int x, int y, int z, Object nbt) {
        applyNbt(world, x, y, z, nbt);
    }

    public CompoundTag toCompoundTag(Object nativeNbt) {
        if (nativeNbt == null) {
            return null;
        }
        try {
            Class<?> tools = streamToolsClass();
            if (tools == null) {
                return null;
            }
            Method write = findMethod(tools, nativeNbt.getClass(), DataOutput.class);
            if (write == null) {
                return null;
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            write.invoke(null, nativeNbt, new DataOutputStream(bytes));

            NBTInputStream in = new NBTInputStream(new ByteArrayInputStream(bytes.toByteArray()));
            try {
                NamedTag named = in.readNamedTag();
                return named.getTag() instanceof CompoundTag ? (CompoundTag) named.getTag() : null;
            } finally {
                in.close();
            }
        } catch (Exception unsupported) {
            return null;
        }
    }

    public Object fromCompoundTag(CompoundTag tag) {
        if (tag == null) {
            return null;
        }
        try {
            Class<?> tools = streamToolsClass();
            if (tools == null) {
                return null;
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NBTOutputStream out = new NBTOutputStream(bytes);
            try {
                out.writeNamedTag("", tag);
            } finally {
                out.close();
            }
            for (Method method : tools.getMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length == 1 && DataInput.class.isAssignableFrom(parameters[0])
                        && !method.getReturnType().equals(Void.TYPE)) {
                    return method.invoke(null, new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
                }
            }
            return null;
        } catch (Exception unsupported) {
            return null;
        }
    }

    static Object captureNbt(World world, int x, int y, int z) {
        try {
            Object tile = tileEntity(world, x, y, z);
            if (tile == null) {
                return null;
            }
            Method write = method(tile.getClass(), WRITE_NBT, 1);
            if (write == null) {
                return null;
            }
            Object nbt = write.getParameterTypes()[0].newInstance();
            write.invoke(tile, nbt);
            return nbt;
        } catch (Exception unsupported) {
            return null;
        }
    }

    static void applyNbt(World world, int x, int y, int z, Object nbt) {
        if (nbt == null) {
            return;
        }
        try {
            Object tile = tileEntity(world, x, y, z);
            if (tile == null) {
                return;
            }
            Method read = method(tile.getClass(), READ_NBT, 1);
            if (read == null) {
                return;
            }
            read.invoke(tile, copyOf(nbt));
            Method dirty = method(tile.getClass(), MARK_DIRTY, 0);
            if (dirty != null) {
                dirty.invoke(tile);
            }
        } catch (Exception unsupported) {
        }
    }

    static Object copyOf(Object nbt) throws Exception {
        Method copy = method(nbt.getClass(), COPY, 0);
        return copy == null ? nbt : copy.invoke(nbt);
    }

    private static Object tileEntity(World world, int x, int y, int z) throws Exception {
        Method getHandle = method(world.getClass(), new String[] {"getHandle"}, 0);
        if (getHandle == null) {
            return null;
        }
        Object handle = getHandle.invoke(world);
        if (handle == null) {
            return null;
        }
        Method getTile = method(handle.getClass(), GET_TILE, 3);
        return getTile == null ? null : getTile.invoke(handle, x, y, z);
    }

    private static Method method(Class<?> owner, String[] names, int parameters) {
        for (String name : names) {
            for (Method method : owner.getMethods()) {
                if (method.getName().equals(name) && method.getParameterTypes().length == parameters) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> owner, Class<?> first, Class<?> second) {
        for (Method method : owner.getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 2 && parameters[0].isAssignableFrom(first)
                    && second.isAssignableFrom(parameters[1])) {
                return method;
            }
        }
        return null;
    }

    private static Class<?> streamToolsClass() {
        for (String name : STREAM_TOOLS) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException absent) {
            }
        }
        return null;
    }
}
