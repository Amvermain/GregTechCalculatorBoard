package com.gtceu.calcboard.client.gui.api;

import com.gtceu.calcboard.client.gui.BoardSelectionModel;
import com.gtceu.calcboard.client.gui.model.PortRef;

import java.util.Set;

/**
 * Handler interface managing element selection states, port selections, and clipboard operations.
 */
public interface IBoardSelectionHandler {

    BoardSelectionModel getSelectionModel();

    Set<String> getSelectedNodeIds();

    Set<String> getSelectedNoteIds();

    Set<String> getSelectedFrameIds();

    boolean isNodeSelected(String id);

    boolean isNoteSelected(String id);

    boolean isFrameSelected(String id);

    void selectNode(String id, boolean multi);

    void deselectNode(String id);

    void selectNote(String id, boolean multi);

    void selectFrame(String id, boolean multi);

    void toggleSelectNode(String id);

    void toggleSelectNote(String id);

    void toggleSelectFrame(String id);

    Set<PortRef> getSelectedPorts();

    boolean isPortSelected(String nodeId, boolean isInput, int portIndex);

    boolean isPortSelected(PortRef port);

    boolean hasSelectedPorts();

    void selectPort(String nodeId, boolean isInput, int portIndex, boolean multi);

    void toggleSelectPort(String nodeId, boolean isInput, int portIndex);

    void selectPortRange(String nodeId, boolean isInput, int targetPortIndex);

    void clearPortSelection();

    void clearSelection();

    void selectAll();

    void deleteSelection();

    void copySelection();

    void pasteSelection(double canvasX, double canvasY);

    void cutSelection();

    void duplicateSelection();

    boolean isBoxSelecting();
}
