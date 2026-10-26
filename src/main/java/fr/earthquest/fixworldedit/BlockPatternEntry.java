package fr.earthquest.fixworldedit;

public class BlockPatternEntry {
    public final int id;
    public final byte data;
    public final double weight;

    public BlockPatternEntry(int id, byte data, double weight) {
        this.id = id;
        this.data = data;
        this.weight = weight;
    }
}
