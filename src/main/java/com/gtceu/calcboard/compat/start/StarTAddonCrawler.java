package com.gtceu.calcboard.compat.start;

import com.gtceu.calcboard.api.MachineAddon;
import com.gtceu.calcboard.compat.gtceu.helper.ParallelHelper;
import com.gtceu.calcboard.compat.gtceu.helper.ReflectorHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

public class StarTAddonCrawler {

    public static void discoverAddons(List<MachineAddon> collector, List<ItemStack> recipeOutputStacks) {
        addBuiltinStarTTraits(collector);

        if (ForgeRegistries.ITEMS != null) {
            for (Item item : ForgeRegistries.ITEMS) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                if (id == null) continue;
                String ns = id.getNamespace();
                if (!ns.equals("start_core") && !ns.equals("gtceu_start")) continue;

                ItemStack stack = new ItemStack(item);

                MachineAddon parallel = ParallelHelper.parseParallelHatch(stack, id);
                if (parallel != null && !containsAddonId(collector, parallel.getId())) {
                    collector.add(parallel);
                    continue;
                }

                MachineAddon reflector = ReflectorHelper.parseReflectorItem(stack, id);
                if (reflector != null && !containsAddonId(collector, reflector.getId())) {
                    collector.add(reflector);
                }
            }
        }
    }

    public static void addBuiltinStarTTraits(List<MachineAddon> list) {
        // SPT Lubricant Boosting (+25% EU/t output)
        MachineAddon sptLubricant = new MachineAddon(
                "gtceu:spt_lubricant_boosting",
                "SPT Lubricant Boosting [+25% EU/t]",
                MachineAddon.Category.MULTIBLOCK_TRAIT,
                "+25% EU/t output (Consumes 1,000 B/h Tungsten Disulfide)",
                ResourceLocation.tryParse("gtceu:supreme_plasma_turbine")
        );
        sptLubricant.setEutMultiplier(1.25);
        sptLubricant.setDiscoverySource("Supreme Plasma Turbine Lubricant Boosting (+25% EU/t)");
        if (!containsAddonId(list, sptLubricant.getId())) list.add(sptLubricant);

        // SPT Coolant Boosting (+75% EU/t output)
        MachineAddon sptCoolant = new MachineAddon(
                "gtceu:spt_coolant_boosting",
                "SPT Coolant Boosting [+75% EU/t]",
                MachineAddon.Category.MULTIBLOCK_TRAIT,
                "+75% EU/t output (Consumes 2,500 B/h Superstate Helium 3)",
                ResourceLocation.tryParse("gtceu:supreme_plasma_turbine")
        );
        sptCoolant.setEutMultiplier(1.75);
        sptCoolant.setDiscoverySource("Supreme Plasma Turbine Coolant Boosting (+75% EU/t)");
        if (!containsAddonId(list, sptCoolant.getId())) list.add(sptCoolant);

        // NPT Lubricant Boosting (+50% EU/t output)
        MachineAddon nptLubricant = new MachineAddon(
                "gtceu:npt_lubricant_boosting",
                "NPT Lubricant Boosting [+50% EU/t]",
                MachineAddon.Category.MULTIBLOCK_TRAIT,
                "+50% EU/t output (Consumes 2,500 B/h Tungsten Disulfide)",
                ResourceLocation.tryParse("gtceu:nyinsane_plasma_turbine")
        );
        nptLubricant.setEutMultiplier(1.50);
        nptLubricant.setDiscoverySource("Nyinsane Plasma Turbine Lubricant Boosting (+50% EU/t)");
        if (!containsAddonId(list, nptLubricant.getId())) list.add(nptLubricant);

        // NPT Coolant Boosting (+150% EU/t output)
        MachineAddon nptCoolant = new MachineAddon(
                "gtceu:npt_coolant_boosting",
                "NPT Coolant Boosting [+150% EU/t]",
                MachineAddon.Category.MULTIBLOCK_TRAIT,
                "+150% EU/t output (Consumes 800 B/h Oganesson BEC)",
                ResourceLocation.tryParse("gtceu:nyinsane_plasma_turbine")
        );
        nptCoolant.setEutMultiplier(2.50);
        nptCoolant.setDiscoverySource("Nyinsane Plasma Turbine Coolant Boosting (+150% EU/t)");
        if (!containsAddonId(list, nptCoolant.getId())) list.add(nptCoolant);
    }

    private static boolean containsAddonId(List<MachineAddon> list, String id) {
        if (id == null) return false;
        for (MachineAddon a : list) {
            if (a.getId().equals(id)) return true;
        }
        return false;
    }
}
