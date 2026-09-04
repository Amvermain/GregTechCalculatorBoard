package com.gtceu.calcboard.testutil;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.concurrent.atomic.AtomicBoolean;

public class MinecraftBootstrapExtension implements BeforeAllCallback {
    private static final AtomicBoolean BOOTSTRAPPED = new AtomicBoolean(false);

    @Override
    public void beforeAll(ExtensionContext context) {
        ensureBootstrapped();
    }

    public static void ensureBootstrapped() {
        if (BOOTSTRAPPED.compareAndSet(false, true)) {
            try {
                net.minecraft.SharedConstants.tryDetectVersion();
                java.lang.reflect.Field field = net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
                field.setAccessible(true);
                if (!field.getBoolean(null)) {
                    net.minecraft.server.Bootstrap.bootStrap();
                }
            } catch (Throwable ignored) {
            }
            TestMultiblockFixtures.initTestEnvironmentDefaults();
        }
    }
}
