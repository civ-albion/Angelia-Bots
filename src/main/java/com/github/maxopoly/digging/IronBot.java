package com.github.maxopoly.digging;

import com.github.maxopoly.angeliacore.connection.DisconnectReason;
import com.github.maxopoly.angeliacore.connection.ServerConnection;
import com.github.maxopoly.angeliacore.model.item.Material;
import com.github.maxopoly.angeliacore.plugin.AngeliaPlugin;
import org.kohsuke.MetaInfServices;

@MetaInfServices(AngeliaPlugin.class)
public class IronBot extends AbstractMiningBot {

	public IronBot() {
		super("IronBot", Material.DIAMOND_PICKAXE, 3);
	}

	@Override
	public void atEndOfField() {
		connection.getLogger().info("Area is done, logging out");
		connection.close(DisconnectReason.Intentional_Disconnect);
	}

	@Override
	public String getHelp() {
		return "Tell max to put something here";
	}

	@Override
	public AngeliaPlugin transistionToNewConnection(ServerConnection newConnection) {
		IronBot digging = new IronBot();
		enrichCopy(digging, newConnection);
		return digging;
	}
}
