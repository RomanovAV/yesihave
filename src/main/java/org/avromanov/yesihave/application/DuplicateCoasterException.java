package org.avromanov.yesihave.application;

import java.util.UUID;

public class DuplicateCoasterException extends IllegalArgumentException {
    private final UUID existingCoasterId;

    public DuplicateCoasterException(UUID existingCoasterId) {
        super("Coaster with the same image pair already exists: " + existingCoasterId);
        this.existingCoasterId = existingCoasterId;
    }

    public UUID existingCoasterId() {
        return existingCoasterId;
    }
}
