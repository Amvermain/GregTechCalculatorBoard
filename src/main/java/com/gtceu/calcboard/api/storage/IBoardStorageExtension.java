package com.gtceu.calcboard.api.storage;

import net.minecraft.nbt.CompoundTag;

/**
 * Storage extension SPI interface allowing external integration modules (e.g. AE2)
 * to hook into BoardManager lifecycle and NBT persistence without introducing compile-time
 * or runtime hard couplings into the core layer.
 */
public interface IBoardStorageExtension {

    /**
     * Invoked when BoardManager resets workspace data to its default state.
     */
    void onReset();

    /**
     * Serializes extension-specific data into a CompoundTag.
     *
     * @return serializable CompoundTag containing extension data, or null if empty
     */
    CompoundTag serialize();

    /**
     * Deserializes extension-specific data from the persisted CompoundTag.
     *
     * @param tag the persisted CompoundTag
     */
    void deserialize(CompoundTag tag);
}
