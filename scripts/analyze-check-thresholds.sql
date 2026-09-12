-- Feedback coverage. Quality metrics are misleading until a meaningful share of
-- checks has been confirmed by a user.
SELECT COUNT(*) AS total_checks,
       COUNT(f.check_request_id) AS labeled_checks,
       ROUND(
         100.0 * COUNT(f.check_request_id) / NULLIF(COUNT(*), 0),
         2
       ) AS labeled_percent
FROM check_requests r
LEFT JOIN check_feedback f ON f.check_request_id = r.id;

-- Prediction/ground-truth matrix. UNCERTAIN stays visible instead of being
-- silently counted as either MATCH or NO_MATCH.
SELECT r.decision AS predicted,
       f.verdict AS actual,
       COUNT(*) AS checks
FROM check_feedback f
JOIN check_requests r ON r.id = f.check_request_id
GROUP BY r.decision, f.verdict
ORDER BY r.decision, f.verdict;

-- Automatic MATCH precision and recall. A MATCH is a true positive only when
-- the predicted coaster id is also correct.
WITH labeled AS (
  SELECT r.decision,
         r.matched_coaster_id AS predicted_coaster_id,
         f.verdict,
         f.matched_coaster_id AS actual_coaster_id
  FROM check_feedback f
  JOIN check_requests r ON r.id = f.check_request_id
), metrics AS (
  SELECT COUNT(*) FILTER (
           WHERE decision = 'MATCH'
             AND verdict = 'MATCH'
             AND predicted_coaster_id = actual_coaster_id
         ) AS true_positive,
         COUNT(*) FILTER (WHERE decision = 'MATCH') AS predicted_positive,
         COUNT(*) FILTER (WHERE verdict = 'MATCH') AS actual_positive
  FROM labeled
)
SELECT true_positive,
       predicted_positive,
       actual_positive,
       ROUND(100.0 * true_positive / NULLIF(predicted_positive, 0), 2) AS precision_percent,
       ROUND(100.0 * true_positive / NULLIF(actual_positive, 0), 2) AS recall_percent
FROM metrics;

-- Score distributions by actual label: these are the inputs for selecting new
-- pair/min-side thresholds.
SELECT f.verdict AS actual,
       COUNT(*) AS checks,
       ROUND(AVG(r.pair_score)::numeric, 4) AS avg_pair_score,
       ROUND(MIN(r.pair_score)::numeric, 4) AS min_pair_score,
       ROUND(MAX(r.pair_score)::numeric, 4) AS max_pair_score,
       ROUND(AVG(LEAST(r.front_score, r.back_score))::numeric, 4) AS avg_min_side_score
FROM check_feedback f
JOIN check_requests r ON r.id = f.check_request_id
WHERE r.pair_score IS NOT NULL
GROUP BY f.verdict
ORDER BY f.verdict;

-- Candidate recall: for confirmed matches, was the correct coaster present in
-- the persisted top candidate list at all?
SELECT COUNT(*) AS confirmed_matches,
       COUNT(*) FILTER (
         WHERE EXISTS (
           SELECT 1
           FROM check_candidates c
           WHERE c.check_request_id = f.check_request_id
             AND c.coaster_id = f.matched_coaster_id
         )
       ) AS correct_candidate_present,
       ROUND(
         100.0 * COUNT(*) FILTER (
           WHERE EXISTS (
             SELECT 1
             FROM check_candidates c
             WHERE c.check_request_id = f.check_request_id
               AND c.coaster_id = f.matched_coaster_id
           )
         ) / NULLIF(COUNT(*), 0),
         2
       ) AS candidate_recall_percent
FROM check_feedback f
WHERE f.verdict = 'MATCH';

-- Most useful mistakes to inspect manually.
SELECT r.id,
       r.decision AS predicted,
       f.verdict AS actual,
       r.pair_score,
       LEAST(r.front_score, r.back_score) AS min_side_score,
       r.matched_coaster_id AS predicted_coaster_id,
       f.matched_coaster_id AS actual_coaster_id,
       r.created_at
FROM check_feedback f
JOIN check_requests r ON r.id = f.check_request_id
WHERE (r.decision = 'MATCH' AND f.verdict = 'NO_MATCH')
   OR (r.decision <> 'MATCH' AND f.verdict = 'MATCH')
   OR (r.decision = 'MATCH' AND f.verdict = 'MATCH'
       AND r.matched_coaster_id IS DISTINCT FROM f.matched_coaster_id)
ORDER BY r.created_at DESC
LIMIT 100;
