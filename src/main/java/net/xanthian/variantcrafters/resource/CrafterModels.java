package net.xanthian.variantcrafters.resource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CrafterModels {
    private static final String VANILLA_CRAFTER = "minecraft:block/crafter";
    private static final List<String> SIDES = List.of("north", "east", "south", "west");

    private CrafterModels() {
    }

    static Map<Identifier, JsonObject> models(Identifier id) {
        Templates templates = VanillaTemplates.CONTENT;
        String path = id.getPath();
        Identifier stateFile = id.withPath("blockstates/" + path + ".json");
        Identifier itemFile = id.withPath("models/item/" + path + ".json");
        if (templates == null) {
            JsonObject variants = new JsonObject();
            JsonObject state = new JsonObject();
            state.addProperty("model", VANILLA_CRAFTER);
            variants.add("", state);
            JsonObject blockstate = new JsonObject();
            blockstate.add("variants", variants);
            return Map.of(stateFile, blockstate, itemFile, withParent(VANILLA_CRAFTER));
        }

        JsonObject states = templates.states().deepCopy();
        String blockModel = "block/" + path;
        for (var entry : states.getAsJsonObject("variants").entrySet()) {
            JsonObject state = entry.getValue().getAsJsonObject();
            String suffix = switch (state.get("model").getAsString()) {
                case VANILLA_CRAFTER -> "";
                case VANILLA_CRAFTER + "_triggered" -> "_triggered";
                case VANILLA_CRAFTER + "_crafting", VANILLA_CRAFTER + "_crafting_triggered" -> "_crafting";
                default -> null;
            };
            if (suffix != null) {
                state.addProperty("model", id.withPath(blockModel + suffix).toString());
            }
        }

        Map<Identifier, JsonObject> files = new LinkedHashMap<>();
        files.put(stateFile, states);
        files.put(id.withPath("models/" + blockModel + ".json"), model(id, templates.idle()));
        files.put(id.withPath("models/" + blockModel + "_triggered.json"), model(id, templates.triggered()));
        files.put(id.withPath("models/" + blockModel + "_crafting.json"), model(id, templates.crafting()));
        files.put(itemFile, withParent(id.withPath(blockModel).toString()));
        return files;
    }

    private static JsonObject withParent(String parent) {
        JsonObject model = new JsonObject();
        model.addProperty("parent", parent);
        return model;
    }

    private static JsonObject model(Identifier id, JsonObject vanillaTextures) {
        JsonObject model = withParent(VANILLA_CRAFTER);
        JsonObject textures = new JsonObject();
        String prefix = "block/crafter_";
        for (String face : SIDES) {
            Identifier texture = Identifier.tryParse(vanillaTextures.get(face).getAsString());
            if (texture != null && texture.getPath().startsWith(prefix)) {
                String name = texture.getPath().substring(prefix.length());
                textures.addProperty(face, id.withPath(GeneratedCrafterPack.texturePath(id, name)).toString());
            }
        }
        textures.addProperty("bottom", vanillaTextures.get("bottom").getAsString());
        textures.addProperty("top", vanillaTextures.get("top").getAsString());
        model.add("textures", textures);
        return model;
    }

    private static JsonObject mergeTextures(JsonObject base, String model) throws IOException {
        JsonObject textures = base.deepCopy();
        JsonHelper.getObject(readVanilla("models/block/" + model + ".json"), "textures").entrySet()
                .forEach(entry -> textures.add(entry.getKey(), entry.getValue()));
        return textures;
    }

    private static JsonObject readVanilla(String path) throws IOException {
        var resource = CrafterModels.class.getResourceAsStream("/assets/minecraft/" + path);
        if (resource == null) {
            throw new IOException("Missing vanilla resource " + path);
        }
        try (var reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            return JsonHelper.asObject(JsonParser.parseReader(reader), path);
        }
    }

    private static Templates loadTemplates() {
        try {
            JsonObject states = readVanilla("blockstates/crafter.json");
            JsonObject variants = JsonHelper.getObject(states, "variants");
            if (variants.isEmpty()) {
                throw new JsonParseException("Vanilla crafter blockstate has no variants");
            }
            variants.asMap().forEach((key, value) -> JsonHelper.getString(JsonHelper.asObject(value, key), "model"));
            JsonObject idle = JsonHelper.getObject(readVanilla("models/block/crafter.json"), "textures");
            JsonObject triggered = mergeTextures(idle, "crafter_triggered");
            JsonObject crafting = mergeTextures(triggered, "crafter_crafting");
            for (JsonObject textures : List.of(idle, triggered, crafting)) {
                SIDES.forEach(face -> JsonHelper.getString(textures, face));
                JsonHelper.getString(textures, "top");
                JsonHelper.getString(textures, "bottom");
            }
            return new Templates(states, idle, triggered, crafting);
        } catch (IOException | JsonParseException exception) {
            LogUtils.getLogger().warn("Could not load vanilla crafter model templates. Using the vanilla model.", exception);
            return null;
        }
    }

    private record Templates(JsonObject states, JsonObject idle, JsonObject triggered, JsonObject crafting) {
    }

    // The dedicated server only generates data, so load the client model templates on demand.
    private static final class VanillaTemplates {
        private static final Templates CONTENT = loadTemplates();
    }

}
