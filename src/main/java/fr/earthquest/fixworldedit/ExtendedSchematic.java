package fr.earthquest.fixworldedit;

import com.sk89q.jnbt.ByteArrayTag;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.IntTag;
import com.sk89q.jnbt.ListTag;
import com.sk89q.jnbt.NBTInputStream;
import com.sk89q.jnbt.NBTOutputStream;
import com.sk89q.jnbt.NamedTag;
import com.sk89q.jnbt.ShortTag;
import com.sk89q.jnbt.StringTag;
import com.sk89q.jnbt.Tag;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class ExtendedSchematic {
    public static final String ALPHA = "Alpha";

    public static final String EXTENDED = "QuestBlockExtended";

    private static final String MAPPING_TAG = "QuestBlockMapping";

    private ExtendedSchematic() {
    }

    public static final class Header {
        public final String materials;
        public final int width;
        public final int height;
        public final int length;

        Header(String materials, int width, int height, int length) {
            this.materials = materials;
            this.width = width;
            this.height = height;
            this.length = length;
        }

        public boolean isExtended() {
            return EXTENDED.equals(materials);
        }
    }

    public static Header peek(File file) throws IOException {
        CompoundTag schematic = readRoot(file);
        return new Header(string(schematic, "Materials"),
                unsignedShort(schematic, "Width"),
                unsignedShort(schematic, "Height"),
                unsignedShort(schematic, "Length"));
    }

    public static ExtendedClipboard read(File file) throws IOException {
        CompoundTag schematic = readRoot(file);
        String materials = string(schematic, "Materials");
        if (!ALPHA.equals(materials) && !EXTENDED.equals(materials)) {
            throw new IOException("Profil de schematic inconnu : " + materials);
        }

        int width = unsignedShort(schematic, "Width");
        int height = unsignedShort(schematic, "Height");
        int length = unsignedShort(schematic, "Length");
        int volume = width * height * length;

        byte[] low = byteArray(schematic, "Blocks");
        byte[] data = byteArray(schematic, "Data");
        if (low.length != volume || data.length != volume) {
            throw new IOException("Plans de blocs incomplets : " + low.length + " et " + data.length
                    + " octets pour " + volume + " cases");
        }
        byte[] add = optionalByteArray(schematic, "AddBlocks");
        byte[] add2 = optionalByteArray(schematic, "AddBlocks2");

        short[] ids = new short[volume];
        for (int index = 0; index < volume; index++) {
            int id = low[index] & 0xFF;
            id |= nibble(add, index) << 8;
            id |= nibble(add2, index) << 12;
            ids[index] = (short) id;
        }

        List<CompoundTag> tiles = new ArrayList<CompoundTag>();
        Tag tileTag = schematic.getValue().get("TileEntities");
        if (tileTag instanceof ListTag) {
            for (Tag tag : ((ListTag) tileTag).getValue()) {
                if (tag instanceof CompoundTag) {
                    tiles.add((CompoundTag) tag);
                }
            }
        }

        Map<String, Integer> mapping = new HashMap<String, Integer>();
        Tag mappingTag = schematic.getValue().get(MAPPING_TAG);
        if (mappingTag instanceof CompoundTag) {
            for (Map.Entry<String, Tag> entry : ((CompoundTag) mappingTag).getValue().entrySet()) {
                if (entry.getValue() instanceof IntTag) {
                    mapping.put(entry.getKey(), ((IntTag) entry.getValue()).getValue());
                }
            }
        }

        return new ExtendedClipboard(width, height, length, ids, data,
                optionalInt(schematic, "WEOffsetX"),
                optionalInt(schematic, "WEOffsetY"),
                optionalInt(schematic, "WEOffsetZ"),
                tiles, mapping);
    }

    public static void write(File file, ExtendedClipboard clipboard) throws IOException {
        boolean extended = clipboard.hasExtendedIds();
        int volume = clipboard.size();

        byte[] low = new byte[volume];
        byte[] add = null;
        byte[] add2 = null;

        for (int index = 0; index < volume; index++) {
            int id = clipboard.idAt(index);
            low[index] = (byte) (id & 0xFF);
            if ((id >> 8 & 0xF) != 0) {
                if (add == null) {
                    add = new byte[(volume >> 1) + 1];
                }
                setNibble(add, index, id >> 8 & 0xF);
            }
            if ((id >> 12 & 0xF) != 0) {
                if (add2 == null) {
                    add2 = new byte[(volume >> 1) + 1];
                }
                setNibble(add2, index, id >> 12 & 0xF);
            }
        }

        Map<String, Tag> schematic = new LinkedHashMap<String, Tag>();
        schematic.put("Width", new ShortTag((short) clipboard.width));
        schematic.put("Height", new ShortTag((short) clipboard.height));
        schematic.put("Length", new ShortTag((short) clipboard.length));
        schematic.put("Materials", new StringTag(extended ? EXTENDED : ALPHA));
        schematic.put("Blocks", new ByteArrayTag(low));
        schematic.put("Data", new ByteArrayTag(clipboard.data));
        if (add != null) {
            schematic.put("AddBlocks", new ByteArrayTag(add));
        }
        if (add2 != null) {
            schematic.put("AddBlocks2", new ByteArrayTag(add2));
        }
        schematic.put("WEOffsetX", new IntTag(clipboard.offsetX));
        schematic.put("WEOffsetY", new IntTag(clipboard.offsetY));
        schematic.put("WEOffsetZ", new IntTag(clipboard.offsetZ));
        schematic.put("Entities", new ListTag(CompoundTag.class, new ArrayList<Tag>()));
        schematic.put("TileEntities", new ListTag(CompoundTag.class, new ArrayList<Tag>(clipboard.tiles)));
        if (extended && !clipboard.mapping.isEmpty()) {
            Map<String, Tag> mapping = new LinkedHashMap<String, Tag>();
            for (Map.Entry<String, Integer> entry : clipboard.mapping.entrySet()) {
                mapping.put(entry.getKey(), new IntTag(entry.getValue()));
            }
            schematic.put(MAPPING_TAG, new CompoundTag(mapping));
        }

        NBTOutputStream out = new NBTOutputStream(new GZIPOutputStream(
                new BufferedOutputStream(new FileOutputStream(file))));
        try {
            out.writeNamedTag("Schematic", new CompoundTag(schematic));
        } finally {
            out.close();
        }
    }

    private static CompoundTag readRoot(File file) throws IOException {
        NBTInputStream in;
        try {
            in = new NBTInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(file))));
        } catch (IOException notGzip) {
            throw new IOException("Fichier illisible, ce n'est pas un schematic gzip : " + file.getName(), notGzip);
        }
        try {
            NamedTag root = in.readNamedTag();
            if (!"Schematic".equals(root.getName()) || !(root.getTag() instanceof CompoundTag)) {
                throw new IOException("Le tag 'Schematic' est absent de " + file.getName());
            }
            return (CompoundTag) root.getTag();
        } finally {
            in.close();
        }
    }

    private static int nibble(byte[] plane, int index) {
        if (plane == null || (index >> 1) >= plane.length) {
            return 0;
        }
        int pair = plane[index >> 1] & 0xFF;
        return (index & 1) == 0 ? pair & 0xF : pair >> 4 & 0xF;
    }

    private static void setNibble(byte[] plane, int index, int value) {
        int pair = plane[index >> 1] & 0xFF;
        plane[index >> 1] = (byte) ((index & 1) == 0
                ? (pair & 0xF0) | value
                : (pair & 0x0F) | value << 4);
    }

    private static String string(CompoundTag schematic, String key) throws IOException {
        Tag tag = schematic.getValue().get(key);
        if (!(tag instanceof StringTag)) {
            throw new IOException("Tag '" + key + "' absent ou du mauvais type");
        }
        return ((StringTag) tag).getValue();
    }

    private static int unsignedShort(CompoundTag schematic, String key) throws IOException {
        Tag tag = schematic.getValue().get(key);
        if (!(tag instanceof ShortTag)) {
            throw new IOException("Tag '" + key + "' absent ou du mauvais type");
        }
        return ((ShortTag) tag).getValue() & 0xFFFF;
    }

    private static byte[] byteArray(CompoundTag schematic, String key) throws IOException {
        Tag tag = schematic.getValue().get(key);
        if (!(tag instanceof ByteArrayTag)) {
            throw new IOException("Tag '" + key + "' absent ou du mauvais type");
        }
        return ((ByteArrayTag) tag).getValue();
    }

    private static byte[] optionalByteArray(CompoundTag schematic, String key) {
        Tag tag = schematic.getValue().get(key);
        return tag instanceof ByteArrayTag ? ((ByteArrayTag) tag).getValue() : null;
    }

    private static int optionalInt(CompoundTag schematic, String key) {
        Tag tag = schematic.getValue().get(key);
        return tag instanceof IntTag ? ((IntTag) tag).getValue() : 0;
    }
}
