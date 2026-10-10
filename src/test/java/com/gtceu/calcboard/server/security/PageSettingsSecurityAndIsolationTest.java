package com.gtceu.calcboard.server.security;

import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.team.TeamWorkspacePage;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.team.ClientWorkspaceState;
import com.gtceu.calcboard.server.storage.TeamBoardSavedData;
import com.gtceu.calcboard.server.storage.TeamWorkspaceData;
import com.gtceu.calcboard.server.storage.WorkspaceLockManager;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

public class PageSettingsSecurityAndIsolationTest {

    private WorkspaceLockManager lockManager;
    private UUID teamA;
    private UUID teamB;
    private UUID playerA;
    private UUID playerB;

    @BeforeEach
    void setUp() {
        lockManager = WorkspaceLockManager.getInstance();
        lockManager.clearAll();
        teamA = UUID.randomUUID();
        teamB = UUID.randomUUID();
        playerA = UUID.randomUUID();
        playerB = UUID.randomUUID();
    }

    @Test
    @DisplayName("TeamWorkspacePage initializes settings with deterministic defaults")
    void testTeamWorkspacePageSettingsDefaultsAndApply() {
        TeamWorkspacePage page = new TeamWorkspacePage("page_ore_processing", "Ore Processing");
        Assertions.assertEquals((byte) -1, page.getDefaultVoltageTierOrdinal());
        Assertions.assertTrue(page.isAutoEquipEnergyHatches());

        page.applySettings((byte) 4, false);
        Assertions.assertEquals((byte) 4, page.getDefaultVoltageTierOrdinal());
        Assertions.assertFalse(page.isAutoEquipEnergyHatches());
    }

    @Test
    @DisplayName("TeamWorkspacePage settings serialize and deserialize correctly via NBT")
    void testTeamWorkspacePageSettingsNbtPersistence() {
        TeamWorkspacePage original = new TeamWorkspacePage("page_circuit_line", "Circuit Assembly");
        original.applySettings((byte) 6, false);

        CompoundTag tag = original.toNBT();
        TeamWorkspacePage restored = TeamWorkspacePage.fromNBT(tag);

        Assertions.assertEquals((byte) 6, restored.getDefaultVoltageTierOrdinal());
        Assertions.assertFalse(restored.isAutoEquipEnergyHatches());
    }

    @Test
    @DisplayName("TeamWorkspacePage fromNBT falls back safely when settings tags are absent")
    void testTeamWorkspacePageBackwardCompatibilityWithoutSettingsTags() {
        CompoundTag legacyTag = new CompoundTag();
        legacyTag.putString("PageId", "legacy_page");
        legacyTag.putString("PageTitle", "Legacy Page");
        legacyTag.putInt("PageRevision", 2);

        TeamWorkspacePage restored = TeamWorkspacePage.fromNBT(legacyTag);
        Assertions.assertEquals((byte) -1, restored.getDefaultVoltageTierOrdinal());
        Assertions.assertTrue(restored.isAutoEquipEnergyHatches());
    }

    @Test
    @DisplayName("TeamWorkspaceData preserves page settings across NBT persistence roundtrip")
    void testTeamWorkspaceDataPageSettingsPersistence() {
        TeamWorkspaceData ws = new TeamWorkspaceData(teamA, "Alpha Team");
        TeamWorkspacePage page = new TeamWorkspacePage("page_chemistry", "Chemical Reactor", 1, new byte[0]);
        page.applySettings((byte) 3, true);
        ws.addOrUpdatePage(page);

        CompoundTag tag = ws.toNBT();
        TeamWorkspaceData restored = TeamWorkspaceData.fromNBT(tag);

        TeamWorkspacePage restoredPage = restored.getPage("page_chemistry");
        Assertions.assertNotNull(restoredPage);
        Assertions.assertEquals((byte) 3, restoredPage.getDefaultVoltageTierOrdinal());
        Assertions.assertTrue(restoredPage.isAutoEquipEnergyHatches());
    }

    @Test
    @DisplayName("WorkspaceLockManager enforces lock authorization before settings commit")
    void testWorkspaceLockManagerCanCommitAccessControl() {
        String pageId = "page_distillation";

        Assertions.assertTrue(lockManager.canCommit(teamA, pageId, playerA));
        Assertions.assertTrue(lockManager.canCommit(teamA, pageId, playerB));

        boolean locked = lockManager.acquireLock(teamA, pageId, playerA, "PlayerA");
        Assertions.assertTrue(locked);

        Assertions.assertTrue(lockManager.canCommit(teamA, pageId, playerA));
        Assertions.assertFalse(lockManager.canCommit(teamA, pageId, playerB));

        var lock = lockManager.getLock(teamA, pageId);
        Assertions.assertNotNull(lock);
        lock.extend(-1000000L);

        Assertions.assertTrue(lockManager.canCommit(teamA, pageId, playerB));
    }

    @Test
    @DisplayName("TeamBoardSavedData isolates page settings across different team workspaces")
    void testTeamWorkspaceDataIsolationAcrossTeams() {
        TeamBoardSavedData savedData = new TeamBoardSavedData();

        TeamWorkspaceData wsA = savedData.getOrCreateWorkspace(teamA, "Team A");
        TeamWorkspaceData wsB = savedData.getOrCreateWorkspace(teamB, "Team B");

        TeamWorkspacePage pageA = new TeamWorkspacePage("page_shared_id", "Main A");
        pageA.applySettings((byte) 5, false);
        wsA.addOrUpdatePage(pageA);

        TeamWorkspacePage pageB = new TeamWorkspacePage("page_shared_id", "Main B");
        pageB.applySettings((byte) 1, true);
        wsB.addOrUpdatePage(pageB);

        Assertions.assertEquals((byte) 5, wsA.getPage("page_shared_id").getDefaultVoltageTierOrdinal());
        Assertions.assertFalse(wsA.getPage("page_shared_id").isAutoEquipEnergyHatches());

        Assertions.assertEquals((byte) 1, wsB.getPage("page_shared_id").getDefaultVoltageTierOrdinal());
        Assertions.assertTrue(wsB.getPage("page_shared_id").isAutoEquipEnergyHatches());
    }

    @Test
    @DisplayName("ClientWorkspaceState propagates team page settings into created BoardPage")
    void testClientWorkspaceStatePageSettingsMapping() {
        ClientWorkspaceState clientState = ClientWorkspaceState.getInstance();
        clientState.clear();

        TeamWorkspacePage tp = new TeamWorkspacePage("page_fusion", "Fusion Reactor");
        tp.applySettings((byte) GTVoltageTier.LuV.ordinal(), false);
        clientState.updateRemotePages(java.util.List.of(tp));

        BoardPage bp = clientState.getTeamPageAsBoardPage("page_fusion");
        Assertions.assertNotNull(bp);
        Assertions.assertEquals(GTVoltageTier.LuV, bp.getDefaultVoltageTier());
        Assertions.assertFalse(bp.isAutoEquipEnergyHatches());
    }

    @Test
    @DisplayName("ClientWorkspaceState syncPageSettingsIfTeamMode updates remote page and skips local mode")
    void testClientWorkspaceStateSyncPageSettingsBehavior() {
        ClientWorkspaceState clientState = ClientWorkspaceState.getInstance();
        clientState.clear();

        TeamWorkspacePage tp = new TeamWorkspacePage("page_turbine", "Turbine");
        tp.applySettings((byte) 2, true);
        clientState.updateRemotePages(java.util.List.of(tp));

        BoardPage localPage = new BoardPage("page_turbine", "Turbine", new com.gtceu.calcboard.api.model.FlowGraph());
        localPage.setDefaultVoltageTier(GTVoltageTier.EV);
        localPage.setAutoEquipEnergyHatches(false);

        clientState.setCurrentMode(ClientWorkspaceState.WorkspaceMode.LOCAL);
        clientState.syncPageSettingsIfTeamMode(localPage);
        Assertions.assertEquals((byte) 2, tp.getDefaultVoltageTierOrdinal());

        clientState.setCurrentMode(ClientWorkspaceState.WorkspaceMode.TEAM);
        clientState.setCurrentTeamId(teamA);
        clientState.syncPageSettingsIfTeamMode(localPage);
        Assertions.assertEquals((byte) GTVoltageTier.EV.ordinal(), tp.getDefaultVoltageTierOrdinal());
        Assertions.assertFalse(tp.isAutoEquipEnergyHatches());
    }

    @Test
    @DisplayName("ClientPacketHandler rolls back pending settings to pre-change snapshot on server rejection")
    void testClientRollbackRestoresPreviousSettingsOnRejection() {
        ClientWorkspaceState clientState = ClientWorkspaceState.getInstance();
        clientState.clear();
        clientState.setCurrentMode(ClientWorkspaceState.WorkspaceMode.TEAM);
        clientState.setCurrentTeamId(teamA);
        clientState.setActiveTeamPageId("page_chem");

        TeamWorkspacePage tp = new TeamWorkspacePage("page_chem", "Chemistry");
        tp.applySettings((byte) GTVoltageTier.LV.ordinal(), true);
        clientState.updateRemotePages(java.util.List.of(tp));

        BoardPage page = clientState.getTeamPageAsBoardPage("page_chem");
        Assertions.assertNotNull(page);
        page.setDefaultVoltageTier(GTVoltageTier.HV);
        page.setAutoEquipEnergyHatches(false);

        clientState.syncPageSettingsIfTeamMode(page);
        Assertions.assertEquals((byte) GTVoltageTier.HV.ordinal(), tp.getDefaultVoltageTierOrdinal());
        Assertions.assertFalse(tp.isAutoEquipEnergyHatches());

        com.gtceu.calcboard.network.packet.s2c.ClientPacketHandler.handleError(
                new com.gtceu.calcboard.network.packet.s2c.S2CWorkspaceErrorPacket(403, "gui.gtcalcboard.error.access_denied")
        );

        Assertions.assertEquals((byte) GTVoltageTier.LV.ordinal(), tp.getDefaultVoltageTierOrdinal());
        Assertions.assertTrue(tp.isAutoEquipEnergyHatches());
    }

    @Test
    @DisplayName("ClientPacketHandler handleUpdatePageSettings applies confirmed settings and revision")
    void testClientUpdatePageSettingsAppliesConfirmedSettingsAndRevision() {
        ClientWorkspaceState clientState = ClientWorkspaceState.getInstance();
        clientState.clear();
        clientState.setCurrentMode(ClientWorkspaceState.WorkspaceMode.TEAM);
        clientState.setCurrentTeamId(teamA);
        clientState.setActiveTeamPageId("page_chem");

        TeamWorkspacePage tp = new TeamWorkspacePage("page_chem", "Chemistry", 1, new byte[0]);
        tp.applySettings((byte) GTVoltageTier.LV.ordinal(), true);
        clientState.updateRemotePages(java.util.List.of(tp));

        com.gtceu.calcboard.network.packet.c2s.UpdatePageSettingsPacket packet =
                new com.gtceu.calcboard.network.packet.c2s.UpdatePageSettingsPacket(
                        teamA, "page_chem", 2, GTVoltageTier.EV.ordinal(), false
                );

        com.gtceu.calcboard.network.packet.s2c.ClientPacketHandler.handleUpdatePageSettings(packet);

        Assertions.assertEquals(2, tp.getPageRevision());
        Assertions.assertEquals((byte) GTVoltageTier.EV.ordinal(), tp.getDefaultVoltageTierOrdinal());
        Assertions.assertFalse(tp.isAutoEquipEnergyHatches());
    }

    @Test
    @DisplayName("PageSettingsDialog refreshPageSettings dynamically syncs targetPage fields")
    void testPageSettingsDialogLiveRefreshOnRemoteUpdate() {
        ClientWorkspaceState clientState = ClientWorkspaceState.getInstance();
        clientState.clear();
        clientState.setCurrentMode(ClientWorkspaceState.WorkspaceMode.TEAM);
        clientState.setCurrentTeamId(teamA);

        TeamWorkspacePage tp = new TeamWorkspacePage("page_dialog_sync", "Dialog Sync", 1, new byte[0]);
        tp.applySettings((byte) GTVoltageTier.MV.ordinal(), true);
        clientState.updateRemotePages(java.util.List.of(tp));

        BoardPage targetPage = clientState.getTeamPageAsBoardPage("page_dialog_sync");
        Assertions.assertNotNull(targetPage);
        Assertions.assertEquals(GTVoltageTier.MV, targetPage.getDefaultVoltageTier());
        Assertions.assertTrue(targetPage.isAutoEquipEnergyHatches());

        com.gtceu.calcboard.client.gui.dialog.PageSettingsDialog dialog =
                new com.gtceu.calcboard.client.gui.dialog.PageSettingsDialog(null);
        dialog.open(targetPage);

        tp.applySettings((byte) GTVoltageTier.IV.ordinal(), false);
        dialog.refreshPageSettings("page_dialog_sync");

        Assertions.assertEquals(GTVoltageTier.IV, targetPage.getDefaultVoltageTier());
        Assertions.assertFalse(targetPage.isAutoEquipEnergyHatches());
    }
}
