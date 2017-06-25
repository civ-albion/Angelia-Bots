package com.github.maxopoly.angelia_digger;

import com.github.maxopoly.angeliacore.actions.actions.LookAtAndBreakBlock;
import com.github.maxopoly.angeliacore.actions.actions.MoveTo;
import com.github.maxopoly.angeliacore.model.Location;
import com.github.maxopoly.angeliacore.model.Material;
import com.github.maxopoly.angeliacore.model.MovementDirection;
import com.github.maxopoly.angeliacore.model.Vector;
import com.github.maxopoly.angeliacore.plugin.AngeliaPlugin;
import com.github.maxopoly.angeliacore.util.HorizontalField;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.cli.Option;
import org.kohsuke.MetaInfServices;

@MetaInfServices(AngeliaPlugin.class)
public class TunnelBot extends AbstractMiningBot {

	private int width;
	private int height;
	private boolean doCorners;
	private MovementDirection orthogonal;

	public TunnelBot() {
		super("TunnelBot", createOptions(), 6, Material.DIAMOND_PICKAXE, 1);
	}

	@Override
	public String getHelp() {
		return "Moves to the given starting location and digs a straight tunnel from there in the given direction. "
				+ "The tunnel will be centered on the starting location and its floor will be at the same y level as the starting location."
				+ "Note that the bot does not move outwards to remove blocks further away, so all blocks to be removed may be at maximum"
				+ " 5 blocks away from the tunnel center.";
	}

	@Override
	protected void parseOptions(Map<String, List<String>> args) {
		int x, z;
		try {
			x = Integer.parseInt(args.get("x").get(0));
			z = Integer.parseInt(args.get("z").get(0));
		} catch (NumberFormatException e) {
			connection.getLogger().warn("One of the coords supplied was not a proper integer");
			finish();
			return;
		}
		int length;
		try {
			height = Integer.parseInt(args.get("h").get(0));
			width = Integer.parseInt(args.get("w").get(0));
			length = Integer.parseInt(args.get("l").get(0));
		} catch (NumberFormatException e) {
			connection.getLogger().warn("One of sizes supplied was not a proper integer");
			finish();
			return;
		}
		MovementDirection direction;
		try {
			direction = MovementDirection.valueOf(args.get("dir").get(0).toUpperCase());
		} catch (IllegalArgumentException e) {
			connection.getLogger().info("The provided movement direction could not be parsed");
			finish();
			return;
		}
		if (!MovementDirection.CARDINAL.contains(direction)) {
			connection.getLogger().info("Direction must be a cardinal!");
			finish();
			return;
		}
		int y = connection.getPlayerStatus().getLocation().getBlockY();
		Vector digVector = direction.toVector().multiply(length);
		// pick any direction orthogonal to the starting one
		orthogonal = null;
		for (MovementDirection dir : MovementDirection.CARDINAL) {
			if (dir.toVector().cross(digVector).isZero()) {
				orthogonal = dir;
			}
		}
		doCorners = !args.containsKey("leaveCorners");
		this.field = new HorizontalField(x, x + (int) digVector.getX(), z, z + (int) digVector.getZ(), y, direction,
				orthogonal, true, snakeLineDistance);
		this.locIterator = field.iterator();
	}

	@Override
	protected void handleLocation(Location loc, MovementDirection direction, int breakTime) {
		// break first 2 blocks
		queue.queue(new LookAtAndBreakBlock(connection, loc, breakTime));
		queue.queue(new LookAtAndBreakBlock(connection, loc.relativeBlock(0, 1, 0), breakTime));
		// move in
		queue.queue(new MoveTo(connection, loc.getBlockCenterXZ(), MoveTo.WALKING_SPEED));
		// mine above middle location
		for (int i = 2; i < height; i++) {
			queue.queue(new LookAtAndBreakBlock(connection, loc.relativeBlock(0, i, 0), breakTime));
		}
		for (int i = 2; i <= width; i++) {
			Vector side = orthogonal.toVector().multiply(i / 2);
			if ((i % 2) == 0) {
				side = side.multiply(-1.0);
			}
			int bottomY = loc.getBlockY();
			int x = loc.getBlockX() + (int) side.getX();
			int z = loc.getBlockZ() + (int) side.getZ();
			for (int y = bottomY; y <= (bottomY + height); y++) {
				if (!doCorners && i >= (width - 1) && (y == bottomY || y == (bottomY + height))) {
					// ignore corner
					continue;
				}
				queue.queue(new LookAtAndBreakBlock(connection, new Location(x, y, z), breakTime));
			}
		}
	}

	private static List<Option> createOptions() {
		List<Option> options = new LinkedList<Option>();
		options.add(Option.builder("x").longOpt("x").numberOfArgs(1).required()
				.desc("x part of the coordinate at which the rail bot will start").build());
		options.add(Option.builder("z").longOpt("z").numberOfArgs(2).required()
				.desc("z part of the coordinate at which the rail bot will start").build());
		options.add(Option.builder("l").longOpt("length").required().numberOfArgs(1)
				.desc("Length of the tunnel to dig, must be an integer bigger than 1").build());
		options.add(Option.builder("h").longOpt("height").required().numberOfArgs(1)
				.desc("Height of the tunnel to dig, must be an integer bigger than 2 and smaller than 6").build());
		options.add(Option.builder("w").longOpt("width").required().numberOfArgs(1)
				.desc("Total width of the tunnel to dig. Must be an integer bigger than 1 and smaller than 11").build());
		options.add(Option.builder("dir").longOpt("direction").numberOfArgs(1).required()
				.desc("Cardinal direction in which the bot will dig the tunnel").build());
		options.add(Option.builder("leaveCorners").longOpt("leaveCorners").hasArg(false).required(false)
				.desc("Whether the bot should not mine the corners of the tunnel").build());
		return options;
	}

	@Override
	public void atEndOfField() {
		connection.getLogger().info("Finished digging tunnel, logging off");
		System.exit(0);
	}

}
