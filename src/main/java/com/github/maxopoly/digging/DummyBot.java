package com.github.maxopoly.digging;

import com.github.maxopoly.angeliacore.actions.actions.LookAtAndPlaceBlock;
import com.github.maxopoly.angeliacore.connection.ServerConnection;
import com.github.maxopoly.angeliacore.event.AngeliaListener;
import com.github.maxopoly.angeliacore.model.location.BlockFace;
import com.github.maxopoly.angeliacore.model.location.Location;
import com.github.maxopoly.angeliacore.plugin.AngeliaPlugin;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.cli.Option;
import org.kohsuke.MetaInfServices;

@MetaInfServices(AngeliaPlugin.class)
public class DummyBot extends AngeliaPlugin implements AngeliaListener {

	private ServerConnection conn;

	public DummyBot() {
		super("dummy");
	}

	@Override
	public void start() {
		Location loc = conn.getPlayerStatus().getLocation().toBlockLocation().relativeBlock(0, -1, 0);
		System.out.println(loc);
		conn.getActionQueue().queue(new LookAtAndPlaceBlock(conn, loc, BlockFace.WEST));

	}

	@Override
	public String getHelp() {
		return "";
	}

	@Override
	protected List<Option> createOptions() {
		return new LinkedList<Option>();
	}

	@Override
	protected void parseOptions(ServerConnection connection, Map<String, List<String>> args) {
		this.conn = connection;
	}

	@Override
	public void tearDown() {
		// TODO Auto-generated method stub

	}

	@Override
	public AngeliaPlugin transistionToNewConnection(ServerConnection newConnection) {
		return new DummyBot();
	}

}
