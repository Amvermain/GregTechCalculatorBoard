package com.gtceu.calcboard.api.event;

import com.gtceu.calcboard.compat.IModAdapter;
import net.minecraftforge.eventbus.ListenerList;
import net.minecraftforge.eventbus.api.Event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Forge event fired when ModAdapterRegistry is initializing.
 * Allows third-party mods to register custom IModAdapter implementations.
 */
public class ModAdapterRegisterEvent extends Event {

    private static ListenerList LISTENER_LIST = new ListenerList();
    private final List<IModAdapter> registeredAdapters = new ArrayList<>();

    public ModAdapterRegisterEvent() {
    }

    /**
     * Registers a custom mod adapter with its defined priority and category handlers.
     */
    public void register(IModAdapter adapter) {
        if (adapter != null) {
            registeredAdapters.add(adapter);
        }
    }

    /**
     * Gets an unmodifiable list of all registered custom adapters.
     */
    public List<IModAdapter> getRegisteredAdapters() {
        return Collections.unmodifiableList(registeredAdapters);
    }

    @Override
    public ListenerList getListenerList() {
        return LISTENER_LIST;
    }

    public static void clearListeners() {
        LISTENER_LIST = new ListenerList();
    }
}
