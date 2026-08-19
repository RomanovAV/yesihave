package org.avromanov.yesihave.image;

import java.awt.image.BufferedImage;

final class ClipImageTensor {
    static final int INPUT_SIZE = 224;

    private static final float[] CHANNEL_MEAN = {
            0.48145466f,
            0.4578275f,
            0.40821073f
    };
    private static final float[] CHANNEL_STD = {
            0.26862954f,
            0.26130258f,
            0.27577711f
    };

    private ClipImageTensor() {
    }

    static float[][][][] from(BufferedImage image) {
        if (image.getWidth() != INPUT_SIZE || image.getHeight() != INPUT_SIZE) {
            throw new IllegalArgumentException("CLIP input image must be 224x224");
        }

        float[][][][] input = new float[1][3][INPUT_SIZE][INPUT_SIZE];
        for (int y = 0; y < INPUT_SIZE; y++) {
            for (int x = 0; x < INPUT_SIZE; x++) {
                int rgb = image.getRGB(x, y);
                input[0][0][y][x] = normalize((rgb >> 16) & 0xFF, 0);
                input[0][1][y][x] = normalize((rgb >> 8) & 0xFF, 1);
                input[0][2][y][x] = normalize(rgb & 0xFF, 2);
            }
        }
        return input;
    }

    private static float normalize(int channelValue, int channel) {
        float unitValue = channelValue / 255.0f;
        return (unitValue - CHANNEL_MEAN[channel]) / CHANNEL_STD[channel];
    }
}
