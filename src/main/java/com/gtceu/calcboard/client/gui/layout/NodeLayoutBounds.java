package com.gtceu.calcboard.client.gui.layout;

import java.util.Collections;
import java.util.List;

public class NodeLayoutBounds {

    public record RectBounds(int x, int y, int width, int height) {
        public static final RectBounds EMPTY = new RectBounds(0, 0, 0, 0);

        public boolean contains(double px, double py) {
            return !isEmpty() && px >= x && px <= x + width && py >= y && py <= y + height;
        }

        public boolean isEmpty() {
            return width <= 0 || height <= 0;
        }
    }

    public record PortBounds(
            int portIndex,
            boolean isInput,
            RectBounds hitBox,
            RectBounds slotBounds,
            float anchorX,
            float anchorY
    ) {}

    private final RectBounds cardBounds;
    private final RectBounds headerBounds;
    private final RectBounds machineIconBounds;
    private final RectBounds nameBounds;
    private final RectBounds countMinusBtnBounds;
    private final RectBounds countBoxBounds;
    private final RectBounds countPlusBtnBounds;
    private final RectBounds countHalfBtnBounds;
    private final RectBounds countDoubleBtnBounds;
    private final RectBounds switchBtnBounds;
    private final RectBounds expandBtnBounds;
    private final RectBounds flipBtnBounds;
    private final RectBounds targetBtnBounds;
    private final RectBounds closeBtnBounds;
    private final RectBounds resizeHandleBounds;
    private final boolean hasRow2Controls;
    private final RectBounds tierBtnBounds;
    private final RectBounds secondaryBtnBounds;
    private final RectBounds configBtnBounds;
    private final RectBounds addonTrayBounds;
    private final RectBounds moduleBadgeBounds;
    private final int separatorY;
    private final int contentStartY;
    private final int autoHeight;
    private final int cardHeight;
    private final List<PortBounds> inputPorts;
    private final List<PortBounds> outputPorts;
    private final RectBounds hiddenPortsBadgeBounds;
    private final RectBounds targetBatchBadgeBounds;

    public NodeLayoutBounds(
            RectBounds cardBounds,
            RectBounds headerBounds,
            RectBounds machineIconBounds,
            RectBounds nameBounds,
            RectBounds countMinusBtnBounds,
            RectBounds countBoxBounds,
            RectBounds countPlusBtnBounds,
            RectBounds countHalfBtnBounds,
            RectBounds countDoubleBtnBounds,
            RectBounds switchBtnBounds,
            RectBounds expandBtnBounds,
            RectBounds flipBtnBounds,
            RectBounds targetBtnBounds,
            RectBounds closeBtnBounds,
            RectBounds resizeHandleBounds,
            boolean hasRow2Controls,
            RectBounds tierBtnBounds,
            RectBounds secondaryBtnBounds,
            RectBounds configBtnBounds,
            RectBounds addonTrayBounds,
            RectBounds moduleBadgeBounds,
            int separatorY,
            int contentStartY,
            int autoHeight,
            int cardHeight,
            List<PortBounds> inputPorts,
            List<PortBounds> outputPorts,
            RectBounds hiddenPortsBadgeBounds,
            RectBounds targetBatchBadgeBounds
    ) {
        this.cardBounds = cardBounds;
        this.headerBounds = headerBounds;
        this.machineIconBounds = machineIconBounds;
        this.nameBounds = nameBounds;
        this.countMinusBtnBounds = countMinusBtnBounds;
        this.countBoxBounds = countBoxBounds;
        this.countPlusBtnBounds = countPlusBtnBounds;
        this.countHalfBtnBounds = countHalfBtnBounds;
        this.countDoubleBtnBounds = countDoubleBtnBounds;
        this.switchBtnBounds = switchBtnBounds;
        this.expandBtnBounds = expandBtnBounds;
        this.flipBtnBounds = flipBtnBounds;
        this.targetBtnBounds = targetBtnBounds;
        this.closeBtnBounds = closeBtnBounds;
        this.resizeHandleBounds = resizeHandleBounds;
        this.hasRow2Controls = hasRow2Controls;
        this.tierBtnBounds = tierBtnBounds;
        this.secondaryBtnBounds = secondaryBtnBounds;
        this.configBtnBounds = configBtnBounds;
        this.addonTrayBounds = addonTrayBounds;
        this.moduleBadgeBounds = moduleBadgeBounds;
        this.separatorY = separatorY;
        this.contentStartY = contentStartY;
        this.autoHeight = autoHeight;
        this.cardHeight = cardHeight;
        this.inputPorts = Collections.unmodifiableList(inputPorts);
        this.outputPorts = Collections.unmodifiableList(outputPorts);
        this.hiddenPortsBadgeBounds = hiddenPortsBadgeBounds;
        this.targetBatchBadgeBounds = targetBatchBadgeBounds;
    }

    public RectBounds getCardBounds() { return cardBounds; }
    public RectBounds getHeaderBounds() { return headerBounds; }
    public RectBounds getMachineIconBounds() { return machineIconBounds; }
    public RectBounds getNameBounds() { return nameBounds; }
    public RectBounds getCountMinusBtnBounds() { return countMinusBtnBounds; }
    public RectBounds getCountBoxBounds() { return countBoxBounds; }
    public RectBounds getCountPlusBtnBounds() { return countPlusBtnBounds; }
    public RectBounds getCountHalfBtnBounds() { return countHalfBtnBounds; }
    public RectBounds getCountDoubleBtnBounds() { return countDoubleBtnBounds; }
    public RectBounds getSwitchBtnBounds() { return switchBtnBounds; }
    public RectBounds getExpandBtnBounds() { return expandBtnBounds; }
    public RectBounds getFlipBtnBounds() { return flipBtnBounds; }
    public RectBounds getTargetBtnBounds() { return targetBtnBounds; }
    public RectBounds getCloseBtnBounds() { return closeBtnBounds; }
    public RectBounds getResizeHandleBounds() { return resizeHandleBounds; }
    public boolean hasRow2Controls() { return hasRow2Controls; }
    public RectBounds getTierBtnBounds() { return tierBtnBounds; }
    public RectBounds getSecondaryBtnBounds() { return secondaryBtnBounds; }
    public RectBounds getConfigBtnBounds() { return configBtnBounds; }
    public RectBounds getAddonTrayBounds() { return addonTrayBounds; }
    public RectBounds getModuleBadgeBounds() { return moduleBadgeBounds; }
    public int getSeparatorY() { return separatorY; }
    public int getContentStartY() { return contentStartY; }
    public int getAutoHeight() { return autoHeight; }
    public int getCardHeight() { return cardHeight; }
    public List<PortBounds> getInputPorts() { return inputPorts; }
    public List<PortBounds> getOutputPorts() { return outputPorts; }
    public RectBounds getHiddenPortsBadgeBounds() { return hiddenPortsBadgeBounds; }
    public RectBounds getTargetBatchBadgeBounds() { return targetBatchBadgeBounds; }

    public boolean isPointInside(double mouseX, double mouseY) {
        if (cardBounds.contains(mouseX, mouseY)) {
            return true;
        }
        if (findHoveredInputPort(mouseX, mouseY) != null || findHoveredOutputPort(mouseX, mouseY) != null) {
            return true;
        }
        return targetBatchBadgeBounds.contains(mouseX, mouseY);
    }

    public PortBounds findHoveredInputPort(double mouseX, double mouseY) {
        for (PortBounds port : inputPorts) {
            if (port.hitBox().contains(mouseX, mouseY)) {
                return port;
            }
        }
        return null;
    }

    public PortBounds findHoveredOutputPort(double mouseX, double mouseY) {
        for (PortBounds port : outputPorts) {
            if (port.hitBox().contains(mouseX, mouseY)) {
                return port;
            }
        }
        return null;
    }

    public int getHoveredInputPortIndex(double mouseX, double mouseY) {
        PortBounds port = findHoveredInputPort(mouseX, mouseY);
        return port != null ? port.portIndex() : -1;
    }

    public int getHoveredOutputPortIndex(double mouseX, double mouseY) {
        PortBounds port = findHoveredOutputPort(mouseX, mouseY);
        return port != null ? port.portIndex() : -1;
    }

    public PortBounds findPort(boolean isInput, int index) {
        List<PortBounds> list = isInput ? inputPorts : outputPorts;
        for (PortBounds port : list) {
            if (port.portIndex() == index) {
                return port;
            }
        }
        return null;
    }

    public double[] getPortBounds(boolean isInput, int index) {
        PortBounds port = findPort(isInput, index);
        if (port == null) {
            return null;
        }
        RectBounds sb = port.slotBounds();
        return new double[]{sb.x(), sb.y(), sb.x() + sb.width(), sb.y() + sb.height()};
    }

    public boolean isHeaderHovered(double mouseX, double mouseY) {
        if (machineIconBounds.contains(mouseX, mouseY)) {
            return false;
        }
        if (isTargetBatchBadgeHovered(mouseX, mouseY)) {
            return false;
        }
        if (getHoveredInputPortIndex(mouseX, mouseY) >= 0 || getHoveredOutputPortIndex(mouseX, mouseY) >= 0) {
            return false;
        }
        if (closeBtnBounds.contains(mouseX, mouseY)) {
            return false;
        }
        return headerBounds.contains(mouseX, mouseY);
    }

    public boolean isMachineIconHovered(double mouseX, double mouseY) {
        return machineIconBounds.contains(mouseX, mouseY);
    }

    public boolean isSwitchButtonHovered(double mouseX, double mouseY) {
        return switchBtnBounds.contains(mouseX, mouseY);
    }

    public boolean isExpandButtonHovered(double mouseX, double mouseY) {
        return expandBtnBounds.contains(mouseX, mouseY);
    }

    public boolean isFlipButtonHovered(double mouseX, double mouseY) {
        return flipBtnBounds.contains(mouseX, mouseY);
    }

    public boolean isTargetButtonHovered(double mouseX, double mouseY) {
        return targetBtnBounds.contains(mouseX, mouseY);
    }

    public boolean isCloseButtonHovered(double mouseX, double mouseY) {
        return closeBtnBounds.contains(mouseX, mouseY);
    }

    public boolean isResizeHandleHovered(double mouseX, double mouseY) {
        return resizeHandleBounds.contains(mouseX, mouseY);
    }

    public boolean isHiddenPortsBadgeHovered(double mouseX, double mouseY) {
        return hiddenPortsBadgeBounds.contains(mouseX, mouseY);
    }

    public boolean isTargetBatchBadgeHovered(double mouseX, double mouseY) {
        return targetBatchBadgeBounds.contains(mouseX, mouseY);
    }

    public boolean isTierButtonHovered(double mouseX, double mouseY) {
        return hasRow2Controls && tierBtnBounds.contains(mouseX, mouseY);
    }

    public boolean isSecondaryButtonHovered(double mouseX, double mouseY) {
        return hasRow2Controls && secondaryBtnBounds.contains(mouseX, mouseY);
    }

    public boolean isMachineConfigButtonHovered(double mouseX, double mouseY) {
        return hasRow2Controls && configBtnBounds.contains(mouseX, mouseY);
    }

    public boolean isAddonTrayHovered(double mouseX, double mouseY) {
        return addonTrayBounds.contains(mouseX, mouseY);
    }

    public boolean isModuleBadgeHovered(double mouseX, double mouseY) {
        return moduleBadgeBounds.contains(mouseX, mouseY);
    }
}
