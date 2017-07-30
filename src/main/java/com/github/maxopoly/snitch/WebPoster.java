package com.github.maxopoly.snitch;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import org.apache.logging.log4j.Logger;

public class WebPoster extends Thread {

	private final LinkedList<byte[]> alertQueue;
	private final String url;
	private Logger logger;

	public WebPoster(Logger logger, String url) {
		this.alertQueue = new LinkedList<byte[]>();
		this.url = url;
		this.logger = logger;
	}

	@Override
	public void run() {
		while (true) {
			try {
				runLoop();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public synchronized void pushJson(String json) {
		alertQueue.add(json.getBytes(StandardCharsets.UTF_8));
		interrupt();
	}

	private synchronized byte[] popAlertJson() {
		return alertQueue.poll();
	}

	private void runLoop() throws IOException {
		byte[] json = popAlertJson();
		if (json == null) {
			try {
				Thread.sleep(10000);
			} catch (InterruptedException ignored) {
			}
			return; // jump back up to popAlertJson
		}

		if (url == null || url.length() <= 0) {
			return;
		}

		HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
		connection.setDoOutput(true);
		connection.setRequestMethod("POST");
		connection.addRequestProperty("User-Agent", "Mozilla/4.76");
		connection.setRequestProperty("Content-Length", String.valueOf(json.length));
		connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

		try (OutputStream os = connection.getOutputStream()) {
			os.write(json);
			os.flush();

			if (connection.getResponseCode() < 200 || 300 <= connection.getResponseCode()) {
				logger.error("Failed to post to url " + url + "  --  " + connection.getResponseCode() + ": "
						+ connection.getResponseMessage());
			}
		} catch (IOException e) {
			logger.error("Exception occured while posting to url " + url, e);
		}
	}

}
