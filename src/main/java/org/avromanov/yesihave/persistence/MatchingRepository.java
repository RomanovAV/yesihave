package org.avromanov.yesihave.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class MatchingRepository {
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<MatchedCandidateRow> scoredRowMapper = (rs, rowNum) -> new MatchedCandidateRow(
            UUID.fromString(rs.getString("coaster_id")),
            rs.getString("name"),
            rs.getDouble("score_front"),
            rs.getDouble("score_back")
    );

    public MatchingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsertEmbedding(UUID coasterId, String modelVersion, float[] frontVector, float[] backVector) {
        jdbcTemplate.update(
                """
                INSERT INTO coaster_embeddings(coaster_id, model_version, front_vector, back_vector, updated_at)
                VALUES (?, ?, ?::vector, ?::vector, ?)
                ON CONFLICT (coaster_id) DO UPDATE SET
                  model_version = EXCLUDED.model_version,
                  front_vector = EXCLUDED.front_vector,
                  back_vector = EXCLUDED.back_vector,
                  updated_at = EXCLUDED.updated_at
                """,
                coasterId,
                modelVersion,
                VectorSql.toVectorLiteral(frontVector),
                VectorSql.toVectorLiteral(backVector),
                Timestamp.from(OffsetDateTime.now().toInstant())
        );
    }

    public List<UUID> findTopFrontIds(float[] frontVector, int topK) {
        return jdbcTemplate.query(
                """
                SELECT coaster_id
                FROM coaster_embeddings
                ORDER BY front_vector <=> ?::vector
                LIMIT ?
                """,
                (rs, rowNum) -> UUID.fromString(rs.getString("coaster_id")),
                VectorSql.toVectorLiteral(frontVector),
                topK
        );
    }

    public List<UUID> findTopBackIds(float[] backVector, int topK) {
        return jdbcTemplate.query(
                """
                SELECT coaster_id
                FROM coaster_embeddings
                ORDER BY back_vector <=> ?::vector
                LIMIT ?
                """,
                (rs, rowNum) -> UUID.fromString(rs.getString("coaster_id")),
                VectorSql.toVectorLiteral(backVector),
                topK
        );
    }

    public List<MatchedCandidateRow> scoreCandidates(List<UUID> coasterIds, float[] frontVector, float[] backVector) {
        if (coasterIds.isEmpty()) {
            return List.of();
        }

        String placeholders = coasterIds.stream().map(id -> "?").reduce((a, b) -> a + "," + b).orElse("");
        String sql = """
                SELECT ce.coaster_id,
                       c.name,
                       1 - (ce.front_vector <=> ?::vector) AS score_front,
                       1 - (ce.back_vector <=> ?::vector) AS score_back
                FROM coaster_embeddings ce
                JOIN coasters c ON c.id = ce.coaster_id
                WHERE ce.coaster_id IN (""" + placeholders + ") ";

        Object[] args = new Object[2 + coasterIds.size()];
        args[0] = VectorSql.toVectorLiteral(frontVector);
        args[1] = VectorSql.toVectorLiteral(backVector);
        for (int i = 0; i < coasterIds.size(); i++) {
            args[i + 2] = coasterIds.get(i);
        }

        return jdbcTemplate.query(sql, scoredRowMapper, args);
    }
}
