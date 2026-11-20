package com.autopickaxe;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

/**
 * Watches the hotbar for a pickaxe that breaks, then (with a real inventory screen):
 * opens inventory -> verifies -> swaps the best replacement pickaxe into the empty slot -> verifies -> closes.
 */
public final class Swapper {
	private enum State { IDLE, OPENING, VERIFY, CLOSING }

	private static final int MAIN_SIZE = 36; // hotbar 0-8 + main inventory 9-35

	private static State state = State.IDLE;
	private static int timer;
	private static int targetSlot;
	private static int retries;

	// Hotbar snapshot from the previous tick (only taken while no screen is open).
	private static final boolean[] wasPick = new boolean[9];
	private static final int[] remaining = new int[9];
	private static boolean haveSnapshot;

	private static boolean outNotified;
	private static LocalPlayer lastPlayer;

	private static InputConstants.Key heldKey;
	private static int heldTicks;

	private static Method clickMethod;
	private static Object swapConstant;
	private static boolean reflectionTried;

	private Swapper() {
	}

	// ------------------------------------------------------------------ main tick

	public static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			releaseHeldKey(mc);
			resetState();
			lastPlayer = null;
			return;
		}
		if (player != lastPlayer) { // respawn / dimension change / new world
			lastPlayer = player;
			resetState();
		}

		Config cfg = Config.get();

		while (AutoPickaxeClient.TOGGLE_KEY.consumeClick()) {
			cfg.enabled = !cfg.enabled;
			Config.save();
			say(mc, "Auto swap " + (cfg.enabled ? "ENABLED" : "DISABLED"), cfg.enabled ? ChatFormatting.GREEN : ChatFormatting.RED, true);
		}
		while (AutoPickaxeClient.GUI_KEY.consumeClick()) {
			if (mc.screen == null) mc.setScreen(new ConfigScreen(null));
		}

		tickHeldKey(mc);

		if (!cfg.enabled) {
			if (state != State.IDLE) abort(mc, player, "Disabled - swap cancelled.");
			haveSnapshot = false;
			return;
		}

		switch (state) {
			case IDLE -> tickIdle(mc, player, cfg);
			case OPENING -> tickOpening(mc, player, cfg);
			case VERIFY -> tickVerify(mc, player, cfg);
			case CLOSING -> tickClosing(mc, player);
		}
	}

	private static void tickIdle(Minecraft mc, LocalPlayer player, Config cfg) {
		// Any screen (chest, inventory, death...) invalidates the snapshot so moving items by hand never looks like a break.
		if (mc.screen != null || player.isDeadOrDying()) {
			haveSnapshot = false;
			return;
		}
		Inventory inv = player.getInventory();

		if (haveSnapshot) {
			for (int i = 0; i < 9; i++) {
				if (wasPick[i] && remaining[i] <= cfg.breakThreshold && inv.getItem(i).isEmpty()) {
					onBroken(mc, player, i);
					break;
				}
			}
		}
		if (state == State.IDLE) snapshot(inv);

		if (outNotified && player.tickCount % 20 == 0 && hasAnyPickaxe(inv)) outNotified = false;
	}

	private static void snapshot(Inventory inv) {
		for (int i = 0; i < 9; i++) {
			ItemStack s = inv.getItem(i);
			boolean pick = isPickaxe(s);
			wasPick[i] = pick;
			remaining[i] = pick ? durabilityLeft(s) : 0;
		}
		haveSnapshot = true;
	}

	// ------------------------------------------------------------------ state machine

	private static void onBroken(Minecraft mc, LocalPlayer player, int slot) {
		Inventory inv = player.getInventory();
		Candidate best = findBest(inv, slot);
		if (best == null) {
			outOfPickaxes(mc, player);
			return;
		}
		targetSlot = slot;
		retries = 0;
		timer = Math.max(1, Config.get().swapDelayTicks);
		state = State.OPENING;
		say(mc, "Pickaxe broke in hotbar slot " + (slot + 1) + " - swapping in " + best.stack.getHoverName().getString(), ChatFormatting.YELLOW, false);
		mc.setScreen(new InventoryScreen(player));
	}

	private static void tickOpening(Minecraft mc, LocalPlayer player, Config cfg) {
		if (!(mc.screen instanceof InventoryScreen)) {
			abort(mc, player, "Inventory was closed unexpectedly - swap cancelled.");
			return;
		}
		if (--timer > 0) return;

		Inventory inv = player.getInventory();
		InventoryMenu menu = player.inventoryMenu;

		if (player.containerMenu != menu) {
			abort(mc, player, "A different container is open - swap cancelled.");
			return;
		}
		if (!inv.getItem(targetSlot).isEmpty()) {
			abort(mc, player, "Target hotbar slot is no longer empty - swap cancelled.");
			return;
		}
		if (mc.gameMode == null || !ensureReflection()) {
			abort(mc, player, "Could not find the inventory click method (check latest.log).");
			return;
		}

		// Re-pick right now so we never act on stale data.
		Candidate best = findBest(inv, targetSlot);
		if (best == null) {
			closeInventory(mc, player);
			state = State.IDLE;
			haveSnapshot = false;
			outOfPickaxes(mc, player);
			return;
		}

		Slot source = null;
		for (Slot s : menu.slots) {
			if (s.container == inv && s.getContainerSlot() == best.slot) {
				source = s;
				break;
			}
		}
		// Final safety check on the exact stack that is about to move.
		if (source == null || !isUsablePickaxe(source.getItem())) {
			abort(mc, player, "Source slot no longer holds a pickaxe - swap cancelled.");
			return;
		}

		try {
			// SWAP click: source slot <-> hotbar button (target hotbar slot is verified empty, so only the pickaxe moves).
			clickMethod.invoke(mc.gameMode, menu.containerId, source.index, targetSlot, swapConstant, player);
		} catch (Throwable t) {
			AutoPickaxeClient.LOGGER.error("Swap click failed", t);
			abort(mc, player, "Swap click failed (check latest.log).");
			return;
		}
		state = State.VERIFY;
		timer = Math.max(1, cfg.swapDelayTicks);
	}

	private static void tickVerify(Minecraft mc, LocalPlayer player, Config cfg) {
		if (--timer > 0) return;
		ItemStack now = player.getInventory().getItem(targetSlot);
		if (isPickaxe(now)) {
			say(mc, "Swapped in " + now.getHoverName().getString() + " (slot " + (targetSlot + 1) + ")", ChatFormatting.GREEN, false);
			state = State.CLOSING;
			timer = Math.max(1, cfg.swapDelayTicks);
		} else if (++retries < 3) {
			state = State.OPENING;
			timer = 1;
		} else {
			abort(mc, player, "Swap did not go through after 3 tries.");
		}
	}

	private static void tickClosing(Minecraft mc, LocalPlayer player) {
		if (--timer > 0) return;
		closeInventory(mc, player);
		state = State.IDLE;
		haveSnapshot = false;
	}

	private static void abort(Minecraft mc, LocalPlayer player, String message) {
		closeInventory(mc, player);
		state = State.IDLE;
		haveSnapshot = false;
		if (message != null) say(mc, message, ChatFormatting.RED, false);
	}

	private static void closeInventory(Minecraft mc, LocalPlayer player) {
		if (mc.screen instanceof InventoryScreen) player.closeContainer();
	}

	private static void resetState() {
		state = State.IDLE;
		haveSnapshot = false;
		retries = 0;
		timer = 0;
	}

	// ------------------------------------------------------------------ out of pickaxes

	private static void outOfPickaxes(Minecraft mc, LocalPlayer player) {
		if (outNotified) return;
		outNotified = true;
		Config cfg = Config.get();
		say(mc, "OUT OF PICKAXES!", ChatFormatting.RED, false);

		// 1) press the selected key
		if (cfg.pressKeyEnabled && !AutoPickaxeClient.PRESS_KEY.isUnbound()) {
			InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(AutoPickaxeClient.PRESS_KEY);
			InputSim.send(mc, key, true);
			heldKey = key;
			heldTicks = cfg.keyHoldTicks;
			say(mc, "Pressed key: " + key.getDisplayName().getString(), ChatFormatting.GRAY, false);
		}

		// 2) then notify Discord
		if (cfg.webhookEnabled && Webhook.isValidUrl(cfg.webhookUrl)) {
			Map<String, String> fields = new LinkedHashMap<>();
			fields.put("Player", player.getName().getString());
			fields.put("Server", mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "Singleplayer");
			fields.put("Dimension", String.valueOf(mc.level.dimension().identifier()));
			fields.put("Position", player.getBlockX() + ", " + player.getBlockY() + ", " + player.getBlockZ());
			Webhook.send(cfg.webhookUrl, "Out of pickaxes", "Your last pickaxe broke and there is no replacement in the inventory.",
					0xE74C3C, fields, result -> mc.execute(() -> {
						if ("OK".equals(result)) say(mc, "Discord notified.", ChatFormatting.GRAY, false);
						else say(mc, "Discord webhook failed: " + result, ChatFormatting.RED, false);
					}));
		} else if (cfg.webhookEnabled) {
			say(mc, "No valid Discord webhook set (press O to open settings).", ChatFormatting.GRAY, false);
		}
	}

	private static void tickHeldKey(Minecraft mc) {
		if (heldKey != null && --heldTicks <= 0) releaseHeldKey(mc);
	}

	private static void releaseHeldKey(Minecraft mc) {
		if (heldKey != null) {
			try {
				InputSim.send(mc, heldKey, false);
			} catch (Throwable ignored) {
			}
			heldKey = null;
		}
	}

	// ------------------------------------------------------------------ pickaxe logic

	private record Candidate(int slot, ItemStack stack) {
	}

	public static boolean isPickaxe(ItemStack s) {
		if (s.isEmpty()) return false;
		if (s.is(ItemTags.PICKAXES)) return true;
		return BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().endsWith("_pickaxe");
	}

	private static int durabilityLeft(ItemStack s) {
		return s.isDamageableItem() ? s.getMaxDamage() - s.getDamageValue() : Integer.MAX_VALUE;
	}

	private static boolean isUsablePickaxe(ItemStack s) {
		return isPickaxe(s) && durabilityLeft(s) > Config.get().minSwapDurability;
	}

	private static boolean hasAnyPickaxe(Inventory inv) {
		for (int i = 0; i < MAIN_SIZE; i++) {
			if (isPickaxe(inv.getItem(i))) return true;
		}
		return false;
	}

	private static int rank(ItemStack s) {
		String path = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
		List<String> order = Config.get().materialPriority;
		for (int i = 0; i < order.size(); i++) {
			String m = order.get(i).toLowerCase();
			if (path.equals(m + "_pickaxe") || path.startsWith(m + "_")) return i;
		}
		return order.size();
	}

	/** Best replacement anywhere in hotbar + main inventory: priority list first (diamond by default), then most durability. */
	private static Candidate findBest(Inventory inv, int excludeSlot) {
		Candidate best = null;
		int bestRank = Integer.MAX_VALUE;
		int bestLeft = -1;
		for (int i = 0; i < MAIN_SIZE; i++) {
			if (i == excludeSlot) continue;
			ItemStack s = inv.getItem(i);
			if (!isUsablePickaxe(s)) continue;
			int r = rank(s);
			int left = durabilityLeft(s);
			if (best == null || r < bestRank || (r == bestRank && left > bestLeft)) {
				best = new Candidate(i, s);
				bestRank = r;
				bestLeft = left;
			}
		}
		return best;
	}

	// ------------------------------------------------------------------ helpers

	/** Finds MultiPlayerGameMode's (int, int, int, ContainerInput, Player) click method by signature, so it works whatever it is named. */
	private static boolean ensureReflection() {
		if (reflectionTried) return clickMethod != null && swapConstant != null;
		reflectionTried = true;
		try {
			Object[] constants = ContainerInput.class.getEnumConstants();
			if (constants != null) {
				for (Object c : constants) {
					if ("SWAP".equals(c.toString())) swapConstant = c;
				}
			}
			for (Method m : MultiPlayerGameMode.class.getMethods()) {
				Class<?>[] p = m.getParameterTypes();
				if (p.length == 5 && p[0] == int.class && p[1] == int.class && p[2] == int.class
						&& p[3] == ContainerInput.class && p[4].isAssignableFrom(LocalPlayer.class)) {
					clickMethod = m;
					break;
				}
			}
		} catch (Throwable t) {
			AutoPickaxeClient.LOGGER.error("Reflection lookup failed", t);
		}
		AutoPickaxeClient.LOGGER.info("Inventory click method: {}, SWAP constant: {}", clickMethod, swapConstant);
		return clickMethod != null && swapConstant != null;
	}

	private static void say(Minecraft mc, String text, ChatFormatting color, boolean always) {
		if (mc.player == null) return;
		if (!always && !Config.get().chatMessages) return;
		mc.player.sendSystemMessage(Component.literal("[AutoPickaxe] " + text).withStyle(color));
	}
}
