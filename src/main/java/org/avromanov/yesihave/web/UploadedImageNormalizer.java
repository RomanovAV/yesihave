package org.avromanov.yesihave.web;

import org.avromanov.yesihave.image.ImageBytesNormalizer;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Component
public class UploadedImageNormalizer {
    private final ImageBytesNormalizer imageBytesNormalizer;

    public UploadedImageNormalizer(ImageBytesNormalizer imageBytesNormalizer) {
        this.imageBytesNormalizer = imageBytesNormalizer;
    }

    public byte[] normalizeToJpegIfHeic(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл не выбран.");
        }
        return imageBytesNormalizer.normalize(file.getContentType(), file.getBytes());
    }
}
