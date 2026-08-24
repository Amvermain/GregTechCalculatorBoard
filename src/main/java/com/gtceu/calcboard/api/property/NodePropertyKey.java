package com.gtceu.calcboard.api.property;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Type-safe, identifier-bound property key for {@link NodePropertyStore}.
 * Encapsulates property ID, class token, default value, and NBT serialization functions.
 *
 * @param <T> The value type of the property.
 */
public final class NodePropertyKey<T> {

    private final String id;
    private final Class<T> type;
    private final T defaultValue;
    private final BiConsumer<CompoundTag, T> serializer;
    private final Function<CompoundTag, T> deserializer;

    public NodePropertyKey(String id, Class<T> type, T defaultValue,
                           BiConsumer<CompoundTag, T> serializer,
                           Function<CompoundTag, T> deserializer) {
        this.id = Objects.requireNonNull(id, "Property key ID must not be null");
        this.type = Objects.requireNonNull(type, "Property key type must not be null");
        this.defaultValue = defaultValue;
        this.serializer = serializer != null ? serializer : (tag, val) -> {};
        this.deserializer = deserializer != null ? deserializer : tag -> defaultValue;
    }

    public String getId() {
        return id;
    }

    public Class<T> getType() {
        return type;
    }

    public T getDefaultValue() {
        return defaultValue;
    }

    public void writeToNBT(CompoundTag tag, T value) {
        if (tag == null || value == null) return;
        serializer.accept(tag, value);
    }

    public T readFromNBT(CompoundTag tag) {
        if (tag == null) return defaultValue;
        try {
            T val = deserializer.apply(tag);
            return val != null ? val : defaultValue;
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }

    public boolean isPresentInNBT(CompoundTag tag) {
        return tag != null && tag.contains(id);
    }

    // Factory methods for standard types
    public static NodePropertyKey<Integer> ofInt(String id, int defaultValue) {
        return new NodePropertyKey<>(
                id,
                Integer.class,
                defaultValue,
                (tag, val) -> tag.putInt(id, val),
                tag -> tag.contains(id, Tag.TAG_ANY_NUMERIC) ? tag.getInt(id) : defaultValue
        );
    }

    public static NodePropertyKey<Long> ofLong(String id, long defaultValue) {
        return new NodePropertyKey<>(
                id,
                Long.class,
                defaultValue,
                (tag, val) -> tag.putLong(id, val),
                tag -> {
                    if (!tag.contains(id, Tag.TAG_ANY_NUMERIC)) return defaultValue;
                    return tag.get(id) instanceof net.minecraft.nbt.NumericTag num ? num.getAsLong() : defaultValue;
                }
        );
    }

    public static NodePropertyKey<Double> ofDouble(String id, double defaultValue) {
        return new NodePropertyKey<>(
                id,
                Double.class,
                defaultValue,
                (tag, val) -> tag.putDouble(id, val),
                tag -> tag.contains(id, Tag.TAG_ANY_NUMERIC) ? tag.getDouble(id) : defaultValue
        );
    }

    public static NodePropertyKey<Boolean> ofBoolean(String id, boolean defaultValue) {
        return new NodePropertyKey<>(
                id,
                Boolean.class,
                defaultValue,
                (tag, val) -> tag.putBoolean(id, val),
                tag -> tag.contains(id, Tag.TAG_ANY_NUMERIC) ? tag.getBoolean(id) : defaultValue
        );
    }

    public static NodePropertyKey<String> ofString(String id, String defaultValue) {
        return new NodePropertyKey<>(
                id,
                String.class,
                defaultValue,
                (tag, val) -> {
                    if (val != null) tag.putString(id, val);
                },
                tag -> tag.contains(id, Tag.TAG_STRING) ? tag.getString(id) : defaultValue
        );
    }

    public static NodePropertyKey<ResourceLocation> ofResourceLocation(String id, ResourceLocation defaultValue) {
        return new NodePropertyKey<>(
                id,
                ResourceLocation.class,
                defaultValue,
                (tag, val) -> {
                    if (val != null) tag.putString(id, val.toString());
                },
                tag -> tag.contains(id, Tag.TAG_STRING) ? ResourceLocation.tryParse(tag.getString(id)) : defaultValue
        );
    }

    public static <E extends Enum<E>> NodePropertyKey<E> ofEnum(String id, Class<E> enumClass, E defaultValue) {
        return new NodePropertyKey<>(
                id,
                enumClass,
                defaultValue,
                (tag, val) -> {
                    if (val != null) tag.putString(id, val.name());
                },
                tag -> {
                    if (!tag.contains(id, Tag.TAG_STRING)) return defaultValue;
                    try {
                        return Enum.valueOf(enumClass, tag.getString(id));
                    } catch (Throwable ignored) {
                        return defaultValue;
                    }
                }
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodePropertyKey<?> that = (NodePropertyKey<?>) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "NodePropertyKey[" + id + " : " + type.getSimpleName() + "]";
    }
}
