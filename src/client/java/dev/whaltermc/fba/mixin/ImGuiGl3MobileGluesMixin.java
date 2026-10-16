// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package dev.whaltermc.fba.mixin;

import dev.whaltermc.fba.AndroidInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Flashback pins its ImGui GL backend to a desktop shader: {@code ReplayUI}
 * calls {@code init("#version 150")}, so {@code createDeviceObjects} always
 * resolves {@code glslVersionValue == 150} and selects the {@code glsl130}
 * desktop pair ({@code in}/{@code out} + {@code texture()}) — never the
 * GLES-native {@code #version 300 es} pair the same class already ships.
 *
 * Under MobileGlues that desktop GLSL has to be rewritten to GLSL ES at
 * runtime. When the translation fails to compile, the program links to
 * nothing and every editor panel draws black. Retargeting the GLSL version to
 * {@code 300 es} makes the backend take its ES path instead: pure GLES
 * sources with explicit precision qualifiers, which is exactly what
 * MobileGlues asks shader authors to emit. No translation is involved.
 *
 * The override is scoped to MobileGlues only; every other renderer keeps
 * Flashback's original {@code #version 150}.
 */
@Mixin(targets = "com.moulberry.flashback.editor.ui.CustomImGuiImplGl3", remap = false)
public abstract class ImGuiGl3MobileGluesMixin {

    @ModifyVariable(
            method = "init(Ljava/lang/String;)Z",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false,
            require = 0
    )
    private String flashbackRedroided$useGlesShader(String glslVersion) {
        return AndroidInput.isMobileGlues() ? "#version 300 es" : glslVersion;
    }
}
