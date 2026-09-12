package org.avromanov.yesihave.application;

import org.avromanov.yesihave.application.model.FeedbackSource;

import java.util.UUID;

public interface RecordCheckFeedbackUseCase {
    void confirmMatch(UUID checkRequestId, UUID coasterId, FeedbackSource source);

    void confirmNoMatch(UUID checkRequestId, UUID addedCoasterId, FeedbackSource source);
}
