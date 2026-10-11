// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package dev.whaltermc.fba.mixin;

import dev.whaltermc.fba.AndroidInput;
import imgui.whaltermc90.ImGui;
import imgui.whaltermc90.ImGuiIO;
import imgui.whaltermc90.flag.ImGuiCond;
import imgui.whaltermc90.type.ImBoolean;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents ImGui editor windows from drifting outside the screen on mobile.
 *
 * After every ImGui.begin() call we read the window's current position and
 * size and push it back inside the display area when any part has escaped.
 * The clamp uses ImGuiCond.Always so it takes effect every frame while a
 * window is out-of-bounds, but normal user dragging still works because
 * ImGui only honours setWindowPos(…, Always) once per frame.
 */
@Mixin(
        targets = "imgui.whaltermc90.ImGui",
        remap = false
)
public abstract class ImGuiWindowMixin {

    // -----------------------------------------------------------------------
    // Inject after every begin() overload
    // -----------------------------------------------------------------------

    @Inject(method = "begin(Ljava/lang/String;)Z",
            at = @At("RETURN"), remap = false, require = 0)
    private static void flashbackRedroided$clampBegin0(
            String title,
            CallbackInfoReturnable<Boolean> cir) {
        flashbackRedroided$clampCurrentWindow();
    }

    @Inject(method = "begin(Ljava/lang/String;Limgui/whaltermc90/type/ImBoolean;)Z",
            at = @At("RETURN"), remap = false, require = 0)
    private static void flashbackRedroided$clampBegin1(
            String title, ImBoolean open,
            CallbackInfoReturnable<Boolean> cir) {
        flashbackRedroided$clampCurrentWindow();
    }

    @Inject(method = "begin(Ljava/lang/String;Limgui/whaltermc90/type/ImBoolean;I)Z",
            at = @At("RETURN"), remap = false, require = 0)
    private static void flashbackRedroided$clampBegin2(
            String title, ImBoolean open, int flags,
            CallbackInfoReturnable<Boolean> cir) {
        flashbackRedroided$clampCurrentWindow();
    }

    @Inject(method = "begin(Ljava/lang/String;I)Z",
            at = @At("RETURN"), remap = false, require = 0)
    private static void flashbackRedroided$clampBegin3(
            String title, int flags,
            CallbackInfoReturnable<Boolean> cir) {
        flashbackRedroided$clampCurrentWindow();
    }

    // -----------------------------------------------------------------------
    // Core clamping logic
    // -----------------------------------------------------------------------

    private static void flashbackRedroided$clampCurrentWindow() {
        if (!AndroidInput.isMobile()) {
            return;
        }

        try {
            ImGuiIO io = ImGui.getIO();
            float displayW = io.getDisplaySizeX();
            float displayH = io.getDisplaySizeY();

            if (displayW <= 0f || displayH <= 0f) {
                return;
            }

            float winX = ImGui.getWindowPosX();
            float winY = ImGui.getWindowPosY();
            float winW = ImGui.getWindowSizeX();
            float winH = ImGui.getWindowSizeY();

            // Minimum visible strip that must stay on screen (title-bar height proxy)
            final float minVisible = 20f;

            // Clamp so at least minVisible pixels of the window remain visible
            float clampedX = Math.max(-(winW - minVisible), Math.min(winX, displayW - minVisible));
            float clampedY = Math.max(0f,                   Math.min(winY, displayH - minVisible));

            if (clampedX != winX || clampedY != winY) {
                ImGui.setWindowPos(clampedX, clampedY, ImGuiCond.Always);
            }
        } catch (Throwable ignored) {
            // Never crash the render loop over a clamping helper
        }
    }
}
