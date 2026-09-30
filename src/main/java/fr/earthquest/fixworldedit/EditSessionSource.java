package fr.earthquest.fixworldedit;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.world.World;

public interface EditSessionSource {
    EditSession create(World world, int blockLimit);
}
