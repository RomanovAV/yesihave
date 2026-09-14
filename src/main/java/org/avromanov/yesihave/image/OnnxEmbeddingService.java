package org.avromanov.yesihave.image;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "app.embedding", name = "provider", havingValue = "onnx")
public class OnnxEmbeddingService implements EmbeddingService {
    private final OrtEnvironment environment;
    private final OrtSession session;
    private final String inputName;
    private final String outputName;
    private final int dimension;

    public OnnxEmbeddingService(EmbeddingProperties properties) {
        if (properties.onnxModelPath() == null || properties.onnxModelPath().isBlank()) {
            throw new IllegalStateException("app.embedding.onnx-model-path must be set for ONNX provider");
        }
        if (properties.dimension() <= 0) {
            throw new IllegalStateException("app.embedding.dimension must be > 0");
        }
        this.dimension = properties.dimension();
        try {
            this.environment = OrtEnvironment.getEnvironment();
            this.session = environment.createSession(properties.onnxModelPath(), new OrtSession.SessionOptions());
            this.inputName = resolveInputName(properties);
            this.outputName = resolveOutputName(properties);
        } catch (OrtException e) {
            throw new IllegalStateException("Failed to initialize ONNX runtime", e);
        }
    }

    @Override
    public float[] toEmbedding(byte[] imageBytes) {
        try {
            BufferedImage original = readImage(imageBytes);
            float[] fullViewEmbedding = runInference(ClipImageTensor.from(
                    ImagePreprocessor.prepareFullImageForEmbedding(original, ClipImageTensor.INPUT_SIZE)
            ));
            float[] croppedViewEmbedding = runInference(ClipImageTensor.from(
                    ImagePreprocessor.prepareForEmbedding(original, ClipImageTensor.INPUT_SIZE)
            ));
            return averageAndNormalize(fullViewEmbedding, croppedViewEmbedding);
        } catch (IOException | OrtException e) {
            throw new IllegalStateException("Failed to create embedding with ONNX", e);
        }
    }

    private BufferedImage readImage(byte[] imageBytes) throws IOException {
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (original == null) {
            throw new IllegalArgumentException("Unsupported image content");
        }
        return original;
    }

    private float[] runInference(float[][][][] input) throws OrtException {
        try (OnnxTensor tensor = OnnxTensor.createTensor(environment, input);
             OrtSession.Result result = session.run(Map.of(inputName, tensor))) {
            Object value = outputName == null
                    ? result.get(0).getValue()
                    : result.get(outputName).get().getValue();
            float[] embedding = extractEmbedding(value);
            return normalize(embedding);
        }
    }

    private float[] averageAndNormalize(float[] first, float[] second) {
        float[] result = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            result[i] = (first[i] + second[i]) / 2.0f;
        }
        return normalize(result);
    }

    private float[] extractEmbedding(Object value) {
        if (value instanceof float[][] batch && batch.length > 0) {
            return requireDimension(batch[0], dimension);
        }
        if (value instanceof float[] vector) {
            return requireDimension(vector, dimension);
        }
        if (value instanceof FloatBuffer floatBuffer) {
            float[] data = new float[floatBuffer.remaining()];
            floatBuffer.get(data);
            return requireDimension(data, dimension);
        }
        throw new IllegalStateException("Unsupported ONNX output type: " + value.getClass().getName());
    }

    private float[] requireDimension(float[] source, int targetDim) {
        if (source.length != targetDim) {
            throw new IllegalStateException(
                    "ONNX output dimension %d does not match configured dimension %d"
                            .formatted(source.length, targetDim)
            );
        }
        return source;
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

    private String resolveInputName(EmbeddingProperties properties) {
        if (properties.onnxInputName() != null && !properties.onnxInputName().isBlank()) {
            return properties.onnxInputName();
        }
        return session.getInputNames().iterator().next();
    }

    private String resolveOutputName(EmbeddingProperties properties) {
        if (properties.onnxOutputName() != null && !properties.onnxOutputName().isBlank()) {
            return properties.onnxOutputName();
        }
        return session.getOutputNames().size() == 1 ? session.getOutputNames().iterator().next() : null;
    }
}
