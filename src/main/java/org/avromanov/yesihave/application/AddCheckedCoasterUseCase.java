package org.avromanov.yesihave.application;

import org.avromanov.yesihave.application.model.FeedbackSource;

import java.util.UUID;

public interface AddCheckedCoasterUseCase {
    UUID add(String name,
             byte[] frontImage,
             byte[] backImage,
             UUID checkRequestId,
             FeedbackSource source);
}
