package com.iota.iri;

import java.util.Arrays;
import java.util.Optional;

public enum IxiEvent {
    CREATE_MODULE("ENTRY_CREATE"),
    MODIFY_MODULE("ENTRY_MODIFY"),
    DELETE_MODULE("ENTRY_DELETE"),
    OVERFLOW("OVERFLOW"),
    UNKNOWN("UNKNOWN");

    private String name;

    IxiEvent(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static IxiEvent fromName(String name) {
        Optional<IxiEvent> ixiEvent = Arrays.stream(IxiEvent.values()).filter(event -> event.name.equals(name)).findFirst();
        return ixiEvent.orElse(UNKNOWN);
    }
}
