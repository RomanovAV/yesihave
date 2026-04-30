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
    private static final int INPUT_SIZE = 224;

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
            float[] fullViewEmbedding = runInference(toInputTensor(ImagePreprocessor.prepareFullImageForEmbedding(original, INPUT_SIZE)));
            float[] croppedViewEmbedding = runInference(toInputTensor(ImagePreprocessor.prepareForEmbedding(original, INPUT_SIZE)));
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

    private float[][][][] toInputTensor(BufferedImage resized) {
        float[] output = new float[3 * INPUT_SIZE * INPUT_SIZE];
        int planeSize = INPUT_SIZE * INPUT_SIZE;

        for (int y = 0; y < INPUT_SIZE; y++) {
            for (int x = 0; x < INPUT_SIZE; x++) {
                int rgb = resized.getRGB(x, y);
                float r = ((rgb >> 16) & 0xFF) / 255.0f;
                float g = ((rgb >> 8) & 0xFF) / 255.0f;
                float b = (rgb & 0xFF) / 255.0f;
                int pos = y * INPUT_SIZE + x;
                output[pos] = (r - 0.485f) / 0.229f;
                output[planeSize + pos] = (g - 0.456f) / 0.224f;
                output[2 * planeSize + pos] = (b - 0.406f) / 0.225f;
            }
        }

        float[][][][] input = new float[1][3][INPUT_SIZE][INPUT_SIZE];
        int idx = 0;
        for (int c = 0; c < 3; c++) {
            for (int y = 0; y < INPUT_SIZE; y++) {
                for (int x = 0; x < INPUT_SIZE; x++) {
                    input[0][c][y][x] = output[idx++];
                }
            }
        }
        return input;
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
            return toFixedDim(batch[0], dimension);
        }
        if (value instanceof float[] vector) {
            return toFixedDim(vector, dimension);
        }
        if (value instanceof FloatBuffer floatBuffer) {
            float[] data = new float[floatBuffer.remaining()];
            floatBuffer.get(data);
            return toFixedDim(data, dimension);
        }
        throw new IllegalStateException("Unsupported ONNX output type: " + value.getClass().getName());
    }

    private float[] toFixedDim(float[] source, int targetDim) {
        if (source.length == targetDim) {
            return source;
        }
        float[] result = new float[targetDim];
        int len = Math.min(source.length, targetDim);
        System.arraycopy(source, 0, result, 0, len);
        return result;
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
