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
        super.discoverAddons(collector, recipeOutputStacks);
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
}



