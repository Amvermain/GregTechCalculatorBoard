package com.gtceu.calcboard.api.history;

import com.gtceu.calcboard.api.FlowGraph;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Manages the per-page Undo and Redo command history using a lightweight vector/delta model.
 */
public class HistoryManager {
    private static final int MAX_HISTORY = 100;

    private final Deque<BoardCommand> undoStack = new ArrayDeque<>();
    private final Deque<BoardCommand> redoStack = new ArrayDeque<>();

    public void record(BoardCommand command) {
        if (command == null) return;
        undoStack.push(command);
        redoStack.clear();
        while (undoStack.size() > MAX_HISTORY) {
            undoStack.removeLast();
        }
    }

    public BoardCommand undo(FlowGraph graph) {
        if (undoStack.isEmpty() || graph == null) return null;
        BoardCommand cmd = undoStack.pop();
        cmd.undo(graph);
        redoStack.push(cmd);
        return cmd;
    }

    public BoardCommand redo(FlowGraph graph) {
        if (redoStack.isEmpty() || graph == null) return null;
        BoardCommand cmd = redoStack.pop();
        cmd.redo(graph);
        undoStack.push(cmd);
        return cmd;
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public BoardCommand peekUndo() {
        return undoStack.peek();
    }

    public BoardCommand peekRedo() {
        return redoStack.peek();
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }
}
