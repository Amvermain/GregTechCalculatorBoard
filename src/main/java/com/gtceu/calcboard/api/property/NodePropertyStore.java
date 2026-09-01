package com.gtceu.calcboard.api.property;

import com.gtceu.calcboard.api.model.RecipeNode;

import net.minecraft.nbt.CompoundTag;

import java.util.*;

/**
 * Type-safe, lightweight dynamic property store for {@link com.gtceu.calcboard.api.model.RecipeNode}.
 * Holds special machine metadata (temperatures, fusion ignition, RPM, cleanroom, etc.)
 * and automates NBT serialization without bloating the core node class.
 */
public class NodePropertyStore {

    private final Map<NodePropertyKey<?>, Object> values = new LinkedHashMap<>();

    public NodePropertyStore() {}

    /**
     * Retrieves the property value, returning the key's default value if absent.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(NodePropertyKey<T> key) {
        if (key == null) return null;
        Object val = values.get(key);
        if (val == null) {
            return key.getDefaultValue();
        }
        return (T) val;
    }

    /**
     * Retrieves the property value, or {@code null} if not explicitly set.
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrNull(NodePropertyKey<T> key) {
        if (key == null) return null;
        return (T) values.get(key);
    }

    /**
     * Sets the property value. If the value is equal to the default or null, it removes the entry.
     */
    public <T> void set(NodePropertyKey<T> key, T value) {
        if (key == null) return;
        if (value == null || Objects.equals(value, key.getDefaultValue())) {
            values.remove(key);
        } else {
            values.put(key, value);
        }
    }

    /**
     * Checks whether an explicit non-default value is present for the given key.
     */
    public <T> boolean has(NodePropertyKey<T> key) {
        if (key == null) return false;
        return values.containsKey(key);
    }

    public boolean hasById(String keyId) {
        NodePropertyKey<?> key = NodeProperties.getById(keyId);
        return key != null && has(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getById(String keyId, T defaultValue) {
        NodePropertyKey<?> key = NodeProperties.getById(keyId);
        if (key == null) return defaultValue;
        Object val = values.get(key);
        return val != null ? (T) val : defaultValue;
    }

    @SuppressWarnings("unchecked")
    public <T> void setById(String keyId, T value) {
        NodePropertyKey<T> key = (NodePropertyKey<T>) NodeProperties.getById(keyId);
        if (key != null) {
            set(key, value);
        }
    }

    /**
     * Removes the property entry.
     */
    public <T> void remove(NodePropertyKey<T> key) {
        if (key != null) {
            values.remove(key);
        }
    }

    /**
     * Clears all properties.
     */
    public void clear() {
        values.clear();
    }

    public void copyFrom(NodePropertyStore other) {
        values.clear();
        if (other != null) {
            values.putAll(other.values);
        }
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public int size() {
        return values.size();
    }

    public Map<NodePropertyKey<?>, Object> getAll() {
        return Collections.unmodifiableMap(values);
    }

    /**
     * Serializes all non-default property values into a {@link CompoundTag}.
     */
    @SuppressWarnings("unchecked")
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<NodePropertyKey<?>, Object> entry : values.entrySet()) {
            NodePropertyKey<Object> key = (NodePropertyKey<Object>) entry.getKey();
            Object val = entry.getValue();
            if (val != null) {
                key.writeToNBT(tag, val);
            }
        }
        return tag;
    }

    /**
     * Deserializes property values from a {@link CompoundTag}.
     */
    public void deserializeNBT(CompoundTag tag) {
        values.clear();
        if (tag == null || tag.isEmpty()) return;

        for (NodePropertyKey<?> key : NodeProperties.getAll().values()) {
            if (key.isPresentInNBT(tag)) {
                readAndPutKey(tag, key);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void readAndPutKey(CompoundTag tag, NodePropertyKey<T> key) {
        T val = key.readFromNBT(tag);
        if (val != null && !Objects.equals(val, key.getDefaultValue())) {
            values.put(key, val);
        }
    }

    /**
     * Deep/shallow clone of the property store.
     */
    public NodePropertyStore copy() {
        NodePropertyStore copy = new NodePropertyStore();
        copy.values.putAll(this.values);
        return copy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodePropertyStore that = (NodePropertyStore) o;
        return Objects.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(values);
    }

    @Override
    public String toString() {
        return "NodePropertyStore" + values;
    }
}


