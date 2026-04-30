package org.avromanov.yesihave.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

final class ImagePreprocessor {
    private static final double FOREGROUND_DISTANCE_THRESHOLD = 55.0;
    private static final double MIN_FOREGROUND_RATIO = 0.01;
    private static final double CROP_PADDING_RATIO = 0.08;
    private static final double TARGET_PADDING_RATIO = 0.06;

    private ImagePreprocessor() {
    }

    static BufferedImage prepareForEmbedding(BufferedImage original, int targetSize) {
        BufferedImage cropped = cropForeground(original);
        return resizeWithPadding(cropped, targetSize);
    }

    static BufferedImage prepareFullImageForEmbedding(BufferedImage original, int targetSize) {
        return resizeWithPadding(original, targetSize);
    }

    static BufferedImage cropForeground(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= 1 || height <= 1) {
            return source;
        }

        Color background = estimateBackground(source);
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        int foregroundPixels = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!isForeground(source.getRGB(x, y), background)) {
                    continue;
                }
                foregroundPixels++;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }

        double foregroundRatio = foregroundPixels / (double) (width * height);
        if (foregroundRatio < MIN_FOREGROUND_RATIO || maxX < minX || maxY < minY) {
            return source;
        }

        int paddingX = Math.max(2, (int) Math.round((maxX - minX + 1) * CROP_PADDING_RATIO));
        int paddingY = Math.max(2, (int) Math.round((maxY - minY + 1) * CROP_PADDING_RATIO));
        minX = Math.max(0, minX - paddingX);
        minY = Math.max(0, minY - paddingY);
        maxX = Math.min(width - 1, maxX + paddingX);
        maxY = Math.min(height - 1, maxY + paddingY);

        return source.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private static BufferedImage resizeWithPadding(BufferedImage source, int targetSize) {
        BufferedImage output = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = output.createGraphics();
        graphics.setColor(estimateBackground(source));
        graphics.fillRect(0, 0, targetSize, targetSize);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        int usableSize = Math.max(1, (int) Math.round(targetSize * (1.0 - 2.0 * TARGET_PADDING_RATIO)));
        double scale = Math.min(
                usableSize / (double) source.getWidth(),
                usableSize / (double) source.getHeight()
        );

        int drawWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int drawHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
        int drawX = (targetSize - drawWidth) / 2;
        int drawY = (targetSize - drawHeight) / 2;

        graphics.drawImage(source, drawX, drawY, drawWidth, drawHeight, null);
        graphics.dispose();
        return output;
    }

    private static boolean isForeground(int rgb, Color background) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        double dr = r - background.getRed();
        double dg = g - background.getGreen();
        double db = b - background.getBlue();
        return Math.sqrt(dr * dr + dg * dg + db * db) >= FOREGROUND_DISTANCE_THRESHOLD;
    }

    private static Color estimateBackground(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int marginX = Math.max(1, width / 12);
        int marginY = Math.max(1, height / 12);

        long sumR = 0;
        long sumG = 0;
        long sumB = 0;
        long count = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean borderPixel = x < marginX || x >= width - marginX || y < marginY || y >= height - marginY;
                if (!borderPixel) {
                    continue;
                }
                int rgb = source.getRGB(x, y);
                sumR += (rgb >> 16) & 0xFF;
                sumG += (rgb >> 8) & 0xFF;
                sumB += rgb & 0xFF;
                count++;
            }
        }

        if (count == 0) {
            return Color.WHITE;
        }

        return new Color(
                (int) (sumR / count),
                (int) (sumG / count),
                (int) (sumB / count)
        );
    }
}
