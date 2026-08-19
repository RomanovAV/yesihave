package org.avromanov.yesihave.image;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ImagePreprocessorTest {
    @Test
    void resizesShortestSideAndCenterCropsLikeClip() {
        BufferedImage landscape = new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = landscape.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, 100, 200);
        graphics.setColor(Color.GREEN);
        graphics.fillRect(100, 0, 200, 200);
        graphics.setColor(Color.BLUE);
        graphics.fillRect(300, 0, 100, 200);
        graphics.dispose();

        BufferedImage prepared = ImagePreprocessor.prepareFullImageForEmbedding(landscape, 224);

        assertThat(prepared.getWidth()).isEqualTo(224);
        assertThat(prepared.getHeight()).isEqualTo(224);
        assertThat(new Color(prepared.getRGB(112, 112))).isEqualTo(Color.GREEN);
    }

    @Test
    void normalizesRgbChannelsWithOpenAiClipConstants() {
        BufferedImage image = new BufferedImage(
                ClipImageTensor.INPUT_SIZE,
                ClipImageTensor.INPUT_SIZE,
                BufferedImage.TYPE_INT_RGB
        );
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(255, 128, 0));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();

        float[][][][] tensor = ClipImageTensor.from(image);

        assertThat(tensor[0][0][0][0]).isCloseTo((1.0f - 0.48145466f) / 0.26862954f, within(0.000001f));
        assertThat(tensor[0][1][0][0]).isCloseTo(((128.0f / 255.0f) - 0.4578275f) / 0.26130258f, within(0.000001f));
        assertThat(tensor[0][2][0][0]).isCloseTo((0.0f - 0.40821073f) / 0.27577711f, within(0.000001f));
    }

    @Test
    void keepsSameObjectCloseAcrossDifferentUniformBackgrounds() {
        BufferedImage onWhite = createScene(new Color(245, 245, 245));
        BufferedImage onGreen = createScene(new Color(50, 120, 70));

        BufferedImage preparedWhite = ImagePreprocessor.prepareForEmbedding(onWhite, 224);
        BufferedImage preparedGreen = ImagePreprocessor.prepareForEmbedding(onGreen, 224);

        double preparedDiff = averagePixelDifference(preparedWhite, preparedGreen);
        double rawDiff = averagePixelDifference(resizeRaw(onWhite, 224), resizeRaw(onGreen, 224));

        assertThat(preparedDiff).isLessThan(rawDiff);
        assertThat(preparedDiff).isLessThan(rawDiff * 0.9);
    }

    private BufferedImage createScene(Color background) {
        BufferedImage image = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(background);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());

        graphics.setColor(new Color(35, 35, 35));
        graphics.fillOval(60, 20, 180, 180);

        graphics.setColor(new Color(220, 210, 120));
        graphics.fillRect(120, 70, 55, 80);

        graphics.setColor(new Color(155, 35, 35));
        graphics.fillRect(80, 100, 30, 25);
        graphics.dispose();
        return image;
    }

    private BufferedImage resizeRaw(BufferedImage original, int targetSize) {
        BufferedImage resized = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        graphics.drawImage(original, 0, 0, targetSize, targetSize, null);
        graphics.dispose();
        return resized;
    }

    private double averagePixelDifference(BufferedImage left, BufferedImage right) {
        long total = 0;
        for (int y = 0; y < left.getHeight(); y++) {
            for (int x = 0; x < left.getWidth(); x++) {
                int rgbLeft = left.getRGB(x, y);
                int rgbRight = right.getRGB(x, y);
                total += Math.abs(((rgbLeft >> 16) & 0xFF) - ((rgbRight >> 16) & 0xFF));
                total += Math.abs(((rgbLeft >> 8) & 0xFF) - ((rgbRight >> 8) & 0xFF));
                total += Math.abs((rgbLeft & 0xFF) - (rgbRight & 0xFF));
            }
        }
        return total / (double) (left.getWidth() * left.getHeight() * 3);
    }
}
