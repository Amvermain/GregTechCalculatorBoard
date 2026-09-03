package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.FolderBlueprintCodec;
import com.gtceu.calcboard.api.storage.FolderBlueprintPackage;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.util.BoardScissorHelper;
import com.gtceu.calcboard.client.gui.widget.BoardToast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class ImportFolderDialog {
    private final BoardScreen screen;
    private boolean visible = false;
    private FolderBlueprintPackage currentPackage;
    private double scrollY = 0;
    private double maxScrollY = 0;

    public ImportFolderDialog(BoardScreen screen) {
        this.screen = screen;
    }

    public void open() {
        Minecraft mc = Minecraft.getInstance();
        String clipboard = mc.keyboardHandler.getClipboard();
        FolderBlueprintPackage pkg = null;
        if (clipboard != null && !clipboard.trim().isEmpty()) {
            pkg = FolderBlueprintCodec.importPackageFromString(clipboard.trim());
        }
        open(pkg);
    }

    public void open(FolderBlueprintPackage pkg) {
        this.currentPackage = pkg;
        this.visible = true;
        this.scrollY = 0;
        this.maxScrollY = 0;
    }

    public void close() {
        this.visible = false;
        this.currentPackage = null;
    }

    public boolean isVisible() {
        return visible;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int screenWidth = getScreenWidth();
        int screenHeight = getScreenHeight();
        int dialogW = 340;
        int dialogH = 230;
        int x = (screenWidth - dialogW) / 2;
        int y = (screenHeight - dialogH) / 2;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500.0f);

        graphics.fill(0, 0, screenWidth, screenHeight, 0x88000000);
        graphics.fill(x, y, x + dialogW, y + dialogH, 0xF0181A24);
        graphics.renderOutline(x, y, dialogW, dialogH, 0xFF4F5B73);

        // Header
        graphics.fill(x, y, x + dialogW, y + 22, 0xFF222634);
        String header = "📥 " + Component.translatable("gui.gtcalcboard.dialog.import_folder_title").getString();
        graphics.drawString(font, header, x + 10, y + 7, 0xFFFFFFFF, false);

        // Browse Disk Files button in title bar
        int diskBtnX = x + dialogW - 90;
        int diskBtnY = y + 3;
        int diskBtnW = 70;
        int diskBtnH = 16;
        boolean diskHover = mouseX >= diskBtnX && mouseX <= diskBtnX + diskBtnW && mouseY >= diskBtnY && mouseY <= diskBtnY + diskBtnH;
        graphics.fill(diskBtnX, diskBtnY, diskBtnX + diskBtnW, diskBtnY + diskBtnH, diskHover ? 0xFF2B4466 : 0xFF1C2C44);
        graphics.renderOutline(diskBtnX, diskBtnY, diskBtnW, diskBtnH, 0xFF355580);
        graphics.drawCenteredString(font, "📂 " + Component.translatable("gui.gtcalcboard.dialog.btn_disk_files").getString(), diskBtnX + diskBtnW / 2, diskBtnY + 4, 0xFF66DDFF);

        // Close button
        int closeBtnX = x + dialogW - 18;
        int closeBtnY = y + 3;
        boolean closeHover = mouseX >= closeBtnX && mouseX <= closeBtnX + 14 && mouseY >= closeBtnY && mouseY <= closeBtnY + 16;
        graphics.fill(closeBtnX, closeBtnY, closeBtnX + 14, closeBtnY + 16, closeHover ? 0xFF882222 : 0xFF442222);
        graphics.drawCenteredString(font, "✕", closeBtnX + 7, closeBtnY + 4, 0xFFFFFFFF);

        if (currentPackage == null) {
            renderEmptyClipboardState(graphics, font, x, y, dialogW, dialogH, mouseX, mouseY);
        } else {
            renderPackagePreviewState(graphics, font, x, y, dialogW, dialogH, mouseX, mouseY);
        }

        graphics.pose().popPose();
    }

    private void renderEmptyClipboardState(GuiGraphics graphics, Font font, int x, int y, int dialogW, int dialogH, int mouseX, int mouseY) {
        graphics.drawCenteredString(font, "§c" + Component.translatable("gui.gtcalcboard.quick_switcher.no_results").getString(), x + dialogW / 2, y + 55, 0xFFFFFFFF);
        graphics.drawCenteredString(font, "§7Copy a folder blueprint (GTFOLDER:...) or browse saved files", x + dialogW / 2, y + 75, 0xFFFFFFFF);

        int btnW = 140;
        int btnH = 22;
        int btnY = y + 120;

        int pasteBtnX = x + (dialogW - (btnW * 2 + 12)) / 2;
        boolean pasteHover = mouseX >= pasteBtnX && mouseX <= pasteBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        graphics.fill(pasteBtnX, btnY, pasteBtnX + btnW, btnY + btnH, pasteHover ? 0xFF2B5270 : 0xFF1A3347);
        graphics.renderOutline(pasteBtnX, btnY, btnW, btnH, pasteHover ? 0xFF4A90E2 : 0xFF2A5A8C);
        graphics.drawCenteredString(font, "📋 " + Component.translatable("gui.gtcalcboard.dialog.btn_paste_from_clipboard").getString(), pasteBtnX + btnW / 2, btnY + 7, 0xFFFFFFFF);

        int diskBrowseBtnX = pasteBtnX + btnW + 12;
        boolean diskBrowseHover = mouseX >= diskBrowseBtnX && mouseX <= diskBrowseBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        graphics.fill(diskBrowseBtnX, btnY, diskBrowseBtnX + btnW, btnY + btnH, diskBrowseHover ? 0xFF2B4466 : 0xFF1C2C44);
        graphics.renderOutline(diskBrowseBtnX, btnY, btnW, btnH, diskBrowseHover ? 0xFF66DDFF : 0xFF355580);
        graphics.drawCenteredString(font, "📂 " + Component.translatable("gui.gtcalcboard.dialog.btn_disk_files").getString(), diskBrowseBtnX + btnW / 2, btnY + 7, 0xFF66DDFF);
    }

    private void renderPackagePreviewState(GuiGraphics graphics, Font font, int x, int y, int dialogW, int dialogH, int mouseX, int mouseY) {
        String rootFolder = currentPackage.getRootFolderName();
        int pageCount = currentPackage.getPages().size();
        int totalNodes = currentPackage.getPages().stream().mapToInt(p -> p.graph() != null ? p.graph().getNodes().size() : 0).sum();
        int totalWires = currentPackage.getPages().stream().mapToInt(p -> p.graph() != null ? p.graph().getConnections().size() : 0).sum();

        graphics.drawString(font, "§e📁 §l" + rootFolder, x + 14, y + 28, 0xFFFFFFFF, false);
        graphics.drawString(font, "§7" + Component.translatable("gui.gtcalcboard.dialog.bp_author").getString() + ": §f" + currentPackage.getAuthor() + "  §8•  §e⚡ " + totalNodes + " §b〰 " + totalWires, x + 14, y + 42, 0xFFFFFFFF, false);

        int listX = x + 14;
        int listY = y + 56;
        int listW = dialogW - 28;
        int listH = 125;

        graphics.fill(listX, listY, listX + listW, listY + listH, 0xFF10131B);
        graphics.renderOutline(listX, listY, listW, listH, 0xFF2B3347);
        BoardScissorHelper.enableScissor(graphics, listX + 1, listY + 1, listX + listW - 1, listY + listH - 1);

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

            String pathSuffix = entry.relativeFolderPath().isEmpty() ? "" : " §8(" + entry.relativeFolderPath() + ")";
            graphics.drawString(font, "§f" + entry.name() + pathSuffix, listX + 24, curY + 5, 0xFFFFFFFF, false);
            curY += 20;
        }

        int totalH = (curY + (int) scrollY) - listY;
        this.maxScrollY = Math.max(0, totalH - listH);
        BoardScissorHelper.disableScissor(graphics);

        // Import Button
        int btnY = y + dialogH - 28;
        int btnW = dialogW - 28;
        int btnH = 20;
        boolean impHover = mouseX >= listX && mouseX <= listX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        graphics.fill(listX, btnY, listX + btnW, btnY + btnH, impHover ? 0xFF2D6A4F : 0xFF1B4332);
        graphics.renderOutline(listX, btnY, btnW, btnH, impHover ? 0xFF52B788 : 0xFF2D6A4F);
        graphics.drawCenteredString(font, "📥 " + Component.translatable("gui.gtcalcboard.dialog.btn_import_folder").getString(), listX + btnW / 2, btnY + 6, 0xFFFFFFFF);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = getScreenWidth();
        int screenHeight = getScreenHeight();
        int dialogW = 340;
        int dialogH = 230;
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

        int diskBtnX = x + dialogW - 90;
        int diskBtnY = y + 3;
        int diskBtnW = 70;
        int diskBtnH = 16;
        if (button == 0 && mouseX >= diskBtnX && mouseX <= diskBtnX + diskBtnW && mouseY >= diskBtnY && mouseY <= diskBtnY + diskBtnH) {
            close();
            if (screen.getDiskBlueprintsDialog() != null) {
                screen.getDiskBlueprintsDialog().open();
            }
            playClickSound();
            return true;
        }

        if (currentPackage == null) {
            int btnW = 140;
            int btnH = 22;
            int btnY = y + 120;
            int pasteBtnX = x + (dialogW - (btnW * 2 + 12)) / 2;
            int diskBrowseBtnX = pasteBtnX + btnW + 12;

            if (button == 0 && mouseX >= pasteBtnX && mouseX <= pasteBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                open();
                playClickSound();
                return true;
            }

            if (button == 0 && mouseX >= diskBrowseBtnX && mouseX <= diskBrowseBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                close();
                if (screen.getDiskBlueprintsDialog() != null) {
                    screen.getDiskBlueprintsDialog().open();
                }
                playClickSound();
                return true;
            }
        } else {
            int listX = x + 14;
            int btnY = y + dialogH - 28;
            int btnW = dialogW - 28;
            int btnH = 20;
            if (button == 0 && mouseX >= listX && mouseX <= listX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                executeImport();
                return true;
            }
        }

        return true;
    }

    private void executeImport() {
        if (currentPackage == null) return;
        int targetIdx = FolderBlueprintCodec.importFolderToBoardManager(currentPackage, "");
        if (targetIdx >= 0) {
            BoardManager.getInstance().switchPage(targetIdx);
            BoardToast.show(Component.translatable("gui.gtcalcboard.toast.folder_import_success", currentPackage.getRootFolderName(), String.valueOf(currentPackage.getPages().size())));
            screen.rebuildWidgets();
        }
        playClickSound();
        close();
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible || currentPackage == null) return false;
        scrollY = Math.max(0, Math.min(maxScrollY, scrollY - delta * 15.0));
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (currentPackage != null) {
                executeImport();
                return true;
            }
        }
        return true;
    }

    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
        );
    }

    private int getScreenWidth() {
        return screen != null && screen.width > 0 ? screen.width : Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    private int getScreenHeight() {
        return screen != null && screen.height > 0 ? screen.height : Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }
}
