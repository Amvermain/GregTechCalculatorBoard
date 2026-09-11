package com.gtceu.calcboard.api.model;

import com.gtceu.calcboard.api.property.NodePropertyStore;
import com.gtceu.calcboard.api.spi.IModAdapter;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;

/**
 * Helper providing typed property access for hardware parameters: rotor, boiler, fusion, and kinetic settings.
 */
public final class NodeHardwarePropertyHelper {

    private NodeHardwarePropertyHelper() {}

    public static int getRpm(NodePropertyStore properties) {
        int cur = properties.getById("kinetic_rpm", 32);
        return cur > 0 ? cur : 32;
    }

    public static void setRpm(RecipeNode node, NodePropertyStore properties, int rpm) {
        int old = properties.getById("kinetic_rpm", 32);
        int val = Math.max(1, Math.min(256, rpm));
        if (old != val) {
            properties.setById("kinetic_rpm", val);
            node.markOverclockDirty();
        }
    }

    public static int getRotorEfficiency(NodePropertyStore properties) {
        return properties.getById("rotor_efficiency", 100);
    }

    public static void setRotorEfficiency(NodePropertyStore properties, int rotorEfficiency) {
        properties.setById("rotor_efficiency", Math.max(1, Math.min(100000, rotorEfficiency)));
    }

    public static int getRotorPower(NodePropertyStore properties) {
        return properties.getById("rotor_power", 100);
    }

    public static void setRotorPower(NodePropertyStore properties, int rotorPower) {
        properties.setById("rotor_power", Math.max(1, Math.min(1000000, rotorPower)));
    }

    public static String getRotorName(NodePropertyStore properties) {
        return properties.getById("rotor_name", "Standard (100%)");
    }

    public static void setRotorName(NodePropertyStore properties, String rotorName) {
        properties.setById("rotor_name", rotorName != null ? rotorName : "Standard (100%)");
    }

    public static int getRecipeTemperature(NodePropertyStore properties) {
        return properties.getById("ebf_temperature", 0);
    }

    public static void setRecipeTemperature(NodePropertyStore properties, int recipeTemperature) {
        properties.setById("ebf_temperature", Math.max(0, recipeTemperature));
    }

    public static int getBoilerThrottle(NodePropertyStore properties) {
        int t = properties.getById("boiler_throttle", 100);
        return Math.max(25, Math.min(100, t));
    }

    public static void setBoilerThrottle(NodePropertyStore properties, int throttle) {
        properties.setById("boiler_throttle", Math.max(25, Math.min(100, throttle)));
    }

    public static long getEuToStart(NodePropertyStore properties) {
        return properties.getById("fusion_start_eu", 0L);
    }

    public static void setEuToStart(RecipeNode node, NodePropertyStore properties, long euToStart) {
        properties.setById("fusion_start_eu", Math.max(0L, euToStart));
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        node.setTargetTier(adapter.sanitizeTargetTier(node, node.getTargetTier()));
    }
}
