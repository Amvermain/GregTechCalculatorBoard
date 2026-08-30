/*
 * Decompiled with CFR 0.152.
 */
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
        this.customNameBox = new EditBox(mc.font, 0, 0, 160, 14, Component.translatable((String)"gui.gtcalcboard.config.custom_name_hint"));
        this.customNameBox.setMaxLength(64);
        this.customNameBox.setValue(Component.translatable((String)"gui.gtcalcboard.config.custom_name_default").getString());
        this.customDurationMult = 1.0;
        this.customEutMult = 1.0;
        this.customParallelMult = 1;
    }

    public EditBox getCustomNameBox() {
        return this.customNameBox;
    }

    public void render(GuiGraphics graphics, Font font, int startX, int startY, int width, int height, int mouseX, int mouseY) {
        if (this.customNameBox != null) {
            this.customNameBox.setX(startX + 4);
            this.customNameBox.setY(startY + 4);
            this.customNameBox.setWidth(180);
            this.customNameBox.render(graphics, mouseX, mouseY, 0.0f);
        }
        int rowY = startY + 24;
        graphics.drawString(font, Component.translatable((String)"gui.gtcalcboard.config.time_mult", (Object[])new Object[]{String.format("%.2fx", this.customDurationMult)}).getString(), startX + 6, rowY + 3, -1, false);
        this.renderMiniBtn(graphics, font, "-0.1x", startX + 110, rowY, 32, mouseX, mouseY);
        this.renderMiniBtn(graphics, font, "1.0x", startX + 146, rowY, 26, mouseX, mouseY);
        this.renderMiniBtn(graphics, font, "+0.1x", startX + 176, rowY, 32, mouseX, mouseY);
        graphics.drawString(font, Component.translatable((String)"gui.gtcalcboard.config.eut_mult", (Object[])new Object[]{String.format("%.2fx", this.customEutMult)}).getString(), startX + 6, (rowY += 20) + 3, -1, false);
        this.renderMiniBtn(graphics, font, "-0.05x", startX + 110, rowY, 36, mouseX, mouseY);
        this.renderMiniBtn(graphics, font, "1.0x", startX + 150, rowY, 26, mouseX, mouseY);
        this.renderMiniBtn(graphics, font, "+0.05x", startX + 180, rowY, 36, mouseX, mouseY);
        graphics.drawString(font, Component.translatable((String)"gui.gtcalcboard.config.parallel_mult", (Object[])new Object[]{String.valueOf(this.customParallelMult)}).getString(), startX + 6, (rowY += 20) + 3, -1, false);
        this.renderMiniBtn(graphics, font, "1x", startX + 70, rowY, 22, mouseX, mouseY);
        this.renderMiniBtn(graphics, font, "4x", startX + 96, rowY, 22, mouseX, mouseY);
        this.renderMiniBtn(graphics, font, "16x", startX + 122, rowY, 26, mouseX, mouseY);
        this.renderMiniBtn(graphics, font, "64x", startX + 152, rowY, 26, mouseX, mouseY);
        int createBtnX = startX + width - 130;
        boolean btnH = mouseX >= createBtnX && mouseX <= createBtnX + 126 && mouseY >= startY + 45 && mouseY <= startY + 68;
        graphics.fill(createBtnX, startY + 45, createBtnX + 126, startY + 68, btnH ? -13270960 : -14393285);
        graphics.renderOutline(createBtnX, startY + 45, 126, 23, -11622289);
        graphics.drawCenteredString(font, Component.translatable((String)"gui.gtcalcboard.config.create_install").getString(), createBtnX + 63, startY + 52, -1);
    }

    private void renderMiniBtn(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int mouseX, int mouseY) {
        boolean h = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + 14;
        graphics.fill(bx, by, bx + bw, by + 14, h ? -12761511 : -14473421);
        graphics.renderOutline(bx, by, bw, 14, h ? -10955777 : -12761511);
        graphics.drawCenteredString(font, text, bx + bw / 2, by + 3, h ? -10955777 : -5195576);
    }

    public boolean mouseClicked(double mX, double mY, int button, int startX, int startY, int width, int height, RecipeNode node, BoardScreen parent) {
        if (this.customNameBox != null) {
            boolean clicked = this.customNameBox.mouseClicked(mX, mY, button);
            this.customNameBox.setFocused(clicked);
            if (clicked) {
                return true;
            }
        }
        int rowY = startY + 24;
        if (mX >= (double)(startX + 110) && mX <= (double)(startX + 142) && mY >= (double)rowY && mY <= (double)(rowY + 14)) {
            this.customDurationMult = Math.max(0.1, this.customDurationMult - 0.1);
            return true;
        }
        if (mX >= (double)(startX + 146) && mX <= (double)(startX + 172) && mY >= (double)rowY && mY <= (double)(rowY + 14)) {
            this.customDurationMult = 1.0;
            return true;
        }
        if (mX >= (double)(startX + 176) && mX <= (double)(startX + 208) && mY >= (double)rowY && mY <= (double)(rowY + 14)) {
            this.customDurationMult += 0.1;
            return true;
        }
        rowY += 20;
        if (mX >= (double)(startX + 110) && mX <= (double)(startX + 146) && mY >= (double)rowY && mY <= (double)(rowY + 14)) {
            this.customEutMult = Math.max(0.05, this.customEutMult - 0.05);
            return true;
        }
        if (mX >= (double)(startX + 150) && mX <= (double)(startX + 176) && mY >= (double)rowY && mY <= (double)(rowY + 14)) {
            this.customEutMult = 1.0;
            return true;
        }
        if (mX >= (double)(startX + 180) && mX <= (double)(startX + 216) && mY >= (double)rowY && mY <= (double)(rowY + 14)) {
            this.customEutMult += 0.05;
            return true;
        }
        rowY += 20;
        if (mX >= (double)(startX + 70) && mX <= (double)(startX + 92) && mY >= (double)rowY && mY <= (double)(rowY + 14)) {
            this.customParallelMult = 1;
            return true;
        }
        if (mX >= (double)(startX + 96) && mX <= (double)(startX + 118) && mY >= (double)rowY && mY <= (double)(rowY + 14)) {
            this.customParallelMult = 4;
            return true;
        }
        if (mX >= (double)(startX + 122) && mX <= (double)(startX + 148) && mY >= (double)rowY && mY <= (double)(rowY + 14)) {
            this.customParallelMult = 16;
            return true;
        }
        if (mX >= (double)(startX + 152) && mX <= (double)(startX + 178) && mY >= (double)rowY && mY <= (double)(rowY + 14)) {
            this.customParallelMult = 64;
            return true;
        }
        int createBtnX = startX + width - 130;
        if (mX >= (double)createBtnX && mX <= (double)(createBtnX + 126) && mY >= (double)(startY + 45) && mY <= (double)(startY + 68)) {
            String cName;
            String string = cName = this.customNameBox != null ? this.customNameBox.getValue().trim() : "Custom Mod";
            if (cName.isEmpty()) {
                cName = "Custom Mod";
            }
            MachineAddon customAddon = MachineAddon.custom(cName, this.customDurationMult, this.customEutMult, this.customParallelMult);
            MachineAddonCatalog.getInstance().registerCustomAddon(customAddon);
            node.addAddon(customAddon.copy());
            this.dialog.getActiveAddonsView().setScrollOffset(Math.max(0, node.getAddons().size() - 2));
            if (parent != null) {
                parent.markSummaryDirty();
            }
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.customNameBox != null && this.customNameBox.isFocused()) {
            return this.customNameBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (this.customNameBox != null && this.customNameBox.isFocused()) {
            return this.customNameBox.charTyped(codePoint, modifiers);
        }
        return false;
    }
}
