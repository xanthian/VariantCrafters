package net.xanthian.variantcrafters;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.xanthian.variantcrafters.block.CrafterVariants;

public final class VariantCrafters implements ModInitializer {
    public static final String MOD_ID = "variantcrafters";

    @Override
    public void onInitialize() {
        if (!FabricLoader.getInstance().isModLoaded(CrafterVariants.TABLES_MOD_ID)) {
            throw new IllegalStateException("Variant Crafters requires Variant Crafting Tables. "
                    + "Install the Fabric version for your Minecraft version before launching.");
        }
        CrafterVariants.register();
        CrafterItemGroup.register();
        CrafterLineupCommand.register();
    }
}
