package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.BlueprintCodec;
import com.gtceu.calcboard.client.team.ClientWorkspaceState;
import com.gtceu.calcboard.network.NetworkHandler;
import com.gtceu.calcboard.network.packet.c2s.C2SCommitWorkspacePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * Modal dialog for confirming and writing a short note when saving a page to the shared team workspace.
 */
public class SaveToTeamDialog {

    private final BoardScreen screen;
    private boolean visible = false;
    private EditBox messageBox;

    public SaveToTeamDialog(BoardScreen screen) {
        this.screen = screen;
    }

    public void open() {
        this.visible = true;
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int dialogW = 280;
        int dialogH = 120;
        int x = (screenWidth - dialogW) / 2;
        int y = (screenHeight - dialogH) / 2;

        this.messageBox = new EditBox(mc.font, x + 12, y + 44, dialogW - 24, 16, Component.translatable("gui.gtcalcboard.dialog.commit_hint"));
        this.messageBox.setMaxLength(100);
        this.messageBox.setHint(Component.translatable("gui.gtcalcboard.dialog.commit_hint"));
        this.messageBox.setFocused(true);
    }

    public void close() {
        this.visible = false;
        this.messageBox = null;
    }

    public boolean isVisible() {
        return visible;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int dialogW = 280;
        int dialogH = 120;
        int x = (screenWidth - dialogW) / 2;
        int y = (screenHeight - dialogH) / 2;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500.0f);

        // Dim background
        graphics.fill(0, 0, screenWidth, screenHeight, 0x88000000);

        // Frame
        graphics.fill(x, y, x + dialogW, y + dialogH, 0xF0181A22);
        graphics.renderOutline(x, y, dialogW, dialogH, 0xFF4F5B73);

        // Title
        String title = "💾 " + Component.translatable("gui.gtcalcboard.dialog.save_to_team_title").getString();
        graphics.drawString(font, title, x + 12, y + 10, 0xFFFFFFFF, false);

        String subtitle = Component.translatable("gui.gtcalcboard.dialog.save_to_team_sub").getString();
        graphics.drawString(font, "§7" + subtitle, x + 12, y + 26, 0xFFAAAAAA, false);

        if (messageBox != null) {
            messageBox.render(graphics, mouseX, mouseY, partialTicks);
        }

        // Save & Sync button
        int saveBtnX = x + 12;
        int saveBtnY = y + 80;
        int saveBtnW = 160;
        int saveBtnH = 22;
        boolean saveHover = mouseX >= saveBtnX && mouseX <= saveBtnX + saveBtnW && mouseY >= saveBtnY && mouseY <= saveBtnY + saveBtnH;
        graphics.fill(saveBtnX, saveBtnY, saveBtnX + saveBtnW, saveBtnY + saveBtnH, saveHover ? 0xFF2A6840 : 0xFF1E4D2F);
        graphics.renderOutline(saveBtnX, saveBtnY, saveBtnW, saveBtnH, 0xFF359050);
        graphics.drawCenteredString(font, "💾 " + Component.translatable("gui.gtcalcboard.dialog.btn_save_sync").getString(), saveBtnX + saveBtnW / 2, saveBtnY + 7, 0xFFFFFFFF);

        // Cancel button
        int cancelBtnX = saveBtnX + saveBtnW + 8;
        int cancelBtnW = dialogW - 32 - saveBtnW;
        boolean cancelHover = mouseX >= cancelBtnX && mouseX <= cancelBtnX + cancelBtnW && mouseY >= saveBtnY && mouseY <= saveBtnY + saveBtnH;
        graphics.fill(cancelBtnX, saveBtnY, cancelBtnX + cancelBtnW, saveBtnY + saveBtnH, cancelHover ? 0xFF4A556B : 0xFF2A313E);
        graphics.renderOutline(cancelBtnX, saveBtnY, cancelBtnW, saveBtnH, 0xFF454E62);
        graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.cancel").getString(), cancelBtnX + cancelBtnW / 2, saveBtnY + 7, 0xFFE0E6F0);

        graphics.pose().popPose();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int dialogW = 280;
        int dialogH = 120;
        int x = (screenWidth - dialogW) / 2;
        int y = (screenHeight - dialogH) / 2;

        if (messageBox != null) {
            boolean clicked = messageBox.mouseClicked(mouseX, mouseY, button);
            messageBox.setFocused(clicked);
            if (clicked) return true;
        }

        int saveBtnX = x + 12;
        int saveBtnY = y + 80;
        int saveBtnW = 160;
        int saveBtnH = 22;
        if (mouseX >= saveBtnX && mouseX <= saveBtnX + saveBtnW && mouseY >= saveBtnY && mouseY <= saveBtnY + saveBtnH) {
            performSave();
            close();
            return true;
        }

        int cancelBtnX = saveBtnX + saveBtnW + 8;
        int cancelBtnW = dialogW - 32 - saveBtnW;
        if (mouseX >= cancelBtnX && mouseX <= cancelBtnX + cancelBtnW && mouseY >= saveBtnY && mouseY <= saveBtnY + saveBtnH) {
            close();
            return true;
        }

        return true; // Block canvas clicks behind modal
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        if (keyCode == 256) { // ESC
            close();
            return true;
        }
        if (keyCode == 257) { // ENTER
            performSave();
            close();
            return true;
        }
        if (messageBox != null && messageBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return true;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible) return false;
        if (messageBox != null && messageBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return true;
    }

    private void performSave() {
        ClientWorkspaceState state = ClientWorkspaceState.getInstance();
        UUID teamId = state.getCurrentTeamId() != null ? state.getCurrentTeamId() : (Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : UUID.randomUUID());
        String pageId = "page_main";
        String pageTitle = "Main Workspace";
        int rev = state.getGlobalRevision();

        String msg = messageBox != null ? messageBox.getValue().trim() : "";
        if (msg.isEmpty()) msg = "Updated factory layout";

        CompoundTag tag = screen.getGraph().serializeNBT();
        byte[] compressed = BlueprintCodec.compressTag(tag);
        int nodeCount = screen.getGraph().getNodes().size();

        NetworkHandler.sendToServer(new C2SCommitWorkspacePacket(teamId, pageId, pageTitle, rev, msg, compressed, nodeCount, 0, 0));
        BoardToast.show("gui.gtcalcboard.toast.saved_to_team", pageTitle);
    }
}
