package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.api.storage.BlueprintFileManager;
import com.gtceu.calcboard.api.storage.FolderBlueprintCodec;
import com.gtceu.calcboard.api.storage.FolderBlueprintPackage;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.widget.BoardToast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.List;

public class ExportFolderDialog {
    private final BoardScreen screen;
    private boolean visible = false;
    private String folderPath = "";
    private EditBox titleInput;
    private EditBox descInput;
    private FolderBlueprintPackage currentPackage;
    private double scrollY = 0;
    private double maxScrollY = 0;

    public ExportFolderDialog(BoardScreen screen) {
        this.screen = screen;
    }

    public void open(String folderPath) {
        this.folderPath = folderPath != null ? folderPath.trim() : "";
        this.visible = true;
        this.scrollY = 0;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int dialogW = 340;
        int dialogH = 250;
        int x = (screenWidth - dialogW) / 2;
        int y = (screenHeight - dialogH) / 2;

        String author = mc.player != null ? mc.player.getScoreboardName() : "Player";
        this.currentPackage = FolderBlueprintCodec.createFolderPackage(this.folderPath, this.folderPath, "", author);

        this.titleInput = new EditBox(font, x + 14, y + 30, dialogW - 28, 16, Component.translatable("gui.gtcalcboard.dialog.bp_title_label"));
        this.titleInput.setMaxLength(64);
        this.titleInput.setValue(this.currentPackage.getRootFolderName());
        this.titleInput.setFocused(true);

        this.descInput = new EditBox(font, x + 14, y + 52, dialogW - 28, 16, Component.translatable("gui.gtcalcboard.dialog.bp_desc_label"));
        this.descInput.setMaxLength(120);
        this.descInput.setHint(Component.translatable("gui.gtcalcboard.dialog.export_bp_desc_hint"));
        this.descInput.setFocused(false);
    }

    public void close() {
        this.visible = false;
        this.titleInput = null;
        this.descInput = null;
        this.currentPackage = null;
    }

    public boolean isVisible() {
        return visible;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!visible || currentPackage == null) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int dialogW = 340;
        int dialogH = 250;
        int x = (screenWidth - dialogW) / 2;
        int y = (screenHeight - dialogH) / 2;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500.0f);

        graphics.fill(0, 0, screenWidth, screenHeight, 0x88000000);
        graphics.fill(x, y, x + dialogW, y + dialogH, 0xF0181A24);
        graphics.renderOutline(x, y, dialogW, dialogH, 0xFF4F5B73);

        // Header
        graphics.fill(x, y, x + dialogW, y + 22, 0xFF222634);
        String header = "📤 " + Component.translatable("gui.gtcalcboard.dialog.export_folder_title").getString();
        graphics.drawString(font, header, x + 10, y + 7, 0xFFFFFFFF, false);

        int closeBtnX = x + dialogW - 18;
        int closeBtnY = y + 3;
        boolean closeHover = mouseX >= closeBtnX && mouseX <= closeBtnX + 14 && mouseY >= closeBtnY && mouseY <= closeBtnY + 16;
        graphics.fill(closeBtnX, closeBtnY, closeBtnX + 14, closeBtnY + 16, closeHover ? 0xFF882222 : 0xFF442222);
        graphics.drawCenteredString(font, "✕", closeBtnX + 7, closeBtnY + 4, 0xFFFFFFFF);

        // Inputs
        if (titleInput != null) titleInput.render(graphics, mouseX, mouseY, partialTicks);
        if (descInput != null) descInput.render(graphics, mouseX, mouseY, partialTicks);

        // Included Pages and Subfolders List View
        int listX = x + 14;
        int listY = y + 74;
        int listW = dialogW - 28;
        int listH = 138;

        graphics.fill(listX, listY, listX + listW, listY + listH, 0xFF10131B);
        graphics.renderOutline(listX, listY, listW, listH, 0xFF2B3347);
        graphics.enableScissor(listX + 1, listY + 1, listX + listW - 1, listY + listH - 1);

        int curY = listY + 3 - (int) scrollY;
        List<FolderBlueprintPackage.FolderPageEntry> pages = currentPackage.getPages();
        for (FolderBlueprintPackage.FolderPageEntry entry : pages) {
            boolean itemHover = mouseX >= listX + 2 && mouseX <= listX + listW - 2 && mouseY >= curY && mouseY <= curY + 18;
            if (itemHover) {
                graphics.fill(listX + 2, curY, listX + listW - 2, curY + 18, 0xFF1C2433);
            }

            ItemStack icon = entry.icon();
            if (icon != null && !icon.isEmpty()) {
                graphics.renderItem(icon, listX + 4, curY + 1);
            } else {
                graphics.drawString(font, "§7📄", listX + 4, curY + 4, 0xFFFFFFFF, false);
            }

            String pathSuffix = entry.relativeFolderPath().isEmpty() ? "" : " §8(📁 " + entry.relativeFolderPath() + ")";
            graphics.drawString(font, "§f" + entry.name() + pathSuffix, listX + 24, curY + 5, 0xFFFFFFFF, false);

            int n = entry.graph() != null ? entry.graph().getNodes().size() : 0;
            int w = entry.graph() != null ? entry.graph().getConnections().size() : 0;
            String statStr = "§e⚡" + n + " §b〰" + w;
            int statW = font.width(statStr);
            graphics.drawString(font, statStr, listX + listW - statW - 6, curY + 5, 0xFFFFFFFF, false);

            curY += 20;
        }

        int totalH = (curY + (int) scrollY) - listY;
        this.maxScrollY = Math.max(0, totalH - listH);
        graphics.disableScissor();

        // Buttons
        int btnY = y + dialogH - 28;
        int btnW = 145;
        int btnH = 20;

        int clipBtnX = x + 14;
        boolean clipHover = mouseX >= clipBtnX && mouseX <= clipBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        graphics.fill(clipBtnX, btnY, clipBtnX + btnW, btnY + btnH, clipHover ? 0xFF2B5270 : 0xFF1A3347);
        graphics.renderOutline(clipBtnX, btnY, btnW, btnH, clipHover ? 0xFF4A90E2 : 0xFF2A5A8C);
        graphics.drawCenteredString(font, "📋 " + Component.translatable("gui.gtcalcboard.dialog.btn_copy_clipboard").getString(), clipBtnX + btnW / 2, btnY + 6, 0xFFFFFFFF);

        int saveBtnX = x + dialogW - 14 - btnW;
        boolean saveHover = mouseX >= saveBtnX && mouseX <= saveBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        graphics.fill(saveBtnX, btnY, saveBtnX + btnW, btnY + btnH, saveHover ? 0xFF2D6A4F : 0xFF1B4332);
        graphics.renderOutline(saveBtnX, btnY, btnW, btnH, saveHover ? 0xFF52B788 : 0xFF2D6A4F);
        graphics.drawCenteredString(font, "💾 " + Component.translatable("gui.gtcalcboard.dialog.btn_save_to_disk").getString(), saveBtnX + btnW / 2, btnY + 6, 0xFFFFFFFF);

        graphics.pose().popPose();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || currentPackage == null) return false;

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int dialogW = 340;
        int dialogH = 250;
        int x = (screenWidth - dialogW) / 2;
        int y = (screenHeight - dialogH) / 2;

        if (mouseX < x || mouseX > x + dialogW || mouseY < y || mouseY > y + dialogH) {
            close();
            return true;
        }

        int closeBtnX = x + dialogW - 18;
        int closeBtnY = y + 3;
        if (mouseX >= closeBtnX && mouseX <= closeBtnX + 14 && mouseY >= closeBtnY && mouseY <= closeBtnY + 16) {
            close();
            playClickSound();
            return true;
        }

        if (titleInput != null && titleInput.mouseClicked(mouseX, mouseY, button)) return true;
        if (descInput != null && descInput.mouseClicked(mouseX, mouseY, button)) return true;

        int btnY = y + dialogH - 28;
        int btnW = 145;
        int btnH = 20;
        int clipBtnX = x + 14;
        int saveBtnX = x + dialogW - 14 - btnW;

        if (button == 0 && mouseX >= clipBtnX && mouseX <= clipBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            exportToClipboard();
            return true;
        }

        if (button == 0 && mouseX >= saveBtnX && mouseX <= saveBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            exportToFile();
            return true;
        }

        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible || currentPackage == null) return false;
        scrollY = Math.max(0, Math.min(maxScrollY, scrollY - delta * 15.0));
        return true;
    }

    private void exportToClipboard() {
        syncPackageMeta();
        String code = FolderBlueprintCodec.exportToString(currentPackage);
        Minecraft.getInstance().keyboardHandler.setClipboard(code);
        BoardToast.show(Component.translatable("gui.gtcalcboard.toast.folder_export_success"));
        playClickSound();
        close();
    }

    private void exportToFile() {
        syncPackageMeta();
        File saved = BlueprintFileManager.saveFolderBlueprint(currentPackage.getRootFolderName(), currentPackage);
        if (saved != null) {
            BoardToast.show(Component.translatable("gui.gtcalcboard.toast.folder_save_disk_success", saved.getName()));
        }
        playClickSound();
        close();
    }

    private void syncPackageMeta() {
        if (titleInput != null && !titleInput.getValue().trim().isEmpty()) {
            currentPackage.setRootFolderName(titleInput.getValue().trim());
        }
        if (descInput != null) {
            currentPackage.setDescription(descInput.getValue().trim());
        }
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            exportToClipboard();
            return true;
        }
        if (titleInput != null && titleInput.isFocused()) {
            return titleInput.keyPressed(keyCode, scanCode, modifiers);
        }
        if (descInput != null && descInput.isFocused()) {
            return descInput.keyPressed(keyCode, scanCode, modifiers);
        }
        return true;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible) return false;
        if (titleInput != null && titleInput.isFocused()) {
            return titleInput.charTyped(codePoint, modifiers);
        }
        if (descInput != null && descInput.isFocused()) {
            return descInput.charTyped(codePoint, modifiers);
        }
        return false;
    }

    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
        );
    }
}
