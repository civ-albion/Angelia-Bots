package com.github.maxopoly.digging;

import com.github.maxopoly.angeliacore.actions.ActionLock;
import com.github.maxopoly.angeliacore.actions.ActionQueue;
import com.github.maxopoly.angeliacore.actions.CodeAction;
import com.github.maxopoly.angeliacore.actions.actions.DetectAndEatFood;
import com.github.maxopoly.angeliacore.actions.actions.LookAtAndBreakBlock;
import com.github.maxopoly.angeliacore.actions.actions.LookAtAndPlaceBlock;
import com.github.maxopoly.angeliacore.actions.actions.MoveTo;
import com.github.maxopoly.angeliacore.actions.actions.Wait;
import com.github.maxopoly.angeliacore.actions.actions.inventory.ChangeSelectedItem;
import com.github.maxopoly.angeliacore.actions.actions.inventory.PickHotbarItemByType;
import com.github.maxopoly.angeliacore.connection.DisconnectReason;
import com.github.maxopoly.angeliacore.connection.ServerConnection;
import com.github.maxopoly.angeliacore.event.AngeliaEventHandler;
import com.github.maxopoly.angeliacore.event.AngeliaListener;
import com.github.maxopoly.angeliacore.event.events.ActionQueueEmptiedEvent;
import com.github.maxopoly.angeliacore.event.events.HealthChangeEvent;
import com.github.maxopoly.angeliacore.event.events.HungerChangeEvent;
import com.github.maxopoly.angeliacore.event.events.TeleportByServerEvent;
import com.github.maxopoly.angeliacore.model.inventory.Inventory;
import com.github.maxopoly.angeliacore.model.inventory.PlayerInventory;
import com.github.maxopoly.angeliacore.model.item.ItemStack;
import com.github.maxopoly.angeliacore.model.item.Material;
import com.github.maxopoly.angeliacore.model.location.Location;
import com.github.maxopoly.angeliacore.model.location.MovementDirection;
import com.github.maxopoly.angeliacore.model.location.Vector;
import com.github.maxopoly.angeliacore.plugin.AngeliaPlugin;
import com.github.maxopoly.angeliacore.util.BreakTimeCalculator;
import com.github.maxopoly.angeliacore.util.HorizontalField;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.cli.Option;

public abstract class AbstractMiningBot extends AngeliaPlugin implements AngeliaListener {

	private static final int locationCacheSize = 10;
	private static final int assumedCaveLength = 8;

	protected ServerConnection connection;
	protected HorizontalField field;
	protected Iterator<Location> locIterator;
	protected ActionQueue queue;
	protected List<LocationDirectionTuple> lastLocations;
	protected boolean movingBack;
	protected Material toolUsed;
	protected int snakeLineDistance;
	protected int tunnelHeight;
	protected ItemStack cachedTool;
	protected int bridgingLeftOver;

	public AbstractMiningBot(String name, Material toolUsed, int snakeLineDistance) {
		super(name);
		this.toolUsed = toolUsed;
		this.snakeLineDistance = snakeLineDistance;
	}

	@Override
	public void start() {
		this.queue = connection.getActionQueue();
		this.lastLocations = new LinkedList<LocationDirectionTuple>();
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
			lastLocations.add(new LocationDirectionTuple(target, direction));
			if (lastLocations.size() > locationCacheSize) {
				lastLocations.remove(0);
			}
			handleLocation(target, direction, false);
			placeTorch(target);
		} else {
			atEndOfField();
		}
	}

	protected void pickTool() {
		// queue.queue(new RefillHotbarWithType(connection, toolUsed));
		Inventory hotbar = connection.getPlayerStatus().getPlayerInventory().getHotbar();
		int slot = -1;
		for (int i = 0; i < hotbar.getSize(); i++) {
			ItemStack is = hotbar.getSlot(i);
			if (is == null || is.isEmpty() || !(is.getMaterial() == toolUsed)) {
				continue;
			}
			if (is.isEnchanted() && is.getDamage() >= (is.getMaterial().getMaximumDurability() - 15)) {
				continue;
			}
			slot = i;
			break;
		}
		if (slot == -1) {
			connection.getLogger().info("Could not find a tool to dig, exiting");
			connection.close(DisconnectReason.Intentional_Disconnect);
		}
		this.cachedTool = connection.getPlayerStatus().getPlayerInventory().getHotbar().getSlot(slot);
		queue.queue(new ChangeSelectedItem(connection, slot));
	}

	@Override
	protected void parseOptions(ServerConnection connection, Map<String, List<String>> args) {
		this.connection = connection;
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
		if (args.containsKey("h")) {
			// height
			try {
				tunnelHeight = Integer.parseInt(args.get("h").get(0));
			} catch (NumberFormatException e) {
				connection.getLogger().warn("The provided tunnel height " + args.get("h").get(0) + " could not be parsed");
				finish();
				return;
			}
		} else {
			// default tunnel height to 4
			tunnelHeight = 4;
		}
		if (tunnelHeight < 2 || tunnelHeight > 6) {
			connection.getLogger().warn(
					"Tunnel height must be between 2 and 6, " + tunnelHeight + " is outside of this range");
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
			// check starting location
			if (playerLoc.equals(field.getStartingLocation())) {
				found = true;
			}
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
		queue.queue(new MoveTo(connection, field.getStartingLocation().getBlockCenterXZ(), MoveTo.SPRINTING_SPEED));
		queue.queue(new CodeAction(connection) {

			@Override
			public void execute() {
				AbstractMiningBot.this.movingBack = false;
			}

			@Override
			public ActionLock[] getActionLocks() {
				return new ActionLock[] { ActionLock.MOVEMENT };
			}
		});
	}

	protected int getBreakTime(final boolean rollback) {
		// gravel worst case, stone best case
		double hardness = rollback ? 0.6 : 1.5;
		return BreakTimeCalculator.getBreakTicks(hardness, true, !rollback, cachedTool, connection.getPlayerStatus()
				.getActivePotionEffects(), connection.getTicksPerSecond());
	}

	protected void handleLocation(Location loc, MovementDirection direction, boolean rollBack) {
		pickTool();
		// calculate breaking time
		int breakTime = getBreakTime(rollBack);
		Location currentLoc = loc.relativeBlock(0, -1, 0).addVector(direction.getOpposite().toVector());
		// if the player just got teleported back due to walking over a ledge, he might already be on the next block and
		// we need to reconstruct the direction he came from
		double xDiff = Math.abs(currentLoc.getX() - currentLoc.getBlockX());
		double zDiff = Math.abs(currentLoc.getZ() - currentLoc.getBlockZ());
		if (xDiff >= 0.7 || xDiff <= 0.3 || zDiff >= 0.7 || zDiff <= 0.3) {
			// probably on the ledge
			// currentLoc = currentLoc.addVector(field.getCurrentDirection().getOpposite().toVector());
		}
		Location upperLoc = new Location(loc.getBlockX(), (int) loc.getY() + 1, (int) loc.getZ());
		// due to anticheat line of sight checks we have to first mine the block at the same y-level as the player's
		// head
		if (tunnelHeight >= 2) {
			queue.queue(new LookAtAndBreakBlock(connection, upperLoc, breakTime));
		}
		queue.queue(new LookAtAndBreakBlock(connection, loc, breakTime));
		// mine as many blocks upwards as needed
		for (int i = 2; i < tunnelHeight; i++) {
			Location toMine = new Location(loc.getBlockX(), (int) loc.getY() + i, (int) loc.getZ());
			queue.queue(new LookAtAndBreakBlock(connection, toMine, breakTime));
		}
		// always place block
		PickHotbarItemByType pick = new PickHotbarItemByType(connection, Material.COBBLESTONE);
		queue.queue(pick);
		queue.queue(new CodeAction(connection) {

			@Override
			public ActionLock[] getActionLocks() {
				return new ActionLock[0];
			}

			@Override
			public void execute() {
				if (!pick.wasFound()) {
					new PickHotbarItemByType(connection, Material.STONE).execute();
				}

			}
		});
		Location desto = loc.getBlockCenterXZ();
		queue.queue(new MoveTo(connection, desto.getMiddle(loc.getBlockCenterXZ()), MoveTo.SPRINTING_SPEED));
		queue.queue(new LookAtAndPlaceBlock(connection, currentLoc, direction.toBlockFace()));
		queue.queue(new MoveTo(connection, desto, MoveTo.SPRINTING_SPEED));

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
		// intentionally disabled for now
		mineAgain = false;
		if (mineAgain) {
			pickTool();
			for (int i = 2; i < tunnelHeight; i++) {
				Location toMine = new Location(loc.getBlockX(), (int) loc.getY() + i, (int) loc.getZ());
				queue.queue(new LookAtAndBreakBlock(connection, toMine, breakTime));
			}
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
		if (e.getNewValue() > e.getOldValue()) {
			return;
		}
		queue.queue(new DetectAndEatFood(connection));
	}

	@AngeliaEventHandler
	public void teleportedBack(TeleportByServerEvent e) {
		if (movingBack) {
			return;
		}
		int index = -1;
		Location oldBlockLoc = e.getLocationTeleportedTo().toBlockLocation();
		for (int i = 0; i < lastLocations.size(); i++) {
			if (lastLocations.get(i).location.equals(oldBlockLoc)) {
				index = i + 1;
			}
		}
		if (index == -1) {
			return;
		}
		connection.getLogger().info("Detected failed break, rolling back to " + oldBlockLoc.toString());
		queue.clear();
		// usually the block missed will be the next one from where we were teleported to, but lets make sure we
		// dont get out of the lists bounds
		if (index == lastLocations.size()) {
			index--;
		}
		if (index >= lastLocations.size()) {
			return;
		}
		// there are two possible causes at this point, either we didnt break a block that we think we broke or there is a
		// hole that we cant walk into
		// the decimal of the location we got teleported to actually let's us determine that
		LocationDirectionTuple current = lastLocations.get(index);
		Vector pos = e.getLocationTeleportedTo().toVector();
		Vector backSet = pos.add(current.direction.getOpposite().toVector().multiply(0.35));
		Location backLoc = new Location(backSet);
		// if the player is just barely standing on the next block, this location will be on the previous block
		boolean rollBack = true;
		if (!backLoc.toBlockLocation().equals(oldBlockLoc)) {
			// lets place a block to stand on
			queue.queue(new PickHotbarItemByType(connection, Material.COBBLESTONE));
			queue.queue(new LookAtAndPlaceBlock(connection, backLoc.toBlockLocation().relativeBlock(0.0, -1.0, 0.0),
					current.direction.toBlockFace()));
			bridgingLeftOver = assumedCaveLength;
			rollBack = false;
		}

		for (int i = index; i < lastLocations.size(); i++) {
			// maybe we hit something harder, so let's take extra time
			LocationDirectionTuple tup = lastLocations.get(i);
			handleLocation(tup.location, tup.direction, rollBack);
		}

	}

	// why is there nothing built in for tuples in java?
	private class LocationDirectionTuple {
		private Location location;
		private MovementDirection direction;

		private LocationDirectionTuple(Location loc, MovementDirection dir) {
			this.location = loc;
			this.direction = dir;
		}
	}

	@AngeliaEventHandler
	public void damaged(HealthChangeEvent e) {
		if (e.getNewValue() < e.getOldValue() && e.getNewValue() < 10) {
			connection.getLogger().info("Received damage, health is " + e.getNewValue() + ". Logging off");
			connection.close(DisconnectReason.Intentional_Disconnect);
		}
	}

	/**
	 * Sets up some stuff for copies for a new connection
	 */
	protected void enrichCopy(AbstractMiningBot miner, ServerConnection newConnection) {
		miner.field = this.field;
		miner.queue = newConnection.getActionQueue();
		miner.lastLocations = this.lastLocations;
		miner.movingBack = this.movingBack;
		miner.toolUsed = this.toolUsed;
		miner.snakeLineDistance = this.snakeLineDistance;
		miner.tunnelHeight = this.tunnelHeight;
		miner.cachedTool = this.cachedTool;

	}

	@Override
	public void tearDown() {
		connection.getEventHandler().unregisterListener(this);
	}

	@Override
	protected List<Option> createOptions() {
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
		options.add(Option.builder("h").longOpt("height")
				.desc("How many blocks the bot should mine per location. Defaults to 4, must be an integer between 2 and 6")
				.numberOfArgs(1).required(false).build());
		return options;
	}

}
