package com.gtceu.calcboard.client.gui.layout;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.client.gui.layout.NodeLayoutBounds.PortBounds;
import com.gtceu.calcboard.client.gui.layout.NodeLayoutBounds.RectBounds;

import java.util.ArrayList;
import java.util.List;

public final class NodeLayoutCalculator {

    private static final int HEADER_HEIGHT = 20;
    private static final int REROUTE_SIZE = 32;

    private NodeLayoutCalculator() {}

    public static NodeLayoutBounds compute(RecipeNode node, boolean isSlimMode, int fontCountBoxW, boolean targetBatchEditing) {
        if (node.isReroute()) {
            return computeRerouteLayout(node, targetBatchEditing);
        }
        return computeStandardLayout(node, isSlimMode, fontCountBoxW);
    }

    private static NodeLayoutBounds computeRerouteLayout(RecipeNode node, boolean targetBatchEditing) {
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        boolean isFlipped = node.isFlipped();

        RectBounds cardBounds = new RectBounds(x, y, REROUTE_SIZE, REROUTE_SIZE);
        RectBounds targetBatchBadge = (node.hasTargetBatch() || targetBatchEditing)
                ? new RectBounds(x - 16, y + 18, 64, 28)
                : RectBounds.EMPTY;

        List<PortBounds> inputPorts = new ArrayList<>(1);
        List<PortBounds> outputPorts = new ArrayList<>(1);

        int inMinX = isFlipped ? (x + 22) : (x - 4);
        int outMinX = isFlipped ? (x - 4) : (x + 22);

        RectBounds inHitBox = new RectBounds(inMinX, y + 6, 14, 20);
        RectBounds inSlotBounds = new RectBounds(inMinX, y + 6, 14, 20);
        float inAnchorX = (float) (isFlipped ? (x + 32) : x);
        float inAnchorY = (float) (y + 16);
        inputPorts.add(new PortBounds(0, true, inHitBox, inSlotBounds, inAnchorX, inAnchorY));

        RectBounds outHitBox = new RectBounds(outMinX, y + 6, 14, 20);
        RectBounds outSlotBounds = new RectBounds(outMinX, y + 6, 14, 20);
        float outAnchorX = (float) (isFlipped ? x : (x + 32));
        float outAnchorY = (float) (y + 16);
        outputPorts.add(new PortBounds(0, false, outHitBox, outSlotBounds, outAnchorX, outAnchorY));

        return new NodeLayoutBounds(
                cardBounds,
                cardBounds,
                RectBounds.EMPTY,
                RectBounds.EMPTY,
                RectBounds.EMPTY,
                RectBounds.EMPTY,
                RectBounds.EMPTY,
                RectBounds.EMPTY,
                RectBounds.EMPTY,
                RectBounds.EMPTY,
                RectBounds.EMPTY,
                RectBounds.EMPTY,
                RectBounds.EMPTY,
                RectBounds.EMPTY,
                RectBounds.EMPTY,
                false,
                RectBounds.EMPTY,
                RectBounds.EMPTY,
                RectBounds.EMPTY,
                RectBounds.EMPTY,
                RectBounds.EMPTY,
                y,
                y,
                REROUTE_SIZE,
                REROUTE_SIZE,
                inputPorts,
                outputPorts,
                RectBounds.EMPTY,
                targetBatchBadge
        );
    }

    private static NodeLayoutBounds computeStandardLayout(RecipeNode node, boolean isSlimMode, int fontCountBoxW) {
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int cardW = node.getCardWidth();

        RectBounds headerBounds = new RectBounds(x, y, cardW - 74, HEADER_HEIGHT);
        RectBounds machineIconBounds = (node.isModule() || node.getMachineIcon() == null)
                ? RectBounds.EMPTY
                : new RectBounds(x + 2, y + 2, 18, 16);

        int titleX = x + (node.getMachineIcon() != null ? 22 : 6);
        RectBounds nameBounds = new RectBounds(titleX, y + 2, Math.max(10, cardW - 74 - (titleX - x)), 16);

        RectBounds switchBtnBounds = !node.isModule()
                ? new RectBounds(x + cardW - 72, y + 2, 16, 16)
                : RectBounds.EMPTY;
        RectBounds expandBtnBounds = node.isModule()
                ? new RectBounds(x + cardW - 72, y + 2, 16, 16)
                : RectBounds.EMPTY;
        RectBounds flipBtnBounds = new RectBounds(x + cardW - 54, y + 2, 16, 16);
        RectBounds targetBtnBounds = new RectBounds(x + cardW - 36, y + 2, 18, 16);
        RectBounds closeBtnBounds = new RectBounds(x + cardW - 18, y + 2, 16, 16);

        int ctrlY = y + HEADER_HEIGHT + 6;
        RectBounds countMinusBtnBounds = new RectBounds(x + 36, ctrlY, 14, 14);

        int countBoxW = Math.max(28, fontCountBoxW + 6);
        int countBoxX = x + 52;
        RectBounds countBoxBounds = new RectBounds(countBoxX, ctrlY, countBoxW, 14);

        int afterCountX = countBoxX + countBoxW + 2;
        RectBounds countPlusBtnBounds = new RectBounds(afterCountX, ctrlY, 14, 14);
        RectBounds countHalfBtnBounds = new RectBounds(afterCountX + 16, ctrlY, 16, 14);
        RectBounds countDoubleBtnBounds = new RectBounds(afterCountX + 34, ctrlY, 16, 14);

        RectBounds moduleBadgeBounds = RectBounds.EMPTY;
        if (node.isModule()) {
            int badgeW = 60;
            moduleBadgeBounds = new RectBounds(x + cardW - 6 - badgeW - 4, ctrlY, badgeW + 4, 16);
        }

        RectBounds addonTrayBounds = RectBounds.EMPTY;
        if (!node.isModule() && !isSlimMode && !node.getAddons().isEmpty()) {
            int trayW = Math.min(node.getAddons().size(), 3) * 16 + (node.getAddons().size() > 3 ? 16 : 0);
            addonTrayBounds = new RectBounds(x + cardW - 6 - trayW, ctrlY - 2, trayW, 18);
        }

        boolean hasRow2 = !isSlimMode && !node.isModule();
        RectBounds tierBtnBounds = RectBounds.EMPTY;
        RectBounds secondaryBtnBounds = RectBounds.EMPTY;
        RectBounds configBtnBounds = RectBounds.EMPTY;

        int row2Y = ctrlY + 18;
        if (hasRow2) {
            if (node.getEnergyType() == EnergyType.NONE) {
                tierBtnBounds = new RectBounds(x + 6, row2Y, cardW - 12, 14);
            } else {
                int tierW = 34;
                tierBtnBounds = new RectBounds(x + 6, row2Y, tierW, 14);
                int ocX = x + 6 + tierW + 4;
                int ocW = 74;
                secondaryBtnBounds = new RectBounds(ocX, row2Y, ocW, 14);
                int cfgX = ocX + ocW + 4;
                int cfgW = Math.max(30, (x + cardW - 6) - cfgX);
                configBtnBounds = new RectBounds(cfgX, row2Y, cfgW, 14);
            }
        }

        int separatorY = isSlimMode
                ? (ctrlY + 16)
                : ((node.isModule() ? (ctrlY + 18) : (row2Y + 18)) + 14);
        int contentStartY = separatorY + 4;

        List<Integer> visInputs = node.getVisibleInputIndices();
        List<Integer> visOutputs = node.getVisibleOutputIndices();
        int maxRows = Math.max(visInputs.size(), visOutputs.size());
        boolean isFlipped = node.isFlipped();
        boolean hasBoth = !node.getInputs().isEmpty() && !node.getOutputs().isEmpty();
        int slotW = hasBoth ? ((cardW / 2) - 4) : (cardW - 4);

        List<PortBounds> inputPorts = new ArrayList<>(visInputs.size());
        for (int r = 0; r < visInputs.size(); r++) {
            int origIdx = visInputs.get(r);
            int rowY = contentStartY + r * 18;
            int minX = isFlipped ? (x + cardW - 40) : x;
            int maxX = isFlipped ? (x + cardW + 4) : (x + 40);
            RectBounds hitBox = new RectBounds(minX, rowY - 3, maxX - minX, 22);
            int slotStartX = (!isFlipped || !hasBoth) ? (x + 2) : (x + (cardW / 2) + 2);
            RectBounds slotBounds = new RectBounds(slotStartX, rowY - 2, slotW, 18);
            float anchorX = (float) (x + (isFlipped ? cardW - 6 : 6));
            float anchorY = (float) (rowY + 8);
            inputPorts.add(new PortBounds(origIdx, true, hitBox, slotBounds, anchorX, anchorY));
        }

        List<PortBounds> outputPorts = new ArrayList<>(visOutputs.size());
        for (int r = 0; r < visOutputs.size(); r++) {
            int origIdx = visOutputs.get(r);
            int rowY = contentStartY + r * 18;
            int minX = isFlipped ? x : (x + cardW - 40);
            int maxX = isFlipped ? (x + 40) : (x + cardW + 4);
            RectBounds hitBox = new RectBounds(minX, rowY - 3, maxX - minX, 22);
            int slotStartX = (isFlipped || !hasBoth) ? (x + 2) : (x + (cardW / 2) + 2);
            RectBounds slotBounds = new RectBounds(slotStartX, rowY - 2, slotW, 18);
            float anchorX = (float) (x + (isFlipped ? 6 : cardW - 6));
            float anchorY = (float) (rowY + 8);
            outputPorts.add(new PortBounds(origIdx, false, hitBox, slotBounds, anchorX, anchorY));
        }

        int extraHidden = node.getTotalHiddenCount() > 0 ? 14 : 0;
        int contentEndY = contentStartY + Math.max(1, maxRows) * 18 + 8 + extraHidden;
        int autoHeight = contentEndY - y;
        int cardHeight = Math.max(autoHeight, node.getCardHeight());

        RectBounds cardBounds = new RectBounds(x, y, cardW, cardHeight);
        RectBounds resizeHandleBounds = new RectBounds(x + cardW - 12, y + cardHeight - 12, 12, 12);
        RectBounds hiddenPortsBadgeBounds = node.getTotalHiddenCount() > 0
                ? new RectBounds(x + cardW - 120, y + cardHeight - 16, 116, 16)
                : RectBounds.EMPTY;

        return new NodeLayoutBounds(
                cardBounds,
                headerBounds,
                machineIconBounds,
                nameBounds,
                countMinusBtnBounds,
                countBoxBounds,
                countPlusBtnBounds,
                countHalfBtnBounds,
                countDoubleBtnBounds,
                switchBtnBounds,
                expandBtnBounds,
                flipBtnBounds,
                targetBtnBounds,
                closeBtnBounds,
                resizeHandleBounds,
                hasRow2,
                tierBtnBounds,
                secondaryBtnBounds,
                configBtnBounds,
                addonTrayBounds,
                moduleBadgeBounds,
                separatorY,
                contentStartY,
                autoHeight,
                cardHeight,
                inputPorts,
                outputPorts,
                hiddenPortsBadgeBounds,
                RectBounds.EMPTY
        );
    }
}
