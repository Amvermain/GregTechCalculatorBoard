package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.RecipeNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Native GTCEu-powered Turbine Rotor item selection dialog.
 * Direct integration with GTCEuAPI.materialManager, PropertyKey.ROTOR, and TurbineRotorBehaviour.
 */
public class TurbineRotorDialog {
    private final BoardScreen parent;
    private RecipeNode targetNode;
    private boolean visible = false;

    public record RotorItemEntry(ItemStack itemStack, String displayName, int efficiencyPercent, int powerPercent) {}

    private final List<RotorItemEntry> allDiscoveredRotors = new ArrayList<>();
    private final List<RotorItemEntry> filteredRotors = new ArrayList<>();
    private boolean rotorsIndexed = false;

    private final EditBox searchBox;
    private final EditBox customEffBox;
    private final EditBox customPowerBox;
    private int scrollOffset = 0;

    private static final int DIALOG_WIDTH = 340;
    private static final int DIALOG_HEIGHT = 270;
    private static final int SLOT_SIZE = 38;
    private static final int COLS = 7;

    public TurbineRotorDialog(BoardScreen parent) {
        this.parent = parent;
        Font font = Minecraft.getInstance().font;

        this.searchBox = new EditBox(font, 0, 0, DIALOG_WIDTH - 24, 16, Component.literal("Search rotor..."));
        this.searchBox.setResponder(this::onSearchChanged);
        this.searchBox.setHint(Component.literal("§8").append(Component.translatable("gui.gtcalcboard.rotor.search_hint")));

        this.customEffBox = new EditBox(font, 0, 0, 36, 14, Component.literal("100"));
        this.customEffBox.setMaxLength(4);

        this.customPowerBox = new EditBox(font, 0, 0, 36, 14, Component.literal("100"));
        this.customPowerBox.setMaxLength(4);
    }

    public boolean isVisible() {
        return visible;
    }

    public void open(RecipeNode node) {
        this.targetNode = node;
        this.visible = true;
        this.scrollOffset = 0;
        this.customEffBox.setValue(String.valueOf(node.getRotorEfficiency()));
        this.customPowerBox.setValue(String.valueOf(node.getRotorPower()));
        this.searchBox.setValue("");
        this.searchBox.setFocused(true);

        if (!rotorsIndexed || allDiscoveredRotors.isEmpty()) {
            indexAllRotors();
            rotorsIndexed = true;
        }
        updateFilter("");
    }

    public void close() {
        this.visible = false;
        this.targetNode = null;
    }

    private void onSearchChanged(String query) {
        this.scrollOffset = 0;
        updateFilter(query);
    }

    private void updateFilter(String query) {
        filteredRotors.clear();
        String q = query.toLowerCase().trim();
        for (RotorItemEntry entry : allDiscoveredRotors) {
            if (q.isEmpty() || entry.displayName.toLowerCase().contains(q) || String.valueOf(entry.efficiencyPercent).contains(q) || String.valueOf(entry.powerPercent).contains(q)) {
                filteredRotors.add(entry);
            }
        }
    }

    private void indexAllRotors() {
        if (!com.gtceu.calcboard.api.ModCompatHelper.isGTLoaded()) {
            allDiscoveredRotors.clear();
            return;
        }

        Map<String, RotorItemEntry> collected = new LinkedHashMap<>();

        try {
            Item rotorItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("gtceu", "turbine_rotor"));
            if (rotorItem == null) {
                rotorItem = BuiltInRegistries.ITEM.get(new ResourceLocation("gtceu", "turbine_rotor"));
            }

            Class<?> gtceuApiCls = Class.forName("com.gregtechceu.gtceu.api.GTCEuAPI");
            Class<?> propertyKeyCls = Class.forName("com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey");
            Class<?> materialCls = Class.forName("com.gregtechceu.gtceu.api.data.chemical.material.Material");
            Class<?> behaviourCls = Class.forName("com.gregtechceu.gtceu.common.item.TurbineRotorBehaviour");

            Object materialManager = gtceuApiCls.getField("materialManager").get(null);
            Object rotorKey = propertyKeyCls.getField("ROTOR").get(null);

            Method getRegisteredMaterialsMethod = materialManager.getClass().getMethod("getRegisteredMaterials");
            Method hasPropertyMethod = materialCls.getMethod("hasProperty", propertyKeyCls);
            Method getPropertyMethod = materialCls.getMethod("getProperty", propertyKeyCls);

            Method getBehaviourMethod = behaviourCls.getMethod("getBehaviour", ItemStack.class);
            Method setPartMaterialMethod = behaviourCls.getMethod("setPartMaterial", ItemStack.class, materialCls);

            Collection<?> materials = (Collection<?>) getRegisteredMaterialsMethod.invoke(materialManager);

            String rotorSuffix = Component.translatable("item.gtceu.turbine_rotor").getString();
            if (rotorSuffix.isEmpty() || rotorSuffix.contains("item.")) {
                rotorSuffix = "Turbine Rotor";
            }

            if (materials != null && rotorItem != null) {
                for (Object mat : materials) {
                    try {
                        Boolean hasRotor = (Boolean) hasPropertyMethod.invoke(mat, rotorKey);
                        if (hasRotor != null && hasRotor) {
                            Object prop = getPropertyMethod.invoke(mat, rotorKey);
                            if (prop != null) {
                                Method getEffMethod = prop.getClass().getMethod("getEfficiency");
                                Method getPowerMethod = prop.getClass().getMethod("getPower");

                                int eff = (int) getEffMethod.invoke(prop);
                                int power = (int) getPowerMethod.invoke(prop);

                                Method getNameMethod = mat.getClass().getMethod("getName");
                                String matName = (String) getNameMethod.invoke(mat);

                                String displayName = formatName(matName) + " " + rotorSuffix;
                                try {
                                    Method getLocalizedNameMethod = mat.getClass().getMethod("getLocalizedName");
                                    Object comp = getLocalizedNameMethod.invoke(mat);
                                    if (comp instanceof Component c) {
                                        displayName = c.getString() + " " + rotorSuffix;
                                    }
                                } catch (Throwable ignored) {}

                                ItemStack stack = new ItemStack(rotorItem);
                                Object behaviour = getBehaviourMethod.invoke(null, stack);
                                if (behaviour != null) {
                                    setPartMaterialMethod.invoke(behaviour, stack, mat);
                                }

                                String key = matName + "_" + eff + "_" + power;
                                collected.put(key, new RotorItemEntry(stack, displayName, eff, power));
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {
            System.err.println("[GTCalcBoard] GTCEuAPI.materialManager Rotor Scan error: " + t.getMessage());
        }

        List<RotorItemEntry> list = new ArrayList<>(collected.values());
        list.sort(Comparator.comparingInt(RotorItemEntry::efficiencyPercent));

        allDiscoveredRotors.clear();
        allDiscoveredRotors.addAll(list);
    }

    private String formatName(String name) {
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) {
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, int screenWidth, int screenHeight) {
        if (!visible || targetNode == null) return;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 600);

        Font font = Minecraft.getInstance().font;

        // Modal backdrop overlay
        graphics.fill(0, 0, screenWidth, screenHeight, 0xCC000000);

        int dialogW = Math.min(340, screenWidth - 24);
        int dialogH = Math.min(270, screenHeight - 24);
        int dialogX = (screenWidth - dialogW) / 2;
        int dialogY = (screenHeight - dialogH) / 2;

        // Modal Box (Fully Opaque)
        graphics.fill(dialogX, dialogY, dialogX + dialogW, dialogY + dialogH, 0xFF14171E);
        graphics.renderOutline(dialogX, dialogY, dialogW, dialogH, 0xFF3D4455);

        // Header
        graphics.fill(dialogX + 1, dialogY + 1, dialogX + dialogW - 1, dialogY + 22, 0xFF1B202A);
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.rotor.title"), dialogX + 8, dialogY + 7, 0xFF55FFFF, false);

        String countBadge = "§a" + Component.translatable("gui.gtcalcboard.rotor.count", filteredRotors.size()).getString();
        graphics.drawString(font, countBadge, dialogX + 155, dialogY + 7, 0xFF55FF55, false);

        // Close button [X]
        int closeX = dialogX + dialogW - 18;
        int closeY = dialogY + 4;
        boolean closeHover = mouseX >= closeX && mouseX <= closeX + 14 && mouseY >= closeY && mouseY <= closeY + 14;
        graphics.fill(closeX, closeY, closeX + 14, closeY + 14, closeHover ? 0xFFAA2222 : 0xFF2B3242);
        graphics.drawCenteredString(font, "✕", closeX + 7, closeY + 3, 0xFFFFFFFF);

        // Custom % Input row (Eff% and Power%)
        int customY = dialogY + 28;
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.rotor.eff"), dialogX + 8, customY + 3, 0xFFAAAAAA, false);
        customEffBox.setX(dialogX + 42);
        customEffBox.setY(customY);
        customEffBox.render(graphics, mouseX, mouseY, partialTicks);

        graphics.drawString(font, Component.translatable("gui.gtcalcboard.rotor.power"), dialogX + 84, customY + 3, 0xFFAAAAAA, false);
        customPowerBox.setX(dialogX + 120);
        customPowerBox.setY(customY);
        customPowerBox.render(graphics, mouseX, mouseY, partialTicks);

        // [Apply] button
        int applyX = dialogX + 162;
        int applyW = 44;
        int applyH = 14;
        boolean applyHover = mouseX >= applyX && mouseX <= applyX + applyW && mouseY >= customY && mouseY <= customY + applyH;
        graphics.fill(applyX, customY, applyX + applyW, customY + applyH, applyHover ? 0xFF336633 : 0xFF204420);
        graphics.renderOutline(applyX, customY, applyW, applyH, 0xFF55AA55);
        graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.rotor.apply").getString(), applyX + applyW / 2, customY + 3, 0xFF55FF55);

        // Search Bar
        int searchY = dialogY + 48;
        searchBox.setX(dialogX + 8);
        searchBox.setY(searchY);
        searchBox.setWidth(dialogW - 16);
        searchBox.render(graphics, mouseX, mouseY, partialTicks);

        // Grid of Rotor Items
        int gridX = dialogX + 10;
        int gridY = searchY + 22;
        int gridW = dialogW - 20;
        int gridH = dialogH - (gridY - dialogY) - 10;

        int totalRows = (int) Math.ceil((double) filteredRotors.size() / COLS);
        int maxScroll = Math.max(0, totalRows * SLOT_SIZE - gridH);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        graphics.enableScissor(gridX, gridY, gridX + gridW, gridY + gridH);

        RotorItemEntry hoveredEntry = null;

        for (int i = 0; i < filteredRotors.size(); i++) {
            RotorItemEntry entry = filteredRotors.get(i);
            int col = i % COLS;
            int row = i / COLS;

            int slotX = gridX + col * (SLOT_SIZE + 6);
            int slotY = gridY + row * SLOT_SIZE - scrollOffset;

            if (slotY + SLOT_SIZE < gridY || slotY > gridY + gridH) continue;

            boolean isHovered = mouseX >= slotX && mouseX <= slotX + SLOT_SIZE && mouseY >= slotY && mouseY <= slotY + SLOT_SIZE;
            boolean isSelected = targetNode.getRotorEfficiency() == entry.efficiencyPercent && targetNode.getRotorPower() == entry.powerPercent;

            int slotBg = isSelected ? 0xFF224422 : (isHovered ? 0xFF2D3546 : 0xFF181C26);
            int slotBorder = isSelected ? 0xFF55FF55 : (isHovered ? 0xFF55FFFF : 0xFF3D4455);

            graphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, slotBg);
            graphics.renderOutline(slotX, slotY, SLOT_SIZE, SLOT_SIZE, slotBorder);

            // Render Item Icon
            graphics.renderItem(entry.itemStack, slotX + 11, slotY + 3);

            // Small Efficiency % text at bottom
            String effText = entry.efficiencyPercent + "%";
            int effColor = entry.efficiencyPercent >= 240 ? 0xFFFFAA00 : 0xFFCCCCCC;
            graphics.drawCenteredString(font, effText, slotX + SLOT_SIZE / 2, slotY + 24, effColor);

            if (isHovered) {
                hoveredEntry = entry;
            }
        }

        graphics.disableScissor();

        // Render Tooltip on top of dialog
        if (hoveredEntry != null) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("§f" + hoveredEntry.displayName));
            tooltip.add(Component.literal("§7").append(Component.translatable("gui.gtcalcboard.rotor.tooltip_eff", "§6" + hoveredEntry.efficiencyPercent)));
            tooltip.add(Component.literal("§7").append(Component.translatable("gui.gtcalcboard.rotor.tooltip_power", "§b" + hoveredEntry.powerPercent)));
            tooltip.add(Component.literal("§8").append(Component.translatable("gui.gtcalcboard.rotor.tooltip_equip")));
            graphics.renderTooltip(font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
        }

        graphics.pose().popPose();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
        if (!visible || targetNode == null) return false;

        int dialogW = Math.min(340, screenWidth - 24);
        int dialogH = Math.min(270, screenHeight - 24);
        int dialogX = (screenWidth - dialogW) / 2;
        int dialogY = (screenHeight - dialogH) / 2;

        if (mouseX < dialogX || mouseX > dialogX + dialogW || mouseY < dialogY || mouseY > dialogY + dialogH) {
            close();
            return true;
        }

        // Close button
        int closeX = dialogX + dialogW - 18;
        int closeY = dialogY + 4;
        if (mouseX >= closeX && mouseX <= closeX + 14 && mouseY >= closeY && mouseY <= closeY + 14) {
            close();
            return true;
        }

        // Apply button
        int customY = dialogY + 28;
        int applyX = dialogX + 162;
        int applyW = 44;
        int applyH = 14;
        if (mouseX >= applyX && mouseX <= applyX + applyW && mouseY >= customY && mouseY <= customY + applyH) {
            commitCustomValues();
            close();
            return true;
        }

        // Focus handling
        searchBox.setFocused(searchBox.isMouseOver(mouseX, mouseY));
        customEffBox.setFocused(customEffBox.isMouseOver(mouseX, mouseY));
        customPowerBox.setFocused(customPowerBox.isMouseOver(mouseX, mouseY));

        if (searchBox.mouseClicked(mouseX, mouseY, button)) return true;
        if (customEffBox.mouseClicked(mouseX, mouseY, button)) return true;
        if (customPowerBox.mouseClicked(mouseX, mouseY, button)) return true;

        // Grid item clicked
        int searchY = dialogY + 48;
        int gridX = dialogX + 10;
        int gridY = searchY + 22;
        int gridW = DIALOG_WIDTH - 20;
        int gridH = DIALOG_HEIGHT - (gridY - dialogY) - 10;

        if (mouseX >= gridX && mouseX <= gridX + gridW && mouseY >= gridY && mouseY <= gridY + gridH) {
            for (int i = 0; i < filteredRotors.size(); i++) {
                int col = i % COLS;
                int row = i / COLS;

                int slotX = gridX + col * (SLOT_SIZE + 6);
                int slotY = gridY + row * SLOT_SIZE - scrollOffset;

                if (mouseX >= slotX && mouseX <= slotX + SLOT_SIZE && mouseY >= slotY && mouseY <= slotY + SLOT_SIZE) {
                    RotorItemEntry entry = filteredRotors.get(i);
                    int oldEff = targetNode.getRotorEfficiency();
                    int oldPwr = targetNode.getRotorPower();
                    String oldMat = targetNode.getRotorName();

                    targetNode.setRotorEfficiency(entry.efficiencyPercent);
                    targetNode.setRotorPower(entry.powerPercent);
                    targetNode.setRotorName(entry.displayName);
                    targetNode.autoCalculateTurbineParallel();

                    List<com.gtceu.calcboard.api.history.BoardCommand> cmds = new ArrayList<>();
                    cmds.add(new com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand(targetNode.getId(), com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.Property.ROTOR_EFFICIENCY, oldEff, entry.efficiencyPercent));
                    cmds.add(new com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand(targetNode.getId(), com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.Property.ROTOR_POWER, oldPwr, entry.powerPercent));
                    cmds.add(new com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand(targetNode.getId(), com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.Property.ROTOR_NAME, oldMat, entry.displayName));
                    parent.recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.CompoundCommand(cmds, "Select Turbine Rotor (" + entry.displayName + ")"));

                    parent.markSummaryDirty();
                    parent.rebuildWidgets();
                    close();
                    return true;
                }
            }
        }

        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta, int screenWidth, int screenHeight) {
        if (!visible || targetNode == null) return false;
        scrollOffset -= (int) (delta * SLOT_SIZE);
        return true;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible || targetNode == null) return false;
        if (searchBox.isFocused() && searchBox.charTyped(codePoint, modifiers)) return true;
        if (customEffBox.isFocused() && customEffBox.charTyped(codePoint, modifiers)) return true;
        if (customPowerBox.isFocused() && customPowerBox.charTyped(codePoint, modifiers)) return true;
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible || targetNode == null) return false;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        if ((customEffBox.isFocused() || customPowerBox.isFocused()) && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            commitCustomValues();
            close();
            return true;
        }

        if (searchBox.isFocused() && searchBox.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (customEffBox.isFocused() && customEffBox.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (customPowerBox.isFocused() && customPowerBox.keyPressed(keyCode, scanCode, modifiers)) return true;

        return false;
    }

    private void commitCustomValues() {
        try {
            int eff = Integer.parseInt(customEffBox.getValue().trim());
            int pwr = Integer.parseInt(customPowerBox.getValue().trim());
            if (eff >= 10 && eff <= 1000 && pwr >= 10 && pwr <= 5000) {
                int oldEff = targetNode.getRotorEfficiency();
                int oldPwr = targetNode.getRotorPower();
                String oldMat = targetNode.getRotorName();
                String newName = "Custom (" + eff + "% / " + pwr + "%P)";

                targetNode.setRotorEfficiency(eff);
                targetNode.setRotorPower(pwr);
                targetNode.setRotorName(newName);
                targetNode.autoCalculateTurbineParallel();

                List<com.gtceu.calcboard.api.history.BoardCommand> cmds = new ArrayList<>();
                cmds.add(new com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand(targetNode.getId(), com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.Property.ROTOR_EFFICIENCY, oldEff, eff));
                cmds.add(new com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand(targetNode.getId(), com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.Property.ROTOR_POWER, oldPwr, pwr));
                cmds.add(new com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand(targetNode.getId(), com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.Property.ROTOR_NAME, oldMat, newName));
                parent.recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.CompoundCommand(cmds, "Custom Turbine Rotor"));

                parent.markSummaryDirty();
                parent.rebuildWidgets();
            }
        } catch (NumberFormatException ignored) {}
    }
}
