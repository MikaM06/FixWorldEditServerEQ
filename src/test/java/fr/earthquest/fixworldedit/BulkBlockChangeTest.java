package fr.earthquest.fixworldedit;

import org.junit.Test;

import static org.junit.Assert.*;

public class BulkBlockChangeTest {
    private static final int ACROSS_SEGMENTS = (1 << 16) + 1;

    private BulkBlockChange empty() {
        return new BulkBlockChange(null, null, null);
    }

    @Test
    public void countsEveryBlockAcrossTheSegmentBoundary() {
        BulkBlockChange history = empty();
        assertTrue(history.isEmpty());

        for (int i = 0; i < ACROSS_SEGMENTS; i++) {
            history.add(i, 64, -i, 5058, (byte) (i % 16), 0, (byte) 0);
        }
        assertEquals(ACROSS_SEGMENTS, history.size());
        assertFalse(history.isEmpty());
    }

    @Test
    public void reportsMemoryBySegment() {
        BulkBlockChange history = empty();
        assertEquals(0L, history.memoryBytes());

        history.add(0, 0, 0, 1, (byte) 0, 0, (byte) 0);
        long oneSegment = history.memoryBytes();
        assertEquals(16L * (1 << 16), oneSegment);

        for (int i = 1; i < ACROSS_SEGMENTS; i++) {
            history.add(i, 0, 0, 1, (byte) 0, 0, (byte) 0);
        }
        assertEquals(oneSegment * 2L, history.memoryBytes());
    }

    @Test
    public void staysAtSixteenBytesPerBlock() {
        BulkBlockChange history = empty();
        for (int i = 0; i < (1 << 16); i++) {
            history.add(i, 0, 0, 1, (byte) 0, 0, (byte) 0);
        }
        assertEquals(16L, history.memoryBytes() / history.size());
    }
}
