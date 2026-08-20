package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.GlobalBalanceSummary;
import com.gtceu.calcboard.api.IngredientStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Drill-down popup dialog displaying per-page production and consumption contributions for a specific item/fluid.
 */
public class ItemContributionPopup {
    private boolean visible = false;
    private IngredientStack targetStack = null;
    private List<GlobalBalanceSummary.PageContribution> contributions = null;
    private double netRate = 0.0;
    private double scrollY = 0;
    private double maxScrollY = 0;

    private static final int POPUP_WIDTH = 280;
    private static final int POPUP_HEIGHT = 200;

    public boolean isVisible() {
        return visible;
    }

    public void open(IngredientStack stack, List<GlobalBalanceSummary.PageContribution> contributions, double netRate) {
        this.targetStack = stack;
        this.contributions = contributions;
        this.netRate = netRate;
        this.scrollY = 0;
        this.visible = true;
    }

    public void close() {
        this.visible = false;
        this.targetStack = null;
        this.contributions = null;
    }

    public void render(GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (!visible || targetStack == null) return;

        Font font = Minecraft.getInstance().font;
        int px = (screenWidth - POPUP_WIDTH) / 2;
        int py = (screenHeight - POPUP_HEIGHT) / 2;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 750.0f);

        // Dim background overlay
        graphics.fill(0, 0, screenWidth, screenHeight, 0x77000000);

        // Main Container Background & Border
        graphics.fill(px, py, px + POPUP_WIDTH, py + POPUP_HEIGHT, 0xF5131822);
        graphics.renderOutline(px, py, POPUP_WIDTH, POPUP_HEIGHT, 0xFF00E5FF);

        // Header Bar
        graphics.fill(px, py, px + POPUP_WIDTH, py + 26, 0xFF1C2433);
        graphics.renderOutline(px, py, POPUP_WIDTH, 26, 0xFF2D3B55);

        // Stack Icon & Title
        IngredientRenderer.render(graphics, targetStack, px + 6, py + 4);
        String title = targetStack.getDisplayName();
        if (font.width(title) > POPUP_WIDTH - 52) {
            title = font.plainSubstrByWidth(title, POPUP_WIDTH - 56) + "..";
        }
        graphics.drawString(font, "§f" + title, px + 26, py + 8, 0xFFFFFFFF, false);

        // Close Button [✕]
        int closeX = px + POPUP_WIDTH - 20;
        int closeY = py + 5;
        boolean closeHover = mouseX >= closeX && mouseX <= closeX + 16 && mouseY >= closeY && mouseY <= closeY + 16;
        graphics.fill(closeX, closeY, closeX + 16, closeY + 16, closeHover ? 0xFF992222 : 0xFF2A3345);
        graphics.renderOutline(closeX, closeY, 16, 16, closeHover ? 0xFFFF4444 : 0xFF4A5A78);
        graphics.drawCenteredString(font, "✕", closeX + 8, closeY + 4, closeHover ? 0xFFFFFFFF : 0xFFAAAAAA);

        // Sub-header label: Page Contribution Breakdown
        int contentY = py + 30;
        int contentH = POPUP_HEIGHT - 64;
        graphics.drawString(font, "§7" + Component.translatable("gui.gtcalcboard.global_balance.breakdown_header").getString(), px + 8, contentY, 0xFFAAAAAA, false);

        int listY = contentY + 12;
        int listH = contentH - 12;

        int rowHeight = 22;
        int totalListHeight = (contributions != null ? contributions.size() : 0) * rowHeight;
        maxScrollY = Math.max(0, totalListHeight - listH);
        scrollY = Math.max(0, Math.min(maxScrollY, scrollY));

        graphics.enableScissor(px + 4, listY, px + POPUP_WIDTH - 4, listY + listH);

        int curY = listY - (int) scrollY;
        if (contributions == null || contributions.isEmpty()) {
            graphics.drawString(font, "  §8" + Component.translatable("gui.gtcalcboard.none").getString(), px + 8, curY + 6, 0xFF888888, false);
        } else {
            for (GlobalBalanceSummary.PageContribution contrib : contributions) {
                if (curY >= listY - rowHeight && curY <= listY + listH) {
                    renderContributionRow(graphics, font, px, curY, contrib);
                }
                curY += rowHeight;
            }
        }

        graphics.disableScissor();

        // Footer Separator & Net Rate Summary
        int footerY = py + POPUP_HEIGHT - 30;
        graphics.fill(px + 6, footerY, px + POPUP_WIDTH - 6, footerY + 1, 0xFF2D3B55);

        String netLabel = Component.translatable("gui.gtcalcboard.global_balance.net_total").getString();
        String sign = netRate > 0.0001 ? "+" : "";
        String rateFormatted = sign + NodeCardRenderer.formatRate(netRate, targetStack.isFluid());
        int rateColor = Math.abs(netRate) <= 0.0001 ? 0xFF55FF55 : (netRate > 0 ? 0xFF55FF55 : 0xFFFF5555);

        graphics.drawString(font, "§e" + netLabel, px + 8, footerY + 8, 0xFFFFFFFF, false);
        int rateW = font.width(rateFormatted);
        graphics.drawString(font, rateFormatted, px + POPUP_WIDTH - 8 - rateW, footerY + 8, rateColor, false);

        graphics.pose().popPose();
    }

    private void renderContributionRow(GuiGraphics graphics, Font font, int px, int y, GlobalBalanceSummary.PageContribution contrib) {
        // Row background
        graphics.fill(px + 6, y, px + POPUP_WIDTH - 6, y + 20, 0x6618202E);
        graphics.renderOutline(px + 6, y, POPUP_WIDTH - 12, 20, 0x442D3B55);

        // Page Name
        String pageName = contrib.pageName();
        if (font.width(pageName) > 110) {
            pageName = font.plainSubstrByWidth(pageName, 105) + "..";
        }
        graphics.drawString(font, "§6📄 " + pageName, px + 10, y + 6, 0xFFFFFFFF, false);

        // Produced / Consumed details
        StringBuilder sb = new StringBuilder();
        if (contrib.hasProduction()) {
            sb.append("§a+").append(NodeCardRenderer.formatRate(contrib.producedRate(), targetStack.isFluid()));
        }
        if (contrib.hasConsumption()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append("§c-").append(NodeCardRenderer.formatRate(contrib.consumedRate(), targetStack.isFluid()));
        }

        String rateStr = sb.toString();
        int rateW = font.width(rateStr);
        graphics.drawString(font, rateStr, px + POPUP_WIDTH - 12 - rateW, y + 6, 0xFFFFFFFF, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
        if (!visible) return false;

        int px = (screenWidth - POPUP_WIDTH) / 2;
        int py = (screenHeight - POPUP_HEIGHT) / 2;

        // Close button click
        int closeX = px + POPUP_WIDTH - 20;
        int closeY = py + 5;
        if (mouseX >= closeX && mouseX <= closeX + 16 && mouseY >= closeY && mouseY <= closeY + 16 && button == 0) {
            close();
            return true;
        }

        // Click outside popup dismisses it
        if (mouseX < px || mouseX > px + POPUP_WIDTH || mouseY < py || mouseY > py + POPUP_HEIGHT) {
            close();
            return true;
        }

        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta, int screenWidth, int screenHeight) {
        if (!visible) return false;

        int px = (screenWidth - POPUP_WIDTH) / 2;
        int py = (screenHeight - POPUP_HEIGHT) / 2;

        if (mouseX >= px && mouseX <= px + POPUP_WIDTH && mouseY >= py && mouseY <= py + POPUP_HEIGHT) {
            if (maxScrollY > 0) {
                scrollY = Math.max(0, Math.min(maxScrollY, scrollY - (delta * 16.0)));
                return true;
            }
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return true;
    }
}
