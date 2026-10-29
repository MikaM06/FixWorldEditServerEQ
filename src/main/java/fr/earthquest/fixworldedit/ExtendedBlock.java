package fr.earthquest.fixworldedit;

import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.blocks.BlockID;

/**
 * BaseBlock refuse tout ID au-dela de 4095 ; cette variante garde l'ID reel a cote du champ
 * d'origine (laisse a l'air) et le rend via getId(), sur lequel s'appuient getType(), equals()
 * et hashCode(). Le NBT natif de la tile entity n'est restitue qu'a sa position de capture.
 */
final class ExtendedBlock extends BaseBlock {
    private int id;
    private final Object tile;
    private final int tileX;
    private final int tileY;
    private final int tileZ;

    ExtendedBlock(int id, int data, Object tile, int tileX, int tileY, int tileZ) {
        super(BlockID.AIR, data);
        this.id = id;
        this.tile = tile;
        this.tileX = tileX;
        this.tileY = tileY;
        this.tileZ = tileZ;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public void setId(int id) {
        if (id < 0) {
            throw new IllegalArgumentException("Can't have a block ID below 0");
        }
        this.id = id;
    }

    Object tileAt(int x, int y, int z) {
        return x == tileX && y == tileY && z == tileZ ? tile : null;
    }
}
