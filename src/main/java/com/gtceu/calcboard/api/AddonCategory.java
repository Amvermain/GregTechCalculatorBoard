package com.gtceu.calcboard.api;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamic, extensible category registry for machine addons, traits, augments, and hardware components.
 * Allows mod adapters to register custom addon categories at runtime without modifying core enums.
 */
public final class AddonCategory {

    private static final Map<String, AddonCategory> REGISTRY = new ConcurrentHashMap<>();

    public static final AddonCategory PARALLEL = register("parallel", "Parallel Hatches", "gui.gtcalcboard.addon_cat.parallel", 100);
    public static final AddonCategory MAINTENANCE = register("maintenance", "Maintenance & Hatches", "gui.gtcalcboard.addon_cat.maintenance", 95);
    public static final AddonCategory COIL = register("coil", "Heating Coils", "gui.gtcalcboard.addon_cat.coil", 90);
    public static final AddonCategory ROTOR = register("rotor", "Turbine Rotors", "gui.gtcalcboard.addon_cat.rotor", 80);
    public static final AddonCategory REFLECTOR = register("reflector", "Fusion Reflectors", "gui.gtcalcboard.addon_cat.reflector", 70);
    public static final AddonCategory MULTIBLOCK_TRAIT = register("multiblock_trait", "Multiblock Traits", "gui.gtcalcboard.addon_cat.trait", 65);
    public static final AddonCategory THERMAL_AUGMENT = register("thermal_augment", "Thermal Augments & Kits", "gui.gtcalcboard.addon_cat.thermal", 60);
    public static final AddonCategory MAGNET = register("magnet", "Magnets & Magnetic Cores", "gui.gtcalcboard.addon_cat.magnet", 50);
    public static final AddonCategory CUSTOM = register("custom", "Custom Modifiers", "gui.gtcalcboard.addon_cat.custom", 0);

    private final String id;
    private final String englishName;
    private final String translatableKey;
    private final int priority;

    public AddonCategory(String id, String englishName, String translatableKey, int priority) {
        this.id = id != null ? id.toLowerCase(Locale.ROOT) : "custom";
        this.englishName = englishName != null ? englishName : id;
        this.translatableKey = translatableKey != null ? translatableKey : "gui.gtcalcboard.addon_cat." + this.id;
        this.priority = priority;
    }

    public static synchronized AddonCategory register(String id, String englishName, String translatableKey, int priority) {
        if (id == null) return CUSTOM;
        String key = id.toLowerCase(Locale.ROOT);
        AddonCategory cat = new AddonCategory(key, englishName, translatableKey, priority);
        REGISTRY.put(key, cat);
        return cat;
    }

    public static AddonCategory get(String id) {
        if (id == null) return CUSTOM;
        return REGISTRY.getOrDefault(id.toLowerCase(Locale.ROOT), CUSTOM);
    }

    public static Collection<AddonCategory> values() {
        List<AddonCategory> list = new ArrayList<>(REGISTRY.values());
        list.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        return Collections.unmodifiableList(list);
    }

    public String getId() {
        return id;
    }

    public String name() {
        return id.toUpperCase(Locale.ROOT);
    }

    public String getEnglishName() {
        return englishName;
    }

    public String getTranslatableKey() {
        return translatableKey;
    }

    public int getPriority() {
        return priority;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AddonCategory that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return id;
    }
}
