package com.gtceu.calcboard.api.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages port visibility (hidden ports) and voided output states for a RecipeNode.
 */
public class NodePortVisibility {

    private final Set<Integer> hiddenInputIndices = new LinkedHashSet<>();
    private final Set<Integer> hiddenOutputIndices = new LinkedHashSet<>();
    private final Set<Integer> voidedOutputIndices = new LinkedHashSet<>();

    public boolean isInputPortHidden(int index) {
        return hiddenInputIndices.contains(index);
    }

    public boolean isOutputPortHidden(int index) {
        return hiddenOutputIndices.contains(index);
    }

    public void hideInputPort(int index, int maxInputs) {
        if (index >= 0 && index < maxInputs) {
            hiddenInputIndices.add(index);
        }
    }

    public void unhideInputPort(int index) {
        hiddenInputIndices.remove(index);
    }

    public void hideOutputPort(int index, int maxOutputs) {
        if (index >= 0 && index < maxOutputs) {
            hiddenOutputIndices.add(index);
        }
    }

    public void unhideOutputPort(int index) {
        hiddenOutputIndices.remove(index);
    }

    public void unhideAllPorts() {
        hiddenInputIndices.clear();
        hiddenOutputIndices.clear();
    }

    public boolean isOutputPortVoided(int index) {
        return voidedOutputIndices.contains(index);
    }

    public void setOutputPortVoided(int index, boolean voided, int maxOutputs) {
        if (index >= 0 && index < maxOutputs) {
            if (voided) {
                voidedOutputIndices.add(index);
            } else {
                voidedOutputIndices.remove(index);
            }
        }
    }

    public void clearVoidedOutputPorts() {
        voidedOutputIndices.clear();
    }

    public Set<Integer> getVoidedOutputIndices() {
        return Collections.unmodifiableSet(voidedOutputIndices);
    }

    public int getVoidedOutputCount() {
        return voidedOutputIndices.size();
    }

    public Set<Integer> getHiddenInputIndices() {
        return Collections.unmodifiableSet(hiddenInputIndices);
    }

    public Set<Integer> getHiddenOutputIndices() {
        return Collections.unmodifiableSet(hiddenOutputIndices);
    }

    public int getHiddenInputCount() {
        return hiddenInputIndices.size();
    }

    public int getHiddenOutputCount() {
        return hiddenOutputIndices.size();
    }

    public int getTotalHiddenCount() {
        return hiddenInputIndices.size() + hiddenOutputIndices.size();
    }

    public List<Integer> getVisibleInputIndices(int totalInputs) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < totalInputs; i++) {
            if (!hiddenInputIndices.contains(i)) {
                list.add(i);
            }
        }
        return list;
    }

    public List<Integer> getVisibleOutputIndices(int totalOutputs) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < totalOutputs; i++) {
            if (!hiddenOutputIndices.contains(i)) {
                list.add(i);
            }
        }
        return list;
    }
}
