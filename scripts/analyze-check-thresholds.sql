SELECT decision,
       COUNT(*) AS total_checks,
       ROUND(AVG(pair_score)::numeric, 4) AS avg_pair_score,
       ROUND(AVG(front_score)::numeric, 4) AS avg_front_score,
       ROUND(AVG(back_score)::numeric, 4) AS avg_back_score
FROM check_requests
GROUP BY decision
ORDER BY decision;

SELECT id,
       decision,
       pair_score,
       front_score,
       back_score,
       created_at
FROM check_requests
WHERE pair_score BETWEEN 0.80 AND 0.95
ORDER BY pair_score DESC, created_at DESC
LIMIT 50;

SELECT id,
       decision,
       pair_score,
       LEAST(front_score, back_score) AS min_side_score,
       matched_coaster_id,
       created_at
FROM check_requests
WHERE decision = 'MATCH'
ORDER BY pair_score ASC, min_side_score ASC, created_at DESC
LIMIT 30;

SELECT id,
       decision,
       pair_score,
       LEAST(front_score, back_score) AS min_side_score,
       matched_coaster_id,
       created_at
FROM check_requests
WHERE decision = 'UNCERTAIN'
ORDER BY pair_score DESC, min_side_score DESC, created_at DESC
LIMIT 30;
