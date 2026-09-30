package fr.earthquest.fixworldedit;

import com.sk89q.jnbt.CompoundTag;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ExtendedClipboard {
    public static final int MAX_VANILLA_ID = 4095;

    public final int width;
    public final int height;
    public final int length;

    public final short[] ids;
    public final byte[] data;

    public final int offsetX;
    public final int offsetY;
    public final int offsetZ;

    public final List<CompoundTag> tiles;

    public final Map<String, Integer> mapping;

    public ExtendedClipboard(int width, int height, int length, short[] ids, byte[] data,
                             int offsetX, int offsetY, int offsetZ,
                             List<CompoundTag> tiles, Map<String, Integer> mapping) {
        int expected = width * height * length;
        if (ids.length != expected || data.length != expected) {
            throw new IllegalArgumentException("Dimensions " + width + "x" + height + "x" + length
                    + " incompatibles avec " + ids.length + " identifiants et " + data.length + " metadonnees");
        }
        this.width = width;
        this.height = height;
        this.length = length;
        this.ids = ids;
        this.data = data;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.tiles = tiles;
        this.mapping = mapping;
    }

    public int size() {
        return width * height * length;
    }

    public int index(int x, int y, int z) {
        return (y * length + z) * width + x;
    }

    public int idAt(int index) {
        return ids[index] & 0xFFFF;
    }

    public byte dataAt(int index) {
        return data[index];
    }

    public boolean hasExtendedIds() {
        for (short id : ids) {
            if ((id & 0xFFFF) > MAX_VANILLA_ID) {
                return true;
            }
        }
        return false;
    }

    public ExtendedClipboard remap(Map<Integer, Integer> replacements) {
        short[] remapped = new short[ids.length];
        for (int i = 0; i < ids.length; i++) {
            Integer replacement = replacements.get(idAt(i));
            remapped[i] = replacement == null ? ids[i] : (short) replacement.intValue();
        }
        Map<String, Integer> names = new HashMap<String, Integer>();
        for (Map.Entry<String, Integer> entry : mapping.entrySet()) {
            Integer replacement = replacements.get(entry.getValue());
            names.put(entry.getKey(), replacement == null ? entry.getValue() : replacement);
        }
        return new ExtendedClipboard(width, height, length, remapped, data.clone(),
                offsetX, offsetY, offsetZ, new ArrayList<CompoundTag>(tiles), names);
    }
}
