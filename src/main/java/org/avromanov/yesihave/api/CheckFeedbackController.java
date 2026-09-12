package org.avromanov.yesihave.api;

import org.avromanov.yesihave.application.RecordCheckFeedbackUseCase;
import org.avromanov.yesihave.application.model.FeedbackSource;
import org.avromanov.yesihave.application.model.FeedbackVerdict;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/check")
public class CheckFeedbackController {
    private final RecordCheckFeedbackUseCase recordCheckFeedbackUseCase;

    public CheckFeedbackController(RecordCheckFeedbackUseCase recordCheckFeedbackUseCase) {
        this.recordCheckFeedbackUseCase = recordCheckFeedbackUseCase;
    }

    @PostMapping("/{checkRequestId}/feedback")
    public ResponseEntity<Void> record(@PathVariable UUID checkRequestId,
                                       @RequestBody FeedbackRequest request) {
        if (request == null || request.verdict() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "verdict must be set");
        }
        try {
            switch (request.verdict()) {
                case MATCH -> {
                    if (request.coasterId() == null) {
                        throw new IllegalArgumentException("coasterId must be set for MATCH feedback");
                    }
                    recordCheckFeedbackUseCase.confirmMatch(
                            checkRequestId,
                            request.coasterId(),
                            FeedbackSource.API
                    );
                }
                case NO_MATCH -> recordCheckFeedbackUseCase.confirmNoMatch(
                        checkRequestId,
                        request.coasterId(),
                        FeedbackSource.API
                );
            }
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(BAD_REQUEST, e.getMessage(), e);
        }
        return ResponseEntity.noContent().build();
    }

    public record FeedbackRequest(FeedbackVerdict verdict, UUID coasterId) {
    }
}
