package com.github.maxopoly.farming;

import com.github.maxopoly.angeliacore.actions.actions.inventory.ClickInventory;
import com.github.maxopoly.angeliacore.connection.ServerConnection;
import com.github.maxopoly.angeliacore.event.AngeliaListener;
import com.github.maxopoly.angeliacore.model.inventory.Inventory;
import com.github.maxopoly.angeliacore.model.inventory.PlayerInventory;
import com.github.maxopoly.angeliacore.model.item.ItemStack;
import com.github.maxopoly.angeliacore.model.item.Material;
import com.github.maxopoly.angeliacore.plugin.AngeliaPlugin;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.cli.Option;
import org.kohsuke.MetaInfServices;

@MetaInfServices(AngeliaPlugin.class)
public class DiamondBot extends AngeliaPlugin implements AngeliaListener {

	private ServerConnection conn;

	public DiamondBot() {
		super("dupebot");
	}

	@Override
	public void start() {
		PlayerInventory inv = conn.getPlayerStatus().getPlayerInventory();
		Inventory hotbar = inv.getHotbar();
		for(int i = 0; i < hotbar.getSize(); i++) {
			ItemStack is = hotbar.getSlot(i);
			if (is != null && is.getMaterial() != Material.EMPTY_SLOT) {
				//dont queue, run right away
				conn.getActionQueue().queue(new ClickInventory(conn, (byte) 0, inv.translateHotbarToTotal(i), (byte) 1, 4, is));
			}
		}
		 /*try {
			conn.sendPacket(new UseEntityPacket(conn.getPlayerStatus().getEntityID(), (byte) 0));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} */
	}

	@Override
	public String getHelp() {
		// TODO Auto-generated method stub
		return "";
	}

	@Override
	protected List<Option> createOptions() {
		// TODO Auto-generated method stub
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
		// TODO Auto-generated method stub
		return null;
	}


}
