package com.github.maxopoly.digging;

import com.github.maxopoly.angeliacore.actions.ActionLock;
import com.github.maxopoly.angeliacore.actions.ActionQueue;
import com.github.maxopoly.angeliacore.actions.CodeAction;
import com.github.maxopoly.angeliacore.actions.actions.DetectAndEatFood;
import com.github.maxopoly.angeliacore.connection.DisconnectReason;
import com.github.maxopoly.angeliacore.connection.ServerConnection;
import com.github.maxopoly.angeliacore.connection.play.packets.out.ChatPacket;
import com.github.maxopoly.angeliacore.event.AngeliaEventHandler;
import com.github.maxopoly.angeliacore.event.AngeliaListener;
import com.github.maxopoly.angeliacore.event.events.HungerChangeEvent;
import com.github.maxopoly.angeliacore.event.events.PlayerSpawnEvent;
import com.github.maxopoly.angeliacore.plugin.AngeliaPlugin;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.cli.Option;
import org.kohsuke.MetaInfServices;

@MetaInfServices(AngeliaPlugin.class)
public class AfkBot extends AngeliaPlugin implements AngeliaListener {

	private ServerConnection connection;

	private List<String> allowedPlayers = Arrays.asList(new String[] { "awoo", "BlueSylvaer", "Smim02", "SafeSpace",
			"RexDrillerson", "ZombieReagan" });

	public AfkBot() {
		super("afkbot");
	}

	@Override
	public void start() {
		connection.getEventHandler().registerListener(this);
		connection.getLogger().info("Starting AFK Bot. Will log if anyone enters radar distance.");
	}

	@Override
	public String getHelp() {
		return "Logs out if someone enters radar distance";
	}

	@Override
	protected List<Option> createOptions() {
		return new LinkedList<Option>();
	}

	@AngeliaEventHandler
	public void hungerChange(HungerChangeEvent e) {
		if (e.getNewValue() > e.getOldValue()) {
			return;
		}
		DetectAndEatFood eat = new DetectAndEatFood(connection);
		ActionQueue queue = connection.getActionQueue();
		queue.queue(eat);
		queue.queue(new CodeAction(connection) {

			@Override
			public ActionLock[] getActionLocks() {
				return new ActionLock[0];
			}

			@Override
			public void execute() {
				if (!eat.foundFood()) {
					connection.getLogger().info("Disconnecting as no food could be found");
					connection.close(DisconnectReason.Intentional_Disconnect);
				}
			}
		});
	}

	@Override
	protected void parseOptions(ServerConnection connection, Map<String, List<String>> args) {
		this.connection = connection;
	}

	@Override
	public void tearDown() {
		connection.getLogger().info("Stopping AFK Bot");
	}

	@Override
	public AngeliaPlugin transistionToNewConnection(ServerConnection newConnection) {
		this.connection = newConnection;
		connection.getEventHandler().registerListener(this);
		return this;
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
}
