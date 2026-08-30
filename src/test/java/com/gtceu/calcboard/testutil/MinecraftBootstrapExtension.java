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
                // Initialize mock LoadingModList so NeoForge's FeatureFlagLoader doesn't NPE during Bootstrap
                try {
                    Class<?> lmlClass = Class.forName("net.neoforged.fml.loading.LoadingModList");
                    java.lang.reflect.Field instanceField = lmlClass.getDeclaredField("INSTANCE");
                    instanceField.setAccessible(true);
                    if (instanceField.get(null) == null) {
                        java.lang.reflect.Constructor<?> ctor = lmlClass.getDeclaredConstructor(
                                java.util.List.class, java.util.List.class, java.util.List.class, java.util.Map.class);
                        ctor.setAccessible(true);
                        Object instance = ctor.newInstance(
                                new java.util.ArrayList<>(), new java.util.ArrayList<>(), new java.util.ArrayList<>(), new java.util.HashMap<>());
                        instanceField.set(null, instance);
                    }
                } catch (Throwable ignored) {}

                net.minecraft.SharedConstants.tryDetectVersion();
                net.minecraft.server.Bootstrap.bootStrap();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }
}
