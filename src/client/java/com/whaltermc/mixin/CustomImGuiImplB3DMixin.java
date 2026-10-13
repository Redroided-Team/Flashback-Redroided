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

        // GLES drivers error on 0x0 scissor: clamp to 1x1 (visually identical).
        if (width <= 0 || height <= 0) {
            args.set(2, 1);
            args.set(3, 1);
        }
    }

    /**
     * Redirects to GLES-compatible ImGui shaders when MobileGlues is detected
     * (desktop {@code #version 330} shaders fail on GLES). require=0: inert
     * if Flashback renames init().
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