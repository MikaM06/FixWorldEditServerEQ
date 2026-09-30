package fr.earthquest.fixworldedit;

import java.lang.reflect.Method;
import org.junit.Test;

import static org.junit.Assert.*;

public class ReflectTest {
    public static class FakeBlock {
    }

    public static class McpNames {
        public static FakeBlock getBlockById(int id) {
            return id == 5058 ? new FakeBlock() : null;
        }

        public static int getIdFromBlock(FakeBlock block) {
            return 5058;
        }

        public static FakeBlock getBlockFromItem(Object item) {
            return null;
        }
    }

    public static class CraftBukkitNames {
        public static FakeBlock getById(int id) {
            return new FakeBlock();
        }

        public static int getId(FakeBlock block) {
            return 5058;
        }
    }

    public static class SrgNames {
        public static FakeBlock func_149729_e(int id) {
            return new FakeBlock();
        }

        public static int func_149682_b(FakeBlock block) {
            return 5058;
        }
    }

    @Test
    public void findsTheIdAccessorsUnderEveryNamingConvention() throws Exception {
        for (Class<?> owner : new Class<?>[] {McpNames.class, CraftBukkitNames.class, SrgNames.class}) {
            Method byId = Reflect.findStatic(owner, FakeBlock.class, Integer.TYPE);
            Method toId = Reflect.findStatic(owner, Integer.TYPE, FakeBlock.class);
            assertNotNull(owner.getSimpleName() + " : (int) -> Block introuvable", byId);
            assertNotNull(owner.getSimpleName() + " : (Block) -> int introuvable", toId);
            assertNotNull(byId.invoke(null, 5058));
            assertEquals(5058, ((Integer) toId.invoke(null, new FakeBlock())).intValue());
        }
    }

    @Test
    public void doesNotConfuseANeighbouringSignature() {
        Method byId = Reflect.findStatic(McpNames.class, FakeBlock.class, Integer.TYPE);
        assertEquals("getBlockById", byId.getName());
        assertNull(Reflect.findStatic(McpNames.class, FakeBlock.class, String.class));
        assertNull(Reflect.findStatic(McpNames.class, Void.TYPE, Integer.TYPE));
    }

    public static class WorldLikeNames {
        public boolean func_147465_d(int x, int y, int z, FakeBlock block, int meta, int flags) {
            return true;
        }

        public void func_147452_c(int x, int y, int z, FakeBlock block, int eventId, int param) {
        }
    }

    @Test
    public void tellsSetBlockFromAddBlockEventByItsReturnType() {
        Method placer = Reflect.find(WorldLikeNames.class, Boolean.TYPE,
                Integer.TYPE, Integer.TYPE, Integer.TYPE, FakeBlock.class, Integer.TYPE, Integer.TYPE);
        assertNotNull("la pose native est introuvable", placer);
        assertEquals("func_147465_d", placer.getName());

        Method event = Reflect.find(WorldLikeNames.class, Void.TYPE,
                Integer.TYPE, Integer.TYPE, Integer.TYPE, FakeBlock.class, Integer.TYPE, Integer.TYPE);
        assertNotNull(event);
        assertEquals("func_147452_c", event.getName());
    }

    @Test
    public void ignoresInstanceMethodsWhenLookingForStaticOnes() {
        assertNull(Reflect.findStatic(Holder.class, String.class));
        assertNotNull(Reflect.find(Holder.class, String.class));
    }

    public static class Holder {
        public String value() {
            return "x";
        }
    }
}
