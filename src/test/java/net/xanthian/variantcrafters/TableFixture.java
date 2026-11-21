package net.xanthian.variantcrafters;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class TableFixture implements ModInitializer {
    public static final Identifier TABLE = Identifier.of("variantcraftingtables", "fixture_crafting_table");
    public static final Identifier FOREIGN_TABLE = Identifier.of("variantcrafters_test", "foreign_crafting_table");
    public static final Identifier LOOSE_PLANKS = Identifier.of("variantcrafters_test", "loose_planks");

    @Override
    public void onInitialize() {
        var table = new CraftingTableBlock(AbstractBlock.Settings.copy(Blocks.CRAFTING_TABLE));
        Registry.register(Registries.ITEM, TABLE, new BlockItem(table, new Item.Settings()));
        Registry.register(Registries.BLOCK, TABLE, table);

        var foreignTable = new CraftingTableBlock(AbstractBlock.Settings.copy(Blocks.CRAFTING_TABLE));
        Registry.register(Registries.BLOCK, FOREIGN_TABLE, foreignTable);
        Registry.register(Registries.ITEM, FOREIGN_TABLE, new BlockItem(foreignTable, new Item.Settings()));

        var planks = new Block(AbstractBlock.Settings.copy(Blocks.OAK_PLANKS));
        Registry.register(Registries.BLOCK, LOOSE_PLANKS, planks);
        Registry.register(Registries.ITEM, LOOSE_PLANKS, new BlockItem(planks, new Item.Settings()));
    }
}
