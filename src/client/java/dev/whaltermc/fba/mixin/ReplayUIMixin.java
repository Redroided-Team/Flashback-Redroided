// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package dev.whaltermc.fba.mixin;

import dev.whaltermc.fba.AndroidInput;
import imgui.whaltermc90.ImGui;
import imgui.whaltermc90.ImGuiIO;
import imgui.whaltermc90.flag.ImGuiConfigFlags;
import imgui.whaltermc90.flag.ImGuiDockNodeFlags;
import imgui.whaltermc90.flag.ImGuiWindowFlags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes the Flashback editor respect mobile screen dimensions: enables ImGui
 * docking and renders a full-screen transparent dockspace each frame.
 * Per-window clamping lives in {@link ImGuiWindowMixin}.
 */
@Mixin(
        targets = "com.moulberry.flashback.editor.ui.ReplayUI",
        remap = false
)
public abstract class ReplayUIMixin {

    private static boolean flashbackRedroided$dockingEnabled = false;

    /**
     * Runs once per frame right after Flashback starts its ImGui frame: the
     * dockspace host must precede all other windows (ImGui ordering), and
     * starting it before {@code newFrame()} would trip the frame assertion.
     */
    @Inject(
            method = "drawOverlayInternal",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/imgui/whaltermc90/ImGui;newFrame()V",
                    shift = At.Shift.AFTER,
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private static void flashbackRedroided$setupDockspace(CallbackInfo ci) {
        if (!AndroidInput.isMobile()) {
            return;
        }

        try {
            ImGuiIO io = ImGui.getIO();

            // Enable docking once – keeps the ini-persisted dock layout across frames.
            if (!flashbackRedroided$dockingEnabled) {
                io.addConfigFlags(ImGuiConfigFlags.DockingEnable);
                flashbackRedroided$dockingEnabled = true;
            }

            float displayW = io.getDisplaySizeX();
            float displayH = io.getDisplaySizeY();

            if (displayW <= 0f || displayH <= 0f) {
                return;
            }

            // Create an invisible full-screen host window that owns the dockspace.
            // NoMove + NoResize + NoTitleBar + NoBringToFront ensure it stays
            // behind everything else and cannot be interacted with.
            int hostFlags =
                    ImGuiWindowFlags.NoTitleBar    |
                    ImGuiWindowFlags.NoCollapse     |
                    ImGuiWindowFlags.NoResize       |
                    ImGuiWindowFlags.NoMove         |
                    ImGuiWindowFlags.NoBackground   |
                    ImGuiWindowFlags.NoBringToFrontOnFocus |
                    ImGuiWindowFlags.NoDocking      |
                    ImGuiWindowFlags.NoNav          |
                    ImGuiWindowFlags.NoScrollbar    |
                    ImGuiWindowFlags.NoScrollWithMouse;

            ImGui.setNextWindowPos(0f, 0f);
            ImGui.setNextWindowSize(displayW, displayH);

            ImGui.begin("##fba_dockspace_host", hostFlags);

            // Passthrough: the host window itself is transparent to clicks.
            int dockFlags = ImGuiDockNodeFlags.PassthruCentralNode;

            // Use a fixed stable ID so the layout is remembered across frames.
            // 0xFBA00001 is an arbitrary constant unlikely to collide with Flashback's own IDs.
            ImGui.dockSpace(0xFBA00001, 0f, 0f, dockFlags);

            ImGui.end();
        } catch (Throwable ignored) {
            // Never crash the render loop
        }
    }
}
