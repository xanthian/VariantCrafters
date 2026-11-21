package net.xanthian.variantcrafters;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.xanthian.variantcrafters.block.CrafterVariants;
import net.xanthian.variantcrafters.block.CraftingTableInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class CrafterLineupCommand {
    private static final SimpleCommandExceptionType CREATIVE_REQUIRED = new SimpleCommandExceptionType(
            Text.translatableWithFallback("commands.variantcrafters.lineup.creative_required",
                    "Only players in Creative mode can place a crafter lineup."));

    private record Column(Block table, Block crafter, Block plank) {
    }

    private CrafterLineupCommand() {
    }

    static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) ->
                dispatcher.register(CommandManager.literal(VariantCrafters.MOD_ID)
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.literal("lineup").executes(context -> place(context.getSource())))));
    }

    private static int place(ServerCommandSource source) throws CommandSyntaxException {
        if (!(source.getEntity() instanceof ServerPlayerEntity player) || !player.isCreative()) {
            throw CREATIVE_REQUIRED.create();
        }
        var world = source.getWorld();
        var variants = CrafterVariants.variants();
        var planks = CraftingTableInfo.findSourcePlanks(world, variants);

        List<Column> columns = new ArrayList<>();
        columns.add(new Column(Blocks.CRAFTING_TABLE, Blocks.CRAFTER, Blocks.OAK_PLANKS));
        for (var variant : variants) {
            columns.add(new Column(Registries.BLOCK.get(variant.table()), variant.block(),
                    planks.get(Registries.ITEM.get(variant.tableItem()))));
        }
        columns.sort(Comparator.comparing(column -> column.crafter() == Blocks.CRAFTER
                ? "oak_crafter" : Registries.BLOCK.getId(column.crafter()).getPath()));

        BlockPos start = BlockPos.ofFloored(source.getPosition()).south(2);
        int placed = 0;
        int missingPlanks = 0;
        for (int index = 0; index < columns.size(); index++) {
            BlockPos tablePos = start.east(index);
            if (!isClear(world, tablePos) || !isClear(world, tablePos.up()) || !isClear(world, tablePos.up(2))) {
                continue;
            }
            Column column = columns.get(index);
            world.setBlockState(tablePos, column.table().getDefaultState());
            world.setBlockState(tablePos.up(), column.crafter().getDefaultState());
            if (column.plank() != null) {
                world.setBlockState(tablePos.up(2), column.plank().getDefaultState());
            } else {
                missingPlanks++;
            }
            placed++;
        }
        int count = placed;
        int missing = missingPlanks;
        source.sendFeedback(() -> Text.translatableWithFallback("commands.variantcrafters.lineup",
                "Placed %s crafter displays. Skipped %s blocked columns. %s placed displays had no matching plank.", count,
                columns.size() - count, missing), false);
        return placed;
    }

    private static boolean isClear(ServerWorld world, BlockPos pos) {
        return world.isInBuildLimit(pos) && world.getWorldBorder().contains(pos) && world.isAir(pos);
    }
}
