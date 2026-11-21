package net.xanthian.variantcrafters;

import net.fabricmc.api.ModInitializer;
import net.xanthian.variantcrafters.block.CrafterVariants;

public final class VariantCrafters implements ModInitializer {
    public static final String MOD_ID = "variantcrafters";

    @Override
    public void onInitialize() {
        CrafterVariants.register();
        CrafterItemGroup.register();
        CrafterLineupCommand.register();
    }
}
