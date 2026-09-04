package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.widget.BoardToast;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.storage.BlueprintCodec;
import com.gtceu.calcboard.client.team.ClientWorkspaceState;
import com.gtceu.calcboard.network.NetworkHandler;
import com.gtceu.calcboard.network.packet.c2s.C2SCommitWorkspacePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * Modal dialog for exporting a personal board page directly to the team shared workspace.
 */
public class ExportToTeamDialog {

    private final BoardScreen screen;
    private boolean visible = false;

    public ExportToTeamDialog(BoardScreen screen) {
        this.screen = screen;
    }

    public void open() {
        this.visible = true;
    }

    public void close() {
        this.visible = false;
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
        int dialogW = 300;
        int dialogH = 140;
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
        String title = "📤 " + Component.translatable("gui.gtcalcboard.dialog.export_to_team_title").getString();
        graphics.drawString(font, title, x + 12, y + 10, 0xFFFFFFFF, false);

        String subtitle = Component.translatable("gui.gtcalcboard.dialog.export_to_team_sub").getString();
        graphics.drawString(font, "§7" + subtitle, x + 12, y + 26, 0xFFAAAAAA, false);

        // Option 1: Add as New Page
        int btn1X = x + 12;
        int btn1Y = y + 46;
        int btnW = dialogW - 24;
        int btnH = 22;
        boolean h1 = mouseX >= btn1X && mouseX <= btn1X + btnW && mouseY >= btn1Y && mouseY <= btn1Y + btnH;
        graphics.fill(btn1X, btn1Y, btn1X + btnW, btn1Y + btnH, h1 ? 0xFF2A6840 : 0xFF1E4D2F);
        graphics.renderOutline(btn1X, btn1Y, btnW, btnH, 0xFF359050);
        graphics.drawCenteredString(font, "➕ " + Component.translatable("gui.gtcalcboard.dialog.btn_export_new_page").getString(), btn1X + btnW / 2, btn1Y + 7, 0xFFFFFFFF);

        // Option 2: Overwrite Current Page
        int btn2Y = btn1Y + 26;
        boolean h2 = mouseX >= btn1X && mouseX <= btn1X + btnW && mouseY >= btn2Y && mouseY <= btn2Y + btnH;
        graphics.fill(btn1X, btn2Y, btn1X + btnW, btn2Y + btnH, h2 ? 0xFF3D4558 : 0xFF282D3B);
        graphics.renderOutline(btn1X, btn2Y, btnW, btnH, 0xFF4F5B73);
        graphics.drawCenteredString(font, "📝 " + Component.translatable("gui.gtcalcboard.dialog.btn_export_overwrite").getString(), btn1X + btnW / 2, btn2Y + 7, 0xFFE0E6F0);

        // Cancel
        int cancelY = btn2Y + 26;
        int cancelH = 18;
        boolean hc = mouseX >= btn1X && mouseX <= btn1X + btnW && mouseY >= cancelY && mouseY <= cancelY + cancelH;
        graphics.fill(btn1X, cancelY, btn1X + btnW, cancelY + cancelH, hc ? 0xFF352020 : 0xFF201616);
        graphics.renderOutline(btn1X, cancelY, btnW, cancelH, 0xFF552A2A);
        graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.cancel").getString(), btn1X + btnW / 2, cancelY + 5, 0xFFAAAAAA);

        graphics.pose().popPose();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = getScreenWidth();
        int screenHeight = getScreenHeight();
        int dialogW = 300;
        int dialogH = 140;
        int x = (screenWidth - dialogW) / 2;
        int y = (screenHeight - dialogH) / 2;

        int btn1X = x + 12;
        int btn1Y = y + 46;
        int btnW = dialogW - 24;
        int btnH = 22;

        // Option 1: Add as New Page
        if (mouseX >= btn1X && mouseX <= btn1X + btnW && mouseY >= btn1Y && mouseY <= btn1Y + btnH) {
            exportToTeam(true);
            close();
            return true;
        }

        // Option 2: Overwrite Current Page
        int btn2Y = btn1Y + 26;
        if (mouseX >= btn1X && mouseX <= btn1X + btnW && mouseY >= btn2Y && mouseY <= btn2Y + btnH) {
            exportToTeam(false);
            close();
            return true;
        }

        // Cancel
        int cancelY = btn2Y + 26;
        int cancelH = 18;
        if (mouseX >= btn1X && mouseX <= btn1X + btnW && mouseY >= cancelY && mouseY <= cancelY + cancelH) {
            close();
            return true;
        }

        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        if (keyCode == 256) { // ESC
            close();
            return true;
        }
        return true;
    }

    private void exportToTeam(boolean asNewPage) {
        ClientWorkspaceState state = ClientWorkspaceState.getInstance();
        UUID teamId = state.getCurrentTeamId() != null ? state.getCurrentTeamId() : (Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : UUID.randomUUID());
        BoardPage activePersonalPage = BoardManager.getInstance().getActivePage();
        String pageTitle = activePersonalPage != null ? activePersonalPage.getName() : "Exported Factory";
        String pageId = asNewPage ? ("page_" + System.currentTimeMillis()) : "page_main";
        int rev = state.getGlobalRevision();

        CompoundTag tag = screen.getGraph().serializeNBT();
        byte[] compressed = BlueprintCodec.compressTag(tag);
        int nodeCount = screen.getGraph().getNodes().size();

        String commitMsg = "Exported from personal board: " + pageTitle;
        com.gtceu.calcboard.client.team.ClientChunkedStreamHelper.commitPageSafely(teamId, pageId, pageTitle, rev, commitMsg, compressed, nodeCount, 0, 0);
        BoardToast.show("gui.gtcalcboard.toast.exported_to_team", pageTitle);
    }

    private int getScreenWidth() {
        return screen != null && screen.width > 0 ? screen.width : Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    private int getScreenHeight() {
        return screen != null && screen.height > 0 ? screen.height : Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }
}



