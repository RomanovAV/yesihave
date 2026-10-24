package org.avromanov.yesihave.application;

import org.avromanov.yesihave.application.model.MatchDecision;
import org.avromanov.yesihave.application.model.MatchResultDto;
import org.avromanov.yesihave.image.EmbeddingProperties;
import org.avromanov.yesihave.image.EmbeddingService;
import org.avromanov.yesihave.image.ImageBytesNormalizer;
import org.avromanov.yesihave.persistence.CheckAuditRepository;
import org.avromanov.yesihave.persistence.MatchedCandidateRow;
import org.avromanov.yesihave.persistence.MatchingRepository;
import org.avromanov.yesihave.storage.ObjectStorageWriteBatch;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DefaultCheckPairUseCaseTest {
    @Test
    void retrievesReverseOrientationEvenWhenDirectShortlistsMissIt() throws Exception {
        var embeddings = mock(EmbeddingService.class);
        var repository = mock(MatchingRepository.class);
        var audit = mock(CheckAuditRepository.class);
        var normalizer = mock(ImageBytesNormalizer.class);
        var writes = mock(ObjectStorageWriteBatch.class);
        var uploads = mock(ObjectStorageWriteBatch.UploadScope.class);
        var transactions = mock(PlatformTransactionManager.class);
        byte[] front = {1};
        byte[] back = {2};
        float[] frontVector = {1, 0};
        float[] backVector = {0, 1};
        UUID directId = UUID.randomUUID();
        UUID reversedId = UUID.randomUUID();
        when(normalizer.normalize(front)).thenReturn(front);
        when(normalizer.normalize(back)).thenReturn(back);
        when(embeddings.toEmbedding(front)).thenReturn(frontVector);
        when(embeddings.toEmbedding(back)).thenReturn(backVector);
        when(repository.findTopFrontIds("test", frontVector, 20)).thenReturn(List.of(directId));
        when(repository.findTopBackIds("test", backVector, 20)).thenReturn(List.of(directId));
        when(repository.findTopBackIds("test", frontVector, 20)).thenReturn(List.of(reversedId));
        when(repository.findTopFrontIds("test", backVector, 20)).thenReturn(List.of(reversedId));
        when(repository.scoreCandidates("test", List.of(directId, reversedId), frontVector, backVector))
                .thenReturn(List.of(new MatchedCandidateRow(directId, "Unrelated", 0.6, 0.6),
                        new MatchedCandidateRow(reversedId, "Reversed", 0.96, 0.94)));
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(writes.execute(any())).thenAnswer(invocation -> {
            Function<ObjectStorageWriteBatch.UploadScope, MatchResultDto> operation = invocation.getArgument(0);
            return operation.apply(uploads);
        });
        var useCase = new DefaultCheckPairUseCase(embeddings, repository, audit, writes,
                new MatchingProperties(0.90, 0.86, 0.80, 0.75, 0.05, 20, 3),
                new EmbeddingProperties("deterministic", "", "", "", 2, "test"),
                normalizer, transactions);

        var result = useCase.check(42L, front, back);

        assertThat(result.decision()).isEqualTo(MatchDecision.MATCH);
        assertThat(result.matchedCoasterId()).isEqualTo(reversedId);
        assertThat(result.topCandidates()).extracting(c -> c.coasterId()).containsExactly(reversedId, directId);
        verify(repository).scoreCandidates("test", List.of(directId, reversedId), frontVector, backVector);
    }
}
