package com.autopickaxe;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class Webhook {
	private static final Gson GSON = new Gson();
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	private Webhook() {
	}

	public static boolean isValidUrl(String url) {
		if (url == null) return false;
		String u = url.trim();
		return u.startsWith("https://") && u.contains("/api/webhooks/");
	}

	/** Sends an embed asynchronously. {@code result} gets "OK" or an error text (called off the game thread). */
	public static void send(String url, String title, String description, int color, Map<String, String> fields, Consumer<String> result) {
		if (!isValidUrl(url)) {
			result.accept("Invalid webhook URL");
			return;
		}
		try {
			JsonObject embed = new JsonObject();
			embed.addProperty("title", title);
			embed.addProperty("description", description);
			embed.addProperty("color", color);
			embed.addProperty("timestamp", Instant.now().toString());
			JsonArray arr = new JsonArray();
			for (Map.Entry<String, String> e : (fields == null ? new LinkedHashMap<String, String>() : fields).entrySet()) {
				JsonObject f = new JsonObject();
				f.addProperty("name", e.getKey());
				f.addProperty("value", e.getValue());
				f.addProperty("inline", true);
				arr.add(f);
			}
			embed.add("fields", arr);
			JsonArray embeds = new JsonArray();
			embeds.add(embed);
			JsonObject root = new JsonObject();
			root.addProperty("username", "Auto Pickaxe Swapper");
			root.add("embeds", embeds);

			HttpRequest req = HttpRequest.newBuilder(URI.create(url.trim()))
					.timeout(Duration.ofSeconds(15))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(root)))
					.build();

			HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((resp, err) -> {
				if (err != null) {
					result.accept("Request failed: " + err.getMessage());
				} else if (resp.statusCode() / 100 == 2) {
					result.accept("OK");
				} else {
					result.accept("Discord replied HTTP " + resp.statusCode());
				}
			});
		} catch (Exception e) {
			result.accept("Could not send: " + e.getMessage());
		}
	}
}
