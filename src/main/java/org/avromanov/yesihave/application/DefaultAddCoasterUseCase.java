package org.avromanov.yesihave.application;

import org.avromanov.yesihave.persistence.CoasterWriteRepository;
import org.avromanov.yesihave.storage.ObjectStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class DefaultAddCoasterUseCase implements AddCoasterUseCase {
    private final ObjectStorageService objectStorageService;
    private final CoasterWriteRepository coasterWriteRepository;

    public DefaultAddCoasterUseCase(ObjectStorageService objectStorageService,
                                    CoasterWriteRepository coasterWriteRepository) {
        this.objectStorageService = objectStorageService;
        this.coasterWriteRepository = coasterWriteRepository;
    }

    @Override
    @Transactional
    public UUID add(String name, byte[] frontImage, byte[] backImage) {
        UUID coasterId = UUID.randomUUID();

        String frontKey = objectStorageService.putImage("coasters/front", frontImage);
        String backKey = objectStorageService.putImage("coasters/back", backImage);

        coasterWriteRepository.insertCoaster(coasterId, name);
        coasterWriteRepository.insertImage(
                UUID.randomUUID(),
                coasterId,
                "FRONT",
                frontKey,
                sha256(frontImage)
        );
        coasterWriteRepository.insertImage(
                UUID.randomUUID(),
                coasterId,
                "BACK",
                backKey,
                sha256(backImage)
        );

        return coasterId;
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
