package com.gtceu.calcboard.api.event;

import com.gtceu.calcboard.api.MachineAddon;
import net.minecraftforge.eventbus.ListenerList;
import net.minecraftforge.eventbus.api.Event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Event fired when discovering hardware addons (hatches, coils, rotors, augments, traits).
 * Allows third-party mods and KubeJS scripts to register custom machine addons.
 */
public class MachineAddonRegisterEvent extends Event {

    private static ListenerList LISTENER_LIST = new ListenerList();
    private final List<MachineAddon> registeredAddons = new ArrayList<>();

    public MachineAddonRegisterEvent() {
    }

    /**
     * Registers a custom machine addon.
     */
    public void register(MachineAddon addon) {
        if (addon != null) {
            registeredAddons.add(addon);
        }
    }

    /**
     * Gets an unmodifiable list of registered custom addons.
     */
    public List<MachineAddon> getRegisteredAddons() {
        return Collections.unmodifiableList(registeredAddons);
    }

    @Override
    public ListenerList getListenerList() {
        return LISTENER_LIST;
    }

    public static void clearListeners() {
        LISTENER_LIST = new ListenerList();
    }
}
