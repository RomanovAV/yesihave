package org.avromanov.yesihave.image;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingPipelineDescriptorProviderTest {
    @TempDir
    Path tempDir;

    @Test
    void describesDeterministicProviderWithoutExternalModel() {
        EmbeddingProperties properties = new EmbeddingProperties(
                "deterministic",
                "",
                "",
                "",
                512,
                "test-v1"
        );

        EmbeddingPipelineDescriptor descriptor = new EmbeddingPipelineDescriptorProvider(properties).descriptor();

        assertThat(descriptor.modelVersion()).isEqualTo("test-v1");
        assertThat(descriptor.provider()).isEqualTo("deterministic");
        assertThat(descriptor.modelFingerprint()).isEqualTo("builtin:sha256-repeat-v1");
        assertThat(descriptor.preprocessingProfile()).isEqualTo("normalized-image-bytes-v1");
        assertThat(descriptor.dimension()).isEqualTo(512);
    }

    @Test
    void fingerprintsOnnxModelBytes() throws IOException {
        Path model = tempDir.resolve("model.onnx");
        Files.write(model, new byte[]{1, 2, 3});
        EmbeddingProperties properties = new EmbeddingProperties(
                "onnx",
                model.toString(),
                "image",
                "embedding",
                512,
                "clip-v2"
        );

        EmbeddingPipelineDescriptor descriptor = new EmbeddingPipelineDescriptorProvider(properties).descriptor();

        assertThat(descriptor.modelFingerprint())
                .isEqualTo("039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81");
        assertThat(descriptor.preprocessingProfile())
                .isEqualTo("openai-clip-bicubic-center-crop-dual-view-v2");
    }
}
