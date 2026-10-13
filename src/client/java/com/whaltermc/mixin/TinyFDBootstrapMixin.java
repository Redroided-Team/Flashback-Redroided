package com.whaltermc.mixin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Minecraft 26.2+ pre-loads the LWJGL tinyfd native during bootstrap
 * ({@code NativeLibrariesBootstrap.loadTinyFD}). Some Android launchers do
 * not provide tinyfd natives (nor a resolvable library path), so the lookup
 * returns null and the game dies with {@code NullPointerException: Loading
 * library tinyfd} before any mod loads. tinyfd is only used for desktop
 * native file dialogs, which never run on Android, so a failed probe is
 * safely replaced with an empty version string. Desktop behavior is
 * untouched: the real call runs first and its result is used whenever it
 * works. require=0: inert where the bootstrap has no tinyfd step.
 *
 * <p>Version gating lives in {@code com.whaltermc.FlashbackMixinPlugin}:
 * on games without {@code NativeLibrariesBootstrap} (1.21.x, 26.1) this
 * mixin is never applied, so Mixin does not log missing-target warnings.
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
