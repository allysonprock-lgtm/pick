package com.autopickaxe;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

public final class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Config instance = new Config();

	/** Master switch (also toggled by the keybind). */
	public boolean enabled = true;
	/** Show [AutoPickaxe] messages in chat. */
	public boolean chatMessages = true;

	public boolean webhookEnabled = true;
	public String webhookUrl = "";

	/** Press the key picked in Options > Controls when no pickaxes are left. */
	public boolean pressKeyEnabled = true;
	/** How many ticks the simulated key stays held. */
	public int keyHoldTicks = 4;

	/** Ticks to wait between each step of the swap (open, click, close). 2 = ~0.1s. */
	public int swapDelayTicks = 2;
	/** A pickaxe that vanishes while at or below this durability counts as "broken". */
	public int breakThreshold = 6;
	/** Never swap in a pickaxe with this much durability or less left. */
	public int minSwapDurability = 2;

	/** First entry = highest priority. Matches item ids like diamond_pickaxe. */
	public List<String> materialPriority = new ArrayList<>(
			List.of("diamond", "netherite", "iron", "copper", "stone", "golden", "wooden"));

	public static Config get() {
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("autopickaxe.json");
	}

	public static void load() {
		Path p = path();
		if (Files.exists(p)) {
			try (Reader r = Files.newBufferedReader(p)) {
				Config c = GSON.fromJson(r, Config.class);
				if (c != null) instance = c;
			} catch (Exception e) {
				AutoPickaxeClient.LOGGER.error("Could not read config, using defaults", e);
			}
		}
		instance.sanitize();
		save();
	}

	public static void save() {
		instance.sanitize();
		try (Writer w = Files.newBufferedWriter(path())) {
			GSON.toJson(instance, w);
		} catch (IOException e) {
			AutoPickaxeClient.LOGGER.error("Could not save config", e);
		}
	}

	private void sanitize() {
		swapDelayTicks = Math.max(1, Math.min(20, swapDelayTicks));
		breakThreshold = Math.max(1, Math.min(50, breakThreshold));
		minSwapDurability = Math.max(0, Math.min(50, minSwapDurability));
		keyHoldTicks = Math.max(1, Math.min(20, keyHoldTicks));
		if (webhookUrl == null) webhookUrl = "";
		if (materialPriority == null || materialPriority.isEmpty()) {
			materialPriority = new ArrayList<>(List.of("diamond", "netherite", "iron", "copper", "stone", "golden", "wooden"));
		}
	}
}
