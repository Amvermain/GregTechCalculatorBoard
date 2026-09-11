package com.gtceu.calcboard.client.gui.dialog.settings;

import com.gtceu.calcboard.api.preset.CategoryMachinePresetManager;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.BoardSettingsDialog;
import com.gtceu.calcboard.client.gui.util.BoardScissorHelper;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Settings tab for browsing and clearing machine category default presets.
 */
public class PresetsSettingsTab extends AbstractSettingsTab {

    private int presetScrollOffset = 0;

    public PresetsSettingsTab(BoardSettingsDialog dialog, BoardScreen parent) {
        super(dialog, parent);
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        graphics.drawString(font, "§d" + Component.translatable("gui.gtcalcboard.settings.presets_desc").getString(), x, y, 0xFFFFFFFF, false);

        var presetManager = CategoryMachinePresetManager.getInstance();
        var allPresets = presetManager.getAllPresets();

        // Clear All Button (Top Right)
        if (!allPresets.isEmpty()) {
            int clearBtnW = 64;
            int clearBtnH = 16;
            int clearBtnX = x + w - clearBtnW - 4;
            int clearBtnY = y - 2;
            boolean clearHover = mouseX >= clearBtnX && mouseX <= clearBtnX + clearBtnW && mouseY >= clearBtnY && mouseY <= clearBtnY + clearBtnH;
            graphics.fill(clearBtnX, clearBtnY, clearBtnX + clearBtnW, clearBtnY + clearBtnH, clearHover ? 0xFF882222 : 0xFF442222);
            graphics.renderOutline(clearBtnX, clearBtnY, clearBtnW, clearBtnH, clearHover ? 0xFFFF6666 : 0xFF883333);
            graphics.drawCenteredString(font, "✖ " + Component.translatable("gui.gtcalcboard.settings.clear_all_presets").getString(), clearBtnX + clearBtnW / 2, clearBtnY + 4, 0xFFFFFFFF);
        }

        int startY = y + 18;
        int listH = h - 22;

        if (allPresets.isEmpty()) {
            graphics.drawString(font, "§7" + Component.translatable("gui.gtcalcboard.settings.no_presets").getString(), x + 4, startY + 10, 0xFF8899AA, false);
            graphics.drawString(font, "§8" + Component.translatable("gui.gtcalcboard.settings.no_presets_hint").getString(), x + 4, startY + 24, 0xFF667788, false);
            return;
        }

        BoardScissorHelper.enableScissor(graphics, x, startY, x + w, startY + listH);

        int rowH = 26;
        int rowY = startY - presetScrollOffset;
        for (var entry : allPresets.entrySet()) {
            var catId = entry.getKey();
            var preset = entry.getValue();

            if (rowY + rowH >= startY && rowY <= startY + listH) {
                renderPresetRow(graphics, font, x, rowY, w, rowH, mouseX, mouseY, catId, preset);
            }
            rowY += rowH;
        }

        BoardScissorHelper.disableScissor(graphics);
    }

    private void renderPresetRow(GuiGraphics graphics, Font font, int x, int rowY, int w, int rowH,
                                 int mouseX, int mouseY, net.minecraft.resources.ResourceLocation catId,
                                 com.gtceu.calcboard.api.preset.CategoryMachinePreset preset) {
        boolean rowHover = mouseX >= x && mouseX <= x + w - 4 && mouseY >= rowY && mouseY <= rowY + rowH - 2;
        graphics.fill(x, rowY, x + w - 4, rowY + rowH - 2, rowHover ? 0xFF222836 : 0xFF181C26);
        graphics.renderOutline(x, rowY, w - 4, rowH - 2, rowHover ? 0xFF5B9BD5 : 0xFF35445E);

        int iconX = x + 4;
        int iconY = rowY + 3;
        if (preset.getMachineIcon() != null) {
            var item = ForgeRegistries.ITEMS.getValue(preset.getMachineIcon());
            if (item != null && item != Items.AIR) {
                graphics.renderItem(new ItemStack(item), iconX, iconY);
            }
        }

        int textX = iconX + 22;
        String catText = catId.toString();
        if (font.width(catText) > 120) {
            catText = font.plainSubstrByWidth(catText, 110) + "...";
        }
        graphics.drawString(font, "§f" + catText, textX, rowY + 7, 0xFFFFFFFF, false);

        int badgeX = x + 155;
        String mbBadge = preset.isMultiblock() ? "§a[MB]" : "§7[SB]";
        graphics.drawString(font, mbBadge, badgeX, rowY + 7, 0xFFFFFFFF, false);
        badgeX += font.width(mbBadge) + 4;

        if (preset.getTargetTier() != null) {
            String tierBadge = "§e" + preset.getTargetTier().name();
            graphics.drawString(font, tierBadge, badgeX, rowY + 7, 0xFFFFFFFF, false);
            badgeX += font.width(tierBadge) + 4;
        }

        if (preset.getParallel() > 1) {
            String parBadge = "§b⚡" + preset.getParallel() + "x";
            graphics.drawString(font, parBadge, badgeX, rowY + 7, 0xFFFFFFFF, false);
            badgeX += font.width(parBadge) + 4;
        }

        if (!preset.getAddons().isEmpty()) {
            String addonBadge = "§d▦" + preset.getAddons().size();
            graphics.drawString(font, addonBadge, badgeX, rowY + 7, 0xFFFFFFFF, false);
        }

        int delBtnW = 20;
        int delBtnH = 18;
        int delBtnX = x + w - 4 - delBtnW - 4;
        int delBtnY = rowY + 3;
        boolean delHover = mouseX >= delBtnX && mouseX <= delBtnX + delBtnW && mouseY >= delBtnY && mouseY <= delBtnY + delBtnH;
        graphics.fill(delBtnX, delBtnY, delBtnX + delBtnW, delBtnY + delBtnH, delHover ? 0xFF882222 : 0xFF3D2020);
        graphics.renderOutline(delBtnX, delBtnY, delBtnW, delBtnH, delHover ? 0xFFFF4444 : 0xFF663333);
        graphics.drawCenteredString(font, "✖", delBtnX + delBtnW / 2, delBtnY + 5, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int x, int y, int w, int h, int button) {
        var presetManager = CategoryMachinePresetManager.getInstance();
        var allPresets = presetManager.getAllPresets();
        BoardManager bm = BoardManager.getInstance();

        // Clear All Button Click
        if (!allPresets.isEmpty()) {
            int clearBtnW = 64;
            int clearBtnH = 16;
            int clearBtnX = x + w - clearBtnW - 4;
            int clearBtnY = y - 2;
            if (mouseX >= clearBtnX && mouseX <= clearBtnX + clearBtnW && mouseY >= clearBtnY && mouseY <= clearBtnY + clearBtnH) {
                presetManager.clearAll();
                bm.saveForCurrentContext();
                onSettingsChanged();
                playClickSound();
                return true;
            }
        }

        int startY = y + 18;
        int listH = h - 22;
        int rowH = 26;
        int rowY = startY - presetScrollOffset;

        for (var entry : allPresets.entrySet()) {
            var catId = entry.getKey();
            if (rowY + rowH >= startY && rowY <= startY + listH) {
                int delBtnW = 20;
                int delBtnH = 18;
                int delBtnX = x + w - 4 - delBtnW - 4;
                int delBtnY = rowY + 3;
                if (mouseX >= delBtnX && mouseX <= delBtnX + delBtnW && mouseY >= delBtnY && mouseY <= delBtnY + delBtnH) {
                    presetManager.removePreset(catId);
                    bm.saveForCurrentContext();
                    onSettingsChanged();
                    playClickSound();
                    return true;
                }
            }
            rowY += rowH;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        var presetManager = CategoryMachinePresetManager.getInstance();
        int totalH = presetManager.getAllPresets().size() * 26;
        int maxScroll = Math.max(0, totalH - 180);
        presetScrollOffset = (int) Math.max(0, Math.min(maxScroll, presetScrollOffset - delta * 20));
        return true;
    }
}
