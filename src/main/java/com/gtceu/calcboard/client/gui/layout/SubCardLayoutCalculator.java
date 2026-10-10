package com.gtceu.calcboard.client.gui.layout;

import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.layout.NodeLayoutBounds.RectBounds;
import com.gtceu.calcboard.client.gui.layout.SubCardLayoutBounds.SubCardPortBounds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Deterministic layout calculator for embedded sub-cards in shared machine pools.
 * Eliminates heuristic offset guessing by calculating exact geometry in a single pass.
 */
public final class SubCardLayoutCalculator {

    public static final int HEADER_HEIGHT = 18;
    public static final int PORT_ROW_HEIGHT = 16;
    public static final int BTN_SIZE = 12;
    public static final int PIN_WIDTH = 6;
    public static final int PIN_HEIGHT = 6;

    private SubCardLayoutCalculator() {}

    public static SubCardLayoutBounds compute(CanvasGroupFrame frame, RecipeNode subNode) {
        if (subNode == null) {
            return createEmptyBounds();
        }

        int cardX = (int) subNode.getPosX();
        int cardY = (int) subNode.getPosY();
        int cardW = subNode.getCardWidth() > 0
                ? subNode.getCardWidth()
                : (int) Math.max(200.0, (frame != null ? frame.getWidth() - 12.0 : 200.0));
        int cardH = subNode.getCardHeight() > 0 ? subNode.getCardHeight() : 56;

        RectBounds cardBounds = new RectBounds(cardX, cardY, cardW, cardH);
        RectBounds headerBounds = new RectBounds(cardX, cardY, cardW, HEADER_HEIGHT);

        int curX = cardX + cardW - 16;
        RectBounds deleteBtnBounds = new RectBounds(curX, cardY + 2, BTN_SIZE, BTN_SIZE);

        curX -= 14;
        RectBounds plusBtnBounds = new RectBounds(curX, cardY + 2, BTN_SIZE, BTN_SIZE);

        int dutyW = 42;
        curX -= (dutyW + 4);
        RectBounds dutyTextBounds = new RectBounds(curX + 2, cardY + 4, dutyW, 10);

        curX -= 14;
        RectBounds minusBtnBounds = new RectBounds(curX, cardY + 2, BTN_SIZE, BTN_SIZE);

        RectBounds nameBounds = new RectBounds(cardX + 6, cardY + 4, Math.max(20, curX - cardX - 10), 10);

        boolean flipped = frame != null && frame.isFlipped();
        List<SubCardPortBounds> inputPorts = computeInputPortBounds(subNode, cardX, cardY, cardW, flipped);
        List<SubCardPortBounds> outputPorts = computeOutputPortBounds(subNode, cardX, cardY, cardW, flipped);

        int maxPorts = Math.max(subNode.getInputs().size(), subNode.getOutputs().size());
        int midX = cardX + cardW / 2 - 6;
        int midY = cardY + HEADER_HEIGHT + (maxPorts > 0 ? (maxPorts * PORT_ROW_HEIGHT) / 2 - 4 : 0);
        RectBounds centerArrowBounds = new RectBounds(midX, midY, 12, 10);

        return new SubCardLayoutBounds(
                cardBounds,
                headerBounds,
                nameBounds,
                deleteBtnBounds,
                plusBtnBounds,
                dutyTextBounds,
                minusBtnBounds,
                inputPorts,
                outputPorts,
                centerArrowBounds
        );
    }

    private static List<SubCardPortBounds> computeInputPortBounds(
            RecipeNode subNode,
            int cardX,
            int cardY,
            int cardW,
            boolean flipped
    ) {
        int count = subNode.getInputs().size();
        if (count == 0) return Collections.emptyList();

        List<SubCardPortBounds> ports = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int portY = cardY + HEADER_HEIGHT + i * PORT_ROW_HEIGHT;
            boolean onRight = flipped;
            ports.add(createPortBounds(i, true, cardX, cardW, portY, onRight));
        }
        return Collections.unmodifiableList(ports);
    }

    private static List<SubCardPortBounds> computeOutputPortBounds(
            RecipeNode subNode,
            int cardX,
            int cardY,
            int cardW,
            boolean flipped
    ) {
        int count = subNode.getOutputs().size();
        if (count == 0) return Collections.emptyList();

        List<SubCardPortBounds> ports = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int portY = cardY + HEADER_HEIGHT + i * PORT_ROW_HEIGHT;
            boolean onRight = !flipped;
            ports.add(createPortBounds(i, false, cardX, cardW, portY, onRight));
        }
        return Collections.unmodifiableList(ports);
    }

    private static SubCardPortBounds createPortBounds(
            int portIndex,
            boolean isInput,
            int cardX,
            int cardW,
            int portY,
            boolean onRight
    ) {
        RectBounds pinHitBox;
        RectBounds slotHitBox;
        RectBounds totalHitBox;
        float anchorX;
        float anchorY = (float) (portY + 8);

        if (onRight) {
            pinHitBox = new RectBounds(cardX + cardW - 10, portY + 4, 8, 8);
            slotHitBox = new RectBounds(cardX + cardW - 24, portY, 16, 16);
            totalHitBox = new RectBounds(cardX + cardW - 28, portY, 26, 16);
            anchorX = (float) (cardX + cardW - 6);
        } else {
            pinHitBox = new RectBounds(cardX + 2, portY + 4, 8, 8);
            slotHitBox = new RectBounds(cardX + 12, portY, 16, 16);
            totalHitBox = new RectBounds(cardX + 2, portY, 28, 16);
            anchorX = (float) (cardX + 6);
        }

        return new SubCardPortBounds(portIndex, isInput, pinHitBox, slotHitBox, totalHitBox, anchorX, anchorY);
    }

    private static SubCardLayoutBounds createEmptyBounds() {
        return new SubCardLayoutBounds(
                RectBounds.EMPTY,
                RectBounds.EMPTY,
                RectBounds.EMPTY,
                RectBounds.EMPTY,
                RectBounds.EMPTY,
                RectBounds.EMPTY,
                RectBounds.EMPTY,
                Collections.emptyList(),
                Collections.emptyList(),
                RectBounds.EMPTY
        );
    }
}
