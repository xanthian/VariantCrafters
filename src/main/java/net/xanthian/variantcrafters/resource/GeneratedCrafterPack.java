package net.xanthian.variantcrafters.resource;

import com.google.gson.Gson;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackInfo;
import net.minecraft.resource.ResourcePackSource;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.metadata.ResourceMetadataReader;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.xanthian.variantcrafters.VariantCrafters;
import net.xanthian.variantcrafters.block.CrafterVariants.Variant;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class GeneratedCrafterPack implements ResourcePack {
    public static final Identifier ATLAS_SOURCE_ID = Identifier.of(VariantCrafters.MOD_ID, "crafter_variants");
    public static final Identifier SOURCE_MOD_FONT_ID = Identifier.of(VariantCrafters.MOD_ID, "source_mod");
    private static final Gson GSON = new Gson();
    private static final ResourcePackInfo INFO = new ResourcePackInfo("variantcrafters_generated",
            Text.literal("Generated Variant Crafters"), ResourcePackSource.BUILTIN, Optional.empty());

    private final ResourceType type;
    private final Map<Identifier, byte[]> files = new LinkedHashMap<>();
    private final Set<String> namespaces;

    public GeneratedCrafterPack(ResourceType type, Collection<Variant> variants) {
        this.type = type;
        if (type == ResourceType.CLIENT_RESOURCES) {
            Map<String, String> translations = new LinkedHashMap<>();
            translations.put("tag.item.variantcrafters.crafter_variants", "Crafter Variants");
            for (var variant : variants) {
                CrafterModels.models(variant.id()).forEach(this::put);
                translations.put(variant.block().getTranslationKey(), variant.displayName());
            }
            put(Identifier.of(VariantCrafters.MOD_ID, "lang/en_us.json"), translations);
            put(Identifier.ofVanilla("lang/en_us.json"), Map.of("block.minecraft.crafter", "Oak Crafter"));
            put(Identifier.ofVanilla("atlases/blocks.json"), Map.of("sources", List.of(Map.of("type", ATLAS_SOURCE_ID.toString()))));
            putSourceModFont();
        } else {
            variants.forEach(this::addData);
            var values = variants.stream().map(variant -> variant.id().toString()).toList();
            var tag = Map.of("replace", false, "values", values);
            put(Identifier.ofVanilla("tags/block/mineable/pickaxe.json"), tag);
            put(Identifier.ofVanilla("tags/block/needs_stone_tool.json"), tag);
            Identifier variantsTag = Identifier.of(VariantCrafters.MOD_ID, "crafter_variants");
            put(file(variantsTag, "tags/item/"), tag);

            Identifier conversion = Identifier.of(VariantCrafters.MOD_ID, "oak_crafter_from_variant");
            put(file(conversion, "recipe/"), Map.of(
                    "type", "minecraft:crafting_shapeless", "category", "redstone",
                    "ingredients", List.of(Map.of("tag", variantsTag.toString())),
                    "result", Map.of("id", "minecraft:crafter")));
            addRecipeUnlock(conversion, "#" + variantsTag);
        }
        namespaces = files.keySet().stream().map(Identifier::getNamespace).collect(Collectors.toUnmodifiableSet());
    }

    private void putSourceModFont() {
        // ASCII glyphs occupy their code point's cell in Minecraft's 16 by 16 bitmap.
        List<String> rows = new ArrayList<>(16);
        for (int row = 0; row < 16; row++) {
            char[] glyphs = new char[16];
            for (int column = 0; column < 16; column++) {
                int codePoint = row * 16 + column;
                if (codePoint >= '!' && codePoint <= '~') {
                    glyphs[column] = (char) codePoint;
                }
            }
            rows.add(new String(glyphs));
        }
        put(file(SOURCE_MOD_FONT_ID, "font/"), Map.of("providers", List.of(
                Map.of("type", "space", "advances", Map.of(" ", 3)),
                Map.of("type", "bitmap", "file", "minecraft:font/ascii.png", "height", 6, "ascent", 5, "chars", rows),
                Map.of("type", "reference", "id", "minecraft:default"))));
    }

    public static String texturePath(Identifier crafter, String face) {
        return "block/crafter/generated/" + crafter.getPath() + "/" + face;
    }

    private void addData(Variant variant) {
        Identifier id = variant.id();
        String item = id.toString();
        String table = variant.tableItem().toString();
        put(file(id, "recipe/"), Map.of(
                "type", "minecraft:crafting_shaped", "category", "redstone", "group", VariantCrafters.MOD_ID,
                "pattern", List.of("III", "ITI", "RDR"),
                "key", Map.of("I", Map.of("item", "minecraft:iron_ingot"),
                        "T", Map.of("item", table),
                        "R", Map.of("item", "minecraft:redstone"),
                        "D", Map.of("item", "minecraft:dropper")),
                "result", Map.of("id", item, "count", 1)));
        addRecipeUnlock(id, table);
        put(file(id, "loot_table/blocks/"), Map.of("type", "minecraft:block", "pools", List.of(Map.of(
                "rolls", 1, "conditions", List.of(Map.of("condition", "minecraft:survives_explosion")),
                "entries", List.of(Map.of("type", "minecraft:item", "name", item))))));
    }

    private void addRecipeUnlock(Identifier recipe, String ingredient) {
        put(file(recipe, "advancement/recipes/redstone/"), Map.of(
                "parent", "minecraft:recipes/root",
                "criteria", Map.of(
                        "has_ingredient", Map.of("trigger", "minecraft:inventory_changed",
                                "conditions", Map.of("items", List.of(Map.of("items", ingredient)))),
                        "has_the_recipe", Map.of("trigger", "minecraft:recipe_unlocked",
                                "conditions", Map.of("recipe", recipe.toString()))),
                "requirements", List.of(List.of("has_ingredient", "has_the_recipe")),
                "rewards", Map.of("recipes", List.of(recipe.toString()))));
    }

    private static Identifier file(Identifier variant, String directory) {
        return variant.withPath(directory + variant.getPath() + ".json");
    }

    private void put(Identifier id, Object json) {
        files.put(id, GSON.toJson(json).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public InputSupplier<InputStream> openRoot(String... segments) {
        return null;
    }

    @Override
    public InputSupplier<InputStream> open(ResourceType type, Identifier id) {
        byte[] bytes = this.type == type ? files.get(id) : null;
        return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
    }

    @Override
    public void findResources(ResourceType type, String namespace, String prefix, ResultConsumer consumer) {
        if (this.type == type) {
            files.forEach((id, bytes) -> {
                if (id.getNamespace().equals(namespace) && (prefix.isEmpty() || id.getPath().startsWith(prefix + "/"))) {
                    consumer.accept(id, () -> new ByteArrayInputStream(bytes));
                }
            });
        }
    }

    @Override
    public Set<String> getNamespaces(ResourceType type) {
        return this.type == type ? namespaces : Set.of();
    }

    @Override
    public <T> T parseMetadata(ResourceMetadataReader<T> reader) {
        return null;
    }

    @Override
    public ResourcePackInfo getInfo() {
        return INFO;
    }

    @Override
    public void close() {
    }
}
