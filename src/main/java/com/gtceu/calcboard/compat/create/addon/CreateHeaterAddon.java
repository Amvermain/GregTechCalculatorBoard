package com.gtceu.calcboard.compat.create.addon;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;
import net.minecraft.resources.ResourceLocation;

public class CreateHeaterAddon extends MachineAddon {

    private final int heatLevel;

    public CreateHeaterAddon(String id, String name, String description, ResourceLocation itemIcon, int heatLevel) {
        super(id, name, AddonCategory.HEATER, description, itemIcon);
        setModId("create");
        this.heatLevel = heatLevel;
    }

    public int getHeatLevel() {
        return heatLevel;
    }

    public boolean isSuperheated() {
        return heatLevel >= 2;
    }

    @Override
    public MachineAddon forMachine(RecipeNode node) {
        CreateHeaterAddon cp = new CreateHeaterAddon(getId(), getName(), getRawDescription(), getItemIcon(), heatLevel);
        cp.setModId(getModId());
        cp.setItemStackSample(getItemStackSample() != null ? getItemStackSample().copy() : null);
        cp.setDiscoverySource(getDiscoverySource());
        return cp;
    }

    @Override
    public MachineAddon copy() {
        CreateHeaterAddon cp = new CreateHeaterAddon(getId(), getName(), getRawDescription(), getItemIcon(), heatLevel);
        cp.setModId(getModId());
        cp.setItemStackSample(getItemStackSample() != null ? getItemStackSample().copy() : null);
        cp.setDiscoverySource(getDiscoverySource());
        cp.setDurationMultiplier(getDurationMultiplier());
        cp.setEutMultiplier(getEutMultiplier());
        cp.setParallelMultiplier(getParallelMultiplier());
        cp.setPowerConstant(isPowerConstant());
        return cp;
    }
}
