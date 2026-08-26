package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * Interactive Cross-Page Global Balance Dashboard Modal Dialog.
 * Provides multi-page process net balance aggregation, total generation vs consumption power balance,
 * and per-item drill-down source/sink contribution analysis.
 */
public class GlobalBalanceDashboardDialog {
    private final BoardScreen parent;
    private boolean visible = false;

    private final Set<String> selectedPageIds = new LinkedHashSet<>();
    private GlobalBalanceSummary cachedSummary = null;
    private boolean dirty = true;

    // Filter & Search
    private int filterTab = 0; // 0: All, 1: Deficits, 2: Surplus, 3: Balanced
    private EditBox searchBox = null;
    private String searchQuery = "";

    // Scrolling
    private double pageScrollY = 0;
    private double maxPageScrollY = 0;
    private double itemScrollY = 0;
    private double maxItemScrollY = 0;

    // Drilldown popup
    private final ItemContributionPopup contributionPopup = new ItemContributionPopup();

    // Tooltip tracking
    private IngredientStack hoveredStack = null;
    private double hoveredRate = 0.0;
    private boolean hoveredPower = false;
    private boolean hoveredMachines = false;

    private static final int DIALOG_WIDTH = 580;
    private static final int DIALOG_HEIGHT = 320;
    private static final int SIDEBAR_WIDTH = 155;

    public GlobalBalanceDashboardDialog(BoardScreen parent) {
        this.parent = parent;
    }

    public boolean isVisible() {
        return visible;
    }

    public void open() {
        this.visible = true;
        this.pageScrollY = 0;
        this.itemScrollY = 0;
        this.contributionPopup.close();

        // Default: select all pages if nothing selected
        List<BoardPage> pages = BoardManager.getInstance().getPages();
        if (selectedPageIds.isEmpty()) {
            for (BoardPage page : pages) {
                selectedPageIds.add(page.getId());
            }
        }
        this.dirty = true;

        if (searchBox == null) {
            Font font = Minecraft.getInstance().font;
            searchBox = new EditBox(font, 0, 0, 85, 14, Component.translatable("gui.gtcalcboard.search"));
            searchBox.setResponder(text -> {
                this.searchQuery = text.trim().toLowerCase();
                this.itemScrollY = 0;
            });
        }
        searchBox.setValue("");
        this.searchQuery = "";
    }

    public void close() {
        this.visible = false;
        this.contributionPopup.close();
        if (searchBox != null) {
            searchBox.setFocused(false);
        }
    }

    public void markDirty() {
        this.dirty = true;
    }

    private void updateSummaryIfNeeded() {
        if (dirty || cachedSummary == null) {
            List<BoardPage> allPages = BoardManager.getInstance().getPages();
            List<BoardPage> selectedPages = new ArrayList<>();
            for (BoardPage p : allPages) {
                if (selectedPageIds.contains(p.getId())) {
                    selectedPages.add(p);
                }
            }
            this.cachedSummary = GlobalBalanceAggregator.compute(selectedPages);
            this.dirty = false;
        }
    }

    public void render(GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (!visible) return;

        updateSummaryIfNeeded();

        Font font = Minecraft.getInstance().font;
        int dialogW = Math.min(DIALOG_WIDTH, screenWidth - 16);
        int dialogH = Math.min(DIALOG_HEIGHT, screenHeight - 16);
        int dialogX = (screenWidth - dialogW) / 2;
        int dialogY = (screenHeight - dialogH) / 2;

        hoveredStack = null;
        hoveredPower = false;
        hoveredMachines = false;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 600.0f);

        // 1. Semi-transparent Black Dim Overlay
        graphics.fill(0, 0, screenWidth, screenHeight, 0x88000000);

        // 2. Dialog Main Container Background & Border
        graphics.fill(dialogX, dialogY, dialogX + dialogW, dialogY + dialogH, 0xF2121722);
        graphics.renderOutline(dialogX, dialogY, dialogW, dialogH, 0xFF3D4B66);

        // 3. Header Bar
        graphics.fill(dialogX, dialogY, dialogX + dialogW, dialogY + 24, 0xFF1C2433);
        graphics.drawString(font, "§6📊 " + Component.translatable("gui.gtcalcboard.global_balance.modal_title").getString(), dialogX + 10, dialogY + 8, 0xFFFFFFFF, false);

        // Close Button [✕]
        int closeX = dialogX + dialogW - 20;
        int closeY = dialogY + 4;
        boolean closeHover = mouseX >= closeX && mouseX <= closeX + 16 && mouseY >= closeY && mouseY <= closeY + 16;
        graphics.fill(closeX, closeY, closeX + 16, closeY + 16, closeHover ? 0xFF992222 : 0xFF2A3345);
        graphics.renderOutline(closeX, closeY, 16, 16, closeHover ? 0xFFFF4444 : 0xFF4A5A78);
        graphics.drawCenteredString(font, "✕", closeX + 8, closeY + 4, closeHover ? 0xFFFFFFFF : 0xFFAAAAAA);

        // 4. Top Summary Banner (Power Balance + Machine Stats)
        int bannerY = dialogY + 26;
        int bannerH = 26;
        graphics.fill(dialogX + 6, bannerY, dialogX + dialogW - 6, bannerY + bannerH, 0xDD18202E);
        graphics.renderOutline(dialogX + 6, bannerY, dialogW - 12, bannerH, 0xFF2A364D);

        // A. Net Power
        int pX = dialogX + 12;
        int pY = bannerY + 5;
        double netGen = cachedSummary.totalGeneratedEUt();
        double netDrain = cachedSummary.totalConsumedEUt();
        double netEUt = netGen - netDrain;

        String powerText;
        int powerColor;
        if (Math.abs(netEUt) < 0.001) {
            powerText = "§e⚡ " + Component.translatable("gui.gtcalcboard.global_balance.power_balanced").getString();
            powerColor = 0xFFFFAA00;
        } else if (netEUt > 0) {
            powerText = "§a⚡ +" + FormatUtil.formatEUt(netEUt) + " (" + Component.translatable("gui.gtcalcboard.global_balance.power_surplus").getString() + " 🟢)";
            powerColor = 0xFF55FF55;
        } else {
            powerText = "§c⚡ -" + FormatUtil.formatEUt(-netEUt) + " (" + Component.translatable("gui.gtcalcboard.global_balance.power_deficit").getString() + " 🔴)";
            powerColor = 0xFFFF5555;
        }

        graphics.drawString(font, powerText, pX, pY, powerColor, false);
        int powerW = font.width(powerText);
        hoveredPower = mouseX >= pX && mouseX <= pX + powerW && mouseY >= pY && mouseY <= pY + 12;

        // Sub-power info
        String subPowerStr = String.format("§7(+%s / -%s)", FormatUtil.formatCompactNumber(netGen), FormatUtil.formatCompactNumber(netDrain));
        graphics.drawString(font, subPowerStr, pX + powerW + 6, pY + 1, 0xFF888888, false);

        // B. Total Machines & Pages Count
        String countFormatted = cachedSummary.totalMachineCount() >= 100_000
            ? FormatUtil.formatCompactNumber(cachedSummary.totalMachineCount())
            : String.format(Locale.ROOT, "%,d", cachedSummary.totalMachineCount());
        String machStr = "§6🏭 " + countFormatted + Component.translatable("gui.gtcalcboard.machine_unit").getString();
        int machW = font.width(machStr);
        int machX = dialogX + dialogW - 18 - machW;
        graphics.drawString(font, machStr, machX, pY, 0xFFFFFFFF, false);
        hoveredMachines = mouseX >= machX && mouseX <= machX + machW && mouseY >= pY && mouseY <= pY + 12;

        String pageCountStr = "§7(" + cachedSummary.selectedPages().size() + "/" + BoardManager.getInstance().getPages().size() + " " + Component.translatable("gui.gtcalcboard.global_balance.pages_selected").getString() + ")";
        int pageCountW = font.width(pageCountStr);
        graphics.drawString(font, pageCountStr, machX - pageCountW - 8, pY, 0xFFAAAAAA, false);

        // 5. Left Sidebar (Included Pages List & Selection Controls)
        int sidebarX = dialogX + 6;
        int sidebarY = bannerY + bannerH + 4;
        int sidebarH = dialogH - (sidebarY - dialogY) - 6;

        renderSidebar(graphics, font, sidebarX, sidebarY, SIDEBAR_WIDTH, sidebarH, mouseX, mouseY);

        // Separator between sidebar and main content
        graphics.fill(dialogX + SIDEBAR_WIDTH + 8, sidebarY, dialogX + SIDEBAR_WIDTH + 9, sidebarY + sidebarH, 0xFF283448);

        // 6. Right Main Content (Material Balance List & Filter Controls)
        int mainX = dialogX + SIDEBAR_WIDTH + 12;
        int mainY = sidebarY;
        int mainW = dialogW - SIDEBAR_WIDTH - 18;
        int mainH = sidebarH;

        renderMainContent(graphics, font, mainX, mainY, mainW, mainH, mouseX, mouseY);

        graphics.pose().popPose();

        // 7. Render Drill-Down Popup (Highest dialog layer)
        if (contributionPopup.isVisible()) {
            contributionPopup.render(graphics, screenWidth, screenHeight, mouseX, mouseY);
        } else {
            // Render Tooltips
            renderTooltips(graphics, font, mouseX, mouseY);
        }
    }

    private void renderSidebar(GuiGraphics graphics, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        // Container background
        graphics.fill(x, y, x + w, y + h, 0x88141C29);
        graphics.renderOutline(x, y, w, h, 0xFF283448);

        // Header Title
        graphics.fill(x, y, x + w, y + 16, 0xCC1C2536);
        graphics.drawString(font, "§6📑 " + Component.translatable("gui.gtcalcboard.global_balance.included_pages").getString(), x + 6, y + 4, 0xFFFFFFFF, false);

        // Bottom Action Buttons ([Select All] / [Deselect All])
        int btnH = 15;
        int btnY = y + h - btnH - 3;
        int btnW = (w - 9) / 2;

        int allBtnX = x + 3;
        int noneBtnX = allBtnX + btnW + 3;

        boolean allHover = mouseX >= allBtnX && mouseX <= allBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        boolean noneHover = mouseX >= noneBtnX && mouseX <= noneBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;

        graphics.fill(allBtnX, btnY, allBtnX + btnW, btnY + btnH, allHover ? 0xFF2B4466 : 0xFF1C2C44);
        graphics.renderOutline(allBtnX, btnY, btnW, btnH, allHover ? 0xFF55AAFF : 0xFF355580);
        graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.global_balance.select_all").getString(), allBtnX + btnW / 2, btnY + 4, allHover ? 0xFFFFFFFF : 0xFFAAD4FF);

        graphics.fill(noneBtnX, btnY, noneBtnX + btnW, btnY + btnH, noneHover ? 0xFF442B2B : 0xFF331C1C);
        graphics.renderOutline(noneBtnX, btnY, btnW, btnH, noneHover ? 0xFFFF5555 : 0xFF663535);
        graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.global_balance.deselect_all").getString(), noneBtnX + btnW / 2, btnY + 4, noneHover ? 0xFFFFFFFF : 0xFFFF8888);

        // Scrollable Page List
        int listY = y + 18;
        int listH = btnY - listY - 2;

        List<BoardPage> pages = BoardManager.getInstance().getPages();
        int rowH = 18;
        int totalH = pages.size() * rowH;
        maxPageScrollY = Math.max(0, totalH - listH);
        pageScrollY = Math.max(0, Math.min(maxPageScrollY, pageScrollY));

        graphics.enableScissor(x + 2, listY, x + w - 2, listY + listH);

        int curY = listY - (int) pageScrollY;
        for (int i = 0; i < pages.size(); i++) {
            BoardPage page = pages.get(i);
            boolean isChecked = selectedPageIds.contains(page.getId());
            boolean rowHover = mouseX >= x + 3 && mouseX <= x + w - 3 && mouseY >= curY && mouseY <= curY + rowH - 1 && mouseY >= listY && mouseY <= listY + listH;

            int rowBg = isChecked ? (rowHover ? 0x99253A55 : 0x771E2D44) : (rowHover ? 0x66222833 : 0x44181E26);
            graphics.fill(x + 3, curY, x + w - 3, curY + rowH - 1, rowBg);

            // Checkbox indicator
            String checkMark = isChecked ? "§a[✔]" : "§7[  ]";
            graphics.drawString(font, checkMark, x + 6, curY + 5, 0xFFFFFFFF, false);

            // Page Name
            String pName = (i + 1) + ". " + page.getName();
            if (font.width(pName) > w - 42) {
                pName = font.plainSubstrByWidth(pName, w - 46) + "..";
            }
            graphics.drawString(font, isChecked ? "§f" + pName : "§8" + pName, x + 28, curY + 5, 0xFFFFFFFF, false);

            curY += rowH;
        }

        graphics.disableScissor();
    }

    private void renderMainContent(GuiGraphics graphics, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        // Container background
        graphics.fill(x, y, x + w, y + h, 0x88141C29);
        graphics.renderOutline(x, y, w, h, 0xFF283448);

        // Filter Tabs & Search Box
        int tabY = y + 3;
        int tabH = 14;
        String[] tabs = {
            Component.translatable("gui.gtcalcboard.addon_cat.all").getString() + " (" + countAllStacks() + ")",
            "§c" + Component.translatable("gui.gtcalcboard.global_balance.tab_deficits").getString() + " (" + cachedSummary.rawInputs().size() + ")",
            "§a" + Component.translatable("gui.gtcalcboard.global_balance.tab_surplus").getString() + " (" + cachedSummary.netOutputs().size() + ")",
            "§b" + Component.translatable("gui.gtcalcboard.global_balance.tab_balanced").getString() + " (" + cachedSummary.fullyBalanced().size() + ")"
        };

        int curTabX = x + 4;
        for (int i = 0; i < tabs.length; i++) {
            String tabText = tabs[i];
            int tabW = font.width(tabText) + 8;
            boolean active = (filterTab == i);
            boolean tabHover = mouseX >= curTabX && mouseX <= curTabX + tabW && mouseY >= tabY && mouseY <= tabY + tabH;

            graphics.fill(curTabX, tabY, curTabX + tabW, tabY + tabH, active ? 0xFF2B4466 : (tabHover ? 0xFF1C2C44 : 0xFF181F2C));
            graphics.renderOutline(curTabX, tabY, tabW, tabH, active ? 0xFF00E5FF : (tabHover ? 0xFF4A5F80 : 0xFF2A364D));
            graphics.drawString(font, tabText, curTabX + 4, tabY + 3, active ? 0xFFFFFFFF : 0xFFAAAAAA, false);

            curTabX += tabW + 3;
        }

        // Search Box (docked to the right)
        int searchW = 85;
        int searchX = x + w - searchW - 4;
        if (searchBox != null) {
            searchBox.setX(searchX);
            searchBox.setY(tabY);
            searchBox.render(graphics, mouseX, mouseY, 0);
        }

        // Material Rows List
        int listY = y + 22;
        int listH = h - 26;

        List<BalanceRowItem> rows = buildDisplayRows();
        int rowH = 20;
        int totalH = rows.size() * rowH;
        maxItemScrollY = Math.max(0, totalH - listH);
        itemScrollY = Math.max(0, Math.min(maxItemScrollY, itemScrollY));

        graphics.enableScissor(x + 2, listY, x + w - 2, listY + listH);

        int curY = listY - (int) itemScrollY;
        if (rows.isEmpty()) {
            graphics.drawString(font, "  §8" + Component.translatable("gui.gtcalcboard.none").getString(), x + 10, curY + 8, 0xFF888888, false);
        } else {
            for (BalanceRowItem item : rows) {
                if (curY >= listY - rowH && curY <= listY + listH) {
                    renderItemRow(graphics, font, x + 3, curY, w - 6, rowH - 1, item, mouseX, mouseY, listY, listH);
                }
                curY += rowH;
            }
        }

        graphics.disableScissor();

        // Render scrollbar
        if (maxItemScrollY > 0) {
            int sbX = x + w - 4;
            graphics.fill(sbX, listY, sbX + 2, listY + listH, 0x55000000);
            int thumbH = Math.max(14, (int) ((double) listH / totalH * listH));
            int thumbY = listY + (int) ((itemScrollY / maxItemScrollY) * (listH - thumbH));
            graphics.fill(sbX, thumbY, sbX + 2, thumbY + thumbH, 0xFFAAAAAA);
        }
    }

    private static class BalanceRowItem {
        final IngredientStack stack;
        final double netRate;
        final double producedRate;
        final double consumedRate;
        final int statusType; // 0: Deficit (🔴), 1: Surplus (🟢), 2: Balanced (🟢/🔵)

        BalanceRowItem(IngredientStack stack, double netRate, double producedRate, double consumedRate, int statusType) {
            this.stack = stack;
            this.netRate = netRate;
            this.producedRate = producedRate;
            this.consumedRate = consumedRate;
            this.statusType = statusType;
        }
    }

    private int countAllStacks() {
        return cachedSummary.rawInputs().size() + cachedSummary.netOutputs().size() + cachedSummary.fullyBalanced().size();
    }

    private List<BalanceRowItem> buildDisplayRows() {
        List<BalanceRowItem> items = new ArrayList<>();

        // Add Deficits
        if (filterTab == 0 || filterTab == 1) {
            for (Map.Entry<IngredientStack, Double> e : cachedSummary.rawInputs().entrySet()) {
                if (matchesSearch(e.getKey())) {
                    double cons = findRate(cachedSummary.totalConsumption(), e.getKey());
                    double prod = findRate(cachedSummary.totalProduction(), e.getKey());
                    items.add(new BalanceRowItem(e.getKey(), -e.getValue(), prod, cons, 0));
                }
            }
        }

        // Add Surplus
        if (filterTab == 0 || filterTab == 2) {
            for (Map.Entry<IngredientStack, Double> e : cachedSummary.netOutputs().entrySet()) {
                if (matchesSearch(e.getKey())) {
                    double cons = findRate(cachedSummary.totalConsumption(), e.getKey());
                    double prod = findRate(cachedSummary.totalProduction(), e.getKey());
                    items.add(new BalanceRowItem(e.getKey(), e.getValue(), prod, cons, 1));
                }
            }
        }

        // Add Balanced
        if (filterTab == 0 || filterTab == 3) {
            for (Map.Entry<IngredientStack, Double> e : cachedSummary.fullyBalanced().entrySet()) {
                if (matchesSearch(e.getKey())) {
                    double prod = e.getValue();
                    items.add(new BalanceRowItem(e.getKey(), 0.0, prod, prod, 2));
                }
            }
        }

        return items;
    }

    private boolean matchesSearch(IngredientStack stack) {
        if (searchQuery.isEmpty()) return true;
        if (stack == null) return false;
        return stack.getDisplayName().toLowerCase().contains(searchQuery);
    }

    private void renderItemRow(GuiGraphics graphics, Font font, int x, int y, int w, int h, BalanceRowItem item, int mouseX, int mouseY, int listY, int listH) {
        boolean rowHover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h && mouseY >= listY && mouseY <= listY + listH;

        int rowBg = rowHover ? 0x8825354D : 0x55192230;
        int rowBorder = rowHover ? 0xFF00E5FF : 0x44283448;
        graphics.fill(x, y, x + w, y + h, rowBg);
        graphics.renderOutline(x, y, w, h, rowBorder);

        // 1. Icon
        IngredientRenderer.render(graphics, item.stack, x + 4, y + 1);

        // 2. Rightmost Status Tag: [Deficit 🔴] / [Surplus 🟢] / [Balanced 🟢]
        String tagStr = switch (item.statusType) {
            case 0 -> "§c[" + Component.translatable("gui.gtcalcboard.tooltip.deficit").getString() + " 🔴]";
            case 1 -> "§a[" + Component.translatable("gui.gtcalcboard.tooltip.surplus").getString() + " 🟢]";
            default -> "§b[" + Component.translatable("gui.gtcalcboard.global_balance.balanced_tag").getString() + " 🟢]";
        };
        int tagW = font.width(tagStr);
        int tagX = x + w - 6 - tagW;
        graphics.drawString(font, tagStr, tagX, y + 5, 0xFFFFFFFF, false);

        // 3. Net Rate: e.g. -1.08M B/s or +282.01/s
        String sign = item.netRate > 0.0001 ? "+" : "";
        String rateFormatted = sign + NodeCardRenderer.formatRate(item.netRate, item.stack.isFluid());
        int rateColor = item.statusType == 0 ? 0xFFFF5555 : (item.statusType == 1 ? 0xFF55FF55 : 0xFF55FFFF);
        int rateW = font.width(rateFormatted);
        int rateX = tagX - 6 - rateW;
        graphics.drawString(font, rateFormatted, rateX, y + 5, rateColor, false);

        // 4. Flow Details: (+Prod -Cons) dynamically placed to the left of Net Rate
        String flowDetails = String.format("§7(+%s -%s)", FormatUtil.formatCompactNumber(item.producedRate), FormatUtil.formatCompactNumber(item.consumedRate));
        int flowW = font.width(flowDetails);
        int flowX = rateX - 6 - flowW;
        graphics.drawString(font, flowDetails, flowX, y + 5, 0xFF888888, false);

        // 5. Name: dynamically sized to fit available width between Icon (x + 24) and Flow Details (flowX)
        int maxNameW = Math.max(20, (flowX - 6) - (x + 24));
        String name = item.stack.getDisplayName();
        if (font.width(name) > maxNameW) {
            name = font.plainSubstrByWidth(name, maxNameW - 6) + "..";
        }
        graphics.drawString(font, "§f" + name, x + 24, y + 5, 0xFFFFFFFF, false);

        if (rowHover) {
            hoveredStack = item.stack;
            hoveredRate = item.netRate;
        }
    }

    private void renderTooltips(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (hoveredPower && cachedSummary != null) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("§6⚡ " + Component.translatable("gui.gtcalcboard.global_balance.modal_title").getString()));
            double netEUt = cachedSummary.totalGeneratedEUt() - cachedSummary.totalConsumedEUt();
            var tier = cachedSummary.highestVoltageTier();
            if (tier == null) tier = GTVoltageTier.LV;
            double amps = Math.abs(netEUt) / (double) tier.getVoltage();
            tooltip.add(Component.literal(String.format(Locale.ROOT, "§a+Gen: §f%,.2f EU/t", cachedSummary.totalGeneratedEUt())));
            tooltip.add(Component.literal(String.format(Locale.ROOT, "§c-Drain: §f%,.2f EU/t", cachedSummary.totalConsumedEUt())));
            tooltip.add(Component.literal(String.format(Locale.ROOT, "§e= Net: §f%,.2f EU/t (%,.2fA %s)", netEUt, amps, tier.getName())));
            graphics.renderTooltip(font, tooltip, Optional.empty(), mouseX, mouseY);
            return;
        }

        if (hoveredMachines && cachedSummary != null && !cachedSummary.machineBreakdown().isEmpty()) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("§6🏭 " + Component.translatable("gui.gtcalcboard.total_machines_breakdown").getString()));
            for (Map.Entry<String, Integer> entry : cachedSummary.machineBreakdown().entrySet()) {
                tooltip.add(Component.literal("§7• " + entry.getKey() + ": §f" + entry.getValue() + Component.translatable("gui.gtcalcboard.machine_unit").getString()));
            }
            graphics.renderTooltip(font, tooltip, Optional.empty(), mouseX, mouseY);
            return;
        }

        if (hoveredStack != null) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal(hoveredStack.getDisplayName()));
            String exactRateStr = FormatUtil.formatExactRate(hoveredRate, hoveredStack.isFluid());
            String ratePrefix = hoveredRate > 0 ? "+" : "";
            tooltip.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.global_balance.net_rate").getString() + ": §f" + ratePrefix + exactRateStr));
            tooltip.add(Component.literal("§8" + Component.translatable("gui.gtcalcboard.global_balance.click_drilldown_hint").getString()));
            graphics.renderTooltip(font, tooltip, Optional.empty(), mouseX, mouseY);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
        if (!visible) return false;

        if (contributionPopup.isVisible()) {
            return contributionPopup.mouseClicked(mouseX, mouseY, button, screenWidth, screenHeight);
        }

        Font font = Minecraft.getInstance().font;
        int dialogW = Math.min(DIALOG_WIDTH, screenWidth - 16);
        int dialogH = Math.min(DIALOG_HEIGHT, screenHeight - 16);
        int dialogX = (screenWidth - dialogW) / 2;
        int dialogY = (screenHeight - dialogH) / 2;

        // 1. Close button
        int closeX = dialogX + dialogW - 20;
        int closeY = dialogY + 4;
        if (mouseX >= closeX && mouseX <= closeX + 16 && mouseY >= closeY && mouseY <= closeY + 16 && button == 0) {
            close();
            return true;
        }

        // 2. Sidebar selection buttons & checkboxes
        int sidebarX = dialogX + 6;
        int bannerH = 26;
        int sidebarY = dialogY + 26 + bannerH + 4;
        int sidebarH = dialogH - (sidebarY - dialogY) - 6;

        int btnH = 15;
        int btnY = sidebarY + sidebarH - btnH - 3;
        int btnW = (SIDEBAR_WIDTH - 9) / 2;
        int allBtnX = sidebarX + 3;
        int noneBtnX = allBtnX + btnW + 3;

        // [Select All] clicked
        if (mouseX >= allBtnX && mouseX <= allBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH && button == 0) {
            for (BoardPage page : BoardManager.getInstance().getPages()) {
                selectedPageIds.add(page.getId());
            }
            this.dirty = true;
            playClickSound();
            return true;
        }

        // [Deselect All] clicked
        if (mouseX >= noneBtnX && mouseX <= noneBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH && button == 0) {
            selectedPageIds.clear();
            this.dirty = true;
            playClickSound();
            return true;
        }

        // Page Checkbox Rows clicked
        int pageListY = sidebarY + 18;
        int pageListH = btnY - pageListY - 2;
        if (mouseX >= sidebarX && mouseX <= sidebarX + SIDEBAR_WIDTH && mouseY >= pageListY && mouseY <= pageListY + pageListH && button == 0) {
            List<BoardPage> pages = BoardManager.getInstance().getPages();
            int rowH = 18;
            int clickedIdx = (int) ((mouseY - pageListY + pageScrollY) / rowH);
            if (clickedIdx >= 0 && clickedIdx < pages.size()) {
                BoardPage p = pages.get(clickedIdx);
                if (selectedPageIds.contains(p.getId())) {
                    selectedPageIds.remove(p.getId());
                } else {
                    selectedPageIds.add(p.getId());
                }
                this.dirty = true;
                playClickSound();
                return true;
            }
        }

        // 3. Filter Tabs
        int mainX = dialogX + SIDEBAR_WIDTH + 12;
        int tabY = sidebarY + 3;
        int tabH = 14;

        String[] tabs = {
            Component.translatable("gui.gtcalcboard.addon_cat.all").getString() + " (" + countAllStacks() + ")",
            "§c" + Component.translatable("gui.gtcalcboard.global_balance.tab_deficits").getString() + " (" + cachedSummary.rawInputs().size() + ")",
            "§a" + Component.translatable("gui.gtcalcboard.global_balance.tab_surplus").getString() + " (" + cachedSummary.netOutputs().size() + ")",
            "§b" + Component.translatable("gui.gtcalcboard.global_balance.tab_balanced").getString() + " (" + cachedSummary.fullyBalanced().size() + ")"
        };

        int curTabX = mainX + 4;
        for (int i = 0; i < tabs.length; i++) {
            int curW = font.width(tabs[i]) + 8;
            if (mouseX >= curTabX && mouseX <= curTabX + curW && mouseY >= tabY && mouseY <= tabY + tabH && button == 0) {
                this.filterTab = i;
                this.itemScrollY = 0;
                playClickSound();
                return true;
            }
            curTabX += curW + 3;
        }

        // Search Box click
        if (searchBox != null && searchBox.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // 4. Material Row click -> Open Drilldown Popup
        int itemListY = sidebarY + 22;
        int mainW = dialogW - SIDEBAR_WIDTH - 18;
        int itemListH = sidebarH - 26;
        if (mouseX >= mainX && mouseX <= mainX + mainW && mouseY >= itemListY && mouseY <= itemListY + itemListH && button == 0) {
            List<BalanceRowItem> rows = buildDisplayRows();
            int rowH = 20;
            int clickedIdx = (int) ((mouseY - itemListY + itemScrollY) / rowH);
            if (clickedIdx >= 0 && clickedIdx < rows.size()) {
                BalanceRowItem item = rows.get(clickedIdx);
                List<GlobalBalanceSummary.PageContribution> contribs = cachedSummary.getContributionsFor(item.stack);
                contributionPopup.open(item.stack, contribs, item.netRate);
                playClickSound();
                return true;
            }
        }

        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible) return false;

        if (contributionPopup.isVisible()) {
            return contributionPopup.mouseScrolled(mouseX, mouseY, delta, parent.width, parent.height);
        }

        int dialogW = Math.min(DIALOG_WIDTH, parent.width - 16);
        int dialogH = Math.min(DIALOG_HEIGHT, parent.height - 16);
        int dialogX = (parent.width - dialogW) / 2;
        int dialogY = (parent.height - dialogH) / 2;

        int sidebarX = dialogX + 6;
        int bannerH = 26;
        int sidebarY = dialogY + 26 + bannerH + 4;
        int sidebarH = dialogH - (sidebarY - dialogY) - 6;

        // Sidebar scroll
        if (mouseX >= sidebarX && mouseX <= sidebarX + SIDEBAR_WIDTH && mouseY >= sidebarY && mouseY <= sidebarY + sidebarH) {
            if (maxPageScrollY > 0) {
                pageScrollY = Math.max(0, Math.min(maxPageScrollY, pageScrollY - (delta * 18.0)));
                return true;
            }
        }

        // Main table scroll
        int mainX = dialogX + SIDEBAR_WIDTH + 12;
        int mainW = dialogW - SIDEBAR_WIDTH - 18;
        if (mouseX >= mainX && mouseX <= mainX + mainW && mouseY >= sidebarY && mouseY <= sidebarY + sidebarH) {
            if (maxItemScrollY > 0) {
                itemScrollY = Math.max(0, Math.min(maxItemScrollY, itemScrollY - (delta * 20.0)));
                return true;
            }
        }

        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;

        if (contributionPopup.isVisible()) {
            return contributionPopup.keyPressed(keyCode, scanCode, modifiers);
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        if (searchBox != null && searchBox.isFocused()) {
            searchBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }

        return true;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible) return false;
        if (contributionPopup.isVisible()) return true;

        if (searchBox != null && searchBox.isFocused()) {
            return searchBox.charTyped(codePoint, modifiers);
        }
        return false;
    }

    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
        );
    }

    private static double findRate(Map<IngredientStack, Double> map, IngredientStack stack) {
        if (stack == null || map == null) return 0.0;
        for (Map.Entry<IngredientStack, Double> entry : map.entrySet()) {
            if (entry.getKey().equals(stack) || entry.getKey().matchesOrAlternative(stack) || stack.matchesOrAlternative(entry.getKey())) {
                return entry.getValue();
            }
        }
        return 0.0;
    }
}
