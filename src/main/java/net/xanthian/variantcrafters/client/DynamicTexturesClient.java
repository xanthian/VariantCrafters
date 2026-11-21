package net.xanthian.variantcrafters.client;

import net.fabricmc.api.ClientModInitializer;
import net.xanthian.variantcrafters.mixin.AtlasSourceManagerAccessor;
import net.xanthian.variantcrafters.resource.GeneratedCrafterPack;

public final class DynamicTexturesClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AtlasSourceManagerAccessor.variantcrafters$getSourceTypes()
                .put(GeneratedCrafterPack.ATLAS_SOURCE_ID, CrafterSpriteSource.TYPE);
        SourceModTooltip.register();
    }
}
