package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.api.storage.BlueprintCodec;
import com.gtceu.calcboard.api.storage.BlueprintFileManager;
import com.gtceu.calcboard.api.storage.BlueprintFileManager.SavedBlueprintEntry;
import com.gtceu.calcboard.api.storage.BlueprintMetadata;
import com.gtceu.calcboard.api.storage.BlueprintPackage;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.widget.BoardToast;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Modal dialog for browsing, loading, copying, and managing saved blueprint files (*.gtcb) from disk.
 */
public class DiskBlueprintsDialog {

    private final BoardScreen screen;
    private boolean visible = false;
    private EditBox searchInput;
    private List<SavedBlueprintEntry> allEntries = new ArrayList<>();
    private List<SavedBlueprintEntry> filteredEntries = new ArrayList<>();
    private int scrollOffset = 0;
    private File pendingDeleteFile = null;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private static final int DIALOG_W = 360;
    private static final int DIALOG_H = 230;
    private static final int ITEM_H = 36;
    private static final int VISIBLE_ITEMS = 4;

    public DiskBlueprintsDialog(BoardScreen screen) {
        this.screen = screen;
    }

    public void open() {
        this.visible = true;
        this.scrollOffset = 0;
        this.pendingDeleteFile = null;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int screenWidth = getScreenWidth();
        int screenHeight = getScreenHeight();
        int x = (screenWidth - DIALOG_W) / 2;
        int y = (screenHeight - DIALOG_H) / 2;

        this.searchInput = new EditBox(font, x + 12, y + 26, DIALOG_W - 100, 16, Component.translatable("gui.gtcalcboard.dialog.disk_bp_search"));
        this.searchInput.setHint(Component.translatable("gui.gtcalcboard.dialog.disk_bp_search_hint"));
        this.searchInput.setResponder(s -> updateFilter());

        refreshList();
    }

    public void close() {
        this.visible = false;
        this.searchInput = null;
        this.pendingDeleteFile = null;
    }

    public boolean isVisible() {
        return visible;
    }

    public void refreshList() {
        this.allEntries = BlueprintFileManager.listSavedBlueprints();
        updateFilter();
    }

    private void updateFilter() {
        String query = searchInput != null ? searchInput.getValue().trim().toLowerCase(Locale.ROOT) : "";
        filteredEntries = new ArrayList<>();
        for (SavedBlueprintEntry entry : allEntries) {
            String title = entry.metadata() != null && entry.metadata().getTitle() != null
                    ? entry.metadata().getTitle().toLowerCase(Locale.ROOT) : "";
            String fileName = entry.fileName().toLowerCase(Locale.ROOT);
            if (query.isEmpty() || title.contains(query) || fileName.contains(query)) {
                filteredEntries.add(entry);
            }
        }
        int maxScroll = Math.max(0, filteredEntries.size() - VISIBLE_ITEMS);
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int screenWidth = getScreenWidth();
        int screenHeight = getScreenHeight();
        int x = (screenWidth - DIALOG_W) / 2;
        int y = (screenHeight - DIALOG_H) / 2;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500.0f);

        // Dim background
        graphics.fill(0, 0, screenWidth, screenHeight, 0x88000000);

        // Dialog background frame
        graphics.fill(x, y, x + DIALOG_W, y + DIALOG_H, 0xF0181A24);
        graphics.renderOutline(x, y, DIALOG_W, DIALOG_H, 0xFF4F5B73);

        // Title bar
        graphics.fill(x, y, x + DIALOG_W, y + 22, 0xFF222634);
        String header = "✓ " + Component.translatable("gui.gtcalcboard.dialog.disk_bp_title").getString();
        graphics.drawString(font, header, x + 10, y + 7, 0xFFFFFFFF, false);

        // Open Folder button in title bar
        int folderBtnX = x + DIALOG_W - 86;
        int folderBtnY = y + 3;
        int folderBtnW = 66;
        int folderBtnH = 16;
        boolean folderHover = mouseX >= folderBtnX && mouseX <= folderBtnX + folderBtnW && mouseY >= folderBtnY && mouseY <= folderBtnY + folderBtnH;
        graphics.fill(folderBtnX, folderBtnY, folderBtnX + folderBtnW, folderBtnY + folderBtnH, folderHover ? 0xFF2B4466 : 0xFF1C2C44);
        graphics.renderOutline(folderBtnX, folderBtnY, folderBtnW, folderBtnH, 0xFF355580);
        graphics.drawCenteredString(font, "≡ " + Component.translatable("gui.gtcalcboard.dialog.btn_open_folder").getString(), folderBtnX + folderBtnW / 2, folderBtnY + 4, 0xFF66DDFF);

        // Close button (X)
        int closeBtnX = x + DIALOG_W - 18;
        int closeBtnY = y + 3;
        boolean closeHover = mouseX >= closeBtnX && mouseX <= closeBtnX + 14 && mouseY >= closeBtnY && mouseY <= closeBtnY + 16;
        graphics.fill(closeBtnX, closeBtnY, closeBtnX + 14, closeBtnY + 16, closeHover ? 0xFF882222 : 0xFF442222);
        graphics.drawCenteredString(font, "✕", closeBtnX + 7, closeBtnY + 4, 0xFFFFFFFF);

        // Search Input
        if (searchInput != null) {
            searchInput.setX(x + 12);
            searchInput.setY(y + 26);
            searchInput.render(graphics, mouseX, mouseY, partialTicks);
        }

        // Refresh button next to search
        int refreshBtnX = x + DIALOG_W - 84;
        int refreshBtnY = y + 26;
        int refreshBtnW = 72;
        int refreshBtnH = 16;
        boolean refreshHover = mouseX >= refreshBtnX && mouseX <= refreshBtnX + refreshBtnW && mouseY >= refreshBtnY && mouseY <= refreshBtnY + refreshBtnH;
        graphics.fill(refreshBtnX, refreshBtnY, refreshBtnX + refreshBtnW, refreshBtnY + refreshBtnH, refreshHover ? 0xFF3D4558 : 0xFF282D3B);
        graphics.renderOutline(refreshBtnX, refreshBtnY, refreshBtnW, refreshBtnH, 0xFF4F5B73);
        graphics.drawCenteredString(font, "⟲ " + Component.translatable("gui.gtcalcboard.dialog.btn_refresh").getString(), refreshBtnX + refreshBtnW / 2, refreshBtnY + 4, 0xFFE0E6F0);

        // List Area
        int listY = y + 46;
        int listH = DIALOG_H - 74;
        graphics.fill(x + 10, listY, x + DIALOG_W - 10, listY + listH, 0x6610141D);
        graphics.renderOutline(x + 10, listY, DIALOG_W - 20, listH, 0xFF353E50);

        if (filteredEntries.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.dialog.disk_bp_empty").getString(), x + DIALOG_W / 2, listY + listH / 2 - 4, 0xFF888888);
        } else {
            int maxScroll = Math.max(0, filteredEntries.size() - VISIBLE_ITEMS);
            if (scrollOffset > maxScroll) scrollOffset = maxScroll;

            for (int i = 0; i < VISIBLE_ITEMS; i++) {
                int idx = scrollOffset + i;
                if (idx >= filteredEntries.size()) break;

                SavedBlueprintEntry entry = filteredEntries.get(idx);
                int itemY = listY + 3 + i * (ITEM_H + 2);
                boolean itemHover = mouseX >= x + 12 && mouseX <= x + DIALOG_W - 12 && mouseY >= itemY && mouseY <= itemY + ITEM_H;

                graphics.fill(x + 12, itemY, x + DIALOG_W - 12, itemY + ITEM_H, itemHover ? 0xFF242A38 : 0xFF1E232E);
                graphics.renderOutline(x + 12, itemY, DIALOG_W - 24, ITEM_H, 0xFF353C4D);

                BlueprintMetadata meta = entry.metadata();
                String title = meta != null && meta.getTitle() != null && !meta.getTitle().isEmpty()
                        ? meta.getTitle() : entry.fileName();
                String dateStr = DATE_FORMAT.format(new Date(entry.lastModified()));

                // Title + Date
                graphics.drawString(font, "§f" + font.plainSubstrByWidth(title, 170), x + 16, itemY + 4, 0xFFFFFFFF, false);
                graphics.drawString(font, "§8" + dateStr, x + 16, itemY + 16, 0xFF888888, false);

                // Stats preview
                int nodeCount = meta != null ? meta.getNodeCount() : 0;
                int machineCount = meta != null ? meta.getMachineCount() : 0;
                String stats = machineCount > 0 && machineCount != nodeCount
                        ? String.format(Locale.ROOT, "⚙ %d (%d)", nodeCount, machineCount)
                        : String.format(Locale.ROOT, "⚙ %d", nodeCount);
                graphics.drawString(font, "§7" + stats, x + 16, itemY + 25, 0xFFAAAAAA, false);

                int loadBtnX = x + DIALOG_W - 130;
                int loadBtnY = itemY + 6;
                int loadBtnW = 38;
                int loadBtnH = 22;
                boolean loadHover = mouseX >= loadBtnX && mouseX <= loadBtnX + loadBtnW && mouseY >= loadBtnY && mouseY <= loadBtnY + loadBtnH;
                graphics.fill(loadBtnX, loadBtnY, loadBtnX + loadBtnW, loadBtnY + loadBtnH, loadHover ? 0xFF2A6840 : 0xFF1E4D2F);
                graphics.renderOutline(loadBtnX, loadBtnY, loadBtnW, loadBtnH, 0xFF359050);
                graphics.drawCenteredString(font, "«", loadBtnX + loadBtnW / 2, loadBtnY + 7, 0xFFFFFFFF);

                int copyBtnX = loadBtnX + loadBtnW + 4;
                int copyBtnW = 38;
                int copyBtnH = 22;
                boolean copyHover = mouseX >= copyBtnX && mouseX <= copyBtnX + copyBtnW && mouseY >= loadBtnY && mouseY <= loadBtnY + copyBtnH;
                graphics.fill(copyBtnX, loadBtnY, copyBtnX + copyBtnW, loadBtnY + copyBtnH, copyHover ? 0xFF2B4466 : 0xFF1C2C44);
                graphics.renderOutline(copyBtnX, loadBtnY, copyBtnW, copyBtnH, 0xFF355580);
                graphics.drawCenteredString(font, "»", copyBtnX + copyBtnW / 2, loadBtnY + 7, 0xFF66DDFF);

                int delBtnX = copyBtnX + copyBtnW + 4;
                int delBtnW = 28;
                int delBtnH = 22;
                boolean isPendingDel = pendingDeleteFile != null && pendingDeleteFile.equals(entry.file());
                boolean delHover = mouseX >= delBtnX && mouseX <= delBtnX + delBtnW && mouseY >= loadBtnY && mouseY <= loadBtnY + delBtnH;
                graphics.fill(delBtnX, loadBtnY, delBtnX + delBtnW, loadBtnY + delBtnH, isPendingDel ? 0xFF992222 : (delHover ? 0xFF662222 : 0xFF3D1C1C));
                graphics.renderOutline(delBtnX, loadBtnY, delBtnW, delBtnH, isPendingDel ? 0xFFFF4444 : 0xFF773B3B);
                graphics.drawCenteredString(font, isPendingDel ? "✔?" : "✖", delBtnX + delBtnW / 2, loadBtnY + 7, isPendingDel ? 0xFFFF8888 : 0xFFFF6B6B);
            }

            // Scroll indicator if needed
            if (filteredEntries.size() > VISIBLE_ITEMS) {
                int scrollBarH = listH - 6;
                int thumbH = Math.max(12, scrollBarH * VISIBLE_ITEMS / filteredEntries.size());
                int thumbY = listY + 3 + (scrollBarH - thumbH) * scrollOffset / maxScroll;
                graphics.fill(x + DIALOG_W - 14, listY + 3, x + DIALOG_W - 11, listY + listH - 3, 0x44000000);
                graphics.fill(x + DIALOG_W - 14, thumbY, x + DIALOG_W - 11, thumbY + thumbH, 0xFF667788);
            }
        }

        // Bottom action bar
        int btmY = y + DIALOG_H - 24;
        int saveCurBtnX = x + 12;
        int saveCurBtnW = 150;
        int saveCurBtnH = 18;
        boolean saveCurHover = mouseX >= saveCurBtnX && mouseX <= saveCurBtnX + saveCurBtnW && mouseY >= btmY && mouseY <= btmY + saveCurBtnH;
        graphics.fill(saveCurBtnX, btmY, saveCurBtnX + saveCurBtnW, btmY + saveCurBtnH, saveCurHover ? 0xFF2B4466 : 0xFF1C2C44);
        graphics.renderOutline(saveCurBtnX, btmY, saveCurBtnW, saveCurBtnH, 0xFF355580);
        graphics.drawCenteredString(font, "✓ " + Component.translatable("gui.gtcalcboard.dialog.btn_save_current_to_disk").getString(), saveCurBtnX + saveCurBtnW / 2, btmY + 5, 0xFF66DDFF);

        int closeBottomBtnX = x + DIALOG_W - 74;
        int closeBottomBtnW = 62;
        int closeBottomBtnH = 18;
        boolean closeBottomHover = mouseX >= closeBottomBtnX && mouseX <= closeBottomBtnX + closeBottomBtnW && mouseY >= btmY && mouseY <= btmY + closeBottomBtnH;
        graphics.fill(closeBottomBtnX, btmY, closeBottomBtnX + closeBottomBtnW, btmY + closeBottomBtnH, closeBottomHover ? 0xFF4A556B : 0xFF2A313E);
        graphics.renderOutline(closeBottomBtnX, btmY, closeBottomBtnW, closeBottomBtnH, 0xFF454E62);
        graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.cancel").getString(), closeBottomBtnX + closeBottomBtnW / 2, btmY + 5, 0xFFE0E6F0);

        graphics.pose().popPose();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = getScreenWidth();
        int screenHeight = getScreenHeight();
        int x = (screenWidth - DIALOG_W) / 2;
        int y = (screenHeight - DIALOG_H) / 2;

        // Close button (X)
        int closeBtnX = x + DIALOG_W - 18;
        int closeBtnY = y + 3;
        if (mouseX >= closeBtnX && mouseX <= closeBtnX + 14 && mouseY >= closeBtnY && mouseY <= closeBtnY + 16) {
            close();
            return true;
        }

        // Open Folder button
        int folderBtnX = x + DIALOG_W - 86;
        int folderBtnY = y + 3;
        int folderBtnW = 66;
        int folderBtnH = 16;
        if (mouseX >= folderBtnX && mouseX <= folderBtnX + folderBtnW && mouseY >= folderBtnY && mouseY <= folderBtnY + folderBtnH) {
            BlueprintFileManager.openBlueprintsFolder();
            return true;
        }

        // Search EditBox
        if (searchInput != null) {
            boolean clicked = searchInput.mouseClicked(mouseX, mouseY, button);
            searchInput.setFocused(clicked);
            if (clicked) return true;
        }

        // Refresh button
        int refreshBtnX = x + DIALOG_W - 84;
        int refreshBtnY = y + 26;
        int refreshBtnW = 72;
        int refreshBtnH = 16;
        if (mouseX >= refreshBtnX && mouseX <= refreshBtnX + refreshBtnW && mouseY >= refreshBtnY && mouseY <= refreshBtnY + refreshBtnH) {
            refreshList();
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        // List Item actions
        int listY = y + 46;
        for (int i = 0; i < VISIBLE_ITEMS; i++) {
            int idx = scrollOffset + i;
            if (idx >= filteredEntries.size()) break;

            SavedBlueprintEntry entry = filteredEntries.get(idx);
            int itemY = listY + 3 + i * (ITEM_H + 2);
            int loadBtnX = x + DIALOG_W - 130;
            int loadBtnY = itemY + 6;
            int loadBtnW = 38;
            int loadBtnH = 22;

            // Load («)
            if (mouseX >= loadBtnX && mouseX <= loadBtnX + loadBtnW && mouseY >= loadBtnY && mouseY <= loadBtnY + loadBtnH) {
                loadBlueprintEntry(entry);
                return true;
            }

            // Copy Code (»)
            int copyBtnX = loadBtnX + loadBtnW + 4;
            int copyBtnW = 38;
            if (mouseX >= copyBtnX && mouseX <= copyBtnX + copyBtnW && mouseY >= loadBtnY && mouseY <= loadBtnY + loadBtnH) {
                copyBlueprintCode(entry);
                return true;
            }

            // Delete (✖)
            int delBtnX = copyBtnX + copyBtnW + 4;
            int delBtnW = 28;
            if (mouseX >= delBtnX && mouseX <= delBtnX + delBtnW && mouseY >= loadBtnY && mouseY <= loadBtnY + loadBtnH) {
                if (pendingDeleteFile != null && pendingDeleteFile.equals(entry.file())) {
                    BlueprintFileManager.deleteBlueprint(entry.file());
                    pendingDeleteFile = null;
                    refreshList();
                    BoardToast.show(Component.literal("§c✖ ").append(Component.translatable("message.gtcalcboard.blueprint_deleted", entry.fileName())));
                    mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.ITEM_BREAK, 1.0F));
                } else {
                    pendingDeleteFile = entry.file();
                    mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
                return true;
            }
        }

        // Bottom buttons:
        // Save current board to disk
        int btmY = y + DIALOG_H - 24;
        int saveCurBtnX = x + 12;
        int saveCurBtnW = 150;
        int saveCurBtnH = 18;
        if (mouseX >= saveCurBtnX && mouseX <= saveCurBtnX + saveCurBtnW && mouseY >= btmY && mouseY <= btmY + saveCurBtnH) {
            close();
            if (screen.getExportBlueprintDialog() != null) {
                screen.getExportBlueprintDialog().open();
            }
            return true;
        }

        // Close bottom button
        int closeBottomBtnX = x + DIALOG_W - 74;
        int closeBottomBtnW = 62;
        int closeBottomBtnH = 18;
        if (mouseX >= closeBottomBtnX && mouseX <= closeBottomBtnX + closeBottomBtnW && mouseY >= btmY && mouseY <= btmY + closeBottomBtnH) {
            close();
            return true;
        }

        return true; // Block clicks outside modal
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible) return false;
        int maxScroll = Math.max(0, filteredEntries.size() - VISIBLE_ITEMS);
        if (maxScroll > 0) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.signum(delta)));
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (searchInput != null && searchInput.isFocused()) {
            return searchInput.keyPressed(keyCode, scanCode, modifiers);
        }
        return true;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible) return false;
        if (searchInput != null && searchInput.isFocused()) {
            return searchInput.charTyped(codePoint, modifiers);
        }
        return true;
    }

    private void loadBlueprintEntry(SavedBlueprintEntry entry) {
        com.gtceu.calcboard.api.storage.FolderBlueprintPackage folderPkg = BlueprintFileManager.loadFolderBlueprint(entry.file());
        if (folderPkg != null) {
            close();
            screen.openImportFolderDialog(folderPkg);
            return;
        }

        BlueprintPackage pkg = BlueprintFileManager.loadBlueprint(entry.file());
        if (pkg != null) {
            close();
            if (screen.getImportBlueprintDialog() != null) {
                screen.getImportBlueprintDialog().open(pkg);
            }
        } else {
            BoardToast.show(Component.literal("§c✖ ").append(Component.translatable("message.gtcalcboard.import_fail")));
        }
    }

    private void copyBlueprintCode(SavedBlueprintEntry entry) {
        com.gtceu.calcboard.api.storage.FolderBlueprintPackage folderPkg = BlueprintFileManager.loadFolderBlueprint(entry.file());
        if (folderPkg != null) {
            String code = com.gtceu.calcboard.api.storage.FolderBlueprintCodec.exportToString(folderPkg);
            Minecraft.getInstance().keyboardHandler.setClipboard(code);
            BoardToast.show(Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.copy_success")));
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.3F));
            return;
        }

        BlueprintPackage pkg = BlueprintFileManager.loadBlueprint(entry.file());
        if (pkg != null) {
            String title = pkg.getMetadata() != null ? pkg.getMetadata().getTitle() : entry.fileName();
            String desc = pkg.getMetadata() != null ? pkg.getMetadata().getDescription() : "";
            String author = pkg.getMetadata() != null ? pkg.getMetadata().getAuthor() : "";
            String code = BlueprintCodec.exportToString(pkg.getGraph(), title, desc, author, pkg.getPanX(), pkg.getPanY(), pkg.getZoom());
            Minecraft.getInstance().keyboardHandler.setClipboard(code);
            BoardToast.show(Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.copy_success")));
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.3F));
        }
    }

    private int getScreenWidth() {
        return screen != null && screen.width > 0 ? screen.width : Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    private int getScreenHeight() {
        return screen != null && screen.height > 0 ? screen.height : Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }
}
