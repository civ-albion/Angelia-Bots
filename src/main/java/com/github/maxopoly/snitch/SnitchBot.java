package com.github.maxopoly.snitch;

import com.github.maxopoly.angeliacore.connection.ServerConnection;
import com.github.maxopoly.angeliacore.event.AngeliaEventHandler;
import com.github.maxopoly.angeliacore.event.AngeliaListener;
import com.github.maxopoly.angeliacore.event.events.ChatMessageReceivedEvent;
import com.github.maxopoly.angeliacore.plugin.AngeliaPlugin;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.cli.Option;
import org.json.JSONObject;
import org.kohsuke.MetaInfServices;

@MetaInfServices(AngeliaPlugin.class)
public class SnitchBot extends AngeliaPlugin implements AngeliaListener {

	// Taken from https://github.com/Gjum/Snitchcord
	private static final Pattern snitchPattern = Pattern
			.compile("\\s*\\*\\s*([^\\s]*)\\s\\b(entered snitch at|logged out in snitch at|logged in to snitch at)"
					+ "\\b\\s*([^\\s]*)\\s\\[([^\\s]*)\\s([-\\d]*)\\s([-\\d]*)\\s([-\\d]*)\\]");
	private static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("HH:mm:ss");

	private ServerConnection connection;
	private String url;
	private WebPoster poster;

	public SnitchBot() {
		super("SnitchBot");
	}

	@AngeliaEventHandler
	public void handleChat(ChatMessageReceivedEvent e) {
		System.out.println(e.getMessage());
		String msg = e.getMessage();
		msg = stripMinecraftFormattingCodes(msg);
		System.out.println(msg);
		Matcher matcher = snitchPattern.matcher(msg);
		if (matcher.matches()) {
			handleSnitchHit(matcher);
		}
	}

	private void handleSnitchHit(Matcher matcher) {
		String playerName = matcher.group(1);
		String activity = matcher.group(2);
		String snitchName = matcher.group(3);
		String worldName = matcher.group(4);
		int x = Integer.parseInt(matcher.group(5));
		int y = Integer.parseInt(matcher.group(6));
		int z = Integer.parseInt(matcher.group(7));
		ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
		String msg = "`" + now.format(dateFormat) + "`   **" + playerName + "**   `" + snitchName + "`   [" + x + ", " + y
				+ ", " + z + "]";
		JSONObject json = new JSONObject();
		json.put("content", msg);
		poster.pushJson(json.toString());
	}

	@Override
	public String getHelp() {
		return "Parses ingame snitch alerts sent by JukeAlert and sends them to a discord bot";
	}

	@Override
	public void tearDown() {
		connection.getEventHandler().unregisterListener(this);
	}

	@Override
	public void start() {
		connection.getEventHandler().registerListener(this);
		connection.getLogger().info("Starting SnitchBot, snitch notifications will be forwarded to Discord");
		poster.start();
	}

	@Override
	protected List<Option> createOptions() {
		List<Option> options = new LinkedList<Option>();
		options.add(Option.builder("url").longOpt("url").numberOfArgs(1).required()
				.desc("The discord API url to which snitch alerts will be posted").build());
		return options;
	}

	@Override
	protected void parseOptions(ServerConnection connection, Map<String, List<String>> args) {
		this.connection = connection;
		this.url = args.get("url").get(0);
		try {
			HttpURLConnection httpConn = (HttpURLConnection) new URL(url).openConnection();
			httpConn.connect();
			httpConn.disconnect();
		} catch (SocketTimeoutException e) {
			connection.getLogger().warn("Failed to connect to url " + url + ", connection timed out. Exiting plugin.");
			finish();
			return;
		} catch (IOException e) {
			connection.getLogger().warn(
					"Exception occured while attempting to connect to url \"" + url + "\", exiting plugin");
			finish();
			return;
		}
		this.poster = new WebPoster(connection.getLogger(), url);
	}

	@Override
	public AngeliaPlugin transistionToNewConnection(ServerConnection newConnection) {
		SnitchBot snitcher = new SnitchBot();
		snitcher.connection = newConnection;
		snitcher.url = this.url;
		snitcher.poster = this.poster;
		return snitcher;
	}

	private String stripMinecraftFormattingCodes(String str) {
		return str.replaceAll("(?i)\\u00A7[a-z0-9]", "");
	}

}
