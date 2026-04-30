package org.avromanov.yesihave.image;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class ImagePreprocessorTest {
    @Test
    void keepsSameObjectCloseAcrossDifferentUniformBackgrounds() {
        BufferedImage onWhite = createScene(new Color(245, 245, 245));
        BufferedImage onGreen = createScene(new Color(50, 120, 70));

        BufferedImage preparedWhite = ImagePreprocessor.prepareForEmbedding(onWhite, 224);
        BufferedImage preparedGreen = ImagePreprocessor.prepareForEmbedding(onGreen, 224);

        double preparedDiff = averagePixelDifference(preparedWhite, preparedGreen);
        double rawDiff = averagePixelDifference(resizeRaw(onWhite, 224), resizeRaw(onGreen, 224));

        assertThat(preparedDiff).isLessThan(rawDiff * 0.4);
        assertThat(preparedDiff).isLessThan(15.0);
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
