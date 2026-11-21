package net.xanthian.variantcrafters;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.CrafterBlock;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.CrafterBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.resource.ResourceType;
import net.minecraft.screen.CrafterScreenHandler;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.xanthian.variantcrafters.block.DynamicCrafters;
import net.xanthian.variantcrafters.client.CrafterTexture;
import net.xanthian.variantcrafters.resource.GeneratedCrafterPack;
import net.xanthian.variantcrafters.util.ModCreativeTab;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import javax.imageio.ImageIO;

public class CrafterGameTest implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void generatedTexturePreservesThePlankPattern(TestContext context) throws IOException {
        BufferedImage template;
        try (var stream = getClass().getResourceAsStream("/assets/minecraft/textures/block/crafter_east.png")) {
            template = ImageIO.read(stream);
        }
        var plankPath = FabricLoader.getInstance().getModContainer("blockus")
                .flatMap(mod -> mod.findPath("assets/blockus/textures/block/herringbone_white_oak_planks.png"));
        BufferedImage planks = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        if (plankPath.isPresent()) {
            try (var stream = Files.newInputStream(plankPath.get())) {
                planks = ImageIO.read(stream);
            }
        } else {
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    planks.setRGB(x, y, (x + y) % 2 == 0 ? 0xFF287067 : 0xFF9BC5B6);
                }
            }
        }

        var generated = CrafterTexture.compose(template, planks);
        var woodColors = new HashSet<Integer>();
        int metalPixels = 0;
        for (int y = 0; y < template.getHeight(); y++) {
            for (int x = 0; x < template.getWidth(); x++) {
                int original = template.getRGB(x, y);
                int result = generated.getRGB(x, y);
                if (original == 0xFF9F844D) {
                    context.assertEquals(planks.getRGB(x, y), result, "The wood should use the plank's texel at the same position");
                    woodColors.add(result);
                } else if (original == 0xFF6A6A6A) {
                    context.assertEquals(original, result, "The metal frame should retain its vanilla color");
                    metalPixels++;
                }
            }
        }
        context.assertTrue(woodColors.size() > 1 && metalPixels > 0, "The test must cover different wood texels and the metal frame");
        context.complete();
    }
    private static Identifier crafterId(String wood) {
        return Identifier.of(Initialise.MOD_ID, wood + "_crafter");
    }

    private static CraftingRecipeInput crafterRecipe(Item center) {
        var iron = Items.IRON_INGOT.getDefaultStack();
        var redstone = Items.REDSTONE.getDefaultStack();
        return CraftingRecipeInput.create(3, 3, List.of(
                iron, iron, iron,
                iron, center.getDefaultStack(), iron,
                redstone, Items.DROPPER.getDefaultStack(), redstone));
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void lineupCommandPlacesMatchingColumns(TestContext context) throws CommandSyntaxException {
        var world = context.getWorld();
        var server = world.getServer();
        var origin = context.getAbsolutePos(new BlockPos(0, 20, 0));
        var source = server.getCommandSource().withWorld(world).withPosition(Vec3d.ofBottomCenter(origin));
        int count = server.getCommandManager().getDispatcher().execute("variantcrafters lineup", source);

        var crafters = new ArrayList<>(DynamicCrafters.variants().stream()
                .map(variant -> variant.id().getPath()).toList());
        crafters.add("oak_crafter");
        crafters.sort(Comparator.naturalOrder());
        context.assertEquals(crafters.size(), count, "The lineup should include every loaded crafter");
        var start = origin.south(2);
        for (int index = 0; index < crafters.size(); index++) {
            context.assertFalse(world.isAir(start.east(index).up(2)), "Missing plank above " + crafters.get(index));
        }
        var cherry = start.east(crafters.indexOf("cherry_crafter"));
        context.assertTrue(world.getBlockState(cherry).isOf(Registries.BLOCK.get(
                Identifier.of("variantcraftingtables", "cherry_crafting_table"))), "Cherry table should be at the bottom");
        context.assertTrue(world.getBlockState(cherry.up()).isOf(Registries.BLOCK.get(crafterId("cherry"))),
                "Cherry crafter should be in the middle");
        context.assertTrue(world.getBlockState(cherry.up(2)).isOf(Blocks.CHERRY_PLANKS), "Cherry planks should be on top");
        var fixture = start.east(crafters.indexOf("fixture_crafter"));
        context.assertTrue(world.getBlockState(fixture.up(2)).isOf(Registries.BLOCK.get(TableFixture.LOOSE_PLANKS)),
                "The modded table should use the plank from its own recipe");
        var oak = start.east(crafters.indexOf("oak_crafter"));
        context.assertTrue(world.getBlockState(oak.up()).isOf(Blocks.CRAFTER), "Oak should use the vanilla crafter");
        context.assertEquals(0, server.getCommandManager().getDispatcher().execute("variantcrafters lineup", source),
                "Running the command again should skip occupied columns");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void creativeTabContainsEveryCrafter(TestContext context) {
        var tab = Registries.ITEM_GROUP.getOrEmpty(ModCreativeTab.ID).orElseThrow();
        context.assertEquals("Variant Crafters", tab.getDisplayName().getString(), "The tab should supply its own title");
        var world = context.getWorld();
        tab.updateEntries(new ItemGroup.DisplayContext(world.getEnabledFeatures(), false, world.getRegistryManager()));
        context.assertTrue(tab.getIcon().isOf(Registries.ITEM.get(crafterId("mangrove"))), "The tab should use the mangrove icon");
        context.assertTrue(tab.contains(Items.CRAFTER.getDefaultStack()), "The tab should contain the vanilla crafter");
        DynamicCrafters.variants().forEach(variant ->
                context.assertTrue(tab.contains(variant.block().asItem().getDefaultStack()), "The tab is missing " + variant.id()));
        context.assertEquals(DynamicCrafters.variants().size() + 1,
                tab.getDisplayStacks().size(),
                "The tab should contain each crafter once");
        context.assertEquals(ModCreativeTab.BACKGROUND, tab.getTexture(), "The tab should use the baked background");
        context.assertFalse(tab.shouldRenderName(), "The texture already includes the title");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void discoversOnlyVariantCraftingTables(TestContext context) {
        var variant = DynamicCrafters.variants().stream().filter(entry -> entry.table().equals(TableFixture.TABLE))
                .findFirst().orElseThrow(() -> new AssertionError("No crafter registered for " + TableFixture.TABLE));
        context.assertEquals(crafterId("fixture"), variant.id(), "The source registry path should determine the crafter ID");
        context.assertEquals(TableFixture.TABLE, variant.tableItem(), "The source table should be a recipe ingredient");
        var input = crafterRecipe(Registries.ITEM.get(TableFixture.LOOSE_PLANKS));
        context.assertTrue(CrafterBlock.getCraftingRecipe(context.getWorld(), input).isEmpty(),
                "Planks without a corresponding table should never generate a crafter recipe");
        context.assertFalse(Registries.BLOCK.containsId(crafterId("foreign")),
                "A table registered by another mod should not generate a crafter block");
        var foreignInput = crafterRecipe(Registries.ITEM.get(TableFixture.FOREIGN_TABLE));
        context.assertTrue(CrafterBlock.getCraftingRecipe(context.getWorld(), foreignInput).isEmpty(),
                "A table registered by another mod should not work in the crafter recipe");
        var pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, variant.block());
        context.getWorld().breakBlock(context.getAbsolutePos(pos), true);
        context.expectItemAt(variant.block().asItem(), pos, 1);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void coversEveryRegisteredTable(TestContext context) {
        var variants = DynamicCrafters.variants();
        for (Identifier table : Registries.BLOCK.getIds()) {
            if (!table.getNamespace().equals("variantcraftingtables")
                    || !table.getPath().endsWith("_crafting_table")
                    || table.getPath().equals("oak_crafting_table")) {
                continue;
            }
            context.assertEquals(1L, variants.stream().filter(variant -> variant.table().equals(table)).count(),
                    table + " must have exactly one crafter");
        }
        if (FabricLoader.getInstance().isModLoaded("blockus")) {
            context.assertTrue(Registries.BLOCK.containsId(crafterId("bls_white_oak")),
                    "Blockus is installed but its white oak crafter is missing");
            context.assertFalse(Registries.BLOCK.containsId(crafterId("white_oak")),
                    "A display label should not register a second ID");
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void generatedNamesFollowTheCraftingTableLabels(TestContext context) throws IOException {
        var type = ResourceType.CLIENT_RESOURCES;
        try (var pack = new GeneratedCrafterPack(type, DynamicCrafters.variants());
             var reader = new InputStreamReader(pack.open(type, Identifier.of(Initialise.MOD_ID, "lang/en_us.json")).get(),
                     StandardCharsets.UTF_8)) {
            var text = JsonParser.parseReader(reader).getAsJsonObject();
            try (var vanillaReader = new InputStreamReader(
                    pack.open(type, Identifier.ofVanilla("lang/en_us.json")).get(), StandardCharsets.UTF_8)) {
                var vanillaText = JsonParser.parseReader(vanillaReader).getAsJsonObject();
                context.assertEquals("Oak Crafter", vanillaText.get("block.minecraft.crafter").getAsString(),
                        "The generated pack should name the vanilla crafter in the Minecraft namespace");
            }
            context.assertEquals("Crafter Variants", text.get("tag.item.variantcrafters.crafter_variants").getAsString(),
                    "The generated pack should name the variant tag");
            context.assertEquals("Dark Oak Crafter", text.get("block.variantcrafters.dark_oak_crafter").getAsString(),
                    "The name should retain both words in Dark Oak");
            if (FabricLoader.getInstance().isModLoaded("blockus")) {
                context.assertEquals("Herringbone Dark Oak Crafter",
                        text.get("block.variantcrafters.bls_herringbone_dark_oak_crafter").getAsString(),
                        "The name should retain the pattern and omit the compatibility prefix");
            }
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void variantsHaveVanillaBlockEntityAndMiningRulesAndRecipes(TestContext context) {
        for (String wood : List.of("acacia", "bamboo", "birch", "cherry", "crimson", "dark_oak", "jungle",
                "mangrove", "spruce", "warped")) {
            var id = crafterId(wood);
            context.assertTrue(Registries.BLOCK.containsId(id), "Missing existing variant " + id);
        }
        DynamicCrafters.variants().forEach(variant -> {
            var id = variant.id();
            var block = variant.block();
            context.assertEquals(Blocks.CRAFTER.getClass(), block.getClass(), id + " must use the vanilla block class");
            context.assertTrue(BlockEntityType.CRAFTER.supports(block.getDefaultState()), id + " cannot tick");
            context.assertTrue(Items.STONE_PICKAXE.getDefaultStack().isSuitableFor(block.getDefaultState()),
                    id + " cannot be harvested with a stone pickaxe");
            context.assertFalse(Items.WOODEN_PICKAXE.getDefaultStack().isSuitableFor(block.getDefaultState()),
                    id + " can be harvested with a wooden pickaxe");

            var input = crafterRecipe(Registries.ITEM.get(variant.tableItem()));
            var recipe = CrafterBlock.getCraftingRecipe(context.getWorld(), input).orElseThrow();
            context.assertTrue(recipe.value().craft(input, context.getWorld().getRegistryManager()).isOf(block.asItem()),
                    id + " has the wrong recipe result");

            var conversionInput = CraftingRecipeInput.create(1, 1, List.of(block.asItem().getDefaultStack()));
            var conversion = CrafterBlock.getCraftingRecipe(context.getWorld(), conversionInput).orElseThrow();
            var result = conversion.value().craft(conversionInput, context.getWorld().getRegistryManager());
            context.assertTrue(result.isOf(Items.CRAFTER) && result.getCount() == 1,
                    id + " should convert to exactly one vanilla crafter");
        });
        context.assertFalse(Registries.BLOCK.containsId(crafterId("oak")), "Oak should come from the vanilla crafter");
        context.assertFalse(Registries.ITEM.containsId(crafterId("oak")), "Oak should not have a separate item");
        var oakInput = crafterRecipe(Items.CRAFTING_TABLE);
        var oakRecipe = CrafterBlock.getCraftingRecipe(context.getWorld(), oakInput).orElseThrow();
        context.assertTrue(oakRecipe.value().craft(oakInput, context.getWorld().getRegistryManager()).isOf(Items.CRAFTER),
                "The vanilla table should still craft the vanilla crafter");
        var vanillaConversion = CraftingRecipeInput.create(1, 1, List.of(Items.CRAFTER.getDefaultStack()));
        context.assertTrue(CrafterBlock.getCraftingRecipe(context.getWorld(), vanillaConversion).isEmpty(),
                "The vanilla crafter should be excluded from the conversion recipe");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void craftsOnRedstoneAndResets(TestContext context) {
        checkCrafterAutomation(context, Registries.BLOCK.get(crafterId("warped")));
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void vanillaCrafterAutomationBaseline(TestContext context) {
        checkCrafterAutomation(context, Blocks.CRAFTER);
    }

    private static void checkCrafterAutomation(TestContext context, Block block) {
        BlockPos crafterPos = new BlockPos(1, 1, 2);
        BlockPos outputPos = crafterPos.north();
        BlockPos hopperPos = crafterPos.up();
        context.setBlockState(crafterPos, block);
        context.setBlockState(outputPos, Blocks.BARREL);
        context.setBlockState(hopperPos, Blocks.HOPPER);
        CrafterBlockEntity crafter = context.getBlockEntity(crafterPos);
        crafter.setSlotEnabled(1, false);
        HopperBlockEntity hopper = context.getBlockEntity(hopperPos);
        hopper.setStack(0, Items.OAK_LOG.getDefaultStack());

        var registries = context.getWorld().getRegistryManager();
        context.runAtTick(12, () -> {
            context.assertTrue(hopper.isEmpty() && crafter.getStack(0).isOf(Items.OAK_LOG),
                    "An unlocked slot should accept input from a hopper");
            context.assertFalse(crafter.isValid(1, Items.OAK_LOG.getDefaultStack()),
                    "A hopper should not be able to insert into a disabled slot");
            context.assertEquals(2, comparatorOutput(context, crafterPos),
                    "The comparator should count the occupied and disabled slots");
            var player = context.createMockPlayer(GameMode.SURVIVAL);
            var menu = crafter.createMenu(0, player.getInventory(), player);
            context.assertTrue(menu instanceof CrafterScreenHandler handler && handler.isSlotDisabled(1),
                    "The vanilla crafter menu should retain the disabled slot");

            var nbt = crafter.createNbtWithId(registries);
            context.assertEquals("minecraft:crafter", nbt.getString("id"), "The saved block entity ID changed");
            var reloaded = BlockEntity.createFromNbt(crafter.getPos(), crafter.getCachedState(), nbt, registries);
            context.assertTrue(reloaded instanceof CrafterBlockEntity saved
                            && saved.getStack(0).isOf(Items.OAK_LOG) && saved.isSlotDisabled(1),
                    "Crafter ingredients and disabled slots must survive saving and loading");
            context.putAndRemoveRedstoneBlock(crafterPos.south(), 8);
        });
        context.runAtTick(18, () -> context.expectBlockProperty(crafterPos, CrafterBlock.CRAFTING, true));
        context.runAtTick(28, () -> {
            context.expectBlockProperty(crafterPos, CrafterBlock.CRAFTING, false);
            context.expectBlockProperty(crafterPos, CrafterBlock.TRIGGERED, false);
            BarrelBlockEntity output = context.getBlockEntity(outputPos);
            context.assertEquals(4, output.count(Items.OAK_PLANKS), "The crafter should transfer four planks");
            context.assertTrue(crafter.getStack(0).isEmpty(), "The crafter did not consume the ingredient");
            context.assertEquals(1, comparatorOutput(context, crafterPos), "Only the disabled slot should count after crafting");
            context.getWorld().breakBlock(context.getAbsolutePos(crafterPos), true);
            context.expectItemAt(block.asItem(), crafterPos, 1);
            context.complete();
        });
    }

    private static int comparatorOutput(TestContext context, BlockPos pos) {
        return context.getBlockState(pos).getComparatorOutput(context.getWorld(), context.getAbsolutePos(pos));
    }
}
