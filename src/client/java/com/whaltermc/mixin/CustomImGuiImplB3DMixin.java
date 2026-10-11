package com.whaltermc.mixin;

import com.whaltermc.MobileCompat;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(
        targets = "com.moulberry.flashback.editor.ui.CustomImGuiImplB3D",
        remap = false
)
public abstract class CustomImGuiImplB3DMixin {

    @ModifyArgs(
            method = "renderDrawData",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderPass;enableScissor(IIII)V",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private void flashbackRedroided$fixInvalidScissor(Args args) {
        int width = args.get(2);
        int height = args.get(3);

        // GLES / MobileGlues drivers error on 0x0 scissor (desktop GL silently
        // ignores it). Flashback can emit 0-width clips for empty ImGui windows.
        // Clamp to 1x1 so the driver stays happy; visually identical (1px).
        if (width <= 0 || height <= 0) {
            args.set(2, 1);
            args.set(3, 1);
        }
    }

    /**
     * MobileGlues cannot compile Flashback's desktop ImGui shaders:
     * {@code #version 330} + {@code GL_ARB_separate_shader_objects : require}
     * fails on GLES. When MobileGlues is detected, redirect to our
     * GLES-compatible copies ({@code flashback_android:core/imgui_b3d_mobile}).
     *
     * <p>require=0 so a Flashback update that renames init() just disables
     * this fix instead of crashing.
     */
    @Redirect(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/resources/Identifier;fromNamespaceAndPath(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/resources/Identifier;",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private static Identifier flashbackRedroided$mobileGluesShader(
            String namespace,
            String path
    ) {
        if (MobileCompat.isMobileGlues()
                && "flashback".equals(namespace)
                && "core/imgui_b3d".equals(path)) {
            return Identifier.fromNamespaceAndPath("flashback_android", "core/imgui_b3d_mobile");
        }
        return Identifier.fromNamespaceAndPath(namespace, path);
    }
}