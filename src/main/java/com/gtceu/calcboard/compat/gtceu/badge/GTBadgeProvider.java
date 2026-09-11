package com.gtceu.calcboard.compat.gtceu.badge;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.property.NodeBadge;
import com.gtceu.calcboard.api.property.NodeBadgeRegistry;
import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.api.property.NodePropertyStore;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.util.NumberFormatUtil;
import com.gtceu.calcboard.compat.gtceu.GTCEuProperties;
import com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter;
import com.gtceu.calcboard.compat.gtceu.GTTurbineHelper;
import com.gtceu.calcboard.compat.gtceu.handler.GTAddonCompatibilityHandler;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Registers GTCEu-specific node badges and tooltips (Fusion, Cleanroom, Reflector, Turbine Deficit).
 */
public final class GTBadgeProvider {

    private GTBadgeProvider() {}

    public static void registerAll() {
        NodeBadgeRegistry.register((node, store) -> {
            if (node == null || store == null) return List.of();
            long startEU = store.get(GTCEuProperties.FUSION_START_EU);
            boolean isFusionCat = com.gtceu.calcboard.compat.gtceu.physics.GTFusionHelper.isFusionCategory(node.getRecipeCategoryId());
            if (startEU <= 0 && !isFusionCat && !node.isFusion()) return List.of();

            GTVoltageTier ctrlTier = GTCEuModAdapter.extractVoltageTierFromIcon(node.getMachineIcon());
            GTVoltageTier tier = (ctrlTier != null) ? ctrlTier : node.getTargetTier();
            String mkLabel;
            if (tier == GTVoltageTier.ZPM) mkLabel = "Mk2";
            else if (tier == GTVoltageTier.UV) mkLabel = "Mk3";
            else if (tier == GTVoltageTier.UHV) mkLabel = "AUX I";
            else if (tier == GTVoltageTier.UEV) mkLabel = "Mk4";
            else if (tier == GTVoltageTier.UIV) mkLabel = "AUX II";
            else if (tier == GTVoltageTier.UXV) mkLabel = "Mk5";
            else if (tier == GTVoltageTier.OpV) mkLabel = "AUX III";
            else if (tier == GTVoltageTier.MAX) mkLabel = "Mk6";
            else if (tier == GTVoltageTier.LuV) mkLabel = "Mk1";
            else {
                int fTier = startEU <= 160_000_000L ? 1 : (startEU <= 320_000_000L ? 2 : 3);
                mkLabel = "Mk" + fTier;
            }

            String tierBadgeText = "⚛ " + mkLabel;
            List<Component> tierTooltip = List.of(
                    Component.literal("§d⚛ ").append(Component.translatable("gui.gtcalcboard.badge.fusion_reactor_title", mkLabel)),
                    Component.literal("§7").append(Component.translatable("gui.gtcalcboard.badge.operating_voltage_tier", "§f" + node.getTargetTier().getName()))
            );
            NodeBadge tierBadge = new NodeBadge(tierBadgeText, 0xFFFFFFFF, 0xEE3D1B5E, 0xFFCC44FF, tierTooltip);

            if (startEU > 0) {
                String startText = "⚡ " + NumberFormatUtil.formatCompactNumber(startEU) + " EU";
                List<Component> startTooltip = List.of(
                        Component.literal("§e⚡ ").append(Component.translatable("gui.gtcalcboard.fusion_start_buffer_title")),
                        Component.literal("§7").append(Component.translatable("gui.gtcalcboard.badge.required_ignition_energy", String.format(Locale.ROOT, "§e%,d", startEU))),
                        Component.literal(String.format(Locale.ROOT, "§7Formatted: §f%s EU", NumberFormatUtil.formatCompactNumber(startEU)))
                );
                NodeBadge startBadge = new NodeBadge(startText, 0xFFFFAA00, 0xEE3D2B1E, 0xFFFFAA00, startTooltip);
                return List.of(tierBadge, startBadge);
            }

            return List.of(tierBadge);
        });

        // 2. Cleanroom Badge Provider
        NodeBadgeRegistry.register((node, store) -> {
            if (node == null || store == null) return List.of();
            String cleanroom = store.get(GTCEuProperties.CLEANROOM_TYPE);
            if (cleanroom == null || cleanroom.isEmpty()) return List.of();

            String label = cleanroom.toLowerCase(Locale.ROOT).startsWith("sterile") ? "☣ Sterile" : "★ Cleanroom";
            List<Component> tooltip = List.of(
                    Component.literal("§b★ ").append(Component.translatable("gui.gtcalcboard.badge.cleanroom_required")),
                    Component.literal("§7").append(Component.translatable("gui.gtcalcboard.badge.cleanroom_type", "§f" + cleanroom))
            );
            return List.of(new NodeBadge(label, 0xFF55FFFF, 0xEE1E2D3D, 0xFF55FFFF, tooltip));
        });

        // 3. Fusion Reflector Badge Provider
        NodeBadgeRegistry.register((node, store) -> {
            if (node == null || store == null) return List.of();
            int reqTier = store.get(GTCEuProperties.REQUIRED_REFLECTOR_TIER);
            int instTier = node.getInstalledReflectorTier();
            if (reqTier <= 0 && instTier <= 0) return List.of();

            if (reqTier > 0) {
                if (instTier >= reqTier) {
                    String badgeText = "✦ T" + instTier;
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(Component.literal("§b✦ ").append(Component.translatable("gui.gtcalcboard.reflector_valid_title")));
                    tooltip.add(Component.literal("§7").append(Component.translatable("gui.gtcalcboard.badge.installed_reflector", "§aTier " + instTier)));
                    tooltip.add(Component.literal("§7").append(Component.translatable("gui.gtcalcboard.badge.required_reflector", reqTier)));
                    int boost = instTier - reqTier;
                    if (boost > 0) {
                        int speed = 1 << boost;
                        tooltip.add(Component.literal("§e⚡ ").append(Component.translatable("gui.gtcalcboard.reflector_boost_tooltip", boost, speed)));
                    }
                    tooltip.add(Component.literal("§a✔ ").append(Component.translatable("gui.gtcalcboard.reflector_met")));
                    return List.of(new NodeBadge(badgeText, 0xFF55FFFF, 0xEE1E3D3D, 0xFF55FFFF, tooltip));
                } else {
                    String badgeText = Component.translatable("gui.gtcalcboard.node_badge.reflector_required", reqTier).getString();
                    List<Component> tooltip = List.of(
                            Component.literal("§c⚠ ").append(Component.translatable("gui.gtcalcboard.reflector_missing_title")),
                            Component.literal("§7").append(Component.translatable("gui.gtcalcboard.badge.required_reflector", reqTier)),
                            Component.literal("§7").append(Component.translatable("gui.gtcalcboard.badge.installed_reflector", instTier > 0 ? ("§eTier " + instTier) : Component.translatable("gui.gtcalcboard.reflector.none").getString())),
                            Component.literal("§c❌ ").append(Component.translatable("gui.gtcalcboard.reflector_missing_desc", reqTier))
                    );
                    return List.of(new NodeBadge(badgeText, 0xFFFF5555, 0xEE3D1E1E, 0xFFFF5555, tooltip, true));
                }
            } else if (instTier > 0) {
                String badgeText = "✦ T" + instTier;
                List<Component> tooltip = List.of(
                        Component.literal("§b✦ ").append(Component.translatable("gui.gtcalcboard.reflector_installed_title")),
                        Component.literal("§7").append(Component.translatable("gui.gtcalcboard.badge.installed_reflector", "§bTier " + instTier))
                );
                return List.of(new NodeBadge(badgeText, 0xFF55FFFF, 0xEE1E3D3D, 0xFF55FFFF, tooltip));
            }
            return List.of();
        });

        // 4. Turbine Deficit Badge Provider
        NodeBadgeRegistry.register((node, store) -> {
            if (node == null || !GTTurbineHelper.isTurbine(node)) return List.of();
            FlowGraph graph = BoardManager.getInstance().getActiveGraph();
            if (graph != null && GTTurbineHelper.hasTurbineFlowDeficit(node, graph)) {
                String badgeText = Component.translatable("gui.gtcalcboard.node_badge.turbine_deficit").getString();
                List<Component> tooltip = List.of(
                        Component.literal("§c⚠ " + Component.translatable("gui.gtcalcboard.turbine_deficit_title").getString()),
                        Component.literal("§7" + Component.translatable("gui.gtcalcboard.turbine_deficit_desc").getString()),
                        Component.literal("§c❌ " + Component.translatable("gui.gtcalcboard.node_warning.inactive").getString())
                );
                return List.of(new NodeBadge(badgeText, 0xFFFF5555, 0xEE3D1E1E, 0xFFFF5555, tooltip, true));
            }
            return List.of();
        });

        // 4b. Missing Energy Hatch Badge Provider
        NodeBadgeRegistry.register((node, store) -> {
            if (node == null) return List.of();
            if (GTAddonCompatibilityHandler.requiresEnergyHatch(node) && !GTAddonCompatibilityHandler.hasEnergyHatch(node)) {
                String badgeText = "⚡ ⚠ " + Component.translatable("gui.gtcalcboard.node_badge.energy_hatch_missing").getString();
                List<Component> tooltip = List.of(
                        Component.literal("§c⚠ " + Component.translatable("gui.gtcalcboard.node_warning.inactive").getString()),
                        Component.literal("§c❌ " + Component.translatable("gui.gtcalcboard.node_warning.energy_hatch_missing").getString())
                );
                return List.of(new NodeBadge(badgeText, 0xFFFF5555, 0xEE3D1E1E, 0xFFFF5555, tooltip, true));
            }
            return List.of();
        });

        // 5. Heating Coil Badge Provider
        NodeBadgeRegistry.register((node, store) -> {
            if (node == null || store == null) return List.of();
            int reqTemp = store.get(GTCEuProperties.EBF_TEMPERATURE);
            if (reqTemp <= 0) reqTemp = node.getRecipeTemperature();

            boolean isCoilMb = false;
            if (node.isMultiblock()) {
                if (node.getMachineIcon() != null) {
                    isCoilMb = com.gtceu.calcboard.api.catalog.MultiblockDetector.isCoilMultiblock(node.getMachineIcon());
                } else if (node.getMultiblockWorkstation() != null) {
                    isCoilMb = com.gtceu.calcboard.api.catalog.MultiblockDetector.isCoilMultiblock(node.getMultiblockWorkstation());
                } else {
                    isCoilMb = com.gtceu.calcboard.api.catalog.MultiblockDetector.isCoilRecipeCategory(node.getRecipeCategoryId());
                }
            }
            int instTemp = com.gtceu.calcboard.compat.gtceu.helper.CoilHelper.getInstalledCoilTemperature(node);

            if (reqTemp <= 0 && instTemp <= 0 && !isCoilMb) return List.of();

            if (reqTemp > 0) {
                if (instTemp >= reqTemp) {
                    String badgeText = String.format(Locale.ROOT, "♨ %,dK", instTemp);
                    List<Component> tooltip = List.of(
                            Component.literal("§6♨ ").append(Component.translatable("gui.gtcalcboard.coil_valid_title")),
                            Component.literal("§7").append(Component.translatable("gui.gtcalcboard.badge.installed_coil", String.format(Locale.ROOT, "§a%,d", instTemp))),
                            Component.literal("§7").append(Component.translatable("gui.gtcalcboard.badge.required_temp", String.format(Locale.ROOT, "§f%,d", reqTemp))),
                            Component.literal("§a✔ ").append(Component.translatable("gui.gtcalcboard.coil_met"))
                    );
                    return List.of(new NodeBadge(badgeText, 0xFFFFAA00, 0xEE3D2E1E, 0xFFFFAA00, tooltip));
                } else {
                    String badgeText = String.format(Locale.ROOT, "♨ ⚠ %,dK", instTemp);
                    List<Component> tooltip = List.of(
                            Component.literal("§c⚠ ").append(Component.translatable("gui.gtcalcboard.coil_missing_title")),
                            Component.literal("§7").append(Component.translatable("gui.gtcalcboard.badge.required_temp", String.format(Locale.ROOT, "§c%,d", reqTemp))),
                            Component.literal("§7").append(Component.translatable("gui.gtcalcboard.badge.installed_coil", String.format(Locale.ROOT, "§e%,d", instTemp))),
                            Component.literal("§c❌ ").append(Component.translatable("gui.gtcalcboard.coil_missing_desc", reqTemp))
                    );
                    return List.of(new NodeBadge(badgeText, 0xFFFF5555, 0xEE3D1E1E, 0xFFFF5555, tooltip, true));
                }
            } else if (instTemp > 0 && isCoilMb) {
                String badgeText = String.format(Locale.ROOT, "♨ %,dK", instTemp);
                List<Component> tooltip = List.of(
                        Component.literal("§6♨ ").append(Component.translatable("gui.gtcalcboard.coil_installed_title")),
                        Component.literal("§7").append(Component.translatable("gui.gtcalcboard.badge.installed_coil", String.format(Locale.ROOT, "§e%,d", instTemp)))
                );
                return List.of(new NodeBadge(badgeText, 0xFFFFAA00, 0xEE3D2E1E, 0xFFFFAA00, tooltip));
            }
            return List.of();
        });

        // 6. Combustion Engine Boost Badge Provider
        NodeBadgeRegistry.register((node, store) -> {
            if (node == null || store == null) return List.of();
            if (!com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper.isCombustionEngine(node)) return List.of();

            List<NodeBadge> badges = new ArrayList<>();
            if (com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper.isOxygenBoosted(node)) {
                List<Component> tt = List.of(
                        Component.literal("§b💨 ").append(Component.translatable("gui.gtcalcboard.addon.oxygen_boost")),
                        Component.literal("§7").append(Component.translatable("gui.gtcalcboard.addon.oxygen_boost.desc")),
                        Component.literal("§a✔ ").append(Component.translatable("gui.gtcalcboard.node_badge.oxygen_boost"))
                );
                badges.add(new NodeBadge("💨 Boost (3.0x)", 0xFF55FFAA, 0xEE1E3D2D, 0xFF55FFAA, tt));
            } else if (com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper.isLiquidOxygenBoosted(node)) {
                List<Component> tt = List.of(
                        Component.literal("§b💨 ").append(Component.translatable("gui.gtcalcboard.addon.liquid_oxygen_boost")),
                        Component.literal("§7").append(Component.translatable("gui.gtcalcboard.addon.liquid_oxygen_boost.desc")),
                        Component.literal("§a✔ ").append(Component.translatable("gui.gtcalcboard.node_badge.liquid_oxygen_boost"))
                );
                badges.add(new NodeBadge("💨 Boost (4.0x)", 0xFF55FFAA, 0xEE1E3D2D, 0xFF55FFAA, tt));
            }

            if (com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper.isOxidizerBoosted(node)) {
                String ox = store.get(GTCEuProperties.COMBUSTION_OXIDIZER_TYPE);
                String oxName = com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper.getOxidizerDisplayName(ox);
                List<Component> tt = List.of(
                        Component.literal("§b💨 ").append(Component.translatable("gui.gtcalcboard.tooltip.oxidizer_boost")),
                        Component.literal("§7").append(Component.translatable("gui.gtcalcboard.badge.oxidizer_label", "§f" + oxName)),
                        Component.literal("§a✔ ").append(Component.translatable("gui.gtcalcboard.badge.oxidizer_boost_active"))
                );
                badges.add(new NodeBadge("💨 " + oxName, 0xFF55FFAA, 0xEE1E3D2D, 0xFF55FFAA, tt));
            }

            if (com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper.isCoolantBoosted(node)) {
                String cl = store.get(GTCEuProperties.COMBUSTION_COOLANT_TYPE);
                String clName = com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper.getCoolantDisplayName(cl);
                List<Component> tt = List.of(
                        Component.literal("§b❄ ").append(Component.translatable("gui.gtcalcboard.tooltip.coolant_boost")),
                        Component.literal("§7").append(Component.translatable("gui.gtcalcboard.badge.coolant_label", "§f" + clName))
                );
                badges.add(new NodeBadge("❄ " + clName, 0xFF58D3FF, 0xEE1E2E3D, 0xFF58D3FF, tt));
            }

            return badges;
        });
    }
}
