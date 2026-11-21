package net.xanthian.variantcrafters.resource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.Identifier;
import net.xanthian.variantcrafters.Initialise;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

final class CrafterModels {
    private CrafterModels() {
    }

    static Map<Identifier, JsonObject> models(Identifier id, Function<String, String> faceTexture) {
        JsonObject states = VanillaTemplates.STATES.deepCopy();
        String path = id.getPath();
        String blockModel = "block/" + path;
        for (var entry : states.getAsJsonObject("variants").entrySet()) {
            JsonObject state = entry.getValue().getAsJsonObject();
            String model = state.get("model").getAsString();
            String suffix = model.substring("minecraft:block/crafter".length());
            if (suffix.equals("_crafting_triggered")) {
                suffix = "_crafting";
            }
            state.addProperty("model", id.withPath(blockModel + suffix).toString());
        }

        JsonObject item = new JsonObject();
        item.addProperty("parent", id.withPath(blockModel).toString());
        Map<Identifier, JsonObject> files = new LinkedHashMap<>();
        files.put(id.withPath("blockstates/" + path + ".json"), states);
        files.put(id.withPath("models/" + blockModel + ".json"), model(VanillaTemplates.IDLE, faceTexture));
        files.put(id.withPath("models/" + blockModel + "_triggered.json"), model(VanillaTemplates.TRIGGERED, faceTexture));
        files.put(id.withPath("models/" + blockModel + "_crafting.json"), model(VanillaTemplates.CRAFTING, faceTexture));
        files.put(id.withPath("models/item/" + path + ".json"), item);
        return files;
    }

    private static JsonObject model(JsonObject vanillaTextures, Function<String, String> faceTexture) {
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:block/crafter");
        JsonObject textures = new JsonObject();
        for (String face : List.of("north", "east", "south", "west")) {
            String texture = vanillaTextures.get(face).getAsString().replace("block/crafter_", "");
            textures.addProperty(face, Initialise.MOD_ID + ":" + faceTexture.apply(texture));
        }
        textures.addProperty("bottom", "minecraft:" + vanillaTextures.get("bottom").getAsString());
        textures.addProperty("top", "minecraft:" + vanillaTextures.get("top").getAsString());
        model.add("textures", textures);
        return model;
    }

    private static JsonObject mergeTextures(JsonObject base, String model) {
        JsonObject textures = base.deepCopy();
        readVanilla("models/block/" + model + ".json").getAsJsonObject("textures").entrySet()
                .forEach(entry -> textures.add(entry.getKey(), entry.getValue()));
        return textures;
    }

    private static JsonObject readVanilla(String path) {
        var resource = Objects.requireNonNull(CrafterModels.class.getResourceAsStream("/assets/minecraft/" + path), path);
        try (var reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // The dedicated server only generates data, so load the client model templates on demand.
    private static final class VanillaTemplates {
        private static final JsonObject STATES = readVanilla("blockstates/crafter.json");
        private static final JsonObject IDLE = readVanilla("models/block/crafter.json").getAsJsonObject("textures");
        private static final JsonObject TRIGGERED = mergeTextures(IDLE, "crafter_triggered");
        private static final JsonObject CRAFTING = mergeTextures(TRIGGERED, "crafter_crafting");
    }

}
