package org.avromanov.yesihave.api;

import org.avromanov.yesihave.application.DuplicateCoasterException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    @Test
    void mapsDuplicateToConflictWithExistingCoasterId() {
        UUID existingId = UUID.randomUUID();

        var response = new ApiExceptionHandler().duplicate(new DuplicateCoasterException(existingId));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("DUPLICATE_COASTER");
        assertThat(response.getBody().coasterId()).isEqualTo(existingId);
    }
}
