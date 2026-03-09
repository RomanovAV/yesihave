package org.avromanov.yesihave.application;

import org.avromanov.yesihave.application.model.MatchResultDto;

public interface CheckPairUseCase {
    MatchResultDto check(long telegramUserId, byte[] frontImage, byte[] backImage);
}
