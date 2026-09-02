package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.NodeWorkstationResolver;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.widget.BoardToast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * Modal dialog for inspecting and switching available machine workstations / multiblock controllers
 * for a RecipeNode. Automatically applies machine presets upon selection.
 */
public class MachineSelectorDialog {

    public record MachineEntry(
            ResourceLocation id,
            ItemStack itemStack,
            String displayName,
            boolean isMultiblock,
            boolean isCoil,
            boolean isSteam,
            boolean supportsParallelHatch,
            boolean supportsBatchMode,
            boolean supportsThroughputBoosting,
            boolean supportsBulkProcessing,
            boolean supportsLaserHatch,
            boolean supportsRotor,
            int helixCapacity,
            int defaultParallel,
            GTVoltageTier tier,
            boolean isCurrent
    ) {}

    private final BoardScreen parent;
    private RecipeNode node;
    private boolean visible = false;

    private String searchQuery = "";
    private EditBox searchBox;

    private final List<MachineEntry> allEntries = new ArrayList<>();
    private final List<MachineEntry> filteredEntries = new ArrayList<>();

    private double scrollY = 0.0;
    private double maxScrollY = 0.0;
    private MachineEntry hoveredEntry = null;

    private static final int DIALOG_WIDTH = 540;
    private static final int DIALOG_HEIGHT = 280;
    private static final int ROW_HEIGHT = 24;

    private record MachineBadge(String text, int fgColor, int bgColor, int borderColor) {}

    public MachineSelectorDialog(BoardScreen parent) {
        this.parent = parent;
    }

    public boolean isVisible() {
        return visible;
    }

    public void open(RecipeNode node) {
        if (node == null) return;
        this.node = node;
        this.visible = true;
        this.searchQuery = "";
        this.scrollY = 0.0;

        int x = (parent.width - DIALOG_WIDTH) / 2;
        int y = (parent.height - DIALOG_HEIGHT) / 2;

        this.searchBox = new EditBox(
                Minecraft.getInstance().font,
                x + DIALOG_WIDTH - 170,
                y + 8,
                140,
                14,
                Component.literal("Search...")
        );
        this.searchBox.setMaxLength(30);
        this.searchBox.setValue("");
        this.searchBox.setResponder(text -> {
            this.searchQuery = text.trim().toLowerCase(Locale.ROOT);
            this.scrollY = 0.0;
            updateFilteredList();
        });

        populateEntries();
        updateFilteredList();
    }

    public void close() {
        this.visible = false;
        if (searchBox != null) {
            searchBox.setFocused(false);
        }
    }

    private void populateEntries() {
        allEntries.clear();
        if (node == null) return;

        Set<ResourceLocation> wsSet = new LinkedHashSet<>();
        List<ResourceLocation> mbList = NodeWorkstationResolver.getMultiblockWorkstations(node);
        wsSet.addAll(mbList);

        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (ws != null) {
                wsSet.add(ws);
            }
        }
        if (node.getMachineIcon() != null) {
            wsSet.add(node.getMachineIcon());
        }

        ResourceLocation currentIcon = node.getMachineIcon();
        boolean isLiveRegistry = !ForgeRegistries.ITEMS.isEmpty();

        for (ResourceLocation ws : wsSet) {
            if (ws == null) continue;
            var item = ForgeRegistries.ITEMS.getValue(ws);
            ItemStack stack = (item != null && item != Items.AIR) ? new ItemStack(item) : ItemStack.EMPTY;
            if (stack.isEmpty()) {
                var block = ForgeRegistries.BLOCKS.getValue(ws);
                if (block != null && block.asItem() != Items.AIR) {
                    stack = new ItemStack(block.asItem());
                }
            }

            // In live game, if this ID has no registered Item or Block, ignore it (filters out unmapped mock placeholders)
            if (isLiveRegistry && stack.isEmpty()) {
                continue;
            }

            String name = !stack.isEmpty() ? stack.getHoverName().getString() : formatId(ws);

            boolean isMb = MultiblockDetector.isMultiblock(ws);
            boolean isCoil = MultiblockDetector.isCoilMultiblock(ws);
            boolean isSteam = MultiblockDetector.isSteamMultiblock(ws);
            boolean supportsParHatch = MultiblockDetector.supportsParallelHatch(ws, null, null);
            boolean supportsBatch = MultiblockDetector.supportsBatchMode(ws, null);
            boolean supportsThroughput = MultiblockDetector.supportsThroughputBoosting(ws);
            boolean supportsBulk = MultiblockDetector.supportsBulkProcessing(ws);
            boolean supportsLaser = MultiblockDetector.supportsLaserHatch(ws, null);
            boolean supportsRotor = MultiblockDetector.supportsTurbineRotor(ws, null);
            int helixCap = MultiblockDetector.getMaxHelixCount(ws);
            int defPar = MultiblockDetector.getDefaultParallel(ws);
            GTVoltageTier tier = extractTier(ws);
            boolean isCur = ws.equals(currentIcon);

            allEntries.add(new MachineEntry(
                    ws,
                    stack,
                    name,
                    isMb,
                    isCoil,
                    isSteam,
                    supportsParHatch,
                    supportsBatch,
                    supportsThroughput,
                    supportsBulk,
                    supportsLaser,
                    supportsRotor,
                    helixCap,
                    defPar,
                    tier,
                    isCur
            ));
        }

        // Fallback for mock test environments where ForgeRegistries.ITEMS is empty
        if (allEntries.isEmpty() && !wsSet.isEmpty()) {
            for (ResourceLocation ws : wsSet) {
                if (ws == null) continue;
                allEntries.add(new MachineEntry(
                        ws,
                        ItemStack.EMPTY,
                        formatId(ws),
                        MultiblockDetector.isMultiblock(ws),
                        MultiblockDetector.isCoilMultiblock(ws),
                        MultiblockDetector.isSteamMultiblock(ws),
                        MultiblockDetector.supportsParallelHatch(ws, null, null),
                        MultiblockDetector.supportsBatchMode(ws, null),
                        MultiblockDetector.supportsThroughputBoosting(ws),
                        MultiblockDetector.supportsBulkProcessing(ws),
                        MultiblockDetector.supportsLaserHatch(ws, null),
                        MultiblockDetector.supportsTurbineRotor(ws, null),
                        MultiblockDetector.getMaxHelixCount(ws),
                        MultiblockDetector.getDefaultParallel(ws),
                        extractTier(ws),
                        ws.equals(currentIcon)
                ));
            }
        }

        // Sort: Multiblocks first, then Steam, then Singleblocks sorted by Tier (ULV -> OpV)
        allEntries.sort((a, b) -> {
            if (a.isMultiblock() != b.isMultiblock()) {
                return a.isMultiblock() ? -1 : 1;
            }
            if (a.isSteam() != b.isSteam()) {
                return a.isSteam() ? -1 : 1;
            }
            if (a.tier() != null && b.tier() != null) {
                return Integer.compare(a.tier().ordinal(), b.tier().ordinal());
            }
            return a.displayName().compareToIgnoreCase(b.displayName());
        });
    }

    private void updateFilteredList() {
        filteredEntries.clear();
        for (MachineEntry entry : allEntries) {
            if (searchQuery.isEmpty() ||
                    entry.displayName().toLowerCase(Locale.ROOT).contains(searchQuery) ||
                    entry.id().getPath().toLowerCase(Locale.ROOT).contains(searchQuery)) {
                filteredEntries.add(entry);
            }
        }
    }

    private String formatId(ResourceLocation id) {
        String path = id.getPath();
        StringBuilder sb = new StringBuilder();
        for (String part : path.split("_")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private GTVoltageTier extractTier(ResourceLocation ws) {
        if (ws == null) return null;
        String path = ws.getPath().toLowerCase(Locale.ROOT);
        for (GTVoltageTier tier : GTVoltageTier.values()) {
            String name = tier.name().toLowerCase(Locale.ROOT);
            if (path.startsWith(name + "_") || path.contains("_" + name + "_") || path.endsWith("_" + name)) {
                return tier;
            }
        }
        return null;
    }

    public void render(GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (!visible || node == null) return;

        Font font = Minecraft.getInstance().font;
        int x = (screenWidth - DIALOG_WIDTH) / 2;
        int y = (screenHeight - DIALOG_HEIGHT) / 2;

        // Dim background
        graphics.fill(0, 0, screenWidth, screenHeight, 0x77000000);

        // Dialog base frame
        graphics.fill(x, y, x + DIALOG_WIDTH, y + DIALOG_HEIGHT, 0xF0121722);
        graphics.renderOutline(x, y, DIALOG_WIDTH, DIALOG_HEIGHT, 0xFF35425E);
        graphics.renderOutline(x + 1, y + 1, DIALOG_WIDTH - 2, DIALOG_HEIGHT - 2, 0xFF1B2332);

        // Title Header
        String title = "§b🏛 " + Component.translatable("gui.gtcalcboard.machine_selector.title").getString();
        graphics.drawString(font, title, x + 12, y + 10, 0xFFFFFFFF, false);

        // Subtitle (Current Node)
        String nodeSub = "§7" + Component.translatable("gui.gtcalcboard.machine_selector.for_node", node.getName()).getString();
        graphics.drawString(font, nodeSub, x + 12, y + 22, 0xFF8FA0BA, false);

        // Close Button [X]
        int closeBtnX = x + DIALOG_WIDTH - 20;
        int closeBtnY = y + 8;
        boolean closeHover = mouseX >= closeBtnX && mouseX <= closeBtnX + 12 && mouseY >= closeBtnY && mouseY <= closeBtnY + 12;
        graphics.fill(closeBtnX, closeBtnY, closeBtnX + 12, closeBtnY + 12, closeHover ? 0xFF9E2A2B : 0xFF2A344A);
        graphics.renderOutline(closeBtnX, closeBtnY, 12, 12, closeHover ? 0xFFFF5555 : 0xFF425170);
        graphics.drawCenteredString(font, "✕", closeBtnX + 6, closeBtnY + 2, closeHover ? 0xFFFFFFFF : 0xFF8FA0BA);

        // Search Box
        int searchX = x + DIALOG_WIDTH - 170;
        int searchY = y + 24;
        searchBox.setX(searchX);
        searchBox.setY(searchY);
        searchBox.render(graphics, mouseX, mouseY, 0);

        // List Area
        int listX = x + 10;
        int listY = y + 44;
        int listW = DIALOG_WIDTH - 20;
        int listH = DIALOG_HEIGHT - 54;

        graphics.fill(listX, listY, listX + listW, listY + listH, 0xFF0D121B);
        graphics.renderOutline(listX, listY, listW, listH, 0xFF263044);

        int totalItemsHeight = filteredEntries.size() * ROW_HEIGHT;
        this.maxScrollY = Math.max(0, totalItemsHeight - listH);
        this.scrollY = Math.max(0, Math.min(scrollY, maxScrollY));

        hoveredEntry = null;

        graphics.enableScissor(listX, listY + 1, listX + listW, listY + listH - 1);
        graphics.pose().pushPose();
        graphics.pose().translate(0, -scrollY, 0);

        for (int i = 0; i < filteredEntries.size(); i++) {
            MachineEntry entry = filteredEntries.get(i);
            int rowY = listY + 2 + i * ROW_HEIGHT;

            double vMouseY = mouseY + scrollY;
            boolean hov = mouseX >= listX + 2 && mouseX <= listX + listW - 8 && vMouseY >= rowY && vMouseY < rowY + ROW_HEIGHT - 2 && mouseY >= listY && mouseY <= listY + listH;
            if (hov) {
                hoveredEntry = entry;
            }

            boolean isGlowing = com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().isMachineSelectorRowGlowing(entry.id());
            int bg = isGlowing ? com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getGlowBgColor(entry.isCurrent() ? 0xFF1C3A2A : 0xFF26334D) : (entry.isCurrent() ? 0xFF1C3A2A : (hov ? 0xFF26334D : ((i % 2 == 0) ? 0xFF131A26 : 0xFF101622)));
            int border = isGlowing ? com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getGlowBorderColor(0xFFFFD700) : (entry.isCurrent() ? 0xFF45B074 : (hov ? 0xFF589CFF : 0x00000000));

            graphics.fill(listX + 2, rowY, listX + listW - 8, rowY + ROW_HEIGHT - 2, bg);
            if (border != 0) {
                graphics.renderOutline(listX + 2, rowY, listW - 10, ROW_HEIGHT - 2, border);
                if (isGlowing) {
                    graphics.renderOutline(listX + 1, rowY - 1, listW - 8, ROW_HEIGHT, border & 0x77FFFFFF);
                }
            }

            // Render Item Icon
            if (!entry.itemStack().isEmpty()) {
                graphics.renderItem(entry.itemStack(), listX + 6, rowY + 3);
            }

            // Prepare Badges
            List<MachineBadge> badges = new ArrayList<>();

            // 1. Multiblock / Steam / Tier
            if (entry.isMultiblock()) {
                badges.add(new MachineBadge("🏛 " + Component.translatable("gui.gtcalcboard.multiblock_badge").getString(), 0xFFDDAAFF, 0xFF2D1B44, 0xFF6C3E9A));
            } else if (entry.isSteam()) {
                badges.add(new MachineBadge("♨ " + Component.translatable("gui.gtcalcboard.steam_badge").getString(), 0xFFFFBB66, 0xFF3A2814, 0xFF7A4E1B));
            } else if (entry.tier() != null) {
                badges.add(new MachineBadge(entry.tier().getName(), 0xFF88CCFF, 0xFF1B2836, 0xFF354F6B));
            }

            // 2. Coil
            if (entry.isCoil()) {
                badges.add(new MachineBadge("♨ Coil", 0xFFFF9944, 0xFF3D2314, 0xFF8A481B));
            }

            // 3. Throughput Boosting
            if (entry.supportsThroughputBoosting()) {
                badges.add(new MachineBadge("⚡ " + Component.translatable("gui.gtcalcboard.throughput_boosting_badge").getString(), 0xFF88FFDD, 0xFF142E28, 0xFF2A7865));
            }

            // 4. Bulk Processing
            if (entry.supportsBulkProcessing()) {
                badges.add(new MachineBadge("⚡ " + Component.translatable("gui.gtcalcboard.bulk_processing_badge").getString(), 0xFFFFCC88, 0xFF352010, 0xFF855025));
            }

            // 5. Batch Mode
            if (entry.supportsBatchMode()) {
                badges.add(new MachineBadge("📦 " + Component.translatable("gui.gtcalcboard.batch_mode_badge").getString(), 0xFFEEAAFF, 0xFF2A1535, 0xFF6A3585));
            }

            // 6. Threading Helix
            if (entry.helixCapacity() > 0) {
                badges.add(new MachineBadge("🧬 " + Component.translatable("gui.gtcalcboard.helix_badge", entry.helixCapacity()).getString(), 0xFF66EEFF, 0xFF112D33, 0xFF227885));
            }

            // 7. Turbine Rotor
            if (entry.supportsRotor()) {
                badges.add(new MachineBadge("⚙ " + Component.translatable("gui.gtcalcboard.rotor_badge").getString(), 0xFFFFD255, 0xFF382A0F, 0xFF9A7B1C));
            }

            // 8. Laser Hatch
            if (entry.supportsLaserHatch()) {
                badges.add(new MachineBadge("⚡ " + Component.translatable("gui.gtcalcboard.laser_hatch_badge").getString(), 0xFFE8B3FF, 0xFF2B163E, 0xFF7D3C98));
            }

            // 9. Parallel Hatch
            if (entry.supportsParallelHatch()) {
                badges.add(new MachineBadge("⚡ " + Component.translatable("gui.gtcalcboard.parallel_hatch_badge").getString(), 0xFF55FFAA, 0xFF0E2E20, 0xFF2A855E));
            }

            // 10. Default Parallel
            if (entry.defaultParallel() > 1) {
                badges.add(new MachineBadge("⚡ " + entry.defaultParallel() + "x Par", 0xFF7BB3FF, 0xFF1C2D44, 0xFF3E659A));
            }

            // Calculate Badges Total Width
            int totalBadgeWidth = 0;
            for (MachineBadge b : badges) {
                totalBadgeWidth += font.width(b.text()) + 6 + 3;
            }

            // Dynamically calculate available space for name
            int maxNameWidth = Math.max(80, (listW - 14) - 26 - totalBadgeWidth - 6);
            String nameText = (entry.isCurrent() ? "§a✔ " : "") + entry.displayName();
            int textColor = entry.isCurrent() ? 0xFF55FF88 : (hov ? 0xFFFFFFFF : 0xFFCFD6E4);
            graphics.drawString(font, font.plainSubstrByWidth(nameText, maxNameWidth), listX + 26, rowY + 7, textColor, false);

            // Render Badges right-to-left
            int badgeRight = listX + listW - 14;
            for (int bi = badges.size() - 1; bi >= 0; bi--) {
                MachineBadge b = badges.get(bi);
                int bW = font.width(b.text()) + 6;
                badgeRight -= bW + 3;
                graphics.fill(badgeRight, rowY + 4, badgeRight + bW, rowY + 18, b.bgColor());
                graphics.renderOutline(badgeRight, rowY + 4, bW, 14, b.borderColor());
                graphics.drawString(font, b.text(), badgeRight + 3, rowY + 7, b.fgColor(), false);
            }
        }

        graphics.pose().popPose();
        graphics.disableScissor();

        // Scrollbar
        if (maxScrollY > 0) {
            int scrollbarX = listX + listW - 6;
            int scrollbarH = listH - 2;
            graphics.fill(scrollbarX, listY + 1, scrollbarX + 4, listY + 1 + scrollbarH, 0xFF18202D);
            int thumbH = Math.max(16, (int) ((double) listH / totalItemsHeight * scrollbarH));
            int thumbY = listY + 1 + (int) (scrollY / maxScrollY * (scrollbarH - thumbH));
            graphics.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbH, 0xFF4F6385);
        }

        // Tooltip
        if (hoveredEntry != null && mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal((hoveredEntry.isMultiblock() ? "§e🏛 " : "§b⚡ ") + hoveredEntry.displayName()));
            tooltip.add(Component.literal("§8ID: " + hoveredEntry.id()));

            if (hoveredEntry.defaultParallel() > 1) {
                tooltip.add(Component.literal("§7⚡ " + Component.translatable("gui.gtcalcboard.tooltip.default_parallel", hoveredEntry.defaultParallel()).getString()));
            }
            if (hoveredEntry.supportsParallelHatch()) {
                tooltip.add(Component.literal("§a⚡ " + Component.translatable("gui.gtcalcboard.tooltip.supports_parallel_hatch").getString()));
            }
            if (hoveredEntry.supportsLaserHatch()) {
                tooltip.add(Component.literal("§d⚡ " + Component.translatable("gui.gtcalcboard.tooltip.supports_laser_hatch").getString()));
            }
            if (hoveredEntry.supportsRotor()) {
                tooltip.add(Component.literal("§6⚙ " + Component.translatable("gui.gtcalcboard.tooltip.supports_turbine_rotor").getString()));
            }
            if (hoveredEntry.helixCapacity() > 0) {
                tooltip.add(Component.literal("§b🧬 " + Component.translatable("gui.gtcalcboard.tooltip.supports_threading_helix", hoveredEntry.helixCapacity()).getString()));
            }
            if (hoveredEntry.supportsBatchMode()) {
                tooltip.add(Component.literal("§d📦 " + Component.translatable("gui.gtcalcboard.tooltip.supports_batch_mode").getString()));
            }
            if (hoveredEntry.supportsThroughputBoosting()) {
                tooltip.add(Component.literal("§a⚡ " + Component.translatable("gui.gtcalcboard.tooltip.supports_throughput_boosting").getString()));
            }
            if (hoveredEntry.supportsBulkProcessing()) {
                tooltip.add(Component.literal("§6⚡ " + Component.translatable("gui.gtcalcboard.tooltip.supports_bulk_processing").getString()));
            }
            if (hoveredEntry.isCoil()) {
                tooltip.add(Component.literal("§6♨ " + Component.translatable("gui.gtcalcboard.tooltip.requires_heating_coil").getString()));
            }
            if (hoveredEntry.isMultiblock()) {
                tooltip.add(Component.literal("§d🏛 " + Component.translatable("gui.gtcalcboard.tooltip.multiblock_structure").getString()));
            }

            if (hoveredEntry.isCurrent()) {
                tooltip.add(Component.literal("§a✔ " + Component.translatable("gui.gtcalcboard.tooltip.currently_selected_machine").getString()));
            } else {
                tooltip.add(Component.literal("§e[Click]: §f" + Component.translatable("gui.gtcalcboard.tooltip.apply_machine_preset").getString()));
            }

            graphics.renderTooltip(font, tooltip, Optional.empty(), mouseX, mouseY);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        int x = (parent.width - DIALOG_WIDTH) / 2;
        int y = (parent.height - DIALOG_HEIGHT) / 2;

        if (searchBox != null && searchBox.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Close Button
        int closeBtnX = x + DIALOG_WIDTH - 20;
        int closeBtnY = y + 8;
        if (mouseX >= closeBtnX && mouseX <= closeBtnX + 12 && mouseY >= closeBtnY && mouseY <= closeBtnY + 12) {
            close();
            Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.9F)
            );
            return true;
        }

        // Item row click
        int listX = x + 10;
        int listY = y + 44;
        int listW = DIALOG_WIDTH - 20;
        int listH = DIALOG_HEIGHT - 54;

        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            double vMouseY = mouseY - listY - 2 + scrollY;
            int clickedIdx = (int) (vMouseY / ROW_HEIGHT);
            if (clickedIdx >= 0 && clickedIdx < filteredEntries.size()) {
                MachineEntry selected = filteredEntries.get(clickedIdx);
                parent.switchMachineWorkstation(node, selected.id());
                Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1F)
                );
                close();
                return true;
            }
        }

        // Consume click inside dialog
        return mouseX >= x && mouseX <= x + DIALOG_WIDTH && mouseY >= y && mouseY <= y + DIALOG_HEIGHT;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible) return false;
        int x = (parent.width - DIALOG_WIDTH) / 2;
        int y = (parent.height - DIALOG_HEIGHT) / 2;
        if (mouseX >= x && mouseX <= x + DIALOG_WIDTH && mouseY >= y && mouseY <= y + DIALOG_HEIGHT) {
            scrollY -= delta * ROW_HEIGHT;
            scrollY = Math.max(0, Math.min(scrollY, maxScrollY));
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;

        if (searchBox != null && searchBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                close();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER && !filteredEntries.isEmpty()) {
                MachineEntry selected = filteredEntries.get(0);
                parent.switchMachineWorkstation(node, selected.id());
                Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1F)
                );
                close();
                return true;
            }
            return searchBox.keyPressed(keyCode, scanCode, modifiers);
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible) return false;
        if (searchBox != null && searchBox.isFocused()) {
            return searchBox.charTyped(codePoint, modifiers);
        }
        return false;
    }
}
