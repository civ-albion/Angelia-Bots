package com.github.maxopoly.farming;

import com.github.maxopoly.angeliacore.actions.ActionLock;
import com.github.maxopoly.angeliacore.actions.ActionQueue;
import com.github.maxopoly.angeliacore.actions.CodeAction;
import com.github.maxopoly.angeliacore.actions.actions.DetectAndEatFood;
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
import com.github.maxopoly.angeliacore.event.events.HealthChangeEvent;
import com.github.maxopoly.angeliacore.event.events.HungerChangeEvent;
import com.github.maxopoly.angeliacore.model.inventory.Inventory;
import com.github.maxopoly.angeliacore.model.inventory.PlayerInventory;
import com.github.maxopoly.angeliacore.model.item.ItemStack;
import com.github.maxopoly.angeliacore.model.item.Material;
import com.github.maxopoly.angeliacore.model.location.Location;
import com.github.maxopoly.angeliacore.model.location.MovementDirection;
import com.github.maxopoly.angeliacore.plugin.AngeliaPlugin;
import com.github.maxopoly.angeliacore.util.HorizontalField;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.cli.Option;
import org.apache.logging.log4j.Logger;
import org.kohsuke.MetaInfServices;

@MetaInfServices(AngeliaPlugin.class)
public class PlatformBot extends AngeliaPlugin implements AngeliaListener {

	protected ServerConnection connection;
	private Logger logger;
	protected HorizontalField field;
	protected Iterator<Location> locIterator;
	protected ActionQueue queue;
	protected boolean movingBack;
	private Material blockToPlace;
	private int refillCounter;

	public PlatformBot() {
		super("PlatformBot");
	}

	@Override
	public void start() {
		connection.getEventHandler().registerListener(this);
		if (connection.getPlayerStatus().getHunger() < 20) {
			eat();
		}
		sendChatMsg("/cto");
		pickBlock(Material.STONE);
		queue.queue(new CodeAction(connection) {

			@Override
			public ActionLock[] getActionLocks() {
				return new ActionLock[0];
			}

			@Override
			public void execute() {
				sendChatMsg("/ctf");
			}
		});
	}


	@AngeliaEventHandler
	public void queueEmpty(ActionQueueEmptiedEvent e) {
		if (locIterator.hasNext()) {
			refillCounter++;
			if (refillCounter % 128 == 0) {
				refillHotbar();
			}
			Location target = locIterator.next().toBlockLocation();
			MovementDirection dir = field.getCurrentDirection();
			queue.queue(new MoveTo(connection, target.getBlockCenterXZ(), MoveTo.WALKING_SPEED));
			pickBlock(blockToPlace);
			queue.queue(new LookAtAndPlaceBlock(connection, target.relativeBlock(0, -1, 0), dir.toBlockFace()));
		} else {
			connection.getLogger().info("Reached end of platform. Logging off");
			connection.close(DisconnectReason.Intentional_Disconnect);
		}
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

	private void refillHotbar() {
		int pulled = 0;
		int emptySlots = 0;
		PlayerInventory inv = connection.getPlayerStatus().getPlayerInventory();
		Inventory storageInv = inv.getPlayerStorageWithoutHotbar();
		for (int i = 0; pulled < emptySlots && i < storageInv.getSize(); i++) {
			ItemStack is = storageInv.getSlot(i);
			if (is == null || is.getMaterial() == Material.EMPTY_SLOT) {
				continue;
			}
			if (is.getMaterial() == blockToPlace) {
				// shift click in to refill hotbar
				queue.queue(new ClickInventory(connection, (byte) 0, inv.translateStorageSlotToTotal(i), (byte) 1, 1, is));
				pulled++;
			}
		}
	}

	private void sendChatMsg(String msg) {
		ChatPacket packet;
		try {
			packet = new ChatPacket(msg);
		} catch (IOException e) {
			logger.error("Failed to create msg packet with msg " + msg, e);
			return;
		}
		try {
			connection.sendPacket(packet);
		} catch (IOException e) {
			logger.error("Failed to send message, server might have disconnected?");
		}
	}

	private void pickBlock(Material mat) {
		Inventory hotbar = connection.getPlayerStatus().getPlayerInventory().getHotbar();
		for (int i = 0; i < hotbar.getSize(); i++) {
			ItemStack is = hotbar.getSlot(i);
			if (is == null) {
				continue;
			}
			if (is.getMaterial() == mat) {
				queue.queue(new ChangeSelectedItem(connection, i));
				return;
			}
		}
		connection.getLogger().info("Could not find any saplings in inventory, logging off");
		connection.close(DisconnectReason.Intentional_Disconnect);
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
		return "Builds a platform";
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
		} catch (IllegalArgumentException e) {
			connection.getLogger().info("One of the provided movement directions could not be parsed");
			finish();
			return;
		}
		String block = args.get("b").get(0);
		blockToPlace = Material.valueOf(block.toUpperCase());
		this.field = new HorizontalField(lowerX, upperX, lowerZ, upperZ, y, startingDirection, secondaryDirection, true, 1);
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
				PlatformBot.this.movingBack = false;
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
				.desc("lower and upper x coordinates limiting the bounding box within which the bot will place").build());
		options.add(Option.builder("z").longOpt("z").numberOfArgs(2).required()
				.desc("lower and upper z coordinates limiting the bounding box within which the bot will place").build());
		options.add(Option.builder("dir1").longOpt("startingDirection").numberOfArgs(1).required()
				.desc("Cardinal direction in which the bot will begin to move").build());
		options.add(Option.builder("dir2").longOpt("secondaryDirection").numberOfArgs(1).required()
				.desc("Secondary direction in which the bot will move after finish a line").build());
		options.add(Option.builder("ff").longOpt("fast-forward").hasArg(false).required(false).build());
		options.add(Option.builder("b").longOpt("block").numberOfArgs(1).required(true).build());
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
