package com.gtceu.calcboard.compat.gtceu.model.mcf;

import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * Enumeration of Star Technology modular combustion and rocket module multiblock archetypes.
 */
public enum MCFModuleType {
    UCM("UCM (LuV)", GTCombustionHelper.START_T1_COMBUSTION, GTVoltageTier.LuV, 1, 5,
            GTCombustionHelper.LUBRICANT, 100.0, GTCombustionHelper.WHITE_FUMING_NITRIC_ACID, 324.0),
    SCM("SCM (ZPM)", GTCombustionHelper.START_T2_COMBUSTION, GTVoltageTier.ZPM, 1, 6,
            GTCombustionHelper.LUBRICANT, 200.0, GTCombustionHelper.RED_FUMING_NITRIC_ACID, 432.0),
    SRM("SRM (UV)", GTCombustionHelper.START_T3_ROCKET, GTVoltageTier.UV, 2, 8,
            GTCombustionHelper.TUNGSTEN_DISULFIDE, 200.0, GTCombustionHelper.DIOXYGEN_DIFLUORIDE, 756.0),
    NRM("NRM (UEV)", GTCombustionHelper.START_T4_ROCKET, GTVoltageTier.UEV, 2, 12,
            GTCombustionHelper.TUNGSTEN_DISULFIDE, 400.0, GTCombustionHelper.FERROCENIUM_SUPEROXIDE, 864.0);

    private final String displayName;
    private final ResourceLocation machineId;
    private final GTVoltageTier tier;
    private final int baseAmps;
    private final int boostAmps;
    private final ResourceLocation lubricantFluid;
    private final double lubricantMbPerPeriod;
    private final ResourceLocation oxidizerFluid;
    private final double oxidizerMbPerPeriod;

    MCFModuleType(String displayName, ResourceLocation machineId, GTVoltageTier tier, int baseAmps, int boostAmps,
                  ResourceLocation lubricantFluid, double lubricantMbPerPeriod,
                  ResourceLocation oxidizerFluid, double oxidizerMbPerPeriod) {
        this.displayName = displayName;
        this.machineId = machineId;
        this.tier = tier;
        this.baseAmps = baseAmps;
        this.boostAmps = boostAmps;
        this.lubricantFluid = lubricantFluid;
        this.lubricantMbPerPeriod = lubricantMbPerPeriod;
        this.oxidizerFluid = oxidizerFluid;
        this.oxidizerMbPerPeriod = oxidizerMbPerPeriod;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ResourceLocation getMachineId() {
        return machineId;
    }

    public GTVoltageTier getTier() {
        return tier;
    }

    public int getBaseAmps() {
        return baseAmps;
    }

    public int getBoostAmps() {
        return boostAmps;
    }

    public ResourceLocation getLubricantFluid() {
        return lubricantFluid;
    }

    public double getLubricantMbPerPeriod() {
        return lubricantMbPerPeriod;
    }

    public ResourceLocation getOxidizerFluid() {
        return oxidizerFluid;
    }

    public double getOxidizerMbPerPeriod() {
        return oxidizerMbPerPeriod;
    }

    public MCFModuleType next() {
        MCFModuleType[] vals = values();
        return vals[(ordinal() + 1) % vals.length];
    }

    public static MCFModuleType fromMachineId(ResourceLocation id) {
        if (id == null) return UCM;
        for (MCFModuleType t : values()) {
            if (t.machineId.equals(id)) return t;
        }
        return UCM;
    }

    public static MCFModuleType fromNameOrDefault(String name) {
        if (name == null || name.isBlank()) return UCM;
        for (MCFModuleType t : values()) {
            if (t.name().equalsIgnoreCase(name) || t.displayName.equalsIgnoreCase(name)) {
                return t;
            }
        }
        return UCM;
    }
}
