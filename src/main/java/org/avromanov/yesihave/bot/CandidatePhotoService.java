package org.avromanov.yesihave.bot;

import org.avromanov.yesihave.image.ImageBytesNormalizer;
import org.avromanov.yesihave.persistence.CoasterReadRepository;
import org.avromanov.yesihave.storage.ObjectStorageService;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

@Service
public class CandidatePhotoService {
    private static final int MAX_DIMENSION = 1280;
    private final CoasterReadRepository repository;
    private final ObjectStorageService storage;
    private final ImageBytesNormalizer normalizer;

    public CandidatePhotoService(CoasterReadRepository repository, ObjectStorageService storage,
                                 ImageBytesNormalizer normalizer) {
        this.repository = repository;
        this.storage = storage;
        this.normalizer = normalizer;
    }

    public PhotoPair load(UUID coasterId) {
        var pair = repository.findImagePair(coasterId)
                .orElseThrow(() -> new IllegalStateException("Candidate photos not found"));
        return loadPair(pair);
    }

    public PhotoPair load(UUID coasterId, UUID referenceCheckRequestId) {
        if (referenceCheckRequestId == null) {
            return load(coasterId);
        }
        var pair = repository.findReferenceImagePair(coasterId, referenceCheckRequestId)
                .orElseThrow(() -> new IllegalStateException("Candidate reference is no longer confirmed"));
        return loadPair(pair);
    }

    private PhotoPair loadPair(CoasterReadRepository.CoasterImagePair pair) {
        try {
            return new PhotoPair(preview(pair.frontKey()), preview(pair.backKey()));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot prepare candidate photos", e);
        }
    }

    private byte[] preview(String key) throws IOException {
        byte[] bytes = normalizer.normalize(storage.getBytes(key));
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
        if (source == null) {
            throw new IOException("Unreadable candidate photo");
        }
        double scale = Math.min(1.0, (double) MAX_DIMENSION / Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        // White padding preserves the whole photo and Telegram's maximum 20:1 aspect ratio.
        int minDimension = (int) Math.ceil(Math.max(width, height) / 20.0);
        BufferedImage preview = new BufferedImage(Math.max(width, minDimension), Math.max(height, minDimension),
                BufferedImage.TYPE_INT_RGB);
        var graphics = preview.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, preview.getWidth(), preview.getHeight());
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, (preview.getWidth() - width) / 2, (preview.getHeight() - height) / 2,
                    width, height, null);
        } finally {
            graphics.dispose();
        }
        var output = new ByteArrayOutputStream();
        if (!ImageIO.write(preview, "jpg", output)) {
            throw new IOException("JPEG encoder unavailable");
        }
        return output.toByteArray();
    }

    public record PhotoPair(byte[] front, byte[] back) {
    }
}
