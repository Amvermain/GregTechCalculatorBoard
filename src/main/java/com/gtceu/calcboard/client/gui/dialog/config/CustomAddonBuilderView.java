package com.gtceu.calcboard.client.gui.dialog.config;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MachineAddonCatalog;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class CustomAddonBuilderView {

    private final MachineConfigDialog dialog;
    private EditBox customNameBox;
    private double customDurationMult = 1.0;
    private double customEutMult = 1.0;
    private int customParallelMult = 1;

    public CustomAddonBuilderView(MachineConfigDialog dialog) {
        this.dialog = dialog;
    }

    public void init() {
        Minecraft mc = Minecraft.getInstance();
        this.customNameBox = new EditBox(mc.font, 0, 0, 160, 14, Component.translatable("gui.gtcalcboard.config.custom_name_hint"));
        this.customNameBox.setMaxLength(64);
        this.customNameBox.setValue(Component.translatable("gui.gtcalcboard.config.custom_name_default").getString());
        this.customDurationMult = 1.0;
        this.customEutMult = 1.0;
        this.customParallelMult = 1;
    }

    public void render(GuiGraphics graphics, Font font, int startX, int startY, int width, int height, int mouseX, int mouseY) {
        if (customNameBox != null) {
            customNameBox.setX(startX + 4);
            customNameBox.setY(startY + 4);
            customNameBox.setWidth(180);
            customNameBox.render(graphics, mouseX, mouseY, 0);
        }

        int rowY = startY + 24;
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.config.time_mult", String.format("%.2fx", customDurationMult)).getString(), startX + 6, rowY + 3, 0xFFFFFFFF, false);
        renderMiniBtn(graphics, font, "-0.1x", startX + 110, rowY, 32, mouseX, mouseY);
        renderMiniBtn(graphics, font, "1.0x", startX + 146, rowY, 26, mouseX, mouseY);
        renderMiniBtn(graphics, font, "+0.1x", startX + 176, rowY, 32, mouseX, mouseY);

        rowY += 20;
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.config.eut_mult", String.format("%.2fx", customEutMult)).getString(), startX + 6, rowY + 3, 0xFFFFFFFF, false);
        renderMiniBtn(graphics, font, "-0.05x", startX + 110, rowY, 36, mouseX, mouseY);
        renderMiniBtn(graphics, font, "1.0x", startX + 150, rowY, 26, mouseX, mouseY);
        renderMiniBtn(graphics, font, "+0.05x", startX + 180, rowY, 36, mouseX, mouseY);

        rowY += 20;
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.config.parallel_mult", String.valueOf(customParallelMult)).getString(), startX + 6, rowY + 3, 0xFFFFFFFF, false);
        renderMiniBtn(graphics, font, "1x", startX + 70, rowY, 22, mouseX, mouseY);
        renderMiniBtn(graphics, font, "4x", startX + 96, rowY, 22, mouseX, mouseY);
        renderMiniBtn(graphics, font, "16x", startX + 122, rowY, 26, mouseX, mouseY);
        renderMiniBtn(graphics, font, "64x", startX + 152, rowY, 26, mouseX, mouseY);

        int createBtnX = startX + width - 130;
        boolean btnH = mouseX >= createBtnX && mouseX <= createBtnX + 126 && mouseY >= startY + 45 && mouseY <= startY + 68;
        graphics.fill(createBtnX, startY + 45, createBtnX + 126, startY + 68, btnH ? 0xFF358050 : 0xFF24603B);
        graphics.renderOutline(createBtnX, startY + 45, 126, 23, 0xFF4EA86F);
        graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.config.create_install").getString(), createBtnX + 63, startY + 52, 0xFFFFFFFF);
    }

    private void renderMiniBtn(GuiGraphics graphics, Font font, String label, int bx, int by, int bw, int mouseX, int mouseY) {
        boolean h = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + 14;
        graphics.fill(bx, by, bx + bw, by + 14, h ? 0xFF3F4658 : 0xFF2B313E);
        graphics.renderOutline(bx, by, bw, 14, 0xFF454E62);
        graphics.drawCenteredString(font, label, bx + bw / 2, by + 3, 0xFFE0E6F0);
    }

    public boolean mouseClicked(double mX, double mY, int button, int startX, int startY, int width, int height, RecipeNode node, BoardScreen parent) {
        if (customNameBox != null) {
            customNameBox.setX(startX + 4);
            customNameBox.setY(startY + 4);
            customNameBox.setWidth(180);
            boolean clicked = customNameBox.mouseClicked(mX, mY, button);
            customNameBox.setFocused(clicked);
            if (clicked) return true;
        }

        int rowY = startY + 24;
        if (mX >= startX + 110 && mX <= startX + 142 && mY >= rowY && mY <= rowY + 14) {
            customDurationMult = Math.max(0.1, customDurationMult - 0.1);
            return true;
        }
        if (mX >= startX + 146 && mX <= startX + 172 && mY >= rowY && mY <= rowY + 14) {
            customDurationMult = 1.0;
            return true;
        }
        if (mX >= startX + 176 && mX <= startX + 208 && mY >= rowY && mY <= rowY + 14) {
            customDurationMult += 0.1;
            return true;
        }

        rowY += 20;
        if (mX >= startX + 110 && mX <= startX + 146 && mY >= rowY && mY <= rowY + 14) {
            customEutMult = Math.max(0.05, customEutMult - 0.05);
            return true;
        }
        if (mX >= startX + 150 && mX <= startX + 176 && mY >= rowY && mY <= rowY + 14) {
            customEutMult = 1.0;
            return true;
        }
        if (mX >= startX + 180 && mX <= startX + 216 && mY >= rowY && mY <= rowY + 14) {
            customEutMult += 0.05;
            return true;
        }

        rowY += 20;
        if (mX >= startX + 70 && mX <= startX + 92 && mY >= rowY && mY <= rowY + 14) {
            customParallelMult = 1;
            return true;
        }
        if (mX >= startX + 96 && mX <= startX + 118 && mY >= rowY && mY <= rowY + 14) {
            customParallelMult = 4;
            return true;
        }
        if (mX >= startX + 122 && mX <= startX + 148 && mY >= rowY && mY <= rowY + 14) {
            customParallelMult = 16;
            return true;
        }
        if (mX >= startX + 152 && mX <= startX + 178 && mY >= rowY && mY <= rowY + 14) {
            customParallelMult = 64;
            return true;
        }

        int createBtnX = startX + width - 130;
        if (mX >= createBtnX && mX <= createBtnX + 126 && mY >= startY + 45 && mY <= startY + 68) {
            String cName = customNameBox != null ? customNameBox.getValue().trim() : "Custom Mod";
            if (cName.isEmpty()) cName = "Custom Mod";
            MachineAddon customAddon = MachineAddon.custom(cName, customDurationMult, customEutMult, customParallelMult);
            MachineAddonCatalog.getInstance().registerCustomAddon(customAddon);
            node.addAddon(customAddon.copy());
            if (parent != null) parent.markSummaryDirty();
            return true;
        }

        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (customNameBox != null && customNameBox.isFocused()) {
            return customNameBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (customNameBox != null && customNameBox.isFocused()) {
            return customNameBox.charTyped(codePoint, modifiers);
        }
        return false;
    }
}
