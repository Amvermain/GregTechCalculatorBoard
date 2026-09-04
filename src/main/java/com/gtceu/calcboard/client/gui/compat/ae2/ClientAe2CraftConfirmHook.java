package com.gtceu.calcboard.client.gui.compat.ae2;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import com.gtceu.calcboard.GregTechCalcBoard;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.integration.ae2.evaluator.Ae2CraftingPlanEvaluator;
import com.gtceu.calcboard.integration.ae2.model.Ae2PlanEvaluationResult;
import com.gtceu.calcboard.integration.ae2.model.Ae2PlanStep;
import com.gtceu.calcboard.integration.ae2.model.PatternId;
import com.gtceu.calcboard.integration.ae2.registry.PatternGraphRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Client Forge screen event hook for AE2 CraftConfirmScreen and item tooltips.
 * Renders an unobtrusive top header Total ETA banner, linked graph indicators, step breakdowns, and interactive bottleneck deep-link badges.
 */
@Mod.EventBusSubscriber(modid = GregTechCalcBoard.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientAe2CraftConfirmHook {

    private static int lastBottleneckX = 0;
    private static int lastBottleneckY = 0;
    private static int lastBottleneckW = 0;
    private static int lastBottleneckH = 0;
    private static String lastBottleneckPageId = "";

    private static Method getPlanMethod = null;
    private static Method getEntriesMethod = null;
    private static Method getCpuCoProcessorsMethod = null;
    private static boolean reflectionInitialized = false;

    static {
        initReflection();
    }

    private static void initReflection() {
        if (reflectionInitialized) return;
        try {
            Class<?> menuClass = Class.forName("appeng.menu.me.crafting.CraftConfirmMenu");
            getCpuCoProcessorsMethod = menuClass.getMethod("getCpuCoProcessors");
            getPlanMethod = menuClass.getMethod("getPlan");

            Class<?> planSummaryClass = Class.forName("appeng.menu.me.crafting.CraftingPlanSummary");
            getEntriesMethod = planSummaryClass.getMethod("getEntries");
        } catch (Throwable ignored) {
        }
        reflectionInitialized = true;
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!ModCompatHelper.isAe2Loaded()) return;
        Screen screen = event.getScreen();
        if (!isCraftConfirmScreen(screen)) return;

        AbstractContainerScreen<?> containerScreen = (AbstractContainerScreen<?>) screen;
        Ae2PlanEvaluationResult etaResult = resolveEtaResult(containerScreen);
        if (etaResult == null || etaResult.totalDurationTicks() <= 0) return;

        renderEtaPanel(event.getGuiGraphics(), Minecraft.getInstance().font, containerScreen, event.getMouseX(), event.getMouseY(), etaResult);
    }

    @SubscribeEvent
    public static void onScreenMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!ModCompatHelper.isAe2Loaded()) return;
        if (!isCraftConfirmScreen(event.getScreen())) return;

        if (isInsideBottleneckBadge(event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
            openBottleneckPage(lastBottleneckPageId);
        }
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!ModCompatHelper.isAe2Loaded()) return;
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        PatternGraphRegistry registry = PatternGraphRegistry.getInstance();
        Optional<BoardPage> boundOpt = registry.getBoundPage(stack);
        if (boundOpt.isPresent()) {
            BoardPage page = boundOpt.get();
            event.getToolTip().add(Component.literal("§b⚡ " + Component.translatable("gui.gtcalcboard.ae2.tooltip.linked_page", page.getName()).getString()));
            return;
        }

        if (appeng.api.crafting.PatternDetailsHelper.isEncodedPattern(stack)) {
            event.getToolTip().add(Component.literal("§8• " + Component.translatable("gui.gtcalcboard.ae2.tooltip.create_hint").getString()));
        }
    }

    private static boolean isCraftConfirmScreen(Screen screen) {
        if (screen == null) return false;
        return screen.getClass().getName().contains("CraftConfirmScreen");
    }

    private static Ae2PlanEvaluationResult resolveEtaResult(AbstractContainerScreen<?> containerScreen) {
        Ae2PlanEvaluationResult cached = ClientCraftingEtaHolder.getInstance().getCurrentResult();
        if (cached != null && cached.totalDurationTicks() > 0) {
            return cached;
        }

        return evaluateFromMenu(containerScreen.getMenu());
    }

    private static Ae2PlanEvaluationResult evaluateFromMenu(AbstractContainerMenu menu) {
        if (menu == null || getPlanMethod == null || getEntriesMethod == null) return null;
        try {
            Object planSummary = getPlanMethod.invoke(menu);
            if (planSummary == null) return null;

            int coProcessors = getCpuCoProcessorsMethod != null ? (int) getCpuCoProcessorsMethod.invoke(menu) : 0;
            List<?> entries = (List<?>) getEntriesMethod.invoke(planSummary);
            if (entries == null || entries.isEmpty()) return null;

            Map<PatternId, Long> patternCounts = extractPatternCountsFromEntries(entries);
            return Ae2CraftingPlanEvaluator.evaluatePatternCounts(patternCounts, coProcessors);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Map<PatternId, Long> extractPatternCountsFromEntries(List<?> entries) {
        Map<PatternId, Long> map = new HashMap<>();
        for (Object entry : entries) {
            processEntry(entry, map);
        }
        return map;
    }

    private static void processEntry(Object entry, Map<PatternId, Long> map) {
        try {
            Method getCraftAmount = entry.getClass().getMethod("getCraftAmount");
            Method getWhat = entry.getClass().getMethod("getWhat");
            long craftAmount = (long) getCraftAmount.invoke(entry);
            if (craftAmount <= 0) return;

            Object aeKey = getWhat.invoke(entry);
            if (aeKey instanceof AEItemKey ak) {
                ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(ak.getItem());
                ItemStack icon = ak.toStack(1);
                String name = icon.getHoverName().getString();
                map.put(new PatternId(itemId.toString(), name, icon, itemId), craftAmount);
            } else if (aeKey instanceof AEFluidKey fk) {
                ResourceLocation fluidId = ForgeRegistries.FLUIDS.getKey(fk.getFluid());
                String name = fk.getDisplayName().getString();
                map.put(new PatternId(fluidId.toString(), name, ItemStack.EMPTY, fluidId), craftAmount);
            } else if (aeKey != null) {
                String keyStr = aeKey.toString();
                map.put(PatternId.ofKey(keyStr, keyStr, null), craftAmount);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void renderEtaPanel(GuiGraphics graphics, Font font, AbstractContainerScreen<?> screen, int mouseX, int mouseY, Ae2PlanEvaluationResult eta) {
        int left = screen.getGuiLeft();
        int top = screen.getGuiTop();
        int width = screen.getXSize();
        int height = screen.getYSize();

        int boundCount = countBoundPages(eta);
        boolean hasLinkedGraphs = boundCount > 0;

        int panelW = Math.max(220, width);
        int panelH = 26;
        int panelX = left;
        int panelY = (top >= 28) ? (top - 28) : (top + height + 2);

        int bgColor = hasLinkedGraphs ? 0xEE0B1626 : 0xDD121A24;
        int borderColor = hasLinkedGraphs ? 0xFF0284C7 : 0xFF3D4455;

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, bgColor);
        graphics.renderOutline(panelX, panelY, panelW, panelH, borderColor);

        Component title = Component.translatable("gui.gtcalcboard.ae2.eta.title", eta.formattedEta());
        String etaText = "§b⚡ " + title.getString();
        if (hasLinkedGraphs) {
            etaText += " §7(" + boundCount + " " + Component.translatable("gui.gtcalcboard.ae2.badge.linked_short").getString() + ")";
        }
        graphics.drawString(font, etaText, panelX + 6, panelY + 4, 0xFFFFFFFF, false);

        renderBottleneckButton(graphics, font, panelX, panelY + 14, panelW, mouseX, mouseY, eta);
        renderEtaTooltipIfHovered(graphics, font, panelX, panelY, mouseX, mouseY, eta);
    }

    private static void renderEtaTooltipIfHovered(GuiGraphics graphics, Font font, int panelX, int panelY, int mouseX, int mouseY, Ae2PlanEvaluationResult eta) {
        if (mouseX < panelX || mouseX > panelX + 160 || mouseY < panelY || mouseY > panelY + 13) {
            return;
        }

        List<Component> tooltipLines = new ArrayList<>();
        tooltipLines.add(Component.literal("§b⚡ " + Component.translatable("gui.gtcalcboard.ae2.eta.title", eta.formattedEta()).getString()));
        if (eta.coProcessors() > 0) {
            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.ae2.eta.coprocessors", eta.coProcessors()).getString()));
        }

        for (Ae2PlanStep step : eta.steps()) {
            String stepTime = Ae2CraftingPlanEvaluator.formatEtaDuration(step.totalDurationTicks() / 20.0);
            String boundBadge = !step.boundPageId().isEmpty() ? " §b[⚡ " + step.boundPageName() + "]" : " §8(Standard)";
            tooltipLines.add(Component.literal("§7• " + step.stepName() + " x" + step.totalCount() + ": §f" + stepTime + boundBadge));
        }

        tooltipLines.add(Component.literal("§8§m----------------------------------------"));
        tooltipLines.add(Component.literal("§8* " + Component.translatable("gui.gtcalcboard.ae2.eta.disclaimer").getString()));

        graphics.renderComponentTooltip(font, tooltipLines, mouseX, mouseY);
    }

    private static int countBoundPages(Ae2PlanEvaluationResult eta) {
        int count = 0;
        for (Ae2PlanStep step : eta.steps()) {
            if (step.boundPageId() != null && !step.boundPageId().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static void renderBottleneckButton(GuiGraphics graphics, Font font, int panelX, int btnY, int panelW, int mouseX, int mouseY, Ae2PlanEvaluationResult eta) {
        String bName = eta.bottleneckName().isEmpty() ? "Standard" : eta.bottleneckName();
        int maxNameWidth = panelW - 35;
        String displayBName = font.plainSubstrByWidth(bName, maxNameWidth);
        if (!displayBName.equals(bName) && displayBName.length() > 3) {
            displayBName = displayBName.substring(0, displayBName.length() - 3) + "...";
        }

        String fullStr = "§e↔ " + displayBName + " §a↗";
        int btnW = font.width(fullStr) + 6;
        int btnH = 10;
        int btnX = panelX + 4;

        boolean hover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        if (hover) {
            graphics.fill(btnX, btnY - 1, btnX + btnW, btnY + btnH, 0x4438BDF8);
            graphics.renderOutline(btnX, btnY - 1, btnW, btnH + 1, 0xFF38BDF8);
        }

        graphics.drawString(font, fullStr, btnX + 3, btnY, 0xFFFFFFFF, false);

        lastBottleneckX = btnX;
        lastBottleneckY = btnY;
        lastBottleneckW = btnW;
        lastBottleneckH = btnH;
        lastBottleneckPageId = eta.bottleneckPageId();
    }

    private static boolean isInsideBottleneckBadge(double mx, double my) {
        return lastBottleneckW > 0 && mx >= lastBottleneckX && mx <= lastBottleneckX + lastBottleneckW
                && my >= lastBottleneckY && my <= lastBottleneckY + lastBottleneckH;
    }

    private static void openBottleneckPage(String pageId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.6f, 1.0f);
        BoardManager.getInstance().openPage(pageId);
        mc.setScreen(new BoardScreen());
    }
}
