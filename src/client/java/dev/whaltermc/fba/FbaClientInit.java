// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package dev.whaltermc.fba;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

public final class FbaClientInit implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        try {
            ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
                try {
                    client.getToastManager().addToast(new LongToast(
                            SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                            Component.literal("FBA loaded"),
                            Component.literal("Android patches active"),
                            15_000L
                    ));
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }
}
