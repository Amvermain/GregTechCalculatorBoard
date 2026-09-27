package com.gtceu.calcboard.client.gui.inspector;

import com.gtceu.calcboard.client.gui.inspector.section.BoilerThrottleSection;
import com.gtceu.calcboard.client.gui.inspector.section.HardwareConfigSection;
import com.gtceu.calcboard.client.gui.inspector.section.IInspectorSection;
import com.gtceu.calcboard.client.gui.inspector.section.InspectorHeaderSection;
import com.gtceu.calcboard.client.gui.inspector.section.MachineCountSection;
import com.gtceu.calcboard.client.gui.inspector.section.MultiblockEnergyHatchSection;
import com.gtceu.calcboard.client.gui.inspector.section.OverclockModeSection;
import com.gtceu.calcboard.client.gui.inspector.section.SingleblockTierSection;
import com.gtceu.calcboard.client.gui.inspector.section.StatsSummarySection;
import com.gtceu.calcboard.client.gui.inspector.section.SteamModeSection;
import com.gtceu.calcboard.client.gui.inspector.section.SubPageModuleSection;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class NodeInspectorRegistry {

    private static final List<Supplier<IInspectorSection>> SECTION_FACTORIES = new ArrayList<>();

    static {
        registerSection(InspectorHeaderSection::new);
        registerSection(MachineCountSection::new);
        registerSection(SingleblockTierSection::new);
        registerSection(MultiblockEnergyHatchSection::new);
        registerSection(OverclockModeSection::new);
        registerSection(SteamModeSection::new);
        registerSection(BoilerThrottleSection::new);
        registerSection(SubPageModuleSection::new);
        registerSection(HardwareConfigSection::new);
        registerSection(StatsSummarySection::new);
    }

    private NodeInspectorRegistry() {}

    public static synchronized void registerSection(Supplier<IInspectorSection> factory) {
        SECTION_FACTORIES.add(factory);
    }

    public static List<IInspectorSection> createSections() {
        List<IInspectorSection> sections = new ArrayList<>(SECTION_FACTORIES.size());
        for (Supplier<IInspectorSection> factory : SECTION_FACTORIES) {
            sections.add(factory.get());
        }
        return sections;
    }
}
