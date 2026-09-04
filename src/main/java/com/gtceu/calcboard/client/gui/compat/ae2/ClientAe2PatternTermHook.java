package com.gtceu.calcboard.client.gui.compat.ae2;

import appeng.menu.me.items.PatternEncodingTermMenu;
import com.gtceu.calcboard.GregTechCalcBoard;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.integration.ae2.generator.Ae2PatternPageGenerator;
import com.gtceu.calcboard.integration.ae2.model.PatternId;
import com.gtceu.calcboard.integration.ae2.registry.PatternGraphRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Optional;

/**
 * Client Forge screen event hook for AE2 Pattern Encoding Terminal.
 * Injects a dedicated GTCalcBoard page generation button and handles Shift+A pattern shortcuts.
 */
@Mod.EventBusSubscriber(modid = GregTechCalcBoard.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientAe2PatternTermHook {

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!ModCompatHelper.isAe2Loaded()) return;
        Screen screen = event.getScreen();
        if (!isPatternEncodingScreen(screen)) return;

        AbstractContainerScreen<?> containerScreen = (AbstractContainerScreen<?>) screen;
        int btnX = containerScreen.getGuiLeft() + 80;
        int btnY = containerScreen.getGuiTop() - 18;

        Button calcBoardBtn = Button.builder(Component.literal("§b⚡ CalcBoard"), btn -> handleTerminalButtonClick(containerScreen))
                .pos(btnX, btnY)
                .size(76, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.gtcalcboard.tooltip.btn_ae2_create_page")))
                .build();

        event.addListener(calcBoardBtn);
    }

    public static boolean tryHandlePatternShortcut(Screen screen, double mouseX, double mouseY) {
        if (!ModCompatHelper.isAe2Loaded()) return false;
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) return false;

        Minecraft mc = Minecraft.getInstance();
        if (tryCreateFromHoveredSlot(containerScreen, mc.level)) {
            return true;
        }

        return tryCreateFromTerminal(containerScreen);
    }

    private static void handleTerminalButtonClick(AbstractContainerScreen<?> screen) {
        if (!(screen.getMenu() instanceof PatternEncodingTermMenu termMenu)) return;

        PatternId patternId = Ae2PatternPageGenerator.createPatternIdFromTerminalMenu(termMenu);
        Optional<BoardPage> existing = PatternGraphRegistry.getInstance().getBoundPage(patternId);
        if (existing.isPresent()) {
            switchToPageAndNotify(existing.get());
            return;
        }

        BoardPage page = Ae2PatternPageGenerator.createPageFromTerminalMenu(termMenu);
        openPageAndNotify(page);
    }

    private static boolean tryCreateFromHoveredSlot(AbstractContainerScreen<?> screen, Level level) {
        Slot hoveredSlot = screen.getSlotUnderMouse();
        if (hoveredSlot == null || !hoveredSlot.hasItem()) return false;

        ItemStack stack = hoveredSlot.getItem();
        if (!appeng.api.crafting.PatternDetailsHelper.isEncodedPattern(stack)) return false;

        Optional<BoardPage> existing = PatternGraphRegistry.getInstance().getBoundPage(stack);
        if (existing.isPresent()) {
            switchToPageAndNotify(existing.get());
            return true;
        }

        BoardPage page = Ae2PatternPageGenerator.createPageFromPatternStack(stack, level);
        return openPageAndNotify(page);
    }

    private static boolean tryCreateFromTerminal(AbstractContainerScreen<?> screen) {
        if (!isPatternEncodingScreen(screen)) return false;
        if (!(screen.getMenu() instanceof PatternEncodingTermMenu termMenu)) return false;

        handleTerminalButtonClick(screen);
        return true;
    }

    private static boolean openPageAndNotify(BoardPage page) {
        if (page == null) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.6f, 1.0f);
            mc.player.sendSystemMessage(Component.translatable("gui.gtcalcboard.ae2.pattern_page_created", page.getName()));
        }

        mc.setScreen(new BoardScreen());
        return true;
    }

    private static void switchToPageAndNotify(BoardPage page) {
        if (page == null) return;
        BoardManager.getInstance().openPage(page.getId());

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.6f, 1.0f);
            mc.player.sendSystemMessage(Component.translatable("gui.gtcalcboard.toast.switched_to_page", page.getName()));
        }

        mc.setScreen(new BoardScreen());
    }

    private static boolean isPatternEncodingScreen(Screen screen) {
        if (screen == null) return false;
        return screen.getClass().getName().contains("PatternEncodingTermScreen");
    }
}
