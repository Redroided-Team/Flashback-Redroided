package com.whaltermc;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Gates version-conditional mixins so Mixin never attempts to resolve a
 * target class that does not exist on the running game, which would
 * otherwise spam
 * {@code Error loading class ... (ClassNotFoundException)} /
 * {@code @Mixin target ... was not found} warnings on every launch.
 *
 * <p>Currently this only gates {@code TinyFDBootstrapMixin}: its target
 * ({@code com.mojang.blaze3d.platform.NativeLibrariesBootstrap}) only
 * exists on Minecraft 26.2+. On 1.21.x (and 26.1) the class is absent, so
 * the mixin is skipped entirely instead of failing target resolution.
 */
public class FlashbackMixinPlugin implements IMixinConfigPlugin {

    private static final String TINY_FD_MIXIN_SUFFIX = "TinyFDBootstrapMixin";
    private static final String TINY_FD_TARGET =
            "com.mojang.blaze3d.platform.NativeLibrariesBootstrap";
    private static final String TINY_FD_TARGET_PATH =
            "com/mojang/blaze3d/platform/NativeLibrariesBootstrap.class";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName != null && mixinClassName.endsWith(TINY_FD_MIXIN_SUFFIX)) {
            return hasTinyFDBootstrap();
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo) {
    }

    /**
     * Probes for the bootstrap class without initialising it. The resource
     * lookup is tried first so no game class gets loaded/verified just to
     * answer "does this exist"; {@code Class.forName(..., false, ...)} is
     * only a fallback for launchers with unusual resource visibility.
     */
    private static boolean hasTinyFDBootstrap() {
        ClassLoader cl = FlashbackMixinPlugin.class.getClassLoader();
        try {
            if (cl != null && cl.getResource(TINY_FD_TARGET_PATH) != null) {
                return true;
            }
            ClassLoader ctx = Thread.currentThread().getContextClassLoader();
            if (ctx != null && ctx != cl
                    && ctx.getResource(TINY_FD_TARGET_PATH) != null) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            Class.forName(TINY_FD_TARGET, false, cl);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
