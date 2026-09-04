-- PostgreSQL performance indexes that must be deployed manually.
-- Inspect the confirmed runtime database for equivalent indexes first,
-- then run each statement outside a transaction.

CREATE INDEX CONCURRENTLY ix_user_tap_daily_date_user
ON user_tap_daily (tap_date, user_id);
