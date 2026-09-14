package org.avromanov.yesihave.image;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageBytesNormalizerTest {

    @Test
    void acceptsSupportedImageWithinLimits() throws Exception {
        byte[] png = png(10, 10);
        ImageBytesNormalizer normalizer = normalizer(1024 * 1024, 100, 100);

        assertThat(normalizer.normalize("image/png", png)).isSameAs(png);
    }

    @Test
    void rejectsEncodedFileBeforeReadingIt() {
        ImageBytesNormalizer normalizer = normalizer(3, 100, 100);

        assertThatThrownBy(() -> normalizer.normalize(new byte[]{1, 2, 3, 4}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("слишком большое");
    }

    @Test
    void rejectsUnsupportedContentEvenWhenMimeTypeClaimsItIsJpeg() {
        ImageBytesNormalizer normalizer = normalizer(1024, 100, 100);

        assertThatThrownBy(() -> normalizer.normalize("image/jpeg", new byte[]{1, 2, 3, 4}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Неподдерживаемый формат");
    }

    @Test
    void rejectsImageWhoseDecodedDimensionsExceedPixelBudget() throws Exception {
        byte[] png = png(11, 10);
        ImageBytesNormalizer normalizer = normalizer(1024 * 1024, 100, 100);

        assertThatThrownBy(() -> normalizer.normalize(png))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("11x10");
    }

    private ImageBytesNormalizer normalizer(long maxBytes, long maxPixels, int maxDimension) {
        return new ImageBytesNormalizer(new ImageProperties(maxBytes, maxPixels, maxDimension, 2));
    }

    private byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }
}
