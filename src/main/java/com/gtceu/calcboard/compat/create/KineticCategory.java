package com.gtceu.calcboard.compat.create;

import net.minecraft.resources.ResourceLocation;

/**
 * Defines the four rotational kinetic machine and energy converter categories (RFC-036 / ADR-036).
 */
public enum KineticCategory {
    SOURCE("kinetic_source", "category.gtcalcboard.kinetic_source", ResourceLocation.tryParse("create:large_water_wheel")),
    FUEL_ENGINE("fuel_kinetic_engine", "category.gtcalcboard.fuel_kinetic_engine", ResourceLocation.tryParse("create:steam_engine")),
    MOTOR("electric_motor", "category.gtcalcboard.electric_motor", ResourceLocation.tryParse("createaddition:electric_motor")),
    ALTERNATOR("kinetic_alternator", "category.gtcalcboard.kinetic_alternator", ResourceLocation.tryParse("createaddition:alternator"));

    private final String id;
    private final String langKey;
    private final ResourceLocation defaultIcon;
    private final ResourceLocation categoryId;

    KineticCategory(String id, String langKey, ResourceLocation defaultIcon) {
        this.id = id;
        this.langKey = langKey;
        this.defaultIcon = defaultIcon;
        this.categoryId = ResourceLocation.tryParse("gtcalcboard:" + id);
    }

    public String getId() {
        return id;
    }

    public String getLangKey() {
        return langKey;
    }

    public ResourceLocation getDefaultIcon() {
        return defaultIcon;
    }

    public ResourceLocation getCategoryId() {
        return categoryId;
    }
}
