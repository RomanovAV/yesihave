package org.avromanov.yesihave.persistence;

import org.avromanov.yesihave.application.DuplicateCoasterException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public class CoasterWriteRepository {
    private final JdbcTemplate jdbcTemplate;

    public CoasterWriteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertCoaster(UUID coasterId, String name) {
        jdbcTemplate.update(
                """
                INSERT INTO coasters(id, name, created_at, updated_at)
                VALUES (?, ?, ?, ?)
                """,
                coasterId,
                name,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    public void insertImage(UUID imageId, UUID coasterId, String side, String objectKey, String sha256) {
        jdbcTemplate.update(
                """
                INSERT INTO coaster_images(id, coaster_id, side, object_key, sha256, created_at)
                VALUES (?, ?, ?::coaster_side, ?, ?, ?)
                """,
                imageId,
                coasterId,
                side,
                objectKey,
                sha256,
                OffsetDateTime.now()
        );
    }

    public void claimImagePair(String imagePairKey, UUID coasterId) {
        int inserted = jdbcTemplate.update(
                """
                INSERT INTO coaster_image_pairs(image_pair_key, coaster_id)
                VALUES (?, ?)
                ON CONFLICT (image_pair_key) DO NOTHING
                """,
                imagePairKey,
                coasterId
        );
        if (inserted == 1) {
            return;
        }

        UUID existingCoasterId = jdbcTemplate.queryForObject(
                "SELECT coaster_id FROM coaster_image_pairs WHERE image_pair_key = ?",
                UUID.class,
                imagePairKey
        );
        throw new DuplicateCoasterException(existingCoasterId);
    }
}
