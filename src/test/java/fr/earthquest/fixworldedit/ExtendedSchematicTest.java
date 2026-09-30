package fr.earthquest.fixworldedit;

import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.IntTag;
import com.sk89q.jnbt.StringTag;
import com.sk89q.jnbt.Tag;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.world.registry.LegacyWorldData;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class ExtendedSchematicTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private ExtendedClipboard clipboard(int... ids) {
        short[] values = new short[ids.length];
        byte[] data = new byte[ids.length];
        for (int i = 0; i < ids.length; i++) {
            values[i] = (short) ids[i];
            data[i] = (byte) (i % 16);
        }
        return new ExtendedClipboard(ids.length, 1, 1, values, data, 3, -2, 7,
                new ArrayList<CompoundTag>(), new HashMap<String, Integer>());
    }

    @Test
    public void writesTheExtendedProfileOnlyWhenNeeded() throws IOException {
        File alpha = folder.newFile("alpha.schematic");
        ExtendedSchematic.write(alpha, clipboard(1, 35, 4095));
        assertEquals(ExtendedSchematic.ALPHA, ExtendedSchematic.peek(alpha).materials);

        File extended = folder.newFile("extended.schematic");
        ExtendedSchematic.write(extended, clipboard(1, 5016, 4095));
        assertEquals(ExtendedSchematic.EXTENDED, ExtendedSchematic.peek(extended).materials);
    }

    @Test
    public void keepsIdsDataAndOffsetThroughARoundTrip() throws IOException {
        File file = folder.newFile("round.schematic");
        ExtendedSchematic.write(file, clipboard(0, 5016, 4095, 32767, 256, 12));
        ExtendedClipboard read = ExtendedSchematic.read(file);
        assertEquals(6, read.width);
        assertEquals(1, read.height);
        assertEquals(1, read.length);
        assertArrayEquals(new int[] {0, 5016, 4095, 32767, 256, 12},
                new int[] {read.idAt(0), read.idAt(1), read.idAt(2), read.idAt(3), read.idAt(4), read.idAt(5)});
        for (int i = 0; i < 6; i++) {
            assertEquals((byte) (i % 16), read.dataAt(i));
        }
        assertEquals(3, read.offsetX);
        assertEquals(-2, read.offsetY);
        assertEquals(7, read.offsetZ);
        assertTrue(read.hasExtendedIds());
    }

    @Test
    public void worldEditReadsTheAlphaProfileWrittenHere() throws Exception {
        File file = folder.newFile("worldedit.schematic");
        ExtendedSchematic.write(file, clipboard(1, 4095, 256, 35, 0, 17));
        try (FileInputStream stream = new FileInputStream(file)) {
            ClipboardReader reader = ClipboardFormat.SCHEMATIC.getReader(stream);
            Clipboard clipboard = reader.read(LegacyWorldData.getInstance());
            Vector min = clipboard.getRegion().getMinimumPoint();
            int[] ids = new int[6];
            for (int x = 0; x < 6; x++) {
                ids[x] = clipboard.getBlock(min.add(x, 0, 0)).getId();
            }
            assertArrayEquals(new int[] {1, 4095, 256, 35, 0, 17}, ids);
        }
    }

    @Test
    public void readsTileEntitiesAndMapping() throws IOException {
        Map<String, Tag> sign = new HashMap<>();
        sign.put("id", new StringTag("Sign"));
        sign.put("x", new IntTag(1));
        sign.put("y", new IntTag(0));
        sign.put("z", new IntTag(0));
        List<CompoundTag> tiles = new ArrayList<>(Arrays.asList(new CompoundTag(sign)));
        Map<String, Integer> mapping = new HashMap<>();
        mapping.put("questblock:pierre_taillee", 5016);
        short[] ids = {1, (short) 5016};
        File file = folder.newFile("tiles.schematic");
        ExtendedSchematic.write(file, new ExtendedClipboard(2, 1, 1, ids, new byte[2], 0, 0, 0, tiles, mapping));

        ExtendedClipboard read = ExtendedSchematic.read(file);
        assertEquals(1, read.tiles.size());
        assertEquals("Sign", read.tiles.get(0).getString("id"));
        assertEquals(Integer.valueOf(5016), read.mapping.get("questblock:pierre_taillee"));
    }

    @Test
    public void remapRewritesIdsAndMapping() {
        Map<String, Integer> mapping = new HashMap<>();
        mapping.put("questblock:pierre_taillee", 5016);
        ExtendedClipboard source = new ExtendedClipboard(2, 1, 1, new short[] {1, (short) 5016}, new byte[2],
                0, 0, 0, new ArrayList<CompoundTag>(), mapping);
        Map<Integer, Integer> replacements = new HashMap<>();
        replacements.put(5016, 6001);
        ExtendedClipboard remapped = source.remap(replacements);
        assertEquals(1, remapped.idAt(0));
        assertEquals(6001, remapped.idAt(1));
        assertEquals(Integer.valueOf(6001), remapped.mapping.get("questblock:pierre_taillee"));
    }

    @Test
    public void refusesAnUnknownProfile() throws IOException {
        File file = folder.newFile("sponge.schematic");
        ExtendedSchematic.write(file, clipboard(1, 2));
        File broken = folder.newFile("broken.schematic");
        java.nio.file.Files.write(broken.toPath(), new byte[] {1, 2, 3, 4});
        try {
            ExtendedSchematic.peek(broken);
            fail("un fichier non gzip doit être refusé");
        } catch (IOException expected) {
            assertNotNull(expected.getMessage());
        }
    }
}
