package com.whaltermc.mixin;

import com.whaltermc.MobileCompat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Records the mode change for consistent UI state; the change itself goes
// through the original call, exactly as Flashback intended.
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
        // Forwards to real GLFW so camera centering and cursor warps happen.
        original.call(window, x, y);
    }
}
