package org.avromanov.yesihave.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class CheckEmbeddingRepository {
    private final JdbcTemplate jdbcTemplate;

    public CheckEmbeddingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsert(UUID checkRequestId, String modelVersion, String imagePairKey,
                       float[] front, float[] back) {
        jdbcTemplate.update("""
                INSERT INTO check_embeddings(check_request_id, model_version, image_pair_key, front_vector, back_vector)
                VALUES (?, ?, ?, ?::vector, ?::vector)
                ON CONFLICT (check_request_id, model_version) DO NOTHING
                """, checkRequestId, modelVersion, imagePairKey,
                VectorSql.toVectorLiteral(front), VectorSql.toVectorLiteral(back));
    }

    public boolean exists(UUID checkRequestId, String modelVersion) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM check_embeddings WHERE check_request_id = ? AND model_version = ?)
                """, Boolean.class, checkRequestId, modelVersion));
    }

    public List<ConfirmedPair> findConfirmedNeedingReindex(String modelVersion, UUID afterId, int limit) {
        return jdbcTemplate.query("""
                SELECT r.id, r.front_image_key, r.back_image_key
                FROM check_requests r
                JOIN check_feedback f ON f.check_request_id = r.id AND f.verdict = 'MATCH'
                LEFT JOIN check_embeddings e ON e.check_request_id = r.id AND e.model_version = ?
                WHERE e.check_request_id IS NULL AND (?::uuid IS NULL OR r.id > ?::uuid)
                ORDER BY r.id
                LIMIT ?
                """, (rs, rowNum) -> new ConfirmedPair(UUID.fromString(rs.getString("id")),
                        rs.getString("front_image_key"), rs.getString("back_image_key")),
                modelVersion, afterId, afterId, limit);
    }

    public record ConfirmedPair(UUID checkRequestId, String frontKey, String backKey) {
    }
}
