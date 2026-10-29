package fr.earthquest.fixworldedit;

import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.internal.registry.AbstractFactory;
import com.sk89q.worldedit.internal.registry.InputParser;
import java.lang.reflect.Field;
import java.util.List;

/**
 * Les masques de blocs (//replace stone ..., //gmask, masques de brosse) recopient chaque bloc lu
 * en new BaseBlock(id, data) et echouent donc sur un ID etendu. Ce parseur, place en tete de la
 * MaskFactory, leur donne un monde ou les blocs etendus apparaissent sous un ID de substitution
 * qui n'est pas un bloc du serveur : ils ne correspondent a aucun bloc nomme, mais bien a !air.
 */
final class ExtendedMaskParser extends InputParser<Mask> {
    private final List<InputParser<Mask>> parsers;
    private final NativeBlocks natives;
    private final ServerBlockRegistry registry;
    private final int standIn;

    private ExtendedMaskParser(WorldEdit worldEdit, List<InputParser<Mask>> parsers,
                               NativeBlocks natives, ServerBlockRegistry registry) {
        super(worldEdit);
        this.parsers = parsers;
        this.natives = natives;
        this.registry = registry;
        this.standIn = standIn(registry);
    }

    static ExtendedMaskParser install(WorldEdit worldEdit, NativeBlocks natives, ServerBlockRegistry registry)
            throws ReflectiveOperationException {
        List<InputParser<Mask>> parsers = parsers(worldEdit);
        ExtendedMaskParser parser = new ExtendedMaskParser(worldEdit, parsers, natives, registry);
        parsers.add(0, parser);
        return parser;
    }

    void uninstall() {
        parsers.remove(this);
    }

    @SuppressWarnings("unchecked")
    private static List<InputParser<Mask>> parsers(WorldEdit worldEdit) throws ReflectiveOperationException {
        Field field = AbstractFactory.class.getDeclaredField("parsers");
        field.setAccessible(true);
        return (List<InputParser<Mask>>) field.get(worldEdit.getMaskFactory());
    }

    private static int standIn(ServerBlockRegistry registry) {
        for (int id = ServerBlockRegistry.VANILLA_MAX_ID; id > 0; id--) {
            if (!registry.isBlock(id)) {
                return id;
            }
        }
        return ServerBlockRegistry.VANILLA_MAX_ID;
    }

    int standIn() {
        return standIn;
    }

    @Override
    public Mask parseFromInput(String input, ParserContext context) throws InputParseException {
        Extent extent = context.getExtent();
        if (extent == null || extent instanceof StandInExtent) {
            return null;
        }
        ParserContext safe = new ParserContext(context);
        safe.setExtent(new StandInExtent(ExtendedWorld.wrap(extent, natives, registry), standIn));
        for (InputParser<Mask> parser : parsers) {
            if (parser == this) {
                continue;
            }
            Mask mask = parser.parseFromInput(input, safe);
            if (mask != null) {
                return mask;
            }
        }
        return null;
    }

    static final class StandInExtent extends AbstractDelegateExtent {
        private final int standIn;

        StandInExtent(Extent extent, int standIn) {
            super(extent);
            this.standIn = standIn;
        }

        @Override
        public BaseBlock getBlock(Vector position) {
            return standIn(super.getBlock(position));
        }

        @Override
        public BaseBlock getLazyBlock(Vector position) {
            return standIn(super.getLazyBlock(position));
        }

        private BaseBlock standIn(BaseBlock block) {
            return block.getId() > ServerBlockRegistry.VANILLA_MAX_ID ? new BaseBlock(standIn, block.getData()) : block;
        }
    }
}
