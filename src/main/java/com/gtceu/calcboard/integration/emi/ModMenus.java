package com.gtceu.calcboard.integration.emi;

import com.gtceu.calcboard.GregTechCalcBoard;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(BuiltInRegistries.MENU, GregTechCalcBoard.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<BoardMenu>> BOARD_MENU = MENUS.register("board_menu",
            () -> IMenuTypeExtension.create((windowId, inv, data) -> new BoardMenu(windowId, inv)));

    private ModMenus() {}
}
