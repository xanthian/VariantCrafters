package net.xanthian.variantcrafters.client;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.xanthian.variantcrafters.resource.GeneratedCrafterPack;

import java.util.HashMap;
import java.util.Map;

final class SourceModTooltip {
    private static final Text MINECRAFT = format("Minecraft");
    private static volatile Map<Item, Text> sourceNames = Map.of();

    private SourceModTooltip() {
    }

    static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            Text source = stack.isOf(Items.CRAFTER) ? MINECRAFT : sourceNames.get(stack.getItem());
            if (source != null) {
                lines.add(Math.min(1, lines.size()), source.copy());
            }
        });
    }

    static void updateSources(Map<Item, String> namespaces) {
        Map<Item, Text> names = new HashMap<>();
        namespaces.forEach((item, namespace) -> {
            String name = FabricLoader.getInstance().getModContainer(namespace)
                    .map(mod -> mod.getMetadata().getName()).orElse(namespace);
            names.put(item, format(name));
        });
        sourceNames = Map.copyOf(names);
    }

    private static Text format(String name) {
        return Text.literal(name).formatted(Formatting.GRAY, Formatting.ITALIC)
                .styled(style -> style.withFont(GeneratedCrafterPack.SOURCE_MOD_FONT_ID));
    }
}
