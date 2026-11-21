package net.xanthian.variantcrafters.block;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class CraftingTableInfo {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SUFFIX = " Crafting Table";
    private static final Map<String, String> ENGLISH_NAMES = load();

    private CraftingTableInfo() {
    }

    public static Identifier sourcePlanks(Identifier table) {
        Object shared = FabricLoader.getInstance().getObjectShare().get(CrafterVariants.TABLES_MOD_ID + ":source_planks");
        if (shared instanceof Map<?, ?> sources && sources.get(table) instanceof Identifier planks) {
            return planks;
        }
        return null;
    }

    public static Map<Item, Block> findSourcePlanks(ServerWorld world, Collection<CrafterVariants.Variant> variants) {
        Map<Item, Block> planks = new HashMap<>();
        var unresolved = new HashSet<Item>();
        for (var variant : variants) {
            Item table = Registries.ITEM.get(variant.tableItem());
            Identifier sourceId = sourcePlanks(variant.table());
            Block source = sourceId == null ? null : Registries.BLOCK.getOrEmpty(sourceId).orElse(null);
            if (source == null) {
                unresolved.add(table);
            } else {
                planks.put(table, source);
            }
        }
        if (unresolved.isEmpty()) {
            return planks;
        }

        var registries = world.getRegistryManager();
        for (var entry : world.getRecipeManager().listAllOfType(RecipeType.CRAFTING)) {
            try {
                var recipe = entry.value();
                Item table = recipe.getResult(registries).getItem();
                if (!unresolved.contains(table)) {
                    continue;
                }
                boolean exact = entry.id().equals(Registries.ITEM.getId(table));
                if (!exact && planks.containsKey(table)) {
                    continue;
                }
                Block source = recipe.getIngredients().stream()
                        .flatMap(ingredient -> Arrays.stream(ingredient.getMatchingStacks()))
                        .filter(stack -> {
                            String path = Registries.ITEM.getId(stack.getItem()).getPath();
                            return stack.getItem() instanceof BlockItem && (stack.isIn(ItemTags.PLANKS)
                                    || path.endsWith("_planks") || path.endsWith("/planks") || path.equals("planks"));
                        })
                        .map(stack -> ((BlockItem) stack.getItem()).getBlock())
                        .findFirst().orElse(null);
                if (source != null) {
                    planks.put(table, source);
                    if (exact) {
                        unresolved.remove(table);
                        if (unresolved.isEmpty()) {
                            break;
                        }
                    }
                }
            } catch (RuntimeException exception) {
                LOGGER.debug("Could not inspect crafting recipe {}", entry.id(), exception);
            }
        }
        return planks;
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
        String mod = CrafterVariants.TABLES_MOD_ID;
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
            LOGGER.warn("Could not read Variant Crafting Tables display names", exception);
            return Map.of();
        }

        return Map.copyOf(names);
    }
}
