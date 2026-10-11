package com.whaltermc.mixin;

import com.whaltermc.MobileCompat;
import imgui.whaltermc90.ImGui;
import imgui.whaltermc90.ImGuiIO;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
        targets = "com.moulberry.flashback.editor.ui.CustomImGuiImplGlfw",
        remap = false
)
public abstract class CustomImGuiImplGlfwMixin {

    @Inject(
            method = "updateKeyModifiers",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void flashbackRedroided$updateKeyModifiers(
            long window,
            CallbackInfo ci
    ) {
        ci.cancel();

        try {
            if (!ImGui.getCurrentContext().isValidPtr()) {
                return;
            }

            ImGuiIO io = ImGui.getIO();

            io.addKeyEvent(
                    4096,
                    flashbackRedroided$down(window, 341, 345)
            );

            io.addKeyEvent(
                    8192,
                    flashbackRedroided$down(window, 340, 344)
            );

            io.addKeyEvent(
                    16384,
                    flashbackRedroided$down(window, 342, 346)
            );

            io.addKeyEvent(
                    32768,
                    flashbackRedroided$down(window, 343, 347)
            );
        } catch (Throwable ignored) {
        }
    }

    @Inject(
            method = "glfwKeyToImGuiKey",
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private static void flashbackRedroided$keyToImGui192(
            int glfwKey,
            CallbackInfoReturnable<Integer> cir
    ) {
        cir.setReturnValue(
                com.whaltermc.FlashbackImGuiKeyMapper.map(
                        cir.getReturnValue()
                )
        );
    }

    // Same as 1.21's ImGuiGlfwMixin: behave exactly as Flashback intended,
    // the mode change goes through the original call. Only record the result
    // so the rest of the UI reads consistent state.
    @WrapOperation(
            method = {
                    "ungrab",
                    "setGrabbed",
                    "updateMouseCursor"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/glfw/GLFW;glfwSetInputMode(JII)V",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private void flashbackRedroided$cursorMode(
            long window,
            int mode,
            int value,
            Operation<Void> original
    ) {
        original.call(window, mode, value);
        if (mode == GLFW.GLFW_CURSOR) {
            MobileCompat.syncCursorMode(window, value);
        }
    }

    @WrapOperation(
            method = {
                    "ungrab",
                    "updateMousePosAndButtons"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/glfw/GLFW;glfwSetCursorPos(JDD)V",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private void flashbackRedroided$cursorPos(
            long window,
            double x,
            double y,
            Operation<Void> original
    ) {
        // Other mods may also redirect cursor pos; only Flashback's own calls are targeted
        // by the method names above, so this does not affect other mods.
        //
        // Forward to real GLFW so Flashback's camera centering and cursor
        // warps happen as intended instead of being silently suppressed.
        original.call(window, x, y);
    }

    @WrapOperation(
            method = {
                    "updateReleaseAllKeys",
                    "updateMouseCursor"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/glfw/GLFW;glfwSetCursor(JJ)V",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private void flashbackRedroided$cursorShape(
            long window,
            long cursor,
            Operation<Void> original
    ) {
        if (!MobileCompat.isMobile()) {
            original.call(window, cursor);
        } else {
            MobileCompat.setCursorShape(window, cursor);
        }
    }

    private static boolean flashbackRedroided$down(
            long window,
            int left,
            int right
    ) {
        return GLFW.glfwGetKey(window, left) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, right) == GLFW.GLFW_PRESS;
    }
}