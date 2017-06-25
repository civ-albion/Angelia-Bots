package com.github.maxopoly.angelia_digger;

import com.github.maxopoly.angeliacore.model.Material;
import com.github.maxopoly.angeliacore.plugin.AngeliaPlugin;
import org.kohsuke.MetaInfServices;

@MetaInfServices(AngeliaPlugin.class)
public class IronBot extends AbstractMiningBot {

	public IronBot() {
		super("IronBot", 6, Material.DIAMOND_PICKAXE, 3);
	}

	@Override
	public void atEndOfField() {
		connection.getLogger().info("Area is done, logging out");
		System.exit(0);
	}

	@Override
	public String getHelp() {
		return "Tell max to put something here";
	}
}
