package com.autopickaxe;

import org.lwjgl.glfw.GLFW;

import com.autopickaxe.mixin.KeyboardHandlerAccessor;
import com.autopickaxe.mixin.MouseHandlerAccessor;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;

/** Sends a real key/mouse event through the game's own input pipeline, so any keybind (vanilla or other mods) reacts to it. */
public final class InputSim {
	private InputSim() {
	}

	public static void send(Minecraft mc, InputConstants.Key key, boolean press) {
		long window = mc.getWindow().handle();
		int action = press ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE;
		switch (key.getType()) {
			case KEYSYM -> ((KeyboardHandlerAccessor) mc.keyboardHandler).invokeKeyPress(window, action, new KeyEvent(key.getValue(), 0, 0));
			case SCANCODE -> ((KeyboardHandlerAccessor) mc.keyboardHandler).invokeKeyPress(window, action, new KeyEvent(GLFW.GLFW_KEY_UNKNOWN, key.getValue(), 0));
			case MOUSE -> ((MouseHandlerAccessor) mc.mouseHandler).invokeOnButton(window, new MouseButtonInfo(key.getValue(), 0), action);
		}
	}
}
