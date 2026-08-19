package org.avromanov.yesihave.image;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class EmbeddingPipelineDescriptorProvider {
    private static final String DETERMINISTIC_FINGERPRINT = "builtin:sha256-repeat-v1";
    private static final String DETERMINISTIC_PREPROCESSING = "normalized-image-bytes-v1";

    private final EmbeddingProperties properties;
    private volatile EmbeddingPipelineDescriptor cachedDescriptor;

    public EmbeddingPipelineDescriptorProvider(EmbeddingProperties properties) {
        this.properties = properties;
    }

    public EmbeddingPipelineDescriptor descriptor() {
        EmbeddingPipelineDescriptor current = cachedDescriptor;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (cachedDescriptor == null) {
                cachedDescriptor = createDescriptor();
            }
            return cachedDescriptor;
        }
    }

    private EmbeddingPipelineDescriptor createDescriptor() {
        String modelVersion = requireText(properties.modelVersion(), "app.embedding.model-version");
        String provider = requireText(properties.provider(), "app.embedding.provider").toLowerCase(Locale.ROOT);
        if (properties.dimension() <= 0) {
            throw new IllegalStateException("app.embedding.dimension must be > 0");
        }

        return switch (provider) {
            case "onnx" -> new EmbeddingPipelineDescriptor(
                    modelVersion,
                    provider,
                    sha256(requireText(properties.onnxModelPath(), "app.embedding.onnx-model-path")),
                    ClipImageTensor.PREPROCESSING_PROFILE,
                    properties.dimension()
            );
            case "deterministic" -> new EmbeddingPipelineDescriptor(
                    modelVersion,
                    provider,
                    DETERMINISTIC_FINGERPRINT,
                    DETERMINISTIC_PREPROCESSING,
                    properties.dimension()
            );
            default -> throw new IllegalStateException("Unsupported embedding provider: " + provider);
        };
    }

    private String sha256(String filePath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(Path.of(filePath))) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to fingerprint ONNX model: " + filePath, e);
        }
    }

    private String requireText(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " must be set");
        }
        return value;
    }
}
