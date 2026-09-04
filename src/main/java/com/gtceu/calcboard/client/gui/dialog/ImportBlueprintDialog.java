package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.storage.BlueprintMetadata;
import com.gtceu.calcboard.api.storage.BlueprintPackage;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.render.BoardTooltipRenderer;
import com.gtceu.calcboard.client.gui.render.IngredientRenderer;
import com.gtceu.calcboard.client.gui.widget.BoardToast;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Modal preview dialog for inspecting blueprint metadata and choosing how to import it (New Page vs Overwrite).
 */
public class ImportBlueprintDialog {

    private final BoardScreen screen;
    private boolean visible = false;
    private BlueprintPackage currentPackage;

    public ImportBlueprintDialog(BoardScreen screen) {
        this.screen = screen;
    }

    public void open(BlueprintPackage pkg) {
        if (pkg == null) return;
        this.currentPackage = pkg;
        this.visible = true;
    }

    public void close() {
        this.visible = false;
        this.currentPackage = null;
    }

    public boolean isVisible() {
        return visible;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!visible || currentPackage == null) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int screenWidth = getScreenWidth();
        int screenHeight = getScreenHeight();
        int dialogW = 330;
        int dialogH = 225;
        int x = (screenWidth - dialogW) / 2;
        int y = (screenHeight - dialogH) / 2;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500.0f);

        // Dim background
        graphics.fill(0, 0, screenWidth, screenHeight, 0x88000000);

        // Frame
        graphics.fill(x, y, x + dialogW, y + dialogH, 0xF0181A24);
        graphics.renderOutline(x, y, dialogW, dialogH, 0xFF4F5B73);

        // Title bar
        graphics.fill(x, y, x + dialogW, y + 22, 0xFF222634);
        String header = "« " + Component.translatable("gui.gtcalcboard.dialog.import_bp_title").getString();
        graphics.drawString(font, header, x + 10, y + 7, 0xFFFFFFFF, false);

        // Browse Disk Files button in title bar
        int diskBtnX = x + dialogW - 90;
        int diskBtnY = y + 3;
        int diskBtnW = 70;
        int diskBtnH = 16;
        boolean diskHover = mouseX >= diskBtnX && mouseX <= diskBtnX + diskBtnW && mouseY >= diskBtnY && mouseY <= diskBtnY + diskBtnH;
        graphics.fill(diskBtnX, diskBtnY, diskBtnX + diskBtnW, diskBtnY + diskBtnH, diskHover ? 0xFF2B4466 : 0xFF1C2C44);
        graphics.renderOutline(diskBtnX, diskBtnY, diskBtnW, diskBtnH, 0xFF355580);
        graphics.drawCenteredString(font, "≡ " + Component.translatable("gui.gtcalcboard.dialog.btn_disk_files").getString(), diskBtnX + diskBtnW / 2, diskBtnY + 4, 0xFF66DDFF);

        // Close button (X)
        int closeBtnX = x + dialogW - 18;
        int closeBtnY = y + 3;
        boolean closeHover = mouseX >= closeBtnX && mouseX <= closeBtnX + 14 && mouseY >= closeBtnY && mouseY <= closeBtnY + 16;
        graphics.fill(closeBtnX, closeBtnY, closeBtnX + 14, closeBtnY + 16, closeHover ? 0xFF882222 : 0xFF442222);
        graphics.drawCenteredString(font, "✕", closeBtnX + 7, closeBtnY + 4, 0xFFFFFFFF);

        BlueprintMetadata meta = currentPackage.getMetadata();

        // Blueprint Title & Author
        String bpTitle = meta != null && meta.getTitle() != null && !meta.getTitle().isEmpty()
                ? meta.getTitle() : "Factory Blueprint";
        graphics.drawString(font, "§e★ §l" + bpTitle, x + 14, y + 28, 0xFFFFFFFF, false);

        String author = meta != null ? meta.getAuthor() : "";
        if (author != null && !author.isEmpty()) {
            String authorStr = "§7by §f" + author;
            graphics.drawString(font, authorStr, x + dialogW - 14 - font.width(authorStr), y + 28, 0xFFAAAAAA, false);
        }

        // Description
        String desc = meta != null ? meta.getDescription() : "";
        if (desc != null && !desc.isEmpty()) {
            graphics.drawString(font, "§8\"" + desc + "\"", x + 14, y + 40, 0xFF99AABB, false);
        }

        // Specifications Card
        int cardY = y + 54;
        int cardH = 92;
        graphics.fill(x + 14, cardY, x + dialogW - 14, cardY + cardH, 0x6610141D);
        graphics.renderOutline(x + 14, cardY, dialogW - 28, cardH, 0xFF353E50);

        int nodeCount = meta != null ? meta.getNodeCount() : currentPackage.getGraph().getNodes().size();
        int machineCount = meta != null ? meta.getMachineCount() : (meta != null ? meta.getNodeCount() : currentPackage.getGraph().getNodes().size());
        int connCount = meta != null ? meta.getConnectionCount() : currentPackage.getGraph().getConnections().size();
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
        graphics.drawString(font, "§f" + statsStr, x + 20, cardY + 6, 0xFFFFFFFF, false);

        // Power summary
        double eu = meta != null ? meta.getNetEuPerTick() : 0.0;
        double fe = meta != null ? meta.getNetFePerTick() : 0.0;
        double su = meta != null ? meta.getNetSuPerTick() : 0.0;

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
        graphics.drawString(font, powerStr, x + 20, cardY + 18, powerColor, false);

        // Inputs row
        int inY = cardY + 32;
        graphics.drawString(font, "§7" + Component.translatable("gui.gtcalcboard.dialog.bp_inputs").getString() + ":", x + 20, inY + 4, 0xFFAAAAAA, false);
        List<IngredientStack> inputs = meta != null ? meta.getPrimaryInputs() : List.of();
        int iconInX = x + 72;
        for (int i = 0; i < Math.min(6, inputs.size()); i++) {
            IngredientStack stack = inputs.get(i);
            graphics.fill(iconInX - 1, inY - 1, iconInX + 17, inY + 17, 0x44222222);
            graphics.renderOutline(iconInX - 1, inY - 1, 18, 18, 0xFF445566);
            IngredientRenderer.render(graphics, stack, iconInX, inY);
            if (mouseX >= iconInX && mouseX <= iconInX + 16 && mouseY >= inY && mouseY <= inY + 16) {
                BoardTooltipRenderer.renderTooltip(graphics, font, Component.literal(stack.getDisplayName() + String.format(" (%,.2f/s)", stack.getAmount())), mouseX, mouseY, screenWidth, screenHeight);
            }
            iconInX += 20;
        }

        // Outputs row
        int outY = cardY + 56;
        graphics.drawString(font, "§7" + Component.translatable("gui.gtcalcboard.dialog.bp_outputs").getString() + ":", x + 20, outY + 4, 0xFFAAAAAA, false);
        List<IngredientStack> outputs = meta != null ? meta.getPrimaryOutputs() : List.of();
        int iconOutX = x + 72;
        for (int i = 0; i < Math.min(6, outputs.size()); i++) {
            IngredientStack stack = outputs.get(i);
            graphics.fill(iconOutX - 1, outY - 1, iconOutX + 17, outY + 17, 0x44222222);
            graphics.renderOutline(iconOutX - 1, outY - 1, 18, 18, 0xFF445566);
            IngredientRenderer.render(graphics, stack, iconOutX, outY);
            if (mouseX >= iconOutX && mouseX <= iconOutX + 16 && mouseY >= outY && mouseY <= outY + 16) {
                BoardTooltipRenderer.renderTooltip(graphics, font, Component.literal(stack.getDisplayName() + String.format(" (%,.2f/s)", stack.getAmount())), mouseX, mouseY, screenWidth, screenHeight);
            }
            iconOutX += 20;
        }

        // Action Buttons:
        // Option 1: Open as New Page
        int btn1X = x + 14;
        int btn1Y = y + 154;
        int btnW = (dialogW - 36) / 2;
        int btnH = 22;
        boolean h1 = mouseX >= btn1X && mouseX <= btn1X + btnW && mouseY >= btn1Y && mouseY <= btn1Y + btnH;
        graphics.fill(btn1X, btn1Y, btn1X + btnW, btn1Y + btnH, h1 ? 0xFF2A6840 : 0xFF1E4D2F);
        graphics.renderOutline(btn1X, btn1Y, btnW, btnH, 0xFF359050);
        graphics.drawCenteredString(font, "+ " + Component.translatable("gui.gtcalcboard.dialog.btn_import_new_page").getString(), btn1X + btnW / 2, btn1Y + 7, 0xFFFFFFFF);

        // Option 2: Overwrite Current Page
        int btn2X = btn1X + btnW + 8;
        boolean h2 = mouseX >= btn2X && mouseX <= btn2X + btnW && mouseY >= btn1Y && mouseY <= btn1Y + btnH;
        graphics.fill(btn2X, btn1Y, btn2X + btnW, btn1Y + btnH, h2 ? 0xFF3D4558 : 0xFF282D3B);
        graphics.renderOutline(btn2X, btn1Y, btnW, btnH, 0xFF4F5B73);
        graphics.drawCenteredString(font, "▪ " + Component.translatable("gui.gtcalcboard.dialog.btn_import_overwrite").getString(), btn2X + btnW / 2, btn1Y + 7, 0xFFE0E6F0);

        // Cancel button
        int cancelY = btn1Y + 26;
        int cancelW = dialogW - 28;
        int cancelH = 16;
        boolean hc = mouseX >= btn1X && mouseX <= btn1X + cancelW && mouseY >= cancelY && mouseY <= cancelY + cancelH;
        graphics.fill(btn1X, cancelY, btn1X + cancelW, cancelY + cancelH, hc ? 0xFF352020 : 0xFF201616);
        graphics.renderOutline(btn1X, cancelY, cancelW, cancelH, 0xFF552A2A);
        graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.cancel").getString(), btn1X + cancelW / 2, cancelY + 4, 0xFFAAAAAA);

        graphics.pose().popPose();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || currentPackage == null) return false;

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = getScreenWidth();
        int screenHeight = getScreenHeight();
        int dialogW = 330;
        int dialogH = 225;
        int x = (screenWidth - dialogW) / 2;
        int y = (screenHeight - dialogH) / 2;

        // Close button (X)
        int closeBtnX = x + dialogW - 18;
        int closeBtnY = y + 3;
        if (mouseX >= closeBtnX && mouseX <= closeBtnX + 14 && mouseY >= closeBtnY && mouseY <= closeBtnY + 16) {
            close();
            return true;
        }

        // Browse Disk Files button
        int diskBtnX = x + dialogW - 90;
        int diskBtnY = y + 3;
        int diskBtnW = 70;
        int diskBtnH = 16;
        if (mouseX >= diskBtnX && mouseX <= diskBtnX + diskBtnW && mouseY >= diskBtnY && mouseY <= diskBtnY + diskBtnH) {
            close();
            if (screen.getDiskBlueprintsDialog() != null) {
                screen.getDiskBlueprintsDialog().open();
            }
            return true;
        }

        int btn1X = x + 14;
        int btn1Y = y + 154;
        int btnW = (dialogW - 36) / 2;
        int btnH = 22;

        // Option 1: Open as New Page
        if (mouseX >= btn1X && mouseX <= btn1X + btnW && mouseY >= btn1Y && mouseY <= btn1Y + btnH) {
            applyImport(true);
            return true;
        }

        // Option 2: Overwrite Current Page
        int btn2X = btn1X + btnW + 8;
        if (mouseX >= btn2X && mouseX <= btn2X + btnW && mouseY >= btn1Y && mouseY <= btn1Y + btnH) {
            applyImport(false);
            return true;
        }

        // Cancel
        int cancelY = btn1Y + 26;
        int cancelW = dialogW - 28;
        int cancelH = 16;
        if (mouseX >= btn1X && mouseX <= btn1X + cancelW && mouseY >= cancelY && mouseY <= cancelY + cancelH) {
            close();
            return true;
        }

        return true; // Block outside clicks
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            applyImport(true);
            return true;
        }
        return true;
    }

    private void applyImport(boolean asNewPage) {
        if (currentPackage == null) {
            close();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        BoardManager bm = BoardManager.getInstance();
        BlueprintMetadata meta = currentPackage.getMetadata();
        String title = meta != null && meta.getTitle() != null && !meta.getTitle().isEmpty()
                ? meta.getTitle() : "Imported Factory";

        if (asNewPage) {
            BoardPage newPage = new BoardPage(title);
            newPage.getGraph().copyFrom(currentPackage.getGraph());
            newPage.setPanX(currentPackage.getPanX());
            newPage.setPanY(currentPackage.getPanY());
            newPage.setZoom(currentPackage.getZoom());

            bm.addPage(newPage);
            bm.setActivePageIndex(bm.getPages().size() - 1);
            screen.setPanX(currentPackage.getPanX());
            screen.setPanY(currentPackage.getPanY());
            screen.setZoom(currentPackage.getZoom());
        } else {
            screen.getGraph().copyFrom(currentPackage.getGraph());
            screen.setPanX(currentPackage.getPanX());
            screen.setPanY(currentPackage.getPanY());
            screen.setZoom(currentPackage.getZoom());
            BoardScreen.lastPanX = screen.getPanX();
            BoardScreen.lastPanY = screen.getPanY();
            BoardScreen.lastZoom = screen.getZoom();
        }

        screen.rebuildWidgets();
        screen.markSummaryDirty();

        BoardToast.show(Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.import_success", String.valueOf(currentPackage.getGraph().getNodes().size()))));
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.1F));
        close();
    }

    private int getScreenWidth() {
        return screen != null && screen.width > 0 ? screen.width : Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    private int getScreenHeight() {
        return screen != null && screen.height > 0 ? screen.height : Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }
}
