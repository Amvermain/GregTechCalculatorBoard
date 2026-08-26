package com.gtceu.calcboard.integration.emi;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

public class BoardMenu extends AbstractContainerMenu {

    public BoardMenu(int containerId, Inventory playerInventory) {
        super(ModMenus.BOARD_MENU.isPresent() ? ModMenus.BOARD_MENU.get() : null, containerId);
    }

    public BoardMenu() {
        this(0, null);
    }

    public BoardMenu(MenuType<?> type, int containerId) {
        super(type, containerId);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
