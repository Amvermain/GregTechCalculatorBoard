package com.gtceu.calcboard.integration.emi;

import com.gtceu.calcboard.GregTechCalcBoard;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, GregTechCalcBoard.MOD_ID);

    public static final RegistryObject<MenuType<BoardMenu>> BOARD_MENU = MENUS.register("board_menu",
            () -> IForgeMenuType.create((windowId, inv, data) -> new BoardMenu(windowId, inv)));

    private ModMenus() {}
}
