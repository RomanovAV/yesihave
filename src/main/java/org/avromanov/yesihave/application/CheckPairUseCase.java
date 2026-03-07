package org.avromanov.yesihave.application;

import org.avromanov.yesihave.application.model.MatchResultDto;

public interface CheckPairUseCase {
    MatchResultDto check(byte[] frontImage, byte[] backImage);
}
