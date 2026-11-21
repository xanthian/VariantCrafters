package net.xanthian.variantcrafters;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.xanthian.variantcrafters.block.CrafterVariants;

import java.util.ArrayList;
import java.util.Comparator;

public final class CrafterItemGroup {
    public static final Identifier ID = Identifier.of(VariantCrafters.MOD_ID, VariantCrafters.MOD_ID);
    public static final Identifier BACKGROUND = Identifier.of(VariantCrafters.MOD_ID,
            "textures/gui/container/creative_inventory/tab_variantcrafters.png");

    private CrafterItemGroup() {
    }

    public static void register() {
        Registry.register(Registries.ITEM_GROUP, ID,
                FabricItemGroup.builder()
                        .displayName(Text.literal("Variant Crafters"))
                        .texture(BACKGROUND)
                        .noRenderedName()
                        .icon(() -> Registries.ITEM.getOrEmpty(Identifier.of(VariantCrafters.MOD_ID, "mangrove_crafter"))
                                .orElse(Items.CRAFTER).getDefaultStack())
                        .entries((context, entries) -> {
                            var crafters = new ArrayList<ItemStack>();
                            crafters.add(Items.CRAFTER.getDefaultStack());
                            CrafterVariants.variants().forEach(variant -> crafters.add(new ItemStack(variant.block())));
                            crafters.sort(Comparator.comparing((ItemStack stack) -> stack.getName().getString(),
                                            String.CASE_INSENSITIVE_ORDER)
                                    .thenComparing(stack -> Registries.ITEM.getId(stack.getItem())));
                            crafters.forEach(entries::add);
                        })
                        .build());
    }
}
