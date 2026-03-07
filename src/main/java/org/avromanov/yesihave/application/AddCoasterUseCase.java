package org.avromanov.yesihave.application;

import java.util.UUID;

public interface AddCoasterUseCase {
    UUID add(String name, byte[] frontImage, byte[] backImage);
}
