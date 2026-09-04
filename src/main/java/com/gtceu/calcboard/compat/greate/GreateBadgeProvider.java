package com.gtceu.calcboard.compat.greate;

import com.gtceu.calcboard.api.property.NodeBadge;
import com.gtceu.calcboard.api.property.NodeBadgeRegistry;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers Greate-specific node badges (Heat Condition, Programmed Circuit, and Machine Tier Deficit).
 */
public final class GreateBadgeProvider {

    private GreateBadgeProvider() {}

    public static void registerAll() {
        NodeBadgeRegistry.register((node, store) -> {
            if (node == null || store == null) return List.of();
            if (!store.get(GreateProperties.IS_GREATE) && !store.has(GreateProperties.REQUIRED_RECIPE_TIER)) {
                return List.of();
            }

            List<NodeBadge> badges = new ArrayList<>();

            int machineTier = Math.max(0, store.get(GreateProperties.MACHINE_TIER));
            int reqTier = Math.max(0, store.get(GreateProperties.REQUIRED_RECIPE_TIER));
            if (reqTier > 0 && machineTier < reqTier) {
                String label = "⚠ " + GreateProperties.getTierName(reqTier) + " Req";
                List<Component> tooltip = List.of(
                        Component.translatable("gui.gtcalcboard.greate.tier_deficit_title"),
                        Component.translatable("gui.gtcalcboard.greate.tier_deficit_desc",
                                GreateProperties.getTierName(machineTier),
                                GreateProperties.getTierName(reqTier))
                );
                badges.add(new NodeBadge(label, 0xFFFF4444, 0xEE3D1A1A, 0xFFFF4444, tooltip));
            }

            String heat = store.get(GreateProperties.HEAT_CONDITION);
            if (heat != null && !heat.isEmpty() && !"NONE".equalsIgnoreCase(heat)) {
                if ("SUPERHEATED".equalsIgnoreCase(heat)) {
                    List<Component> tooltip = List.of(
                            Component.translatable("gui.gtcalcboard.greate.heat_superheated"),
                            Component.translatable("gui.gtcalcboard.greate.heat_superheated_desc")
                    );
                    badges.add(new NodeBadge("♨ Superheated", 0xFF00FFFF, 0xEE1E2D3D, 0xFF00FFFF, tooltip));
                } else {
                    List<Component> tooltip = List.of(
                            Component.translatable("gui.gtcalcboard.greate.heat_heated"),
                            Component.translatable("gui.gtcalcboard.greate.heat_heated_desc")
                    );
                    badges.add(new NodeBadge("♨ Heated", 0xFFFFA500, 0xEE3D2B1E, 0xFFFFA500, tooltip));
                }
            }

            int circuit = store.get(GreateProperties.CIRCUIT_NUMBER);
            if (circuit >= 0) {
                String label = "⚙ [#" + circuit + "]";
                List<Component> tooltip = List.of(
                        Component.translatable("gui.gtcalcboard.greate.circuit_required", circuit),
                        Component.translatable("gui.gtcalcboard.greate.circuit_required_desc", circuit)
                );
                badges.add(new NodeBadge(label, 0xFFE0E0E0, 0xEE2A2A2A, 0xFFAAAAAA, tooltip));
            }

            return badges;
        });
    }
}
