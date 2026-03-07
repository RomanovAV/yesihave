package org.avromanov.yesihave.image;

import org.springframework.stereotype.Service;

@Service
public class StubEmbeddingService implements EmbeddingService {
    @Override
    public float[] toEmbedding(byte[] image) {
        return new float[512];
    }
}
