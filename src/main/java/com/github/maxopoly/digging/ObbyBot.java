package com.github.maxopoly.digging;

import com.github.maxopoly.angeliacore.actions.ActionQueue;
import com.github.maxopoly.angeliacore.actions.actions.MoveTo;
import com.github.maxopoly.angeliacore.connection.ServerConnection;
import com.github.maxopoly.angeliacore.event.AngeliaEventHandler;
import com.github.maxopoly.angeliacore.event.AngeliaListener;
import com.github.maxopoly.angeliacore.event.events.ActionQueueEmptiedEvent;
import com.github.maxopoly.angeliacore.model.location.Location;
import com.github.maxopoly.angeliacore.plugin.AngeliaPlugin;
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

	public ObbyBot() {
		super("ObbyBot");
		// TODO Auto-generated constructor stub
	}

	@AngeliaEventHandler
	public void queueEmpty(ActionQueueEmptiedEvent e) {
		connection.getEventHandler().registerListener(this);
		queueEmpty(null);
	}

	@Override
	public void start() {
		queue.queue(new MoveTo(connection, miningLoc.getBlockCenterXZ(), MoveTo.SPRINTING_SPEED));
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
		options.add(Option.builder("z").longOpt("z").numberOfArgs(2).required()
				.desc("z coordinate of where the bot will stand").build());
		options.add(Option.builder("dir").longOpt("miningDirection").numberOfArgs(1).required()
				.desc("Cardinal direction in which the bot mine obby").build());
		options.add(Option.builder("l").longOpt("lineLength").numberOfArgs(1).required(false)
				.desc("Length of the obby line").build());
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
