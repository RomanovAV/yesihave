-- Run with psql -X -q -At -v model_version=<active-version> -f this-file.
-- Output contains private collection metadata; keep it outside version control.
\if :{?model_version}
\else
\set model_version clip-vitb32-openai-preprocess-v2
\endif
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SELECT json_build_object('type','coaster','id',c.id,'name',c.name,'at',c.created_at,
       'front',e.front_vector::text,'back',e.back_vector::text,
       'front_key',fi.object_key,'back_key',bi.object_key,'front_sha',fi.sha256,'back_sha',bi.sha256)
FROM coasters c
JOIN coaster_embeddings e ON e.coaster_id=c.id AND e.model_version=:'model_version'
JOIN coaster_images fi ON fi.coaster_id=c.id AND fi.side='FRONT'
JOIN coaster_images bi ON bi.coaster_id=c.id AND bi.side='BACK'
ORDER BY c.created_at;
SELECT json_build_object('type','check','id',r.id,'at',r.created_at,'decision',r.decision,
       'pair',r.pair_score,'verdict',f.verdict,'actual',f.matched_coaster_id,
       'added',f.added_coaster_id,'confirmed_at',f.updated_at,
       'front_key',r.front_image_key,'back_key',r.back_image_key)
FROM check_requests r LEFT JOIN check_feedback f ON f.check_request_id=r.id
ORDER BY r.created_at;
ROLLBACK;
