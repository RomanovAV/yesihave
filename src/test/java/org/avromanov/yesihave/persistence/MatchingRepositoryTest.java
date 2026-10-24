package org.avromanov.yesihave.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MatchingRepositoryTest {
    @Test
    void disablingReferencesUsesOriginalsForRetrievalAndScoring() {
        var jdbc = mock(JdbcTemplate.class);
        var repository = new MatchingRepository(jdbc, false);
        var vector = new float[]{1, 0};
        repository.findTopFrontIds("test", vector, 20);
        repository.findTopBackIds("test", vector, 20);
        repository.scoreCandidates("test", List.of(UUID.randomUUID()), vector, vector);
        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(3)).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sql.getAllValues()).allSatisfy(query -> {
            assertThat(query).contains("FROM coaster_embeddings").doesNotContain("coaster_matching_embeddings");
        });
        assertThat(sql.getAllValues().getLast()).contains("NULL::uuid AS source_check_request_id");
    }

    @Test
    void choosesOneCompleteReferencePairAndReturnsEachCoasterOnce() {
        var jdbc = mock(JdbcTemplate.class);
        UUID coaster = UUID.randomUUID(), other = UUID.randomUUID();
        UUID firstReference = UUID.randomUUID(), secondReference = UUID.randomUUID();
        var first = new MatchedCandidateRow(coaster, "A", .98, .40, firstReference);
        var second = new MatchedCandidateRow(coaster, "A", .40, .97, secondReference);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<MatchedCandidateRow>>any(),
                any(Object[].class))).thenReturn(List.of(first, second,
                new MatchedCandidateRow(other, "B", .80, .80)));

        var rows = new MatchingRepository(jdbc).scoreCandidates("test", List.of(coaster, other),
                new float[]{1, 0}, new float[]{0, 1});

        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst()).isEqualTo(first);
        assertThat(rows.getFirst().scoreBack()).isEqualTo(.40);
        assertThat(rows.getFirst().referenceCheckRequestId()).isEqualTo(firstReference);
    }
}
