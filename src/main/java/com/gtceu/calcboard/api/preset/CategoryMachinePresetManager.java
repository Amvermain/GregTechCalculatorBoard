package com.gtceu.calcboard.api.preset;

import com.gtceu.calcboard.api.model.RecipeNode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Singleton manager for category-level default machine configuration presets.
 * Handles storage, retrieval, NBT serialization, and automatic application to new RecipeNodes.
 */
public class CategoryMachinePresetManager {

    private static final CategoryMachinePresetManager INSTANCE = new CategoryMachinePresetManager();

    private final Map<ResourceLocation, CategoryMachinePreset> presets = new LinkedHashMap<>();

    private CategoryMachinePresetManager() {}

    public static CategoryMachinePresetManager getInstance() {
        return INSTANCE;
    }

    public synchronized CategoryMachinePreset getPreset(ResourceLocation categoryId) {
        if (categoryId == null) return null;
        return presets.get(categoryId);
    }

    public synchronized boolean hasPreset(ResourceLocation categoryId) {
        if (categoryId == null) return false;
        return presets.containsKey(categoryId);
    }

    public synchronized void setPreset(CategoryMachinePreset preset) {
        if (preset == null || preset.getCategoryId() == null) return;
        presets.put(preset.getCategoryId(), preset);
    }

    public synchronized boolean removePreset(ResourceLocation categoryId) {
        if (categoryId == null) return false;
        return presets.remove(categoryId) != null;
    }

    public synchronized void clearAll() {
        presets.clear();
    }

    public synchronized Map<ResourceLocation, CategoryMachinePreset> getAllPresets() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(presets));
    }

    public synchronized boolean applyPresetIfPresent(RecipeNode node) {
        if (node == null) return false;
        ResourceLocation catId = node.getRecipeCategoryId();
        if (catId == null) {
            catId = node.getMachineIcon();
        }
        if (catId != null && presets.containsKey(catId)) {
            presets.get(catId).applyTo(node);
            return true;
        }
        return false;
    }

    public synchronized CompoundTag serializeNBT() {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        for (CategoryMachinePreset preset : presets.values()) {
            if (preset != null) {
                list.add(preset.serializeNBT());
            }
        }
        root.put("presets", list);
        return root;
    }

    public synchronized void deserializeNBT(CompoundTag root) {
        presets.clear();
        if (root == null || !root.contains("presets", Tag.TAG_LIST)) return;

        ListTag list = root.getList("presets", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CategoryMachinePreset preset = CategoryMachinePreset.deserializeNBT(list.getCompound(i));
            if (preset != null && preset.getCategoryId() != null) {
                presets.put(preset.getCategoryId(), preset);
            }
        }
    }
}
