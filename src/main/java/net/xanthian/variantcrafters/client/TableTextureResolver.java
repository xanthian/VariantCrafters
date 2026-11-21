package net.xanthian.variantcrafters.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.logging.LogUtils;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceFactory;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;

/** Resolves inherited JSON model textures. Adapted from Variant Crafting Tables under the MIT license. */
final class TableTextureResolver {
    private static final Logger LOGGER = LogUtils.getLogger();

    record WoodTexture(Identifier id, Resource resource) {
    }

    private TableTextureResolver() {
    }

    static WoodTexture resolve(ResourceFactory resources, Identifier table) throws IOException {
        return resolveModel(resources, table, "down", "bottom", "wood", "all", "side", "particle", "north", "top", "up");
    }

    static WoodTexture resolvePlanks(ResourceFactory resources, Identifier planks) throws IOException {
        try {
            return resolveModel(resources, planks, "all", "side", "top", "particle", "north", "up");
        } catch (FileNotFoundException exception) {
            Identifier texture = planks.withPrefixedPath("block/");
            return new WoodTexture(texture,
                    resources.getResourceOrThrow(texture.withPrefixedPath("textures/").withSuffixedPath(".png")));
        }
    }

    private static WoodTexture resolveModel(ResourceFactory resources, Identifier block, String... keys) throws IOException {
        Identifier defaultModel = block.withPrefixedPath("block/");
        JsonObject state = readObject(resources, block.withPrefixedPath("blockstates/").withSuffixedPath(".json"));
        if (state != null) {
            Identifier model = findModel(state);
            if (model != null && !model.equals(defaultModel)) {
                WoodTexture wood = findTexture(resources, model, keys);
                if (wood != null) {
                    return wood;
                }
            }
        }

        WoodTexture wood = findTexture(resources, defaultModel, keys);
        if (wood == null) {
            throw new FileNotFoundException("No wood texture for " + block);
        }
        return wood;
    }

    private static Identifier findModel(JsonObject state) {
        if (state.get("variants") instanceof JsonObject variants) {
            for (JsonElement variant : variants.asMap().values()) {
                Identifier model = modelChoice(variant);
                if (model != null) {
                    return model;
                }
            }
        }
        if (state.get("multipart") instanceof JsonArray parts) {
            for (JsonElement part : parts) {
                if (part instanceof JsonObject object) {
                    Identifier model = modelChoice(object.get("apply"));
                    if (model != null) {
                        return model;
                    }
                }
            }
        }
        return null;
    }

    private static Identifier modelChoice(JsonElement choice) {
        if (choice instanceof JsonArray choices) {
            for (JsonElement candidate : choices) {
                Identifier model = modelId(candidate);
                if (model != null) {
                    return model;
                }
            }
            return null;
        }
        return modelId(choice);
    }

    private static Identifier modelId(JsonElement choice) {
        return choice instanceof JsonObject object ? identifier(object.get("model")) : null;
    }

    private static WoodTexture findTexture(ResourceFactory resources, Identifier model, String[] keys) {
        var textures = new HashMap<String, String>();
        var visitedModels = new HashSet<Identifier>();
        while (model != null && visitedModels.size() < 32 && visitedModels.add(model)) {
            JsonObject json = readObject(resources, model.withPrefixedPath("models/").withSuffixedPath(".json"));
            if (json == null) {
                break;
            }
            if (json.get("textures") instanceof JsonObject modelTextures) {
                modelTextures.asMap().forEach((key, value) -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                        textures.putIfAbsent(key, primitive.getAsString());
                    }
                });
            }
            model = identifier(json.get("parent"));
        }

        for (String key : keys) {
            String value = textures.get(key);
            var visitedAliases = new HashSet<String>();
            while (value != null && value.startsWith("#") && visitedAliases.add(value)) {
                value = textures.get(value.substring(1));
            }
            Identifier texture = value == null ? null : Identifier.tryParse(value);
            if (texture != null) {
                var resource = resources.getResource(texture.withPrefixedPath("textures/").withSuffixedPath(".png"));
                if (resource.isPresent()) {
                    return new WoodTexture(texture, resource.get());
                }
            }
        }
        return null;
    }

    private static JsonObject readObject(ResourceFactory resources, Identifier id) {
        var resource = resources.getResource(id);
        if (resource.isEmpty()) {
            return null;
        }
        try (var reader = resource.get().getReader()) {
            return JsonParser.parseReader(reader) instanceof JsonObject json ? json : null;
        } catch (IOException | JsonParseException exception) {
            LOGGER.warn("Ignoring unreadable model resource {}", id, exception);
            return null;
        }
    }

    private static Identifier identifier(JsonElement element) {
        return element instanceof JsonPrimitive value && value.isString()
                ? Identifier.tryParse(value.getAsString()) : null;
    }
}

