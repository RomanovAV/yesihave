package org.avromanov.yesihave.application;

import org.avromanov.yesihave.image.*;
import org.avromanov.yesihave.persistence.*;
import org.avromanov.yesihave.storage.ObjectStorageService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ReindexEmbeddingsServiceTest {
    @Test
    void reindexesConfirmedReferencesBeforeActivatingNewVersion() throws Exception {
        var coasters = mock(CoasterReadRepository.class);
        var storage = mock(ObjectStorageService.class);
        var embeddings = mock(EmbeddingService.class);
        var matching = mock(MatchingRepository.class);
        var provider = mock(EmbeddingPipelineDescriptorProvider.class);
        var versions = mock(EmbeddingVersionRepository.class);
        var normalizer = mock(ImageBytesNormalizer.class);
        var checks = mock(CheckEmbeddingRepository.class);
        var descriptor = new EmbeddingPipelineDescriptor("next", "deterministic", "test", "test", 512);
        UUID id = UUID.randomUUID();
        byte[] front = {1};
        byte[] back = {2};
        float[] fv = {1, 0};
        float[] bv = {0, 1};
        when(provider.descriptor()).thenReturn(descriptor);
        when(coasters.findPairsNeedingReindex("next", null, 100)).thenReturn(List.of());
        when(checks.findConfirmedNeedingReindex("next", null, 100))
                .thenReturn(List.of(new CheckEmbeddingRepository.ConfirmedPair(id, "front", "back")));
        when(checks.findConfirmedNeedingReindex("next", id, 100)).thenReturn(List.of());
        when(storage.getBytes("front")).thenReturn(front);
        when(storage.getBytes("back")).thenReturn(back);
        when(normalizer.normalize(front)).thenReturn(front);
        when(normalizer.normalize(back)).thenReturn(back);
        when(embeddings.toEmbedding(front)).thenReturn(fv);
        when(embeddings.toEmbedding(back)).thenReturn(bv);
        var service = new ReindexEmbeddingsService(coasters, storage, embeddings, matching,
                provider, versions, normalizer, checks);

        assertThat(service.reindexAll()).isEqualTo(1);

        var order = inOrder(versions, checks);
        order.verify(versions).beginBuild(descriptor);
        order.verify(checks).findConfirmedNeedingReindex("next", null, 100);
        order.verify(checks).upsert(id, "next", ImagePairFingerprint.of(front, back), fv, bv);
        order.verify(checks).findConfirmedNeedingReindex("next", id, 100);
        order.verify(versions).activate(descriptor);
    }
}
