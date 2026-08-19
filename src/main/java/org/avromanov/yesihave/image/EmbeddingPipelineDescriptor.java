package org.avromanov.yesihave.image;

public record EmbeddingPipelineDescriptor(
        String modelVersion,
        String provider,
        String modelFingerprint,
        String preprocessingProfile,
        int dimension
) {
}
