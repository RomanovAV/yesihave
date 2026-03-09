package org.avromanov.yesihave.image;

import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class StubEmbeddingService implements EmbeddingService {
    private static final int DIM = 512;

    @Override
    public float[] toEmbedding(byte[] image) {
        byte[] digest = sha256(image);
        float[] vector = new float[DIM];

        for (int i = 0; i < DIM; i++) {
            int unsigned = digest[i % digest.length] & 0xFF;
            vector[i] = (unsigned / 255.0f) - 0.5f;
        }

        return normalize(vector);
    }

    private byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private float[] normalize(float[] vector) {
        double normSquared = 0.0;
        for (float v : vector) {
            normSquared += v * v;
        }
        double norm = Math.sqrt(normSquared);
        if (norm == 0.0) {
            return vector;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
        return vector;
    }
}
