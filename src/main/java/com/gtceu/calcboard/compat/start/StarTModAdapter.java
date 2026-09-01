package com.gtceu.calcboard.compat.start;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTThreadingHelix;
import com.gtceu.calcboard.api.type.NodeThreadingConfig;

import com.gtceu.calcboard.compat.ModAdapterRegistry;
import com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StarTModAdapter extends GTCEuModAdapter {

    @Override
    public String getModId() {
        return "start_core";
    }

    @Override
    public int getPriority() {
        return 105;
    }

    @Override
    public boolean isLoaded() {
        try {
            if (ModList.get() != null) {
                return ModList.get().isLoaded("start_core") || ModList.get().isLoaded("gtceu_start")
                        || ModList.get().isLoaded("start") || ModList.get().isLoaded("star_technology");
            }
        } catch (Throwable ignored) {}
        return true;
    }

    @Override
    public boolean handlesCategory(ResourceLocation categoryId) {
        if (categoryId == null) return false;
        String ns = categoryId.getNamespace().toLowerCase(Locale.ROOT);
        return ns.equals("start_core") || ns.equals("gtceu_start") || ns.equals("start") || ns.equals("star_technology");
    }

    @Override
    public boolean handlesNode(RecipeNode node) {
        if (node == null) return false;
        if (node.getEnergyTypeOverride() == com.gtceu.calcboard.api.type.EnergyType.KINETIC_SU) return false;

        if (node.getRecipeCategoryId() != null && handlesCategory(node.getRecipeCategoryId())) {
            return true;
        }

        if (node.getMachineIcon() != null) {
            String ns = node.getMachineIcon().getNamespace().toLowerCase(Locale.ROOT);
            if (ns.equals("start_core") || ns.equals("gtceu_start")) {
                return true;
            }
            if (MultiblockDetector.isThreadingMultiblock(node.getMachineIcon())) {
                return true;
            }
        }

        if (StarTTurbineHelper.isStarTTurbine(node)) {
            return true;
        }

        return node.getThreadingConfig() != null && node.getThreadingConfig().isActive();
    }

    @Override
    public void discoverAddons(List<MachineAddon> collector, List<ItemStack> recipeOutputStacks) {
        StarTAddonCrawler.discoverAddons(collector, recipeOutputStacks);
    }

    @Override
    public boolean isAddonCompatible(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return false;
        if (addon.getCategory().equals(AddonCategory.CUSTOM)) return true;

        if (StarTTurbineHelper.isStarTTrait(addon)) {
            return StarTTurbineHelper.isCompatibleStarTTrait(node, addon);
        }

        return super.isAddonCompatible(node, addon);
    }

    @Override
    public List<AddonCategory> getApplicableAddonCategories(RecipeNode node) {
        List<AddonCategory> mutable = new ArrayList<>(super.getApplicableAddonCategories(node));
        if (node.isTurbine() && node.isMultiblock() && StarTTurbineHelper.isStarTTurbine(node)) {
            if (!mutable.contains(AddonCategory.MULTIBLOCK_TRAIT)) {
                mutable.add(AddonCategory.MULTIBLOCK_TRAIT);
            }
        }
        if (node.hasThreading() && !mutable.contains(AddonCategory.THREADING)) {
            mutable.add(AddonCategory.THREADING);
        }

        return mutable;
    }

    @Override
    public void onAddonRemoved(RecipeNode node, MachineAddon addon) {
        super.onAddonRemoved(node, addon);
        if (node != null && addon != null && addon.getCategory().equals(AddonCategory.THREADING)) {
            GTThreadingHelix helix = GTThreadingHelix.fromId(addon.getId());
            if (helix != null && node.getThreadingConfig() != null) {
                node.getThreadingConfig().setHelixCount(helix, 0);
            }
        }
    }

    @Override
    public String formatAddonBadge(RecipeNode node, MachineAddon addon) {
        if (addon != null && addon.getCategory().equals(AddonCategory.THREADING)) {
            GTThreadingHelix helix = GTThreadingHelix.fromId(addon.getId());
            if (helix != null) {
                int count = 1;
                if (addon.getItemStackSample() != null && addon.getItemStackSample().getCount() > 0) {
                    count = addon.getItemStackSample().getCount();
                } else if (node != null && node.getThreadingConfig() != null) {
                    count = node.getThreadingConfig().getHelixCount(helix);
                }
                return count > 1 ? (count + "x " + helix.getTier().name()) : helix.getTier().name();
            }
        }
        return super.formatAddonBadge(node, addon);
    }

    @Override
    public String formatAddonSubtitle(RecipeNode node, MachineAddon addon) {
        if (addon != null && addon.getCategory().equals(AddonCategory.THREADING)) {
            return addon.getDescription();
        }
        return super.formatAddonSubtitle(node, addon);
    }

    public static void syncThreadingAddons(RecipeNode node) {
        if (node == null) return;
        NodeThreadingConfig cfg = node.getThreadingConfig();
        if (cfg == null) return;

        // Remove existing THREADING addons from the active list
        node.getAddons().removeIf(a -> a.getCategory().equals(AddonCategory.THREADING));

        // For each helix with count > 0, create a representative MachineAddon
        for (java.util.Map.Entry<GTThreadingHelix, Integer> entry : cfg.getHelixCounts().entrySet()) {
            GTThreadingHelix helix = entry.getKey();
            int count = entry.getValue();
            if (helix == null || count <= 0) continue;

            String id = helix.getId() != null ? helix.getId().toString() : "start_core:" + helix.name().toLowerCase();
            String name = count + "x " + helix.getEnglishName();

            StringBuilder desc = new StringBuilder();
            if (helix.getGeneral() > 0) desc.append("+").append(helix.getGeneral() * count).append(" Gen ");
            if (helix.getSpeed() > 0) desc.append("+").append(helix.getSpeed() * count).append(" Spd ");
            if (helix.getEfficiency() > 0) desc.append("+").append(helix.getEfficiency() * count).append(" Eff ");
            if (helix.getParallels() > 0) desc.append("+").append(helix.getParallels() * count).append(" Par ");
            if (helix.getThreading() > 0) desc.append("+").append(helix.getThreading() * count).append(" Thrd ");

            MachineAddon addon = new MachineAddon(id, name, AddonCategory.THREADING, desc.toString().trim(), helix.getId());
            addon.setParallelMultiplier(1);
            try {
                if (net.minecraftforge.registries.ForgeRegistries.ITEMS != null && helix.getId() != null) {
                    net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(helix.getId());
                    if (item != null) {
                        ItemStack s = new ItemStack(item, count);
                        addon.setItemStackSample(s);
                    }
                }
            } catch (Throwable ignored) {}
            addon.setDiscoverySource("Star Technology Threading Helix [" + helix.getTier().name() + "]");
            node.getAddons().add(addon);
        }
    }

    @Override
    public boolean supportsBoosterControl(RecipeNode node) {
        return StarTTurbineHelper.supportsBoost(node);
    }

    @Override
    public net.minecraft.network.chat.Component getBoosterDisplayComponent(RecipeNode node) {
        if (!StarTTurbineHelper.supportsBoost(node)) return null;
        com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel curModel = com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel.getModel(node);
        boolean lub = Boolean.TRUE.equals(node.getProperties().get(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.LUBRICANT_BOOST));
        boolean cool = Boolean.TRUE.equals(node.getProperties().get(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.COOLANT_BOOST));
        String activeFluid = curModel == com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel.NPT ? "Og" : "He3";
        String fullMult = curModel == com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel.NPT ? "3.0x" : "2.0x";
        String passMult = curModel == com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel.NPT ? "+50%" : "+25%";
        String noneMult = curModel == com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel.NPT ? "0.8x" : "0.9x";

        if (lub && cool) {
            return net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.config.turbine_boost_full", activeFluid, fullMult);
        } else if (lub) {
            return net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.config.turbine_boost_passive", passMult);
        } else {
            return net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.config.turbine_boost_none", noneMult);
        }
    }

    @Override
    public void cycleBooster(RecipeNode node, int direction) {
        StarTTurbineHelper.cycleTurbineBoost(node, direction);
    }

    @Override
    public void syncBoosterInputs(RecipeNode node) {
        StarTTurbineHelper.syncBoosterInputs(node);
    }

    @Override
    public int getBoosterBackgroundColor(RecipeNode node, boolean isHovered) {
        if (!StarTTurbineHelper.supportsBoost(node)) return super.getBoosterBackgroundColor(node, isHovered);
        boolean lub = Boolean.TRUE.equals(node.getProperties().get(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.LUBRICANT_BOOST));
        boolean cool = Boolean.TRUE.equals(node.getProperties().get(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.COOLANT_BOOST));
        if (lub && cool) return isHovered ? 0xFF284456 : 0xFF1C3240;
        if (lub) return isHovered ? 0xFF60501D : 0xFF4A3E16;
        return isHovered ? 0xFF3D2424 : 0xFF2A1818;
    }

    @Override
    public int getBoosterBorderColor(RecipeNode node, boolean isHovered) {
        if (!StarTTurbineHelper.supportsBoost(node)) return super.getBoosterBorderColor(node, isHovered);
        boolean lub = Boolean.TRUE.equals(node.getProperties().get(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.LUBRICANT_BOOST));
        boolean cool = Boolean.TRUE.equals(node.getProperties().get(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.COOLANT_BOOST));
        if (lub && cool) return 0xFF38BDF8;
        if (lub) return 0xFFFFD700;
        return isHovered ? 0xFFFF7777 : 0xFFAA4444;
    }

    @Override
    public int getBoosterTextColor(RecipeNode node, boolean isHovered) {
        if (!StarTTurbineHelper.supportsBoost(node)) return super.getBoosterTextColor(node, isHovered);
        boolean lub = Boolean.TRUE.equals(node.getProperties().get(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.LUBRICANT_BOOST));
        boolean cool = Boolean.TRUE.equals(node.getProperties().get(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.COOLANT_BOOST));
        if (lub && cool) return 0xFF38BDF8;
        if (lub) return 0xFFFFD700;
        return isHovered ? 0xFFFF7777 : 0xFFAA6666;
    }

    @Override
    public void buildBoosterTooltip(RecipeNode node, List<net.minecraft.network.chat.Component> tt) {
        if (!StarTTurbineHelper.supportsBoost(node) || tt == null) return;
        boolean lub = Boolean.TRUE.equals(node.getProperties().get(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.LUBRICANT_BOOST));
        boolean cool = Boolean.TRUE.equals(node.getProperties().get(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.COOLANT_BOOST));
        com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel curModel = com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel.getModel(node);
        String activeFluid = curModel == com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel.NPT ? "BEC-Og" : "SS-He3";
        String fullMult = curModel == com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel.NPT ? "3.0x" : "2.0x";
        String passMult = curModel == com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel.NPT ? "+50%" : "+25%";
        String noneMult = curModel == com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel.NPT ? "0.8x" : "0.9x";

        if (lub && cool) {
            tt.add(net.minecraft.network.chat.Component.literal("§b" + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.tooltip.turbine_boost_full_desc", activeFluid, fullMult).getString()));
            tt.add(net.minecraft.network.chat.Component.literal("§7• " + (curModel == com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel.NPT ? "BEC-Og (800 mB/hr)" : "SS-He3 (2,500 mB/hr)") + " + WS₂ (" + (curModel == com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel.NPT ? "2,500" : "1,000") + " mB/hr)"));
        } else if (lub) {
            tt.add(net.minecraft.network.chat.Component.literal("§e" + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.tooltip.turbine_boost_passive_desc", passMult).getString()));
            tt.add(net.minecraft.network.chat.Component.literal("§7• WS₂ (" + (curModel == com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel.NPT ? "2,500" : "1,000") + " mB/hr)"));
            tt.add(net.minecraft.network.chat.Component.literal("§a💡 " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.tooltip.turbine_boost_hint_full", activeFluid, fullMult).getString()));
        } else {
            tt.add(net.minecraft.network.chat.Component.literal("§c" + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.tooltip.turbine_boost_none_desc", noneMult).getString()));
            tt.add(net.minecraft.network.chat.Component.literal("§7• " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.tooltip.turbine_boost_none_warn").getString()));
        }
    }

    @Override
    public void onMachineIconChanged(RecipeNode node, ResourceLocation oldIcon, ResourceLocation newIcon) {
        super.onMachineIconChanged(node, oldIcon, newIcon);
        StarTTurbineHelper.syncBoosterInputs(node);
    }
}



