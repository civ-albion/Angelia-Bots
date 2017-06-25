package com.github.maxopoly.angelia_digger;

import com.github.maxopoly.angeliacore.actions.ActionQueue;
import com.github.maxopoly.angeliacore.actions.CodeAction;
import com.github.maxopoly.angeliacore.actions.actions.DetectAndEatFood;
import com.github.maxopoly.angeliacore.actions.actions.LookAtAndBreakBlock;
import com.github.maxopoly.angeliacore.actions.actions.LookAtAndPlaceBlock;
import com.github.maxopoly.angeliacore.actions.actions.MoveTo;
import com.github.maxopoly.angeliacore.actions.actions.Wait;
import com.github.maxopoly.angeliacore.actions.actions.inventory.ChangeSelectedItem;
import com.github.maxopoly.angeliacore.actions.actions.inventory.PickHotbarItemByType;
import com.github.maxopoly.angeliacore.actions.actions.inventory.RefillHotbarWithType;
import com.github.maxopoly.angeliacore.connection.ServerConnection;
import com.github.maxopoly.angeliacore.event.AngeliaEventHandler;
import com.github.maxopoly.angeliacore.event.AngeliaListener;
import com.github.maxopoly.angeliacore.event.events.ActionQueueEmptiedEvent;
import com.github.maxopoly.angeliacore.event.events.HungerChangeEvent;
import com.github.maxopoly.angeliacore.event.events.TeleportByServerEvent;
import com.github.maxopoly.angeliacore.model.ItemStack;
import com.github.maxopoly.angeliacore.model.Location;
import com.github.maxopoly.angeliacore.model.Material;
import com.github.maxopoly.angeliacore.model.MovementDirection;
import com.github.maxopoly.angeliacore.model.inventory.PlayerInventory;
import com.github.maxopoly.angeliacore.plugin.AngeliaPlugin;
import com.github.maxopoly.angeliacore.util.HorizontalField;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.cli.Option;

public abstract class AbstractMiningBot extends AngeliaPlugin implements AngeliaListener {

	private static final int locationCacheSize = 10;

	protected ServerConnection connection;
	protected HorizontalField field;
	protected Iterator<Location> locIterator;
	protected ActionQueue queue;
	protected List<Location> lastLocations;
	protected boolean movingBack;
	protected int breakTime;
	protected Material toolUsed;
	protected int snakeLineDistance;

	public AbstractMiningBot(String name, int breakTime, Material toolUsed, int snakeLineDistance) {
		super(name, constructOptions());
		this.breakTime = breakTime;
		this.toolUsed = toolUsed;
		this.snakeLineDistance = snakeLineDistance;
	}

	public AbstractMiningBot(String name, List<Option> options, int breakTime, Material toolUsed, int snakeLineDistance) {
		super(name, options);
		this.breakTime = breakTime;
		this.toolUsed = toolUsed;
		this.snakeLineDistance = snakeLineDistance;
	}

	@Override
	public void start(ServerConnection connection, Map<String, List<String>> args) {
		this.connection = connection;
		this.queue = connection.getActionQueue();
		this.movingBack = true;
		this.lastLocations = new LinkedList<Location>();
		parseOptions(args);
		if (isFinished()) {
			return;
		}
		connection.getEventHandler().registerListener(this);
		// explicitly reset slot selection
		queue.queue(new ChangeSelectedItem(connection, 0));
	}

	public abstract void atEndOfField();

	@AngeliaEventHandler
	public void queueEmpty(ActionQueueEmptiedEvent e) {
		if (locIterator.hasNext()) {
			// important to get direction before block
			MovementDirection direction = field.getCurrentDirection();
			Location target = locIterator.next();
			lastLocations.add(target);
			if (lastLocations.size() > locationCacheSize) {
				lastLocations.remove(0);
			}
			handleLocation(target, direction, breakTime);
			placeTorch(target);
		} else {
			atEndOfField();
		}
	}

	protected void pickTool() {
		queue.queue(new RefillHotbarWithType(connection, toolUsed));
		final PickHotbarItemByType picker = new PickHotbarItemByType(connection, toolUsed);
		queue.queue(picker);
		queue.queue(new CodeAction(connection) {

			@Override
			public void execute() {
				if (!picker.wasFound()) {
					connection.getLogger().info("Failed to find tool to dig, exiting");
					System.exit(0);
				}

			}
		});
	}

	protected void parseOptions(Map<String, List<String>> args) {
		int lowerX, upperX, lowerZ, upperZ, y;
		MovementDirection startingDirection, secondaryDirection;
		List<String> xCoords = args.get("x");
		List<String> zCoords = args.get("z");
		try {
			lowerX = Integer.parseInt(xCoords.get(0));
			upperX = Integer.parseInt(xCoords.get(1));
			if (upperX < lowerX) {
				// swap them
				int temp = lowerX;
				lowerX = upperX;
				upperX = temp;
			}
			lowerZ = Integer.parseInt(zCoords.get(0));
			upperZ = Integer.parseInt(zCoords.get(1));
			if (upperZ < lowerZ) {
				// swap them
				int temp = lowerZ;
				lowerZ = upperZ;
				upperZ = temp;
			}
		} catch (NumberFormatException e) {
			connection.getLogger().info("One of the provided coordinates was not a proper number");
			finish();
			return;
		}
		y = connection.getPlayerStatus().getLocation().getBlockY();
		try {
			startingDirection = MovementDirection.valueOf(args.get("dir1").get(0).toUpperCase());
			secondaryDirection = MovementDirection.valueOf(args.get("dir2").get(0).toUpperCase());
		} catch (IllegalArgumentException e) {
			connection.getLogger().info("One of the provided movement directions could not be parsed");
			finish();
			return;
		}
		this.field = new HorizontalField(lowerX, upperX, lowerZ, upperZ, y, startingDirection, secondaryDirection, true,
				snakeLineDistance);
		this.locIterator = field.iterator();
		if (args.containsKey("ff")) {
			// fast forward to current location
			Location playerLoc = connection.getPlayerStatus().getLocation().toBlockLocation();
			boolean found = false;
			while (!found && locIterator.hasNext()) {
				Location loc = locIterator.next();
				if (loc.equals(playerLoc)) {
					found = true;
					break;
				}
			}
			if (!found) {
				// could not fast forward
				connection.getLogger().warn(
						"Could not fast forward to player location as it was not inside the field. Exiting.");
				finish();
			}
			// already there
			movingBack = false;
			return;
		}
		queue.queue(new MoveTo(connection, field.getStartingLocation().getBlockCenterXZ(), MoveTo.WALKING_SPEED));
		queue.queue(new CodeAction(connection) {

			@Override
			public void execute() {
				AbstractMiningBot.this.movingBack = false;
			}
		});
	}

	protected void handleLocation(Location loc, MovementDirection direction, int breakTime) {
		pickTool();
		Location currentLoc = connection.getPlayerStatus().getLocation().relativeBlock(0.0, -1.0, 0.0);
		// if the player just got teleported back due to walking over a ledge, he might already be on the next block and we
		// need to reconstruct the direction he came from
		double xDiff = Math.abs(currentLoc.getX() - currentLoc.getBlockX());
		double zDiff = Math.abs(currentLoc.getZ() - currentLoc.getBlockZ());
		if (xDiff >= 0.7 || xDiff <= 0.3 || zDiff >= 0.7 || zDiff <= 0.3) {
			// probably on the ledge
			currentLoc = currentLoc.addVector(field.getCurrentDirection().getOpposite().toVector());
		}
		Location upperLoc = new Location(loc.getBlockX(), (int) loc.getY() + 1, (int) loc.getZ());
		Location upperLoc2 = new Location(loc.getBlockX(), (int) loc.getY() + 2, (int) loc.getZ());
		Location upperLoc3 = new Location(loc.getBlockX(), (int) loc.getY() + 3, (int) loc.getZ());
		queue.queue(new LookAtAndBreakBlock(connection, upperLoc, breakTime));
		queue.queue(new LookAtAndBreakBlock(connection, loc, breakTime));
		queue.queue(new LookAtAndBreakBlock(connection, upperLoc2, breakTime));
		queue.queue(new LookAtAndBreakBlock(connection, upperLoc3, breakTime));
		queue.queue(new PickHotbarItemByType(connection, Material.COBBLESTONE));
		queue.queue(new LookAtAndPlaceBlock(connection, currentLoc, field.getCurrentDirection().toBlockFace()));
		queue.queue(new MoveTo(connection, loc.getBlockCenterXZ(), MoveTo.SPRINTING_SPEED));
		// every 8th block we want to double mine up, in case we have left a trail
		boolean mineAgain = false;
		if (field.getSecondaryDirection() == MovementDirection.EAST
				|| field.getSecondaryDirection() == MovementDirection.WEST) {
			// mainly north/south movement, so every 10th block on z coord
			if (loc.getBlockZ() % 8 == 0) {
				mineAgain = true;
			}
		} else {
			// same for x coord
			if (loc.getBlockX() % 8 == 0) {
				mineAgain = true;
			}
		}
		if (mineAgain) {
			queue.queue(new LookAtAndBreakBlock(connection, upperLoc2, breakTime));
			queue.queue(new LookAtAndBreakBlock(connection, upperLoc3, breakTime));
		}
	}

	private void placeTorch(Location loc) {
		if (loc.getBlockX() % 4 != 0 || loc.getBlockZ() % 4 != 0) {
			return;
		}
		PlayerInventory inv = connection.getPlayerStatus().getPlayerInventory();
		int seedSlot = inv.getHotbar().findSlotByType(new ItemStack(Material.TORCH));
		if (seedSlot != -1 && seedSlot != connection.getPlayerStatus().getSelectedHotbarSlot()) {
			queue.queue(new Wait(connection, 5));
			queue.queue(new ChangeSelectedItem(connection, seedSlot));
			queue.queue(new LookAtAndPlaceBlock(connection, loc.relativeBlock(0.0, -1.0, 0.0)));
			queue.queue(new Wait(connection, 5));
		}
	}

	@AngeliaEventHandler
	public void hungerChange(HungerChangeEvent e) {
		if (e.getNewValue() > 7) {
			return;
		}
		queue.queue(new DetectAndEatFood(connection));
	}

	@AngeliaEventHandler
	public void teleportedBack(TeleportByServerEvent e) {
		if (movingBack) {
			return;
		}
		Location oldBlockLoc = e.getLocationTeleportedTo().toBlockLocation();
		if (lastLocations.contains(oldBlockLoc)) {
			connection.getLogger().info("Detected failed break, rolling back to " + oldBlockLoc.toString());
			queue.clear();
			int index = lastLocations.indexOf(oldBlockLoc);
			for (int i = index; i < lastLocations.size(); i++) {
				// maybe we hit something harder, so let's take extra time
				handleLocation(lastLocations.get(i), field.getCurrentDirection(), breakTime * 4);
			}
		}
	}

	@Override
	public void tearDown() {
		connection.getEventHandler().unregisterListener(this);
	}

	private static List<Option> constructOptions() {
		List<Option> options = new LinkedList<Option>();
		options.add(Option.builder("x").longOpt("x").numberOfArgs(2).required()
				.desc("lower and upper x coordinates limiting the bounding box within which the bot will mine").build());
		options.add(Option.builder("z").longOpt("z").numberOfArgs(2).required()
				.desc("lower and upper z coordinates limiting the bounding box within which the bot will mine").build());
		options.add(Option.builder("dir1").longOpt("startingDirection").numberOfArgs(1).required()
				.desc("Cardinal direction in which the bot will begin to move").build());
		options.add(Option.builder("dir2").longOpt("secondaryDirection").numberOfArgs(1).required()
				.desc("Secondary direction in which the bot will move after finish a line").build());
		options.add(Option.builder("ff").longOpt("fast-forward").hasArg(false).required(false).build());
		return options;
	}

}
