package com.gtceu.calcboard.client.gui.compat.create;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import com.gtceu.calcboard.compat.create.CreateModAdapter;
import com.gtceu.calcboard.compat.create.CreateProperties;
import com.gtceu.calcboard.compat.create.addon.CreateHeaterAddon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Locale;

/**
 * Client GUI view component for configuring Create Steam Boiler physical parameters (ADR-036).
 */
@OnlyIn(Dist.CLIENT)
public class CreateBoilerConfigView {

    private final MachineConfigDialog dialog;

    private static final int[] SIZE_PRESETS = {4, 16, 36, 72};
    private static final int[] HEAT_PRESETS = {0, 1, 4, 9, 18};

    public CreateBoilerConfigView(MachineConfigDialog dialog) {
        this.dialog = dialog;
    }

    public void render(GuiGraphics graphics, Font font, RecipeNode node, int startX, int startY, int width, int height, int mouseX, int mouseY) {
        renderHeaderSummary(graphics, font, node, startX, startY, width, mouseX, mouseY);
        renderSizeControlRow(graphics, font, node, startX, startY + 38, width, mouseX, mouseY);
        renderHeatControlRow(graphics, font, node, startX, startY + 68, width, mouseX, mouseY);
        renderWaterControlRow(graphics, font, node, startX, startY + 98, width, mouseX, mouseY);
        renderQuickAddonRow(graphics, font, node, startX, startY + 128, width, mouseX, mouseY);
    }

    private void renderHeaderSummary(GuiGraphics graphics, Font font, RecipeNode node, int x, int y, int w, int mx, int my) {
        int size = node.getProperties().get(CreateProperties.BOILER_SIZE_BLOCKS);
        int heat = node.getProperties().get(CreateProperties.BOILER_HEAT_LEVEL);
        int water = node.getProperties().get(CreateProperties.BOILER_WATER_MB_TICK);
        int eff = CreateProperties.calculateEffectiveLevel(size, heat, water);
        CreateProperties.BoilerBottleneck bottleneck = CreateProperties.getBottleneck(size, heat, water);

        graphics.fill(x, y, x + w, y + 34, 0xFF14161E);
        graphics.renderOutline(x, y, w, 34, 0xFF2D3342);

        String title = formatSummaryTitle(eff, node.getBaseEUt(), bottleneck);
        graphics.drawString(font, title, x + 6, y + 5, 0xFFFFFFFF, false);

        int barW = (w - 24) / 3;
        renderMiniStatusBar(graphics, font, "Size", size, 72, x + 6, y + 17, barW, 0xFF44AAFF);
        renderMiniStatusBar(graphics, font, "Heat", heat, 18, x + 10 + barW, y + 17, barW, 0xFFFF8822);
        renderMiniStatusBar(graphics, font, "Water", water, 180, x + 14 + barW * 2, y + 17, barW, 0xFF55DDFF);
    }

    private String formatSummaryTitle(int eff, double su, CreateProperties.BoilerBottleneck bn) {
        if (eff < 0) {
            return "§c♨ " + Component.translatable("gui.gtcalcboard.create.boiler_inactive").getString();
        }
        String levelStr = eff == 0
                ? Component.translatable("gui.gtcalcboard.create.boiler_passive").getString()
                : Component.translatable("gui.gtcalcboard.create.boiler_level", eff).getString();
        String bnStr = switch (bn) {
            case SIZE -> " §e⚠ " + Component.translatable("gui.gtcalcboard.create.boiler_bn_size").getString();
            case WATER -> " §9⚠ " + Component.translatable("gui.gtcalcboard.create.boiler_bn_water").getString();
            case HEAT -> " §6⚠ " + Component.translatable("gui.gtcalcboard.create.boiler_bn_heat").getString();
            case NONE, INACTIVE -> " §a✔ " + Component.translatable("gui.gtcalcboard.create.boiler_bn_balanced").getString();
        };
        return String.format(Locale.ROOT, "§6♨ %s §7(+%,.0f SU) |%s", levelStr, su, bnStr);
    }

    private void renderMiniStatusBar(GuiGraphics graphics, Font font, String label, int current, int max, int bx, int by, int bw, int color) {
        graphics.fill(bx, by, bx + bw, by + 12, 0xFF1C202B);
        graphics.renderOutline(bx, by, bw, 12, 0xFF353C4D);
        int fillW = Math.max(0, Math.min(bw - 2, (bw - 2) * current / max));
        if (fillW > 0) {
            graphics.fill(bx + 1, by + 1, bx + 1 + fillW, by + 11, color | 0x88000000);
        }
        String text = String.format(Locale.ROOT, "%s: %d/%d", label, current, max);
        graphics.drawCenteredString(font, text, bx + bw / 2, by + 2, 0xFFE0E6F0);
    }

    private void renderSizeControlRow(GuiGraphics graphics, Font font, RecipeNode node, int x, int y, int w, int mx, int my) {
        int size = node.getProperties().get(CreateProperties.BOILER_SIZE_BLOCKS);
        int cap = CreateProperties.getSizeLevel(size);

        graphics.fill(x, y, x + w, y + 26, 0xFF181B24);
        graphics.renderOutline(x, y, w, 26, 0xFF2D3342);

        String label = String.format(Locale.ROOT, "§b📦 %s: §f%d §7(Lv.%d Cap)",
                Component.translatable("gui.gtcalcboard.create.boiler_size").getString(), size, cap);
        graphics.drawString(font, label, x + 6, y + 9, 0xFFFFFFFF, false);

        int bx = x + w - 176;
        renderButton(graphics, font, "-", bx, y + 5, 18, 16, mx, my, false);
        bx += 22;
        renderButton(graphics, font, "+", bx, y + 5, 18, 16, mx, my, false);
        bx += 22;

        for (int p : SIZE_PRESETS) {
            boolean active = size == p;
            renderButton(graphics, font, String.valueOf(p), bx, y + 5, 30, 16, mx, my, active);
            bx += 34;
        }
    }

    private void renderHeatControlRow(GuiGraphics graphics, Font font, RecipeNode node, int x, int y, int w, int mx, int my) {
        int heat = node.getProperties().get(CreateProperties.BOILER_HEAT_LEVEL);
        String heatName = heat == 0
                ? Component.translatable("gui.gtcalcboard.create.boiler_passive").getString()
                : Component.translatable("gui.gtcalcboard.create.boiler_level", heat).getString();

        graphics.fill(x, y, x + w, y + 26, 0xFF181B24);
        graphics.renderOutline(x, y, w, 26, 0xFF2D3342);

        String label = String.format(Locale.ROOT, "§6♨ %s: §f%s",
                Component.translatable("gui.gtcalcboard.create.boiler_heat").getString(), heatName);
        graphics.drawString(font, label, x + 6, y + 9, 0xFFFFFFFF, false);

        int bx = x + w - 212;
        renderButton(graphics, font, "-", bx, y + 5, 18, 16, mx, my, false);
        bx += 22;
        renderButton(graphics, font, "+", bx, y + 5, 18, 16, mx, my, false);
        bx += 22;

        for (int p : HEAT_PRESETS) {
            String pLabel = p == 0 ? "Pass" : ("Lv." + p);
            int pw = p == 0 ? 36 : 30;
            boolean active = heat == p;
            renderButton(graphics, font, pLabel, bx, y + 5, pw, 16, mx, my, active);
            bx += pw + 4;
        }
    }

    private void renderWaterControlRow(GuiGraphics graphics, Font font, RecipeNode node, int x, int y, int w, int mx, int my) {
        int water = node.getProperties().get(CreateProperties.BOILER_WATER_MB_TICK);
        double flowSec = water * 20.0;

        graphics.fill(x, y, x + w, y + 26, 0xFF181B24);
        graphics.renderOutline(x, y, w, 26, 0xFF2D3342);

        String label = String.format(Locale.ROOT, "§3💧 %s: §f%d mB/t §7(%,.0f/s)",
                Component.translatable("gui.gtcalcboard.create.boiler_water_supply").getString(), water, flowSec);
        graphics.drawString(font, label, x + 6, y + 9, 0xFFFFFFFF, false);

        String matchLabel = Component.translatable("gui.gtcalcboard.create.boiler_auto_match").getString();
        int matchW = Math.max(50, font.width(matchLabel) + 8);
        int bx = x + w - (48 + matchW);

        renderButton(graphics, font, "-", bx, y + 5, 18, 16, mx, my, false);
        bx += 22;
        renderButton(graphics, font, "+", bx, y + 5, 18, 16, mx, my, false);
        bx += 22;
        renderButton(graphics, font, matchLabel, bx, y + 5, matchW, 16, mx, my, false);
    }

    private void renderQuickAddonRow(GuiGraphics graphics, Font font, RecipeNode node, int x, int y, int w, int mx, int my) {
        long heatedBurners = node.getAddons().stream().filter(a -> a instanceof CreateHeaterAddon h && !h.isSuperheated()).count();
        long superheatedBurners = node.getAddons().stream().filter(a -> a instanceof CreateHeaterAddon h && h.isSuperheated()).count();
        long totalBurners = heatedBurners + superheatedBurners;

        graphics.fill(x, y, x + w, y + 26, 0xFF161922);
        graphics.renderOutline(x, y, w, 26, 0xFF2D3342);

        String countLabel = String.format(Locale.ROOT, "§e✦ %s: §f%d/9 §7(🔥%d / ⚡%d)",
                Component.translatable("gui.gtcalcboard.create.boiler_burners").getString(), totalBurners, heatedBurners, superheatedBurners);
        graphics.drawString(font, countLabel, x + 6, y + 9, 0xFFFFFFFF, false);

        int bx = x + w - 212;
        boolean canAdd = totalBurners < 9;
        renderButton(graphics, font, "+ Heated", bx, y + 5, 54, 16, mx, my, false, canAdd);
        bx += 58;
        renderButton(graphics, font, "+ Super", bx, y + 5, 52, 16, mx, my, false, canAdd);
        bx += 56;
        renderButton(graphics, font, "- 1", bx, y + 5, 26, 16, mx, my, false, totalBurners > 0);
        bx += 30;
        renderButton(graphics, font, "Clr", bx, y + 5, 26, 16, mx, my, false, totalBurners > 0);
    }

    private void renderButton(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int bh, int mx, int my, boolean active) {
        renderButton(graphics, font, text, bx, by, bw, bh, mx, my, active, true);
    }

    private void renderButton(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int bh, int mx, int my, boolean active, boolean enabled) {
        boolean hover = enabled && mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
        int bg = !enabled ? 0xFF1A1C24 : (active ? 0xFF5D3E1A : (hover ? 0xFF3D4558 : 0xFF282D3B));
        int border = !enabled ? 0xFF2A2D3A : (active ? 0xFFFFAA00 : (hover ? 0xFF65728F : 0xFF3F4658));
        int txtColor = !enabled ? 0xFF555A6A : (active ? 0xFFFFD28C : (hover ? 0xFFFFFFFF : 0xFFB0B8C8));

        graphics.fill(bx, by, bx + bw, by + bh, bg);
        graphics.renderOutline(bx, by, bw, bh, border);
        graphics.drawCenteredString(font, text, bx + bw / 2, by + (bh - 8) / 2, txtColor);
    }

    public boolean mouseClicked(RecipeNode node, int startX, int startY, int width, int height, double mouseX, double mouseY, int button, BoardScreen parent) {
        if (mouseY >= startY + 38 && mouseY <= startY + 64) {
            return handleSizeRowClick(node, startX, startY + 38, width, mouseX, mouseY, parent);
        }
        if (mouseY >= startY + 68 && mouseY <= startY + 94) {
            return handleHeatRowClick(node, startX, startY + 68, width, mouseX, mouseY, parent);
        }
        if (mouseY >= startY + 98 && mouseY <= startY + 124) {
            return handleWaterRowClick(node, startX, startY + 98, width, mouseX, mouseY, parent);
        }
        if (mouseY >= startY + 128 && mouseY <= startY + 154) {
            return handleQuickAddonRowClick(node, startX, startY + 128, width, mouseX, mouseY, parent);
        }
        return false;
    }

    private boolean handleSizeRowClick(RecipeNode node, int x, int y, int w, double mx, double my, BoardScreen parent) {
        int cur = node.getProperties().get(CreateProperties.BOILER_SIZE_BLOCKS);
        int bx = x + w - 176;

        if (isInside(mx, my, bx, y + 5, 18, 16)) {
            CreateProperties.setBoilerSize(node, cur - 4);
            triggerUpdate(parent);
            return true;
        }
        bx += 22;
        if (isInside(mx, my, bx, y + 5, 18, 16)) {
            CreateProperties.setBoilerSize(node, cur + 4);
            triggerUpdate(parent);
            return true;
        }
        bx += 22;

        for (int p : SIZE_PRESETS) {
            if (isInside(mx, my, bx, y + 5, 30, 16)) {
                CreateProperties.setBoilerSize(node, p);
                triggerUpdate(parent);
                return true;
            }
            bx += 34;
        }
        return false;
    }

    private boolean handleHeatRowClick(RecipeNode node, int x, int y, int w, double mx, double my, BoardScreen parent) {
        int cur = node.getProperties().get(CreateProperties.BOILER_HEAT_LEVEL);
        int bx = x + w - 212;

        if (isInside(mx, my, bx, y + 5, 18, 16)) {
            CreateProperties.setBoilerHeat(node, cur - 1);
            triggerUpdate(parent);
            return true;
        }
        bx += 22;
        if (isInside(mx, my, bx, y + 5, 18, 16)) {
            CreateProperties.setBoilerHeat(node, cur + 1);
            triggerUpdate(parent);
            return true;
        }
        bx += 22;

        for (int p : HEAT_PRESETS) {
            int pw = p == 0 ? 36 : 30;
            if (isInside(mx, my, bx, y + 5, pw, 16)) {
                CreateProperties.setBoilerHeat(node, p);
                triggerUpdate(parent);
                return true;
            }
            bx += pw + 4;
        }
        return false;
    }

    private boolean handleWaterRowClick(RecipeNode node, int x, int y, int w, double mx, double my, BoardScreen parent) {
        int cur = node.getProperties().get(CreateProperties.BOILER_WATER_MB_TICK);
        int matchW = Math.max(50, Minecraft.getInstance().font.width(Component.translatable("gui.gtcalcboard.create.boiler_auto_match").getString()) + 8);
        int bx = x + w - (48 + matchW);

        if (isInside(mx, my, bx, y + 5, 18, 16)) {
            CreateProperties.setBoilerWater(node, cur - 10);
            triggerUpdate(parent);
            return true;
        }
        bx += 22;
        if (isInside(mx, my, bx, y + 5, 18, 16)) {
            CreateProperties.setBoilerWater(node, cur + 10);
            triggerUpdate(parent);
            return true;
        }
        bx += 22;

        if (isInside(mx, my, bx, y + 5, matchW, 16)) {
            int sizeLvl = CreateProperties.getSizeLevel(node.getProperties().get(CreateProperties.BOILER_SIZE_BLOCKS));
            int heatLvl = node.getProperties().get(CreateProperties.BOILER_HEAT_LEVEL);
            int targetLvl = Math.max(1, Math.min(sizeLvl, heatLvl > 0 ? heatLvl : 1));
            CreateProperties.setBoilerWater(node, targetLvl * 10);
            triggerUpdate(parent);
            return true;
        }
        return false;
    }

    private boolean handleQuickAddonRowClick(RecipeNode node, int x, int y, int w, double mx, double my, BoardScreen parent) {
        CreateModAdapter adapter = (CreateModAdapter) ModAdapterRegistry.getAdapterForNode(node);
        if (adapter == null) return false;

        int bx = x + w - 212;
        if (isInside(mx, my, bx, y + 5, 54, 16)) {
            MachineAddon heater = new CreateHeaterAddon("create:blaze_burner_heated", "gui.gtcalcboard.addon.create_heater", "gui.gtcalcboard.addon.create_heater_desc", ResourceLocation.tryParse("create:blaze_burner"), 1);
            if (adapter.canInstallAddon(node, heater)) {
                adapter.onAddonInstalled(node, heater);
                triggerUpdate(parent);
                return true;
            }
        }
        bx += 58;
        if (isInside(mx, my, bx, y + 5, 52, 16)) {
            MachineAddon superheated = new CreateHeaterAddon("create:blaze_burner_superheated", "gui.gtcalcboard.addon.create_superheated_heater", "gui.gtcalcboard.addon.create_superheated_heater_desc", ResourceLocation.tryParse("create:blaze_burner"), 2);
            if (adapter.canInstallAddon(node, superheated)) {
                adapter.onAddonInstalled(node, superheated);
                triggerUpdate(parent);
                return true;
            }
        }
        bx += 56;
        if (isInside(mx, my, bx, y + 5, 26, 16) && !node.getAddons().isEmpty()) {
            MachineAddon removed = node.getAddons().remove(node.getAddons().size() - 1);
            adapter.onAddonRemoved(node, removed);
            triggerUpdate(parent);
            return true;
        }
        bx += 30;
        if (isInside(mx, my, bx, y + 5, 26, 16) && !node.getAddons().isEmpty()) {
            node.getAddons().clear();
            CreateProperties.setBoilerHeat(node, 0);
            triggerUpdate(parent);
            return true;
        }
        return false;
    }

    private static boolean isInside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void triggerUpdate(BoardScreen parent) {
        if (parent != null) {
            parent.markSummaryDirty();
        }
        dialog.invalidateFilteredCatalog();
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.2F)
        );
    }
}
