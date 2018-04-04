package com.github.maxopoly.farming;

import com.github.maxopoly.angeliacore.actions.ActionLock;
import com.github.maxopoly.angeliacore.actions.ActionQueue;
import com.github.maxopoly.angeliacore.actions.CodeAction;
import com.github.maxopoly.angeliacore.actions.actions.DetectAndEatFood;
import com.github.maxopoly.angeliacore.actions.actions.LookAt;
import com.github.maxopoly.angeliacore.actions.actions.LookAtAndBreakBlock;
import com.github.maxopoly.angeliacore.actions.actions.MoveTo;
import com.github.maxopoly.angeliacore.actions.actions.Wait;
import com.github.maxopoly.angeliacore.actions.actions.inventory.ChangeSelectedItem;
import com.github.maxopoly.angeliacore.actions.actions.inventory.ClickInventory;
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
import org.apache.logging.log4j.Logger;
import org.kohsuke.MetaInfServices;

@MetaInfServices(AngeliaPlugin.class)
public class MelonBot extends AngeliaPlugin implements AngeliaListener {

	protected ServerConnection connection;
	private Logger logger;
	protected HorizontalField field;
	protected Iterator<Location> locIterator;
	protected ActionQueue queue;
	protected Material toolUsed;
	protected ItemStack cachedTool;
	protected boolean movingBack;
	private List<Location> lastLocations;
	private MovementDirection dropoffDir;
	private int melons;
	private Location waitingLocation;
	private long waitingTime;

	public MelonBot() {
		super("MelonBot");
		lastLocations = new LinkedList<Location>();
	}

	@Override
	public void start() {
		connection.getEventHandler().registerListener(this);
		if (connection.getPlayerStatus().getHunger() < 20) {
			eat();
		}
		queueEmpty(null);
	}

	private void eat() {
		DetectAndEatFood eat = new DetectAndEatFood(connection);
		queue.queue(eat);
		queue.queue(new CodeAction(connection) {

			@Override
			public ActionLock[] getActionLocks() {
				return new ActionLock[0];
			}

			@Override
			public void execute() {
				if (!eat.foundFood()) {
					logger.info("Disconnecting as no food could be found");
					connection.close(DisconnectReason.Intentional_Disconnect);
				}
			}
		});
	}

	private void breakMelon(Location loc) {
		pickAxe();
		queue.queue(new LookAtAndBreakBlock(connection, loc, getBreakTime()));
		queue.queue(new MoveTo(connection, loc.getBlockCenterXZ(), MoveTo.WALKING_SPEED));
	}

	@AngeliaEventHandler
	public void teleportedBack(TeleportByServerEvent e) {
		if (movingBack) {
			return;
		}
		int index = -1;
		Location oldBlockLoc = e.getLocationTeleportedTo().toBlockLocation();
		for (int i = 0; i < lastLocations.size(); i++) {
			if (lastLocations.get(i).equals(oldBlockLoc)) {
				index = i;
			}
		}
		if (index == -1) {
			return;
		}
		connection.getLogger().info("Detected failed break, rolling back to " + oldBlockLoc.toString());
		queue.clear();
		// usually the block missed will be the next one from where we were teleported to, but lets make sure we
		// dont get out of the lists bounds
		for (int i = index; i < lastLocations.size(); i++) {
			breakMelon(lastLocations.get(i));
		}
	}

	private void atEndOfField() {
		movingBack = true;
		Location start = field.getStartingLocation().getBlockCenterXZ();
		Location player = connection.getPlayerStatus().getLocation();

		Vector toMiddle = Vector.calcLocationDifference(player, waitingLocation);
		Location firstToMiddle = player.addVector(toMiddle.cross(field.getSecondaryDirection().toVector()));
		queue.queue(new MoveTo(connection, firstToMiddle, MoveTo.SPRINTING_SPEED));
		queue.queue(new MoveTo(connection, waitingLocation, MoveTo.SPRINTING_SPEED));
		queue.queue(new Wait(connection, (int) (connection.getTicksPerSecond() * waitingTime)));

		Vector toStart = Vector.calcLocationDifference(waitingLocation, field.getStartingLocation());
		Location backToStart = waitingLocation.addVector(toStart.cross(field.getOriginalPrimaryMovementDirection()
				.toVector()));
		queue.queue(new MoveTo(connection, backToStart, MoveTo.SPRINTING_SPEED));
		queue.queue(new MoveTo(connection, start, MoveTo.SPRINTING_SPEED));
		field = field.copy(field.getY());
		locIterator = field.iterator();
		lastLocations.clear();
		queue.queue(new CodeAction(connection) {

			@Override
			public ActionLock[] getActionLocks() {
				return new ActionLock[0];
			}

			@Override
			public void execute() {
				movingBack = false;
			}
		});
	}

	private void pickAxe() {
		Inventory hotbar = connection.getPlayerStatus().getPlayerInventory().getHotbar();
		for (int i = 0; i < hotbar.getSize(); i++) {
			ItemStack is = hotbar.getSlot(i);
			if (is == null) {
				continue;
			}
			if (is.getMaterial() == Material.DIAMOND_AXE) {
				if (is.getDamage() >= (is.getMaterial().getMaximumDurability() - 5)) {
					continue;
				}
				// valid axe found
				cachedTool = is;
				queue.queue(new ChangeSelectedItem(connection, i));
				return;
			}
		}
		connection.getLogger().info("Could not find an axe in inventory, logging off");
		connection.close(DisconnectReason.Intentional_Disconnect);
	}

	private int getBreakTime() {
		return BreakTimeCalculator.getBreakTicks(1, true, true, cachedTool, connection.getPlayerStatus()
				.getActivePotionEffects(), connection.getTicksPerSecond());
	}

	@AngeliaEventHandler
	public void queueEmpty(ActionQueueEmptiedEvent e) {
		if (locIterator.hasNext()) {
			Location currLoc = connection.getPlayerStatus().getLocation().toBlockLocation();
			if (field.isAtSide(dropoffDir, currLoc)) {
				dropMelons();
			}
			Location target = locIterator.next();
			lastLocations.add(target);
			if (lastLocations.size() > 10) {
				lastLocations.remove(0);
			}
			breakMelon(target);
		} else {
			atEndOfField();
		}
	}

	private void dropMelons() {
		Vector dropVector = dropoffDir.toVector().multiply(5);
		queue.queue(new LookAt(connection, connection.getPlayerStatus().getLocation()
				.relativeBlock(dropVector.getX(), 0, dropVector.getZ())));
		PlayerInventory inv = connection.getPlayerStatus().getPlayerInventory();
		Inventory storageInv = inv.getPlayerStorage();
		int thrown = 0;
		for (int i = 0; i < storageInv.getSize(); i++) {
			ItemStack is = storageInv.getSlot(i);
			if (is == null || is.getMaterial() == Material.EMPTY_SLOT) {
				continue;
			}
			if (is.getMaterial() == Material.MELON_BLOCK) {
				// throw entire stack
				queue.queue(new ClickInventory(connection, (byte) 0, inv.translateStorageSlotToTotal(i), (byte) 1, 4,
						is));
				thrown += is.getAmount();
			}
		}
		melons += thrown;
		if (thrown != 0) {
			connection.getLogger().info(
					String.format("Threw %d melons into the collection, total of %d harvest so far", thrown, melons));
		}
	}

	@AngeliaEventHandler
	public void hungerChange(HungerChangeEvent e) {
		if (e.getNewValue() > e.getOldValue()) {
			return;
		}
		eat();
	}

	@AngeliaEventHandler
	public void damaged(HealthChangeEvent e) {
		if (e.getNewValue() < e.getOldValue()) {
			connection.getLogger().info("Received damage, health is " + e.getNewValue() + ". Logging off");
			connection.close(DisconnectReason.Intentional_Disconnect);
		}
	}

	@Override
	public String getHelp() {
		return "Harvests melons separated by 2 rows of stems until it runs out of axes or food";
	}

	@Override
	protected void parseOptions(ServerConnection connection, Map<String, List<String>> args) {
		this.connection = connection;
		this.logger = connection.getLogger();
		this.queue = connection.getActionQueue();
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
			dropoffDir = MovementDirection.valueOf(args.get("wdir").get(0).toUpperCase());
		} catch (IllegalArgumentException e) {
			connection.getLogger().info("One of the provided movement directions could not be parsed");
			finish();
			return;
		}
		this.field = new HorizontalField(lowerX, upperX, lowerZ, upperZ, y, startingDirection, secondaryDirection,
				true, new int[] { 3, 1 });
		this.locIterator = field.iterator();
		if (args.containsKey("wt")) {
			try {
				int x = Integer.parseInt(args.get("wx").get(0));
				int z = Integer.parseInt(args.get("wz").get(0));
				waitingTime = Integer.parseInt(args.get("wt").get(0));
				waitingLocation = new Location(x, y, z).toBlockLocation().getBlockCenterXZ();
			} catch (NumberFormatException e) {
				connection.getLogger().info("The waiting data given contained malformed numbers");
				finish();
				return;
			}
		}
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
				MelonBot.this.movingBack = false;
			}

			@Override
			public ActionLock[] getActionLocks() {
				return new ActionLock[] { ActionLock.MOVEMENT };
			}
		});

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
		options.add(Option.builder("wt").longOpt("waitingTime").numberOfArgs(1).required(false)
				.desc("The time the bot will wait at the waiting location in second").build());
		options.add(Option.builder("wx").longOpt("waitingX").numberOfArgs(1).required(false)
				.desc("x coordinate of the waiting position").build());
		options.add(Option.builder("wz").longOpt("waitingZ").numberOfArgs(1).required(false)
				.desc("z coordinate of the waiting position").build());
		options.add(Option.builder("ff").longOpt("fast-forward").hasArg(false).required(false).build());
		options.add(Option.builder("wdir").longOpt("waterDirection").numberOfArgs(1).required()
				.desc("The side of the field on which the bot is supposed to throw out melons").build());
		return options;
	}

	@Override
	public AngeliaPlugin transistionToNewConnection(ServerConnection newConnection) {
		tearDown();
		this.connection = newConnection;
		this.logger = newConnection.getLogger();
		this.queue = newConnection.getActionQueue();
		connection.getEventHandler().registerListener(this);
		return this;
	}

}
