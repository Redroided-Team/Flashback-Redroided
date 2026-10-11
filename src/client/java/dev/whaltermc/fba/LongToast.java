// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package dev.whaltermc.fba;

import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.network.chat.Component;

/**
 * A {@link SystemToast} that stays on screen for a fixed duration instead of
 * the few seconds baked into {@link SystemToast.SystemToastId}. Rendering is
 * inherited untouched, so this works on any Minecraft version.
 */
public final class LongToast extends SystemToast {

    private final long visibleUntilMs;

    public LongToast(
            SystemToast.SystemToastId id,
            Component title,
            Component description,
            long millis
    ) {
        super(id, title, description);
        this.visibleUntilMs = System.currentTimeMillis() + millis;
    }

    @Override
    public Toast.Visibility getWantedVisibility() {
        if (System.currentTimeMillis() < visibleUntilMs) {
            return Toast.Visibility.SHOW;
        }
        return super.getWantedVisibility();
    }
}
