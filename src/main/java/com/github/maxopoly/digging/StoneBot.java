package com.github.maxopoly.digging;

import com.github.maxopoly.angeliacore.actions.ActionQueue;
import com.github.maxopoly.angeliacore.actions.actions.DetectAndEatFood;
import com.github.maxopoly.angeliacore.actions.actions.LookAtAndBreakBlock;
import com.github.maxopoly.angeliacore.actions.actions.MoveTo;
import com.github.maxopoly.angeliacore.actions.actions.inventory.ChangeSelectedItem;
import com.github.maxopoly.angeliacore.connection.DisconnectReason;
import com.github.maxopoly.angeliacore.connection.ServerConnection;
import com.github.maxopoly.angeliacore.connection.play.packets.out.ChatPacket;
import com.github.maxopoly.angeliacore.event.AngeliaEventHandler;
import com.github.maxopoly.angeliacore.event.AngeliaListener;
import com.github.maxopoly.angeliacore.event.events.ActionQueueEmptiedEvent;
import com.github.maxopoly.angeliacore.event.events.HealthChangeEvent;
import com.github.maxopoly.angeliacore.event.events.HungerChangeEvent;
import com.github.maxopoly.angeliacore.event.events.PlayerSpawnEvent;
import com.github.maxopoly.angeliacore.model.inventory.Inventory;
import com.github.maxopoly.angeliacore.model.item.ItemStack;
import com.github.maxopoly.angeliacore.model.item.Material;
import com.github.maxopoly.angeliacore.model.location.Location;
import com.github.maxopoly.angeliacore.model.location.MovementDirection;
import com.github.maxopoly.angeliacore.plugin.AngeliaPlugin;
import com.github.maxopoly.angeliacore.util.BreakTimeCalculator;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.cli.Option;
import org.apache.logging.log4j.Logger;
import org.kohsuke.MetaInfServices;

@MetaInfServices(AngeliaPlugin.class)
public class StoneBot extends AngeliaPlugin implements AngeliaListener {

	private ServerConnection connection;
	private ActionQueue queue;
	private Location miningLoc;
	private Logger logger;
	private int obbyLineLength;
	private MovementDirection miningDirection;
	private ItemStack cachedTool;
	private List<String> allowedPlayers = Arrays.asList(new String[] { "awoo", "BlueSylvaer", "Smim02", "SafeSpace",
			"RexDrillerson", "ZombieReagan", "ZombieMcCarthy", "HorseViolator", "Frensin", "KyotoGirl2001",
			"Tarenaran", "BotterDammerung", "Maxopoly" });

	public StoneBot() {
		super("StoneBot");
	}

	@AngeliaEventHandler
	public void queueEmpty(ActionQueueEmptiedEvent e) {
		mineLine(miningDirection);
		mineLine(miningDirection.getOpposite());
	}

	private void mineLine(MovementDirection dir) {
		pickPickAxe();
		Location upperLoc = miningLoc.relativeBlock(0, 1, 0);
		int breakTime = BreakTimeCalculator.getBreakTicks(1.5, true, true, cachedTool, connection.getPlayerStatus()
				.getActivePotionEffects(), connection.getTicksPerSecond());
		for (int i = 1; i <= obbyLineLength; i++) {
			Location obbyLoc = upperLoc.addVector(dir.toVector().multiply(i));
			queue.queue(new LookAtAndBreakBlock(connection, obbyLoc, breakTime));
		}
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
		logoff();
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
		logger.info("Starting StoneBot");
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

	private void logoff() {
		ChatPacket packet;
		try {
			packet = new ChatPacket("/logout");
		} catch (IOException ex) {
			connection.getLogger().error("Failed to create logout msg", ex);
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

}
