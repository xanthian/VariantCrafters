package net.xanthian.variantcrafters.mixin;

import net.minecraft.resource.LifecycledResourceManagerImpl;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourceType;
import net.xanthian.variantcrafters.block.CrafterVariants;
import net.xanthian.variantcrafters.resource.GeneratedCrafterPack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

@Mixin(LifecycledResourceManagerImpl.class)
public abstract class LifecycledResourceManagerImplMixin {
    @ModifyVariable(method = "<init>(Lnet/minecraft/resource/ResourceType;Ljava/util/List;)V",
            at = @At("HEAD"), argsOnly = true)
    private static List<ResourcePack> variantcrafters$addGeneratedPack(List<ResourcePack> packs, ResourceType type) {
        var variants = CrafterVariants.variants();
        if (variants.isEmpty() && type == ResourceType.SERVER_DATA) {
            return packs;
        }
        var result = new ArrayList<>(packs);
        int position = 0;
        for (int index = 0; index < packs.size(); index++) {
            if ("vanilla".equals(packs.get(index).getId())) {
                position = index + 1;
                break;
            }
        }
        result.add(position, new GeneratedCrafterPack(type, variants));
        return result;
    }
}
