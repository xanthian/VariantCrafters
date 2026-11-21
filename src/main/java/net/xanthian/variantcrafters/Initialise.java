package net.xanthian.variantcrafters;

import net.fabricmc.api.ModInitializer;

import net.xanthian.variantcrafters.block.DynamicCrafters;
import net.xanthian.variantcrafters.util.ModCreativeTab;

public class Initialise implements ModInitializer {

	public static final String MOD_ID = "variantcrafters";

	@Override
	public void onInitialize() {
		DynamicCrafters.register();
		ModCreativeTab.register();
		LineupCommand.register();
	}
}
