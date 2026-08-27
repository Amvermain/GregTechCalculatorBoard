package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.widget.BoardToast;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.storage.BlueprintCodec;
import com.gtceu.calcboard.client.team.ClientWorkspaceState;
import com.gtceu.calcboard.server.storage.CommitLogEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Modal dialog for viewing recent saves/commits on the team workspace and copying past revisions.
 */
public class RecentSavesDialog {

    private final BoardScreen screen;
    private boolean visible = false;
    private int scrollOffset = 0;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM-dd HH:mm");

    public RecentSavesDialog(BoardScreen screen) {
        this.screen = screen;
    }

    public void open() {
        this.visible = true;
        this.scrollOffset = 0;
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
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int dialogW = 340;
        int dialogH = 220;
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
        String title = "📜 " + Component.translatable("gui.gtcalcboard.dialog.recent_saves_title").getString();
        graphics.drawString(font, title, x + 12, y + 10, 0xFFFFFFFF, false);

        // Close button
        boolean closeHover = mouseX >= x + dialogW - 20 && mouseX <= x + dialogW - 6 && mouseY >= y + 8 && mouseY <= y + 22;
        graphics.fill(x + dialogW - 20, y + 8, x + dialogW - 6, y + 22, closeHover ? 0xFF882222 : 0xFF442222);
        graphics.drawCenteredString(font, "✕", x + dialogW - 13, y + 10, 0xFFFFFFFF);

        // Commits List area
        int listY = y + 28;
        int listH = dialogH - 36;
        graphics.fill(x + 8, listY, x + dialogW - 8, listY + listH, 0xFF14171E);
        graphics.renderOutline(x + 8, listY, dialogW - 16, listH, 0xFF2F3646);

        List<CommitLogEntry> commits = ClientWorkspaceState.getInstance().getCommitHistory();
        if (commits.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.dialog.no_saves_history").getString(), x + dialogW / 2, listY + 70, 0xFF888888);
        } else {
            int visibleCount = 5;
            int maxScroll = Math.max(0, commits.size() - visibleCount);
            if (scrollOffset > maxScroll) scrollOffset = maxScroll;

            int itemH = 32;
            int startIdx = commits.size() - 1 - scrollOffset;
            for (int i = 0; i < visibleCount; i++) {
                int idx = startIdx - i;
                if (idx < 0) break;

                CommitLogEntry entry = commits.get(idx);
                int itemY = listY + 4 + i * (itemH + 3);

                graphics.fill(x + 12, itemY, x + dialogW - 12, itemY + itemH, 0xFF1E232E);
                graphics.renderOutline(x + 12, itemY, dialogW - 24, itemH, 0xFF353C4D);

                String dateStr = DATE_FORMAT.format(new Date(entry.getTimestamp()));
                String line1 = String.format("§6#%d §f%s §7(%s)", entry.getRevision(), entry.getAuthorName(), dateStr);
                graphics.drawString(font, line1, x + 16, itemY + 4, 0xFFFFFFFF, false);

                String msg = font.plainSubstrByWidth(entry.getMessage(), dialogW - 110);
                graphics.drawString(font, "§7" + msg, x + 16, itemY + 18, 0xFFAAAAAA, false);

                // Copy to Personal button
                int copyBtnX = x + dialogW - 90;
                int copyBtnY = itemY + 6;
                int copyBtnW = 72;
                int copyBtnH = 20;
                boolean copyH = mouseX >= copyBtnX && mouseX <= copyBtnX + copyBtnW && mouseY >= copyBtnY && mouseY <= copyBtnY + copyBtnH;
                graphics.fill(copyBtnX, copyBtnY, copyBtnX + copyBtnW, copyBtnY + copyBtnH, copyH ? 0xFF2A5A38 : 0xFF1C3D26);
                graphics.renderOutline(copyBtnX, copyBtnY, copyBtnW, copyBtnH, 0xFF3B774E);
                graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.dialog.btn_copy_to_me").getString(), copyBtnX + copyBtnW / 2, copyBtnY + 6, 0xFFFFFFFF);
            }
        }

        graphics.pose().popPose();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int dialogW = 340;
        int dialogH = 220;
        int x = (screenWidth - dialogW) / 2;
        int y = (screenHeight - dialogH) / 2;

        // Close button
        if (mouseX >= x + dialogW - 20 && mouseX <= x + dialogW - 6 && mouseY >= y + 8 && mouseY <= y + 22) {
            close();
            return true;
        }

        List<CommitLogEntry> commits = ClientWorkspaceState.getInstance().getCommitHistory();
        int listY = y + 28;
        int visibleCount = 5;
        int itemH = 32;
        int startIdx = commits.size() - 1 - scrollOffset;

        for (int i = 0; i < visibleCount; i++) {
            int idx = startIdx - i;
            if (idx < 0) break;

            CommitLogEntry entry = commits.get(idx);
            int itemY = listY + 4 + i * (itemH + 3);
            int copyBtnX = x + dialogW - 90;
            int copyBtnY = itemY + 6;
            int copyBtnW = 72;
            int copyBtnH = 20;

            if (mouseX >= copyBtnX && mouseX <= copyBtnX + copyBtnW && mouseY >= copyBtnY && mouseY <= copyBtnY + copyBtnH) {
                copyCommitToPersonal(entry);
                close();
                return true;
            }
        }

        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible) return false;
        List<CommitLogEntry> commits = ClientWorkspaceState.getInstance().getCommitHistory();
        int maxScroll = Math.max(0, commits.size() - 5);
        if (maxScroll > 0) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.signum(delta)));
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        if (keyCode == 256) { // ESC
            close();
            return true;
        }
        return true;
    }

    private void copyCommitToPersonal(CommitLogEntry entry) {
        ClientWorkspaceState state = ClientWorkspaceState.getInstance();
        BoardManager bm = BoardManager.getInstance();

        com.gtceu.calcboard.server.storage.TeamWorkspacePage remotePage = state.getRemotePage(entry.getPageId());
        String baseTitle = (remotePage != null && remotePage.getTitle() != null && !remotePage.getTitle().trim().isEmpty())
                ? remotePage.getTitle() : "Team Design";
        String newPageName = baseTitle + " (Rev#" + entry.getRevision() + ")";

        BoardPage newPage = new BoardPage(newPageName);
        com.gtceu.calcboard.api.model.FlowGraph sourceGraph = state.getTeamGraph(entry.getPageId());

        if (sourceGraph == null || sourceGraph.getNodes().isEmpty()) {
            if (remotePage != null && remotePage.getCompressedGraphData() != null && remotePage.getCompressedGraphData().length > 0) {
                try {
                    net.minecraft.nbt.CompoundTag tag = BlueprintCodec.decompressTag(remotePage.getCompressedGraphData());
                    sourceGraph = com.gtceu.calcboard.api.model.FlowGraph.deserializeNBT(tag);
                } catch (Exception ignored) {}
            }
        }

        if (sourceGraph == null || sourceGraph.getNodes().isEmpty()) {
            sourceGraph = screen.getGraph();
        }

        if (sourceGraph != null) {
            com.gtceu.calcboard.api.model.FlowGraph copiedGraph = sourceGraph.copy();
            for (com.gtceu.calcboard.api.model.RecipeNode n : copiedGraph.getNodes()) {
                newPage.getGraph().addNode(n);
            }
            for (com.gtceu.calcboard.api.model.FlowGraph.ConnectionEdge e : copiedGraph.getConnections()) {
                newPage.getGraph().getConnections().add(e);
            }
        }

        newPage.setPanX(screen.getPanX());
        newPage.setPanY(screen.getPanY());
        newPage.setZoom(screen.getZoom());

        bm.addPage(newPage);
        bm.setActivePageIndex(bm.getPages().size() - 1);
        state.setCurrentMode(ClientWorkspaceState.WorkspaceMode.LOCAL);
        screen.rebuildWidgets();
        screen.markSummaryDirty();
        BoardToast.show("gui.gtcalcboard.toast.copied_to_personal", newPageName);
    }
}




