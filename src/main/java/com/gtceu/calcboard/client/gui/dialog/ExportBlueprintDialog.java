package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.storage.BlueprintCodec;
import com.gtceu.calcboard.api.storage.BlueprintFileManager;
import com.gtceu.calcboard.api.storage.BlueprintMetadata;
import com.gtceu.calcboard.api.storage.BlueprintPackage;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.render.BoardTooltipRenderer;
import com.gtceu.calcboard.client.gui.render.IngredientRenderer;
import com.gtceu.calcboard.client.gui.widget.BoardToast;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.List;

/**
 * Modal dialog for packaging and exporting flow blueprints with customizable title and metadata.
 */
public class ExportBlueprintDialog {

    private final BoardScreen screen;
    private boolean visible = false;
    private EditBox titleInput;
    private EditBox descInput;
    private BlueprintMetadata previewMeta;

    public ExportBlueprintDialog(BoardScreen screen) {
        this.screen = screen;
    }

    public void open() {
        this.visible = true;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int screenWidth = getScreenWidth();
        int screenHeight = getScreenHeight();
        int dialogW = 340;
        int dialogH = 220;
        int x = (screenWidth - dialogW) / 2;
        int y = (screenHeight - dialogH) / 2;

        BoardPage page = BoardManager.getInstance().getActivePage();
        String defaultTitle = (page != null && page.getName() != null && !page.getName().isEmpty())
                ? page.getName() : "Factory Blueprint";

        String author = mc.player != null ? mc.player.getScoreboardName() : "Player";
        FlowGraph graph = screen.getGraph();
        this.previewMeta = BlueprintMetadata.fromGraph(graph, defaultTitle, "", author);

        this.titleInput = new EditBox(font, x + 14, y + 38, dialogW - 28, 16, Component.translatable("gui.gtcalcboard.dialog.bp_title_label"));
        this.titleInput.setMaxLength(64);
        this.titleInput.setValue(defaultTitle);
        this.titleInput.setFocused(true);

        this.descInput = new EditBox(font, x + 14, y + 72, dialogW - 28, 16, Component.translatable("gui.gtcalcboard.dialog.bp_desc_label"));
        this.descInput.setMaxLength(120);
        this.descInput.setHint(Component.translatable("gui.gtcalcboard.dialog.export_bp_desc_hint"));
        this.descInput.setFocused(false);
    }

    public void close() {
        this.visible = false;
        this.titleInput = null;
        this.descInput = null;
        this.previewMeta = null;
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
        int dialogH = 220;
        int x = (screenWidth - dialogW) / 2;
        int y = (screenHeight - dialogH) / 2;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500.0f);

        // Dim background
        graphics.fill(0, 0, screenWidth, screenHeight, 0x88000000);

        // Dialog background frame
        graphics.fill(x, y, x + dialogW, y + dialogH, 0xF0181A24);
        graphics.renderOutline(x, y, dialogW, dialogH, 0xFF4F5B73);

        // Title bar
        graphics.fill(x, y, x + dialogW, y + 22, 0xFF222634);
        String header = "» " + Component.translatable("gui.gtcalcboard.dialog.export_bp_title").getString();
        graphics.drawString(font, header, x + 10, y + 7, 0xFFFFFFFF, false);

        // Open Folder button in title bar
        int folderBtnX = x + dialogW - 80;
        int folderBtnY = y + 3;
        int folderBtnW = 60;
        int folderBtnH = 16;
        boolean folderHover = mouseX >= folderBtnX && mouseX <= folderBtnX + folderBtnW && mouseY >= folderBtnY && mouseY <= folderBtnY + folderBtnH;
        graphics.fill(folderBtnX, folderBtnY, folderBtnX + folderBtnW, folderBtnY + folderBtnH, folderHover ? 0xFF2B4466 : 0xFF1C2C44);
        graphics.renderOutline(folderBtnX, folderBtnY, folderBtnW, folderBtnH, 0xFF355580);
        graphics.drawCenteredString(font, "≡ " + Component.translatable("gui.gtcalcboard.dialog.btn_open_folder").getString(), folderBtnX + folderBtnW / 2, folderBtnY + 4, 0xFF66DDFF);

        // Close button (X)
        int closeBtnX = x + dialogW - 18;
        int closeBtnY = y + 3;
        boolean closeHover = mouseX >= closeBtnX && mouseX <= closeBtnX + 14 && mouseY >= closeBtnY && mouseY <= closeBtnY + 16;
        graphics.fill(closeBtnX, closeBtnY, closeBtnX + 14, closeBtnY + 16, closeHover ? 0xFF882222 : 0xFF442222);
        graphics.drawCenteredString(font, "✕", closeBtnX + 7, closeBtnY + 4, 0xFFFFFFFF);

        // Inputs
        graphics.drawString(font, "§7" + Component.translatable("gui.gtcalcboard.dialog.bp_title_label").getString(), x + 14, y + 28, 0xFFAAAAAA, false);
        if (titleInput != null) {
            titleInput.setX(x + 14);
            titleInput.setY(y + 38);
            titleInput.render(graphics, mouseX, mouseY, partialTicks);
        }

        graphics.drawString(font, "§7" + Component.translatable("gui.gtcalcboard.dialog.bp_desc_label").getString(), x + 14, y + 61, 0xFFAAAAAA, false);
        if (descInput != null) {
            descInput.setX(x + 14);
            descInput.setY(y + 72);
            descInput.render(graphics, mouseX, mouseY, partialTicks);
        }

        // Summary Preview Panel
        int panelY = y + 96;
        int panelH = 74;
        graphics.fill(x + 14, panelY, x + dialogW - 14, panelY + panelH, 0x6610141D);
        graphics.renderOutline(x + 14, panelY, dialogW - 28, panelH, 0xFF353E50);

        if (previewMeta != null) {
            int nodeCount = previewMeta.getNodeCount();
            int machineCount = previewMeta.getMachineCount();
            int connCount = previewMeta.getConnectionCount();
            String statsStr;
            if (machineCount > 0 && machineCount != nodeCount) {
                statsStr = String.format(java.util.Locale.ROOT, "⚙ %d %s (%d %s), %d %s",
                        nodeCount, Component.translatable("gui.gtcalcboard.dialog.bp_nodes").getString(),
                        machineCount, Component.translatable("gui.gtcalcboard.dialog.bp_machines").getString(),
                        connCount, Component.translatable("gui.gtcalcboard.dialog.bp_wires").getString());
            } else {
                statsStr = String.format(java.util.Locale.ROOT, "⚙ %d %s, %d %s",
                        nodeCount, Component.translatable("gui.gtcalcboard.dialog.bp_nodes").getString(),
                        connCount, Component.translatable("gui.gtcalcboard.dialog.bp_wires").getString());
            }
            graphics.drawString(font, "§f" + statsStr, x + 20, panelY + 6, 0xFFFFFFFF, false);

            // Power summary
            double eu = previewMeta.getNetEuPerTick();
            double fe = previewMeta.getNetFePerTick();
            double su = previewMeta.getNetSuPerTick();

            String powerStr;
            int powerColor;
            if (Math.abs(eu) > 0.01) {
                if (eu > 0.01) {
                    powerStr = String.format(java.util.Locale.ROOT, "⚡ -%,.1f EU/t", eu);
                    powerColor = 0xFFFF8844;
                } else {
                    powerStr = String.format(java.util.Locale.ROOT, "⚡ +%,.1f EU/t", -eu);
                    powerColor = 0xFF55FF88;
                }
            } else if (Math.abs(fe) > 0.01) {
                if (fe > 0.01) {
                    powerStr = String.format(java.util.Locale.ROOT, "⚡ +%,.1f FE/t", fe);
                    powerColor = 0xFF55FF88;
                } else {
                    powerStr = String.format(java.util.Locale.ROOT, "⚡ -%,.1f FE/t", -fe);
                    powerColor = 0xFFFF8844;
                }
            } else if (Math.abs(su) > 0.01) {
                if (su > 0.01) {
                    powerStr = String.format(java.util.Locale.ROOT, "⚙ +%,.1f SU", su);
                    powerColor = 0xFF66DDFF;
                } else {
                    powerStr = String.format(java.util.Locale.ROOT, "⚙ -%,.1f SU", -su);
                    powerColor = 0xFFFF6B6B;
                }
            } else {
                powerStr = "⚡ 0 EU/t";
                powerColor = 0xFFAAAAAA;
            }
            graphics.drawString(font, powerStr, x + 20, panelY + 18, powerColor, false);

            // Primary Outputs preview
            List<IngredientStack> outputs = previewMeta.getPrimaryOutputs();
            graphics.drawString(font, "§7" + Component.translatable("gui.gtcalcboard.dialog.bp_outputs").getString() + ":", x + 20, panelY + 32, 0xFFAAAAAA, false);
            if (outputs.isEmpty()) {
                graphics.drawString(font, "§8(None)", x + 20, panelY + 48, 0xFF888888, false);
            } else {
                int iconX = x + 20;
                int iconY = panelY + 46;
                for (int i = 0; i < Math.min(6, outputs.size()); i++) {
                    IngredientStack stack = outputs.get(i);
                    graphics.fill(iconX - 1, iconY - 1, iconX + 17, iconY + 17, 0x44222222);
                    graphics.renderOutline(iconX - 1, iconY - 1, 18, 18, 0xFF445566);
                    IngredientRenderer.render(graphics, stack, iconX, iconY);

                    // Tooltip on hover
                    if (mouseX >= iconX && mouseX <= iconX + 16 && mouseY >= iconY && mouseY <= iconY + 16) {
                        BoardTooltipRenderer.renderTooltip(graphics, font, Component.literal(stack.getDisplayName() + String.format(" (%,.2f/s)", stack.getAmount())), mouseX, mouseY, screenWidth, screenHeight);
                    }
                    iconX += 20;
                }
            }
        }

        // Action Buttons:
        // 1. Copy to Clipboard
        int btnY = y + dialogH - 28;
        int copyBtnX = x + 14;
        int copyBtnW = 120;
        int copyBtnH = 20;
        boolean copyHover = mouseX >= copyBtnX && mouseX <= copyBtnX + copyBtnW && mouseY >= btnY && mouseY <= btnY + copyBtnH;
        graphics.fill(copyBtnX, btnY, copyBtnX + copyBtnW, btnY + copyBtnH, copyHover ? 0xFF2A6840 : 0xFF1E4D2F);
        graphics.renderOutline(copyBtnX, btnY, copyBtnW, copyBtnH, 0xFF359050);
        graphics.drawCenteredString(font, "» " + Component.translatable("gui.gtcalcboard.dialog.btn_copy_clipboard").getString(), copyBtnX + copyBtnW / 2, btnY + 6, 0xFFFFFFFF);

        // 2. Save to Disk
        int saveDiskBtnX = copyBtnX + copyBtnW + 6;
        int saveDiskBtnW = 115;
        int saveDiskBtnH = 20;
        boolean saveDiskHover = mouseX >= saveDiskBtnX && mouseX <= saveDiskBtnX + saveDiskBtnW && mouseY >= btnY && mouseY <= btnY + saveDiskBtnH;
        graphics.fill(saveDiskBtnX, btnY, saveDiskBtnX + saveDiskBtnW, btnY + saveDiskBtnH, saveDiskHover ? 0xFF2B4466 : 0xFF1C2C44);
        graphics.renderOutline(saveDiskBtnX, btnY, saveDiskBtnW, saveDiskBtnH, 0xFF355580);
        graphics.drawCenteredString(font, "✓ " + Component.translatable("gui.gtcalcboard.dialog.btn_save_to_disk").getString(), saveDiskBtnX + saveDiskBtnW / 2, btnY + 6, 0xFF66DDFF);

        // 3. Cancel Button
        int cancelBtnX = saveDiskBtnX + saveDiskBtnW + 6;
        int cancelBtnW = dialogW - 28 - copyBtnW - saveDiskBtnW - 12;
        int cancelBtnH = 20;
        boolean cancelHover = mouseX >= cancelBtnX && mouseX <= cancelBtnX + cancelBtnW && mouseY >= btnY && mouseY <= btnY + cancelBtnH;
        graphics.fill(cancelBtnX, btnY, cancelBtnX + cancelBtnW, btnY + cancelBtnH, cancelHover ? 0xFF4A556B : 0xFF2A313E);
        graphics.renderOutline(cancelBtnX, btnY, cancelBtnW, cancelBtnH, 0xFF454E62);
        graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.cancel").getString(), cancelBtnX + cancelBtnW / 2, btnY + 6, 0xFFE0E6F0);

        graphics.pose().popPose();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = getScreenWidth();
        int screenHeight = getScreenHeight();
        int dialogW = 340;
        int dialogH = 220;
        int x = (screenWidth - dialogW) / 2;
        int y = (screenHeight - dialogH) / 2;

        // Close button (X)
        int closeBtnX = x + dialogW - 18;
        int closeBtnY = y + 3;
        if (mouseX >= closeBtnX && mouseX <= closeBtnX + 14 && mouseY >= closeBtnY && mouseY <= closeBtnY + 16) {
            close();
            return true;
        }

        // Open Folder button
        int folderBtnX = x + dialogW - 80;
        int folderBtnY = y + 3;
        int folderBtnW = 60;
        int folderBtnH = 16;
        if (mouseX >= folderBtnX && mouseX <= folderBtnX + folderBtnW && mouseY >= folderBtnY && mouseY <= folderBtnY + folderBtnH) {
            com.gtceu.calcboard.api.storage.BlueprintFileManager.openBlueprintsFolder();
            return true;
        }

        if (titleInput != null) {
            boolean tClicked = titleInput.mouseClicked(mouseX, mouseY, button);
            titleInput.setFocused(tClicked);
            if (tClicked) {
                if (descInput != null) descInput.setFocused(false);
                return true;
            }
        }

        if (descInput != null) {
            boolean dClicked = descInput.mouseClicked(mouseX, mouseY, button);
            descInput.setFocused(dClicked);
            if (dClicked) {
                if (titleInput != null) titleInput.setFocused(false);
                return true;
            }
        }

        int btnY = y + dialogH - 28;
        int copyBtnX = x + 14;
        int copyBtnW = 120;
        int copyBtnH = 20;
        if (mouseX >= copyBtnX && mouseX <= copyBtnX + copyBtnW && mouseY >= btnY && mouseY <= btnY + copyBtnH) {
            commitExportClipboard();
            return true;
        }

        int saveDiskBtnX = copyBtnX + copyBtnW + 6;
        int saveDiskBtnW = 115;
        int saveDiskBtnH = 20;
        if (mouseX >= saveDiskBtnX && mouseX <= saveDiskBtnX + saveDiskBtnW && mouseY >= btnY && mouseY <= btnY + saveDiskBtnH) {
            commitExportDisk();
            return true;
        }

        int cancelBtnX = saveDiskBtnX + saveDiskBtnW + 6;
        int cancelBtnW = dialogW - 28 - copyBtnW - saveDiskBtnW - 12;
        int cancelBtnH = 20;
        if (mouseX >= cancelBtnX && mouseX <= cancelBtnX + cancelBtnW && mouseY >= btnY && mouseY <= btnY + cancelBtnH) {
            close();
            return true;
        }

        return true; // Block clicks outside
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            commitExportClipboard();
            return true;
        }
        if (titleInput != null && titleInput.isFocused()) {
            titleInput.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (descInput != null && descInput.isFocused()) {
            descInput.keyPressed(keyCode, scanCode, modifiers);
            return true;
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

        return true;
    }

    private void commitExportClipboard() {
        Minecraft mc = Minecraft.getInstance();
        String title = titleInput != null ? titleInput.getValue().trim() : "";
        if (title.isEmpty()) title = "Factory Blueprint";
        String desc = descInput != null ? descInput.getValue().trim() : "";
        String author = mc.player != null ? mc.player.getScoreboardName() : "Player";

        String code = BlueprintCodec.exportToString(screen.getGraph(), title, desc, author, screen.getPanX(), screen.getPanY(), screen.getZoom());
        mc.keyboardHandler.setClipboard(code);

        BoardToast.show(Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.copy_success")));
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.3F));
        close();
    }

    private void commitExportDisk() {
        Minecraft mc = Minecraft.getInstance();
        String title = titleInput != null ? titleInput.getValue().trim() : "";
        if (title.isEmpty()) title = "Factory Blueprint";
        String desc = descInput != null ? descInput.getValue().trim() : "";
        String author = mc.player != null ? mc.player.getScoreboardName() : "Player";

        BlueprintMetadata meta = BlueprintMetadata.fromGraph(screen.getGraph(), title, desc, author);
        BlueprintPackage pkg = new BlueprintPackage(meta, screen.getGraph(), screen.getPanX(), screen.getPanY(), screen.getZoom());
        File savedFile = com.gtceu.calcboard.api.storage.BlueprintFileManager.saveBlueprint(title, pkg);

        if (savedFile != null) {
            BoardToast.show(Component.literal("§a✓ ").append(Component.translatable("message.gtcalcboard.disk_save_success", savedFile.getName())));
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2F));
            close();
        } else {
            BoardToast.show(Component.literal("§c✖ ").append(Component.translatable("message.gtcalcboard.disk_save_fail")));
        }
    }

    private int getScreenWidth() {
        return screen != null && screen.width > 0 ? screen.width : Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    private int getScreenHeight() {
        return screen != null && screen.height > 0 ? screen.height : Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }
}
