package com.whaltermc.mixin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Guards MC 26.2+'s tinyfd bootstrap pre-load: launchers without tinyfd
 * natives die with {@code NullPointerException: Loading library tinyfd}, and
 * desktop file dialogs never run on Android, so a failed probe becomes an
 * empty version string. Desktop behavior is untouched. require=0: inert
 * where the bootstrap has no tinyfd step.
 */
@Mixin(
        targets = "com.mojang.blaze3d.platform.NativeLibrariesBootstrap",
        remap = false
)
public abstract class TinyFDBootstrapMixin {

    private static final Logger LOGGER =
            LoggerFactory.getLogger("flashback_android/natives");

    @Redirect(
            method = "loadTinyFD",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/util/tinyfd/TinyFileDialogs;" +
                            "tinyfd_getGlobalChar(Ljava/lang/CharSequence;)" +
                            "Ljava/lang/String;",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private static String flashbackRedroided$guardTinyFD(CharSequence name) {
        String version;
        try {
            version = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_getGlobalChar(name);
        } catch (Throwable t) {
            LOGGER.info("FBA: tinyfd native unavailable, skipped bootstrap pre-load ({})",
                    t.toString());
            return "";
        }
        if (version == null) {
            LOGGER.info("FBA: tinyfd native unavailable (null), skipped bootstrap pre-load");
            return "";
        }
        return version;
    }
}
