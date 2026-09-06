package com.gtceu.calcboard.client.gui.dialog.modal;

import java.util.Objects;

/**
 * Type-safe identifier for modal dialogs.
 */
public final class ModalId<T extends IBoardModal> {

    private final String id;
    private final Class<T> modalClass;

    private ModalId(String id, Class<T> modalClass) {
        this.id = Objects.requireNonNull(id);
        this.modalClass = Objects.requireNonNull(modalClass);
    }

    public static <T extends IBoardModal> ModalId<T> of(String id, Class<T> modalClass) {
        return new ModalId<>(id, modalClass);
    }

    public String getId() {
        return id;
    }

    public Class<T> getModalClass() {
        return modalClass;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModalId<?> other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "ModalId[" + id + "]";
    }
}
