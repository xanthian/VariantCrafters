package net.xanthian.variantcrafters.block;

import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityType;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.CrafterBlock;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.xanthian.variantcrafters.Initialise;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class DynamicCrafters {
    public static final String TABLES_MOD_ID = "variantcraftingtables";
    public record Variant(Identifier id, Identifier table, Identifier tableItem, CrafterBlock block) {
        public String displayName() {
            return TableNames.woodLabel(table, Registries.BLOCK.get(table).getTranslationKey()) + " Crafter";
        }
    }

    private static final Map<Block, Identifier> pending = new IdentityHashMap<>();
    private static final Map<Identifier, Variant> variants = new TreeMap<>();

    private DynamicCrafters() {
    }

    public static Collection<Variant> variants() {
        return List.copyOf(variants.values());
    }

    public static void register() {
        RegistryEntryAddedCallback.event(Registries.BLOCK).register((rawId, id, block) -> {
            Identifier ingredient = pending.remove(block);
            if (ingredient != null) {
                registerCandidate(id, ingredient);
            }
        });
        RegistryEntryAddedCallback.event(Registries.ITEM).register((rawId, id, item) -> consider(item, id));
        // Snapshot existing items because discovery can register new items during iteration.
        Registries.ITEM.stream().toList().forEach(item -> consider(item, Registries.ITEM.getId(item)));
    }

    private static void consider(Item item, Identifier ingredient) {
        if (!(item instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof CraftingTableBlock)) {
            return;
        }
        Block block = blockItem.getBlock();
        Registries.BLOCK.getKey(block).ifPresentOrElse(
                key -> registerCandidate(key.getValue(), ingredient),
                () -> pending.putIfAbsent(block, ingredient));
    }

    private static void registerCandidate(Identifier table, Identifier tableItem) {
        if (!table.getNamespace().equals(TABLES_MOD_ID)) {
            return;
        }

        String path = table.getPath();
        if (path.equals("oak_crafting_table")) {
            return;
        }
        String suffix = "_crafting_table";
        String crafterPath = path.endsWith(suffix)
                ? path.substring(0, path.length() - suffix.length()) + "_crafter"
                : path + "/crafter";
        Identifier id = Identifier.of(Initialise.MOD_ID, crafterPath);
        if (Registries.BLOCK.containsId(id) || Registries.ITEM.containsId(id)) {
            return;
        }

        var block = new CrafterBlock(AbstractBlock.Settings.copy(Blocks.CRAFTER));
        Registry.register(Registries.BLOCK, id, block);
        // Keep the vanilla block entity ID for existing worlds and vanilla crafter behaviour.
        ((FabricBlockEntityType) BlockEntityType.CRAFTER).addSupportedBlock(block);
        Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));

        variants.put(id, new Variant(id, table, tableItem, block));
    }
}
