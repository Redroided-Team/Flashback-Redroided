package com.whaltermc.mixin;

import com.whaltermc.MobileCompat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Same as 1.21's CursorInputMixin: behave exactly as Flashback intended
// and pass the mode change through the original call. Only record the
// result so the rest of the UI reads consistent state.
@Mixin(targets = "com.moulberry.flashback.editor.ui.ReplayUI", remap = false, priority = 1000)
public abstract class ReplayUIMobileCursorMixin {

    @WrapOperation(
            method = "transitionActiveState",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/glfw/GLFW;glfwSetInputMode(JII)V",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private static void flashbackRedroided$setCursorMode(long window, int mode, int value, Operation<Void> original) {
        original.call(window, mode, value);
        if (mode == GLFW.GLFW_CURSOR) {
            MobileCompat.syncCursorMode(window, value);
        }
    }

    @WrapOperation(
            method = "transitionActiveState",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/glfw/GLFW;glfwSetCursorPos(JDD)V",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private static void flashbackRedroided$setCursorPos(long window, double x, double y, Operation<Void> original) {
        // Forward to real GLFW so camera centering and cursor warps happen
        // as Flashback intended instead of being silently suppressed.
        original.call(window, x, y);
    }
}
