package org.avromanov.yesihave.application;

import org.avromanov.yesihave.application.model.FeedbackSource;
import org.avromanov.yesihave.persistence.CheckAuditRepository;
import org.avromanov.yesihave.storage.ObjectStorageService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DefaultAddCheckedCoasterUseCase implements AddCheckedCoasterUseCase {
    private final CoasterCreationService coasterCreationService;
    private final RecordCheckFeedbackUseCase recordCheckFeedbackUseCase;
    private final CheckAuditRepository checkAuditRepository;
    private final ObjectStorageService objectStorageService;

    public DefaultAddCheckedCoasterUseCase(CoasterCreationService coasterCreationService,
                                           RecordCheckFeedbackUseCase recordCheckFeedbackUseCase,
                                           CheckAuditRepository checkAuditRepository,
                                           ObjectStorageService objectStorageService) {
        this.coasterCreationService = coasterCreationService;
        this.recordCheckFeedbackUseCase = recordCheckFeedbackUseCase;
        this.checkAuditRepository = checkAuditRepository;
        this.objectStorageService = objectStorageService;
    }

    @Override
    public UUID add(String name,
                    UUID checkRequestId,
                    FeedbackSource source) {
        CheckAuditRepository.CheckImageKeys imageKeys = checkAuditRepository.requireImageKeys(checkRequestId);
        byte[] frontImage = objectStorageService.getBytes(imageKeys.frontKey());
        byte[] backImage = objectStorageService.getBytes(imageKeys.backKey());
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
