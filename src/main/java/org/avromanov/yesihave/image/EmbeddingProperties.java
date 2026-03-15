package org.avromanov.yesihave.image;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.embedding")
public record EmbeddingProperties(
        String provider,
        String onnxModelPath,
        String onnxInputName,
        String onnxOutputName,
        int dimension,
        String modelVersion
) {
}
