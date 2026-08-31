package com.gtceu.calcboard.api.type;

import com.google.gson.JsonObject;

import java.util.EnumMap;
import java.util.Map;

/**
 * Configuration model for Star Technology threading helices and stats assignment on multiblock nodes.
 */
public class NodeThreadingConfig {
    private final Map<GTThreadingHelix, Integer> helixCounts = new EnumMap<>(GTThreadingHelix.class);
    private int maxHelixCapacity = 0;
    private int assignedSpeed = 0;
    private int assignedEfficiency = 0;
    private int assignedParallels = 0;
    private int assignedThreading = 0;

    public NodeThreadingConfig() {
    }

    public int getMaxHelixCapacity() {
        return maxHelixCapacity;
    }

    public void setMaxHelixCapacity(int maxHelixCapacity) {
        this.maxHelixCapacity = Math.max(0, maxHelixCapacity);
    }

    public int getTotalHelixCount() {
        int sum = 0;
        for (int c : helixCounts.values()) {
            sum += c;
        }
        return sum;
    }

    public int getHelixCount(GTThreadingHelix helix) {
        return helixCounts.getOrDefault(helix, 0);
    }

    public void setHelixCount(GTThreadingHelix helix, int count) {
        if (count <= 0) {
            helixCounts.remove(helix);
        } else {
            helixCounts.put(helix, count);
        }
        clampAssignments();
    }

    public void addHelixCount(GTThreadingHelix helix, int delta) {
        int current = getHelixCount(helix);
        if (delta > 0 && maxHelixCapacity > 0) {
            int total = getTotalHelixCount();
            int allowed = Math.min(delta, maxHelixCapacity - total);
            if (allowed <= 0) return;
            setHelixCount(helix, current + allowed);
        } else {
            setHelixCount(helix, Math.max(0, current + delta));
        }
    }

    public Map<GTThreadingHelix, Integer> getHelixCounts() {
        return helixCounts;
    }

    public int getAssignedSpeed() {
        return assignedSpeed;
    }

    public void setAssignedSpeed(int assignedSpeed) {
        this.assignedSpeed = Math.max(0, assignedSpeed);
        clampAssignments();
    }

    public int getAssignedEfficiency() {
        return assignedEfficiency;
    }

    public void setAssignedEfficiency(int assignedEfficiency) {
        this.assignedEfficiency = Math.max(0, assignedEfficiency);
        clampAssignments();
    }

    public int getAssignedParallels() {
        return assignedParallels;
    }

    public void setAssignedParallels(int assignedParallels) {
        this.assignedParallels = Math.max(0, assignedParallels);
        clampAssignments();
    }

    public int getAssignedThreading() {
        return assignedThreading;
    }

    public void setAssignedThreading(int assignedThreading) {
        this.assignedThreading = Math.max(0, assignedThreading);
        clampAssignments();
    }

    public int getBaseGeneral() {
        int total = 0;
        for (Map.Entry<GTThreadingHelix, Integer> entry : helixCounts.entrySet()) {
            total += entry.getKey().getGeneral() * entry.getValue();
        }
        return total;
    }

    public int getBaseSpeed() {
        int total = 0;
        for (Map.Entry<GTThreadingHelix, Integer> entry : helixCounts.entrySet()) {
            total += entry.getKey().getSpeed() * entry.getValue();
        }
        return total;
    }

    public int getBaseEfficiency() {
        int total = 0;
        for (Map.Entry<GTThreadingHelix, Integer> entry : helixCounts.entrySet()) {
            total += entry.getKey().getEfficiency() * entry.getValue();
        }
        return total;
    }

    public int getBaseParallels() {
        int total = 0;
        for (Map.Entry<GTThreadingHelix, Integer> entry : helixCounts.entrySet()) {
            total += entry.getKey().getParallels() * entry.getValue();
        }
        return total;
    }

    public int getBaseThreading() {
        int total = 0;
        for (Map.Entry<GTThreadingHelix, Integer> entry : helixCounts.entrySet()) {
            total += entry.getKey().getThreading() * entry.getValue();
        }
        return total;
    }

    public int getTotalAssignedGeneral() {
        return assignedSpeed + assignedEfficiency + assignedParallels + assignedThreading;
    }

    public int getRemainingGeneral() {
        return Math.max(0, getBaseGeneral() - getTotalAssignedGeneral());
    }

    public int getTotalSpeed() {
        return getBaseSpeed() + assignedSpeed;
    }

    public int getTotalEfficiency() {
        return getBaseEfficiency() + assignedEfficiency;
    }

    public int getTotalParallels() {
        return getBaseParallels() + assignedParallels;
    }

    public int getTotalThreading() {
        return getBaseThreading() + assignedThreading;
    }

    private void clampAssignments() {
        int maxGen = getBaseGeneral();
        int currentAssigned = getTotalAssignedGeneral();
        if (currentAssigned > maxGen && currentAssigned > 0) {
            int over = currentAssigned - maxGen;
            if (assignedThreading >= over) {
                assignedThreading -= over;
            } else {
                over -= assignedThreading;
                assignedThreading = 0;
                if (assignedParallels >= over) {
                    assignedParallels -= over;
                } else {
                    over -= assignedParallels;
                    assignedParallels = 0;
                    if (assignedEfficiency >= over) {
                        assignedEfficiency -= over;
                    } else {
                        over -= assignedEfficiency;
                        assignedEfficiency = 0;
                        assignedSpeed = Math.max(0, assignedSpeed - over);
                    }
                }
            }
        }
    }

    public double calculateDurationMultiplier() {
        int totalSpeed = getTotalSpeed();
        if (totalSpeed <= 0) return 1.0;
        double points = (double) totalSpeed / 100.0;
        double n = (-1.0 + Math.sqrt(1.0 + 8.0 * points)) / 2.0;
        return Math.pow(0.5, n);
    }

    public double calculateEnergyMultiplier() {
        int totalEfficiency = getTotalEfficiency();
        if (totalEfficiency <= 0) return 1.0;
        return 30.0 / (30.0 + totalEfficiency);
    }

    public int getEffectiveParallels() {
        int totalParallels = getTotalParallels();
        return 1 + (totalParallels / 20);
    }

    public int getEffectiveThreads() {
        int totalThreading = getTotalThreading();
        return 1 + (totalThreading / 5);
    }

    public double getFinalDurationMultiplier() {
        return calculateDurationMultiplier() * Math.sqrt(getEffectiveParallels());
    }

    public double getFinalPowerMultiplier() {
        return calculateEnergyMultiplier();
    }

    private boolean active = false;

    public boolean isActive() {
        return active || !helixCounts.isEmpty() || getTotalAssignedGeneral() > 0;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void reset() {
        helixCounts.clear();
        assignedSpeed = 0;
        assignedEfficiency = 0;
        assignedParallels = 0;
        assignedThreading = 0;
        active = false;
    }

    public NodeThreadingConfig copy() {
        NodeThreadingConfig copy = new NodeThreadingConfig();
        copy.helixCounts.putAll(this.helixCounts);
        copy.assignedSpeed = this.assignedSpeed;
        copy.assignedEfficiency = this.assignedEfficiency;
        copy.assignedParallels = this.assignedParallels;
        copy.assignedThreading = this.assignedThreading;
        copy.active = this.active;
        return copy;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (active) json.addProperty("active", true);
        if (!helixCounts.isEmpty()) {
            JsonObject counts = new JsonObject();
            for (Map.Entry<GTThreadingHelix, Integer> entry : helixCounts.entrySet()) {
                counts.addProperty(entry.getKey().name(), entry.getValue());
            }
            json.add("helixes", counts);
        }
        if (assignedSpeed > 0) json.addProperty("speed", assignedSpeed);
        if (assignedEfficiency > 0) json.addProperty("efficiency", assignedEfficiency);
        if (assignedParallels > 0) json.addProperty("parallels", assignedParallels);
        if (assignedThreading > 0) json.addProperty("threading", assignedThreading);
        return json;
    }

    public void fromJson(JsonObject json) {
        reset();
        if (json == null) return;
        if (json.has("active")) active = json.get("active").getAsBoolean();
        if (json.has("helixes") && json.get("helixes").isJsonObject()) {
            JsonObject counts = json.getAsJsonObject("helixes");
            for (String key : counts.keySet()) {
                GTThreadingHelix helix = GTThreadingHelix.fromId(key);
                if (helix != null) {
                    int c = counts.get(key).getAsInt();
                    if (c > 0) helixCounts.put(helix, c);
                }
            }
        }
        if (json.has("speed")) assignedSpeed = json.get("speed").getAsInt();
        if (json.has("efficiency")) assignedEfficiency = json.get("efficiency").getAsInt();
        if (json.has("parallels")) assignedParallels = json.get("parallels").getAsInt();
        if (json.has("threading")) assignedThreading = json.get("threading").getAsInt();
        clampAssignments();
    }
}

