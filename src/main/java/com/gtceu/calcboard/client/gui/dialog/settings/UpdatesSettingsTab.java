package com.gtceu.calcboard.client.gui.dialog.settings;

import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.BoardSettingsDialog;
import com.gtceu.calcboard.client.storage.ClientPreferenceManager;
import com.gtceu.calcboard.client.update.ClientUpdateNotifier;
import com.gtceu.calcboard.config.CalcBoardClientConfig;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

import java.net.URI;

/**
 * Settings tab for viewing version status, toggling update checks, and opening download links.
 */
public class UpdatesSettingsTab extends AbstractSettingsTab {

    public UpdatesSettingsTab(BoardSettingsDialog dialog, BoardScreen parent) {
        super(dialog, parent);
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        graphics.drawString(font, "§a" + Component.translatable("gui.gtcalcboard.settings.updates_desc").getString(), x, y, 0xFFFFFFFF, false);

        ClientUpdateNotifier notifier = ClientUpdateNotifier.getInstance();
        renderUpdateStatusCard(graphics, font, x, y + 16, w - 8, mouseX, mouseY, notifier);

        int rowY = y + 86;
        int rowH = 20;

        drawCheckbox(graphics, font, x, rowY, w, rowH, mouseX, mouseY,
                Component.translatable("gui.gtcalcboard.settings.check_updates_auto").getString(),
                CalcBoardClientConfig.CHECK_FOR_UPDATES.get());
        rowY += rowH + 3;

        drawCheckbox(graphics, font, x, rowY, w, rowH, mouseX, mouseY,
                Component.translatable("gui.gtcalcboard.settings.show_update_badge").getString(),
                CalcBoardClientConfig.SHOW_UPDATE_BADGE.get());
        rowY += rowH + 3;

        drawCheckbox(graphics, font, x, rowY, w, rowH, mouseX, mouseY,
                Component.translatable("gui.gtcalcboard.settings.notify_update_chat").getString(),
                CalcBoardClientConfig.NOTIFY_UPDATE_IN_CHAT.get());
        rowY += rowH + 8;

        drawButton(graphics, font, Component.translatable("gui.gtcalcboard.settings.check_updates_now").getString(),
                x + 4, rowY, 130, 20, mouseX, mouseY, 0xFF58D3FF, 0xFF222834, 0xFF35445E);
    }

    private void renderUpdateStatusCard(GuiGraphics graphics, Font font, int cx, int cy, int cw, int mouseX, int mouseY, ClientUpdateNotifier notifier) {
        int cardH = 58;
        graphics.fill(cx, cy, cx + cw, cy + cardH, 0xEE161B26);
        graphics.renderOutline(cx, cy, cw, cardH, 0xFF2A364D);

        String currentVer = Component.translatable("gui.gtcalcboard.settings.current_version", notifier.getCurrentVersion()).getString();
        graphics.drawString(font, currentVer, cx + 8, cy + 7, 0xFFE2E8F0, false);

        if (notifier.isUpdateAvailable()) {
            String updateText = "§e★ " + Component.translatable("gui.gtcalcboard.settings.version_outdated", notifier.getLatestVersion()).getString();
            graphics.drawString(font, updateText, cx + 8, cy + 22, 0xFFFCD34D, false);

            int btnH = 18;
            int btnY = cy + 34;

            int btnW = 75;
            int btnX = cx + cw - btnW - 8;
            drawButton(graphics, font, Component.translatable("gui.gtcalcboard.settings.btn_download").getString(),
                    btnX, btnY, btnW, btnH, mouseX, mouseY, 0xFF34D399, 0xFF064E3B, 0xFF059669);

            boolean isDismissed = ClientPreferenceManager.getInstance().getDismissedUpdateVersion().equalsIgnoreCase(notifier.getLatestVersion());
            String dismissText = Component.translatable(isDismissed ? "gui.gtcalcboard.settings.btn_show_badge" : "gui.gtcalcboard.settings.btn_hide_badge").getString();
            int dismissW = 100;
            int dismissX = btnX - dismissW - 6;
            drawButton(graphics, font, dismissText, dismissX, btnY, dismissW, btnH, mouseX, mouseY, 0xFFCBD5E1, 0xFF1E293B, 0xFF475569);
            return;
        }

        String statusText = notifier.getStatus() == ClientUpdateNotifier.UpdateStatus.UP_TO_DATE
                ? "§a✔ " + Component.translatable("gui.gtcalcboard.settings.version_up_to_date").getString()
                : "§7" + Component.translatable("gui.gtcalcboard.settings.version_unknown").getString();
        graphics.drawString(font, statusText, cx + 8, cy + 26, 0xFF94A3B8, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int x, int y, int w, int h, int button) {
        ClientUpdateNotifier notifier = ClientUpdateNotifier.getInstance();
        int cardY = y + 16;
        int cw = w - 8;

        if (notifier.isUpdateAvailable()) {
            int btnH = 18;
            int btnY = cardY + 34;

            int btnW = 75;
            int btnX = x + cw - btnW - 8;
            if (isInsideRow(mouseX, mouseY, btnX, btnY, btnW, btnH)) {
                openDownloadUrl(notifier.getUpdateUrl());
                return true;
            }

            int dismissW = 100;
            int dismissX = btnX - dismissW - 6;
            if (isInsideRow(mouseX, mouseY, dismissX, btnY, dismissW, btnH)) {
                toggleDismissCurrentUpdate();
                return true;
            }
        }

        int rowY = y + 86;
        int rowH = 20;

        if (isInsideRow(mouseX, mouseY, x, rowY, w, rowH)) {
            toggleConfigBoolean(CalcBoardClientConfig.CHECK_FOR_UPDATES);
            return true;
        }
        rowY += rowH + 3;

        if (isInsideRow(mouseX, mouseY, x, rowY, w, rowH)) {
            toggleConfigBoolean(CalcBoardClientConfig.SHOW_UPDATE_BADGE);
            return true;
        }
        rowY += rowH + 3;

        if (isInsideRow(mouseX, mouseY, x, rowY, w, rowH)) {
            toggleConfigBoolean(CalcBoardClientConfig.NOTIFY_UPDATE_IN_CHAT);
            return true;
        }
        rowY += rowH + 8;

        if (isInsideRow(mouseX, mouseY, x + 4, rowY, 130, 20)) {
            notifier.fetchDirectAsync();
            playClickSound();
            return true;
        }
        return false;
    }

    private void toggleConfigBoolean(ForgeConfigSpec.BooleanValue configValue) {
        configValue.set(!configValue.get());
        CalcBoardClientConfig.SPEC.save();
        playClickSound();
    }

    private void toggleDismissCurrentUpdate() {
        ClientUpdateNotifier notifier = ClientUpdateNotifier.getInstance();
        String currentDismissed = ClientPreferenceManager.getInstance().getDismissedUpdateVersion();
        if (currentDismissed.equalsIgnoreCase(notifier.getLatestVersion())) {
            notifier.undismissUpdate();
        } else {
            notifier.dismissCurrentUpdate();
        }
        playClickSound();
    }

    private void openDownloadUrl(String url) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        playClickSound();
        mc.setScreen(new ConfirmLinkScreen(confirmed -> {
            if (confirmed) {
                Util.getPlatform().openUri(URI.create(url));
            }
            mc.setScreen(parent);
        }, url, true));
    }
}
