package com.autopickaxe;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

public class AutoPickaxeClient implements ClientModInitializer {
	public static final String MOD_ID = "autopickaxe";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static KeyMapping TOGGLE_KEY;
	public static KeyMapping GUI_KEY;
	public static KeyMapping PRESS_KEY;

	@Override
	public void onInitializeClient() {
		Config.load();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

		TOGGLE_KEY = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.autopickaxe.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, category));
		GUI_KEY = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.autopickaxe.gui", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, category));
		// Unbound by default: the player picks the key in Options > Controls.
		PRESS_KEY = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.autopickaxe.press", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));

		ClientTickEvents.END_CLIENT_TICK.register(Swapper::tick);
		LOGGER.info("Auto Pickaxe Swapper loaded");
	}
}
