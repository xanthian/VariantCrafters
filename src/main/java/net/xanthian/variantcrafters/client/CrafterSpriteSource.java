package net.xanthian.variantcrafters.client;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.metadata.AnimationResourceMetadata;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.client.texture.SpriteOpener;
import net.minecraft.client.texture.atlas.AtlasSource;
import net.minecraft.client.texture.atlas.AtlasSourceType;
import net.minecraft.item.Item;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.xanthian.variantcrafters.block.CrafterVariants;
import net.xanthian.variantcrafters.block.CraftingTableInfo;
import net.xanthian.variantcrafters.resource.GeneratedCrafterPack;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CrafterSpriteSource implements AtlasSource {
    public static final AtlasSourceType TYPE = new AtlasSourceType(MapCodec.unit(CrafterSpriteSource::new));

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<String> FACES = List.of("east", "east_crafting", "east_triggered", "north",
            "north_crafting", "south", "south_triggered", "west", "west_crafting", "west_triggered");

    private record Template(Resource resource, BufferedImage image) {
    }

    @Override
    public AtlasSourceType getType() {
        return TYPE;
    }

    @Override
    public void load(ResourceManager resources, SpriteRegions regions) {
        var variants = CrafterVariants.variants();
        if (variants.isEmpty()) {
            SourceModTooltip.updateSources(Map.of());
            return;
        }

        Map<String, Optional<Template>> templates = new HashMap<>();
        Map<Item, String> woodSources = new HashMap<>();
        for (var variant : variants) {
            Identifier source = CraftingTableInfo.sourcePlanks(variant.table());
            Item item = variant.block().asItem();
            BufferedImage planks = loadPlanks(resources, variant.table(), source, item, woodSources);
            for (String face : FACES) {
                Identifier sprite = variant.id().withPath(GeneratedCrafterPack.texturePath(variant.id(), face));
                Resource override = resources.getResource(RESOURCE_FINDER.toResourcePath(sprite)).orElse(null);
                Template template = templates.computeIfAbsent(face, key -> loadTemplate(resources, key)).orElse(null);
                regions.add(sprite, opener -> openSprite(opener, sprite, override, template, planks));
            }
        }
        SourceModTooltip.updateSources(woodSources);
    }

    private static BufferedImage loadPlanks(ResourceManager resources, Identifier table, Identifier source, Item item,
                                            Map<Item, String> woodSources) {
        if (source != null) {
            woodSources.put(item, source.getNamespace());
            try {
                return readPlankFrame(TableTextureResolver.resolvePlanks(resources, source).resource());
            } catch (IOException | RuntimeException exception) {
                LOGGER.debug("Could not read source planks {} for {}. Trying the table model.", source, table, exception);
            }
        }
        try {
            var wood = TableTextureResolver.resolve(resources, table);
            BufferedImage planks = readPlankFrame(wood.resource());
            woodSources.putIfAbsent(item, wood.id().getNamespace());
            return planks;
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not read the plank texture for {}. Using oak.", table, exception);
            return null;
        }
    }

    private static Optional<Template> loadTemplate(ResourceManager resources, String face) {
        Identifier file = RESOURCE_FINDER.toResourcePath(Identifier.ofVanilla("block/crafter_" + face));
        var vanilla = MinecraftClient.getInstance().getDefaultResourcePack();
        var stream = vanilla.open(ResourceType.CLIENT_RESOURCES, file);
        Resource resource = stream == null ? resources.getResource(file).orElse(null) : new Resource(vanilla, stream);
        if (resource == null) {
            LOGGER.warn("Crafter template texture {} is missing", file);
            return Optional.empty();
        }
        try {
            return Optional.of(new Template(resource, read(resource)));
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not read crafter template texture {}", file, exception);
            return Optional.empty();
        }
    }

    private static SpriteContents openSprite(SpriteOpener opener, Identifier sprite, Resource override,
                                              Template template, BufferedImage planks) {
        if (override != null) {
            try {
                SpriteContents contents = opener.loadSprite(sprite, override);
                if (contents != null) {
                    return contents;
                }
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not load crafter sprite override {}", sprite, exception);
            }
        }
        if (template == null) {
            return null;
        }
        if (planks != null) {
            try {
                SpriteContents contents = opener.loadSprite(sprite, applyPlanks(template, planks));
                if (contents != null) {
                    return contents;
                }
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn("Could not recolor crafter sprite {}. Using oak.", sprite, exception);
            }
        }
        return opener.loadSprite(sprite, template.resource());
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

    private static Resource applyPlanks(Template template, BufferedImage planks) throws IOException {
        var image = CrafterTexture.compose(template.image(), planks);
        var output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) {
            throw new IOException("PNG encoder unavailable");
        }
        byte[] bytes = output.toByteArray();
        return new Resource(template.resource().getPack(), () -> new ByteArrayInputStream(bytes),
                template.resource()::getMetadata);
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
