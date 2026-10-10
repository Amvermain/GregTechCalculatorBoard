package com.gtceu.calcboard.client.gui.layout;

import com.gtceu.calcboard.client.gui.layout.NodeLayoutBounds.RectBounds;

import java.util.List;

/**
 * Deductive layout bounds model for embedded sub-cards within a shared machine pool frame.
 * Precomputes deterministic bounding boxes for the header, controls, slots, and port pins.
 */
public record SubCardLayoutBounds(
        RectBounds cardBounds,
        RectBounds headerBounds,
        RectBounds nameBounds,
        RectBounds deleteBtnBounds,
        RectBounds plusBtnBounds,
        RectBounds dutyTextBounds,
        RectBounds minusBtnBounds,
        List<SubCardPortBounds> inputPorts,
        List<SubCardPortBounds> outputPorts,
        RectBounds centerArrowBounds
) {

    public record SubCardPortBounds(
            int portIndex,
            boolean isInput,
            RectBounds pinHitBox,
            RectBounds slotHitBox,
            RectBounds totalHitBox,
            float anchorX,
            float anchorY
    ) {
        public boolean isHovered(double x, double y) {
            return totalHitBox != null && totalHitBox.contains(x, y);
        }

        public boolean isPinHovered(double x, double y) {
            return pinHitBox != null && pinHitBox.contains(x, y);
        }

        public boolean isSlotHovered(double x, double y) {
            return slotHitBox != null && slotHitBox.contains(x, y);
        }
    }

    public SubCardPortBounds findHoveredPort(double x, double y) {
        for (SubCardPortBounds port : inputPorts) {
            if (port.isHovered(x, y)) return port;
        }
        for (SubCardPortBounds port : outputPorts) {
            if (port.isHovered(x, y)) return port;
        }
        return null;
    }

    public SubCardPortBounds findPort(boolean isInput, int index) {
        List<SubCardPortBounds> list = isInput ? inputPorts : outputPorts;
        for (SubCardPortBounds p : list) {
            if (p.portIndex() == index) return p;
        }
        return null;
    }
}
