package com.github.maxopoly.angelia_digger;

import com.github.maxopoly.angeliacore.actions.ActionQueue;
import com.github.maxopoly.angeliacore.actions.CodeAction;
import com.github.maxopoly.angeliacore.actions.actions.DetectAndEatFood;
import com.github.maxopoly.angeliacore.actions.actions.DigDown;
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

public class DiggingBot extends AngeliaPlugin implements AngeliaListener {

	private static final int BREAK_TIME = 6;
	private static final int locationCacheSize = 10;

	private ServerConnection connection;
	private HorizontalField field;
	private Iterator<Location> locIterator;
	private ActionQueue queue;
	private List<Location> lastLocations;
	private boolean movingBack;

	public DiggingBot() {
		super("DiggingBot");
	}

	@Override
	public void start(ServerConnection connection, String[] args) {
		this.connection = connection;
		if (args.length != 7) {
			connection
					.getLogger()
					.warn(
							"Wrong args, should be: <firstX> <secondX> <firstZ> <secondZ> <yLevel> <startingDirection> <secondaryMovementDirection>");
			finish();
			return;
		}
		int lowerX, upperX, lowerZ, upperZ, y;
		MovementDirection startingDirection, secondaryDirection;
		try {
			lowerX = Integer.parseInt(args[0]);
			upperX = Integer.parseInt(args[1]);
			if (upperX < lowerX) {
				// swap them
				int temp = lowerX;
				lowerX = upperX;
				upperX = temp;
			}
			lowerZ = Integer.parseInt(args[2]);
			upperZ = Integer.parseInt(args[3]);
			if (upperZ < lowerZ) {
				// swap them
				int temp = lowerZ;
				lowerZ = upperZ;
				upperZ = temp;
			}
			y = Integer.parseInt(args[4]);
		} catch (NumberFormatException e) {
			connection.getLogger().info("One of the provided coordinates was not a proper number");
			finish();
			return;
		}
		try {
			startingDirection = MovementDirection.valueOf(args[5].toUpperCase());
			secondaryDirection = MovementDirection.valueOf(args[6].toUpperCase());
		} catch (IllegalArgumentException e) {
			connection.getLogger().info("One of the provided movement directions could not be parsed");
			finish();
			return;
		}
		this.movingBack = true;
		this.field = new HorizontalField(lowerX, upperX, lowerZ, upperZ, y, startingDirection, secondaryDirection, true, 1);
		this.locIterator = field.iterator();
		this.lastLocations = new LinkedList<Location>();
		this.queue = connection.getActionQueue();
		connection.getEventHandler().registerListener(this);
		// explicitly reset slot selection
		queue.queue(new MoveTo(connection, field.getStartingLocation().getBlockCenterXZ(), MoveTo.WALKING_SPEED, connection
				.getTicksPerSecond()));
		queue.queue(new CodeAction(connection) {

			@Override
			public void execute() {
				DiggingBot.this.movingBack = false;
			}
		});
		queue.queue(new ChangeSelectedItem(connection, 0));
	}

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
			mineBlocksAndMoveIn(target, direction, BREAK_TIME);
			placeTorch(target);
		} else {
			pickTool();
			queue.queue(new MoveTo(connection, field.getStartingLocation().getBlockCenterXZ(), MoveTo.WALKING_SPEED,
					connection.getTicksPerSecond()));
			queue.queue(new DigDown(connection, field.getStartingLocation().getBlockCenterXZ(), 4, BREAK_TIME * 2));
			field = field.copy(field.getY() - 4);
			locIterator = field.iterator();
			lastLocations.clear();
			queueEmpty(null);
		}
	}

	private void pickTool() {
		queue.queue(new RefillHotbarWithType(connection, Material.DIAMOND_PICKAXE));
		final PickHotbarItemByType picker = new PickHotbarItemByType(connection, Material.DIAMOND_PICKAXE);
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

	private void mineBlocksAndMoveIn(Location loc, MovementDirection direction, int breakTime) {
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
		queue.queue(new MoveTo(connection, loc.getBlockCenterXZ(), MoveTo.SPRINTING_SPEED, connection.getTicksPerSecond()));
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
				mineBlocksAndMoveIn(lastLocations.get(i), field.getCurrentDirection(), BREAK_TIME * 4);
			}
		}
	}

	@Override
	public void finish() {
		connection.getEventHandler().unregisterListener(this);
	}
}
