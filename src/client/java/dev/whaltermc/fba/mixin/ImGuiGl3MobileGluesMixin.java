// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package dev.whaltermc.fba.mixin;

import dev.whaltermc.fba.AndroidInput;
import org.lwjgl.opengl.GL32;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Two MobileGlues hardening changes for Flashback's ImGui GL backend
 * ({@code CustomImGuiImplGl3}). Both are scoped to MobileGlues; every other
 * renderer keeps Flashback's original behaviour.
 *
 * <p><b>1. Report GL 2.1 to the backend.</b> MobileGlues advertises a high
 * desktop GL (it declares GLSL 4.60), which trips Flashback's
 * {@code glVersion >= 320} / {@code >= 450} feature gates into enabling
 * {@code RendererHasVtxOffset} and drawing with
 * {@code glDrawElementsBaseVertex}. MobileGlues' base-vertex / MultiDraw
 * emulation is a documented black-UI culprit (its V1.3.2 notes call out
 * "UI disappearance" from the draw_elements_base_vertex path, and issue #404
 * reports a black screen with "prefer base vertex"). Faking the version to
 * 2.1 keeps every modern path off: plain {@code glDrawElements}, no
 * clip-origin query, no base vertex.
 *
 * <p><b>2. Use the GLSL 1.20 compatibility shader.</b> {@code ReplayUI} calls
 * {@code init("#version 150")}, so {@code createDeviceObjects} always selects
 * the {@code glsl130} desktop pair (value 150 falls through to the else arm).
 * Dropping the version below 130 selects the {@code glsl120} pair instead,
 * which already guards its precision qualifier with {@code #ifdef GL_ES} — the
 * classic desktop-to-ES translation path MobileGlues is battle-tested against.
 * (Forcing the {@code 300 es} pair proved device-dependent: some GPUs compiled
 * it, others flickered or stayed black.)
 */
@Mixin(targets = "com.moulberry.flashback.editor.ui.CustomImGuiImplGl3", remap = false)
public abstract class ImGuiGl3MobileGluesMixin {

    // glGetInteger is invoked three times in init(): GL_MAJOR_VERSION,
    // GL_MINOR_VERSION and GL_CONTEXT_PROFILE_MASK. Only the first two are
    // faked; the profile-mask query passes straight through to the driver.
    @Redirect(
            method = "init(Ljava/lang/String;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/opengl/GL32;glGetInteger(I)I",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private int flashbackRedroided$fakeDesktopGl(int pname) {
        if (AndroidInput.isMobileGlues()) {
            if (pname == GL32.GL_MAJOR_VERSION) {
                return 2;
            }
            if (pname == GL32.GL_MINOR_VERSION) {
                return 1;
            }
        }
        return GL32.glGetInteger(pname);
    }

    @ModifyVariable(
            method = "init(Ljava/lang/String;)Z",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false,
            require = 0
    )
    private String flashbackRedroided$useCompatShader(String glslVersion) {
        // Value must parse below 130 to reach createDeviceObjects' glsl120 arm.
        return AndroidInput.isMobileGlues() ? "#version 120" : glslVersion;
    }
}
