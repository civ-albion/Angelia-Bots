package com.github.maxopoly.digging;

import com.github.maxopoly.angeliacore.actions.ActionQueue;
import com.github.maxopoly.angeliacore.actions.actions.DetectAndEatFood;
import com.github.maxopoly.angeliacore.actions.actions.LookAtAndBreakBlock;
import com.github.maxopoly.angeliacore.actions.actions.LookAtAndPlaceBlock;
import com.github.maxopoly.angeliacore.actions.actions.MoveTo;
import com.github.maxopoly.angeliacore.actions.actions.inventory.ChangeSelectedItem;
import com.github.maxopoly.angeliacore.actions.actions.inventory.ClickInventory;
import com.github.maxopoly.angeliacore.connection.DisconnectReason;
import com.github.maxopoly.angeliacore.connection.ServerConnection;
import com.github.maxopoly.angeliacore.event.AngeliaEventHandler;
import com.github.maxopoly.angeliacore.event.AngeliaListener;
import com.github.maxopoly.angeliacore.event.events.ActionQueueEmptiedEvent;
import com.github.maxopoly.angeliacore.event.events.HealthChangeEvent;
import com.github.maxopoly.angeliacore.event.events.HungerChangeEvent;
import com.github.maxopoly.angeliacore.event.events.PlayerSpawnEvent;
import com.github.maxopoly.angeliacore.model.inventory.DummyInventory;
import com.github.maxopoly.angeliacore.model.inventory.Inventory;
import com.github.maxopoly.angeliacore.model.inventory.PlayerInventory;
import com.github.maxopoly.angeliacore.model.item.Enchantment;
import com.github.maxopoly.angeliacore.model.item.ItemStack;
import com.github.maxopoly.angeliacore.model.item.Material;
import com.github.maxopoly.angeliacore.model.location.Location;
import com.github.maxopoly.angeliacore.model.location.MovementDirection;
import com.github.maxopoly.angeliacore.plugin.AngeliaPlugin;
import com.github.maxopoly.angeliacore.util.BreakTimeCalculator;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.cli.Option;
import org.apache.logging.log4j.Logger;
import org.kohsuke.MetaInfServices;

@MetaInfServices(AngeliaPlugin.class)
public class ObbyBot extends AngeliaPlugin implements AngeliaListener {

	private ServerConnection connection;
	private ActionQueue queue;
	private Location miningLoc;
	private Logger logger;
	private int obbyLineLength;
	private MovementDirection miningDirection;
	private MovementDirection buttonOffSetDirection;
	private ItemStack cachedTool;
	private int cobbleClearCounter;
	private static final int cobbleClearIntervall = 5;
	private static final int reportIncrement = 128;
	private int obbyMined;
	private long startingTime;
	private List<String> allowedPlayers = Arrays.asList(new String[] { "awoo", "BlueSylvaer", "Smim02", "SafeSpace",
			"RexDrillerson", "ZombieReagan", "AltVault", "Frensin", "ZaNotHere" });

	public ObbyBot() {
		super("ObbyBot");
	}

	@AngeliaEventHandler
	public void queueEmpty(ActionQueueEmptiedEvent e) {
		if (cobbleClearCounter % cobbleClearIntervall == 0) {
			clearCobble(miningDirection);
		}
		placeStringAndPressButton(miningDirection);
		mineLine(miningDirection.getOpposite());
		if (cobbleClearCounter % cobbleClearIntervall == 0) {
			clearCobble(miningDirection.getOpposite());
		}
		placeStringAndPressButton(miningDirection.getOpposite());
		mineLine(miningDirection);
		// sometimes the bot acciedntally picks up obby, which we need to throw on the hopper manually
		throwObby();
		cobbleClearCounter++;
	}

	private void placeStringAndPressButton(MovementDirection dir) {
		Location upperLoc = miningLoc.relativeBlock(0, 1, 0);
		// place string
		pickString();
		for (int i = obbyLineLength + 1; i > 1; i--) {
			Location obbyLoc = upperLoc.addVector(dir.toVector().multiply(i));
			queue.queue(new LookAtAndPlaceBlock(connection, obbyLoc, dir.getOpposite().toBlockFace()));
		}
		// press button
		queue.queue(new LookAtAndPlaceBlock(connection, upperLoc.addVector(dir.toVector().multiply(obbyLineLength + 1))
				.addVector(buttonOffSetDirection.toVector())));
	}

	private void mineLine(MovementDirection dir) {
		pickPickAxe();
		Location upperLoc = miningLoc.relativeBlock(0, 1, 0);
		int breakTime = BreakTimeCalculator.getBreakTicks(50.0, true, true, cachedTool, connection.getPlayerStatus()
				.getActivePotionEffects(), connection.getTicksPerSecond());
		for (int i = 1; i <= obbyLineLength; i++) {
			Location obbyLoc = upperLoc.addVector(dir.toVector().multiply(i));
			queue.queue(new LookAtAndBreakBlock(connection, obbyLoc, breakTime));
		}
	}

	private void clearCobble(MovementDirection dir) {
		pickPickAxe();
		Location upperLoc = miningLoc.relativeBlock(0, 1, 0).addVector(buttonOffSetDirection.getOpposite().toVector());
		int breakTime = BreakTimeCalculator.getBreakTicks(2.0, true, true, cachedTool, connection.getPlayerStatus()
				.getActivePotionEffects(), connection.getTicksPerSecond());
		for (int i = 1; i <= obbyLineLength; i++) {
			Location obbyLoc = upperLoc.addVector(dir.toVector().multiply(i));
			queue.queue(new LookAtAndBreakBlock(connection, obbyLoc, breakTime));
		}
	}

	private void pickString() {
		Inventory hotbar = connection.getPlayerStatus().getPlayerInventory().getHotbar();
		for (int i = 0; i < hotbar.getSize(); i++) {
			ItemStack is = hotbar.getSlot(i);
			if (is == null) {
				continue;
			}
			if (is.getMaterial() == Material.STRING) {
				queue.queue(new ChangeSelectedItem(connection, i));
				// amount that will be placed
				int placed = Math.min(obbyLineLength, is.getAmount());
				reportAmountMined(obbyMined, placed);
				return;
			}
		}
		logger.info("Couldn't find any string, waiting for more");
		PlayerInventory inv = connection.getPlayerStatus().getPlayerInventory();
		DummyInventory storageSlots = inv.getPlayerStorage();
		for (int i = 0; i < storageSlots.getSize(); i++) {
			ItemStack is = storageSlots.getSlot(i);
			if (is.getMaterial() == Material.STRING) {
				queue.queue(new ClickInventory(connection, (byte) 0, inv.translateStorageSlotToTotal(i), (byte) 1, 1,
						is));
			}
		}
	}

	private void reportAmountMined(int oldAmount, int amountPlaced) {
		//we dont know how much we actually mine, so we guess based on the amount of string placed
		int newAmount = obbyMined + amountPlaced;
		if (oldAmount / reportIncrement != newAmount / reportIncrement) {
			int duraLeft = calculatePickDuraLeft();
			logger.info(String.format("Mined a total of %d stacks of obsidian in %s", newAmount/ 64 , getFormattedTime(System.currentTimeMillis() - startingTime)));
			logger.info(String.format("Total pick durability left for about %d stacks of obsidian, estimated enough for %s",
					duraLeft / 64, getFormattedTime(calculateRuntimeLeft(duraLeft))));
		}
		obbyMined += amountPlaced;
	}

	private static String getFormattedTime(long millis) {
		long second = (millis / 1000) % 60;
		long minute = (millis / (1000 * 60)) % 60;
		long hour = (millis / (1000 * 60 * 60)) % 24;
		return String.format("%02dh %02dm %02ds", hour, minute, second);
	}

	private int calculateRuntimeLeft(int duraLeft) {
		double rate = (double) obbyMined / (double) (System.currentTimeMillis() - startingTime);
		double inversedTime = rate / duraLeft;
		return (int) (1/inversedTime);
	}

	private int calculatePickDuraLeft() {
		int totalDura = 0;
		Inventory hotbar = connection.getPlayerStatus().getPlayerInventory().getHotbar();
		for (int i = 0; i < hotbar.getSize(); i++) {
			ItemStack is = hotbar.getSlot(i);
			if (is == null) {
				continue;
			}
			if (is.getMaterial() == Material.DIAMOND_PICKAXE) {
				if (is.getDamage() >= (is.getMaterial().getMaximumDurability() - 5)) {
					continue;
				}
				// valid pick found
				Map <Enchantment, Integer> enchants = is.getEnchants();
				int pickDura = is.getMaterial().getMaximumDurability() - is.getDamage() - 5;
				if (enchants != null) {
					Integer ubLevel = enchants.get(Enchantment.UNBREAKING);
					if (ubLevel != null) {
						pickDura *= (ubLevel + 1);
					}
				}
				totalDura += pickDura;
			}
		}
		return totalDura;
	}

	private void pickPickAxe() {
		Inventory hotbar = connection.getPlayerStatus().getPlayerInventory().getHotbar();
		for (int i = 0; i < hotbar.getSize(); i++) {
			ItemStack is = hotbar.getSlot(i);
			if (is == null) {
				continue;
			}
			if (is.getMaterial() == Material.DIAMOND_PICKAXE) {
				if (is.getDamage() >= (is.getMaterial().getMaximumDurability() - 5)) {
					continue;
				}
				// valid pick found
				cachedTool = is;
				queue.queue(new ChangeSelectedItem(connection, i));
				return;
			}
		}
		connection.getLogger().info("Could not find a pick in inventory, logging off");
		connection.close(DisconnectReason.Intentional_Disconnect);
	}

	private void throwObby() {
		PlayerInventory inv = connection.getPlayerStatus().getPlayerInventory();
		Inventory hotbar = inv.getHotbar();
		for (int i = 0; i < hotbar.getSize(); i++) {
			ItemStack is = hotbar.getSlot(i);
			if (is == null || is.getMaterial() == Material.EMPTY_SLOT) {
				continue;
			}
			if (is.getMaterial() == Material.OBSIDIAN) {
				// throw entire stack
				queue.queue(new ClickInventory(connection, (byte) 0, inv.translateHotbarToTotal(i), (byte) 1, 4, is));
			}
		}
	}

	@AngeliaEventHandler
	public void damaged(HealthChangeEvent e) {
		if (e.getNewValue() < e.getOldValue()) {
			connection.getLogger().info("Received damage, health is " + e.getNewValue() + ". Logging off");
			connection.close(DisconnectReason.Intentional_Disconnect);
		}
	}

	@AngeliaEventHandler
	public void playerNearby(PlayerSpawnEvent e) {
		if (e.getOnlinePlayer() != null) {
			if (allowedPlayers.contains(e.getOnlinePlayer().getName())) {
				logger.info(e.getOnlinePlayer().getName()
						+ " entered radar distance, but he is whitelisted, so ignoring it");
				return;
			}
			logger.info("Logging off, because " + e.getOnlinePlayer().getName() + " is nearby");
		}
		connection.close(DisconnectReason.Intentional_Disconnect);
	}

	@AngeliaEventHandler
	public void hungerChange(HungerChangeEvent e) {
		if (e.getNewValue() > e.getOldValue() || e.getNewValue() > 15) {
			return;
		}
		queue.queue(new DetectAndEatFood(connection));
	}

	@Override
	public void start() {
		queue.queue(new MoveTo(connection, miningLoc.getBlockCenterXZ(), MoveTo.SPRINTING_SPEED));
		connection.getEventHandler().registerListener(this);
		this.cobbleClearCounter = 0;
		if (startingTime == 0) {
			startingTime = System.currentTimeMillis();
		}
		logger.info("Starting ObbyBot");
	}

	@Override
	public String getHelp() {
		return "";
	}

	@Override
	protected List<Option> createOptions() {
		List<Option> options = new LinkedList<Option>();
		options.add(Option.builder("x").longOpt("x").numberOfArgs(1).required()
				.desc("x coordinate of where the bot will stand").build());
		options.add(Option.builder("z").longOpt("z").numberOfArgs(1).required()
				.desc("z coordinate of where the bot will stand").build());
		options.add(Option.builder("odir").longOpt("miningDirection").numberOfArgs(1).required()
				.desc("Cardinal direction in which the bot mine obby").build());
		options.add(Option.builder("l").longOpt("lineLength").numberOfArgs(1).required(false)
				.desc("Length of the obby line, defaults to 2").build());
		options.add(Option.builder("bdir").longOpt("buttonDirection").numberOfArgs(1).required()
				.desc("Cardinal direction in which the bot is offset from the obby line").build());
		return options;
	}

	@Override
	protected void parseOptions(ServerConnection connection, Map<String, List<String>> args) {
		this.connection = connection;
		this.queue = connection.getActionQueue();
		this.logger = connection.getLogger();
		int x, z;
		try {
			x = Integer.parseInt(args.get("x").get(0));
			z = Integer.parseInt(args.get("z").get(0));
		} catch (NumberFormatException e) {
			logger.error("One of the coords given was not a proper number");
			finish();
			return;
		}
		try {
			this.buttonOffSetDirection = MovementDirection.valueOf(args.get("bdir").get(0).toUpperCase());
		} catch (IllegalArgumentException e) {
			logger.error("The button offset direction given was not a proper direction");
			finish();
			return;
		}
		try {
			this.miningDirection = MovementDirection.valueOf(args.get("odir").get(0).toUpperCase());
		} catch (IllegalArgumentException e) {
			logger.error("The mining direction given was not a proper direction");
			finish();
			return;
		}
		if (args.containsKey("l")) {
			try {
				obbyLineLength = Integer.parseInt(args.get("l").get(0));
			} catch (NumberFormatException e) {
				logger.error("The obby line length given was not a proper number");
				finish();
				return;
			}
		} else {
			obbyLineLength = 2;
		}
		this.miningLoc = new Location(x, connection.getPlayerStatus().getLocation().getBlockY(), z);
	}

	@Override
	public void tearDown() {
		connection.getEventHandler().unregisterListener(this);
	}

	@Override
	public AngeliaPlugin transistionToNewConnection(ServerConnection newConnection) {
		this.connection = newConnection;
		this.queue = newConnection.getActionQueue();
		this.logger = connection.getLogger();
		connection.getEventHandler().registerListener(this);
		return this;
	}

}
