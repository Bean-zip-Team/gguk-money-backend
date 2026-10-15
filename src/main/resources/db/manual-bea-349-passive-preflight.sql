-- Read-only preflight. Run on the target before scheduling the writer-stop maintenance window.
-- Record these results and the timed rehearsal on a recent clone. This does not execute migration.
SELECT statement_timestamp() AS measured_at,
    (SELECT COUNT(*) FROM user_tap_daily) AS daily_rows,
    (SELECT COUNT(*) FROM user_tap_progress) AS progress_rows,
    (SELECT COUNT(*) FROM tap_batch) AS batch_rows,
    pg_total_relation_size('user_tap_daily') AS daily_bytes,
    pg_total_relation_size('user_tap_progress') AS progress_bytes,
    pg_total_relation_size('tap_batch') AS batch_bytes;
