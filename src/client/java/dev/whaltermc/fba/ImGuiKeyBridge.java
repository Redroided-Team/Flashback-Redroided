// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package dev.whaltermc.fba;

import imgui.whaltermc90.ImGui;
import imgui.whaltermc90.flag.ImGuiKey;

public final class ImGuiKeyBridge {

    private ImGuiKeyBridge() {}

    public static boolean isKeyDown(int key) {
        int k = ImGuiKeyMapper.map(key);
        return k != ImGuiKey.None && ImGui.isKeyDown(k);
    }

    public static boolean isKeyPressed(int key) {
        int k = ImGuiKeyMapper.map(key);
        return k != ImGuiKey.None && ImGui.isKeyPressed(k);
    }

    public static boolean isKeyPressed(int key, boolean repeat) {
        int k = ImGuiKeyMapper.map(key);
        return k != ImGuiKey.None && ImGui.isKeyPressed(k, repeat);
    }

    public static boolean isKeyReleased(int key) {
        int k = ImGuiKeyMapper.map(key);
        return k != ImGuiKey.None && ImGui.isKeyReleased(k);
    }
}
