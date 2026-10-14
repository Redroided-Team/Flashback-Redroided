// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package dev.whaltermc.fba.mixin;

import dev.whaltermc.fba.AndroidDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// FBA: Flashback 0.43.6 (26.3) reads the editor content scale from SDL3
// (SDL_GetWindowDisplayScale), which returns 0.0f on failure. ReplayUI feeds
// that straight into its UI scale — and into initFonts before the in-frame
// 0.125 floor runs — so a zero rasterizes a 2px font or trips ImGui's size
// assert. Any non-positive answer becomes the device display density instead.
@Mixin(targets = "com.moulberry.flashback.editor.ui.CustomImGuiWindowerSdl", remap = false)
public abstract class CustomImGuiWindowerSdlMixin {

    @Inject(
            method = "getContentScale",
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void flashbackRedroided$defaultContentScale(CallbackInfoReturnable<Float> cir) {
        if (!(cir.getReturnValueF() > 0.0f)) {
            cir.setReturnValue(AndroidDisplay.contentScale());
        }
    }
}
