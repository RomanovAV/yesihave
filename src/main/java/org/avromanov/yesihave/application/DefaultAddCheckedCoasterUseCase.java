package org.avromanov.yesihave.application;

import org.avromanov.yesihave.application.model.FeedbackSource;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DefaultAddCheckedCoasterUseCase implements AddCheckedCoasterUseCase {
    private final CoasterCreationService coasterCreationService;
    private final RecordCheckFeedbackUseCase recordCheckFeedbackUseCase;

    public DefaultAddCheckedCoasterUseCase(CoasterCreationService coasterCreationService,
                                           RecordCheckFeedbackUseCase recordCheckFeedbackUseCase) {
        this.coasterCreationService = coasterCreationService;
        this.recordCheckFeedbackUseCase = recordCheckFeedbackUseCase;
    }

    @Override
    public UUID add(String name,
                    byte[] frontImage,
                    byte[] backImage,
                    UUID checkRequestId,
                    FeedbackSource source) {
        return coasterCreationService.create(
                name,
                frontImage,
                backImage,
                coasterId -> recordCheckFeedbackUseCase.confirmNoMatch(
                        checkRequestId,
                        coasterId,
                        source
                )
        );
    }
}
