package org.avromanov.yesihave.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.UUID;

@Repository
public class MatchingRepository {
    private final JdbcTemplate jdbcTemplate;
    private final String matchingSource;
    private final String referenceColumn;

    private final RowMapper<MatchedCandidateRow> scoredRowMapper = (rs, rowNum) -> MatchedCandidateRow.bestOrientation(
            UUID.fromString(rs.getString("coaster_id")),
            rs.getString("name"),
            rs.getDouble("score_front"),
            rs.getDouble("score_back"),
            rs.getDouble("score_front_to_back"),
            rs.getDouble("score_back_to_front"),
            rs.getObject("source_check_request_id", UUID.class)
    );

    public MatchingRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, true);
    }

    @Autowired
    public MatchingRepository(JdbcTemplate jdbcTemplate,
                              @Value("${app.matching.use-confirmed-references:true}") boolean useConfirmedReferences) {
        this.jdbcTemplate = jdbcTemplate;
        this.matchingSource = useConfirmedReferences ? "coaster_matching_embeddings" : "coaster_embeddings";
        this.referenceColumn = useConfirmedReferences ? "ce.source_check_request_id" : "NULL::uuid";
    }

    public void upsertEmbedding(UUID coasterId, String modelVersion, float[] frontVector, float[] backVector) {
        jdbcTemplate.update(
                """
                INSERT INTO coaster_embeddings(coaster_id, model_version, front_vector, back_vector, updated_at)
                VALUES (?, ?, ?::vector, ?::vector, ?)
                ON CONFLICT (coaster_id, model_version) DO UPDATE SET
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

    public List<UUID> findTopFrontIds(String modelVersion, float[] frontVector, int topK) {
        return jdbcTemplate.query(
                """
                SELECT coaster_id
                FROM %s
                WHERE model_version = ?
                GROUP BY coaster_id
                ORDER BY MIN(front_vector <=> ?::vector), coaster_id
                LIMIT ?
                """.formatted(matchingSource),
                (rs, rowNum) -> UUID.fromString(rs.getString("coaster_id")),
                modelVersion,
                VectorSql.toVectorLiteral(frontVector),
                topK
        );
    }

    public List<UUID> findTopBackIds(String modelVersion, float[] backVector, int topK) {
        return jdbcTemplate.query(
                """
                SELECT coaster_id
                FROM %s
                WHERE model_version = ?
                GROUP BY coaster_id
                ORDER BY MIN(back_vector <=> ?::vector), coaster_id
                LIMIT ?
                """.formatted(matchingSource),
                (rs, rowNum) -> UUID.fromString(rs.getString("coaster_id")),
                modelVersion,
                VectorSql.toVectorLiteral(backVector),
                topK
        );
    }

    public List<MatchedCandidateRow> scoreCandidates(String modelVersion,
                                                     List<UUID> coasterIds,
                                                     float[] frontVector,
                                                     float[] backVector) {
        if (coasterIds.isEmpty()) {
            return List.of();
        }

        String placeholders = coasterIds.stream().map(id -> "?").reduce((a, b) -> a + "," + b).orElse("");
        String sql = """
                SELECT ce.coaster_id,
                       c.name,
                       %s AS source_check_request_id,
                       1 - (ce.front_vector <=> ?::vector) AS score_front,
                       1 - (ce.back_vector <=> ?::vector) AS score_back,
                       1 - (ce.back_vector <=> ?::vector) AS score_front_to_back,
                       1 - (ce.front_vector <=> ?::vector) AS score_back_to_front
                FROM %s ce
                JOIN coasters c ON c.id = ce.coaster_id
                WHERE ce.model_version = ?
                  AND ce.coaster_id IN (""".formatted(referenceColumn, matchingSource)
                + placeholders + ") ORDER BY source_check_request_id NULLS FIRST";

        Object[] args = new Object[5 + coasterIds.size()];
        args[0] = VectorSql.toVectorLiteral(frontVector);
        args[1] = VectorSql.toVectorLiteral(backVector);
        args[2] = VectorSql.toVectorLiteral(frontVector);
        args[3] = VectorSql.toVectorLiteral(backVector);
        args[4] = modelVersion;
        for (int i = 0; i < coasterIds.size(); i++) {
            args[i + 5] = coasterIds.get(i);
        }

        var bestByCoaster = new LinkedHashMap<UUID, MatchedCandidateRow>();
        for (var row : jdbcTemplate.query(sql, scoredRowMapper, args)) {
            // Keep both sides from one reference pair. Independent side maxima
            // across photos could manufacture a match that no pair supports.
            bestByCoaster.merge(row.coasterId(), row, (first, second) ->
                    second.scoreFront() + second.scoreBack() > first.scoreFront() + first.scoreBack()
                            ? second : first);
        }
        return new ArrayList<>(bestByCoaster.values());
    }
}
