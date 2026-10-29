package fr.earthquest.fixworldedit;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.EditSessionFactory;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extent.inventory.BlockBag;
import com.sk89q.worldedit.world.World;
import java.lang.reflect.Field;

/**
 * Fait passer toutes les EditSession de WorldEdit par un ExtendedWorld. WorldEdit 6 a neutralise
 * setEditSessionFactory (il se contente de journaliser), d'ou le remplacement par reflexion.
 */
final class ExtendedEditSessionFactory extends EditSessionFactory {
    private static final String FIELD = "editSessionFactory";

    private final EditSessionFactory delegate;
    private final NativeBlocks natives;
    private final ServerBlockRegistry registry;

    private ExtendedEditSessionFactory(EditSessionFactory delegate, NativeBlocks natives, ServerBlockRegistry registry) {
        this.delegate = delegate;
        this.natives = natives;
        this.registry = registry;
    }

    static ExtendedEditSessionFactory install(WorldEdit worldEdit, NativeBlocks natives, ServerBlockRegistry registry)
            throws ReflectiveOperationException {
        Field field = field();
        EditSessionFactory current = (EditSessionFactory) field.get(worldEdit);
        ExtendedEditSessionFactory installed = new ExtendedEditSessionFactory(current, natives, registry);
        field.set(worldEdit, installed);
        return installed;
    }

    void uninstall(WorldEdit worldEdit) throws ReflectiveOperationException {
        Field field = field();
        if (field.get(worldEdit) == this) {
            field.set(worldEdit, delegate);
        }
    }

    private static Field field() throws NoSuchFieldException {
        Field field = WorldEdit.class.getDeclaredField(FIELD);
        field.setAccessible(true);
        return field;
    }

    private World wrap(World world) {
        return (World) ExtendedWorld.wrap(world, natives, registry);
    }

    @Override
    public EditSession getEditSession(World world, int maxBlocks) {
        return delegate.getEditSession(wrap(world), maxBlocks);
    }

    @Override
    public EditSession getEditSession(World world, int maxBlocks, Player player) {
        return delegate.getEditSession(wrap(world), maxBlocks, player);
    }

    @Override
    public EditSession getEditSession(World world, int maxBlocks, BlockBag blockBag) {
        return delegate.getEditSession(wrap(world), maxBlocks, blockBag);
    }

    @Override
    public EditSession getEditSession(World world, int maxBlocks, BlockBag blockBag, Player player) {
        return delegate.getEditSession(wrap(world), maxBlocks, blockBag, player);
    }
}
