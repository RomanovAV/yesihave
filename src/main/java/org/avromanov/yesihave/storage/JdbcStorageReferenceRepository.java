package org.avromanov.yesihave.storage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class JdbcStorageReferenceRepository implements StorageReferenceRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcStorageReferenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean isReferenced(String objectKey) {
        Boolean referenced = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                  SELECT 1 FROM coaster_images WHERE object_key = ?
                  UNION ALL
                  SELECT 1 FROM check_requests WHERE front_image_key = ? OR back_image_key = ?
                )
                """,
                Boolean.class,
                objectKey,
                objectKey,
                objectKey
        );
        return Boolean.TRUE.equals(referenced);
    }

    @Override
    public List<String> findReferencedKeysAfter(String afterObjectKey, int limit) {
        return jdbcTemplate.query(
                """
                SELECT object_key
                FROM (
                  SELECT object_key FROM coaster_images
                  UNION
                  SELECT front_image_key AS object_key FROM check_requests
                  UNION
                  SELECT back_image_key AS object_key FROM check_requests
                ) referenced
                WHERE (?::text IS NULL OR object_key > ?::text)
                ORDER BY object_key
                LIMIT ?
                """,
                (rs, rowNum) -> rs.getString("object_key"),
                afterObjectKey,
                afterObjectKey,
                limit
        );
    }
}
