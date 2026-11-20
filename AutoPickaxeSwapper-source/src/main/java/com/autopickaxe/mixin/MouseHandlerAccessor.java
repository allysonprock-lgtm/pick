package com.autopickaxe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;

@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
	@Invoker
	void invokeOnButton(long window, MouseButtonInfo info, int action);
}
