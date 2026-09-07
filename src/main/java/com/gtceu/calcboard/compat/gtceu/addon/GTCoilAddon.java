package com.gtceu.calcboard.compat.gtceu.addon;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.compat.gtceu.helper.CoilHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * GTCEu Modern heating coil addon model.
 */
public class GTCoilAddon extends MachineAddon {

    public GTCoilAddon(String id, String name, String description, ResourceLocation itemIcon, CoilHelper.CoilStats stats) {
        super(id, name, Category.COIL, description, itemIcon);
        setModId("gtceu");
        if (stats != null) {
            setCoilTemperature(stats.temperature());
            setPyrolyseSpeedPercent(stats.pyrolyseSpeedPercent());
            setCrackingEnergyPercent(stats.crackingEnergyPercent());
            setChemicalSpeedPercent(stats.chemicalSpeedPercent());
            setChemicalEnergyPercent(stats.chemicalEnergyPercent());
            setSmelterParallel(stats.smelterParallel());
        }
    }

    public GTCoilAddon(String id, String name, String description, ResourceLocation itemIcon) {
        super(id, name, Category.COIL, description, itemIcon);
        setModId("gtceu");
    }

    @Override
    public MachineAddon copy() {
        GTCoilAddon cp = new GTCoilAddon(getId(), getName(), getDescription(), getItemIcon());
        cp.setModId(getModId());
        cp.setDurationMultiplier(getDurationMultiplier());
        cp.setEutMultiplier(getEutMultiplier());
        cp.setParallelMultiplier(getParallelMultiplier());
        cp.setDiscoverySource(getDiscoverySource());
        cp.setItemStackSample(getItemStackSample());
        cp.setCoilTemperature(getCoilTemperature());
        cp.setPyrolyseSpeedPercent(getPyrolyseSpeedPercent());
        cp.setCrackingEnergyPercent(getCrackingEnergyPercent());
        cp.setChemicalSpeedPercent(getChemicalSpeedPercent());
        cp.setChemicalEnergyPercent(getChemicalEnergyPercent());
        cp.setSmelterParallel(getSmelterParallel());
        return cp;
    }
}

