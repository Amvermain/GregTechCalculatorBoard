package com.gtceu.calcboard.client.gui.dialog.config;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MachineAddonCatalog;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.spi.IModAdapter;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Filter and query matching helper for the addon catalog.
 */
public final class AddonCatalogFilterHelper {

    private AddonCatalogFilterHelper() {}

    public static List<MachineAddon> filterCatalog(RecipeNode node, String searchQuery, MachineConfigDialog dialog) {
        List<MachineAddon> list = MachineAddonCatalog.getInstance().getAllAddons();
        String q = searchQuery != null ? searchQuery.toLowerCase().trim() : "";
        String qClean = q.replace('_', ' ').trim();
        String qUnder = q.replace(' ', '_').trim();

        List<MachineAddon> filtered = new ArrayList<>();
        List<AddonCategory> rel = (dialog.getSelectedCategory() == null) ? MachineAddon.getRelevantCategories(node) : null;

        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        if (adapter != null) {
            List<MachineAddon> resetCards = adapter.getResetAddonCards(node);
            for (MachineAddon resetCard : resetCards) {
                if (!adapter.isAddonCompatible(node, resetCard)) continue;
                if (dialog.getSelectedCategory() != null && !resetCard.getCategory().equals(dialog.getSelectedCategory())) continue;
                if (dialog.getSelectedCategory() == null && rel != null && !rel.contains(resetCard.getCategory())) continue;
                String resetBtnText = Component.translatable("gui.gtcalcboard.rotor.reset_btn").getString().toLowerCase();
                if (q.isEmpty() || resetCard.getName().toLowerCase().contains(q) || "reset".contains(q) || "standard".contains(q) || resetBtnText.contains(q) || "none".contains(q)) {
                    filtered.add(resetCard);
                }
            }
        }

        for (MachineAddon addon : list) {
            if (addon == null) continue;
            if (dialog.getSelectedCategory() != null && !addon.getCategory().equals(dialog.getSelectedCategory())) continue;
            if (dialog.getSelectedCategory() == null && rel != null && !rel.contains(addon.getCategory())) continue;
            if (!addon.isCompatibleWith(node)) continue;
            if (!q.isEmpty()) {
                String n = addon.getName().toLowerCase();
                String d = addon.getDescription() != null ? addon.getDescription().toLowerCase() : "";
                String idStr = addon.getId() != null ? addon.getId().toString().toLowerCase() : "";
                if (!n.contains(q) && !n.contains(qClean) && !n.contains(qUnder)
                        && !d.contains(q) && !d.contains(qClean) && !d.contains(qUnder)
                        && !idStr.contains(q) && !idStr.contains(qClean) && !idStr.contains(qUnder)) {
                    continue;
                }
            }
            filtered.add(addon);
        }
        filtered.sort(AddonCatalogComparator.INSTANCE);
        return filtered;
    }
}
