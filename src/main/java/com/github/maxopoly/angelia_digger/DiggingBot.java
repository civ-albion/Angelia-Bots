package com.github.maxopoly.angelia_digger;

import com.github.maxopoly.angeliacore.actions.actions.DigDown;
import com.github.maxopoly.angeliacore.actions.actions.MoveTo;
import com.github.maxopoly.angeliacore.model.Material;
import com.github.maxopoly.angeliacore.plugin.AngeliaPlugin;
import org.kohsuke.MetaInfServices;

@MetaInfServices(AngeliaPlugin.class)
public class DiggingBot extends AbstractMiningBot {

	public DiggingBot() {
		super("DiggingBot", 6, Material.DIAMOND_PICKAXE, 1);
	}

	@Override
	public String getHelp() {
		return "Digs down the selected area from the current y-level to bedrock. The given starting direction and "
				+ "secondary direction implicitly define a starting corner. The bot will begin by walking to this corner and then iterate "
				+ "over the field block for block from there. At each location 4 blocks will be mined, the 2 on the height of the players body"
				+ "and the two blocks above those. \n After finishing the entire area, the bot will walk back to it's starting location dig down four block straight and continue mining. \n"
				+ "If the bot has cobblestone available on it's hotbar, it will use it to bridge across caves and holes in the floor. If no cobble is available, the bot will get stuck on those.\n"
				+ "If the bot has torches available on it's hotbar, it will place them at every location, where both x modulo 4 and z modulo 4 equal 0 \n"
				+ "If food is available on the hotbar or in the players offhand, the bot will eat it as needed\n";
	}

	@Override
	public void atEndOfField() {
		if (field.getY() == 1) {
			connection.getLogger().info("Successfully dug down to bedrock. Logging off");
			System.exit(0);
		}
		pickTool();
		queue.queue(new MoveTo(connection, field.getStartingLocation().getBlockCenterXZ(), MoveTo.WALKING_SPEED));
		queue.queue(new DigDown(connection, field.getStartingLocation().getBlockCenterXZ(), 4, breakTime * 2));
		int y = field.getY() - 4;
		if (y <= 0) {
			y = 1;
		}
		field = field.copy(y);
		locIterator = field.iterator();
		lastLocations.clear();
		queueEmpty(null);
	}
}
