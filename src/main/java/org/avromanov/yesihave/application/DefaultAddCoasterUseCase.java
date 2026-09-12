package org.avromanov.yesihave.application;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DefaultAddCoasterUseCase implements AddCoasterUseCase {
    private final CoasterCreationService coasterCreationService;

    public DefaultAddCoasterUseCase(CoasterCreationService coasterCreationService) {
        this.coasterCreationService = coasterCreationService;
    }

    @Override
    public UUID add(String name, byte[] frontImage, byte[] backImage) {
        return coasterCreationService.create(name, frontImage, backImage, ignored -> { });
    }
}
