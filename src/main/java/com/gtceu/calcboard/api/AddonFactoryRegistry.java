package com.gtceu.calcboard.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of deserializer factories for polymorphic MachineAddon categories.
 * Allows mod adapters to register custom MachineAddon subclasses without hardcoding them in the API.
 */
public final class AddonFactoryRegistry {

    @FunctionalInterface
    public interface AddonFactory {
        MachineAddon create(String id, String name, String description, ResourceLocation icon, CompoundTag tag);
    }

    private static final Map<AddonCategory, AddonFactory> FACTORIES = new ConcurrentHashMap<>();

    private AddonFactoryRegistry() {}

    public static void register(AddonCategory category, AddonFactory factory) {
        if (category != null && factory != null) {
            FACTORIES.put(category, factory);
        }
    }

    public static MachineAddon create(AddonCategory category, String id, String name, String description, ResourceLocation icon, CompoundTag tag) {
        if (category != null) {
            AddonFactory factory = FACTORIES.get(category);
            if (factory != null) {
                try {
                    return factory.create(id, name, description, icon, tag);
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }
}
