package org.avromanov.yesihave.bot;

import org.avromanov.yesihave.image.ImageBytesNormalizer;
import org.avromanov.yesihave.image.ImageProperties;
import org.avromanov.yesihave.persistence.CoasterReadRepository;
import org.avromanov.yesihave.storage.ObjectStorageService;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CandidatePhotoServiceTest {
    private final CoasterReadRepository repository = mock(CoasterReadRepository.class);
    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final CandidatePhotoService service = new CandidatePhotoService(repository, storage,
            new ImageBytesNormalizer(new ImageProperties(10 * 1024 * 1024, 13_000_000, 6000, 1)));
    private final UUID id = UUID.randomUUID();

    @Test
    void loadsTheSelectedReferenceRatherThanTheOriginalPair() throws Exception {
        UUID reference = UUID.randomUUID();
        when(repository.findReferenceImagePair(id, reference)).thenReturn(Optional.of(
                new CoasterReadRepository.CoasterImagePair(id, "reference-front", "reference-back")));
        when(storage.getBytes("reference-front")).thenReturn(png(100, 100, Color.GREEN));
        when(storage.getBytes("reference-back")).thenReturn(png(100, 100, Color.RED));
        var photos = service.load(id, reference);
        var front = ImageIO.read(new ByteArrayInputStream(photos.front()));
        assertThat(new Color(front.getRGB(50, 50)).getGreen()).isGreaterThan(240);
        verify(repository, never()).findImagePair(any());
    }

    @Test
    void revokedReferenceIsNotShownAsEvidenceOfAMatch() {
        UUID reference = UUID.randomUUID();
        when(repository.findReferenceImagePair(id, reference)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.load(id, reference)).hasMessageContaining("no longer confirmed");
        verifyNoInteractions(storage);
    }

    @Test
    void loadsCorrectSidesAndCreatesJpegPreviewsWithoutCropping() throws Exception {
        pair();
        when(storage.getBytes("front-key")).thenReturn(png(2560, 1280, Color.RED));
        when(storage.getBytes("back-key")).thenReturn(png(100, 200, Color.BLUE));
        var photos = service.load(id);
        var front = ImageIO.read(new ByteArrayInputStream(photos.front()));
        var back = ImageIO.read(new ByteArrayInputStream(photos.back()));
        assertThat(photos.front()[0] & 0xff).isEqualTo(0xff);
        assertThat(photos.front()[1] & 0xff).isEqualTo(0xd8);
        assertThat(front.getWidth()).isEqualTo(1280);
        assertThat(front.getHeight()).isEqualTo(640);
        assertThat(back.getWidth()).isEqualTo(100);
        assertThat(back.getHeight()).isEqualTo(200);
        assertThat(new Color(front.getRGB(0, 0)).getRed()).isGreaterThan(240);
        assertThat(new Color(back.getRGB(0, 0)).getBlue()).isGreaterThan(240);
        assertThat(photos.front().length).isLessThan(10_000_000);
    }

    @Test
    void padsExtremeAspectRatiosAndTransparencyWithWhite() throws Exception {
        pair();
        when(storage.getBytes("front-key")).thenReturn(png(2000, 10, Color.RED));
        when(storage.getBytes("back-key")).thenReturn(png(100, 100, new Color(0, 0, 0, 0)));
        var photos = service.load(id);
        var front = ImageIO.read(new ByteArrayInputStream(photos.front()));
        var back = ImageIO.read(new ByteArrayInputStream(photos.back()));
        assertThat(front.getWidth()).isEqualTo(1280);
        assertThat(front.getHeight()).isEqualTo(64);
        assertThat(new Color(front.getRGB(0, 0))).isEqualTo(Color.WHITE);
        assertThat(new Color(back.getRGB(50, 50))).isEqualTo(Color.WHITE);
    }

    @Test
    void missingPairDoesNotReadStorage() {
        when(repository.findImagePair(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.load(id)).hasMessage("Candidate photos not found");
        verifyNoInteractions(storage);
    }

    @Test
    void corruptStoredPhotoIsRejected() {
        pair();
        when(storage.getBytes("front-key")).thenReturn(new byte[]{1, 2, 3});
        assertThatThrownBy(() -> service.load(id)).isInstanceOf(IllegalArgumentException.class);
        verify(storage, never()).getBytes("back-key");
    }

    private void pair() {
        when(repository.findImagePair(id)).thenReturn(Optional.of(
                new CoasterReadRepository.CoasterImagePair(id, "front-key", "back-key")));
    }

    private byte[] png(int width, int height, Color color) throws Exception {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
