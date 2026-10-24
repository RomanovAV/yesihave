package org.avromanov.yesihave.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CoasterReadRepository {
    private final JdbcTemplate jdbcTemplate;

    public CoasterReadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<CoasterImagePair> findImagePair(UUID coasterId) {
        return jdbcTemplate.query(
                """
                SELECT c.id, f.object_key AS front_key, b.object_key AS back_key
                FROM coasters c
                JOIN coaster_images f ON f.coaster_id = c.id AND f.side = 'FRONT'
                JOIN coaster_images b ON b.coaster_id = c.id AND b.side = 'BACK'
                WHERE c.id = ?
                """,
                (rs, rowNum) -> new CoasterImagePair(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("front_key"), rs.getString("back_key")),
                coasterId
        ).stream().findFirst();
    }

    public Optional<CoasterImagePair> findReferenceImagePair(UUID coasterId, UUID checkRequestId) {
        return jdbcTemplate.query("""
                SELECT r.front_image_key, r.back_image_key
                FROM check_requests r
                JOIN check_feedback f ON f.check_request_id = r.id
                WHERE r.id = ? AND f.verdict = 'MATCH' AND f.matched_coaster_id = ?
                """, (rs, rowNum) -> new CoasterImagePair(coasterId,
                rs.getString("front_image_key"), rs.getString("back_image_key")),
                checkRequestId, coasterId).stream().findFirst();
    }

    public List<CoasterImagePair> findPairsNeedingReindex(String modelVersion,
                                                          UUID afterCoasterId,
                                                          int limit) {
        return jdbcTemplate.query(
                """
                SELECT c.id,
                       f.object_key AS front_key,
                       b.object_key AS back_key
                FROM coasters c
                JOIN coaster_images f ON f.coaster_id = c.id AND f.side = 'FRONT'
                JOIN coaster_images b ON b.coaster_id = c.id AND b.side = 'BACK'
                LEFT JOIN coaster_embeddings ce
                  ON ce.coaster_id = c.id AND ce.model_version = ?
                WHERE ce.coaster_id IS NULL
                  AND (?::uuid IS NULL OR c.id > ?::uuid)
                ORDER BY c.id
                LIMIT ?
                """,
                (rs, rowNum) -> new CoasterImagePair(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("front_key"),
                        rs.getString("back_key")
                ),
                modelVersion,
                afterCoasterId,
                afterCoasterId,
                limit
        );
    }

    public record CoasterImagePair(UUID coasterId, String frontKey, String backKey) {
    }
}
