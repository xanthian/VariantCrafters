package net.xanthian.variantcrafters.client;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.texture.atlas.AtlasSource;
import net.minecraft.client.texture.atlas.AtlasSourceType;
import net.minecraft.client.resource.metadata.AnimationResourceMetadata;
import net.minecraft.item.Item;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.xanthian.variantcrafters.Initialise;
import net.xanthian.variantcrafters.block.DynamicCrafters;
import net.xanthian.variantcrafters.block.TableNames;
import net.xanthian.variantcrafters.resource.GeneratedCrafterPack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CrafterSpriteSource implements AtlasSource {
    public static final AtlasSourceType TYPE = new AtlasSourceType(MapCodec.unit(CrafterSpriteSource::new));

    private static final List<String> FACES = List.of("east", "east_crafting", "east_triggered", "north",
            "north_crafting", "south", "south_triggered", "west", "west_crafting", "west_triggered");

    @Override
    public AtlasSourceType getType() {
        return TYPE;
    }

    @Override
    public void load(ResourceManager resources, SpriteRegions regions) {
        var variants = DynamicCrafters.variants();
        if (variants.isEmpty()) {
            SourceModTooltip.updateSources(Map.of());
            return;
        }

        Map<String, Resource> templates = new HashMap<>();
        Map<Item, String> woodSources = new HashMap<>();
        for (var variant : variants) {
            Identifier source = TableNames.sourcePlanks(variant.table());
            Item item = variant.block().asItem();
            if (source != null) {
                woodSources.put(item, source.getNamespace());
            }
            BufferedImage planks;
            try {
                var wood = source == null
                        ? TableTextureResolver.resolve(resources, variant.table())
                        : TableTextureResolver.resolvePlanks(resources, source);
                woodSources.putIfAbsent(item, wood.id().getNamespace());
                planks = readPlankFrame(wood.resource());
            } catch (IOException | RuntimeException exception) {
                LogUtils.getLogger().warn("Could not read the plank texture for {}. Using oak.", variant.table(), exception);
                planks = null;
            }
            for (String face : FACES) {
                Identifier sprite = Identifier.of(Initialise.MOD_ID, GeneratedCrafterPack.texturePath(variant.id(), face));
                var override = resources.getResource(RESOURCE_FINDER.toResourcePath(sprite));
                if (override.isPresent()) {
                    regions.add(sprite, override.get());
                    continue;
                }
                var template = templates.computeIfAbsent(face, key -> vanillaSprite(resources, key));
                if (planks == null) {
                    regions.add(sprite, template);
                    continue;
                }
                BufferedImage plankTexture = planks;
                regions.add(sprite, opener -> {
                    try {
                        return opener.loadSprite(sprite, applyPlanks(template, plankTexture));
                    } catch (IOException | RuntimeException exception) {
                        LogUtils.getLogger().warn("Could not recolor crafter sprite {}. Using oak.", sprite, exception);
                        return opener.loadSprite(sprite, template);
                    }
                });
            }
        }
        SourceModTooltip.updateSources(woodSources);
    }

    private static Resource vanillaSprite(ResourceManager resources, String face) {
        Identifier file = RESOURCE_FINDER.toResourcePath(Identifier.ofVanilla("block/crafter_" + face));
        return resources.getAllResources(file).stream()
                .filter(resource -> resource.getPackId().equals("vanilla"))
                .findFirst()
                .orElseGet(() -> resources.getResource(file).orElseThrow());
    }

    private static BufferedImage readPlankFrame(Resource resource) throws IOException {
        var image = read(resource);
        var animation = resource.getMetadata().decode(AnimationResourceMetadata.READER);
        if (animation.isEmpty()) {
            return image;
        }
        var size = animation.get().getSize(image.getWidth(), image.getHeight());
        return image.getSubimage(0, 0, size.width(), size.height());
    }

    private static Resource applyPlanks(Resource template, BufferedImage planks) throws IOException {
        var image = CrafterTexture.compose(read(template), planks);
        var output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) {
            throw new IOException("PNG encoder unavailable");
        }
        byte[] bytes = output.toByteArray();
        return new Resource(template.getPack(), () -> new ByteArrayInputStream(bytes), template::getMetadata);
    }

    private static BufferedImage read(Resource resource) throws IOException {
        try (var stream = resource.getInputStream()) {
            BufferedImage image = ImageIO.read(stream);
            if (image == null) {
                throw new IOException("Invalid texture from " + resource.getPackId());
            }
            return image;
        }
    }
}
