package com.gtceu.calcboard.client.gui.dialog.config;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTCoilAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTReflectorAddon;

import java.util.Comparator;
import java.util.Objects;

public class AddonCatalogComparator implements Comparator<MachineAddon> {

    public static final AddonCatalogComparator INSTANCE = new AddonCatalogComparator();

    @Override
    public int compare(MachineAddon a, MachineAddon b) {
        if (a == b) return 0;
        if (a == null) return 1;
        if (b == null) return -1;

        int resetCmp = compareResetCards(a, b);
        if (resetCmp != 0) return resetCmp;

        int categoryCmp = compareCategories(a.getCategory(), b.getCategory());
        if (categoryCmp != 0) return categoryCmp;

        int specCmp = compareCategorySpecific(a.getCategory(), a, b);
        if (specCmp != 0) return specCmp;

        int parallelCmp = Integer.compare(a.getParallelMultiplier(), b.getParallelMultiplier());
        if (parallelCmp != 0) return parallelCmp;

        int nameCmp = compareNames(a.getName(), b.getName());
        if (nameCmp != 0) return nameCmp;

        return Objects.toString(a.getId(), "").compareTo(Objects.toString(b.getId(), ""));
    }

    private static int compareResetCards(MachineAddon a, MachineAddon b) {
        boolean aReset = isResetCard(a);
        boolean bReset = isResetCard(b);
        if (aReset == bReset) return 0;
        return aReset ? -1 : 1;
    }

    private static boolean isResetCard(MachineAddon addon) {
        String id = addon.getId();
        return "gtceu:rotor_standard".equals(id) || "gtceu:reflector_none".equals(id);
    }

    private static int compareCategories(AddonCategory catA, AddonCategory catB) {
        AddonCategory nonNullA = catA != null ? catA : AddonCategory.CUSTOM;
        AddonCategory nonNullB = catB != null ? catB : AddonCategory.CUSTOM;
        if (nonNullA.equals(nonNullB)) return 0;

        int prioCmp = Integer.compare(nonNullB.getPriority(), nonNullA.getPriority());
        if (prioCmp != 0) return prioCmp;

        return nonNullA.getId().compareTo(nonNullB.getId());
    }

    private static int compareCategorySpecific(AddonCategory category, MachineAddon a, MachineAddon b) {
        if (category == AddonCategory.REFLECTOR) {
            return compareReflectors(a, b);
        }
        if (category == AddonCategory.COIL) {
            return compareCoils(a, b);
        }

        int tierA = extractHatchTier(a);
        int tierB = extractHatchTier(b);
        if (tierA != tierB) {
            return Integer.compare(tierA, tierB);
        }

        return 0;
    }

    private static int compareReflectors(MachineAddon a, MachineAddon b) {
        int tA = (a instanceof GTReflectorAddon ra) ? ra.getReflectorTier() : 0;
        int tB = (b instanceof GTReflectorAddon rb) ? rb.getReflectorTier() : 0;
        return Integer.compare(tA, tB);
    }

    private static int compareCoils(MachineAddon a, MachineAddon b) {
        int tempA = (a instanceof GTCoilAddon ca) ? ca.getCoilTemperature() : 0;
        int tempB = (b instanceof GTCoilAddon cb) ? cb.getCoilTemperature() : 0;
        return Integer.compare(tempA, tempB);
    }

    private static int extractHatchTier(MachineAddon addon) {
        if (addon instanceof GTHatchAddon ha && ha.getTier() != null) {
            return ha.getTier().ordinal();
        }
        if (addon instanceof GTEnergyHatchAddon ea && ea.getTier() != null) {
            return ea.getTier().ordinal();
        }
        return -1;
    }

    private static int compareNames(String nameA, String nameB) {
        String nonNullA = Objects.toString(nameA, "");
        String nonNullB = Objects.toString(nameB, "");
        return nonNullA.compareToIgnoreCase(nonNullB);
    }
}
