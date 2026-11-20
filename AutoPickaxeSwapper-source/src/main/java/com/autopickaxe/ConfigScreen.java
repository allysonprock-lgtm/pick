package com.autopickaxe;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

public class ConfigScreen extends Screen {
	private final Screen parent;
	private EditBox urlBox;
	private Button enabledBtn;
	private Button webhookBtn;
	private Button keyBtn;

	public ConfigScreen(Screen parent) {
		super(Component.literal("Auto Pickaxe Swapper"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Config cfg = Config.get();
		int w = 250;
		int x = this.width / 2 - w / 2;
		int y = this.height / 2 - 100;
		int step = 24;

		addRenderableWidget(label("Auto Pickaxe Swapper", x, y, w));

		enabledBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
			cfg.enabled = !cfg.enabled;
			refresh();
		}).bounds(x, y + step, w, 20).build());

		webhookBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
			cfg.webhookEnabled = !cfg.webhookEnabled;
			refresh();
		}).bounds(x, y + step * 2, w, 20).build());

		keyBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
			cfg.pressKeyEnabled = !cfg.pressKeyEnabled;
			refresh();
		}).bounds(x, y + step * 3, w, 20).build());

		addRenderableWidget(label("Discord webhook URL:", x, y + step * 4, w));

		urlBox = new EditBox(Minecraft.getInstance().font, x, y + step * 5, w, 20, Component.literal("Discord webhook URL"));
		urlBox.setMaxLength(400);
		urlBox.setValue(cfg.webhookUrl);
		addRenderableWidget(urlBox);

		addRenderableWidget(Button.builder(Component.literal("Send test message"), b -> sendTest())
				.bounds(x, y + step * 6, w, 20).build());

		addRenderableWidget(label("Pick the key in Options > Controls", x, y + step * 7, w));

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(x, y + step * 8, w, 20).build());

		refresh();
	}

	private static Button label(String text, int x, int y, int w) {
		Button b = Button.builder(Component.literal(text), btn -> {
		}).bounds(x, y, w, 20).build();
		b.active = false;
		return b;
	}

	private void refresh() {
		Config cfg = Config.get();
		enabledBtn.setMessage(Component.literal("Auto swap: " + (cfg.enabled ? "ON" : "OFF")));
		webhookBtn.setMessage(Component.literal("Discord notification: " + (cfg.webhookEnabled ? "ON" : "OFF")));
		String keyName = "not set";
		if (!AutoPickaxeClient.PRESS_KEY.isUnbound()) {
			InputConstants.Key k = KeyMappingHelper.getBoundKeyOf(AutoPickaxeClient.PRESS_KEY);
			keyName = k.getDisplayName().getString();
		}
		keyBtn.setMessage(Component.literal("Press key when out: " + (cfg.pressKeyEnabled ? "ON" : "OFF") + " [" + keyName + "]"));
	}

	private void sendTest() {
		Minecraft mc = Minecraft.getInstance();
		String url = urlBox.getValue().trim();
		Config.get().webhookUrl = url;
		Webhook.send(url, "Test message", "Auto Pickaxe Swapper can reach this webhook.", 0x2ECC71, null, result -> mc.execute(() -> {
			if (mc.player != null) {
				boolean ok = "OK".equals(result);
				mc.player.sendSystemMessage(Component.literal("[AutoPickaxe] Webhook test: " + (ok ? "success" : result))
						.withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
			}
		}));
	}

	@Override
	public void onClose() {
		Config.get().webhookUrl = urlBox.getValue().trim();
		Config.save();
		Minecraft.getInstance().setScreen(parent);
	}
}
