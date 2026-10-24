package org.avromanov.yesihave.application;

import org.avromanov.yesihave.application.model.FeedbackSource;
import org.avromanov.yesihave.application.model.FeedbackVerdict;
import org.avromanov.yesihave.persistence.CheckFeedbackRepository;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DefaultRecordCheckFeedbackUseCase implements RecordCheckFeedbackUseCase {
    private final CheckFeedbackRepository checkFeedbackRepository;
    private final ApplicationEventPublisher events;

    public DefaultRecordCheckFeedbackUseCase(CheckFeedbackRepository checkFeedbackRepository,
                                             ApplicationEventPublisher events) {
        this.checkFeedbackRepository = checkFeedbackRepository;
        this.events = events;
    }

    @Override
    @Transactional
    public void confirmMatch(UUID checkRequestId, UUID coasterId, FeedbackSource source) {
        requireCheckRequest(checkRequestId);
        if (coasterId == null) {
            throw new IllegalArgumentException("coasterId must be set for MATCH feedback");
        }
        if (!checkFeedbackRepository.coasterExists(coasterId)) {
            throw new IllegalArgumentException("Unknown coaster: " + coasterId);
        }
        checkFeedbackRepository.upsert(
                checkRequestId,
                FeedbackVerdict.MATCH,
                coasterId,
                null,
                requireSource(source)
        );
        events.publishEvent(new ConfirmedReferenceIndexer.ConfirmedCheck(checkRequestId));
    }

    @Override
    @Transactional
    public void confirmNoMatch(UUID checkRequestId, UUID addedCoasterId, FeedbackSource source) {
        requireCheckRequest(checkRequestId);
        if (addedCoasterId != null && !checkFeedbackRepository.coasterExists(addedCoasterId)) {
            throw new IllegalArgumentException("Unknown added coaster: " + addedCoasterId);
        }
        checkFeedbackRepository.upsert(
                checkRequestId,
                FeedbackVerdict.NO_MATCH,
                null,
                addedCoasterId,
                requireSource(source)
        );
    }

    private void requireCheckRequest(UUID checkRequestId) {
        if (checkRequestId == null) {
            throw new IllegalArgumentException("checkRequestId must be set");
        }
        if (!checkFeedbackRepository.checkRequestExists(checkRequestId)) {
            throw new IllegalArgumentException("Unknown check request: " + checkRequestId);
        }
    }

    private FeedbackSource requireSource(FeedbackSource source) {
        if (source == null) {
            throw new IllegalArgumentException("source must be set");
        }
        return source;
    }
}
