package net.xanthian.variantcrafters.block;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class TableNames {
    private static final String SUFFIX = " Crafting Table";
    private static final Map<String, String> ENGLISH_NAMES = load();

    private TableNames() {
    }

    public static Identifier sourcePlanks(Identifier table) {
        Object shared = FabricLoader.getInstance().getObjectShare().get(DynamicCrafters.TABLES_MOD_ID + ":source_planks");
        if (shared instanceof Map<?, ?> sources && sources.get(table) instanceof Identifier planks) {
            return planks;
        }
        return null;
    }

    static String woodLabel(Identifier table, String translationKey) {
        Identifier planks = sourcePlanks(table);
        if (planks != null) {
            return plankLabel(planks);
        }
        String name = ENGLISH_NAMES.get(translationKey);
        if (name != null) {
            return name;
        }
        String wood = table.getPath().replaceFirst("(^|[/_])crafting_table$", "");
        return Arrays.stream(wood.split("[/_.-]+")).filter(word -> !word.isEmpty())
                .map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    private static String plankLabel(Identifier planks) {
        String label = planks.getPath().replaceFirst("(^|[/_])planks$", "").replace('/', ' ').replace('_', ' ').trim();
        if (label.isEmpty()) {
            label = planks.getNamespace().replace('_', ' ').trim();
            if (label.isEmpty()) {
                label = planks.getNamespace();
            }
        }
        return Arrays.stream(label.split(" +"))
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    private static Map<String, String> load() {
        String mod = DynamicCrafters.TABLES_MOD_ID;
        var path = FabricLoader.getInstance().getModContainer(mod)
                .flatMap(container -> container.findPath("assets/" + mod + "/lang/en_us.json"));
        if (path.isEmpty()) {
            return Map.of();
        }
        Map<String, String> names = new HashMap<>();
        try (var reader = Files.newBufferedReader(path.get())) {
            JsonParser.parseReader(reader).getAsJsonObject().entrySet().forEach(entry -> {
                var value = entry.getValue();
                if (!entry.getKey().startsWith("block." + mod + ".")
                        || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                    return;
                }
                String translated = value.getAsString();
                if (!translated.endsWith(SUFFIX)) {
                    return;
                }
                String label = translated.substring(0, translated.length() - SUFFIX.length()).strip();
                if (!label.isEmpty()) {
                    names.put(entry.getKey(), label);
                }
            });
        } catch (IOException | JsonParseException | IllegalStateException exception) {
            LogUtils.getLogger().warn("Could not read Variant Crafting Tables display names", exception);
            return Map.of();
        }

        return Map.copyOf(names);
    }
}
