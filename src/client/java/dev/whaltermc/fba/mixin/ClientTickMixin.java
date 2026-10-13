// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package dev.whaltermc.fba.mixin;

import dev.whaltermc.fba.AndroidInput;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Pumps the touch state machine every client tick, so deferred releases and
// hold timeouts cannot get stuck when nothing queries the cursor.
@Mixin(Minecraft.class)
public abstract class ClientTickMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void flashbackRedroided$pumpInput(CallbackInfo ci) {
        AndroidInput.frame();
    }
}
