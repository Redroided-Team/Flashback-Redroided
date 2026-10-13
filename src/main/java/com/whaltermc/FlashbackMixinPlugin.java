package com.whaltermc;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Skips version-conditional mixins whose target class is absent on the
 * running game, instead of logging missing-target warnings every launch.
 * Currently gates only {@code TinyFDBootstrapMixin} (26.2+ target).
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
     * Probes for the target class without initialising it: resource lookup
     * first, {@code Class.forName} only as a fallback.
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
