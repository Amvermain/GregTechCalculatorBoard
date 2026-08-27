package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.*;
import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.gtceu.addon.GTReflectorAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTRotorAddon;
import com.gtceu.calcboard.compat.gtceu.helper.CoilHelper;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mod Adapter facade for GregTech CEu Modern (GTCEu).
 * Manages GTCEu hardware addons (coils, rotors, reflectors, parallel hatches), recipes, and dialog controls.
 */
public class GTCEuModAdapter implements IModAdapter {

    static {
        com.gtceu.calcboard.api.AddonFactoryRegistry.register(com.gtceu.calcboard.api.AddonCategory.COIL, (id, name, desc, icon, tag) -> new com.gtceu.calcboard.compat.gtceu.addon.GTCoilAddon(id, name, desc, icon));
        com.gtceu.calcboard.api.AddonFactoryRegistry.register(com.gtceu.calcboard.api.AddonCategory.ROTOR, (id, name, desc, icon, tag) -> new com.gtceu.calcboard.compat.gtceu.addon.GTRotorAddon(id, name, desc, icon));
        com.gtceu.calcboard.api.AddonFactoryRegistry.register(com.gtceu.calcboard.api.AddonCategory.REFLECTOR, (id, name, desc, icon, tag) -> new com.gtceu.calcboard.compat.gtceu.addon.GTReflectorAddon(id, name, desc, icon));
        com.gtceu.calcboard.api.AddonFactoryRegistry.register(com.gtceu.calcboard.api.AddonCategory.PARALLEL, (id, name, desc, icon, tag) -> new com.gtceu.calcboard.compat.gtceu.addon.GTParallelHatchAddon(id, name, desc, icon));
        com.gtceu.calcboard.api.AddonFactoryRegistry.register(com.gtceu.calcboard.api.AddonCategory.ENERGY_HATCH, (id, name, desc, icon, tag) -> new com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon(id, name, desc, icon));
        com.gtceu.calcboard.api.AddonFactoryRegistry.register(com.gtceu.calcboard.api.AddonCategory.HATCH_BUS, (id, name, desc, icon, tag) -> new com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon(id, name, desc, icon));

        // 1. Fusion Reactor Badges Provider (RFC-001 & RFC-002)
        com.gtceu.calcboard.api.property.NodeBadgeRegistry.register((node, store) -> {
            if (node == null || store == null) return java.util.List.of();
            long startEU = store.get(com.gtceu.calcboard.api.property.NodeProperties.FUSION_START_EU);
            boolean isFusionCat = node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("fusion_reactor");
            if (startEU <= 0 && !isFusionCat) return java.util.List.of();

            int fTier = startEU <= 160_000_000L ? 1 : (startEU <= 320_000_000L ? 2 : 3);
            String tierBadgeText = "⚛ Mk" + fTier;
            java.util.List<net.minecraft.network.chat.Component> tierTooltip = java.util.List.of(
                    net.minecraft.network.chat.Component.literal(String.format(java.util.Locale.ROOT, "§d⚛ Fusion Reactor Mk%d", fTier)),
                    net.minecraft.network.chat.Component.literal(String.format(java.util.Locale.ROOT, "§7Min Voltage Tier: §f%s", fTier == 1 ? "LuV" : (fTier == 2 ? "ZPM" : "UV")))
            );
            com.gtceu.calcboard.api.property.NodeBadge tierBadge = new com.gtceu.calcboard.api.property.NodeBadge(tierBadgeText, 0xFFFFFFFF, 0xEE3D1B5E, 0xFFCC44FF, tierTooltip);

            if (startEU > 0) {
                String startText = "⚡ " + com.gtceu.calcboard.client.gui.FormatUtil.formatCompactNumber(startEU) + " EU";
                java.util.List<net.minecraft.network.chat.Component> startTooltip = java.util.List.of(
                        net.minecraft.network.chat.Component.literal("§e⚡ " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.fusion_start_buffer_title").getString()),
                        net.minecraft.network.chat.Component.literal(String.format(java.util.Locale.ROOT, "§7Required Ignition Energy: §e%,d EU", startEU)),
                        net.minecraft.network.chat.Component.literal(String.format(java.util.Locale.ROOT, "§7Formatted: §f%s EU", com.gtceu.calcboard.client.gui.FormatUtil.formatCompactNumber(startEU)))
                );
                com.gtceu.calcboard.api.property.NodeBadge startBadge = new com.gtceu.calcboard.api.property.NodeBadge(startText, 0xFFFFAA00, 0xEE3D2B1E, 0xFFFFAA00, startTooltip);
                return java.util.List.of(tierBadge, startBadge);
            }

            return java.util.List.of(tierBadge);
        });

        // 2. Cleanroom Badge Provider
        com.gtceu.calcboard.api.property.NodeBadgeRegistry.register((node, store) -> {
            if (node == null || store == null) return java.util.List.of();
            String cleanroom = store.get(com.gtceu.calcboard.api.property.NodeProperties.CLEANROOM_TYPE);
            if (cleanroom == null || cleanroom.isEmpty()) return java.util.List.of();

            String label = cleanroom.toLowerCase(java.util.Locale.ROOT).contains("sterile") ? "☣ Sterile" : "🧹 Cleanroom";
            java.util.List<net.minecraft.network.chat.Component> tooltip = java.util.List.of(
                    net.minecraft.network.chat.Component.literal("§b🧹 Cleanroom Required"),
                    net.minecraft.network.chat.Component.literal("§7Type: §f" + cleanroom)
            );
            return java.util.List.of(new com.gtceu.calcboard.api.property.NodeBadge(label, 0xFF55FFFF, 0xEE1E2D3D, 0xFF55FFFF, tooltip));
        });

        // 3. Fusion Reflector Badge Provider
        com.gtceu.calcboard.api.property.NodeBadgeRegistry.register((node, store) -> {
            if (node == null || store == null) return java.util.List.of();
            int reqTier = store.get(com.gtceu.calcboard.api.property.NodeProperties.REQUIRED_REFLECTOR_TIER);
            int instTier = node.getInstalledReflectorTier();
            if (reqTier <= 0 && instTier <= 0) return java.util.List.of();

            if (reqTier > 0) {
                if (instTier >= reqTier) {
                    String badgeText = "🪞 T" + instTier;
                    java.util.List<net.minecraft.network.chat.Component> tooltip = java.util.List.of(
                            net.minecraft.network.chat.Component.literal("§b🪞 " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.reflector_valid_title").getString()),
                            net.minecraft.network.chat.Component.literal(String.format(java.util.Locale.ROOT, "§7Installed Reflector: §aTier %d", instTier)),
                            net.minecraft.network.chat.Component.literal(String.format(java.util.Locale.ROOT, "§7Required Reflector: §fTier %d", reqTier)),
                            net.minecraft.network.chat.Component.literal("§a✔ " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.reflector_met").getString())
                    );
                    return java.util.List.of(new com.gtceu.calcboard.api.property.NodeBadge(badgeText, 0xFF55FFFF, 0xEE1E3D3D, 0xFF55FFFF, tooltip));
                } else {
                    String badgeText = net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.reflector_required", reqTier).getString();
                    java.util.List<net.minecraft.network.chat.Component> tooltip = java.util.List.of(
                            net.minecraft.network.chat.Component.literal("§c⚠ " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.reflector_missing_title").getString()),
                            net.minecraft.network.chat.Component.literal(String.format(java.util.Locale.ROOT, "§7Required Reflector: §cTier %d", reqTier)),
                            net.minecraft.network.chat.Component.literal(String.format(java.util.Locale.ROOT, "§7Installed Reflector: §e%s", instTier > 0 ? ("Tier " + instTier) : net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.none").getString())),
                            net.minecraft.network.chat.Component.literal("§c❌ " + String.format(java.util.Locale.ROOT, net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.reflector_missing_desc").getString(), reqTier))
                    );
                    return java.util.List.of(new com.gtceu.calcboard.api.property.NodeBadge(badgeText, 0xFFFF5555, 0xEE3D1E1E, 0xFFFF5555, tooltip, true));
                }
            } else if (instTier > 0) {
                String badgeText = "🪞 T" + instTier;
                java.util.List<net.minecraft.network.chat.Component> tooltip = java.util.List.of(
                        net.minecraft.network.chat.Component.literal("§b🪞 " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.reflector_installed_title").getString()),
                        net.minecraft.network.chat.Component.literal(String.format(java.util.Locale.ROOT, "§7Installed Reflector: §bTier %d", instTier))
                );
                return java.util.List.of(new com.gtceu.calcboard.api.property.NodeBadge(badgeText, 0xFF55FFFF, 0xEE1E3D3D, 0xFF55FFFF, tooltip));
            }
            return java.util.List.of();
        });

        // 4. Turbine Deficit Badge Provider
        com.gtceu.calcboard.api.property.NodeBadgeRegistry.register((node, store) -> {
            if (node == null || !GTTurbineHelper.isTurbine(node)) return java.util.List.of();
            com.gtceu.calcboard.api.FlowGraph graph = com.gtceu.calcboard.api.BoardManager.getInstance().getActiveGraph();
            if (graph != null && GTTurbineHelper.hasTurbineFlowDeficit(node, graph)) {
                String badgeText = net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.turbine_deficit").getString();
                java.util.List<net.minecraft.network.chat.Component> tooltip = java.util.List.of(
                        net.minecraft.network.chat.Component.literal("§c⚠ " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.turbine_deficit_title").getString()),
                        net.minecraft.network.chat.Component.literal("§7" + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.turbine_deficit_desc").getString()),
                        net.minecraft.network.chat.Component.literal("§c❌ " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_warning.inactive").getString())
                );
                return java.util.List.of(new com.gtceu.calcboard.api.property.NodeBadge(badgeText, 0xFFFF5555, 0xEE3D1E1E, 0xFFFF5555, tooltip, true));
            }
            return java.util.List.of();
        });
    }

    @Override
    public String getModId() {
        return "gtceu";
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public boolean isLoaded() {
        try {
            Class<?> mlClass = Class.forName("net.minecraftforge.fml.ModList");
            Object ml = mlClass.getMethod("get").invoke(null);
            if (ml != null) {
                return (boolean) mlClass.getMethod("isLoaded", String.class).invoke(ml, "gtceu");
            }
        } catch (Throwable ignored) {}
        return true;
    }

    @Override
    public boolean handlesCategory(ResourceLocation categoryId) {
        if (categoryId == null) return false;
        String ns = categoryId.getNamespace().toLowerCase(Locale.ROOT);
        return ns.equals("gtceu") || ns.equals("start_core") || ns.equals("gtceu_start") || ns.equals("start") || ns.equals("star_technology");
    }

    @Override
    public boolean handlesNode(RecipeNode node) {
        if (node == null) return false;
        if (node.getEnergyTypeOverride() == com.gtceu.calcboard.api.EnergyType.KINETIC_SU) return false;

        if (node.getMachineIcon() != null) {
            String ns = node.getMachineIcon().getNamespace().toLowerCase(Locale.ROOT);
            if (ns.equals("minecraft") || ns.equals("emi")) {
                return false;
            }
            if (ns.equals("gtceu") || ns.equals("start_core") || ns.equals("gtceu_start") || ns.equals("start") || ns.equals("star_technology")) {
                return true;
            }
        }
        if (node.getRecipeCategoryId() != null) {
            String ns = node.getRecipeCategoryId().getNamespace().toLowerCase(Locale.ROOT);
            if (ns.equals("minecraft") || ns.equals("emi")) {
                return false;
            }
            if (ns.equals("gtceu") || ns.equals("start_core") || ns.equals("gtceu_start") || ns.equals("start") || ns.equals("star_technology")) {
                return true;
            }
        }
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (ws != null) {
                String ns = ws.getNamespace().toLowerCase(Locale.ROOT);
                if (ns.equals("gtceu") || ns.equals("start_core") || ns.equals("gtceu_start") || ns.equals("start") || ns.equals("star_technology")) {
                    return true;
                }
            }
        }
        if (node.getEnergyTypeOverride() == EnergyType.ELECTRIC_EU || (node.getEnergyTypeOverride() == null && node.getBaseEUt() > 0 && (node.getMachineIcon() == null || !node.getMachineIcon().getNamespace().equals("minecraft")))) {
            return true;
        }
        return false;
    }

    @Override
    public void discoverAddons(List<MachineAddon> collector, List<ItemStack> recipeOutputStacks) {
        GTCEuAddonCrawler.discoverAddons(collector, recipeOutputStacks);
    }

    @Override
    public void enrichCapabilities(CategoryCapabilityMatrix matrix, Object emiRecipeManager) {
        com.gtceu.calcboard.compat.gtceu.helper.GTCEuCapabilityScanner.enrichCapabilities(matrix, emiRecipeManager);
    }

    @Override
    public void scanMultiblocks(Object emiRecipeManager) {
        com.gtceu.calcboard.compat.gtceu.helper.GTCEuMultiblockScanner.scan(emiRecipeManager);
    }

    @Override
    public void scanMultiblockStructures() {
        com.gtceu.calcboard.compat.gtceu.helper.GTCEuMultiblockStructureScanner.scan();
    }

    @Override
    public com.gtceu.calcboard.api.bom.MultiblockStructureDef scanMultiblockStructure(ResourceLocation machineId) {
        return com.gtceu.calcboard.compat.gtceu.helper.GTCEuMultiblockStructureScanner.scanSingle(machineId);
    }

    @Override
    public boolean isTurbine(RecipeNode node) {
        return GTTurbineHelper.isTurbine(node);
    }

    @Override
    public boolean isLargeTurbine(RecipeNode node) {
        return GTTurbineHelper.isLargeTurbine(node);
    }

    @Override
    public boolean isGenerator(RecipeNode node) {
        if (node == null) return false;
        return node.isGenerator() || GTTurbineHelper.isTurbine(node) || node.getBaseEUt() < 0;
    }

    @Override
    public double getGeneratorMaxPower(RecipeNode node) {
        return GTTurbineHelper.getGeneratorMaxEUt(node);
    }

    @Override
    public double computeEffectiveOutputChance(RecipeNode node, int outputIndex, double defaultChance) {
        if (node == null || outputIndex < 0 || outputIndex >= node.getOutputs().size()) return defaultChance;
        IngredientStack out = node.getOutputs().get(outputIndex);
        if (out.getChance() >= 1.0) return 1.0;

        // 1. Steam Ore Factory multiblock produces full outputs and byproducts
        if (node.isMultiblock() && node.getMachineIcon() != null && node.getMachineIcon().getPath().contains("steam_ore_factory")) {
            return out.getEffectiveChance(node.getTierDelta());
        }

        // 2. Standard Steam machines in GTCEu cannot produce byproducts
        if (node.getSteamMode() != null && node.getSteamMode().isSteam()) {
            return 0.0;
        }

        // 3. GTCEu Macerator ore byproduct tier gating:
        if (outputIndex >= 1 && out.getChance() > 0.0 && node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("macerator")) {
            GTVoltageTier curTier = node.getTargetTier();
            if (curTier == null) curTier = GTVoltageTier.LV;
            int curTierIdx = curTier.ordinal();

            GTVoltageTier reqTier;
            if (outputIndex == 1) reqTier = GTVoltageTier.HV;
            else if (outputIndex == 2) reqTier = GTVoltageTier.EV;
            else reqTier = GTVoltageTier.IV;

            if (curTierIdx < reqTier.ordinal()) {
                return 0.0;
            }

            int extraTiers = curTierIdx - reqTier.ordinal();
            double boost = out.getTierChanceBoost();
            return Math.min(1.0, Math.max(0.0, out.getChance() + extraTiers * boost));
        }

        return defaultChance;
    }

    @Override
    public boolean supportsAddons(RecipeNode node) {
        if (node == null || node.getEnergyType() == EnergyType.NONE) return false;
        if (isBoilerRecipe(node)) {
            return node.isMultiblock();
        }
        if (node.isTurbine()) {
            return node.isMultiblock();
        }
        return node.isMultiblock() || node.hasMultiblockOption() || node.canUseCoils() || node.isFusion() || node.getRequiredReflectorTier() > 0;
    }

    @Override
    public List<AddonCategory> getApplicableAddonCategories(RecipeNode node) {
        if (node == null) return List.of();

        if (isBoilerRecipe(node)) {
            if (node.isMultiblock()) {
                List<AddonCategory> cats = new ArrayList<>();
                cats.add(AddonCategory.MAINTENANCE);
                cats.add(AddonCategory.HATCH_BUS);
                cats.add(AddonCategory.CUSTOM);
                return cats;
            }
            return List.of();
        }

        boolean isTurbine = node.isTurbine();
        boolean isFusion = node.isFusion() || node.getRequiredReflectorTier() > 0
                || (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("fusion"))
                || (node.getMachineIcon() != null && node.getMachineIcon().getPath().contains("fusion"))
                || (node.getName() != null && node.getName().toLowerCase().contains("fusion"));
        boolean isMb = node.isMultiblock() || node.hasMultiblockOption() || isFusion;
        boolean isCoil = node.canUseCoils();

        if (isTurbine) {
            if (node.isMultiblock()) {
                List<AddonCategory> cats = new ArrayList<>();
                cats.add(AddonCategory.ROTOR);
                cats.add(AddonCategory.MAINTENANCE);
                if (com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel.isPlasmaTurbine(node)) {
                    cats.add(AddonCategory.MULTIBLOCK_TRAIT);
                }
                cats.add(AddonCategory.CUSTOM);
                return cats;
            }
            return List.of();
        }

        if (isFusion) {
            List<AddonCategory> cats = new ArrayList<>();
            cats.add(AddonCategory.REFLECTOR);
            cats.add(AddonCategory.ENERGY_HATCH);
            cats.add(AddonCategory.PARALLEL);
            cats.add(AddonCategory.MAINTENANCE);
            cats.add(AddonCategory.CUSTOM);
            return cats;
        }

        if (node.getRecipeCategoryId() != null) {
            CategoryCapability cap = CategoryCapabilityMatrix.getInstance().getCapability(node.getRecipeCategoryId());
            if (cap != null && cap != CategoryCapability.DEFAULT) {
                return cap.getActiveCategoriesForNode(node);
            }
        }

        if (isMb) {
            ResourceLocation mbId = node.getMachineIcon() != null ? node.getMachineIcon() : node.getMultiblockWorkstation();
            var def = mbId != null ? com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.getStructure(mbId) : null;
            boolean isSteamMb = (node.getSteamMode() != null && node.getSteamMode().isSteam()) || (mbId != null && MultiblockDetector.isSteamMultiblock(mbId));

            List<AddonCategory> cats = new ArrayList<>();
            if (!isSteamMb && !node.isGenerator() && node.getEnergyType() != EnergyType.NONE && node.getEnergyType() != EnergyType.KINETIC_SU
                    && (def == null || def.supportsAbility("INPUT_ENERGY") || def.supportsAbility("SUBSTATION_INPUT_ENERGY") || def.supportsAbility("INPUT_LASER") || def.energyHatchSlotCount() > 0 || def.allowedAbilities().isEmpty() || node.getEnergyType() != EnergyType.NONE)) {
                cats.add(AddonCategory.ENERGY_HATCH);
            }
            if (def == null || !def.allowedAbilities().isEmpty() || def.inputBusSlotCount() > 0 || def.outputBusSlotCount() > 0 || def.inputHatchSlotCount() > 0 || def.outputHatchSlotCount() > 0 || !node.getInputs().isEmpty() || !node.getOutputs().isEmpty() || isMb) {
                cats.add(AddonCategory.HATCH_BUS);
            }
            boolean supportsCoil = false;
            if (def != null) {
                supportsCoil = def.coilSlotCount() > 0 || def.supportsAbility("HEATING_COILS") || MultiblockDetector.isCoilMultiblock(mbId);
            } else {
                supportsCoil = node.canUseCoils() || MultiblockDetector.isCoilMultiblock(mbId);
            }
            if (supportsCoil) {
                cats.add(AddonCategory.COIL);
            }
            if (!isSteamMb && (MultiblockDetector.supportsParallelHatch(node.getMachineIcon(), node.getAvailableWorkstations()) || (def != null && def.supportsAbility("PARALLEL_HATCH")))) {
                cats.add(AddonCategory.PARALLEL);
            }
            if (!isSteamMb && (def == null || def.supportsAbility("MAINTENANCE") || def.maintenanceSlotCount() > 0 || node.getEnergyType() != EnergyType.NONE)) {
                cats.add(AddonCategory.MAINTENANCE);
            }
            if (node.hasThreading()) {
                cats.add(AddonCategory.THREADING);
            }
            cats.add(AddonCategory.MULTIBLOCK_TRAIT);
            cats.add(AddonCategory.CUSTOM);
            return cats;
        }

        List<AddonCategory> cats = new ArrayList<>();
        if (node.hasThreading()) {
            cats.add(AddonCategory.THREADING);
        }
        cats.add(AddonCategory.CUSTOM);
        return cats;
    }

    @Override
    public boolean isAddonCompatible(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return false;
        if (addon.getCategory().equals(AddonCategory.CUSTOM)) return true;

        if (isBoilerRecipe(node)) {
            if (!node.isMultiblock()) return false;
            return addon.getCategory() == AddonCategory.MAINTENANCE || addon.getCategory() == AddonCategory.HATCH_BUS;
        }

        if (node.isTurbine()) {
            if (!node.isMultiblock()) return false;
            if (addon.getCategory() == AddonCategory.ROTOR || addon.getCategory() == AddonCategory.MAINTENANCE || addon.getCategory() == AddonCategory.HATCH_BUS) {
                return true;
            }
            if (addon.getCategory() == AddonCategory.MULTIBLOCK_TRAIT && com.gtceu.calcboard.compat.start.StarTTurbineHelper.isStarTTrait(addon)) {
                return com.gtceu.calcboard.compat.start.StarTTurbineHelper.isCompatibleStarTTrait(node, addon);
            }
            return false;
        }

        List<AddonCategory> applicable = getApplicableAddonCategories(node);
        if (!applicable.contains(addon.getCategory())) {
            return false;
        }

        boolean isGen = node.isGenerator();
        boolean isFusion = node.isFusion() || node.getRequiredReflectorTier() > 0
                || (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("fusion"))
                || (node.getMachineIcon() != null && node.getMachineIcon().getPath().contains("fusion"))
                || (node.getName() != null && node.getName().toLowerCase().contains("fusion"));

        if (addon.getCategory() == MachineAddon.Category.ROTOR) {
            return node.isTurbine() && node.isMultiblock();
        }
        if (addon.getCategory() == MachineAddon.Category.REFLECTOR) {
            return isFusion;
        }
        if (addon.getCategory() == MachineAddon.Category.COIL) {
            if (isGen || !node.canUseCoils() || !node.isMultiblock()) return false;
            ResourceLocation mbId = node.getMachineIcon();
            if (mbId == null) {
                mbId = node.getMultiblockWorkstation();
            }
            if (mbId != null) {
                var def = com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.getStructure(mbId);
                if (def != null && def.coilSlotCount() == 0) return false;
            }
            return true;
        }
        if (addon.getCategory() == MachineAddon.Category.PARALLEL) {
            return node.isMultiblock() && MultiblockDetector.supportsParallelHatch(node.getMachineIcon(), node.getAvailableWorkstations());
        }
        if (addon.getCategory() == MachineAddon.Category.MAINTENANCE) {
            if (!node.isMultiblock()) return false;
            ResourceLocation mbId = node.getMachineIcon();
            if (mbId == null) {
                mbId = node.getMultiblockWorkstation();
            }
            if (mbId != null) {
                var def = com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.getStructure(mbId);
                if (def != null) {
                    if (isMufflerAddon(addon)) {
                        boolean hasMuffler = def.parts().stream().anyMatch(p -> p != null && p.itemId() != null && p.itemId().getPath().contains("muffler"));
                        if (!hasMuffler) return false;
                    } else {
                        if (def.maintenanceSlotCount() == 0 && !def.supportsAbility("MAINTENANCE")) return false;
                    }
                }
            }
            return true;
        }
        if (addon.getCategory() == MachineAddon.Category.ENERGY_HATCH) {
            if (!node.isMultiblock() || isGen) return false;
            ResourceLocation mbId = node.getMachineIcon();
            if (mbId == null) {
                mbId = node.getMultiblockWorkstation();
            }
            if (mbId != null) {
                var def = com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.getStructure(mbId);
                if (def != null) {
                    if (!def.allowedAbilities().isEmpty() && !def.supportsAbility("INPUT_ENERGY") && !def.supportsAbility("INPUT_LASER") && !def.supportsAbility("SUBSTATION_INPUT_ENERGY")) {
                        return false;
                    }
                    if (def.energyHatchSlotCount() == 0 && (MultiblockDetector.isSteamMultiblock(mbId) || isGen)) return false;
                }
            }
            return true;
        }
        if (addon.getCategory() == MachineAddon.Category.HATCH_BUS) {
            if (!node.isMultiblock()) return false;
            ResourceLocation mbId = node.getMachineIcon();
            if (mbId == null) {
                mbId = node.getMultiblockWorkstation();
            }
            if (mbId != null) {
                var def = com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.getStructure(mbId);
                if (def != null) {
                    // 1. Candidate block direct match
                    if (addon.getItemIcon() != null && def.isCandidateBlock(addon.getItemIcon())) {
                        return true;
                    }

                    // 2. PartAbility matching
                    if (def.allowedAbilities() != null && !def.allowedAbilities().isEmpty()) {
                        if (addon instanceof com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon gh && !gh.getAbilities().isEmpty()) {
                            for (String reqAbility : gh.getAbilities()) {
                                if (!def.supportsAbility(reqAbility)) {
                                    return false;
                                }
                            }
                        } else {
                            var type = resolveHatchType(addon);
                            switch (type) {
                                case ITEM_INPUT -> {
                                    if (!def.supportsAbility("IMPORT_ITEMS") && !def.supportsAbility("STEAM_IMPORT_ITEMS")) return false;
                                }
                                case ITEM_OUTPUT -> {
                                    if (!def.supportsAbility("EXPORT_ITEMS") && !def.supportsAbility("STEAM_EXPORT_ITEMS")) return false;
                                }
                                case FLUID_INPUT -> {
                                    if (!def.supportsAbility("IMPORT_FLUIDS") && !def.supportsAbility("STEAM_IMPORT_FLUIDS")) return false;
                                }
                                case FLUID_OUTPUT -> {
                                    if (!def.supportsAbility("EXPORT_FLUIDS") && !def.supportsAbility("STEAM_EXPORT_FLUIDS")) return false;
                                }
                                case DUAL_INPUT -> {
                                    if (!def.supportsAbility("IMPORT_ITEMS") && !def.supportsAbility("IMPORT_FLUIDS")) return false;
                                }
                                case DUAL_OUTPUT -> {
                                    if (!def.supportsAbility("EXPORT_ITEMS") && !def.supportsAbility("EXPORT_FLUIDS")) return false;
                                }
                                default -> {}
                            }
                        }
                    }
                }
            }
            if (isDistillationTower(node)) {
                if (addon instanceof com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon h) {
                    if ((h.getHatchType() == com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon.HatchType.FLUID_OUTPUT || h.getHatchType() == com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon.HatchType.DUAL_OUTPUT) && h.getSlotCapacity() > 1) {
                        return false;
                    }
                } else {
                    String path = addon.getId().toLowerCase(Locale.ROOT);
                    if ((path.contains("4x") || path.contains("9x") || path.contains("16x") || path.contains("quadruple") || path.contains("nonuple") || path.contains("hexadecimal") || path.contains("multi_fluid")) && (path.contains("output") || path.contains("export"))) {
                        return false;
                    }
                }
            }
            return true;
        }
        if (addon.getCategory().equals(AddonCategory.THREADING)) {
            return node.hasThreading();
        }
        if (addon.getCategory() == MachineAddon.Category.MULTIBLOCK_TRAIT) {
            if (com.gtceu.calcboard.compat.start.StarTTurbineHelper.isStarTTrait(addon)) {
                return node.isTurbine() && com.gtceu.calcboard.compat.start.StarTTurbineHelper.isCompatibleStarTTrait(node, addon);
            }
            if (node.isTurbine()) return false;
            if (addon.getId().equals("gtceu:batch_processing")) {
                return !isGen && node.isMultiblock() && MultiblockDetector.supportsBatchMode(node.getMachineIcon(), node.getAvailableWorkstations());
            }
            if (addon.getItemIcon() != null) {
                ResourceLocation target = addon.getItemIcon();
                if (node.getMachineIcon() != null && node.getMachineIcon().equals(target)) return true;
                if (node.getAvailableWorkstations().contains(target)) return true;
                if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().equals(target)) return true;
                return false;
            }
            return !isGen && node.isMultiblock();
        }

        return true;
    }

    public static boolean isDistillationTower(RecipeNode node) {
        if (node == null) return false;
        if (node.getMachineIcon() != null && node.getMachineIcon().getPath().contains("distillation_tower")) return true;
        if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("distillation_tower")) return true;
        return false;
    }

    @Override
    public boolean canInstallAddon(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return false;
        if (addon.getCategory() == AddonCategory.CUSTOM || addon.getCategory() == AddonCategory.THERMAL_AUGMENT) {
            return true;
        }
        if (isDistillationTower(node)) {
            if (addon instanceof com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon h) {
                if (h.getHatchType() == com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon.HatchType.FLUID_OUTPUT || h.getHatchType() == com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon.HatchType.DUAL_OUTPUT) {
                    if (h.getSlotCapacity() > 1) {
                        return false;
                    }
                    int reqFluidOut = (int) node.getOutputs().stream().filter(IngredientStack::isFluid).count();
                    long currentInstalled = node.getAddons().stream()
                        .filter(a -> a instanceof com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon gh && (gh.getHatchType() == com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon.HatchType.FLUID_OUTPUT || gh.getHatchType() == com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon.HatchType.DUAL_OUTPUT))
                        .count();
                    if (reqFluidOut > 0 && currentInstalled >= reqFluidOut) {
                        return false;
                    }
                }
            } else if (addon.getCategory() == MachineAddon.Category.HATCH_BUS) {
                String path = addon.getId().toLowerCase(Locale.ROOT);
                if ((path.contains("4x") || path.contains("9x") || path.contains("16x") || path.contains("quadruple") || path.contains("nonuple") || path.contains("hexadecimal") || path.contains("multi_fluid")) && (path.contains("output") || path.contains("export"))) {
                    return false;
                }
            }
        }
        if (node.isMultiblock()) {
            ResourceLocation mbWs = node.getMachineIcon();
            if (mbWs == null) mbWs = node.getMultiblockWorkstation();
            if (mbWs != null) {
                var def = com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.getStructure(mbWs);
                if (def != null) {
                    if (addon.getCategory() == MachineAddon.Category.COIL && def.coilSlotCount() == 0 && !MultiblockDetector.isCoilMultiblock(mbWs)) return false;
                    if (addon.getCategory() == MachineAddon.Category.MAINTENANCE && def.maintenanceSlotCount() == 0 && !def.supportsAbility("MAINTENANCE") && def.allowedAbilities() != null && !def.allowedAbilities().isEmpty()) return false;
                    if (addon.getCategory() == MachineAddon.Category.ENERGY_HATCH && def.energyHatchSlotCount() == 0 && !def.supportsAbility("INPUT_ENERGY") && !def.supportsAbility("SUBSTATION_INPUT_ENERGY") && (MultiblockDetector.isSteamMultiblock(mbWs) || node.isGenerator())) return false;
                    if (addon.getCategory() == MachineAddon.Category.HATCH_BUS) {
                        if (addon.getItemIcon() != null && def.isCandidateBlock(addon.getItemIcon())) {
                            // Directly matched candidate block
                        } else if (def.allowedAbilities() != null && !def.allowedAbilities().isEmpty()) {
                            if (addon instanceof com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon gh && !gh.getAbilities().isEmpty()) {
                                for (String reqAbility : gh.getAbilities()) {
                                    if (!def.supportsAbility(reqAbility)) return false;
                                }
                            } else {
                                var type = resolveHatchType(addon);
                                switch (type) {
                                    case ITEM_INPUT -> { if (!def.supportsAbility("IMPORT_ITEMS") && !def.supportsAbility("STEAM_IMPORT_ITEMS")) return false; }
                                    case ITEM_OUTPUT -> { if (!def.supportsAbility("EXPORT_ITEMS") && !def.supportsAbility("STEAM_EXPORT_ITEMS")) return false; }
                                    case FLUID_INPUT -> { if (!def.supportsAbility("IMPORT_FLUIDS") && !def.supportsAbility("STEAM_IMPORT_FLUIDS")) return false; }
                                    case FLUID_OUTPUT -> { if (!def.supportsAbility("EXPORT_FLUIDS") && !def.supportsAbility("STEAM_EXPORT_FLUIDS")) return false; }
                                    case DUAL_INPUT -> { if (!def.supportsAbility("IMPORT_ITEMS") && !def.supportsAbility("IMPORT_FLUIDS")) return false; }
                                    case DUAL_OUTPUT -> { if (!def.supportsAbility("EXPORT_ITEMS") && !def.supportsAbility("EXPORT_FLUIDS")) return false; }
                                    default -> {}
                                }
                            }
                        }
                    }
                }
            }
        }
        if (addon.getCategory() == MachineAddon.Category.ROTOR && !node.isTurbine() && !MachineAddon.isTurbineMachine(node)) {
            return false;
        }
        return true;
    }

    public static com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon.HatchType resolveHatchType(MachineAddon addon) {
        if (addon instanceof com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon gh) {
            return gh.getHatchType();
        }
        if (addon == null || addon.getId() == null) return com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon.HatchType.ITEM_INPUT;
        String idStr = addon.getId().toLowerCase(Locale.ROOT);
        if (idStr.contains("me_pattern_provider") || idStr.contains("pattern_provider")) return com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon.HatchType.ME_PATTERN_PROVIDER;
        if (idStr.contains("dual_input") || idStr.contains("stocking_input") || idStr.contains("stocking_bus")) return com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon.HatchType.DUAL_INPUT;
        if (idStr.contains("dual_output")) return com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon.HatchType.DUAL_OUTPUT;
        if (idStr.contains("input_hatch") || idStr.contains("fluid_import") || idStr.contains("multi_fluid_input")) return com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon.HatchType.FLUID_INPUT;
        if (idStr.contains("output_hatch") || idStr.contains("fluid_export") || idStr.contains("multi_fluid_output")) return com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon.HatchType.FLUID_OUTPUT;
        if (idStr.contains("input_bus") || idStr.contains("import_bus") || idStr.contains("item_import")) return com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon.HatchType.ITEM_INPUT;
        if (idStr.contains("output_bus") || idStr.contains("export_bus") || idStr.contains("item_export")) return com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon.HatchType.ITEM_OUTPUT;
        return com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon.HatchType.ITEM_INPUT;
    }

    @Override
    public ResourceLocation getPreferredMultiblockWorkstation(RecipeNode node, List<ResourceLocation> availableWorkstations) {
        if (node == null || availableWorkstations == null || availableWorkstations.isEmpty()) return null;
        ResourceLocation catId = node.getRecipeCategoryId();
        if (catId != null) {
            for (ResourceLocation ws : availableWorkstations) {
                if (MultiblockDetector.isMultiblock(ws) && ws.getPath().equalsIgnoreCase(catId.getPath())) {
                    return ws;
                }
            }
        }

        // Standard base large multiblocks (e.g. large_chemical_reactor before extreme_/incomprehensible_)
        for (ResourceLocation ws : availableWorkstations) {
            if (MultiblockDetector.isMultiblock(ws)) {
                String path = ws.getPath().toLowerCase(Locale.ROOT);
                if (path.startsWith("large_")) {
                    return ws;
                }
            }
        }

        // Standard base multiblocks before advanced/special variants
        for (ResourceLocation ws : availableWorkstations) {
            if (MultiblockDetector.isMultiblock(ws)) {
                String path = ws.getPath().toLowerCase(Locale.ROOT);
                if (!path.startsWith("mega_") && !path.startsWith("extreme_") && !path.startsWith("incomprehensible_")
                        && !path.startsWith("advanced_") && !path.startsWith("yielding_") && !path.startsWith("super_")
                        && !path.startsWith("supreme_") && !path.startsWith("nyinsane_")) {
                    return ws;
                }
            }
        }

        for (ResourceLocation ws : availableWorkstations) {
            if (MultiblockDetector.isMultiblock(ws)) {
                return ws;
            }
        }
        return null;
    }

    @Override
    public void onAddonInstalled(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return;
        if (!node.isMultiblock() && addon.getCategory() != AddonCategory.CUSTOM && addon.getCategory() != AddonCategory.THERMAL_AUGMENT) {
            node.setMultiblock(true);
            ResourceLocation mbWs = node.getMultiblockWorkstation();
            if (mbWs != null) {
                node.setMachineIcon(mbWs);
            }
        }
        if (addon.getCategory() == MachineAddon.Category.THERMAL_AUGMENT) {
            if (addon.getId().contains("upgrade_kit") || addon.getId().contains("tier_kit")) {
                node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.THERMAL_AUGMENT && (a.getId().contains("upgrade_kit") || a.getId().contains("tier_kit")));
            }
            node.getAddons().add(addon);
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.ROTOR) {
            node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.ROTOR);
            int eff = (int) Math.round(addon.getDurationMultiplier() * 100.0);
            int power = addon.getRotorPower() > 0 ? addon.getRotorPower() : 100;
            node.setRotorEfficiency(eff);
            node.setRotorPower(power);
            node.setRotorName(addon.getName());
            node.getAddons().add(addon);
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.COIL) {
            node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.COIL);
            MachineAddon tailored = addon.forMachine(node);
            node.getAddons().add(tailored);
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.REFLECTOR) {
            node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.REFLECTOR);
            node.getAddons().add(addon);
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.MAINTENANCE) {
            if (isMufflerAddon(addon)) {
                node.getAddons().removeIf(GTCEuModAdapter::isMufflerAddon);
            } else {
                node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.MAINTENANCE && !isMufflerAddon(a));
            }
            node.getAddons().add(addon);
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.PARALLEL) {
            node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.PARALLEL);
            node.getAddons().add(addon);
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.ENERGY_HATCH) {
            List<MachineAddon> existing = new ArrayList<>();
            for (MachineAddon a : node.getAddons()) {
                if (a.getCategory() == MachineAddon.Category.ENERGY_HATCH) {
                    existing.add(a);
                }
            }
            if (existing.size() >= 2) {
                node.getAddons().remove(existing.get(0));
            }
            node.getAddons().add(addon);
            updateNodeTierFromEnergyHatches(node);
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.MULTIBLOCK_TRAIT) {
            node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.MULTIBLOCK_TRAIT || a.getId().equals(addon.getId()));
            if (addon.getId().equals("gtceu:spt_lubricant_boosting")) {
                node.getInputs().removeIf(in -> in.isFluid() && in.getId() != null && in.getId().getPath().contains("tungsten_disulfide"));
                node.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:tungsten_disulfide"), "Tungsten Disulfide", 277.77777777777777, 1.0));
            } else if (addon.getId().equals("gtceu:spt_coolant_boosting")) {
                node.getInputs().removeIf(in -> in.isFluid() && in.getId() != null && (in.getId().getPath().contains("helium_3") || in.getId().getPath().contains("superstate")));
                node.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:superstate_helium_3"), "Superstate Helium 3", 694.4444444444445, 1.0));
            } else if (addon.getId().equals("gtceu:npt_lubricant_boosting")) {
                node.getInputs().removeIf(in -> in.isFluid() && in.getId() != null && in.getId().getPath().contains("tungsten_disulfide"));
                node.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:tungsten_disulfide"), "Tungsten Disulfide", 694.4444444444445, 1.0));
            } else if (addon.getId().equals("gtceu:npt_coolant_boosting")) {
                node.getInputs().removeIf(in -> in.isFluid() && in.getId() != null && (in.getId().getPath().contains("bec_og") || in.getId().getPath().contains("bose_einstein") || in.getId().getPath().contains("oganesson")));
                node.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:bec_og"), "Oganesson Stabilized BEC", 222.22222222222223, 1.0));
            }
            node.getAddons().add(addon);
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.HATCH_BUS) {
            node.getAddons().add(addon);
            return;
        }
        node.getAddons().removeIf(a -> a.getId().equals(addon.getId()));
        node.getAddons().add(addon);
    }

    @Override
    public void onAddonRemoved(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return;
        if (addon.getCategory() == MachineAddon.Category.ENERGY_HATCH) {
            updateNodeTierFromEnergyHatches(node);
        } else if (addon.getCategory() == MachineAddon.Category.ROTOR) {
            node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.ROTOR);
            node.setRotorEfficiency(100);
            node.setRotorPower(100);
            node.setRotorName("Standard (100%)");
        } else if (addon.getId().equals("gtceu:spt_lubricant_boosting") || addon.getId().equals("gtceu:npt_lubricant_boosting")) {
            node.getInputs().removeIf(in -> in.isFluid() && in.getId() != null && in.getId().getPath().contains("tungsten_disulfide"));
        } else if (addon.getId().equals("gtceu:spt_coolant_boosting")) {
            node.getInputs().removeIf(in -> in.isFluid() && in.getId() != null && (in.getId().getPath().contains("helium_3") || in.getId().getPath().contains("superstate")));
        } else if (addon.getId().equals("gtceu:npt_coolant_boosting")) {
            node.getInputs().removeIf(in -> in.isFluid() && in.getId() != null && (in.getId().getPath().contains("bec_og") || in.getId().getPath().contains("bose_einstein") || in.getId().getPath().contains("oganesson")));
        }
    }

    public static void updateNodeTierFromEnergyHatches(RecipeNode node) {
        if (node == null) return;
        List<com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon> hatches = new ArrayList<>();
        for (MachineAddon a : node.getAddons()) {
            if (a instanceof com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon eh) {
                hatches.add(eh);
            }
        }
        if (hatches.isEmpty()) return;

        long totalEUtCapacity = 0;
        GTVoltageTier maxSingleHatchTier = GTVoltageTier.ULV;

        for (var h : hatches) {
            totalEUtCapacity += (long) h.getTier().getVoltage() * h.getAmperage();
            if (h.getTier().ordinal() > maxSingleHatchTier.ordinal()) {
                maxSingleHatchTier = h.getTier();
            }
        }

        GTVoltageTier capacityTier = GTVoltageTier.getMaxTierProvided(totalEUtCapacity);

        // 1. Dual Hatch of the exact same tier N -> unlocks tier N + 1 (Dual Hatch Overclock)
        if (hatches.size() == 2 && hatches.get(0).getTier() == hatches.get(1).getTier()) {
            GTVoltageTier base = hatches.get(0).getTier();
            GTVoltageTier dualTier = base.ordinal() < GTVoltageTier.MAX.ordinal()
                    ? GTVoltageTier.getByIndex(base.ordinal() + 1)
                    : base;
            node.setTargetTier(capacityTier.ordinal() > dualTier.ordinal() ? capacityTier : dualTier);
            return;
        }

        // 2. Asymmetric Hatches (e.g. 16A EV + 1A IV):
        // Total EU/t capacity determines effective power tier for speed overclocks while satisfying recipe tier requirements
        if (capacityTier.ordinal() > maxSingleHatchTier.ordinal()) {
            node.setTargetTier(capacityTier);
        } else {
            node.setTargetTier(maxSingleHatchTier);
        }
    }

    @Override
    public void buildAddonTooltip(RecipeNode node, MachineAddon addon, boolean isActiveAddon, List<Component> tooltip) {
        if (addon == null || tooltip == null) return;
        if (addon.getId().equals("gtceu:spt_lubricant_boosting")) {
            tooltip.add(Component.literal("§e⚡ +25% EU/t §7(1,000 B/h Tungsten Disulfide)"));
            return;
        }
        if (addon.getId().equals("gtceu:spt_coolant_boosting")) {
            tooltip.add(Component.literal("§e⚡ +75% EU/t §7(2,500 B/h Superstate Helium 3)"));
            return;
        }
        if (addon.getId().equals("gtceu:npt_lubricant_boosting")) {
            tooltip.add(Component.literal("§e⚡ +50% EU/t §7(2,500 B/h Tungsten Disulfide)"));
            return;
        }
        if (addon.getId().equals("gtceu:npt_coolant_boosting")) {
            tooltip.add(Component.literal("§e⚡ +150% EU/t §7(800 B/h Oganesson BEC)"));
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.ROTOR) {
            int eff = (int) Math.round(addon.getDurationMultiplier() * 100.0);
            int pwr = addon.getRotorPower() > 0 ? addon.getRotorPower() : 100;
            tooltip.add(Component.literal("§a⚙ ").append(Component.translatable("gui.gtcalcboard.addon.rotor.efficiency", eff + "%")));
            tooltip.add(Component.literal("§6⚡ ").append(Component.translatable("gui.gtcalcboard.addon.rotor.power", pwr + "%")));
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.COIL) {
            tooltip.add(Component.literal("§6♨ ").append(Component.translatable("gui.gtcalcboard.addon.stat.coil_temp", addon.getCoilTemperature())));
            MachineAddon tailored = addon.forMachine(node);
            if (tailored.getParallelMultiplier() > 1) {
                tooltip.add(Component.literal("§b⚡ ").append(Component.translatable("gui.gtcalcboard.addon.stat.parallel", tailored.getParallelMultiplier())));
            }
            if (tailored.getDurationMultiplier() != 1.0) {
                tooltip.add(Component.literal("§a⏳ ").append(Component.translatable("gui.gtcalcboard.addon.stat.speed_mult", String.format(Locale.ROOT, "%.2fx", 1.0 / tailored.getDurationMultiplier()))));
            }
            if (tailored.getEutMultiplier() != 1.0) {
                tooltip.add(Component.literal("§e⚡ ").append(Component.translatable("gui.gtcalcboard.addon.stat.eut_mult", String.format(Locale.ROOT, "%.2fx", tailored.getEutMultiplier()))));
            }
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.REFLECTOR) {
            tooltip.add(Component.literal("§b🪞 ").append(Component.translatable("gui.gtcalcboard.addon.reflector.tier", addon.getReflectorTier())));
            return;
        }
        IModAdapter.super.buildAddonTooltip(node, addon, isActiveAddon, tooltip);
    }

    @Override
    public List<MachineAddon> getResetAddonCards(RecipeNode node) {
        List<MachineAddon> list = new ArrayList<>();
        if (node == null) return list;

        if (node.isTurbine() && node.isMultiblock()) {
            GTRotorAddon stdRotor = new GTRotorAddon("gtceu:rotor_standard",
                    Component.translatable("gui.gtcalcboard.rotor.standard").getString(),
                    Component.translatable("gui.gtcalcboard.addon.turbine_efficiency_desc", "100").getString(),
                    ResourceLocation.tryParse("gtceu:turbine_rotor"), 100, 100, 1600.0);
            stdRotor.setDiscoverySource("Standard Default Rotor");
            list.add(stdRotor);
        }

        boolean isFusion = node.isFusion() || node.getRequiredReflectorTier() > 0
                || (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("fusion"))
                || (node.getMachineIcon() != null && node.getMachineIcon().getPath().contains("fusion"))
                || (node.getName() != null && node.getName().toLowerCase().contains("fusion"));

        if (isFusion) {
            GTReflectorAddon noRefl = new GTReflectorAddon("gtceu:reflector_none",
                    Component.translatable("gui.gtcalcboard.reflector.none").getString(),
                    Component.translatable("gui.gtcalcboard.reflector.none_desc").getString(),
                    null, 0);
            noRefl.setDiscoverySource("No Reflector Default");
            list.add(noRefl);
        }

        return list;
    }

    @Override
    public boolean validateNode(RecipeNode node, List<Component> warnings) {
        return validateNode(node, (FlowGraph) null, warnings);
    }

    @Override
    public boolean validateNode(RecipeNode node, FlowGraph graph, List<Component> warnings) {
        if (node == null) return true;
        boolean valid = true;

        // 1. Reflector requirement check (RFC-001 & RFC-002)
        int req = node.getProperties().get(NodeProperties.REQUIRED_REFLECTOR_TIER);
        if (req > 0) {
            int inst = node.getInstalledReflectorTier();
            if (inst < req) {
                if (warnings != null) {
                    warnings.add(Component.translatable("gui.gtcalcboard.reflector_required_warning", req, inst > 0 ? ("T" + inst) : Component.translatable("gui.gtcalcboard.none")));
                }
                valid = false;
            }
        }

        // 2. Turbine 100% Flow Fulfillment Check
        if (graph != null && GTTurbineHelper.isTurbine(node)) {
            for (int inIdx = 0; inIdx < node.getInputs().size(); inIdx++) {
                FlowGraphSolver.PortFlowStats stats = graph.getInputPortStats(node, inIdx);
                if (stats != null && stats.isConnected() && stats.isInputDeficit()) {
                    if (warnings != null) {
                        IngredientStack inStack = node.getInputs().get(inIdx);
                        String matName = inStack != null ? inStack.getDisplayName() : "Fluid";
                        warnings.add(Component.translatable("gui.gtcalcboard.turbine_flow_deficit_warning", matName, (int) Math.round(stats.getPercent())));
                    }
                    valid = false;
                }
            }
        }

        // 3. Equipped Custom Hatch / Bus Capacity & Slot Validation
        int totalEquippedItemInSlots = 0;
        int totalEquippedItemOutSlots = 0;
        int totalEquippedFluidInSlots = 0;
        int totalEquippedFluidOutSlots = 0;
        long maxOutputTankCapacityMB = 0;
        boolean hasCustomItemIn = false;
        boolean hasCustomItemOut = false;
        boolean hasCustomFluidIn = false;
        boolean hasCustomFluidOut = false;

        for (MachineAddon addon : node.getAddons()) {
            if (addon instanceof com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon h) {
                int cap = h.getSlotCapacity();
                long tankPerSlot = h.getTankCapacityMB() / Math.max(1, cap);
                switch (h.getHatchType()) {
                    case ITEM_INPUT -> {
                        hasCustomItemIn = true;
                        totalEquippedItemInSlots += cap;
                    }
                    case ITEM_OUTPUT -> {
                        hasCustomItemOut = true;
                        totalEquippedItemOutSlots += cap;
                    }
                    case FLUID_INPUT -> {
                        hasCustomFluidIn = true;
                        totalEquippedFluidInSlots += cap;
                    }
                    case FLUID_OUTPUT -> {
                        hasCustomFluidOut = true;
                        totalEquippedFluidOutSlots += cap;
                        maxOutputTankCapacityMB = Math.max(maxOutputTankCapacityMB, tankPerSlot);
                    }
                    case DUAL_INPUT -> {
                        hasCustomItemIn = true;
                        hasCustomFluidIn = true;
                        totalEquippedItemInSlots += cap;
                        totalEquippedFluidInSlots += cap;
                    }
                    case DUAL_OUTPUT -> {
                        hasCustomItemOut = true;
                        hasCustomFluidOut = true;
                        totalEquippedItemOutSlots += cap;
                        totalEquippedFluidOutSlots += cap;
                        maxOutputTankCapacityMB = Math.max(maxOutputTankCapacityMB, tankPerSlot);
                    }
                    case ME_PATTERN_PROVIDER -> {
                        hasCustomItemIn = true;
                        hasCustomFluidIn = true;
                        totalEquippedItemInSlots += 36;
                        totalEquippedFluidInSlots += 36;
                    }
                    default -> {}
                }
            }
        }

        int reqItemOut = 0;
        for (int i = 0; i < node.getOutputs().size(); i++) {
            IngredientStack out = node.getOutputs().get(i);
            if (out.isItem() && node.getEffectiveOutputChance(i) > 0.0) {
                reqItemOut++;
            }
        }
        if (hasCustomItemOut && reqItemOut > totalEquippedItemOutSlots) {
            if (warnings != null) {
                warnings.add(Component.translatable("gui.gtcalcboard.node_warning.item_slot_deficit", reqItemOut, totalEquippedItemOutSlots));
            }
            valid = false;
        }

        int reqItemIn = (int) node.getInputs().stream().filter(IngredientStack::isItem).count();
        if (hasCustomItemIn && reqItemIn > totalEquippedItemInSlots) {
            if (warnings != null) {
                warnings.add(Component.translatable("gui.gtcalcboard.node_warning.item_input_slot_deficit", reqItemIn, totalEquippedItemInSlots));
            }
            valid = false;
        }

        int reqFluidOut = 0;
        for (int i = 0; i < node.getOutputs().size(); i++) {
            IngredientStack out = node.getOutputs().get(i);
            if (out.isFluid() && node.getEffectiveOutputChance(i) > 0.0) {
                reqFluidOut++;
            }
        }
        if (hasCustomFluidOut && reqFluidOut > totalEquippedFluidOutSlots) {
            if (warnings != null) {
                warnings.add(Component.translatable("gui.gtcalcboard.node_warning.fluid_slot_deficit", reqFluidOut, totalEquippedFluidOutSlots));
            }
            valid = false;
        }

        int reqFluidIn = (int) node.getInputs().stream().filter(IngredientStack::isFluid).count();
        if (hasCustomFluidIn && reqFluidIn > totalEquippedFluidInSlots) {
            if (warnings != null) {
                warnings.add(Component.translatable("gui.gtcalcboard.node_warning.fluid_input_slot_deficit", reqFluidIn, totalEquippedFluidInSlots));
            }
            valid = false;
        }

        if (hasCustomFluidOut && maxOutputTankCapacityMB > 0) {
            for (IngredientStack out : node.getOutputs()) {
                if (out != null && out.isFluid()) {
                    double batchMB = out.getAmount() * node.getTotalParallel();
                    if (batchMB > maxOutputTankCapacityMB) {
                        if (warnings != null) {
                            warnings.add(Component.translatable("gui.gtcalcboard.node_warning.tank_capacity_overflow",
                                out.getDisplayName(), (int) Math.round(batchMB), maxOutputTankCapacityMB));
                        }
                        valid = false;
                    }
                }
            }
        }

        if (isDistillationTower(node)) {
            int reqFluidOutDT = (int) node.getOutputs().stream().filter(IngredientStack::isFluid).count();
            int installedFluidOutHatches = 0;
            for (MachineAddon addon : node.getAddons()) {
                if (addon instanceof com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon h) {
                    if (h.getHatchType() == com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon.HatchType.FLUID_OUTPUT || h.getHatchType() == com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon.HatchType.DUAL_OUTPUT) {
                        if (h.getSlotCapacity() > 1) {
                            if (warnings != null) {
                                warnings.add(Component.translatable("gui.gtcalcboard.node_warning.dt_multi_fluid_unsupported", h.getName()));
                            }
                            valid = false;
                        }
                        installedFluidOutHatches++;
                    }
                }
            }
            if (installedFluidOutHatches > reqFluidOutDT && reqFluidOutDT > 0) {
                if (warnings != null) {
                    warnings.add(Component.translatable("gui.gtcalcboard.node_warning.dt_excess_output_hatches", installedFluidOutHatches, reqFluidOutDT));
                }
                valid = false;
            }
        }

        return valid;
    }

    @Override
    public MachineAddon tailorAddon(MachineAddon addon, RecipeNode targetNode) {
        if (addon == null || targetNode == null) return addon;
        if (addon.getCategory() == MachineAddon.Category.COIL) {
            return CoilHelper.tailorCoilAddon(addon, targetNode);
        }
        return addon;
    }

    @Override
    public OverclockMode.OverclockResult computeOverclock(RecipeNode node, GTVoltageTier targetTier, boolean isGenerator) {
        if (node.getEnergyType() == EnergyType.NONE) {
            double durationTicks = Math.max(1.0, node.getBaseDurationTicks() * node.getCombinedDurationMultiplier());
            return new OverclockMode.OverclockResult(durationTicks, 0.0, 1.0, 0);
        }

        if (node.getSteamMode() != null && node.getSteamMode().isSteam()) {
            double durationTicks = Math.max(1.0, node.getBaseDurationTicks() * node.getSteamMode().getDurationMultiplier() * node.getCombinedDurationMultiplier());
            return new OverclockMode.OverclockResult(durationTicks, 0.0, 1.0, 0);
        }

        if (node.getEnergyType() == EnergyType.HEAT_OR_SELF) {
            double boilerSpeed = getBoilerSpeedMultiplier(node);
            double durationTicks = Math.max(1.0, node.getBaseDurationTicks() / boilerSpeed);
            return new OverclockMode.OverclockResult(durationTicks, 0.0, 1.0, 0);
        }

        OverclockMode.OverclockResult baseRes;
        if (isGenerator) {
            baseRes = new OverclockMode.OverclockResult(node.getBaseDurationTicks(), node.getBaseEUt(), 1.0, 0);
        } else {
            baseRes = node.getOverclockMode().calculate(node.getBaseDurationTicks(), node.getBaseEUt(), node.getTierDelta());
        }

        double finalDuration;
        double finalEut;

        if (isGenerator) {
            if (GTTurbineHelper.isLargeTurbine(node)) {
                int holderBonus = GTTurbineHelper.getTurbineHolderEfficiencyBonus(node);
                double rMult = (node.getRotorEfficiency() > 0 ? node.getRotorEfficiency() : 100) / 100.0;
                for (MachineAddon a : node.getAddons()) {
                    if (a.getCategory() == MachineAddon.Category.ROTOR) {
                        rMult = a.getDurationMultiplier();
                        break;
                    }
                }
                double rotorEffMult = rMult + (holderBonus / 100.0);

                double otherMult = 1.0;
                for (MachineAddon a : node.getAddons()) {
                    if (a.getCategory() != MachineAddon.Category.ROTOR) {
                        otherMult *= a.getDurationMultiplier();
                    }
                }

                finalDuration = Math.max(1.0, baseRes.durationTicks() * rotorEffMult * otherMult);
                finalEut = baseRes.eut();
            } else {
                finalDuration = Math.max(1.0, baseRes.durationTicks() * node.getCombinedDurationMultiplier());
                finalEut = Math.max(1.0, baseRes.eut() * node.getCombinedEutMultiplier());
            }
        } else {
            finalDuration = Math.max(1.0, baseRes.durationTicks() * node.getCombinedDurationMultiplier());
            finalEut = Math.max(1.0, baseRes.eut() * node.getCombinedEutMultiplier());
        }

        if (node.hasThreading()) {
            finalDuration = Math.max(1.0, finalDuration * node.getThreadingConfig().getFinalDurationMultiplier());
            finalEut = Math.max(1.0, finalEut * node.getThreadingConfig().getFinalPowerMultiplier());
        }

        return new OverclockMode.OverclockResult(finalDuration, finalEut, baseRes.batchesPerTick(), baseRes.overclocks());
    }

    public static double getBoilerSpeedMultiplier(RecipeNode node) {
        if (node == null) return 1.0;
        GTBoilerTier bt = GTBoilerTier.getBoilerTier(node);
        boolean isLiquid = node.isLiquidBoilerRecipe();
        boolean isLargeBoiler = isLargeBoilerRecipe(node);
        double speed = bt.getSpeedMultiplier(isLiquid, isLargeBoiler);
        if (bt.isMultiblock()) {
            int throttle = node.getProperties().get(NodeProperties.BOILER_THROTTLE);
            throttle = Math.max(25, Math.min(100, throttle));
            speed *= (throttle / 100.0);
        }
        return speed;
    }

    public static boolean isLargeBoilerRecipe(RecipeNode node) {
        if (node == null) return false;
        if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("large_boiler")) {
            return true;
        }
        return false;
    }

    @Override
    public List<Component> buildEnergyTooltip(RecipeNode node) {
        return GTCEuGuiHandler.buildEnergyTooltip(node);
    }

    @Override
    public void renderCardControls(GuiGraphics graphics, Font font,
                                   RecipeNode node, int x, int row2Y, int cardW, int mouseX, int mouseY,
                                   boolean isGlowing) {
        GTCEuGuiHandler.renderCardControls(graphics, font, node, x, row2Y, cardW, mouseX, mouseY, isGlowing);
    }

    @Override
    public boolean adaptRecipeDetails(Object emiRecipeObj, Object backing, EmiRecipeConverter.RecipeDetails details) {
        return GTCEuRecipeHandler.adaptRecipeDetails(emiRecipeObj, backing, details);
    }

    @Override
    public double computeSingleMachinePower(RecipeNode node) {
        if (!node.isOperational()) return 0.0;
        if (node.isGenerator()) {
            if (GTTurbineHelper.isLargeTurbine(node)) {
                double boost = node.getCombinedEutMultiplier();
                if (node.getParallel() == 1 && GTTurbineHelper.hasRotorAddon(node)) {
                    double cap = GTTurbineHelper.getGeneratorMaxEUt(node);
                    if (cap < Double.MAX_VALUE) {
                        return cap * boost;
                    }
                }
                double rawGen = computeOverclock(node, node.getTargetTier(), true).eut() * computeEffectiveParallel(node);
                double cap = GTTurbineHelper.getGeneratorMaxEUt(node);
                return Math.min(rawGen, cap) * boost;
            } else if (isGTGenerator(node) && node.getParallel() == 1) {
                double recipeEUt = Math.abs(node.getBaseEUt());
                if (recipeEUt < node.getTargetTier().getVoltage()) {
                    double rawGen = computeOverclock(node, node.getTargetTier(), true).eut() * computeEffectiveParallel(node);
                    double cap = (double) node.getTargetTier().getVoltage();
                    return Math.min(rawGen, cap);
                }
            }
            return computeOverclock(node, node.getTargetTier(), true).eut() * computeEffectiveParallel(node);
        }
        if (node.hasPowerConstantAddon()) {
            return computeOverclock(node, node.getTargetTier(), false).eut() * node.getParallel();
        }
        return computeOverclock(node, node.getTargetTier(), false).eut() * computeEffectiveParallel(node);
    }

    @Override
    public int computeEffectiveParallel(RecipeNode node) {
        int par;
        if (node.isGenerator()) {
            if (GTTurbineHelper.isLargeTurbine(node)) {
                par = GTTurbineHelper.getEffectiveTurbineParallel(node) * node.getCombinedParallelMultiplier();
            } else if (isGTGenerator(node)) {
                par = getEffectiveSingleblockParallel(node) * node.getCombinedParallelMultiplier();
            } else {
                par = Math.max(1, node.getParallel() * node.getCombinedParallelMultiplier());
            }
        } else {
            int base = node.isMultiblock() ? getDefaultParallel(node) : 1;
            int effectiveBase = Math.max(base, node.getParallel() > 1 ? node.getParallel() : 1);
            par = Math.max(1, effectiveBase * node.getCombinedParallelMultiplier());
        }
        if (node.hasThreading()) {
            par *= node.getThreadingConfig().getEffectiveParallels();
        }
        return par;
    }

    @Override
    public int getDefaultParallel(RecipeNode node) {
        if (node == null) return 1;
        return MultiblockDetector.getDefaultParallel(node);
    }

    @Override
    public void autoTuneParallel(RecipeNode node) {
        if (node.isGenerator() && GTTurbineHelper.isLargeTurbine(node)) {
            GTTurbineHelper.autoCalculateTurbineParallel(node);
        } else if (node.getParallel() <= 1) {
            int defPar = getDefaultParallel(node);
            if (defPar > 1) {
                node.setParallel(defPar);
            }
        }
    }

    private boolean isGTGenerator(RecipeNode node) {
        if (!node.isGenerator()) return false;
        if (node.getEnergyType() != EnergyType.ELECTRIC_EU) return false;
        if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getNamespace().equals("gtceu")) return true;
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (ws != null && ws.getNamespace().equals("gtceu")) return true;
        }
        if (node.getMachineIcon() != null && node.getMachineIcon().getNamespace().equals("gtceu")) return true;
        if (node.getName() != null && (node.getName().contains("Turbine") || node.getName().contains("Generator"))) return true;
        return false;
    }

    private int getEffectiveSingleblockParallel(RecipeNode node) {
        if (!isGTGenerator(node) || GTTurbineHelper.isLargeTurbine(node)) return Math.max(1, node.getParallel());
        if (node.getParallel() > 1) return node.getParallel();
        double recipeEUt = Math.abs(node.getBaseEUt());
        if (recipeEUt > 0 && node.getTargetTier() != null && recipeEUt < node.getTargetTier().getVoltage()) {
            return (int) Math.max(1, Math.floor((double) node.getTargetTier().getVoltage() / recipeEUt));
        }
        return Math.max(1, node.getParallel());
    }

    @Override
    public boolean supportsSteamMode(RecipeNode node) {
        if (node == null) return false;
        if (node.getSteamMode() != null && node.getSteamMode().isSteam()) {
            return true;
        }
        if (MultiblockDetector.isSteamMultiblock(node.getMachineIcon()) || MultiblockDetector.isSteamMultiblock(node.getMultiblockWorkstation())) {
            return true;
        }
        if (node.getRecipeCategoryId() != null) {
            CategoryCapability cap = CategoryCapabilityMatrix.getInstance().getCapability(node.getRecipeCategoryId());
            if (cap != null && cap.supportsSteamMode()) {
                return true;
            }
        }
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (ws != null && (MultiblockDetector.isSteamMultiblock(ws) || ws.getPath().startsWith("steam_") || ws.getPath().startsWith("lp_steam_") || ws.getPath().startsWith("hp_steam_"))) {
                return true;
            }
            if (ws != null && ws.getNamespace().equals("gtceu")) {
                try {
                    Class<?> gtRegistriesCls = Class.forName("com.gregtechceu.gtceu.api.registry.GTRegistries");
                    Object machinesRegistry = gtRegistriesCls.getField("MACHINES").get(null);
                    java.lang.reflect.Method mGet = machinesRegistry.getClass().getMethod("get", ResourceLocation.class);
                    Object def = mGet.invoke(machinesRegistry, ws);
                    if (def != null && com.gtceu.calcboard.compat.gtceu.helper.GTCEuCapabilityScanner.isSteamDefinition(def, ws)) {
                        return true;
                    }
                } catch (Throwable ignored) {}
            }
        }
        return false;
    }

    @Override
    public void onMachineIconChanged(RecipeNode node, ResourceLocation oldIcon, ResourceLocation newIcon) {
        if (node == null || newIcon == null) return;
        if (MultiblockDetector.isSteamMultiblock(newIcon)) {
            node.setMultiblock(true);
            int defPar = MultiblockDetector.getDefaultParallel(newIcon);
            node.setParallel(Math.max(1, defPar));
            node.setSteamMode(SteamMode.HIGH_PRESSURE);
        } else if (MultiblockDetector.isMultiblock(newIcon)) {
            node.setMultiblock(true);
            if (node.getSteamMode().isSteam()) {
                node.setSteamMode(SteamMode.NONE);
            }
            int defPar = MultiblockDetector.getDefaultParallel(newIcon);
            if (defPar > 1 && node.getParallel() <= 1) {
                node.setParallel(defPar);
            }
        } else {
            node.setMultiblock(false);
            if (node.getParallel() > 1 && oldIcon != null && MultiblockDetector.isMultiblock(oldIcon)) {
                node.setParallel(1);
            }
        }
    }

    @Override
    public EnergyType getEnergyType(RecipeNode node) {
        if (node == null) return EnergyType.ELECTRIC_EU;
        if (isBoilerRecipe(node) || isLiquidBoilerRecipe(node)) {
            return EnergyType.HEAT_OR_SELF;
        }
        if (node.getBaseEUt() <= 0.0 && !node.isGenerator()) {
            return EnergyType.NONE;
        }
        return EnergyType.ELECTRIC_EU;
    }

    @Override
    public void onSteamModeChanged(RecipeNode node, SteamMode oldMode, SteamMode newMode) {
        ResourceLocation steamId = ResourceLocation.tryParse("gtceu:steam");
        if (newMode != null && newMode.isSteam()) {
            double durTicks = node.getBaseDurationTicks() * newMode.getDurationMultiplier();
            double steamAmountPerBatch;
            if (node.isMultiblock() || MultiblockDetector.isSteamMultiblock(node.getMachineIcon())) {
                double steamRatePerTick = MultiblockDetector.getSteamMultiblockConsumption(node.getMachineIcon(), newMode);
                int parallel = Math.max(1, node.getParallel());
                steamAmountPerBatch = (steamRatePerTick * durTicks) / parallel;
            } else {
                steamAmountPerBatch = (node.getBaseEUt() * 2.0) * durTicks;
            }

            node.getInputs().removeIf(in -> in.isFluid() && steamId != null && steamId.equals(in.getId()));
            node.getInputs().add(IngredientStack.fluid(steamId, "Steam", steamAmountPerBatch));

            // Sync workstation icon from pre-baked capability matrix for singleblock machines
            if (!node.isMultiblock() && !MultiblockDetector.isSteamMultiblock(node.getMachineIcon()) && node.getRecipeCategoryId() != null) {
                CategoryCapability cap = CategoryCapabilityMatrix.getInstance().getCapability(node.getRecipeCategoryId());
                if (newMode == SteamMode.LOW_PRESSURE && cap.lowPressureWorkstation() != null) {
                    node.setMachineIcon(cap.lowPressureWorkstation());
                } else if (newMode == SteamMode.HIGH_PRESSURE && cap.highPressureWorkstation() != null) {
                    node.setMachineIcon(cap.highPressureWorkstation());
                }
            }
        } else if (oldMode != null && oldMode.isSteam()) {
            // Remove injected steam stack
            node.getInputs().removeIf(in -> in.isFluid() && steamId != null && steamId.equals(in.getId()));
            // Restore singleblock workstation icon for singleblock machines
            if (!node.isMultiblock() && !MultiblockDetector.isSteamMultiblock(node.getMachineIcon())) {
                ResourceLocation sbWs = node.getWorkstationForTier(node.getTargetTier());
                if (sbWs == null) {
                    sbWs = node.getSingleblockWorkstation();
                }
                if (sbWs != null) {
                    node.setMachineIcon(sbWs);
                }
            }
        }
    }

    @Override
    public double computeEffectiveIngredientRate(RecipeNode node, IngredientStack stack, boolean isInput, double defaultRate) {
        if (isInput && stack != null && stack.isFluid() && stack.getId() != null && "gtceu:steam".equals(stack.getId().toString())) {
            if (node.getSteamMode() != null && node.getSteamMode().isSteam()) {
                if (node.isMultiblock() || MultiblockDetector.isSteamMultiblock(node.getMachineIcon())) {
                    double steamRatePerTick = MultiblockDetector.getSteamMultiblockConsumption(node.getMachineIcon(), node.getSteamMode());
                    return steamRatePerTick * 20.0 * node.getMachineCount();
                } else {
                    return (node.getBaseEUt() * 2.0 * 20.0) * node.getMachineCount();
                }
            }
        }
        return defaultRate;
    }

    @Override
    public double computeSingleMachineIngredientRate(RecipeNode node, IngredientStack stack, boolean isInput, double defaultRate) {
        if (isInput && stack != null && stack.isFluid() && stack.getId() != null && "gtceu:steam".equals(stack.getId().toString())) {
            if (node.getSteamMode() != null && node.getSteamMode().isSteam()) {
                if (node.isMultiblock() || MultiblockDetector.isSteamMultiblock(node.getMachineIcon())) {
                    double steamRatePerTick = MultiblockDetector.getSteamMultiblockConsumption(node.getMachineIcon(), node.getSteamMode());
                    return steamRatePerTick * 20.0;
                } else {
                    return node.getBaseEUt() * 2.0 * 20.0;
                }
            }
        }
        return defaultRate;
    }

    @Override
    public boolean isBoilerRecipe(RecipeNode node) {
        if (node == null) return false;
        if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("boiler")) {
            return true;
        }
        if (node.getMachineIcon() != null && node.getMachineIcon().getPath().contains("boiler")) {
            return true;
        }
        return false;
    }

    @Override
    public boolean isLiquidBoilerRecipe(RecipeNode node) {
        if (!isBoilerRecipe(node)) return false;
        boolean hasNonWaterFluid = false;
        boolean hasItemInput = false;
        for (IngredientStack in : node.getInputs()) {
            if (in != null && in.getId() != null) {
                if (in.isItem()) {
                    hasItemInput = true;
                } else if (in.isFluid() && !in.getId().getPath().contains("water")) {
                    hasNonWaterFluid = true;
                }
            }
        }
        if (hasNonWaterFluid) return true;
        if (hasItemInput) return false;
        if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("liquid")) return true;
        if (node.getMachineIcon() != null && node.getMachineIcon().getPath().contains("liquid")) return true;
        if (node.getName() != null) {
            String n = node.getName().toLowerCase(java.util.Locale.ROOT);
            if (n.contains("lava") || n.contains("liquid") || n.contains("creosote") || n.contains("diesel") || n.contains("ethanol") || n.contains("biomass")
                    || n.contains("oil boiler") || n.contains("(oil)") || n.contains("liquid fuel")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void renderDialogHeader(GuiGraphics graphics, Font font, RecipeNode node, int x, int y, int dialogW, int mouseX, int mouseY, float partialTicks, net.minecraft.client.gui.components.EditBox parallelBox, com.gtceu.calcboard.client.gui.BoardScreen parent) {
        GTCEuGuiHandler.renderDialogHeader(graphics, font, node, x, y, dialogW, mouseX, mouseY, partialTicks, parallelBox, parent);
    }

    @Override
    public boolean handleDialogHeaderClick(com.gtceu.calcboard.client.gui.MachineConfigDialog dialog, RecipeNode node, int x, int y, int dialogW, double mouseX, double mouseY, int button, net.minecraft.client.gui.components.EditBox parallelBox, com.gtceu.calcboard.client.gui.BoardScreen parent) {
        return GTCEuGuiHandler.handleDialogHeaderClick(dialog, node, x, y, dialogW, mouseX, mouseY, button, parallelBox, parent);
    }

    @Override
    public boolean handleDialogHeaderScroll(com.gtceu.calcboard.client.gui.MachineConfigDialog dialog, RecipeNode node, int x, int y, int dialogW, double mouseX, double mouseY, double delta) {
        return GTCEuGuiHandler.handleControllerScroll(node, delta);
    }

    public static void syncTurbineMachineIcon(RecipeNode node) {
        if (node == null || !node.isTurbine()) return;
        if (node.isMultiblock()) {
            ResourceLocation mbWs = node.getMultiblockWorkstation();
            if (mbWs != null) {
                node.setMachineIcon(mbWs);
            } else if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("steam_turbine")) {
                node.setMachineIcon(ResourceLocation.tryParse("gtceu:large_steam_turbine"));
            } else if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("gas_turbine")) {
                node.setMachineIcon(ResourceLocation.tryParse("gtceu:large_gas_turbine"));
            } else if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("plasma_turbine")) {
                node.setMachineIcon(ResourceLocation.tryParse("gtceu:large_plasma_turbine"));
            }
        } else {
            GTVoltageTier tier = node.getTargetTier();
            if (tier == null) tier = GTVoltageTier.LV;
            String prefix = tier.name().toLowerCase(java.util.Locale.ROOT);
            if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("steam_turbine")) {
                node.setMachineIcon(ResourceLocation.tryParse("gtceu:" + prefix + "_steam_turbine"));
            } else if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("gas_turbine")) {
                node.setMachineIcon(ResourceLocation.tryParse("gtceu:" + prefix + "_gas_turbine"));
            }
        }
    }

    public static boolean isMufflerAddon(MachineAddon addon) {
        if (addon == null) return false;
        String id = addon.getId() != null ? addon.getId().toLowerCase(java.util.Locale.ROOT) : "";
        return id.contains("muffler");
    }

    @Override
    public List<com.gtceu.calcboard.api.bom.MultiblockStructurePart> resolveStructureParts(RecipeNode node, boolean dualLowerTierEnergyHatches) {
        if (node == null) return List.of();
        ResourceLocation machineId = node.getMachineIcon();
        if (node.isMultiblock() || (machineId != null && MultiblockDetector.isMultiblock(machineId))) {
            return com.gtceu.calcboard.compat.gtceu.helper.GTCEuBOMHelper.resolveGTMultiblockParts(node, dualLowerTierEnergyHatches);
        }
        return IModAdapter.super.resolveStructureParts(node, dualLowerTierEnergyHatches);
    }
}
