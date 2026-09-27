package com.gtceu.calcboard.client.gui.inspector.section;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.api.IBoardScreenContext;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

public class InspectorHeaderSection implements IInspectorSection {

    private NodeWidget widget;
    private RecipeNode node;
    private IBoardScreenContext screen;

    @Override
    public void bind(NodeWidget widget, RecipeNode node, IBoardScreenContext screen) {
        this.widget = widget;
        this.node = node;
        this.screen = screen;
    }

    @Override
    public boolean isApplicable(RecipeNode node) {
        return node != null;
    }

    @Override
    public int getHeight(RecipeNode node) {
        return 28;
    }

    @Override
    public boolean isFullWidth() {
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int w, int mouseX, int mouseY) {
        if (node == null) return;

        graphics.fill(x, y, x + w, y + 22, 0xFF1E293B);
        graphics.renderOutline(x, y, w, 22, 0xFF475569);

        int titleX = renderNodeIcon(graphics, x, y);
        renderTitleAndClose(graphics, font, x, y, w, titleX, mouseX, mouseY);
    }

    private int renderNodeIcon(GuiGraphics graphics, int x, int y) {
        if (node.getMachineIcon() == null) {
            return x + 6;
        }

        Item item = ForgeRegistries.ITEMS != null ? ForgeRegistries.ITEMS.getValue(node.getMachineIcon()) : null;
        if ((item == null || item == Items.AIR) && ForgeRegistries.BLOCKS != null) {
            var block = ForgeRegistries.BLOCKS.getValue(node.getMachineIcon());
            if (block != null && block.asItem() != Items.AIR) {
                item = block.asItem();
            }
        }

        if (item != null && item != Items.AIR) {
            graphics.renderItem(new ItemStack(item), x + 4, y + 3);
            return x + 24;
        }
        return x + 6;
    }

    private void renderTitleAndClose(GuiGraphics graphics, Font font, int x, int y, int w, int titleX, int mouseX, int mouseY) {
        String nodeName = font.plainSubstrByWidth(node.getName(), w - 44);
        graphics.drawString(font, nodeName, titleX, y + 7, 0xFFE2E8F0, false);

        int closeX = x + w - 16;
        int closeY = y + 5;
        boolean closeHov = mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12;
        graphics.drawString(font, "✕", closeX + 1, closeY + 1, closeHov ? 0xFFEF4444 : 0xFF94A3B8, false);
    }

    @Override
    public boolean mouseClicked(int x, int y, int w, double mouseX, double mouseY, int button) {
        if (button != 0 || node == null) return false;

        int closeX = x + w - 16;
        int closeY = y + 5;
        if (mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12) {
            if (screen != null && screen.getNodeInspectorPanel() != null) {
                screen.getNodeInspectorPanel().close();
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            return true;
        }
        return false;
    }
}
