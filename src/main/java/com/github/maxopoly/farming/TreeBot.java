package com.github.maxopoly.farming;

import com.github.maxopoly.angeliacore.actions.ActionLock;
import com.github.maxopoly.angeliacore.actions.ActionQueue;
import com.github.maxopoly.angeliacore.actions.CodeAction;
import com.github.maxopoly.angeliacore.actions.actions.DetectAndEatFood;
import com.github.maxopoly.angeliacore.actions.actions.LookAt;
import com.github.maxopoly.angeliacore.actions.actions.LookAtAndBreakBlock;
import com.github.maxopoly.angeliacore.actions.actions.LookAtAndPlaceBlock;
import com.github.maxopoly.angeliacore.actions.actions.MoveTo;
import com.github.maxopoly.angeliacore.actions.actions.inventory.ChangeSelectedItem;
import com.github.maxopoly.angeliacore.actions.actions.inventory.ClickInventory;
import com.github.maxopoly.angeliacore.connection.DisconnectReason;
import com.github.maxopoly.angeliacore.connection.ServerConnection;
import com.github.maxopoly.angeliacore.connection.play.packets.out.ChatPacket;
import com.github.maxopoly.angeliacore.event.AngeliaEventHandler;
import com.github.maxopoly.angeliacore.event.AngeliaListener;
import com.github.maxopoly.angeliacore.event.events.ActionQueueEmptiedEvent;
import com.github.maxopoly.angeliacore.event.events.HungerChangeEvent;
import com.github.maxopoly.angeliacore.event.events.PlayerSpawnEvent;
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
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.cli.Option;
import org.kohsuke.MetaInfServices;

@MetaInfServices(AngeliaPlugin.class)
public class TreeBot extends AngeliaPlugin implements AngeliaListener {

	private static final List<String> allowedPlayers = Arrays.asList(new String[] { "awoo", "BlueSylvaer", "Smim02", "SafeSpace",
			"RexDrillerson", "ZombieReagan", "AltVault", "Jezza" });

	private static final int leafBreakTime = 7;

	private HorizontalField field;
	private ServerConnection connection;
	private Iterator<Location> locIterator;
	private ActionQueue queue;

	private int lineTreeCounter;
	private int treesPerLine = 30;
	private boolean movingBack;

	private List<List<Location>> pastSegements;
	private ItemStack cachedTool;

	public TreeBot() {
		super("TreeBot");
	}

	@Override
	public void start() {
		connection.getEventHandler().registerListener(this);
		// ensure we have an axe right away and trigger queue empty listener
		pickAxe();
		throwOutStuff();
	}

	@AngeliaEventHandler
	public void queueEmpty(ActionQueueEmptiedEvent e) {
		if (!locIterator.hasNext()) {
			handleEndOffField();
			return;
		}
		if (lineTreeCounter == treesPerLine) {
			movingBack = true;
			switchToNewLine();
			lineTreeCounter = 0;
		} else {
			List<Location> segment = getNextSegment();
			addSegment(segment);
			handleTree(segment);
			lineTreeCounter++;
		}
	}

	@AngeliaEventHandler
	public void playerNearby(PlayerSpawnEvent e) {
		if (e.getOnlinePlayer() != null) {
			if (allowedPlayers.contains(e.getOnlinePlayer().getName())) {
				connection.getLogger().info(
						e.getOnlinePlayer().getName()
								+ " entered radar distance, but he is whitelisted, so ignoring it");
				return;
			}
			queue.clear();
			connection.getLogger().info("Logging off, because " + e.getOnlinePlayer().getName() + " is nearby");
		}
		ChatPacket packet;
		try {
			packet = new ChatPacket("/logout");
		} catch (IOException ex) {
			connection.getLogger().error("Failed to create logout msg", e);
			System.exit(1);
			return;
		}
		try {
			connection.sendPacket(packet);
		} catch (IOException ex) {
			connection.getLogger().error("Failed to send message, server might have disconnected?");
			System.exit(1);
		}
		new java.util.Timer().schedule(new java.util.TimerTask() {
			@Override
			public void run() {
				System.exit(1);
			}
		}, 12000);
	}

	private void handleTree(List<Location> segment) {
		Location firstLeaf = segment.get(0).relativeBlock(0, 1, 0);
		Location secondLeaf = segment.get(1).relativeBlock(0, 1, 0);
		Location bottomTreeBlock = segment.get(2);
		Location upperTreeBlock = bottomTreeBlock.relativeBlock(0, 1, 0);
		pickStick();
		queue.queue(new LookAtAndPlaceBlock(connection, bottomTreeBlock));
		queue.queue(new LookAtAndBreakBlock(connection, firstLeaf, leafBreakTime));
		queue.queue(new LookAtAndBreakBlock(connection, secondLeaf, leafBreakTime));
		pickAxe();
		int breakTicks = BreakTimeCalculator.getBreakTicks(2.0, true, true, cachedTool, connection.getPlayerStatus()
				.getActivePotionEffects(), connection.getTicksPerSecond());
		queue.queue(new LookAtAndBreakBlock(connection, bottomTreeBlock, breakTicks));
		queue.queue(new LookAtAndBreakBlock(connection, upperTreeBlock, breakTicks));
		queue.queue(new MoveTo(connection, bottomTreeBlock.getBlockCenterXZ(), MoveTo.SPRINTING_SPEED));
		Location logBlock = upperTreeBlock;
		for (int i = 0; i < 4; i++) {
			logBlock = logBlock.relativeBlock(0, 1, 0);
			queue.queue(new LookAtAndBreakBlock(connection, logBlock, breakTicks));
		}
		pickSapling();
		queue.queue(new LookAtAndPlaceBlock(connection, bottomTreeBlock.relativeBlock(0, -1, 0)));
		pickStick();
		queue.queue(new LookAtAndBreakBlock(connection, segment.get(3).relativeBlock(0, 1, 0), leafBreakTime));
		queue.queue(new LookAtAndBreakBlock(connection, segment.get(4).relativeBlock(0, 1, 0), leafBreakTime));
		queue.queue(new MoveTo(connection, segment.get(5).getBlockCenterXZ(), MoveTo.SPRINTING_SPEED));
	}

	private void switchToNewLine() {
		Location nextLine = fastForward(3).getBlockCenterXZ();
		// go sidewards
		queue.queue(new MoveTo(connection, nextLine, MoveTo.SPRINTING_SPEED));
		// move up to be ready for next tree
		Location finalLoc = fastForward(3);
		queue.queue(new MoveTo(connection, finalLoc.getBlockCenterXZ(), MoveTo.SPRINTING_SPEED));
		throwOutStuff();
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
		for (int i = 0; i < pastSegements.size(); i++) {
			if (pastSegements.get(i).contains(oldBlockLoc)) {
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
		for (int i = index; i < pastSegements.size(); i++) {
			handleTree(pastSegements.get(i));
		}
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

	private void pickSapling() {
		Inventory hotbar = connection.getPlayerStatus().getPlayerInventory().getHotbar();
		for (int i = 0; i < hotbar.getSize(); i++) {
			ItemStack is = hotbar.getSlot(i);
			if (is == null) {
				continue;
			}
			if (is.getMaterial() == Material.SAPLING) {
				queue.queue(new ChangeSelectedItem(connection, i));
				return;
			}
		}
		connection.getLogger().info("Could not find any saplings in inventory, logging off");
		connection.close(DisconnectReason.Intentional_Disconnect);
	}

	private void throwOutStuff() {
		MovementDirection dir = field.getCurrentDirection();
		MovementDirection toTurnTo = null;
		for (MovementDirection car : MovementDirection.CARDINAL) {
			if (car != dir && !(car.getOpposite() == dir)) {
				toTurnTo = car;
			}
		}
		Vector turnVector = toTurnTo.toVector().multiply(3);
		Vector leafInTheWayVector = toTurnTo.toVector();
		pickStick();
		queue.queue(new LookAtAndBreakBlock(connection, connection.getPlayerStatus().getLocation()
				.relativeBlock(leafInTheWayVector.getX(), 1, leafInTheWayVector.getZ()), leafBreakTime));
		queue.queue(new LookAt(connection, connection.getPlayerStatus().getLocation()
				.relativeBlock(turnVector.getX(), -1, turnVector.getZ()).getBlockCenter()));
		PlayerInventory inv = connection.getPlayerStatus().getPlayerInventory();
		Inventory storageInv = inv.getPlayerStorageWithoutHotbar();
		for (int i = 0; i < storageInv.getSize(); i++) {
			ItemStack is = storageInv.getSlot(i);
			if (is == null || is.getMaterial() == Material.EMPTY_SLOT) {
				continue;
			}
			if (is.getMaterial() == Material.LOG || is.getMaterial() == Material.APPLE) {
				// throw entire stack
				queue.queue(new ClickInventory(connection, (byte) 0, inv.translateStorageSlotToTotal(i), (byte) 1, 4, is));
			}
		}
		Inventory hotbar = inv.getHotbar();
		int emptySlots = 0;
		for (int i = 0; i < hotbar.getSize(); i++) {
			ItemStack is = hotbar.getSlot(i);
			if (is == null || is.getMaterial() == Material.EMPTY_SLOT) {
				emptySlots++;
				continue;
			}
			if (is.getMaterial() == Material.LOG) {
				emptySlots++;
				// throw entire stack
				queue.queue(new ClickInventory(connection, (byte) 0, inv.translateHotbarToTotal(i), (byte) 1, 4, is));
			}
		}
		int pulled = 0;
		for (int i = 0; pulled < emptySlots && i < storageInv.getSize(); i++) {
			ItemStack is = storageInv.getSlot(i);
			if (is == null || is.getMaterial() == Material.EMPTY_SLOT) {
				continue;
			}
			if (is.getMaterial() == Material.SAPLING) {
				// shift click in to refill hotbar
				queue.queue(new ClickInventory(connection, (byte) 0, inv.translateStorageSlotToTotal(i), (byte) 1, 1, is));
				pulled++;
			}
		}
	}

	private void pickStick() {
		Inventory hotbar = connection.getPlayerStatus().getPlayerInventory().getHotbar();
		for (int i = 0; i < hotbar.getSize(); i++) {
			ItemStack is = hotbar.getSlot(i);
			if (is == null) {
				continue;
			}
			if (is.getMaterial() == Material.STICK) {
				queue.queue(new ChangeSelectedItem(connection, i));
				return;
			}
		}
		connection.getLogger().info("Could not find any sticks in inventory, logging off");
		connection.close(DisconnectReason.Intentional_Disconnect);
	}

	private void handleEndOffField() {
		connection.getLogger().info("Completely harvest, logging off");
		connection.close(DisconnectReason.Intentional_Disconnect);
	}

	private void addSegment(List<Location> segment) {
		if (pastSegements.size() >= 10) {
			pastSegements.remove(0);
		}
		pastSegements.add(segment);
	}

	public Location fastForward(int count) {
		for (int i = 0; i < count - 1; i++) {
			locIterator.next();
		}
		return locIterator.next();
	}

	@Override
	public String getHelp() {
		return "";
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
		return options;
	}

	@Override
	protected void parseOptions(ServerConnection connection, Map<String, List<String>> args) {
		this.connection = connection;
		this.movingBack = false;
		this.queue = connection.getActionQueue();
		this.pastSegements = new LinkedList<List<Location>>();
		// explicitly reset slot selection and ensure axe is cached
		pickAxe();
		this.lineTreeCounter = 0;
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
		this.field = new HorizontalField(lowerX, upperX, lowerZ, upperZ, y, startingDirection, secondaryDirection, true, 3);
		this.locIterator = field.iterator();
		if (args.containsKey("ff")) {
			// fast forward to current location
			Location playerLoc = connection.getPlayerStatus().getLocation().toBlockLocation();
			boolean found = false;
			// check starting location
			if (playerLoc.equals(field.getStartingLocation())) {
				found = true;
			}
			boolean startFound = false;
			for (int i = 0; i < 3; i++) {
				Location loc = locIterator.next();
				if (loc.equals(playerLoc)) {
					startFound = true;
				}
				if (i == 3 && startFound) {
					queue.queue(new MoveTo(connection, loc.getBlockCenterXZ(), MoveTo.SPRINTING_SPEED));
					found = true;
				}
			}
			while (!found && locIterator.hasNext()) {
				// handle proper restarting from sidewards movement
				if (lineTreeCounter == treesPerLine) {
					lineTreeCounter = 0;
					Location firstCorner = null;
					boolean foundLoc = false;
					for (int i = 0; i < 3; i++) {
						Location vertLoc = locIterator.next();
						if (vertLoc.equals(playerLoc)) {
							foundLoc = true;
						}
						if (i == 2 && foundLoc) {
							firstCorner = vertLoc;
						}
					}
					if (firstCorner != null) {
						queue.queue(new MoveTo(connection, firstCorner.getBlockCenterXZ(), MoveTo.SPRINTING_SPEED));
						queue.queue(new MoveTo(connection, fastForward(3).getBlockCenterXZ(), MoveTo.SPRINTING_SPEED));
						found = true;
					}
					Location restartLoc = null;
					for (int i = 0; i < 3; i++) {
						Location vertLoc = locIterator.next();
						if (vertLoc.equals(playerLoc)) {
							foundLoc = true;
						}
						if (i == 2 && foundLoc) {
							found = true;
							restartLoc = vertLoc;
						}
					}
					if (restartLoc != null) {
						queue.queue(new MoveTo(connection, restartLoc.getBlockCenterXZ(), MoveTo.SPRINTING_SPEED));
						found = true;
						break;
					}

				} else {
					List<Location> segment = getNextSegment();
					addSegment(segment);
					lineTreeCounter++;
					if (segment.contains(playerLoc)) {
						handleTree(segment);
						found = true;
					}
				}
			}
			if (!found) {
				queue.queue(new MoveTo(connection, field.getStartingLocation().getBlockCenterXZ(), MoveTo.SPRINTING_SPEED));
				fastForward(3);
			}
		} else {
			queue.queue(new MoveTo(connection, field.getStartingLocation().getBlockCenterXZ(), MoveTo.SPRINTING_SPEED));
			fastForward(3);
		}
	}

	private List<Location> getNextSegment() {
		List<Location> locs = new LinkedList<Location>();
		for (int i = 0; i < 6; i++) {
			locs.add(locIterator.next());
		}
		return locs;
	}

	@Override
	public void tearDown() {
		connection.getEventHandler().unregisterListener(this);
	}

	@Override
	public AngeliaPlugin transistionToNewConnection(ServerConnection newConnection) {
		this.connection = newConnection;
		this.queue = newConnection.getActionQueue();
		connection.getEventHandler().registerListener(this);
		return this;
	}

}
