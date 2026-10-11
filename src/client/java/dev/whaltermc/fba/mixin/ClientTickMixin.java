// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package dev.whaltermc.fba.mixin;

import dev.whaltermc.fba.AndroidInput;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Pumps the touch state machine once per client tick. Deferred cursor-mode
// releases and synthetic-hold timeouts used to run only when something
// queried the cursor position, so a tap with no motion afterward left the
// grab (and the held button) stuck forever. Ticking unconditionally fixes
// that; the call itself is a few field reads when idle.
@Mixin(Minecraft.class)
public abstract class ClientTickMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void flashbackRedroided$pumpInput(CallbackInfo ci) {
        AndroidInput.frame();
    }
}
