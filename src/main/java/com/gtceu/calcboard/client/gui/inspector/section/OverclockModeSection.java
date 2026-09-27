package com.gtceu.calcboard.client.gui.inspector.section;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.client.gui.api.IBoardScreenContext;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public class OverclockModeSection implements IInspectorSection {

    private NodeWidget widget;
    private RecipeNode node;
    private IBoardScreenContext screen;

    @Override
    public void bind(NodeWidget widget, RecipeNode node, IBoardScreenContext screen) {
        this.widget = widget;
        this.node = node;
        this.screen = screen;
    }

    @Override
    public boolean isApplicable(RecipeNode node) {
        return supportsOverclockMode(node);
    }

    public static boolean supportsOverclockMode(RecipeNode node) {
        if (node == null || node.getOverclockMode() == null || node.isModule()) return false;
        if (node.isGenerator() || node.getEnergyType() == EnergyType.NONE) return false;
        if (SteamModeSection.isSteamNode(node) || BoilerThrottleSection.isBoiler(node)) return false;
        return node.getEnergyType() == EnergyType.ELECTRIC_EU;
    }

    @Override
    public int getHeight(RecipeNode node) {
        return 32;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int w, int mouseX, int mouseY) {
        if (node == null) return;

        String ocLabel = Component.translatable("gui.gtcalcboard.inspector.overclock").getString();
        graphics.drawString(font, ocLabel, x, y, 0xFF94A3B8, false);

        int btnY = y + 12;
        boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= btnY && mouseY <= btnY + 16;
        graphics.fill(x, btnY, x + w, btnY + 16, hov ? 0xFF1E293B : 0xFF0F172A);
        graphics.renderOutline(x, btnY, w, 16, hov ? 0xFF38BDF8 : 0xFF334155);

        String modeName = font.plainSubstrByWidth(node.getOverclockMode().getDisplayName(), w - 20);
        graphics.drawString(font, modeName, x + 6, btnY + 4, 0xFFE2E8F0, false);
        graphics.drawString(font, "▼", x + w - 12, btnY + 4, 0xFF64748B, false);
    }

    @Override
    public boolean mouseClicked(int x, int y, int w, double mouseX, double mouseY, int button) {
        if (button != 0 || node == null) return false;

        int btnY = y + 12;
        if (mouseX >= x && mouseX <= x + w && mouseY >= btnY && mouseY <= btnY + 16) {
            cycleOverclockMode();
            return true;
        }
        return false;
    }

    private void cycleOverclockMode() {
        var curMode = node.getOverclockMode();
        var vals = OverclockMode.values();
        var nextMode = vals[(curMode.ordinal() + 1) % vals.length];
        node.setOverclockMode(nextMode);

        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
        if (widget != null) widget.invalidateCache();
        syncSharedFrame();
        if (screen != null) {
            screen.recordCommand(BoardCommand.ModifyPropertyCommand.overclockMode(node.getId(), curMode, nextMode));
            screen.markSummaryDirty();
        }
    }

    private void syncSharedFrame() {
        if (screen != null && screen.getGraph() != null) {
            var frame = screen.getGraph().findFrameEnclosingNode(node);
            if (frame != null && frame.isSharedMachineFrame()) {
                frame.syncHardwareConfig(node, screen.getGraph());
                screen.rebuildBoardWidgets();
            }
        }
    }
}
