package com.gtceu.calcboard.integration.ae2.model;

import net.minecraft.nbt.CompoundTag;

import java.util.Objects;

/**
 * Metadata entry capturing the binding relationship between an AE2 pattern and a calculator board page.
 */
public record PatternBindingEntry(
        PatternId patternId,
        String pageId,
        String pageName,
        long boundTimestamp
) {
    public PatternBindingEntry {
        Objects.requireNonNull(patternId, "PatternId cannot be null");
        Objects.requireNonNull(pageId, "PageId cannot be null");
        pageName = pageName != null ? pageName : "Page";
    }

    public static PatternBindingEntry of(PatternId patternId, String pageId, String pageName) {
        return new PatternBindingEntry(patternId, pageId, pageName, System.currentTimeMillis());
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("patternId", patternId.serializeNBT());
        tag.putString("pageId", pageId);
        tag.putString("pageName", pageName);
        tag.putLong("timestamp", boundTimestamp);
        return tag;
    }

    public static PatternBindingEntry deserializeNBT(CompoundTag tag) {
        if (tag == null) return null;
        PatternId patternId = PatternId.deserializeNBT(tag.getCompound("patternId"));
        String pageId = tag.getString("pageId");
        String pageName = tag.getString("pageName");
        long timestamp = tag.getLong("timestamp");
        return new PatternBindingEntry(patternId, pageId, pageName, timestamp);
    }
}
