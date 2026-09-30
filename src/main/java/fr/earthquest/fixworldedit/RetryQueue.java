package fr.earthquest.fixworldedit;

import java.util.ArrayList;
import java.util.List;

final class RetryQueue {
    private static final int SEGMENT = 1 << 14;
    private static final int FIELDS = 5;

    private static final int MAX = 2000000;

    private final List<int[]> segments = new ArrayList<int[]>();
    private int size;
    private boolean overflowed;

    void add(int x, int y, int z, int id, int data) {
        if (size >= MAX) {
            overflowed = true;
            return;
        }
        int offset = size % SEGMENT;
        if (offset == 0) {
            segments.add(new int[SEGMENT * FIELDS]);
        }
        int[] slot = segments.get(size / SEGMENT);
        slot[offset * FIELDS] = x;
        slot[offset * FIELDS + 1] = y;
        slot[offset * FIELDS + 2] = z;
        slot[offset * FIELDS + 3] = id;
        slot[offset * FIELDS + 4] = data;
        size++;
    }

    int size() {
        return size;
    }

    boolean isEmpty() {
        return size == 0;
    }

    boolean hasOverflowed() {
        return overflowed;
    }

    int x(int index) {
        return field(index, 0);
    }

    int y(int index) {
        return field(index, 1);
    }

    int z(int index) {
        return field(index, 2);
    }

    int id(int index) {
        return field(index, 3);
    }

    byte data(int index) {
        return (byte) field(index, 4);
    }

    private int field(int index, int which) {
        return segments.get(index / SEGMENT)[(index % SEGMENT) * FIELDS + which];
    }
}
