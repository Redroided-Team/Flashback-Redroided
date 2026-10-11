// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package dev.whaltermc.fba.mixin;

import dev.whaltermc.fba.AndroidInput;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
        targets = "com.moulberry.flashback.editor.ui.CustomImGuiImplGlfw",
        remap = false
)
public abstract class ImGuiGlfwMixin {

    @Inject(
            method = "updateKeyModifiers",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void flashbackRedroided$updateKeyModifiers(long window, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(
            method = "glfwKeyToImGuiKey",
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void flashbackRedroided$updateImGui192KeyValues(
            int glfwKey,
            CallbackInfoReturnable<Integer> cir
    ) {
        int imguiKey = cir.getReturnValue();
        cir.setReturnValue(dev.whaltermc.fba.ImGuiKeyMapper.map(imguiKey));
    }

    @WrapOperation(
            method = {"ungrab", "setGrabbed"},
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/glfw/GLFW;glfwSetInputMode(JII)V",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private void flashbackRedroided$setGrabCursorMode(long window, int mode, int value, Operation<Void> original) {
        // Behave exactly as Flashback intended: the mode change goes through.
        // The mobile request/defer state machine used to swallow these, which
        // left the cursor in states Flashback never asked for. Only record the
        // result so the rest of the UI reads consistent state.
        original.call(window, mode, value);
        if (mode == org.lwjgl.glfw.GLFW.GLFW_CURSOR) {
            AndroidInput.syncCursorMode(window, value);
        }
    }

    @WrapOperation(
            method = "updateMouseCursor",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/glfw/GLFW;glfwSetInputMode(JII)V",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private void flashbackRedroided$setCursorModeIfChanged(long window, int mode, int value, Operation<Void> original) {
        // Same as above: pass through, only record.
        original.call(window, mode, value);
        if (mode == org.lwjgl.glfw.GLFW.GLFW_CURSOR) {
            AndroidInput.syncCursorMode(window, value);
        }
    }

    @WrapOperation(
            method = {"ungrab", "updateMousePosAndButtons"},
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/glfw/GLFW;glfwSetCursorPos(JDD)V",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private void flashbackRedroided$setCursorPos(long window, double x, double y, Operation<Void> original) {
        // Forward to real GLFW so camera centering and cursor warps happen as
        // Flashback intended instead of being silently suppressed on mobile.
        original.call(window, x, y);
    }

    @WrapOperation(
            method = {"updateReleaseAllKeys", "updateMouseCursor"},
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/glfw/GLFW;glfwSetCursor(JJ)V",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private void flashbackRedroided$setCursorShape(long window, long cursor, Operation<Void> original) {
        if (!AndroidInput.isMobile()) {
            original.call(window, cursor);
        } else {
            AndroidInput.setCursorShape(window, cursor);
        }
    }
}
