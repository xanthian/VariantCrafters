package net.xanthian.variantcrafters.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.Identifier;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Resolves inherited JSON model textures. Adapted from Variant Crafting Tables under the MIT license. */
final class TableTextureResolver {
    record WoodTexture(Identifier id, Resource resource) {
    }

    private TableTextureResolver() {
    }

    static WoodTexture resolve(ResourceManager resources, Identifier table) throws IOException {
        return resolveModel(resources, table, "down", "bottom", "wood", "all", "side", "particle", "north", "top", "up");
    }

    static WoodTexture resolvePlanks(ResourceManager resources, Identifier planks) throws IOException {
        try {
            return resolveModel(resources, planks, "all", "side", "top", "particle", "north", "up");
        } catch (FileNotFoundException exception) {
            Identifier texture = planks.withPrefixedPath("block/");
            return new WoodTexture(texture,
                    resources.getResourceOrThrow(texture.withPrefixedPath("textures/").withSuffixedPath(".png")));
        }
    }

    private static WoodTexture resolveModel(ResourceManager resources, Identifier table, String... keys) throws IOException {
        Identifier model = table.withPath("block/" + table.getPath());
        var blockstate = resources.getResource(table.withPath("blockstates/" + table.getPath() + ".json"));
        if (blockstate.isPresent()) {
            try (Reader reader = blockstate.get().getReader()) {
                JsonObject state = JsonParser.parseReader(reader).getAsJsonObject();
                JsonElement apply = null;
                if (state.has("variants") && !state.getAsJsonObject("variants").isEmpty()) {
                    apply = state.getAsJsonObject("variants").entrySet().iterator().next().getValue();
                } else if (state.has("multipart") && !state.getAsJsonArray("multipart").isEmpty()) {
                    apply = state.getAsJsonArray("multipart").get(0).getAsJsonObject().get("apply");
                }
                if (apply != null) {
                    if (apply.isJsonArray()) {
                        apply = apply.getAsJsonArray().get(0);
                    }
                    model = Identifier.of(apply.getAsJsonObject().get("model").getAsString());
                }
            }
        }

        Map<String, String> textures = new LinkedHashMap<>();
        collectTextures(resources, model, textures, new HashSet<>());
        for (String key : keys) {
            String value = textures.get(key);
            Set<String> visited = new HashSet<>();
            while (value != null && value.startsWith("#") && visited.add(value)) {
                value = textures.get(value.substring(1));
            }
            if (value != null && !value.startsWith("#")) {
                Identifier texture = Identifier.of(value);
                var resource = resources.getResource(texture.withPath("textures/" + texture.getPath() + ".png"));
                if (resource.isPresent()) {
                    return new WoodTexture(texture, resource.get());
                }
            }
        }
        throw new FileNotFoundException("No wood texture in model " + model);
    }

    private static void collectTextures(ResourceManager resources, Identifier model, Map<String, String> textures,
                                        Set<Identifier> visited) throws IOException {
        if (visited.size() >= 32 || !visited.add(model)) {
            return;
        }
        var resource = resources.getResource(model.withPath("models/" + model.getPath() + ".json"));
        if (resource.isEmpty()) {
            return;
        }
        try (Reader reader = resource.get().getReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.has("parent")) {
                collectTextures(resources, Identifier.of(json.get("parent").getAsString()), textures, visited);
            }
            if (json.has("textures")) {
                json.getAsJsonObject("textures").entrySet().forEach(entry -> {
                    if (entry.getValue().isJsonPrimitive()) {
                        textures.put(entry.getKey(), entry.getValue().getAsString());
                    }
                });
            }
        }
    }
}

