package com.gtceu.calcboard.client.gui.dialog.settings;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.type.WireColorPreset;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.BoardSettingsDialog;
import com.gtceu.calcboard.client.gui.render.BoardTooltipRenderer;
import com.gtceu.calcboard.client.gui.render.ConnectionRenderer;
import com.gtceu.calcboard.client.gui.util.OklabColorUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Settings tab for customizing default and matched wire color palettes and previewing interpolation.
 */
public class WiresSettingsTab extends AbstractSettingsTab {

    public WiresSettingsTab(BoardSettingsDialog dialog, BoardScreen parent) {
        super(dialog, parent);
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        BoardManager bm = BoardManager.getInstance();
        graphics.drawString(font, "§a" + Component.translatable("gui.gtcalcboard.settings.wires_desc").getString(), x, y, 0xFFFFFFFF, false);

        int rowY = y + 18;

        // 1. Default Wire Color Palette
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.settings.default_wire_color").getString(), x, rowY, 0xFFCCCCCC, false);
        rowY += 12;

        WireColorPreset curDef = bm.getWireColorPreset();
        WireColorPreset[] presets = WireColorPreset.values();
        int palX = x;
        int palSize = 18;
        for (WireColorPreset p : presets) {
            boolean isSel = (p == curDef);
            boolean hover = mouseX >= palX && mouseX <= palX + palSize && mouseY >= rowY && mouseY <= rowY + palSize;
            graphics.fill(palX, rowY, palX + palSize, rowY + palSize, p.getArgb());
            graphics.renderOutline(palX, rowY, palSize, palSize, isSel ? 0xFFFFFFFF : (hover ? 0xFFAAAAAA : 0xFF3D4B66));
            if (isSel) {
                graphics.drawCenteredString(font, "✔", palX + palSize / 2, rowY + 5, 0xFF000000);
            }
            if (hover) {
                BoardTooltipRenderer.renderTooltip(graphics, font, p.getDisplayName(), mouseX, mouseY, parent.width, parent.height);
            }
            palX += palSize + 6;
        }

        rowY += palSize + 14;

        // 2. Matched / Dragging Wire Color Palette
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.settings.matched_wire_color").getString(), x, rowY, 0xFFCCCCCC, false);
        rowY += 12;

        WireColorPreset curMatched = bm.getMatchedWireColorPreset();
        palX = x;
        for (WireColorPreset p : presets) {
            boolean isSel = (p == curMatched);
            boolean hover = mouseX >= palX && mouseX <= palX + palSize && mouseY >= rowY && mouseY <= rowY + palSize;
            graphics.fill(palX, rowY, palX + palSize, rowY + palSize, p.getArgb());
            graphics.renderOutline(palX, rowY, palSize, palSize, isSel ? 0xFFFFFFFF : (hover ? 0xFFAAAAAA : 0xFF3D4B66));
            if (isSel) {
                graphics.drawCenteredString(font, "✔", palX + palSize / 2, rowY + 5, 0xFF000000);
            }
            if (hover) {
                BoardTooltipRenderer.renderTooltip(graphics, font, p.getDisplayName(), mouseX, mouseY, parent.width, parent.height);
            }
            palX += palSize + 6;
        }

        rowY += palSize + 16;

        // 3. Live Wire Preview Box
        renderLiveWirePreview(graphics, font, x, rowY, w, curDef, curMatched);
    }

    private void renderLiveWirePreview(GuiGraphics graphics, Font font, int x, int rowY, int w,
                                       WireColorPreset curDef, WireColorPreset curMatched) {
        int previewH = 54;
        int previewW = w - 4;
        graphics.fill(x, rowY, x + previewW, rowY + previewH, 0xEE10131A);
        graphics.renderOutline(x, rowY, previewW, previewH, 0xFF2C394F);

        graphics.drawString(font, Component.translatable("gui.gtcalcboard.settings.preview_label").getString(), x + 6, rowY + 4, 0xFF8899AA, false);

        int midColor = OklabColorUtil.interpolateOklab(curDef.getArgb(), curMatched.getArgb(), 0.5f);
        float sectionW = (previewW - 20) / 3.0f;

        float wx1 = x + 10;
        float wy1 = rowY + 34;
        float wx2 = x + 10 + sectionW - 8;
        float wy2 = rowY + 20;
        ConnectionRenderer.renderBezier(graphics, wx1, wy1, wx2, wy2, curDef.getArgb(), 2.0f);
        graphics.drawCenteredString(font, "0%", (int) (wx1 + wx2) / 2, (int) Math.min(wy1, wy2) - 8, curDef.getArgb());

        float bx1 = x + 10 + sectionW + 4;
        float by1 = rowY + 34;
        float bx2 = x + 10 + 2 * sectionW - 4;
        float by2 = rowY + 20;
        ConnectionRenderer.renderBezier(graphics, bx1, by1, bx2, by2, midColor, 2.5f);
        graphics.drawCenteredString(font, "50%", (int) (bx1 + bx2) / 2, (int) Math.min(by1, by2) - 8, midColor);

        float mx1 = x + 10 + 2 * sectionW + 8;
        float my1 = rowY + 34;
        float mx2 = x + previewW - 10;
        float my2 = rowY + 20;
        ConnectionRenderer.renderBezier(graphics, mx1, my1, mx2, my2, curMatched.getArgb(), 2.5f);
        graphics.drawCenteredString(font, "100%", (int) (mx1 + mx2) / 2, (int) Math.min(my1, my2) - 8, curMatched.getArgb());

        int barX = x + 10;
        int barW = previewW - 20;
        int barY = rowY + previewH - 6;
        int[] lut = OklabColorUtil.getWireColorLut(curDef.getArgb(), curMatched.getArgb());
        for (int i = 0; i < barW; i++) {
            int lutIdx = (i * (lut.length - 1)) / (barW - 1);
            graphics.fill(barX + i, barY, barX + i + 1, barY + 2, lut[lutIdx]);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int x, int y, int w, int h, int button) {
        BoardManager bm = BoardManager.getInstance();
        int rowY = y + 18 + 12;
        int palSize = 18;
        WireColorPreset[] presets = WireColorPreset.values();

        // Default Wire Palette
        int palX = x;
        for (WireColorPreset p : presets) {
            if (mouseX >= palX && mouseX <= palX + palSize && mouseY >= rowY && mouseY <= rowY + palSize) {
                bm.setWireColorPreset(p);
                onSettingsChanged();
                return true;
            }
            palX += palSize + 6;
        }

        rowY += palSize + 14 + 12;

        // Matched Wire Palette
        palX = x;
        for (WireColorPreset p : presets) {
            if (mouseX >= palX && mouseX <= palX + palSize && mouseY >= rowY && mouseY <= rowY + palSize) {
                bm.setMatchedWireColorPreset(p);
                onSettingsChanged();
                return true;
            }
            palX += palSize + 6;
        }

        return false;
    }
}
